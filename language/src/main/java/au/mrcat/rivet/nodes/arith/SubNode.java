package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SubNode extends RivetOpNode {
    @Child RivetOpNode minuend;
    @Child RivetOpNode subtrahend;

    public SubNode(RivetOpNode minuend, RivetOpNode subtrahend) {
        this.minuend = minuend;
        this.subtrahend = subtrahend;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        return minuend.executeLong(frame) - subtrahend.executeLong(frame);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SubNode{");
        sb.append("minuend=").append(minuend);
        sb.append(", subtrahend=").append(subtrahend);
        sb.append('}');
        return sb.toString();
    }
}
