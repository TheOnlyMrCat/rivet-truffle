package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreWordNode extends RivetNode {
    @Child RivetOpNode address;
    private final long offset;
    @Child RivetOpNode value;

    public StoreWordNode(RivetOpNode address, long offset, RivetOpNode value) {
        this.address = address;
        this.offset = offset;
        this.value = value;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        ctx.writeIntMisaligned(address.executeLong(frame) + offset, (int) value.executeLong(frame));
    }
}
