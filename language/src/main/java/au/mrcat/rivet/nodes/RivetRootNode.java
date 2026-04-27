package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.parser.RivetParser;
import au.mrcat.rivet.runtime.RiscvExitException;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class RivetRootNode extends RootNode {
    private final RivetLanguage language;
    @Child private RivetStartupNode startupNode;
    private CallTarget callTarget;
//    @Children RivetBasicBlockNode[] basicBlockNodes;
//    long[] pcOffsets;

    public RivetRootNode(RivetLanguage language, RivetStartupNode startupNode) {
        var frameDescriptor = FrameDescriptor.newBuilder();
        frameDescriptor.useSlotKinds(false);
        frameDescriptor.addSlots(32);
        super(language, frameDescriptor.build());

        this.language = language;
        this.startupNode = startupNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        startupNode.executeVoid(frame);
        long pc = startupNode.getStartingPc();

        for (int i = 0; i < 32; i++) {
            frame.setLongStatic(i, 0);
        }

        try {
            while (true) {
                callTarget = RivetParser.extractCallTarget(language, RivetContext.get(this), pc).getCallTarget();
                var returnFrame = (MaterializedFrame) callTarget.call(null, frame.materialize(), pc);
                pc = returnFrame.getLongStatic(0);
                for (int i = 1; i < 32; i++) {
                    frame.setLongStatic(i, returnFrame.getLongStatic(i));
                }
            }
        } catch (RiscvExitException exit) {
            return exit.exitCode;
        }
    }
}
