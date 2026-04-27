package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadWordReservedNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final long pc;

    public LoadWordReservedNode(RivetOpNode address, long pc) {
        this.address = address;
        this.pc = pc;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        try {
            long address = this.address.executeLong(frame);
            ctx.reserveAddress(address);
            return ctx.readInt(address);
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
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
