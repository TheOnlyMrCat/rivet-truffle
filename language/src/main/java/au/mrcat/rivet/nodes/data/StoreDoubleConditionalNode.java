package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreDoubleConditionalNode extends RivetOpNode {
    @Child RivetOpNode address;
    @Child RivetOpNode src;
    private final long pcOffset;
    private final short instret;

    public StoreDoubleConditionalNode(RivetOpNode address, RivetOpNode src, long pcOffset, short instret) {
        this.address = address;
        this.src = src;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc) {
        var ctx = currentLanguageContext();

        long virtualAddress = address.executeLong(frame, basePc);
        long value = src.executeLong(frame, basePc);
        try {
            boolean succeeded = ctx.writeLongConditional(virtualAddress, value);
            return succeeded ? 0 : 1;
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
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
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
