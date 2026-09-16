package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetOpNode extends RivetInstructionNode {
    public abstract long executeLong(VirtualFrame frame, long basePc);

    @Override
    public void executeVoid(VirtualFrame frame, long basePc) {
        executeLong(frame, basePc);
    }
}
