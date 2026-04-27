package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.runtime.RiscvInstructionFenceException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class InstructionFenceNode extends RivetDivergentNode {
    private final long nextPc;

    public InstructionFenceNode(long nextPc) {
        this.nextPc = nextPc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new RiscvInstructionFenceException(nextPc);
    }

    @Override
    public Long[] callTargetContinuations() {
        // Don't bother parsing instructions that would be immediately invalidated anyway
        return new Long[0];
    }
}
