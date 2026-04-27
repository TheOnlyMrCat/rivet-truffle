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
                frame.setLongStatic(0, mscratch);
                ctx.privilegedState.mscratch = op.executeLong(frame);
                frame.setLongStatic(rd, mscratch);
            }
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction, pc);
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("CsrRmwNode{");
        sb.append("op=").append(op);
        sb.append(", csr=").append(csr);
        sb.append(", rd=").append(rd);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
