package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.polyglot.io.ByteSequence;

public class RiscvStartupNode extends RivetNode {
    private final ByteSequence initialMemory;

    public RiscvStartupNode(ByteSequence initialMemory) {
        this.initialMemory = initialMemory;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        RivetContext ctx = RivetContext.get(this);
        for (int i = 0; i < initialMemory.length(); i++) {
            ctx.writeByte(0x8000_0000L + i, initialMemory.byteAt(i));
        }
        throw new RiscvJumpException(0x8000_0000L);
    }
}
