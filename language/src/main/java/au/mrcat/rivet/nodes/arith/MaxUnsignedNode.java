package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MaxUnsignedNode extends RivetOpNode {
    @Child RivetOpNode operand1;
    @Child RivetOpNode operand2;

    public MaxUnsignedNode(RivetOpNode operand1, RivetOpNode operand2) {
        this.operand1 = operand1;
        this.operand2 = operand2;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        long operand1 = this.operand1.executeLong(frame);
        long operand2 = this.operand2.executeLong(frame);
        if (Long.compareUnsigned(operand1, operand2) > 0) {
            return operand1;
        } else {
            return operand2;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MaxUnsignedNode{");
        sb.append("operand1=").append(operand1);
        sb.append(", operand2=").append(operand2);
        sb.append('}');
        return sb.toString();
    }
}
