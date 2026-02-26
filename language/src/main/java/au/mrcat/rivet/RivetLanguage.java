package au.mrcat.rivet;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.ExecutableNode;

public final class RivetLanguage extends TruffleLanguage<RivetContext> {
    @Override
    protected RivetContext createContext(Env env) {
        return new RivetContext();
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return super.parse(request);
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
        return super.parse(request);
    }
}
