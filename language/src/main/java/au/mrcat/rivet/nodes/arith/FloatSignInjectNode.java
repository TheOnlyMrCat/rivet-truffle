package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FloatSignInjectNode extends RivetOpNode {
    @Child RivetOpNode base;
    @Child RivetOpNode sign;
    private final FloatWidth width;

    public FloatSignInjectNode(RivetOpNode base, RivetOpNode sign, FloatWidth width) {
        this.base = base;
        this.sign = sign;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        long baseValue = base.executeLong(frame, basePc, priv);
        long signValue = sign.executeLong(frame, basePc, priv);
        return switch (width) {
            case Single, RawSingle -> baseValue & ~(1L << 31) | signValue & (1L << 31);
            case Double -> baseValue & ~(1L << 63) | signValue & (1L << 63);
        };
    }
}
