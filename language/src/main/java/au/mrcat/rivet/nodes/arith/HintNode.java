package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class HintNode extends RivetNode {
    private final int instruction;

    public HintNode(int instruction) {
        this.instruction = instruction;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("HintNode{");
        sb.append("instruction=").append(instruction);
        sb.append('}');
        return sb.toString();
    }
}
