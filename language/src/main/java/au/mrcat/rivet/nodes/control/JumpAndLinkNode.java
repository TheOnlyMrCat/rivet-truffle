package au.mrcat.rivet.nodes.control;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class JumpAndLinkNode extends RivetDivergentNode {
    @Child RivetOpNode targetPc;
    @Child RivetNode link;

    public JumpAndLinkNode(RivetOpNode targetPc, RivetNode link) {
        this.targetPc = targetPc;
        this.link = link;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        long targetPc = this.targetPc.executeLong(frame);
        link.executeVoid(frame);
        throw new RiscvJumpException(targetPc & ~0b1);
    }
}
