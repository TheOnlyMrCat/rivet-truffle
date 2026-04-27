package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class OrNode extends RivetOpNode {
    @Child RivetOpNode operand1;
    @Child RivetOpNode operand2;

    public OrNode(RivetOpNode operand1, RivetOpNode operand2) {
        this.operand1 = operand1;
        this.operand2 = operand2;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        return operand1.executeLong(frame) | operand2.executeLong(frame);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("OrNode{");
        sb.append("operand1=").append(operand1);
        sb.append(", operand2=").append(operand2);
        sb.append('}');
        return sb.toString();
    }
}
