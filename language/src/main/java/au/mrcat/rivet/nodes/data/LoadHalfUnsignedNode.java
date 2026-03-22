package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadHalfUnsignedNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final int offset;

    public LoadHalfUnsignedNode(RivetOpNode address, int offset) {
        this.address = address;
        this.offset = offset;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        return Short.toUnsignedLong(ctx.readShortMisaligned(address.executeLong(frame) + offset));
    }
}
