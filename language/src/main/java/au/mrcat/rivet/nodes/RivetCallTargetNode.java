package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.runtime.RiscvInstructionFenceException;
import au.mrcat.rivet.runtime.RiscvJumpException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.Arrays;
import java.util.SequencedMap;
import java.util.StringJoiner;

public class RivetCallTargetNode extends RootNode {
    @Children RivetBasicBlockNode[] basicBlockNodes;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final long[] pcOffsets;

    public RivetCallTargetNode(RivetLanguage language, SequencedMap<Long, RivetBasicBlockNode> basicBlocks) {
        var frameDescriptor = FrameDescriptor.newBuilder();
        frameDescriptor.useSlotKinds(false);
        frameDescriptor.addSlots(32);
        super(language, frameDescriptor.build());

        basicBlockNodes = new RivetBasicBlockNode[basicBlocks.size()];
        pcOffsets = new long[basicBlocks.size()];
        int i = 0;
        for (var entry : basicBlocks.sequencedEntrySet()) {
            pcOffsets[i] = entry.getKey();
            basicBlockNodes[i] = entry.getValue();
            i++;
        }
    }

    public long[] getPcOffsets() {
        return pcOffsets;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        var ctx = RivetContext.get(this);
        var registers = (MaterializedFrame) frame.getArguments()[0];
        long pc = (Long) frame.getArguments()[1];

        // Copy registers into the new frame, except for the zero (temp) register
        for (int i = 1; i < 32; i++) {
            frame.setLongStatic(i, registers.getLongStatic(i));
        }

        while (true) {
            int bbIndex = Arrays.binarySearch(pcOffsets, pc);
            if (bbIndex < 0) {
                // Not a basic block in this call target; return to the dispatch node to go to the next call target
                // Since we can only return one thing, just put the program counter in the zero (temp) register
                frame.setLongStatic(0, pc);
                return frame.materialize();
            }
            try {
                basicBlockNodes[bbIndex].executeVoid(frame);
                CompilerDirectives.shouldNotReachHere("Basic block exited without updating pc");
            } catch (RiscvJumpException jump) {
                pc = jump.targetPc;
                ctx.privilegedState.stepPerformanceCounters(basicBlockNodes[bbIndex].instructionsRetired);
            } catch (RiscvInstructionFenceException fence) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                CompilerAsserts.neverPartOfCompilation("Instruction fences should always deoptimise");
                fence.setFrame(frame.materialize());
                throw fence;
            } catch (RiscvTrapException trap) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                CompilerAsserts.neverPartOfCompilation("Traps should always deoptimise");
                trap.setFrame(frame.materialize());
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
