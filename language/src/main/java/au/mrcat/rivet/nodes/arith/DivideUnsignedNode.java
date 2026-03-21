package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class DivideUnsignedNode extends RivetOpNode {
    @Child RivetOpNode dividend;
    @Child RivetOpNode divisor;

    public DivideUnsignedNode(RivetOpNode dividend, RivetOpNode divisor) {
        this.dividend = dividend;
        this.divisor = divisor;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        long dividend = this.dividend.executeLong(frame);
        long divisor = this.divisor.executeLong(frame);

        if (divisor == 0) {
            return -1;
        }
        return Long.divideUnsigned(dividend, divisor);
    }
}
