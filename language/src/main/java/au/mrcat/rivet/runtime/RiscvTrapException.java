package au.mrcat.rivet.runtime;

import au.mrcat.rivet.riscv.ExceptionCause;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvTrapException extends ControlFlowException {
    private MaterializedFrame frame;
    public final ExceptionCause cause;
    private long pc;
    private long tval = 0;
    private short instret = 0;

    @CompilerDirectives.TruffleBoundary
    public RiscvTrapException(ExceptionCause cause) {
        this.cause = cause;
    }

    @CompilerDirectives.TruffleBoundary
    public RiscvTrapException(ExceptionCause cause, long pc) {
        this.cause = cause;
        this.pc = pc;
    }

    @CompilerDirectives.TruffleBoundary
    public RiscvTrapException(ExceptionCause cause, long pc, long tval) {
        this.cause = cause;
        this.pc = pc;
        this.tval = tval;
    }

    @CompilerDirectives.TruffleBoundary
    public RiscvTrapException(ExceptionCause cause, long pc, long tval, short instret) {
        this.cause = cause;
        this.pc = pc;
        this.tval = tval;
        this.instret = instret;
    }

    public long getPc() {
        return pc;
    }

    public void setPc(long pc) {
        this.pc = pc;
    }

    public long getTval() {
        return tval;
    }

    public void setTval(long tval) {
        this.tval = tval;
    }

    public short getInstret() {
        return instret;
    }

    public void setInstret(short instret) {
        this.instret = instret;
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
