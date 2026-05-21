package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadByteUnsignedNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final int offset;
    private final long pc;

    public LoadByteUnsignedNode(RivetOpNode address, int offset, long pc) {
        this.address = address;
        this.offset = offset;
        this.pc = pc;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        long virtualAddress = address.executeLong(frame) + offset;
        try {
            return Byte.toUnsignedLong(ctx.readByte(virtualAddress));
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            trap.setTval(virtualAddress);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("LoadByteUnsignedNode{");
        sb.append("address=").append(address);
        sb.append(", offset=").append(offset);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
