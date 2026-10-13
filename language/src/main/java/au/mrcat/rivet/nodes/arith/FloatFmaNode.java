package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.riscv.RoundingMode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatFmaNode extends RivetOpNode {
    @Child RivetOpNode multiplicand;
    @Child RivetOpNode multiplier;
    @Child RivetOpNode addend;
    private final RoundingMode roundingMode;
    private final FloatWidth width;

    public FloatFmaNode(RivetOpNode multiplicand, RivetOpNode multiplier, RivetOpNode addend, RoundingMode roundingMode, FloatWidth width) {
        this.multiplicand = multiplicand;
        this.multiplier = multiplier;
        this.addend = addend;
        this.roundingMode = roundingMode;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.fma_sf32((int) multiplicand.executeLong(frame, basePc, priv), (int) multiplier.executeLong(frame, basePc, priv), (int) addend.executeLong(frame, basePc, priv), roundingMode);
            case Double -> ctx.ffi.fma_sf64(multiplicand.executeLong(frame, basePc, priv), multiplier.executeLong(frame, basePc, priv), addend.executeLong(frame, basePc, priv), roundingMode);
        };
    }
}
