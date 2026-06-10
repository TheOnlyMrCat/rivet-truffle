package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.IOException;
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

        // Reset the architectural state
        ctx.privilegedState.reset();
        for (int i = 0; i < 32; i++) {
            frame.setLongStatic(i, 0);
        }

        // Load the startup program into memory
        for (long startingOffset : initialMemory.keySet()) {
            ByteSequence segment = initialMemory.get(startingOffset);
            for (int i = 0; i < segment.length(); i++) {
                ctx.writeByte(startingOffset + i, segment.byteAt(i));
            }
        }

        // Load the device tree into memory
        try (var deviceTree = getClass().getResourceAsStream("../rivet-truffle.dtb")) {
            byte[] bytes = deviceTree.readAllBytes();
            long baseAddr = 0xbffff000L;
            for (int i = 0; i < bytes.length; i++) {
                ctx.writeByte(baseAddr + i, bytes[i]);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Write an OpenSBI struct fw_dynamic_info
        long baseAddr = 0xbfffd000L;
        ctx.writeLong(baseAddr, 0x4942534f); // Magic value ('OSBI' in little endian)
        ctx.writeLong(baseAddr + 8L, 0x2); // Info version
        ctx.writeLong(baseAddr + 16L, 0x8800_0000L); // Next booting stage address
        ctx.writeLong(baseAddr + 24L, 0x0); // Next booting stage mode (U-mode)
        ctx.writeLong(baseAddr + 32L, 0x0); // OpenSBI options
        ctx.writeLong(baseAddr + 40L, 0x0); // Preferred boot hart

        // Prepare boot arguments
        frame.setLongStatic(RegisterState.A0, 0);
        frame.setLongStatic(RegisterState.A1, 0xbffff000L);
        frame.setLongStatic(RegisterState.A2, 0xbfffd000L);
    }
}
