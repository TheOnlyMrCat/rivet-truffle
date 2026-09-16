package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class DivideNode extends RivetOpNode {
    @Child RivetOpNode dividend;
    @Child RivetOpNode divisor;

    public DivideNode(RivetOpNode dividend, RivetOpNode divisor) {
        this.dividend = dividend;
        this.divisor = divisor;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc) {
        long dividend = this.dividend.executeLong(frame, basePc);
        long divisor = this.divisor.executeLong(frame, basePc);

        if (divisor == 0) {
            return -1;
        }
        if (dividend == Long.MIN_VALUE && divisor == -1) {
            return Long.MIN_VALUE;
        }
        return dividend / divisor;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("DivideNode{");
        sb.append("dividend=").append(dividend);
        sb.append(", divisor=").append(divisor);
        sb.append('}');
        return sb.toString();
    }
}
