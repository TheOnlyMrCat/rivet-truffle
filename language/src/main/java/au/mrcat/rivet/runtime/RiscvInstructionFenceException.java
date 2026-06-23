package au.mrcat.rivet.runtime;

import au.mrcat.rivet.riscv.RegisterState;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvInstructionFenceException extends ControlFlowException {
    private RegisterState state;
    private final long nextPc;
    private final short instret;

    public RiscvInstructionFenceException(long nextPc, short instret) {
        this.nextPc = nextPc;
        this.instret = instret;
    }

    public RegisterState getState() {
        return state;
    }

    public void setState(RegisterState state) {
        this.state = state;
    }

    public long getNextPc() {
        return nextPc;
    }

    public short getInstret() {
        return instret;
    }
}
