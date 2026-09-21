package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.MemoryWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LoadWordUnsignedNode extends RivetOpNode {
    @Child RivetOpNode address;
    private final int offset;
    private final long pcOffset;
    private final short instret;

    public LoadWordUnsignedNode(RivetOpNode address, int offset, long pcOffset, short instret) {
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
            int result;
            if (!priv.currentAddressSpace(priv.readAccessType()).isAccessContiguous(virtualAddress, MemoryWidth.Word)) {
                CompilerDirectives.transferToInterpreter();
                int lsb = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress, ctx)));
                int sb1 = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 1, ctx)));
                int sb2 = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 2, ctx)));
                int msb = Byte.toUnsignedInt(ctx.physicalMemory.readByte(priv.translateReadAddress(virtualAddress + 3, ctx)));
                result = lsb | (sb1 << 8) | (sb2 << 16) | (msb << 24);
            } else {
                result = ctx.physicalMemory.readInt(priv.translateReadAddress(virtualAddress, ctx), priv.readAccessType());
            }
            return Integer.toUnsignedLong(result);
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(virtualAddress);
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("LoadWordUnsignedNode{");
        sb.append("address=").append(address);
        sb.append(", offset=").append(offset);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
