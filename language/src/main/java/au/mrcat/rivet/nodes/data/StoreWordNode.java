package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetInstructionNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreWordNode extends RivetInstructionNode {
    @Child RivetOpNode address;
    private final long offset;
    @Child RivetOpNode value;
    private final long pcOffset;
    private final short instret;

    public StoreWordNode(RivetOpNode address, long offset, RivetOpNode value, long pcOffset, short instret) {
        this.address = address;
        this.offset = offset;
        this.value = value;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc) {
        var ctx = currentLanguageContext();
        long virtualAddress = address.executeLong(frame, basePc) + offset;
        try {
            ctx.writeInt(virtualAddress, (int) value.executeLong(frame, basePc));
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(virtualAddress);
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("StoreWordNode{");
        sb.append("address=").append(address);
        sb.append(", offset=").append(offset);
        sb.append(", value=").append(value);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
