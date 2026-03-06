package au.mrcat.rivet.runtime;

import au.mrcat.rivet.riscv.ExceptionCause;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvTrapException extends ControlFlowException {
    public final ExceptionCause cause;

    public RiscvTrapException(ExceptionCause cause) {
        this.cause = cause;
    }
}
