package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetTrapNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class EnvironmentCallNode extends RivetTrapNode {
    private final long pc;

    public EnvironmentCallNode(long pc, short instret) {
        super(instret);
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        throw new RiscvTrapException(switch (ctx.privilegedState.currentMode()) {
            case User -> ExceptionCause.EnvironmentCallFromUMode;
            case Supervisor -> ExceptionCause.EnvironmentCallFromSMode;
            case Machine -> ExceptionCause.EnvironmentCallFromMMode;
        }, pc, 0, instret);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EnvironmentCallNode{");
        sb.append("pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
