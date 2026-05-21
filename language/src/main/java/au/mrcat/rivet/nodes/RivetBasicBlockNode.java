package au.mrcat.rivet.nodes;

import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;

public class RivetBasicBlockNode extends RivetNode implements BlockNode.ElementExecutor<RivetNode> {
    @Child BlockNode<RivetNode> instructions = null;
    private final long nextPc;
    public final short instructionsRetired;

    public RivetBasicBlockNode(RivetNode[] instructions, long nextPc, short instructionsRetired) {
        if (instructions.length != 0) {
            this.instructions = BlockNode.create(instructions, this);
        }
        this.nextPc = nextPc;
        this.instructionsRetired = instructionsRetired;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        if (instructions != null) {
            instructions.executeVoid(frame, BlockNode.NO_ARGUMENT);
        }
        throw new RiscvJumpException(nextPc);
    }

    @Override
    public void executeVoid(VirtualFrame frame, RivetNode node, int index, int argument) {
        node.executeVoid(frame);
    }
}
