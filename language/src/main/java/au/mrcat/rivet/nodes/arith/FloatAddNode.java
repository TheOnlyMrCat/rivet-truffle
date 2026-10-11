package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.riscv.RoundingMode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatAddNode extends RivetOpNode {
    @Child RivetOpNode addend1;
    @Child RivetOpNode addend2;
    private final RoundingMode roundingMode;
    private final FloatWidth width;

    public FloatAddNode(RivetOpNode addend1, RivetOpNode addend2, RoundingMode roundingMode, FloatWidth width) {
        this.addend1 = addend1;
        this.addend2 = addend2;
        this.roundingMode = roundingMode;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.add_sf32((int) addend1.executeLong(frame, basePc, priv), (int) addend2.executeLong(frame, basePc, priv), roundingMode);
            case Double -> ctx.ffi.add_sf64(addend1.executeLong(frame, basePc, priv), addend2.executeLong(frame, basePc, priv), roundingMode);
        };
    }
}
