package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.riscv.RoundingMode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatMulNode extends RivetOpNode {
    @Child RivetOpNode multiplicand;
    @Child RivetOpNode multiplier;
    private final RoundingMode roundingMode;
    private final FloatWidth width;

    public FloatMulNode(RivetOpNode multiplicand, RivetOpNode multiplier, RoundingMode roundingMode, FloatWidth width) {
        this.multiplicand = multiplicand;
        this.multiplier = multiplier;
        this.roundingMode = roundingMode;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.mul_sf32((int) multiplicand.executeLong(frame, basePc, priv), (int) multiplier.executeLong(frame, basePc, priv), roundingMode);
            case Double -> ctx.ffi.mul_sf64(multiplicand.executeLong(frame, basePc, priv), multiplier.executeLong(frame, basePc, priv), roundingMode);
        };
    }
}
