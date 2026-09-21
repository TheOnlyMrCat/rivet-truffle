package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class PcOffsetNode extends RivetOpNode {
    private final long offset;

    public PcOffsetNode(long offset) {
        this.offset = offset;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        return basePc + offset;
    }

    public long getOffset() {
        return offset;
    }
}
