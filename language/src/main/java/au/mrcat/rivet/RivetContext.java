package au.mrcat.rivet;

import au.mrcat.rivet.riscv.*;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

public class RivetContext {
    private static final TruffleLanguage.ContextReference<RivetContext> REF = TruffleLanguage.ContextReference.create(RivetLanguage.class);

    public static RivetContext get(Node node) {
        return REF.get(node);
    }

    public final TruffleLanguage.Env env;
    public final PrivilegedState privilegedState;
    public final PhysicalMemory physicalMemory;
    public final RivetFfi ffi;

    public RivetContext(TruffleLanguage.Env env) {
        this.env = env;
        privilegedState = new PrivilegedState(this);
        physicalMemory = new PhysicalMemory(this);
        ffi = new RivetFfi(this);
    }
}
