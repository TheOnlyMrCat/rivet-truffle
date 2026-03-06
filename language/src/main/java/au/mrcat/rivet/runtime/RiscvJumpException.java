package au.mrcat.rivet.runtime;

import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvJumpException extends ControlFlowException {
    public final long targetPc;

    public RiscvJumpException(long targetPc) {
        this.targetPc = targetPc;
    }
}
