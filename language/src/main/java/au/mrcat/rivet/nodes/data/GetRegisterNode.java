package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GetRegisterNode extends RivetOpNode {
    private final int register;

    public static final GetRegisterNode TEMP_REGISTER = new GetRegisterNode(SetRegisterNode.TEMP_REGISTER);

    private GetRegisterNode(int register) {
        this.register = register;
    }

    public static RivetOpNode create(int register) {
        if (register == 0) {
            return new ConstantNode(0);
        } else {
            return new GetRegisterNode(register);
        }
    }

    public static RivetOpNode createIncludingTemp(int register) {
        return new GetRegisterNode(register);
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc) {
        return frame.getLongStatic(register);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("x");
        sb.append(register);
        return sb.toString();
    }
}
