package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class RemainderUnsignedNode extends RivetOpNode {
    @Child RivetOpNode dividend;
    @Child RivetOpNode divisor;

    public RemainderUnsignedNode(RivetOpNode dividend, RivetOpNode divisor) {
        this.dividend = dividend;
        this.divisor = divisor;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        long dividend = this.dividend.executeLong(frame);
        long divisor = this.divisor.executeLong(frame);

        if (divisor == 0) {
            return dividend;
        }
        return Long.remainderUnsigned(dividend, divisor);
    }
}
