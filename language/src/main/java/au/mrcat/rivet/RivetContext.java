package au.mrcat.rivet;

import au.mrcat.rivet.riscv.RegisterState;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

public class RivetContext {
    private static final TruffleLanguage.ContextReference<RivetContext> REF = TruffleLanguage.ContextReference.create(RivetLanguage.class);

    public static RivetContext get(Node node) {
        return REF.get(node);
    }

    public final RegisterState registerState;

    public RivetContext() {
        registerState = new RegisterState();
    }

    public long getRegister(int r) {
        return registerState.getRegister(r);
    }

    public void setRegister(int r, long value) {
        registerState.setRegister(r, value);
    }

    public void dumpRegisterState() {
        registerState.dumpRegisterState();
    }
}
