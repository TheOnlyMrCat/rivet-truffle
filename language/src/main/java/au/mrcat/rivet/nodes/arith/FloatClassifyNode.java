package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatClassifyNode extends RivetOpNode {
    @Child RivetOpNode operand;
    private final FloatWidth width;

    public FloatClassifyNode(RivetOpNode operand, FloatWidth width) {
        this.operand = operand;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.fclass_sf32((int) operand.executeLong(frame, basePc, priv));
            case Double -> ctx.ffi.fclass_sf64(operand.executeLong(frame, basePc, priv));
        };
    }
}
