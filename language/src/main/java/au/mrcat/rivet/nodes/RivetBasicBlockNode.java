package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import java.util.Arrays;

public class RivetBasicBlockNode extends RivetNode {
    @Children RivetNode[] instructions;
    @Child RivetDivergentNode divergentNode;
    private final long nextPc;
    public final short instructionsRetired;

    public RivetBasicBlockNode(RivetNode[] instructions, RivetDivergentNode divergentNode, long nextPc, short instructionsRetired) {
        this.instructions = instructions;
        this.divergentNode = divergentNode;
        this.nextPc = nextPc;
        this.instructionsRetired = instructionsRetired;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeDivergent(frame);
    }

    public long executeDivergent(VirtualFrame frame) {
        if (instructions != null) {
            for (RivetNode instruction : instructions) {
                instruction.executeVoid(frame);
            }
        }
        return divergentNode.executeDivergent(frame);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RivetBasicBlockNode{");
        sb.append("instructions=").append(Arrays.toString(instructions));
        sb.append(", nextPc=").append(nextPc);
        sb.append(", instructionsRetired=").append(instructionsRetired);
        sb.append('}');
        return sb.toString();
    }
}
