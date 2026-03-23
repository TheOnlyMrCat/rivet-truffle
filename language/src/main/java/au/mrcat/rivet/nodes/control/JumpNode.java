package au.mrcat.rivet.nodes.control;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class JumpNode extends RivetDivergentNode {
    @Child RivetOpNode targetPc;

    public JumpNode(RivetOpNode targetPc) {
        this.targetPc = targetPc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new RiscvJumpException(targetPc.executeLong(frame) & ~0b1);
    }
}
