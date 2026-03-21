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
    public long executeLong(VirtualFrame frame) {
        return operand1.executeLong(frame) & operand2.executeLong(frame);
    }
}
