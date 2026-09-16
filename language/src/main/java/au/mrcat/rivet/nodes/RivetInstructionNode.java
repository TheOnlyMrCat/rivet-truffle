package au.mrcat.rivet.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetInstructionNode extends RivetNode {
    public abstract void executeVoid(VirtualFrame frame, long basePc);
}
