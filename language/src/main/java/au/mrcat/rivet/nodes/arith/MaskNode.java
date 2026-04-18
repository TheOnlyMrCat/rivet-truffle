package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MaskNode extends RivetOpNode {
    @Child RivetOpNode operand;
    @Child RivetOpNode mask;

    public MaskNode(RivetOpNode operand, RivetOpNode mask) {
        this.operand = operand;
        this.mask = mask;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        return operand.executeLong(frame) & ~mask.executeLong(frame);
    }
}
