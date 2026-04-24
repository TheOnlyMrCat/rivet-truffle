package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.Map;

public class RivetStartupNode extends RivetNode {
    private final Map<Long, ByteSequence> initialMemory;

    public RivetStartupNode(Map<Long, ByteSequence> initialMemory) {
        this.initialMemory = initialMemory;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        RivetContext ctx = RivetContext.get(this);
        for (long startingOffset : initialMemory.keySet()) {
            ByteSequence segment = initialMemory.get(startingOffset);
            for (int i = 0; i < segment.length(); i++) {
                ctx.writeByte(startingOffset + i, segment.byteAt(i));
            }
        }
        throw new RiscvJumpException(0x8000_0000L);
    }
}
