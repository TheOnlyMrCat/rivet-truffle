package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatEqNode extends RivetOpNode {
    @Child RivetOpNode operand1;
    @Child RivetOpNode operand2;
    private final FloatWidth width;

    public FloatEqNode(RivetOpNode operand1, RivetOpNode operand2, FloatWidth width) {
        this.operand1 = operand1;
        this.operand2 = operand2;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        var ctx = currentLanguageContext();
        return switch (width) {
            case Single, RawSingle -> ctx.ffi.eq_quiet_sf32((int) operand1.executeLong(frame, basePc, priv), (int) operand2.executeLong(frame, basePc, priv));
            case Double -> ctx.ffi.eq_quiet_sf64(operand1.executeLong(frame, basePc, priv), operand2.executeLong(frame, basePc, priv));
        };
    }
}
