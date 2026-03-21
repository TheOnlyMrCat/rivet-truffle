package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SetLessThanUnsignedNode extends RivetOpNode {
    @Child RivetOpNode lhs;
    @Child RivetOpNode rhs;

    public SetLessThanUnsignedNode(RivetOpNode lhs, RivetOpNode rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        return Long.compareUnsigned(lhs.executeLong(frame), rhs.executeLong(frame)) < 0 ? 1L : 0L;
    }
}
