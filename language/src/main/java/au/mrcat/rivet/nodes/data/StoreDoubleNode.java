package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreDoubleNode extends RivetNode {
    @Child RivetOpNode address;
    private final long offset;
    @Child RivetOpNode value;
    private final long pc;

    public StoreDoubleNode(RivetOpNode address, long offset, RivetOpNode value, long pc) {
        this.address = address;
        this.offset = offset;
        this.value = value;
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        try {
            ctx.writeLongMisaligned(address.executeLong(frame) + offset, value.executeLong(frame));
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            throw trap;
        }
    }
}
