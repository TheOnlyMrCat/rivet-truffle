package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GetCsrNode extends RivetOpNode {
    private final int csr;
    private final long pc;
    private final int instruction;

    public GetCsrNode(int csr, long pc, int instruction) {
        this.csr = csr;
        this.pc = pc;
        this.instruction = instruction;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        try {
            return ctx.privilegedState.tryRead(csr);
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            trap.setTval(Integer.toUnsignedLong(instruction));
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
