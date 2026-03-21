package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SetRegisterNode extends RivetNode {
    private final int register;
    @Child RivetOpNode op;

    public SetRegisterNode(int register, RivetOpNode op) {
        this.register = register;
        this.op = op;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        currentLanguageContext().setRegister(register, op.executeLong(frame));
        return null;
    }
}
