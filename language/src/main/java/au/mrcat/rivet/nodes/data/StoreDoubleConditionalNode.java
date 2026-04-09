package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreDoubleConditionalNode extends RivetNode {
    @Child RivetOpNode address;
    @Child RivetOpNode src;
    final int rd;

    public StoreDoubleConditionalNode(RivetOpNode address, RivetOpNode src) {
        this(address, src, 0);
    }

    public StoreDoubleConditionalNode(RivetOpNode address, RivetOpNode src, int rd) {
        this.address = address;
        this.src = src;
        this.rd = rd;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        // We're executing outside a WithLockedWordNode; always fail
        address.executeLong(frame);
        src.executeLong(frame);
        if (rd != 0) {
            currentLanguageContext().setRegister(rd, 1);
        }
    }
}
