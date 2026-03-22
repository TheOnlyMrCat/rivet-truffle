package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreHalfNode extends RivetNode {
    @Child RivetOpNode address;
    private final long offset;
    @Child RivetOpNode value;

    public StoreHalfNode(RivetOpNode address, long offset, RivetOpNode value) {
        this.address = address;
        this.offset = offset;
        this.value = value;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        ctx.writeShortMisaligned(address.executeLong(frame) + offset, (short) value.executeLong(frame));
    }
}
