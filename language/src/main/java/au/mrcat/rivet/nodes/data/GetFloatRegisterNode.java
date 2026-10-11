package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GetFloatRegisterNode extends RivetOpNode {
    private final int register;
    private final FloatWidth width;

    public GetFloatRegisterNode(int register, FloatWidth width) {
        this.register = register;
        this.width = width;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        long value = frame.getLongStatic(32 + register);
        if (width == FloatWidth.Single && (value & 0xffff_ffff_0000_0000L) != 0xffff_ffff_0000_0000L) {
            return 0x7fc00000L;
        }
        if (width != FloatWidth.Double) {
            value &= 0xffff_ffffL;
        }
        return value;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("f");
        sb.append(register);
        return sb.toString();
    }
}
