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
    private final short instret;

    public GetCsrNode(int csr, long pc, int instruction, short instret) {
        this.csr = csr;
        this.pc = pc;
        this.instruction = instruction;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        try {
            long result = ctx.privilegedState.tryRead(csr);
            // If we've just read an instruction counter, it will be inaccurate in the case we're in the middle of a
            // basic block. Add the instret counter to the result to account for this.
            if (csr == Csr.MCYCLE || csr == Csr.MINSTRET || csr == Csr.CYCLE || csr == Csr.INSTRET) {
                result += instret;
            }
            return result;
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            trap.setTval(Integer.toUnsignedLong(instruction));
            trap.setInstret(instret);
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
