package au.mrcat.rivet.nodes;

import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;

public class RiscvDispatchNode extends RivetNode implements BlockNode.ElementExecutor<RivetNode> {
    @Child BlockNode<RivetNode> instructions = null;
    private final long nextPc;

    public RiscvDispatchNode(RivetNode[] instructions, long nextPc) {
        if (instructions.length != 0) {
            this.instructions = BlockNode.create(instructions, this);
        }
        this.nextPc = nextPc;
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
