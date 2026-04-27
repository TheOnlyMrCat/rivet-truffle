package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class BreakpointNode extends RivetDivergentNode {
    private final long pc;

    public BreakpointNode(long pc) {
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new RiscvTrapException(ExceptionCause.Breakpoint, pc);
    }

    @Override
    public Long[] callTargetContinuations() {
        return new Long[0];
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BreakpointNode{");
        sb.append("pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
