package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.riscv.RoundingMode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatFromLongNode extends RivetOpNode {
    @Child RivetOpNode operand;
    private final RoundingMode roundingMode;
    private final FloatWidth width;

    public FloatFromLongNode(RivetOpNode operand, RoundingMode roundingMode, FloatWidth width) {
        this.operand = operand;
        this.roundingMode = roundingMode;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.cvt_i64_sf32(operand.executeLong(frame, basePc, priv), roundingMode);
            case Double -> ctx.ffi.cvt_i64_sf64(operand.executeLong(frame, basePc, priv), roundingMode);
        };
    }
}
