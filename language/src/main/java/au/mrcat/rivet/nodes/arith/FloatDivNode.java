package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.riscv.RoundingMode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatDivNode extends RivetOpNode {
    @Child RivetOpNode dividend;
    @Child RivetOpNode divisor;
    private final RoundingMode roundingMode;
    private final FloatWidth width;

    public FloatDivNode(RivetOpNode dividend, RivetOpNode divisor, RoundingMode roundingMode, FloatWidth width) {
        this.dividend = dividend;
        this.divisor = divisor;
        this.roundingMode = roundingMode;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.div_sf32((int) dividend.executeLong(frame, basePc, priv), (int) divisor.executeLong(frame, basePc, priv), roundingMode);
            case Double -> ctx.ffi.div_sf64(dividend.executeLong(frame, basePc, priv), divisor.executeLong(frame, basePc, priv), roundingMode);
        };
    }
}
