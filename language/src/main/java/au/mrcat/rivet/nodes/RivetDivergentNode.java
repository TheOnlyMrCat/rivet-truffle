package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetDivergentNode extends RivetInstructionNode {
    public abstract int executeDivergent(VirtualFrame frame, long basePc);
    public abstract Long[] callTargetContinuations();

    @Override
    public void executeVoid(VirtualFrame frame, long basePc) {
        executeDivergent(frame, basePc);
    }
}
