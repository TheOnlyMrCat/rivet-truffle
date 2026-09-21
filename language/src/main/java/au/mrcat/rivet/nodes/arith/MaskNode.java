package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MaskNode extends RivetOpNode {
    @Child RivetOpNode operand;
    @Child RivetOpNode mask;

    public MaskNode(RivetOpNode operand, RivetOpNode mask) {
        this.operand = operand;
        this.mask = mask;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        return operand.executeLong(frame, basePc, priv) & ~mask.executeLong(frame, basePc, priv);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MaskNode{");
        sb.append("operand=").append(operand);
        sb.append(", mask=").append(mask);
        sb.append('}');
        return sb.toString();
    }
}
