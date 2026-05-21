package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreDoubleConditionalNode extends RivetOpNode {
    @Child RivetOpNode address;
    @Child RivetOpNode src;
    private final long pc;
    private final short instret;

    public StoreDoubleConditionalNode(RivetOpNode address, RivetOpNode src, long pc, short instret) {
        this.address = address;
        this.src = src;
        this.pc = pc;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long virtualAddress = address.executeLong(frame);
        long value = src.executeLong(frame);
        try {
            boolean succeeded = ctx.writeLongConditional(virtualAddress, value);
            return succeeded ? 0 : 1;
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            trap.setTval(virtualAddress);
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("StoreDoubleConditionalNode{");
        sb.append("address=").append(address);
        sb.append(", src=").append(src);
        sb.append(", pc=").append(pc);
        sb.append('}');
        return sb.toString();
    }
}
