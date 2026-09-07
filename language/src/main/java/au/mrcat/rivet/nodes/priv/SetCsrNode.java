package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetInstretNode;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvInstructionFenceException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SetCsrNode extends RivetInstretNode {
    @Child RivetOpNode value;
    private final int csr;
    private final long pc;
    private final int instruction;
    private final short instret;

    public SetCsrNode(RivetOpNode value, int csr, long pc, int instruction, short instret) {
        this.value = value;
        this.csr = csr;
        this.pc = pc;
        this.instruction = instruction;
        this.instret = instret;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        try {
            ctx.privilegedState.tryWrite(csr, value.executeLong(frame));
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            trap.setTval(Integer.toUnsignedLong(instruction));
            trap.setInstret(instret);
            throw trap;
        }

        // CSR-write instructions reset the basic-block instruction counter, to allow them to read an accurate count.
        // Additionally, writes to mcycle and minstret are considered to happen after the instruction has othewrise retired.
        // We therefore need to step the counters here, only if we haven't just written to them
        ctx.privilegedState.stepPerformanceCounters((short) (csr != Csr.MCYCLE ? instret + 1 : 0), (short) (csr != Csr.MINSTRET ? instret + 1 : 0));
        if (csr == Csr.SATP) {
            throw new RiscvInstructionFenceException(pc + 4, (short) 0);
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
