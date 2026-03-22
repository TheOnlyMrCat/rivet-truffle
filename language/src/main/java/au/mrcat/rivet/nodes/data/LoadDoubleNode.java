package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadDoubleNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final int offset;

    public LoadDoubleNode(RivetOpNode address, int offset) {
        this.address = address;
        this.offset = offset;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        return ctx.readLongMisaligned(address.executeLong(frame) + offset);
    }
}
