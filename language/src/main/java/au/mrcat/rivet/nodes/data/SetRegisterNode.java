package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetInstructionNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SetRegisterNode extends RivetInstructionNode {
    private final int register;
    @Child RivetOpNode op;

    public static final int TEMP_REGISTER = 0;

    public SetRegisterNode(int register, RivetOpNode op) {
        this.register = register;
        this.op = op;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc) {
        frame.setLongStatic(register, op.executeLong(frame, basePc));
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("x");
        sb.append(register);
        sb.append(" <- ").append(op);
        return sb.toString();
    }
}
