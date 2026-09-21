package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class RemainderNode extends RivetOpNode {
    @Child RivetOpNode dividend;
    @Child RivetOpNode divisor;

    public RemainderNode(RivetOpNode dividend, RivetOpNode divisor) {
        this.dividend = dividend;
        this.divisor = divisor;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        long dividend = this.dividend.executeLong(frame, basePc, priv);
        long divisor = this.divisor.executeLong(frame, basePc, priv);

        if (divisor == 0) {
            return dividend;
        }
        if (dividend == Long.MIN_VALUE && divisor == -1) {
            return 0;
        }
        return dividend % divisor;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("RemainderNode{");
        sb.append("dividend=").append(dividend);
        sb.append(", divisor=").append(divisor);
        sb.append('}');
        return sb.toString();
    }
}
