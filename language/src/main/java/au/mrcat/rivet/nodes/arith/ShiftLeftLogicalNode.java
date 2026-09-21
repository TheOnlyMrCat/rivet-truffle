package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ShiftLeftLogicalNode extends RivetOpNode {
    @Child RivetOpNode operand;
    @Child RivetOpNode shiftAmount;
    private final long shiftAmountMask;

    public ShiftLeftLogicalNode(RivetOpNode operand, RivetOpNode shiftAmount) {
        this(operand, shiftAmount, 0b111_111);
    }

    public ShiftLeftLogicalNode(RivetOpNode operand, RivetOpNode shiftAmount, long shiftAmountMask) {
        this.operand = operand;
        this.shiftAmount = shiftAmount;
        this.shiftAmountMask = shiftAmountMask;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        return operand.executeLong(frame, basePc, priv) << (shiftAmount.executeLong(frame, basePc, priv) & shiftAmountMask);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ShiftLeftLogicalNode{");
        sb.append("operand=").append(operand);
        sb.append(", shiftAmount=").append(shiftAmount);
        sb.append(", shiftAmountMask=").append(shiftAmountMask);
        sb.append('}');
        return sb.toString();
    }
}
