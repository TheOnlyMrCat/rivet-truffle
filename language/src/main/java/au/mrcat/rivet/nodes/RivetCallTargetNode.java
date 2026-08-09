package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.nodes.data.BareSetRegisterNode;
import au.mrcat.rivet.nodes.data.GetRegisterNode;
import au.mrcat.rivet.nodes.data.SetRegisterNode;
import au.mrcat.rivet.riscv.RegisterState;
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
import java.util.SequencedMap;

public class RivetCallTargetNode extends RootNode {
    @Children RivetBasicBlockNode[] basicBlockNodes;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final long[] pcOffsets;

    @Children BareSetRegisterNode[] copyFromState = new BareSetRegisterNode[31];
    @Children GetRegisterNode[] copyToState = new GetRegisterNode[31];

    public RivetCallTargetNode(RivetLanguage language, SequencedMap<Long, RivetBasicBlockNode> basicBlocks) {
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

        for (i = 0; i < 31; i++) {
            copyFromState[i] = new BareSetRegisterNode(i + 1);
            copyToState[i] = (GetRegisterNode) GetRegisterNode.create(i + 1);
        }
    }

    public long[] getPcOffsets() {
        return pcOffsets;
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
    public Object execute(VirtualFrame frame) {
        var ctx = RivetContext.get(this);
        var registers = (RegisterState) frame.getArguments()[0];
        long pc = registers.getPc();

        copyFromRegisterState(frame, registers);

        while (true) {
            int bbIndex = Arrays.binarySearch(pcOffsets, pc);
            if (bbIndex < 0) {
                // Not a basic block in this call target; return to the dispatch node to go to the next call target
                copyToRegisterState(frame, registers);
                registers.setPc(pc);
                return registers;
            }
            try {
                pc = basicBlockNodes[bbIndex].executeDivergent(frame);
                ctx.privilegedState.stepPerformanceCounters(basicBlockNodes[bbIndex].instructionsRetired);
            } catch (RiscvInstructionFenceException fence) {
                CompilerDirectives.transferToInterpreter();
                CompilerAsserts.neverPartOfCompilation("Instruction fences should always deoptimise");
                copyToRegisterState(frame, registers);
                fence.setState(registers);
                throw fence;
            } catch (RiscvTrapException trap) {
                CompilerDirectives.transferToInterpreter();
                CompilerAsserts.neverPartOfCompilation("Traps should always deoptimise");
                copyToRegisterState(frame, registers);
                trap.setState(registers);
                throw trap;
            }
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
}
