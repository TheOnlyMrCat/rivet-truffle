package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreByteNode extends RivetNode {
    @Child RivetOpNode address;
    private final long offset;
    @Child RivetOpNode value;
    private final long pc;

    public StoreByteNode(RivetOpNode address, long offset, RivetOpNode value, long pc) {
        this.address = address;
        this.offset = offset;
        this.value = value;
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        try {
            ctx.writeByte(address.executeLong(frame) + offset, (byte) value.executeLong(frame));
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            throw trap;
        }
    }
}
