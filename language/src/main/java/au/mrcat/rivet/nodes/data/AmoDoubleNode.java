package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class AmoDoubleNode extends RivetOpNode {
    @Child RivetOpNode address;
    @Child RivetOpNode op;

    public AmoDoubleNode(RivetOpNode address, RivetOpNode op) {
        this.address = address;
        this.op = op;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        long originalValue = ctx.readLong(address);

        // Use the zero register as a temporary
        ctx.setRegister(0, originalValue);
        long opResult = op.executeLong(frame);

        ctx.writeLong(address, opResult);
        return originalValue;
    }
}
