package au.mrcat.rivet.runtime;

import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.riscv.ExceptionCause;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvTrapException extends ControlFlowException {
    private RegisterState state;
    public final ExceptionCause cause;
    private long pc;
    private long tval = 0;
    private short instret = 0;

    public RiscvTrapException(ExceptionCause cause) {
        this.cause = cause;
    }

    public RiscvTrapException(ExceptionCause cause, long pc) {
        this.cause = cause;
        this.pc = pc;
    }

    public RiscvTrapException(ExceptionCause cause, long pc, long tval) {
        this.cause = cause;
        this.pc = pc;
        this.tval = tval;
    }

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

    public RegisterState getState() {
        return state;
    }

    public void setState(RegisterState state) {
        this.state = state;
    }

    @Override
    public String getMessage() {
        return String.format("%s at %x", cause.name(), pc);
    }
}
