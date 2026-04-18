package au.mrcat.rivet.runtime;

import au.mrcat.rivet.riscv.ExceptionCause;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvTrapException extends ControlFlowException {
    public final ExceptionCause cause;
    private long pc;

    public RiscvTrapException(ExceptionCause cause) {
        this.cause = cause;
    }

    public RiscvTrapException(ExceptionCause cause, long pc) {
        this.cause = cause;
        this.pc = pc;
    }

    public void setPc(long pc) {
        this.pc = pc;
    }

    @Override
    public String getMessage() {
        return String.format("%s at %x", cause.name(), pc);
    }
}
