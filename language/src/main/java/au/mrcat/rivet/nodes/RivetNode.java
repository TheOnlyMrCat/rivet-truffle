package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class RivetNode extends Node {
    protected final RivetContext currentLanguageContext() {
        return RivetContext.get(this);
    }

    public abstract Object execute(VirtualFrame frame);
}
