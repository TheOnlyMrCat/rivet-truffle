package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MultiplyHighNode extends RivetOpNode {
    @Child RivetOpNode multiplicand;
    @Child RivetOpNode multiplier;

    public MultiplyHighNode(RivetOpNode multiplicand, RivetOpNode multiplier) {
        this.multiplicand = multiplicand;
        this.multiplier = multiplier;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        return Math.multiplyHigh(multiplicand.executeLong(frame), multiplier.executeLong(frame));
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MultiplyHighNode{");
        sb.append("multiplicand=").append(multiplicand);
        sb.append(", multiplier=").append(multiplier);
        sb.append('}');
        return sb.toString();
    }
}
