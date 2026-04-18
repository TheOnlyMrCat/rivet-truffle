package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.Csr;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public class CsrRmwNode extends RivetNode {
    @Node.Child RivetOpNode op;
    private final int csr;
    private final int rd;
    private final long pc;

    public CsrRmwNode(RivetOpNode op, int csr, int rd, long pc) {
        this.op = op;
        this.csr = csr;
        this.rd = rd;
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        switch (csr) {
            case Csr.MSCRATCH -> {
                // This uses the temp (0) register
                long mscratch = ctx.privilegedState.mscratch;
                ctx.setRegister(0, mscratch);
                ctx.privilegedState.mscratch = op.executeLong(frame);
                ctx.setRegister(rd, mscratch);
            }
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction, pc);
        }

    }
}
