package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetInstructionNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.AccessType;
import au.mrcat.rivet.riscv.MemoryWidth;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class StoreHalfNode extends RivetInstructionNode {
    @Child RivetOpNode address;
    private final long offset;
    @Child RivetOpNode value;
    private final long pcOffset;
    private final short instret;

    public StoreHalfNode(RivetOpNode address, long offset, RivetOpNode value, long pcOffset, short instret) {
        this.address = address;
        this.offset = offset;
        this.value = value;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();
        long virtualAddress = address.executeLong(frame, basePc, priv) + offset;
        try {
            short value1 = (short) value.executeLong(frame, basePc, priv);
            if (!priv.currentAddressSpace(AccessType.WRITE).isAccessContiguous(virtualAddress, MemoryWidth.HalfWord)) {
                CompilerDirectives.transferToInterpreter();
                ctx.physicalMemory.writeByte(priv.translateWriteAddress(virtualAddress, ctx), (byte) value1);
                ctx.physicalMemory.writeByte(priv.translateWriteAddress(virtualAddress + 1, ctx), (byte) (value1 >> 8));
            } else {
                ctx.physicalMemory.writeShort(priv.translateWriteAddress(virtualAddress, ctx), value1);
            }
        } catch (RiscvTrapException trap) {
            trap.setPc(basePc + pcOffset);
            trap.setTval(virtualAddress);
            trap.setInstret(instret);
            throw trap;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("StoreHalfNode{");
        sb.append("address=").append(address);
        sb.append(", offset=").append(offset);
        sb.append(", value=").append(value);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
