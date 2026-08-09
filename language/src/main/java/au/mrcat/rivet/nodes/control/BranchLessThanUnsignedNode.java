package au.mrcat.rivet.nodes.control;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class BranchLessThanUnsignedNode extends RivetDivergentNode {
    @Child RivetOpNode lhs;
    @Child RivetOpNode rhs;
    private final long trueBranchPc;
    private final long falseBranchPc;

    public BranchLessThanUnsignedNode(RivetOpNode lhs, RivetOpNode rhs, long trueBranchPc, long falseBranchPc) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.trueBranchPc = trueBranchPc;
        this.falseBranchPc = falseBranchPc;
    }

    @Override
    public int executeDivergent(VirtualFrame frame) {
        if (Long.compareUnsigned(lhs.executeLong(frame), rhs.executeLong(frame)) < 0) {
            return 0;
        } else {
            return 1;
        }
    }

    @Override
    public Long[] callTargetContinuations() {
        return new Long[] {trueBranchPc, falseBranchPc};
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BranchLessThanUnsignedNode{");
        sb.append("lhs=").append(lhs);
        sb.append(", rhs=").append(rhs);
        sb.append(", trueBranchPc=").append(trueBranchPc);
        sb.append(", falseBranchPc=").append(falseBranchPc);
        sb.append('}');
        return sb.toString();
    }
}
