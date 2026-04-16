package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.RiscvExitException;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class EnvironmentCallNode extends RivetNode {
    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        switch ((int) ctx.getRegister(RegisterState.A7)) {
            case 64 -> {
                int fd = (int) ctx.getRegister(RegisterState.A0);
                OutputStream out = switch (fd) {
                    case 1 -> ctx.env.out();
                    case 2 -> ctx.env.err();
                    default -> null;
                };
                if (out == null) {
                    // Return -EBADF
                    ctx.setRegister(RegisterState.A0, -9);
                    break;
                }

                long ptr = ctx.getRegister(RegisterState.A1);
                long size = ctx.getRegister(RegisterState.A2);
                MemorySegment bufSegment = ctx.slice(ptr, size);
                byte[] buf = bufSegment.toArray(ValueLayout.JAVA_BYTE);
                try {
                    out.write(buf);
                } catch (IOException e) {
                    // Return EIO
                    ctx.setRegister(RegisterState.A0, -5);
                    break;
                }

                // Return number of bytes written
                ctx.setRegister(RegisterState.A0, buf.length);
            }
            case 93 -> throw new RiscvExitException(ctx.getRegister(RegisterState.A0));
            default -> throw new RuntimeException(String.format("Unimplemented syscall: %d", ctx.getRegister(RegisterState.A7)));
        }
    }
}
