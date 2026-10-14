package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetCsrwNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SipReadOrNode extends RivetCsrwNode {
    @Child RivetOpNode op;
    private final int rd;
    private final short instret;

    public SipReadOrNode(RivetOpNode op, int rd, short instret) {
        super(Csr.SIP);
        this.op = op;
        this.rd = rd;
        this.instret = instret;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        var ctx = currentLanguageContext();
        // CSR-write instructions reset the basic-block instruction counter, to allow them to read an accurate count.
        // We therefore need to step the counters here.
        ctx.privilegedState.stepPerformanceCounters(instret);
        long previousValue = ctx.privilegedState.readOrSip(op.executeLong(frame, basePc, priv));
        previousValue = ctx.privilegedState.reconstituteHardwareSeip(csr, previousValue);
        frame.setLongStatic(rd, previousValue);
    }
}
