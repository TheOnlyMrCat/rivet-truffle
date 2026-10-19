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

    private int callTargetsLength;
    private final Map<CallTargetKey, DirectCallNode> callTargetPcs;
    private final Set<CallTargetKey> invalidatedCallTargets;
    private final ArrayList<Integer> freeCallTargets;

    public RivetRootNode(RivetLanguage language, RivetStartupNode startupNode) {
        var frameDescriptor = FrameDescriptor.newBuilder();
        frameDescriptor.useSlotKinds(false);
        frameDescriptor.addSlots(32);
        super(language, frameDescriptor.build());

        this.language = language;
        this.startupNode = startupNode;
        this.callTargetsLength = 0;
        this.callTargetPcs = new LinkedHashMap<>(16, .75f, true) {
            private static final int MAX_CALL_TARGETS = 10000;

            @Override
            protected boolean removeEldestEntry(Map.Entry<CallTargetKey, DirectCallNode> eldest) {
                return size() > MAX_CALL_TARGETS;
            }
        };
        this.invalidatedCallTargets = new HashSet<>();
        freeCallTargets = new ArrayList<>();
    }

    private void invalidateCallTarget(CallTargetKey key) {
        callTargetPcs.remove(key);
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
        callTargetPcs.clear();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        var ctx = RivetContext.get(this);
        while (true) {
            RegisterState cpuState = null;
            try {
                cpuState = startupNode.executeState(frame);
            } catch (RiscvTrapException e) {
                throw new RuntimeException(e);
            }
            try {
                while (true) {
                    var key = new CallTargetKey(cpuState.getPc(), ctx.privilegedState.currentContext());
                    var callTarget = callTargetPcs.get(key);
                    if (callTarget == null) {
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
                        callTarget = DirectCallNode.create(root.getCallTarget());
                        callTargetPcs.put(key, callTarget);
                    }

                    try {
                        cpuState = (RegisterState) callTarget.call(cpuState);
                    } catch (RiscvInstructionFenceException fence) {
                        cpuState = fence.getState();
                        ctx.privilegedState.stepPerformanceCounters(fence.getInstret());
                        cpuState.setPc(fence.getNextPc());
                        invalidateDirtyCallTargets();
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
