package au.mrcat.rivet.runtime;

import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvIndirectJumpException extends ControlFlowException {
    private final long targetPc;
    public RiscvIndirectJumpException(long targetPc) {
        this.targetPc = targetPc;
    }

    public long getTargetPc() {
        return targetPc;
    }
}
