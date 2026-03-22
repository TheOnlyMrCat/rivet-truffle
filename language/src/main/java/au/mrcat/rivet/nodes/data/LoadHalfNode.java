package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadHalfNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final int offset;

    public LoadHalfNode(RivetOpNode address, int offset) {
        this.address = address;
        this.offset = offset;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        return ctx.readShortMisaligned(address.executeLong(frame) + offset);
    }
}
