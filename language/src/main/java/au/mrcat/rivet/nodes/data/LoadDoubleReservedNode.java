package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadDoubleReservedNode extends RivetNode {
    @Child
    RivetOpNode address;
    final int rd;

    public LoadDoubleReservedNode(RivetOpNode address, int rd) {
        this.address = address;
        this.rd = rd;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        ctx.reserveAddress(address);
        long doubleWord = ctx.readLong(address);
        if (rd != 0) {
            ctx.setRegister(rd, doubleWord);
        }
    }
}
