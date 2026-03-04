package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class RivetRootNode extends RootNode {
    @Child private RiscvDispatchNode bodyNode;

    public RivetRootNode(RivetLanguage language, FrameDescriptor frameDescriptor, RiscvDispatchNode bodyNode) {
        super(language, frameDescriptor);
        this.bodyNode = bodyNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        bodyNode.execute(frame);
        return true;
    }
}
