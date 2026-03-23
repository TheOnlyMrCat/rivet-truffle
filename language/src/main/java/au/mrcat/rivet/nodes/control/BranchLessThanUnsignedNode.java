package au.mrcat.rivet.nodes.control;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvJumpException;
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
    public void executeVoid(VirtualFrame frame) {
        if (Long.compareUnsigned(lhs.executeLong(frame), rhs.executeLong(frame)) < 0) {
            throw new RiscvJumpException(trueBranchPc);
        } else {
            throw new RiscvJumpException(falseBranchPc);
        }
    }
}
