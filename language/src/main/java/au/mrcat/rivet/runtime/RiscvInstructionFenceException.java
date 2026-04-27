package au.mrcat.rivet.runtime;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvInstructionFenceException extends ControlFlowException {
    private MaterializedFrame frame;
    private final long nextPc;

    public RiscvInstructionFenceException(long nextPc) {
        this.nextPc = nextPc;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    public void setFrame(MaterializedFrame frame) {
        this.frame = frame;
    }

    public long getNextPc() {
        return nextPc;
    }
}
