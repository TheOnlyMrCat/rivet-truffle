package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ShiftRightLogicalNode extends RivetOpNode {
    @Child RivetOpNode operand;
    @Child RivetOpNode shiftAmount;
    private final long shiftAmountMask;

    public ShiftRightLogicalNode(RivetOpNode operand, RivetOpNode shiftAmount) {
        this(operand, shiftAmount, 0b111_111);
    }

    public ShiftRightLogicalNode(RivetOpNode operand, RivetOpNode shiftAmount, long shiftAmountMask) {
        this.operand = operand;
        this.shiftAmount = shiftAmount;
        this.shiftAmountMask = shiftAmountMask;
    }


    @Override
    public long executeLong(VirtualFrame frame) {
        return operand.executeLong(frame) >>> (shiftAmount.executeLong(frame) & shiftAmountMask);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ShiftRightLogicalNode{");
        sb.append("operand=").append(operand);
        sb.append(", shiftAmount=").append(shiftAmount);
        sb.append(", shiftAmountMask=").append(shiftAmountMask);
        sb.append('}');
        return sb.toString();
    }
}
