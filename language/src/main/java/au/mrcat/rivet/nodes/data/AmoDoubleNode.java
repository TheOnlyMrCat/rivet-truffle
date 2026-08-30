package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class AmoDoubleNode extends RivetOpNode {
    @Child RivetOpNode address;
    @Child RivetOpNode op;
    private final long pc;
    private final short instret;

    public AmoDoubleNode(RivetOpNode address, RivetOpNode op, long pc, short instret) {
        this.address = address;
        this.op = op;
        this.pc = pc;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault, pc, address, instret);
        }
        try {
            long originalValue = ctx.readLong(address);

            // Use the zero register as a temporary
            frame.setLongStatic(0, originalValue);
            long opResult = op.executeLong(frame);

            ctx.writeLong(address, opResult);
            return originalValue;
        } catch (RiscvTrapException trap) {
            if (trap.cause == ExceptionCause.LoadPageFault || trap.cause == ExceptionCause.StoreAmoPageFault) {
                throw new RiscvTrapException(ExceptionCause.StoreAmoPageFault, pc, address, instret);
            }
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault, pc, address, instret);
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("AmoDoubleNode{");
        sb.append("address=").append(address);
        sb.append(", op=").append(op);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
