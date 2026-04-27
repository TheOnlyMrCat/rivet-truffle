package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.parser.RivetParser;
import au.mrcat.rivet.runtime.RiscvExitException;
import au.mrcat.rivet.runtime.RiscvInstructionFenceException;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RivetRootNode extends RootNode {
    private final RivetLanguage language;
    @Child private RivetStartupNode startupNode;

    @Children private DirectCallNode[] callTargets;
    private int callTargetsLength;
    private final Map<Long, Integer> callTargetPcs;

    public RivetRootNode(RivetLanguage language, RivetStartupNode startupNode) {
        var frameDescriptor = FrameDescriptor.newBuilder();
        frameDescriptor.useSlotKinds(false);
        frameDescriptor.addSlots(32);
        super(language, frameDescriptor.build());

        this.language = language;
        this.startupNode = startupNode;
        this.callTargets = new DirectCallNode[16];
        this.callTargetsLength = 0;
        this.callTargetPcs = new HashMap<>();
    }

    private int addCallTarget(CallTarget callTarget) {
        if (callTargetsLength == callTargets.length) {
            callTargets = Arrays.copyOf(callTargets, callTargets.length * 2);
        }
        callTargets[callTargetsLength] = DirectCallNode.create(callTarget);
        return callTargetsLength++;
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
                Integer callTargetIndex = callTargetPcs.get(pc);
                if (callTargetIndex == null) {
                    CompilerDirectives.transferToInterpreter();
                    var root = RivetParser.extractCallTarget(language, RivetContext.get(this), pc);
                    callTargetIndex = addCallTarget(root.getCallTarget());
                    for (long entryPc : root.getPcOffsets()) {
                        callTargetPcs.put(entryPc, callTargetIndex);
                    }
                }

                var callTarget = callTargets[callTargetIndex];
                MaterializedFrame returnFrame;
                try {
                    returnFrame = (MaterializedFrame) callTarget.call(frame.materialize(), pc);
                    pc = returnFrame.getLongStatic(0);
                } catch (RiscvInstructionFenceException fence) {
                    returnFrame = fence.getFrame();
                    pc = fence.getNextPc();;
                }
                for (int i = 1; i < 32; i++) {
                    frame.setLongStatic(i, returnFrame.getLongStatic(i));
                }
            }
        } catch (RiscvExitException exit) {
            return exit.exitCode;
        }
    }
}
