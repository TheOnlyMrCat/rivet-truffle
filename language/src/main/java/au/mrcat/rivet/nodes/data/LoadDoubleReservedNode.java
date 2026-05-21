package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadDoubleReservedNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final long pc;

    public LoadDoubleReservedNode(RivetOpNode address, long pc) {
        this.address = address;
        this.pc = pc;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        long virtualAddress = address.executeLong(frame);

        try {
            ctx.reserveAddress(virtualAddress);
            return ctx.readLong(virtualAddress);
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            trap.setTval(virtualAddress);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("LoadDoubleReservedNode{");
        sb.append("address=").append(address);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
