package au.mrcat.rivet;

import au.mrcat.rivet.mmio.SifiveUart;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.MemoryWidth;
import au.mrcat.rivet.riscv.PrivilegedState;
import au.mrcat.rivet.runtime.RiscvExitException;
import au.mrcat.rivet.runtime.RiscvRebootException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

public class RivetContext {
    private static final TruffleLanguage.ContextReference<RivetContext> REF = TruffleLanguage.ContextReference.create(RivetLanguage.class);

    public static RivetContext get(Node node) {
        return REF.get(node);
    }

    public final TruffleLanguage.Env env;
    public final PrivilegedState privilegedState;
    public final byte[] memory;
    private final ByteArraySupport byteArray;

    private final SifiveUart uart;

    private static final long NO_RESERVATION = 1;
    private long reservedDoubleWord = NO_RESERVATION;

    public RivetContext(TruffleLanguage.Env env) {
        this.env = env;
        privilegedState = new PrivilegedState();
        memory = new byte[1 * 1024 * 1024 * 1024];
        byteArray = ByteArraySupport.littleEndian();
        uart = new SifiveUart(this);
    }

    public byte readByte(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length) {
            return (byte) readMmio(address, MemoryWidth.Byte);
        }
        return byteArray.getByte(memory, address - 0x8000_0000L);
    }

    public short readShort(long address) {
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            return (short) readMmio(address, MemoryWidth.HalfWord);
        }
        return byteArray.getShort(memory, address - 0x8000_0000L);
    }

    public short readShortMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            return (short) readMmio(address, MemoryWidth.HalfWord);
        }
        if ((address & 0b1) != 0) {
            return byteArray.getShortUnaligned(memory, address - 0x8000_0000L);
        }
        return byteArray.getShort(memory, address - 0x8000_0000L);
    }

    public int readInt(long address) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            return (int) readMmio(address, MemoryWidth.Word);
        }
        return byteArray.getInt(memory, address - 0x8000_0000L);
    }

    public int readIntMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            return (int) readMmio(address, MemoryWidth.Word);
        }
        if ((address & 0b11) != 0) {
            return byteArray.getIntUnaligned(memory, address - 0x8000_0000L);
        }
        return byteArray.getInt(memory, address - 0x8000_0000L);
    }

    public long readLong(long address) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            return readMmio(address, MemoryWidth.DoubleWord);
        }
        return byteArray.getLong(memory, address - 0x8000_0000L);
    }

    public long readLongMisaligned(long address) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            return readMmio(address, MemoryWidth.DoubleWord);
        }
        if ((address & 0b111) != 0) {
            return byteArray.getLongUnaligned(memory, address - 0x8000_0000L);
        }
        return byteArray.getLong(memory, address - 0x8000_0000L);
    }

    public void reserveIntAddress(long address) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        reservedDoubleWord = address & ~0b111L;
    }

    public void reserveLongAddress(long address) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        reservedDoubleWord = address & ~0b111L;
    }

    public long readMmio(long address, MemoryWidth width) {
        // Syscon
        if (0x10_0000L <= address && address + width.bytes < 0x10_1000) {
            if (!width.isNaturallyAligned(address)) {
                throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
            }

            if (width != MemoryWidth.Word) {
                throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
            }
            return 0;
        }

        // SiFive UART
        if (0x1000_0000 <= address && address + width.bytes < 0x1000_1000) {
            if (!width.isNaturallyAligned(address)) {
                throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
            }

            long reg = address - 0x1000_0000;
            return uart.readMmio(reg, width);
        }

        throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
    }

    public void writeByte(long address, byte value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length) {
            writeMmio(address, value, MemoryWidth.Byte);
            return;
        }
        byteArray.putByte(memory, address - 0x8000_0000L, value);
    }

    public void writeShort(long address, short value) {
        if ((address & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            writeMmio(address, value, MemoryWidth.HalfWord);
            return;
        }
        byteArray.putShort(memory, address - 0x8000_0000L, value);
    }

    public void writeShortMisaligned(long address, short value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            writeMmio(address, value, MemoryWidth.HalfWord);
            return;
        }
        byteArray.putShort(memory, address - 0x8000_0000L, value);
    }

    public void writeInt(long address, int value) {
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            writeMmio(address, value, MemoryWidth.Word);
            return;
        }
        byteArray.putInt(memory, address - 0x8000_0000L, value);
    }

    public void writeIntMisaligned(long address, int value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            writeMmio(address, value, MemoryWidth.Word);
            return;
        }
        byteArray.putInt(memory, address - 0x8000_0000L, value);
    }

    public void writeLong(long address, long value) {
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            writeMmio(address, value, MemoryWidth.DoubleWord);
            return;
        }
        byteArray.putLong(memory, address - 0x8000_0000L, value);
    }

    public void writeLongMisaligned(long address, long value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            writeMmio(address, value, MemoryWidth.DoubleWord);
            return;
        }
        byteArray.putLong(memory, address - 0x8000_0000L, value);
    }

    public boolean writeIntConditional(long address, int value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((address & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        long reserved = reservedDoubleWord;
        reservedDoubleWord = NO_RESERVATION;
        if ((address & ~0b111L) != reserved) {
            return false;
        }
        byteArray.putInt(memory, address - 0x8000_0000L, value);
        return true;
    }

    public boolean writeLongConditional(long address, long value) {
        if (address < 0x8000_0000L || address - 0x8000_0000L > memory.length - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((address & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        long reserved = reservedDoubleWord;
        reservedDoubleWord = NO_RESERVATION;
        if ((address & ~0b111L) != reserved) {
            return false;
        }
        byteArray.putLong(memory, address - 0x8000_0000L, value);
        return true;
    }

    private void writeMmio(long address, long value, MemoryWidth width) {
        // Syscon
        if (0x10_0000L <= address && address + width.bytes < 0x10_1000) {
            if (address != 0x10_0000L || width != MemoryWidth.Word) {
                throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
            }
            if (!width.isNaturallyAligned(address)) {
                throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
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
            if (!width.isNaturallyAligned(address)) {
                throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
            }

            long reg = address - 0x1000_0000;
            uart.writeMmio(reg, value, width);
            return;
        }

        throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
    }
}
