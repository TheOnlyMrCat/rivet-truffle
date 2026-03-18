package au.mrcat.rivet.runtime;

import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvExitException extends ControlFlowException {
    public final long exitCode;

    public RiscvExitException(long exitCode) {
        this.exitCode = exitCode;
    }
}
