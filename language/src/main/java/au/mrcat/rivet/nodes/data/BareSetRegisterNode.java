package au.mrcat.rivet.nodes.data;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public class BareSetRegisterNode extends Node {
    private final int register;

    public static final int TEMP_REGISTER = 0;

    public BareSetRegisterNode(int register) {
        this.register = register;
    }

    public void executeVoid(VirtualFrame frame, long value) {
        frame.setLongStatic(register, value);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("x");
        sb.append(register);
        sb.append(" <- ?");
        return sb.toString();
    }
}
