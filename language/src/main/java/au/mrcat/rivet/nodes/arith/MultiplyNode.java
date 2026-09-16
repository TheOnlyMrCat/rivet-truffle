package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MultiplyNode extends RivetOpNode {
    @Child RivetOpNode multiplicand;
    @Child RivetOpNode multiplier;

    public MultiplyNode(RivetOpNode multiplicand, RivetOpNode multiplier) {
        this.multiplicand = multiplicand;
        this.multiplier = multiplier;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc) {
        return multiplicand.executeLong(frame, basePc) * multiplier.executeLong(frame, basePc);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MultiplyNode{");
        sb.append("multiplicand=").append(multiplicand);
        sb.append(", multiplier=").append(multiplier);
        sb.append('}');
        return sb.toString();
    }
}
