package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetTrapNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class BreakpointNode extends RivetTrapNode {
    private final long pc;

    public BreakpointNode(long pc, short instret) {
        super(instret);
        this.pc = pc;
    }

    @Override
    public long executeDivergent(VirtualFrame frame) {
        throw new RiscvTrapException(ExceptionCause.Breakpoint, pc, pc, instret);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BreakpointNode{");
        sb.append("pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
