package au.mrcat.rivet;

import au.mrcat.rivet.riscv.AccessType;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.PhysicalMemory;
import au.mrcat.rivet.riscv.PrivilegedState;
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

    public RivetContext(TruffleLanguage.Env env) {
        this.env = env;
        privilegedState = new PrivilegedState();
        physicalMemory = new PhysicalMemory(this);
    }

    public byte readByte(long address) {
        return physicalMemory.readByte(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory));
    }

    public short readShort(long address) {
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return physicalMemory.readShort(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory));
    }

    public short readShortMisaligned(long address) {
        return physicalMemory.readShortMisaligned(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory));
    }

    public int readInt(long address) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return physicalMemory.readInt(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory));
    }

    public int readIntMisaligned(long address) {
        return physicalMemory.readIntMisaligned(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory), privilegedState.readAccessType());
    }

    public int readInstructionIntMisaligned(long address) {
        return physicalMemory.readIntMisaligned(privilegedState.currentAddressSpace(AccessType.EXECUTE).toPhysicalAddress(address, AccessType.EXECUTE, privilegedState, physicalMemory), AccessType.EXECUTE);
    }

    public long readLong(long address) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return physicalMemory.readLong(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory));
    }

    public long readLongMisaligned(long address) {
        return physicalMemory.readLongMisaligned(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory));
    }

    public void reserveIntAddress(long address) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        physicalMemory.reserveIntAddress(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory));
    }

    public void reserveLongAddress(long address) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        physicalMemory.reserveLongAddress(privilegedState.currentAddressSpace(privilegedState.readAccessType()).toPhysicalAddress(address, privilegedState.readAccessType(), privilegedState, physicalMemory));
    }

    public void writeByte(long address, byte value) {
        physicalMemory.writeByte(privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(address, AccessType.WRITE, privilegedState, physicalMemory), value);
    }

    public void writeShort(long address, short value) {
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        physicalMemory.writeShort(privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(address, AccessType.WRITE, privilegedState, physicalMemory), value);
    }

    public void writeShortMisaligned(long address, short value) {
        physicalMemory.writeShortMisaligned(address, value);
    }

    public void writeInt(long address, int value) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        physicalMemory.writeInt(privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(address, AccessType.WRITE, privilegedState, physicalMemory), value);
    }

    public void writeIntMisaligned(long address, int value) {
        physicalMemory.writeIntMisaligned(privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(address, AccessType.WRITE, privilegedState, physicalMemory), value);
    }

    public void writeLong(long address, long value) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        physicalMemory.writeLong(privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(address, AccessType.WRITE, privilegedState, physicalMemory), value);
    }

    public void writeLongMisaligned(long address, long value) {
        physicalMemory.writeLongMisaligned(privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(address, AccessType.WRITE, privilegedState, physicalMemory), value);
    }

    public boolean writeIntConditional(long address, int value) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        return physicalMemory.writeIntConditional(privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(address, AccessType.WRITE, privilegedState, physicalMemory), value);
    }

    public boolean writeLongConditional(long address, long value) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        return physicalMemory.writeLongConditional(privilegedState.currentAddressSpace(AccessType.WRITE).toPhysicalAddress(address, AccessType.WRITE, privilegedState, physicalMemory), value);
    }
}
