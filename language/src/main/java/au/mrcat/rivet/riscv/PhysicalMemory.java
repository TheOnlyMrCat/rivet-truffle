package au.mrcat.rivet.riscv;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.mmio.SifiveUart;
import au.mrcat.rivet.runtime.RiscvExitException;
import au.mrcat.rivet.runtime.RiscvRebootException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.memory.ByteArraySupport;

public class PhysicalMemory {
    private final byte[] memory;
    private final ByteArraySupport byteArray;

    private final SifiveUart uart;

    private static final long NO_RESERVATION = 1;
    private long reservedDoubleWord = NO_RESERVATION;

    public PhysicalMemory(RivetContext context) {
        memory = new byte[1 * 1024 * 1024 * 1024];
        byteArray = ByteArraySupport.littleEndian();
        uart = new SifiveUart(context);
    }

    public boolean isInPhysicalMemory(long physicalAddress, long accessWidth) {
        return 0x8000_0000L <= physicalAddress && physicalAddress - 0x8000_0000L + accessWidth <= memory.length;
    }

    public byte readByte(long physicalAddress) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length) {
            return (byte) readMmio(physicalAddress, MemoryWidth.Byte, AccessType.READ);
        }
        return byteArray.getByte(memory, physicalAddress - 0x8000_0000L);
    }

    public short readShort(long physicalAddress) {
        if ((physicalAddress & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            return (short) readMmio(physicalAddress, MemoryWidth.HalfWord, AccessType.READ);
        }
        return byteArray.getShort(memory, physicalAddress - 0x8000_0000L);
    }

    public short readShortMisaligned(long physicalAddress) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            return (short) readMmio(physicalAddress, MemoryWidth.HalfWord, AccessType.READ);
        }
        if ((physicalAddress & 0b1) != 0) {
            return byteArray.getShortUnaligned(memory, physicalAddress - 0x8000_0000L);
        }
        return byteArray.getShort(memory, physicalAddress - 0x8000_0000L);
    }

    public int readInt(long physicalAddress) {
        if ((physicalAddress & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            return (int) readMmio(physicalAddress, MemoryWidth.Word, AccessType.READ);
        }
        return byteArray.getInt(memory, physicalAddress - 0x8000_0000L);
    }

    public int readIntMisaligned(long physicalAddress, AccessType accessType) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            return (int) readMmio(physicalAddress, MemoryWidth.Word, accessType);
        }
        if ((physicalAddress & 0b11) != 0) {
            return byteArray.getIntUnaligned(memory, physicalAddress - 0x8000_0000L);
        }
        return byteArray.getInt(memory, physicalAddress - 0x8000_0000L);
    }

    public long readLong(long physicalAddress) {
        if ((physicalAddress & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
        }
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            return readMmio(physicalAddress, MemoryWidth.DoubleWord, AccessType.READ);
        }
        return byteArray.getLong(memory, physicalAddress - 0x8000_0000L);
    }

    public long readLongMisaligned(long physicalAddress) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            return readMmio(physicalAddress, MemoryWidth.DoubleWord, AccessType.READ);
        }
        if ((physicalAddress & 0b111) != 0) {
            return byteArray.getLongUnaligned(memory, physicalAddress - 0x8000_0000L);
        }
        return byteArray.getLong(memory, physicalAddress - 0x8000_0000L);
    }

    public void reserveIntAddress(long physicalAddress) {
        if ((physicalAddress & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        reservedDoubleWord = physicalAddress & ~0b111L;
    }

    public void reserveLongAddress(long physicalAddress) {
        if ((physicalAddress & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
        }
        reservedDoubleWord = physicalAddress & ~0b111L;
    }

    public long readMmio(long physicalAddress, MemoryWidth width, AccessType accessType) {
        // Syscon
        if (0x10_0000L <= physicalAddress && physicalAddress + width.bytes < 0x10_1000) {
            if (!width.isNaturallyAligned(physicalAddress)) {
                throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
            }

            if (width != MemoryWidth.Word) {
                throw new RiscvTrapException(ExceptionCause.LoadAccessFault);
            }
            return 0;
        }

        // SiFive UART
        if (0x1000_0000 <= physicalAddress && physicalAddress + width.bytes < 0x1000_1000) {
            if (!width.isNaturallyAligned(physicalAddress)) {
                throw new RiscvTrapException(ExceptionCause.LoadAddressMisaligned);
            }

            long reg = physicalAddress - 0x1000_0000;
            return uart.readMmio(reg, width);
        }

        throw new RiscvTrapException(accessType.accessFaultCause);
    }

    public void writeByte(long physicalAddress, byte value) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length) {
            writeMmio(physicalAddress, value, MemoryWidth.Byte);
            return;
        }
        byteArray.putByte(memory, physicalAddress - 0x8000_0000L, value);
    }

    public void writeShort(long physicalAddress, short value) {
        if ((physicalAddress & 0b1) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            writeMmio(physicalAddress, value, MemoryWidth.HalfWord);
            return;
        }
        byteArray.putShort(memory, physicalAddress - 0x8000_0000L, value);
    }

    public void writeShortMisaligned(long physicalAddress, short value) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            writeMmio(physicalAddress, value, MemoryWidth.HalfWord);
            return;
        }
        byteArray.putShort(memory, physicalAddress - 0x8000_0000L, value);
    }

    public void writeInt(long physicalAddress, int value) {
        if ((physicalAddress & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            writeMmio(physicalAddress, value, MemoryWidth.Word);
            return;
        }
        byteArray.putInt(memory, physicalAddress - 0x8000_0000L, value);
    }

    public void writeIntMisaligned(long physicalAddress, int value) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            writeMmio(physicalAddress, value, MemoryWidth.Word);
            return;
        }
        byteArray.putInt(memory, physicalAddress - 0x8000_0000L, value);
    }

    public void writeLong(long physicalAddress, long value) {
        if ((physicalAddress & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
        }
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            writeMmio(physicalAddress, value, MemoryWidth.DoubleWord);
            return;
        }
        byteArray.putLong(memory, physicalAddress - 0x8000_0000L, value);
    }

    public void writeLongMisaligned(long physicalAddress, long value) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            writeMmio(physicalAddress, value, MemoryWidth.DoubleWord);
            return;
        }
        byteArray.putLong(memory, physicalAddress - 0x8000_0000L, value);
    }

    public boolean writeIntConditional(long physicalAddress, int value) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((physicalAddress & 0b11) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        long reserved = reservedDoubleWord;
        reservedDoubleWord = NO_RESERVATION;
        if ((physicalAddress & ~0b111L) != reserved) {
            return false;
        }
        byteArray.putInt(memory, physicalAddress - 0x8000_0000L, value);
        return true;
    }

    public boolean writeLongConditional(long physicalAddress, long value) {
        if (physicalAddress < 0x8000_0000L || physicalAddress - 0x8000_0000L > memory.length - 1) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        if ((physicalAddress & 0b111) != 0) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }
        long reserved = reservedDoubleWord;
        reservedDoubleWord = NO_RESERVATION;
        if ((physicalAddress & ~0b111L) != reserved) {
            return false;
        }
        byteArray.putLong(memory, physicalAddress - 0x8000_0000L, value);
        return true;
    }

    private void writeMmio(long physicalAddress, long value, MemoryWidth width) {
        // Syscon
        if (0x10_0000L <= physicalAddress && physicalAddress + width.bytes < 0x10_1000) {
            if (physicalAddress != 0x10_0000L || width != MemoryWidth.Word) {
                throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
            }
            if (!width.isNaturallyAligned(physicalAddress)) {
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
        if (0x1000_0000 <= physicalAddress && physicalAddress + width.bytes < 0x1000_1000) {
            if (!width.isNaturallyAligned(physicalAddress)) {
                throw new RiscvTrapException(ExceptionCause.StoreAmoAddressMisaligned);
            }

            long reg = physicalAddress - 0x1000_0000;
            uart.writeMmio(reg, value, width);
            return;
        }

        throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
    }
}
