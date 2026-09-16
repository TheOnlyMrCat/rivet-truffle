package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetTrapNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class BreakpointNode extends RivetTrapNode {
    private final long pcOffset;

    public BreakpointNode(long pcOffset, short instret) {
        super(instret);
        this.pcOffset = pcOffset;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc) {
        throw new RiscvTrapException(ExceptionCause.Breakpoint, basePc + pcOffset, basePc + pcOffset, instret);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BreakpointNode{");
        sb.append("pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
