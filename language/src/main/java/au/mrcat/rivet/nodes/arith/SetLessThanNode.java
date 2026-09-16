package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SetLessThanNode extends RivetOpNode {
    @Child RivetOpNode lhs;
    @Child RivetOpNode rhs;

    public SetLessThanNode(RivetOpNode lhs, RivetOpNode rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc) {
        return lhs.executeLong(frame, basePc) < rhs.executeLong(frame, basePc) ? 1L : 0L;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SetLessThanNode{");
        sb.append("lhs=").append(lhs);
        sb.append(", rhs=").append(rhs);
        sb.append('}');
        return sb.toString();
    }
}
