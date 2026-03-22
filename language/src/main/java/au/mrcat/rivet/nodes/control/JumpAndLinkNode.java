package au.mrcat.rivet.nodes.control;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class JumpAndLinkNode extends JumpNode {
    @Child RivetNode link;

    public JumpAndLinkNode(RivetOpNode targetPc, RivetNode link) {
        super(targetPc);
        this.link = link;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        link.executeVoid(frame);
        super.executeVoid(frame);
    }
}
