package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.riscv.RoundingMode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public class FloatToUnsignedIntNode extends RivetOpNode {
    @Child RivetOpNode operand;
    private final RoundingMode roundingMode;
    private final FloatWidth width;

    public FloatToUnsignedIntNode(RivetOpNode operand, RoundingMode roundingMode, FloatWidth width) {
        this.operand = operand;
        this.roundingMode = roundingMode;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.cvt_sf32_u32((int) operand.executeLong(frame, basePc, priv), roundingMode);
            case Double -> ctx.ffi.cvt_sf64_u32(operand.executeLong(frame, basePc, priv), roundingMode);
        };
    }
}
