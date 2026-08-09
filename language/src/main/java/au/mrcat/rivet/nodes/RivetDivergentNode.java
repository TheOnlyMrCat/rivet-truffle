package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetDivergentNode extends RivetNode {
    public abstract int executeDivergent(VirtualFrame frame);
    public abstract Long[] callTargetContinuations();

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeDivergent(frame);
    }
}
