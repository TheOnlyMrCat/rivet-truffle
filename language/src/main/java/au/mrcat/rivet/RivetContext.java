package au.mrcat.rivet;

import au.mrcat.rivet.mmio.SifiveUart;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.MemoryWidth;
import au.mrcat.rivet.riscv.PrivilegedState;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.RiscvExitException;
import au.mrcat.rivet.runtime.RiscvRebootException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

import java.io.IOException;
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

    private final SifiveUart uart;

    private static final long NO_RESERVATION = 1;
    private long reservedDoubleWord = NO_RESERVATION;

    private final VarHandle BYTE = ValueLayout.JAVA_BYTE.varHandle();
    private final VarHandle LE_SHORT = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).varHandle();
    private final VarHandle LE_SHORT_UNALIGNED = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN).varHandle();
    private final VarHandle LE_INT = ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).varHandle();
    private final VarHandle LE_INT_UNALIGNED = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN).varHandle();
    private final VarHandle LE_LONG = ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN).varHandle();
    private final VarHandle LE_LONG_UNALIGNED = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN).varHandle();

    public RivetContext(TruffleLanguage.Env env) {
        this.env = env;
        privilegedState = new PrivilegedState();
        arena = Arena.ofAuto();
        memory = arena.allocate(1 * 1024 * 1024 * 1024, 4096);
        uart = new SifiveUart(this);
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
            return (byte) readMmio(address, MemoryWidth.Byte);
        }
        return (byte) BYTE.get(memory, address - 0x8000_0000L);
    }

    public short readShort(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            return (short) readMmio(address, MemoryWidth.HalfWord);
        }
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return (short) LE_SHORT.get(memory, address - 0x8000_0000L);
    }

    public short readShortMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            return (short) readMmio(address, MemoryWidth.HalfWord);
        }
        return (short) LE_SHORT_UNALIGNED.get(memory, address - 0x8000_0000L);
    }

    public int readInt(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            return (int) readMmio(address, MemoryWidth.Word);
        }
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return (int) LE_INT.get(memory, address - 0x8000_0000L);
    }

    public int readIntMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            return (int) readMmio(address, MemoryWidth.Word);
        }
        return (int) LE_INT_UNALIGNED.get(memory, address - 0x8000_0000L);
    }

    public long readLong(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            return readMmio(address, MemoryWidth.DoubleWord);
        }
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        return (long) LE_LONG.get(memory, address - 0x8000_0000L);
    }

    public long readLongMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            return readMmio(address, MemoryWidth.DoubleWord);
        }
        return (long) LE_LONG_UNALIGNED.get(memory, address - 0x8000_0000L);
    }

    public void reserveAddress(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        reservedDoubleWord = address & ~0b111L;
    }

    public long readMmio(long address, MemoryWidth width) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }

        // Syscon
        if (0x10_0000L <= address && address + width.bytes < 0x10_1000) {
            if (width != MemoryWidth.Word) {
                throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
            }
            return 0;
        }

        // SiFive UART
        if (0x1000_0000 <= address && address + width.bytes < 0x1000_1000) {
            long reg = address - 0x1000_0000;
            return uart.readMmio(reg, width);
        }

        throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
    }

    public void writeByte(long address, byte value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize()) {
            writeMmio(address, value, MemoryWidth.Byte);
            return;
        }
        BYTE.set(memory, address - 0x8000_0000L, value);
    }

    public void writeShort(long address, short value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            writeMmio(address, value, MemoryWidth.HalfWord);
            return;
        }
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        LE_SHORT.set(memory, address - 0x8000_0000L, value);
    }

    public void writeShortMisaligned(long address, short value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            writeMmio(address, value, MemoryWidth.HalfWord);
            return;
        }
        LE_SHORT_UNALIGNED.set(memory, address - 0x8000_0000L, value);
    }

    public void writeInt(long address, int value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            writeMmio(address, value, MemoryWidth.Word);
            return;
        }
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        LE_INT.set(memory, address - 0x8000_0000L, value);
    }

    public void writeIntMisaligned(long address, int value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            writeMmio(address, value, MemoryWidth.Word);
            return;
        }
        LE_INT_UNALIGNED.set(memory, address - 0x8000_0000L, value);
    }

    public void writeLong(long address, long value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            writeMmio(address, value, MemoryWidth.DoubleWord);
            return;
        }
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        LE_LONG.set(memory, address - 0x8000_0000L, value);
    }

    public void writeLongMisaligned(long address, long value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.byteSize() - 1) {
            writeMmio(address, value, MemoryWidth.DoubleWord);
            return;
        }
        LE_LONG_UNALIGNED.set(memory, address - 0x8000_0000L, value);
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
        LE_INT.set(memory, address - 0x8000_0000L, value);
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
        LE_LONG.set(memory, address - 0x8000_0000L, value);
        return true;
    }

    private void writeMmio(long address, long value, MemoryWidth width) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }

        // Syscon
        if (0x10_0000L <= address && address + width.bytes < 0x10_1000) {
            if (address != 0x10_0000L || width != MemoryWidth.Word) {
                throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
            }
            if (value == 0x5555) {
                throw new RiscvExitException(0);
            } else if ((value & 0xFFFF) == 0x3333) {
                throw new RiscvExitException(value >> 16);
            } else if (value == 0x7777) {
                throw new RiscvRebootException();
            }
            return;
        }

        // SiFive UART
        if (0x1000_0000 <= address && address + width.bytes < 0x1000_1000) {
            long reg = address - 0x1000_0000;
            uart.writeMmio(reg, value, width);
            return;
        }

        throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
    }
}
