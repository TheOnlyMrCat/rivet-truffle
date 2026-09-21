package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetTrapNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class EnvironmentCallNode extends RivetTrapNode {
    private final long pcOffset;

    public EnvironmentCallNode(long pcOffset, short instret) {
        super(instret);
        this.pcOffset = pcOffset;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        throw new RiscvTrapException(switch (priv.currentMode()) {
            case User -> ExceptionCause.EnvironmentCallFromUMode;
            case Supervisor -> ExceptionCause.EnvironmentCallFromSMode;
            case Machine -> ExceptionCause.EnvironmentCallFromMMode;
        }, basePc + pcOffset, 0, instret);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EnvironmentCallNode{");
        sb.append("pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
