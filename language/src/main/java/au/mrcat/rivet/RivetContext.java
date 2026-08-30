package au.mrcat.rivet;

import au.mrcat.rivet.riscv.*;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

public class RivetContext {
    private static final TruffleLanguage.ContextReference<RivetContext> REF = TruffleLanguage.ContextReference.create(RivetLanguage.class);

    public static RivetContext get(Node node) {
        return REF.get(node);
    }

    public final TruffleLanguage.Env env;
    public final PrivilegedState privilegedState;
    public final PhysicalMemory physicalMemory;
    public final RivetFfi ffi;

    public RivetContext(TruffleLanguage.Env env) {
        this.env = env;
        privilegedState = new PrivilegedState(this);
        physicalMemory = new PhysicalMemory(this);
        ffi = new RivetFfi(this);
    }

    private long translateReadAddress(long virtualAddress) {
        return privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(virtualAddress, privilegedState.readAccessType(), privilegedState, physicalMemory);
    }

    public byte readByte(long virtualAddress) {
        return physicalMemory.readByte(translateReadAddress(virtualAddress));
    }

    public short readShort(long virtualAddress) {
        if (!privilegedState.currentAddressSpace(privilegedState.readAccessType()).isAccessContiguous(virtualAddress, MemoryWidth.HalfWord)) {
            int lsb = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress)));
            int msb = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 1)));
            return (short) (lsb | (msb << 8));
        }
        return physicalMemory.readShort(translateReadAddress(virtualAddress), privilegedState.readAccessType());
    }

    public int readInt(long virtualAddress) {
        if (!privilegedState.currentAddressSpace(privilegedState.readAccessType()).isAccessContiguous(virtualAddress, MemoryWidth.Word)) {
            int lsb = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress)));
            int sb1 = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 1)));
            int sb2 = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 2)));
            int msb = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 3)));
            return lsb | (sb1 << 8) | (sb2 << 16) | (msb << 24);
        }
        return physicalMemory.readInt(translateReadAddress(virtualAddress), privilegedState.readAccessType());
    }

    public int readInstructionInt(long virtualAddress) {
        if ((virtualAddress & 0b1) != 0) {
            throw new IllegalArgumentException("Instruction virtual address not aligned to 16 bits: " + virtualAddress);
        }
        AddressSpace addressSpace = privilegedState.currentAddressSpace(AccessType.EXECUTE);
        if (!addressSpace.isAccessContiguous(virtualAddress, MemoryWidth.Word)) {
            int lsh = Short.toUnsignedInt(physicalMemory.readShort(addressSpace.toPhysicalAddress(virtualAddress, AccessType.EXECUTE, privilegedState, physicalMemory), AccessType.EXECUTE));
            if ((lsh & 0b11) != 0b11) {
                // Compressed instruction; don't bother reading the most significant half since it'll be discarded anyway
                return lsh;
            }
            int msh = physicalMemory.readShort(addressSpace.toPhysicalAddress(virtualAddress + 2, AccessType.EXECUTE, privilegedState, physicalMemory), AccessType.EXECUTE);
            return lsh | (msh << 16);
        }
        return physicalMemory.readInt(addressSpace.toPhysicalAddress(virtualAddress, AccessType.EXECUTE, privilegedState, physicalMemory), AccessType.EXECUTE);
    }

    public long readLong(long virtualAddress) {
        if (!privilegedState.currentAddressSpace(privilegedState.readAccessType()).isAccessContiguous(virtualAddress, MemoryWidth.DoubleWord)) {
            long lsb = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress)));
            long sb1 = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 1)));
            long sb2 = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 2)));
            long sb3 = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 3)));
            long sb4 = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 4)));
            long sb5 = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 5)));
            long sb6 = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 6)));
            long msb = Byte.toUnsignedInt(physicalMemory.readByte(translateReadAddress(virtualAddress + 7)));
            return lsb | (sb1 << 8) | (sb2 << 16) | (sb3 << 24) | (sb4 << 32) | (sb5 << 40) | (sb6 << 48) | (msb << 56);
        }
        return physicalMemory.readLong(translateReadAddress(virtualAddress));
    }

    public void reserveIntAddress(long virtualAddress) {
        if ((virtualAddress & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        physicalMemory.reserveIntAddress(translateReadAddress(virtualAddress));
    }

    public void reserveLongAddress(long virtualAddress) {
        if ((virtualAddress & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        physicalMemory.reserveLongAddress(translateReadAddress(virtualAddress));
    }

    private long translateWriteAddress(long virtualAddress) {
        return privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(virtualAddress, AccessType.WRITE, privilegedState, physicalMemory);
    }

    public void writeByte(long virtualAddress, byte value) {
        physicalMemory.writeByte(translateWriteAddress(virtualAddress), value);
    }

    public void writeShort(long virtualAddress, short value) {
        if (!privilegedState.currentAddressSpace(AccessType.WRITE).isAccessContiguous(virtualAddress, MemoryWidth.HalfWord)) {
            physicalMemory.writeByte(translateWriteAddress(virtualAddress), (byte) value);
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 1), (byte) (value >> 8));
        }
        physicalMemory.writeShort(translateWriteAddress(virtualAddress), value);
    }

    public void writeInt(long virtualAddress, int value) {
        if (!privilegedState.currentAddressSpace(AccessType.WRITE).isAccessContiguous(virtualAddress, MemoryWidth.Word)) {
            physicalMemory.writeByte(translateWriteAddress(virtualAddress), (byte) value);
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 1), (byte) (value >> 8));
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 2), (byte) (value >> 16));
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 3), (byte) (value >> 24));
        }
        physicalMemory.writeInt(translateWriteAddress(virtualAddress), value);
    }

    public void writeLong(long virtualAddress, long value) {
        if (!privilegedState.currentAddressSpace(AccessType.WRITE).isAccessContiguous(virtualAddress, MemoryWidth.DoubleWord)) {
            physicalMemory.writeByte(translateWriteAddress(virtualAddress), (byte) value);
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 1), (byte) (value >> 8));
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 2), (byte) (value >> 16));
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 3), (byte) (value >> 24));
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 4), (byte) (value >> 32));
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 5), (byte) (value >> 40));
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 6), (byte) (value >> 48));
            physicalMemory.writeByte(translateWriteAddress(virtualAddress + 7), (byte) (value >> 56));
        }
        physicalMemory.writeLong(translateWriteAddress(virtualAddress), value);
    }

    public boolean writeIntConditional(long virtualAddress, int value) {
        if ((virtualAddress & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        return physicalMemory.writeIntConditional(translateWriteAddress(virtualAddress), value);
    }

    public boolean writeLongConditional(long virtualAddress, long value) {
        if ((virtualAddress & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        return physicalMemory.writeLongConditional(translateWriteAddress(virtualAddress), value);
    }
}
