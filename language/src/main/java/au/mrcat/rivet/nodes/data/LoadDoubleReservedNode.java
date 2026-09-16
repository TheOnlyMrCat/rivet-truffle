package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadDoubleReservedNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final long pcOffset;
    private final short instret;

    public LoadDoubleReservedNode(RivetOpNode address, long pcOffset, short instret) {
        this.address = address;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc) {
        var ctx = currentLanguageContext();
        long virtualAddress = address.executeLong(frame, basePc);

        try {
            ctx.reserveLongAddress(virtualAddress);
            return ctx.readLong(virtualAddress);
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(virtualAddress);
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("LoadDoubleReservedNode{");
        sb.append("address=").append(address);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
