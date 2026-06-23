package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.parser.RivetParser;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.*;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
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
        var ctx = RivetContext.get(this);
        while (true) {
            startupNode.executeVoid(frame);
            RegisterState cpuState = new RegisterState();
            cpuState.setPc(startupNode.getStartingPc());

            try {
                while (true) {
                    Integer callTargetIndex = callTargetPcs.get(cpuState.getPc());
                    if (callTargetIndex == null) {
                        CompilerDirectives.transferToInterpreter();
                        RivetCallTargetNode root;
                        try {
                            root = RivetParser.extractCallTarget(language, RivetContext.get(this), cpuState.getPc());
                        } catch (RiscvTrapException trap) {
                            cpuState.setPc(ctx.privilegedState.handleTrap(trap));
                            continue;
                        }
                        callTargetIndex = addCallTarget(root.getCallTarget());
                        for (long entryPc : root.getPcOffsets()) {
                            callTargetPcs.put(entryPc, callTargetIndex);
                        }
                    }

                    var callTarget = callTargets[callTargetIndex];
                    try {
                        cpuState = (RegisterState) callTarget.call(cpuState);
                    } catch (RiscvInstructionFenceException fence) {
                        cpuState = fence.getState();
                        ctx.privilegedState.stepPerformanceCounters(fence.getInstret());
                        cpuState.setPc(fence.getNextPc());
                    } catch (RiscvTrapException trap) {
                        cpuState = trap.getState();
                        ctx.privilegedState.stepPerformanceCounters(trap.getInstret());
                        cpuState.setPc(ctx.privilegedState.handleTrap(trap));
                    }
                }
            } catch (RiscvExitException exit) {
                return exit.exitCode;
            } catch (RiscvRebootException _) {
                // Loop back to beginning
            }
        }
    }
}
