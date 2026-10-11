package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.riscv.RoundingMode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatSubNode extends RivetOpNode {
    @Child RivetOpNode minuend;
    @Child RivetOpNode subtrahend;
    private final RoundingMode roundingMode;
    private final FloatWidth width;

    public FloatSubNode(RivetOpNode minuend, RivetOpNode subtrahend, RoundingMode roundingMode, FloatWidth width) {
        this.minuend = minuend;
        this.subtrahend = subtrahend;
        this.roundingMode = roundingMode;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.sub_sf32((int) minuend.executeLong(frame, basePc, priv), (int) subtrahend.executeLong(frame, basePc, priv), roundingMode);
            case Double -> ctx.ffi.sub_sf64(minuend.executeLong(frame, basePc, priv), subtrahend.executeLong(frame, basePc, priv), roundingMode);
        };
    }
}
