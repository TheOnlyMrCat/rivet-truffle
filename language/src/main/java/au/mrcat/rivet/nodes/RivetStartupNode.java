package au.mrcat.rivet.nodes;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.riscv.RegisterState;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.IOException;
import java.util.Map;

public class RivetStartupNode extends Node {
    private final Map<Long, ByteSequence> initialMemory;
    private long startingPc;
    private long nextPhasePc;
    private boolean provideNextPhaseInfo = false;

    public RivetStartupNode(Map<Long, ByteSequence> initialMemory, long startingPc) {
        this.initialMemory = initialMemory;
        this.startingPc = startingPc;
    }

    public void loadOpensbi() {
        long baseAddress = 0xbff00000L;
        try (var opensbi = RivetContext.class.getResourceAsStream("fw_dynamic.bin")) {
            byte[] bytes = opensbi.readAllBytes();
            initialMemory.put(baseAddress, ByteSequence.create(bytes));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        nextPhasePc = startingPc;
        startingPc = baseAddress;
        provideNextPhaseInfo = true;
    }

    public RegisterState executeState(VirtualFrame frame) {
        RivetContext ctx = RivetContext.get(this);

        // Reset the architectural state
        ctx.privilegedState.reset();
        RegisterState cpuState = new RegisterState();
        cpuState.setPc(startingPc);

        // Load the startup program into memory
        for (long startingOffset : initialMemory.keySet()) {
            ByteSequence segment = initialMemory.get(startingOffset);
            for (int i = 0; i < segment.length(); i++) {
                ctx.writeByte(startingOffset + i, segment.byteAt(i));
            }
        }

        // Load the device tree into memory
        try (var deviceTree = RivetContext.class.getResourceAsStream("rivet-truffle.dtb")) {
            if (deviceTree != null) {
                byte[] bytes = deviceTree.readAllBytes();
                long baseAddr = 0xbffff000L;
                for (int i = 0; i < bytes.length; i++) {
                    ctx.writeByte(baseAddr + i, bytes[i]);
                }
            } else {
                System.err.println("Warning: no device tree loaded. Running software may not be able to find devices.");
                System.err.println("Build rivet-truffle.dtb and include it in the language module to resolve this.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (provideNextPhaseInfo) {
            // Write an OpenSBI struct fw_dynamic_info
            long baseAddr = 0xbfffd000L;
            ctx.writeLong(baseAddr, 0x4942534f); // Magic value ('OSBI' in little endian)
            ctx.writeLong(baseAddr + 8L, 0x2); // Info version
            ctx.writeLong(baseAddr + 16L, nextPhasePc); // Next booting stage address
            ctx.writeLong(baseAddr + 24L, 0x1); // Next booting stage mode (S-mode)
            ctx.writeLong(baseAddr + 32L, 0x0); // OpenSBI options
            ctx.writeLong(baseAddr + 40L, 0x0); // Preferred boot hart
        }

        // Prepare boot arguments
        cpuState.setRegister(RegisterState.A0, 0);
        cpuState.setRegister(RegisterState.A1, 0xbffff000L);
        if (provideNextPhaseInfo) {
            cpuState.setRegister(RegisterState.A2, 0xbfffd000L);
        }
        return cpuState;
    }
}
