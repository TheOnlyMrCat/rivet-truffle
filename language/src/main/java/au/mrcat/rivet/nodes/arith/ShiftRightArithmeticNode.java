package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ShiftRightArithmeticNode extends RivetOpNode {
    @Child RivetOpNode operand;
    @Child RivetOpNode shiftAmount;
    private final long shiftAmountMask;

    public ShiftRightArithmeticNode(RivetOpNode operand, RivetOpNode shiftAmount) {
        this(operand, shiftAmount, 0b111_111);
    }

    public ShiftRightArithmeticNode(RivetOpNode operand, RivetOpNode shiftAmount, long shiftAmountMask) {
        this.operand = operand;
        this.shiftAmount = shiftAmount;
        this.shiftAmountMask = shiftAmountMask;
    }


    @Override
    public long executeLong(VirtualFrame frame) {
        return operand.executeLong(frame) >> (shiftAmount.executeLong(frame) & shiftAmountMask);
    }
}
