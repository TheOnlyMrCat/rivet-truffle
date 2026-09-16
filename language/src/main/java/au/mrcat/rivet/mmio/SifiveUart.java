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
    private int ip = 0;
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
                            var c = context.env.in().read();
                            recalculateInterrupts();
                            yield c;
                    }
                } catch (IOException _) {
                    // Ignore errors
                }
                yield Integer.MIN_VALUE;
            }
            case 0x8 -> txctrl;
            case 0xc -> rxctrl;
            case 0x10 -> ie;
            case 0x14 -> ip;
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
                    recalculateInterrupts();
                }
            }
            case 0x4 -> { /* Do nothing */ }
            case 0x8 -> {
                txctrl = (int) value;
                recalculateInterrupts();
            }
            case 0xc -> {
                rxctrl = (int) value;
                recalculateInterrupts();
            }
            case 0x10 -> {
                ie = (int) value & 0b11;
                recalculateInterrupts();
            }
            case 0x14 -> { /* Do nothing */ }
            case 0x18 -> div = (int) value;
        }
    }

    private void recalculateInterrupts() {
        // First, recalculate the pending interrupt bits
        ip = 0;

        // Transmits happen instantly, so if the watermark level is non-zero, post the transmit watermark interrupt pending
        if ((txctrl & (0b111 << 16)) != 0) {
            ip |= 0b1;
        }

        var in = context.env.in();
        try {
            // Check if the number of available characters is above the receive watermark level
            if (in.available() > (rxctrl & (0b111 << 16)) >> 16) {
                ip |= 0b1 << 1;
            }
        } catch (IOException _) {
            // Ignore errors
        }

        if ((ip & ie) != 0) {
            context.ffi.rustTriggerSerialInterrupt();
        } else {
            context.ffi.rustUntriggerSerialInterrupt();
        }
    }
}
