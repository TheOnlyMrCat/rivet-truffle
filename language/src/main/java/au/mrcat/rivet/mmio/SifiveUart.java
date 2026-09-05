package au.mrcat.rivet.mmio;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.MemoryWidth;
import au.mrcat.rivet.runtime.RiscvTrapException;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SifiveUart {
    private final RivetContext context;

    private int txctrl = 0;
    private int rxctrl = 0;
    private int ie = 0;
    private int div = 0;

    public SifiveUart(RivetContext context) {
        this.context = context;
    }

    public long readMmio(long reg, MemoryWidth width) {
        if (width != MemoryWidth.Word) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }

        return switch ((int) reg) {
            case 0x0 -> 0;
            case 0x4 -> {
                var in = context.env.in();
                try {
                    if (in != null && in.available() > 0) {
                            yield context.env.in().read();
                    }
                } catch (IOException _) {
                    // Ignore errors
                }
                yield Integer.MIN_VALUE;
            }
            case 0x8 -> txctrl;
            case 0xc -> rxctrl;
            case 0x10 -> ie;
            case 0x14 -> {
                long flags = 0;

                // Transmits happen instantly, so if the watermark level is non-zero, post the transmit watermark interrupt pending
                if ((txctrl & (0b111 << 16)) != 0) {
                    flags |= 0b1;
                }

                var in = context.env.in();
                try {
                    // Check if the number of available characters is above the receive watermark level
                    if (in.available() > (rxctrl & (0b111 << 16)) >> 16) {
                        flags |= 0b1 << 1;
                    }
                } catch (IOException _) {
                    // Ignore errors
                }

                yield flags;
            }
            case 0x18 -> div;
            default -> 0;
        };
    }

    public void writeMmio(long reg, long value, MemoryWidth width) {
        if (width != MemoryWidth.Word) {
            throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
        }

        switch ((int) reg) {
            case 0x0 -> {
                if (context.env.out() != null && (txctrl & 0b1) == 1) {
                    try {
                        context.env.out().write((byte) value);
                    } catch (IOException _) {
                        // Ignore errors
                    }
                }
            }
            case 0x4 -> { /* Do nothing */ }
            case 0x8 -> txctrl = (int) value;
            case 0xc -> rxctrl = (int) value;
            case 0x10 -> ie = (int) value & 0b11;
            case 0x14 -> { /* Do nothing */ }
            case 0x18 -> div = (int) value;
        }
    }
}
