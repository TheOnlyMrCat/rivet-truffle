package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreWordConditionalNode extends RivetNode {
    @Child RivetOpNode address;
    @Child RivetOpNode src;
    final int rd;

    public StoreWordConditionalNode(RivetOpNode address, RivetOpNode src) {
        this(address, src, 0);
    }

    public StoreWordConditionalNode(RivetOpNode address, RivetOpNode src, int rd) {
        this.address = address;
        this.src = src;
        this.rd = rd;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        long value = src.executeLong(frame);
        boolean succeeded = ctx.writeIntConditional(address, (int) value);
        if (rd != 0) {
            ctx.setRegister(rd, succeeded ? 0 : 1);
        }
    }
}
