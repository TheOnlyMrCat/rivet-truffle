package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadWordReservedNode extends RivetOpNode {
    @Child RivetOpNode address;

    public LoadWordReservedNode(RivetOpNode address) {
        this.address = address;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        ctx.reserveAddress(address);
        return ctx.readInt(address);
    }
}
