package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetInstructionNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.FloatWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SetFloatRegisterNode extends RivetInstructionNode {
    private final int register;
    private final FloatWidth width;
    @Child RivetOpNode op;

    public SetFloatRegisterNode(int register, FloatWidth width, RivetOpNode op) {
        this.register = register;
        this.width = width;
        this.op = op;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        ctx.privilegedState.markFpuDirty();
        long value = op.executeLong(frame, basePc, priv);
        if (width == FloatWidth.Single) {
            // NaN-box single-precision values
            value |= 0xffff_ffff_0000_0000L;
        }
        frame.setLongStatic(32 + register, value);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("f");
        sb.append(register);
        sb.append(" <- ").append(op);
        return sb.toString();
    }
}
