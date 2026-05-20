package au.mrcat.rivet.runtime;

import au.mrcat.rivet.riscv.ExceptionCause;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvTrapException extends ControlFlowException {
    private MaterializedFrame frame;
    public final ExceptionCause cause;
    private long pc;

    public RiscvTrapException(ExceptionCause cause) {
        this.cause = cause;
    }

    public RiscvTrapException(ExceptionCause cause, long pc) {
        this.cause = cause;
        this.pc = pc;
    }

    public long getPc() {
        return pc;
    }

    public void setPc(long pc) {
        this.pc = pc;
    }
    public MaterializedFrame getFrame() {
        return frame;
    }

    public void setFrame(MaterializedFrame frame) {
        this.frame = frame;
    }

    @Override
    public String getMessage() {
        return String.format("%s at %x", cause.name(), pc);
    }
}
