package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GetCsrNode extends RivetOpNode {
    private final int csr;
    private final long pcOffset;
    private final int instruction;
    private final short instret;

    public GetCsrNode(int csr, long pcOffset, int instruction, short instret) {
        this.csr = csr;
        this.pcOffset = pcOffset;
        this.instruction = instruction;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        try {
            long result = ctx.privilegedState.tryRead(csr);
            // If we've just read an instruction counter, it will be inaccurate in the case we're in the middle of a
            // basic block. Add the instret counter to the result to account for this.
            if (csr == Csr.MCYCLE || csr == Csr.MINSTRET || csr == Csr.CYCLE || csr == Csr.INSTRET) {
                result += instret;
            }
            result = ctx.privilegedState.reconstituteHardwareSeip(csr, result);
            return result;
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(Integer.toUnsignedLong(instruction));
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("GetCsrNode{");
        sb.append("csr=").append(csr);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
