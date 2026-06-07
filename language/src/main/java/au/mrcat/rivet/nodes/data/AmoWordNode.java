package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class AmoWordNode extends RivetOpNode {
    @Child RivetOpNode address;
    @Child RivetOpNode op;
    private final long pc;
    private final short instret;

    public AmoWordNode(RivetOpNode address, RivetOpNode op, long pc, short instret) {
        this.address = address;
        this.op = op;
        this.pc = pc;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        try {
            int originalValue = ctx.readInt(address);

            // Use the zero register as a temporary
            frame.setLongStatic(0, originalValue);
            long opResult = op.executeLong(frame);

            ctx.writeInt(address, (int) opResult);
            return originalValue;
        } catch (RiscvTrapException trap) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault, pc, address, instret);
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("AmoWordNode{");
        sb.append("address=").append(address);
        sb.append(", op=").append(op);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
