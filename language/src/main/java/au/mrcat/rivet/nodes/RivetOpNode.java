package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetOpNode extends RivetNode {
    public abstract long executeLong(VirtualFrame frame);

    @Override
    public Object execute(VirtualFrame frame) {
        return executeLong(frame);
    }
}
