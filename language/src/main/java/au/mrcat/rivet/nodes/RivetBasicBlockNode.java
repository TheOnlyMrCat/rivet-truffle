package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import java.util.Arrays;

public class RivetBasicBlockNode extends RivetDivergentNode {
    @Children RivetNode[] instructions;
    @Child RivetDivergentNode divergentNode;

    private final long firstPc;
    public final short instructionsRetired;

    @CompilerDirectives.CompilationFinal(dimensions = 1) int[] successorIndices;

    public RivetBasicBlockNode(RivetNode[] instructions, RivetDivergentNode divergentNode, long firstPc, short instructionsRetired) {
        this.instructions = instructions;
        this.divergentNode = divergentNode;
        this.firstPc = firstPc;
        this.instructionsRetired = instructionsRetired;
    }

    @Override
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

    @Override
    public Long[] callTargetContinuations() {
        return divergentNode.callTargetContinuations();
    }

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
