package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MultiplyHighUnsignedNode extends RivetOpNode {
    @Child RivetOpNode multiplicand;
    @Child RivetOpNode multiplier;

    public MultiplyHighUnsignedNode(RivetOpNode multiplicand, RivetOpNode multiplier) {
        this.multiplicand = multiplicand;
        this.multiplier = multiplier;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        return Math.unsignedMultiplyHigh(multiplicand.executeLong(frame, basePc, priv), multiplier.executeLong(frame, basePc, priv));
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MultiplyHighUnsignedNode{");
        sb.append("multiplicand=").append(multiplicand);
        sb.append(", multiplier=").append(multiplier);
        sb.append('}');
        return sb.toString();
    }
}
