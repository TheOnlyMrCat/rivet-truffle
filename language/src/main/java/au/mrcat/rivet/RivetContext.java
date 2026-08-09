package au.mrcat.rivet;

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
        return physicalMemory.readByte(address);
    }

    public short readShort(long address) {
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return physicalMemory.readShort(address);
    }

    public short readShortMisaligned(long address) {
        return physicalMemory.readShortMisaligned(address);
    }

    public int readInt(long address) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return physicalMemory.readInt(address);
    }

    public int readIntMisaligned(long address) {
        return physicalMemory.readIntMisaligned(address);
    }

    public long readLong(long address) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return physicalMemory.readLong(address);
    }

    public long readLongMisaligned(long address) {
        return physicalMemory.readLongMisaligned(address);
    }

    public void reserveIntAddress(long address) {
        physicalMemory.reserveIntAddress(address);
    }

    public void reserveLongAddress(long address) {
        physicalMemory.reserveLongAddress(address);
    }

    public void writeByte(long address, byte value) {
        physicalMemory.writeByte(address, value);
    }

    public void writeShort(long address, short value) {
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        physicalMemory.writeShort(address, value);
    }

    public void writeShortMisaligned(long address, short value) {
        physicalMemory.writeShortMisaligned(address, value);
    }

    public void writeInt(long address, int value) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        physicalMemory.writeInt(address, value);
    }

    public void writeIntMisaligned(long address, int value) {
        physicalMemory.writeIntMisaligned(address, value);
    }

    public void writeLong(long address, long value) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        physicalMemory.writeLong(address, value);
    }

    public void writeLongMisaligned(long address, long value) {
        physicalMemory.writeLongMisaligned(address, value);
    }

    public boolean writeIntConditional(long address, int value) {
        return physicalMemory.writeIntConditional(address, value);
    }

    public boolean writeLongConditional(long address, long value) {
        return physicalMemory.writeLongConditional(address, value);
    }
}
