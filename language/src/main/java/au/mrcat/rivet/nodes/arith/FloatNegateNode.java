package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatNegateNode extends RivetOpNode {
    @Child RivetOpNode operand;
    private final FloatWidth width;

    public FloatNegateNode(RivetOpNode operand, FloatWidth width) {
        this.operand = operand;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        long value = operand.executeLong(frame, basePc, priv);
        return switch (width) {
            case Single, RawSingle -> value ^ (1L << 31);
            case Double -> value ^ (1L << 63);
        };
    }
}
