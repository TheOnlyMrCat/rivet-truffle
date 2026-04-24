package au.mrcat.rivet;

import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.PrivilegedState;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public class RivetContext {
    private static final TruffleLanguage.ContextReference<RivetContext> REF = TruffleLanguage.ContextReference.create(RivetLanguage.class);

    public static RivetContext get(Node node) {
        return REF.get(node);
    }

    public final TruffleLanguage.Env env;
    private final Arena arena;
    public final PrivilegedState privilegedState;
    public final MemorySegment memory;

    private static final long NO_RESERVATION = 1;
    private long reservedDoubleWord = NO_RESERVATION;

    public RivetContext(TruffleLanguage.Env env) {
        this.env = env;
        privilegedState = new PrivilegedState();
        arena = Arena.ofAuto();
        memory = arena.allocate(1 * 1024 * 1024 * 1024, 4096);
    }

    public MemorySegment slice(long address, long size) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize()) {
            // FIXME: Throw something different?
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        return memory.asSlice(address - 0x8000_0000L, size);
    }

    public byte readByte(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize()) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        return memory.get(ValueLayout.JAVA_BYTE, address - 0x8000_0000L);
    }

    public short readShort(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return memory.get(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), address - 0x8000_0000L);
    }

    public short readShortMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        return memory.get(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1), address - 0x8000_0000L);
    }

    public int readInt(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return memory.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), address - 0x8000_0000L);
    }

    public int readIntMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        return memory.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1), address - 0x8000_0000L);
    }

    public long readLong(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return memory.get(ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), address - 0x8000_0000L);
    }

    public long readLongMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        return memory.get(ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1), address - 0x8000_0000L);
    }

    public void reserveAddress(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        reservedDoubleWord = address & ~0b111L;
    }

    public void writeByte(long address, byte value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize()) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        memory.set(ValueLayout.JAVA_BYTE, address - 0x8000_0000L, value);
    }

    public void writeShort(long address, short value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        memory.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), address - 0x8000_0000L, value);
    }

    public void writeShortMisaligned(long address, short value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        memory.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1), address - 0x8000_0000L, value);
    }

    public void writeInt(long address, int value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        memory.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), address - 0x8000_0000L, value);
    }

    public void writeIntMisaligned(long address, int value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        memory.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1), address - 0x8000_0000L, value);
    }

    public void writeLong(long address, long value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        memory.set(ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), address - 0x8000_0000L, value);
    }

    public void writeLongMisaligned(long address, long value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        memory.set(ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1), address - 0x8000_0000L, value);
    }

    public boolean writeIntConditional(long address, int value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        long reserved = reservedDoubleWord;
        reservedDoubleWord = NO_RESERVATION;
        if ((address & ~0b111L) != reserved) {
            return false;
        }
        memory.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), address - 0x8000_0000L, value);
        return true;
    }

    public boolean writeLongConditional(long address, long value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        long reserved = reservedDoubleWord;
        reservedDoubleWord = NO_RESERVATION;
        if ((address & ~0b111L) != reserved) {
            return false;
        }
        memory.set(ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), address - 0x8000_0000L, value);
        return true;
    }
}
