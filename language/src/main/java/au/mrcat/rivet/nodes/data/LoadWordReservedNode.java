package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadWordReservedNode extends RivetNode {
    @Child RivetOpNode address;
    final int rd;

    public LoadWordReservedNode(RivetOpNode address, int rd) {
        this.address = address;
        this.rd = rd;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        ctx.reserveAddress(address);
        int word = ctx.readInt(address);
        if (rd != 0) {
            ctx.setRegister(rd, word);
        }
    }
}
