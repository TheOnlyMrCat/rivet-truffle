package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.riscv.RegisterState;
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
        return bodyNode.execute(frame);
    }
}
