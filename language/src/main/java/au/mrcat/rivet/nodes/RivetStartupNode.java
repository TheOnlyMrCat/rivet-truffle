package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.Map;

public class RivetStartupNode extends Node {
    private final Map<Long, ByteSequence> initialMemory;
    private final long startingPc;

    public RivetStartupNode(Map<Long, ByteSequence> initialMemory, long startingPc) {
        this.initialMemory = initialMemory;
        this.startingPc = startingPc;
    }

    public long getStartingPc() {
        return startingPc;
    }

    public void executeVoid(VirtualFrame frame) {
        RivetContext ctx = RivetContext.get(this);
        for (long startingOffset : initialMemory.keySet()) {
            ByteSequence segment = initialMemory.get(startingOffset);
            for (int i = 0; i < segment.length(); i++) {
                ctx.writeByte(startingOffset + i, segment.byteAt(i));
            }
        }
    }
}
