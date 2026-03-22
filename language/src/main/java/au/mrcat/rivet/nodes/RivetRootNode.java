package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.parser.RivetParser;
import au.mrcat.rivet.runtime.RiscvExitException;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class RivetRootNode extends RootNode {
    @Child private RivetNode bodyNode;

    public RivetRootNode(RivetLanguage language, FrameDescriptor frameDescriptor, RivetNode bodyNode) {
        super(language, frameDescriptor);
        this.bodyNode = bodyNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            while (true) {
                try {
                    bodyNode.executeVoid(frame);
                } catch (RiscvJumpException jump) {
                    bodyNode = RivetParser.extractBasicBlock(RivetContext.get(this), jump.targetPc);
                }
            }
        } catch (RiscvExitException exit) {
            return exit.exitCode;
        }
    }
}
