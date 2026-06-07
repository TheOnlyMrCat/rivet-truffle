package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadWordReservedNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final long pc;
    private final short instret;

    public LoadWordReservedNode(RivetOpNode address, long pc, short instret) {
        this.address = address;
        this.pc = pc;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        long virtualAddress = address.executeLong(frame);

        try {
            ctx.reserveIntAddress(virtualAddress);
            return ctx.readInt(virtualAddress);
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            trap.setTval(virtualAddress);
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("LoadWordReservedNode{");
        sb.append("address=").append(address);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
