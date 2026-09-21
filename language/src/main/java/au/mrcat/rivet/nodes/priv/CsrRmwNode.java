package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetCsrwNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public class CsrRmwNode extends RivetCsrwNode {
    @Node.Child RivetOpNode op;
    private final int rd;
    private final long pcOffset;
    private final int instruction;
    private final short instret;

    public CsrRmwNode(RivetOpNode op, int csr, int rd, long pcOffset, int instruction, short instret) {
        super(csr);
        this.op = op;
        this.rd = rd;
        this.pcOffset = pcOffset;
        this.instruction = instruction;
        this.instret = instret;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        // CSR-write instructions reset the basic-block instruction counter, to allow them to read an accurate count.
        // We therefore need to step the counters here.
        ctx.privilegedState.stepPerformanceCounters(instret);

        try {
            long previousValue = ctx.privilegedState.tryReadWrite(csr);
            // Use the temp (0) register as the operand
            frame.setLongStatic(0, previousValue);
            ctx.privilegedState.tryWrite(csr, op.executeLong(frame, basePc, priv));
            previousValue = ctx.privilegedState.reconstituteHardwareSeip(csr, previousValue);
            frame.setLongStatic(rd, previousValue);
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(Integer.toUnsignedLong(instruction));
            throw trap;
        }

        // Writes to mcycle and minstret are considered to happen after the instruction has othewrise retired.
        // Emulate this by only incrementing them if they respectively haven't just been written by this instruction.
        // It would be preferable to combine with this the step call at the top of the function, but that would
        // necessitate a duplicated check for an illegal CSR.
        ctx.privilegedState.stepPerformanceCounters((short) (csr != Csr.MCYCLE ? 1 : 0), (short) (csr != Csr.MINSTRET ? 1 : 0));
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("CsrRmwNode{");
        sb.append("op=").append(op);
        sb.append(", csr=").append(csr);
        sb.append(", rd=").append(rd);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
