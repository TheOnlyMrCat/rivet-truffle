package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvInstructionFenceException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class InstructionFenceNode extends RivetDivergentNode {
    private final long nextPc;
    private final short instret;

    public InstructionFenceNode(long nextPc, short instret) {
        this.nextPc = nextPc;
        this.instret = instret;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        throw new RiscvInstructionFenceException(basePc + nextPc, instret);
    }

    @Override
    public Long[] callTargetContinuations() {
        // Don't bother parsing instructions that would be immediately invalidated anyway
        return new Long[0];
    }
}
