package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class RivetNode extends Node {
    protected final RivetContext currentLanguageContext() {
        return RivetContext.get(this);
    }

    public abstract void executeVoid(VirtualFrame frame);
}
