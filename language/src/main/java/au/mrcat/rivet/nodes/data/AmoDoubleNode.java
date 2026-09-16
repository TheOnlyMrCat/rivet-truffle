package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class AmoDoubleNode extends RivetOpNode {
    @Child RivetOpNode address;
    @Child RivetOpNode op;
    private final long pcOffset;
    private final short instret;

    public AmoDoubleNode(RivetOpNode address, RivetOpNode op, long pcOffset, short instret) {
        this.address = address;
        this.op = op;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame, basePc);
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault, basePc + pcOffset, address, instret);
        }
        try {
            long originalValue = ctx.readLong(address);

            // Use the zero register as a temporary
            frame.setLongStatic(0, originalValue);
            long opResult = op.executeLong(frame, basePc);

            ctx.writeLong(address, opResult);
            return originalValue;
        } catch (RiscvTrapException trap) {
            if (trap.cause == ExceptionCause.LoadPageFault || trap.cause == ExceptionCause.StoreAmoPageFault) {
                throw new RiscvTrapException(ExceptionCause.StoreAmoPageFault, basePc + pcOffset, address, instret);
            }
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault, basePc + pcOffset, address, instret);
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("AmoDoubleNode{");
        sb.append("address=").append(address);
        sb.append(", op=").append(op);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
