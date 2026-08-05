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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.Arrays;
import java.util.SequencedMap;

public class RivetCallTargetNode extends RootNode {
    @Children BareSetRegisterNode[] copyFromState = new BareSetRegisterNode[31];
    @Children GetRegisterNode[] copyToState = new GetRegisterNode[31];

    @Child private LoopNode loop;

    private static class RivetCallTargetRepeatingNode extends RivetNode implements RepeatingNode {
        @Children RivetBasicBlockNode[] basicBlockNodes;
        @CompilerDirectives.CompilationFinal(dimensions = 1) final long[] pcOffsets;

        RivetCallTargetRepeatingNode(SequencedMap<Long, RivetBasicBlockNode> basicBlocks) {
            basicBlockNodes = new RivetBasicBlockNode[basicBlocks.size()];
            pcOffsets = new long[basicBlocks.size()];
            int i = 0;
            for (var entry : basicBlocks.sequencedEntrySet()) {
                pcOffsets[i] = entry.getKey();
                basicBlockNodes[i] = entry.getValue();
                i++;
            }
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            CompilerAsserts.neverPartOfCompilation("should be executeRepeating()");
            throw new IllegalStateException("should be executeRepeating()");
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            var ctx = RivetContext.get(this);
            long pc = frame.getLongStatic(0);

            int bbIndex = Arrays.binarySearch(pcOffsets, pc);
            if (bbIndex < 0) {
                // Not a basic block in this call target; return to the dispatch node to go to the next call target
                return false;
            }

            try {
                frame.setLongStatic(0, basicBlockNodes[bbIndex].executeDivergent(frame));
            } catch (RiscvInstructionFenceException fence) {
                CompilerDirectives.transferToInterpreter();
                CompilerAsserts.neverPartOfCompilation("Instruction fences should always deoptimise");

                var newRegisters = new RegisterState();
                for (int i = 0; i < 31; i++) {
                    newRegisters.setRegister(i+1, frame.getLongStatic(i+1));
                }
                fence.setState(newRegisters);
                throw fence;
            } catch (RiscvTrapException trap) {
                CompilerDirectives.transferToInterpreter();
                CompilerAsserts.neverPartOfCompilation("Traps should always deoptimise");

                var newRegisters = new RegisterState();
                for (int i = 0; i < 31; i++) {
                    newRegisters.setRegister(i+1, frame.getLongStatic(i+1));
                }
                trap.setState(newRegisters);
                throw trap;
            }
            ctx.privilegedState.stepPerformanceCounters(basicBlockNodes[bbIndex].instructionsRetired);
            return true;
        }
    }

    public RivetCallTargetNode(RivetLanguage language, SequencedMap<Long, RivetBasicBlockNode> basicBlocks) {
        var frameDescriptor = FrameDescriptor.newBuilder();
//        frameDescriptor.useSlotKinds(false);
        frameDescriptor.addSlots(32, FrameSlotKind.Static);
        super(language, frameDescriptor.build());

        loop = Truffle.getRuntime().createLoopNode(new RivetCallTargetRepeatingNode(basicBlocks));

        for (int i = 0; i < 31; i++) {
            copyFromState[i] = new BareSetRegisterNode(i + 1);
            copyToState[i] = (GetRegisterNode) GetRegisterNode.create(i + 1);
        }
    }

    public long[] getPcOffsets() {
        return ((RivetCallTargetRepeatingNode) loop.getRepeatingNode()).pcOffsets;
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

        frame.setLongStatic(0, pc);
        copyFromRegisterState(frame, registers);

        loop.execute(frame);

        var newRegisters = new RegisterState();
        copyToRegisterState(frame, newRegisters);
        newRegisters.setPc(frame.getLongStatic(0));
        return newRegisters;
    }

    @Override
    public String toString() {
        var repeating = (RivetCallTargetRepeatingNode) loop.getRepeatingNode();
        final StringBuilder sb = new StringBuilder("RivetCallTargetNode{");
        sb.append("basicBlockNodes=").append(Arrays.toString(repeating.basicBlockNodes));
        sb.append(", pcOffsets=").append(Arrays.toString(repeating.pcOffsets));
        sb.append('}');
        return sb.toString();
    }
}
