package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class AndNode extends RivetOpNode {
    @Child RivetOpNode operand1;
    @Child RivetOpNode operand2;

    public AndNode(RivetOpNode operand1, RivetOpNode operand2) {
        this.operand1 = operand1;
        this.operand2 = operand2;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc) {
        return operand1.executeLong(frame, basePc) & operand2.executeLong(frame, basePc);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("AndNode{");
        sb.append("operand1=").append(operand1);
        sb.append(", operand2=").append(operand2);
        sb.append('}');
        return sb.toString();
    }
}
