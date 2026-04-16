package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadDoubleReservedNode extends RivetOpNode {
    @Child RivetOpNode address;

    public LoadDoubleReservedNode(RivetOpNode address) {
        this.address = address;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        ctx.reserveAddress(address);
        return ctx.readLong(address);
    }
}
