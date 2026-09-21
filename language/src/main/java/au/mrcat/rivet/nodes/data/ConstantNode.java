package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ConstantNode extends RivetOpNode {
    private final long value;

    public ConstantNode(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        return value;
    }

    @Override
    public String toString() {
        return Long.toString(value, 16);
    }
}
