package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetOpNode extends RivetNode {
    public abstract long executeLong(VirtualFrame frame);

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeLong(frame);
    }
}
