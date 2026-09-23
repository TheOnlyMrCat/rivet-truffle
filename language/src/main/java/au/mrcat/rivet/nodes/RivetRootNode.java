package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.parser.RivetParser;
import au.mrcat.rivet.riscv.*;
import au.mrcat.rivet.runtime.*;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.*;

public class RivetRootNode extends RootNode {
    private record CallTargetKey(long pc, PrivilegedContext priv) {
    }

    private final RivetLanguage language;
    @Child private RivetStartupNode startupNode;

    @Children private DirectCallNode[] callTargets;
    private int callTargetsLength;
    private final Map<CallTargetKey, Integer> callTargetPcs;
    private final Set<CallTargetKey> invalidatedCallTargets;

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
        this.invalidatedCallTargets = new HashSet<>();
    }

    private int addCallTarget(CallTarget callTarget) {
        if (callTargetsLength == callTargets.length) {
            callTargets = Arrays.copyOf(callTargets, callTargets.length * 2);
        }
        callTargets[callTargetsLength] = DirectCallNode.create(callTarget);
        return callTargetsLength++;
    }

    private void clearCallTargets() {
        for (var invalidated : invalidatedCallTargets) {
            var callTargetIdx = callTargetPcs.remove(invalidated);
            if (callTargetIdx != null) {
                callTargets[callTargetIdx] = null;
            }
        }
        invalidatedCallTargets.clear();
//        callTargets = new DirectCallNode[callTargetsLength];
//        callTargetPcs.clear();
    }

    public void invalidateCallTarget(InstructionParseRequest request) {
        invalidatedCallTargets.add(new CallTargetKey(request.pc(), request.priv()));
    }

    @Override
    public Object execute(VirtualFrame frame) {
        var ctx = RivetContext.get(this);
        while (true) {
            RegisterState cpuState = startupNode.executeState(frame);
            try {
                while (true) {
                    var key = new CallTargetKey(cpuState.getPc(), ctx.privilegedState.currentContext());
                    Integer callTargetIndex = callTargetPcs.get(key);
                    if (callTargetIndex == null) {
                        CompilerDirectives.transferToInterpreter();
                        RivetCallTargetNode root;
                        try {
                            root = RivetParser.extractCallTarget(language, ctx, new InstructionParseRequest(key.pc, key.priv, this));
                        } catch (RiscvTrapException trap) {
                            cpuState.setPc(ctx.privilegedState.handleException(trap));
                            continue;
                        }
                        callTargetIndex = addCallTarget(root.getCallTarget());
                        callTargetPcs.put(key, callTargetIndex);
//                        for (long entryPc : root.getPcOffsets()) {
//                            callTargetPcs.put(entryPc, callTargetIndex);
//                        }
                    }

                    var callTarget = callTargets[callTargetIndex];
                    try {
                        cpuState = (RegisterState) callTarget.call(cpuState);
                    } catch (RiscvInstructionFenceException fence) {
                        cpuState = fence.getState();
                        ctx.privilegedState.stepPerformanceCounters(fence.getInstret());
                        cpuState.setPc(fence.getNextPc());
                        clearCallTargets();
                    } catch (RiscvTrapException trap) {
                        cpuState = trap.getState();
                        ctx.privilegedState.stepPerformanceCounters(trap.getInstret());
                        cpuState.setPc(ctx.privilegedState.handleException(trap));
                    }
                }
            } catch (RiscvExitException exit) {
                return exit.exitCode;
            } catch (RiscvRebootException _) {
                clearCallTargets();
                // Loop back to beginning
            }
        }
    }
}
