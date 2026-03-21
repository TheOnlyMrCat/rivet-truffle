package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class EncodedInstructionNode extends RivetNode {
    public final int instruction;

    public EncodedInstructionNode(int instruction) {
        this.instruction = instruction;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return null;
    }
}
