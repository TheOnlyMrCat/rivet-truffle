package au.mrcat.rivet.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class RiscvInstructionFenceException extends ControlFlowException {
    private MaterializedFrame frame;
    private final long nextPc;
    private final short instret;

    @CompilerDirectives.TruffleBoundary
    public RiscvInstructionFenceException(long nextPc, short instret) {
        this.nextPc = nextPc;
        this.instret = instret;
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

    public short getInstret() {
        return instret;
    }
}
