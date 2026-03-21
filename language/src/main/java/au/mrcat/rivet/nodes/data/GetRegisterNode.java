package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GetRegisterNode extends RivetOpNode {
    private final int register;

    public GetRegisterNode(int register) {
        this.register = register;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        return currentLanguageContext().getRegister(register);
    }
}
