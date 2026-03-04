package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import com.oracle.truffle.api.nodes.Node;

public class RivetNode extends Node {
    protected final RivetContext currentLanguageContext() {
        return RivetContext.get(this);
    }
}
