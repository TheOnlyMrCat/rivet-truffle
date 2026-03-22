package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.RiscvExitException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class EnvironmentCallNode extends RivetNode {
    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        switch ((int) ctx.getRegister(RegisterState.A7)) {
            case 93 -> throw new RiscvExitException(ctx.getRegister(RegisterState.A0));
            default -> throw new RuntimeException(String.format("Unimplemented syscall: %d", ctx.getRegister(RegisterState.A7)));
        }
    }
}
