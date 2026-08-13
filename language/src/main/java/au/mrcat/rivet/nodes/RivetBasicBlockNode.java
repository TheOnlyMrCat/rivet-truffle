package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import java.util.Arrays;

public class RivetBasicBlockNode extends RivetNode {
    @Children RivetNode[] instructions;
    @Child RivetDivergentNode divergentNode;

    private final long firstPc;
    public final short instructionsRetired;

    @CompilerDirectives.CompilationFinal(dimensions = 1) int[] successorIndices;
    @CompilerDirectives.CompilationFinal(dimensions = 1) long[] successorFirstPcs;

    public RivetBasicBlockNode(RivetNode[] instructions, RivetDivergentNode divergentNode, long firstPc, short instructionsRetired) {
        this.instructions = instructions;
        this.divergentNode = divergentNode;
        this.firstPc = firstPc;
        this.instructionsRetired = instructionsRetired;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeDivergent(frame);
    }

    @ExplodeLoop
    public int executeDivergent(VirtualFrame frame) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (instructions != null) {
            for (RivetNode instruction : instructions) {
                instruction.executeVoid(frame);
            }
        }
        return divergentNode.executeDivergent(frame);
    }

//    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
//    public int getSuccessor(long pc) {
//        CompilerAsserts.compilationConstant(successorIndices.length);
//        for (int i = 0; i < successorIndices.length; i++) {
//            if (successorFirstPcs[i] == pc) {
//                return successorIndices[i];
//            }
//        }
//        return -1;
//    }

    public long getFirstPc() {
        return firstPc;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RivetBasicBlockNode{");
        sb.append("instructions=").append(Arrays.toString(instructions));
        sb.append(", divergent=").append(divergentNode);
        sb.append(", firstPc=").append(firstPc);
        sb.append(", instructionsRetired=").append(instructionsRetired);
        sb.append('}');
        return sb.toString();
    }
}
