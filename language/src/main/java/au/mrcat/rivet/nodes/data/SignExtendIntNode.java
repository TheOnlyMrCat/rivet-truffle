package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SignExtendIntNode extends RivetOpNode {
    @Child RivetOpNode op;

    public SignExtendIntNode(RivetOpNode op) {
        this.op = op;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        return (int) op.executeLong(frame, basePc, priv);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SignExtendIntNode{");
        sb.append("op=").append(op);
        sb.append('}');
        return sb.toString();
    }
}
