package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreWordNode extends RivetNode {
    @Child RivetOpNode address;
    private final long offset;
    @Child RivetOpNode value;
    private final long pc;

    public StoreWordNode(RivetOpNode address, long offset, RivetOpNode value, long pc) {
        this.address = address;
        this.offset = offset;
        this.value = value;
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        try {
            ctx.writeIntMisaligned(address.executeLong(frame) + offset, (int) value.executeLong(frame));
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("StoreWordNode{");
        sb.append("address=").append(address);
        sb.append(", offset=").append(offset);
        sb.append(", value=").append(value);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
