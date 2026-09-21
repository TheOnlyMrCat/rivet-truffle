package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class DivideUnsignedNode extends RivetOpNode {
    @Child RivetOpNode dividend;
    @Child RivetOpNode divisor;

    public DivideUnsignedNode(RivetOpNode dividend, RivetOpNode divisor) {
        this.dividend = dividend;
        this.divisor = divisor;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        long dividend = this.dividend.executeLong(frame, basePc, priv);
        long divisor = this.divisor.executeLong(frame, basePc, priv);

        if (divisor == 0) {
            return -1;
        }
        return Long.divideUnsigned(dividend, divisor);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("DivideUnsignedNode{");
        sb.append("dividend=").append(dividend);
        sb.append(", divisor=").append(divisor);
        sb.append('}');
        return sb.toString();
    }
}
