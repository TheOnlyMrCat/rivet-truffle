package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetCsrwNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvInstructionFenceException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SetCsrNode extends RivetCsrwNode {
    @Child RivetOpNode value;
    private final long pcOffset;
    private final int instruction;
    private final short instret;

    public SetCsrNode(RivetOpNode value, int csr, long pcOffset, int instruction, short instret) {
        super(csr);
        this.value = value;
        this.pcOffset = pcOffset;
        this.instruction = instruction;
        this.instret = instret;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();

        try {
            ctx.privilegedState.tryWrite(csr, value.executeLong(frame, basePc, priv));
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(Integer.toUnsignedLong(instruction));
            trap.setInstret(instret);
            throw trap;
        }

        // CSR-write instructions reset the basic-block instruction counter, to allow them to read an accurate count.
        // Additionally, writes to mcycle and minstret are considered to happen after the instruction has othewrise retired.
        // We therefore need to step the counters here, only if we haven't just written to them
        ctx.privilegedState.stepPerformanceCounters((short) (csr != Csr.MCYCLE ? instret + 1 : 0), (short) (csr != Csr.MINSTRET ? instret + 1 : 0));
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SetCsrNode{");
        sb.append("value=").append(value);
        sb.append(", csr=").append(csr);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
