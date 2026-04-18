package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreDoubleConditionalNode extends RivetOpNode {
    @Child RivetOpNode address;
    @Child RivetOpNode src;
    private final long pc;

    public StoreDoubleConditionalNode(RivetOpNode address, RivetOpNode src, long pc) {
        this.address = address;
        this.src = src;
        this.pc = pc;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = this.address.executeLong(frame);
        long value = src.executeLong(frame);
        try {
            boolean succeeded = ctx.writeLongConditional(address, value);
            return succeeded ? 0 : 1;
        } catch (RiscvTrapException trap) {
            trap.setPc(pc);
            throw trap;
        }
    }
}
