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
        CallTargetKey(InstructionParseRequest request) {
            this(request.pc(), request.priv());
        }
    }

    private final RivetLanguage language;
    @Child private RivetStartupNode startupNode;

    @Children private DirectCallNode[] callTargets;
    private int callTargetsLength;
    private final Map<CallTargetKey, Integer> callTargetPcs;
    private final Set<CallTargetKey> invalidatedCallTargets;
    private final ArrayList<Integer> freeCallTargets;

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
        freeCallTargets = new ArrayList<>();
    }

    private int addCallTarget(CallTarget callTarget) {
        int newIndex;
        if (freeCallTargets.isEmpty()) {
            if (callTargetsLength == callTargets.length) {
                callTargets = Arrays.copyOf(callTargets, callTargets.length * 2);
            }
            newIndex = callTargetsLength++;
        } else {
            newIndex = freeCallTargets.removeLast();
        }
        if (callTargets[newIndex] != null) {
            throw new IllegalStateException("Attempted to override existing call target");
        }
        callTargets[newIndex] = DirectCallNode.create(callTarget);
        return newIndex;
    }

    private void invalidateCallTarget(CallTargetKey key) {
        var callTargetIdx = callTargetPcs.remove(key);
        if (callTargetIdx != null) {
            callTargets[callTargetIdx] = null;
            freeCallTargets.add(callTargetIdx);
        }
    }

    private void invalidateDirtyCallTargets() {
        for (var invalidated : invalidatedCallTargets) {
            invalidateCallTarget(invalidated);
        }
        invalidatedCallTargets.clear();
    }

    public void invalidateCallTarget(InstructionParseRequest request) {
        invalidateCallTarget(new CallTargetKey(request));
    }

    public void markDirtyCallTarget(InstructionParseRequest request) {
        invalidatedCallTargets.add(new CallTargetKey(request));
    }

    private void clearCallTargets() {
        callTargets = new DirectCallNode[callTargetsLength];
        callTargetPcs.clear();
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
                            var parseRequest = new InstructionParseRequest(key.pc, key.priv, this);
                            root = RivetParser.extractCallTarget(language, ctx, parseRequest);
                            ctx.privilegedState.registerVirtualMemoryParseRequest(parseRequest);
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
                        invalidateDirtyCallTargets();
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
