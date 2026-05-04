package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GetCsrNode extends RivetOpNode {
    private final int csr;
    private final long pc;

    public GetCsrNode(int csr, long pc) {
        this.csr = csr;
        this.pc = pc;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        try {
            return ctx.privilegedState.tryRead(csr);
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("GetCsrNode{");
        sb.append("csr=").append(csr);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
