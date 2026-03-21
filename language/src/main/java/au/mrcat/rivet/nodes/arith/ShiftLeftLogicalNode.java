package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ShiftLeftLogicalNode extends RivetOpNode {
    @Child RivetOpNode operand;
    @Child RivetOpNode shiftAmount;

    public ShiftLeftLogicalNode(RivetOpNode operand, RivetOpNode shiftAmount) {
        this.operand = operand;
        this.shiftAmount = shiftAmount;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        return operand.executeLong(frame) << shiftAmount.executeLong(frame);
    }
}
