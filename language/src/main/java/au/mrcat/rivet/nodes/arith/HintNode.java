package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class HintNode extends RivetNode {
    private final int instruction;

    public HintNode(int instruction) {
        this.instruction = instruction;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return null;
    }
}
