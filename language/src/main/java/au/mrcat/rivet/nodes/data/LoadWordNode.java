package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadWordNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final int offset;
    private final long pc;

    public LoadWordNode(RivetOpNode address, int offset, long pc) {
        this.address = address;
        this.offset = offset;
        this.pc = pc;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        try {
            return ctx.readIntMisaligned(address.executeLong(frame) + offset);
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            return pc;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("LoadWordNode{");
        sb.append("address=").append(address);
        sb.append(", offset=").append(offset);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
