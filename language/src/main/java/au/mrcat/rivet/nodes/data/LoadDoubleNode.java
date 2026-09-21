package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.MemoryWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadDoubleNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final int offset;
    private final long pcOffset;
    private final short instret;

    public LoadDoubleNode(RivetOpNode address, int offset, long pcOffset, short instret) {
        this.address = address;
        this.offset = offset;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        long virtualAddress = address.executeLong(frame, basePc, priv) + offset;
        try {
            if (!priv.currentAddressSpace(priv.readAccessType()).isAccessContiguous(virtualAddress, MemoryWidth.DoubleWord)) {
                CompilerDirectives.transferToInterpreter();
                long lsb = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress, ctx)));
                long sb1 = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 1, ctx)));
                long sb2 = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 2, ctx)));
                long sb3 = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 3, ctx)));
                long sb4 = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 4, ctx)));
                long sb5 = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 5, ctx)));
                long sb6 = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 6, ctx)));
                long msb = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 7, ctx)));
                return lsb | (sb1 << 8) | (sb2 << 16) | (sb3 << 24) | (sb4 << 32) | (sb5 << 40) | (sb6 << 48) | (msb << 56);
            }
            return ctx.physicalMemory.readLong(priv.translateReadAddress(virtualAddress, ctx));
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(virtualAddress);
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("LoadDoubleNode{");
        sb.append("address=").append(address);
        sb.append(", offset=").append(offset);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
