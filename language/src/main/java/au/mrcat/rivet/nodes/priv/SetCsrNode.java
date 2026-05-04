package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SetCsrNode extends RivetNode {
    @Child RivetOpNode value;
    private final int csr;
    private final long pc;

    public SetCsrNode(RivetOpNode value, int csr, long pc) {
        this.value = value;
        this.csr = csr;
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        try {
            ctx.privilegedState.tryWrite(csr, value.executeLong(frame));
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SetCsrNode{");
        sb.append("value=").append(value);
        sb.append(", csr=").append(csr);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
