package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreDoubleConditionalNode extends RivetOpNode {
    @Child RivetOpNode address;
    @Child RivetOpNode src;

    public StoreDoubleConditionalNode(RivetOpNode address, RivetOpNode src) {
        this.address = address;
        this.src = src;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        long value = src.executeLong(frame);
        boolean succeeded = ctx.writeLongConditional(address, value);
        return succeeded ? 0 : 1;
    }
}
