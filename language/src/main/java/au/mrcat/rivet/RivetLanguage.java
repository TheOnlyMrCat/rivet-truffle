package au.mrcat.rivet;

import au.mrcat.rivet.nodes.RiscvStartupNode;
import au.mrcat.rivet.nodes.RivetRootNode;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.ExecutableNode;

@TruffleLanguage.Registration(id = "rv64", name = "Rivet RV64I", byteMimeTypes = "application/x-riscv")
public final class RivetLanguage extends TruffleLanguage<RivetContext> {
    @Override
    protected RivetContext createContext(Env env) {
        return new RivetContext();
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        var startupNode = new RiscvStartupNode(request.getSource().getBytes());
        return new RivetRootNode(this, FrameDescriptor.newBuilder().build(), startupNode).getCallTarget();
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
        return super.parse(request);
    }
}
