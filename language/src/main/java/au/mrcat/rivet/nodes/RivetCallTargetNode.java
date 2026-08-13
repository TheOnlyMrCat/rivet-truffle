package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.nodes.data.BareSetRegisterNode;
import au.mrcat.rivet.nodes.data.GetRegisterNode;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.RiscvIndirectJumpException;
import au.mrcat.rivet.runtime.RiscvInstructionFenceException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.Arrays;
import java.util.SortedMap;

public class RivetCallTargetNode extends RootNode {
    @Children RivetBasicBlockNode[] basicBlockNodes;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final long[] pcOffsets;

    @Children BareSetRegisterNode[] copyFromState = new BareSetRegisterNode[31];
    @Children GetRegisterNode[] copyToState = new GetRegisterNode[31];

    private final long entryPc;
    private final int firstBlockIndex;

    public RivetCallTargetNode(RivetLanguage language, long entryPc, SortedMap<Long, RivetBasicBlockNode> basicBlocks) {
        var frameDescriptor = FrameDescriptor.newBuilder();
//        frameDescriptor.useSlotKinds(false);
        frameDescriptor.addSlots(32, FrameSlotKind.Static);
        super(language, frameDescriptor.build());

        basicBlockNodes = new RivetBasicBlockNode[basicBlocks.size()];
        pcOffsets = new long[basicBlocks.size()];
        int i = 0;
        for (var entry : basicBlocks.sequencedEntrySet()) {
            pcOffsets[i] = entry.getKey();
            basicBlockNodes[i] = entry.getValue();
            i++;
        }

        for (var block : basicBlockNodes) {
            var continuations = block.divergentNode.callTargetContinuations();
            block.successorIndices = new int[continuations.length];
            block.successorFirstPcs = new long[continuations.length];

            for (i = 0; i < continuations.length; i++) {
                block.successorIndices[i] = Arrays.binarySearch(pcOffsets, continuations[i]);
                assert block.successorIndices[i] >= 0; // FIXME: This will fail when the parser bailed out for large call targets
                block.successorFirstPcs[i] = continuations[i];
            }
        }

        for (i = 0; i < 31; i++) {
            copyFromState[i] = new BareSetRegisterNode(i + 1);
            copyToState[i] = (GetRegisterNode) GetRegisterNode.create(i + 1);
        }

        this.entryPc = entryPc;
        this.firstBlockIndex = lookupBlock(entryPc);
    }

    public long[] getPcOffsets() {
        return pcOffsets;
    }

    private int lookupBlock(long pc) {
        int bbIndex = Arrays.binarySearch(pcOffsets, pc);
        if (bbIndex < 0) {
            return -1;
        }
        return bbIndex;
    }

    @ExplodeLoop
    private void copyFromRegisterState(VirtualFrame frame, RegisterState state) {
        for (int i = 0; i < 31; i++) {
            copyFromState[i].executeVoid(frame, state.getRegister(i + 1));
        }
    }

    @ExplodeLoop
    private void copyToRegisterState(VirtualFrame frame, RegisterState state) {
        for (int i = 0; i < 31; i++) {
            state.setRegister(i+1, copyToState[i].executeLong(frame));
        }
    }

    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object execute(VirtualFrame frame) {
        var ctx = RivetContext.get(this);
        var registers = (RegisterState) frame.getArguments()[0];
        long pc = registers.getPc();

        assert pc == entryPc;
        pc = entryPc;
        CompilerAsserts.partialEvaluationConstant(pc);

        copyFromRegisterState(frame, registers);


        CompilerAsserts.partialEvaluationConstant(basicBlockNodes);

        try {
            int block = firstBlockIndex;

            CompilerAsserts.partialEvaluationConstant(basicBlockNodes[0]);
            while (true) {
//                CompilerAsserts.partialEvaluationConstant(block);
                int successorIndex;
                try {
                    successorIndex = basicBlockNodes[block].executeDivergent(frame);
                } catch (RiscvIndirectJumpException jump) {
                    // IndirectJumpExceptions should only be thrown in divergent nodes at the end of basic blocks, and only when
                    // they retire normally, so the block's instret counter is correct.
                    ctx.privilegedState.stepPerformanceCounters(basicBlockNodes[block].instructionsRetired);
                    copyToRegisterState(frame, registers);

                    // Handling an indirect jump is the root's "normal" case, so return normally here instead of rethrowing.
                    registers.setPc(jump.getTargetPc());
                    return registers;
                }
                ctx.privilegedState.stepPerformanceCounters(basicBlockNodes[block].instructionsRetired);

                for (int i = 0; i < basicBlockNodes[block].successorIndices.length; i++) {
                    if (successorIndex == i) {
                        block = basicBlockNodes[block].successorIndices[i];
                    }
                }
//                block = basicBlockNodes[block].successorIndices[successorIndex];
            }
        } catch (RiscvInstructionFenceException fence) {
            CompilerDirectives.transferToInterpreter();
            copyToRegisterState(frame, registers);

            // Root needs to special-case this and figure out which call targets to invalidate
            fence.setState(registers);
            throw fence;
        } catch (RiscvTrapException trap) {
            CompilerDirectives.transferToInterpreter();
            copyToRegisterState(frame, registers);

            // Root needs to special case this and trigger a trap. That logic could theoretically live here, but we
            // assume a trap loop is an atypical situation we do not need to optimise for.
            trap.setState(registers);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RivetCallTargetNode{");
        sb.append("basicBlockNodes=").append(Arrays.toString(basicBlockNodes));
        sb.append(", pcOffsets=").append(Arrays.toString(pcOffsets));
        sb.append('}');
        return sb.toString();
    }

    public long getEntryPc() {
        return entryPc;
    }
}
