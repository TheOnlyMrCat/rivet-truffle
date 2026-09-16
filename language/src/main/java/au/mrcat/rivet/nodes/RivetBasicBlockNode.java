package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import java.util.Arrays;

public class RivetBasicBlockNode extends RivetNode {
    @Children RivetInstructionNode[] instructions;
    @Child RivetDivergentNode divergentNode;

    public final short instructionsRetired;

    @CompilerDirectives.CompilationFinal(dimensions = 1) int[] successorIndices;

    public RivetBasicBlockNode(RivetInstructionNode[] instructions, RivetDivergentNode divergentNode, short instructionsRetired) {
        this.instructions = instructions;
        this.divergentNode = divergentNode;
        this.instructionsRetired = instructionsRetired;
    }

    @ExplodeLoop
    public int executeDivergent(VirtualFrame frame, long basePc) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (instructions != null) {
            for (RivetInstructionNode instruction : instructions) {
                instruction.executeVoid(frame, basePc);
            }
        }
        return divergentNode.executeDivergent(frame, basePc);
    }

    public Long[] callTargetContinuations() {
        return divergentNode.callTargetContinuations();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RivetBasicBlockNode{");
        sb.append("instructions=").append(Arrays.toString(instructions));
        sb.append(", divergent=").append(divergentNode);
        sb.append(", instructionsRetired=").append(instructionsRetired);
        sb.append('}');
        return sb.toString();
    }
}
