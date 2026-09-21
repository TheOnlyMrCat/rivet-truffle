package au.mrcat.rivet.nodes.control;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvIndirectJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;

/// Represents a "hole" in a call target: a region that has not / cannot be parsed yet.
/// Effectively a JumpNode(PcOffsetNode(pcOffset)), but without triggering a call target continuation.
public class HoleNode extends RivetDivergentNode {
    private final long pcOffset;

    public HoleNode(long pcOffset) {
        this.pcOffset = pcOffset;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        throw new RiscvIndirectJumpException(basePc + pcOffset);
    }

    @Override
    public Long[] callTargetContinuations() {
        return new Long[0];
    }
}
