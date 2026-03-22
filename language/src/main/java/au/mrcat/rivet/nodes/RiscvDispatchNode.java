package au.mrcat.rivet.nodes;

import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;

public class RiscvDispatchNode extends RivetNode implements BlockNode.ElementExecutor<RivetNode> {
    @Child BlockNode<RivetNode> instructions;
    private final long baseAddress;

    public RiscvDispatchNode(RivetNode[] instructions, long baseAddress) {
        this.instructions = BlockNode.create(instructions, this);
        this.baseAddress = baseAddress;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        instructions.executeVoid(frame, BlockNode.NO_ARGUMENT);
        throw new RiscvJumpException(this.baseAddress + 4L * instructions.getElements().length);
    }

    @Override
    public void executeVoid(VirtualFrame frame, RivetNode node, int index, int argument) {
        node.executeVoid(frame);
    }
}
