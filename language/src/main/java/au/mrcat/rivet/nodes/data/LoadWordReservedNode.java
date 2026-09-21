package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadWordReservedNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final long pcOffset;
    private final short instret;

    public LoadWordReservedNode(RivetOpNode address, long pcOffset, short instret) {
        this.address = address;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        long virtualAddress = address.executeLong(frame, basePc, priv);

        try {
            if ((virtualAddress & 0b11) != 0) {
                throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
            }
            long physicalAddress = priv.translateReadAddress(virtualAddress, ctx);
            ctx.physicalMemory.reserveIntAddress(physicalAddress);
            return ctx.physicalMemory.readInt(physicalAddress, priv.readAccessType());
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(virtualAddress);
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("LoadWordReservedNode{");
        sb.append("address=").append(address);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
