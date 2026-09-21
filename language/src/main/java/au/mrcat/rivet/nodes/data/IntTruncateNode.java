package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class IntTruncateNode extends RivetOpNode {
    @Child RivetOpNode op;

    public IntTruncateNode(RivetOpNode op) {
        this.op = op;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        return op.executeLong(frame, basePc, priv) & 0xFFFF_FFFFL;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("IntTruncateNode{");
        sb.append("op=").append(op);
        sb.append('}');
        return sb.toString();
    }
}
