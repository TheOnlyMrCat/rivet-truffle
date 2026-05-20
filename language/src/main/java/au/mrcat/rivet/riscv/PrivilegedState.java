package au.mrcat.rivet.riscv;

import au.mrcat.rivet.runtime.RiscvTrapException;

public final class PrivilegedState {
    private PrivilegeMode mode = PrivilegeMode.Machine;

    private static final long MSTATUS_FIELDS_MASK =
            0b0111_1110_0001_1001_1010_1010;
    private static final long EXCEPTION_MASK = 0b1101_1011_1011_1111_1111;
    private static final long INTERRUPT_MASK = 0;
    private static final long COUNTER_MASK = 0b101;

    private long mstatus;
    private long medeleg;
    private long mideleg;
    private long mie;
    private long mtvec;
    private long mcounteren;

    private long mscratch;
    private long mepc;
    private long mcause;
    private long mtval;
    private long mip;

    private long menvcfg;

    private long mcountinhibit;

    public PrivilegeMode currentMode() {
        return mode;
    }

    public long tryRead(int csr) {
        // Do an initial rough check based on the privilege mode/CSR pair
        int minimumMode = (csr >> 8) & 0b11;
        if (mode.value < minimumMode) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }

        return tryReadPrivileged(csr);
    }

    public long tryReadWrite(int csr) {
        // Do an initial rough check based on the privilege mode/CSR pair
        boolean isReadOnly = ((csr >> 10) & 0b11) == 0b11;
        int minimumMode = (csr >> 8) & 0b11;
        if (mode.value < minimumMode || isReadOnly) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }

        return tryReadPrivileged(csr);
    }

    private long tryReadPrivileged(int csr) {
        switch (csr) {
            // User-level CSRs
            case Csr.TIME -> { return System.nanoTime(); }

            // Machine-level CSRs
            case Csr.MVENDORID -> { return 0; }
            case Csr.MARCHID -> { return 0; }
            case Csr.MIMPID -> { return 0; }
            case Csr.MHARTID -> { return 0; }
            case Csr.MCONFIGPTR -> { return 0; }

            case Csr.MSTATUS -> { return mstatus; }
            case Csr.MISA -> { return 0x8000000000000000L | 0b00_0000_0000_0001_0001_0000_0101; }
            case Csr.MEDELEG -> { return medeleg; }
            case Csr.MIDELEG -> { return mideleg; }
            case Csr.MIE -> { return mie; }
            case Csr.MTVEC -> { return mtvec; }
            case Csr.MCOUNTEREN -> { return mcounteren; }

            case Csr.MSCRATCH -> { return mscratch; }
            case Csr.MEPC -> { return mepc; }
            case Csr.MCAUSE -> { return mcause; }
            case Csr.MTVAL -> { return mtval; }
            case Csr.MIP -> { return mip; }

            case Csr.MENVCFG -> { return menvcfg; }

            case Csr.MCOUNTINHIBIT -> { return mcountinhibit; }

            default -> {
                if (Csr.MCOUNTINHIBIT + 3 <= csr && csr <= Csr.MCOUNTINHIBIT + 31) {
                    // Hardware performance counters; not implemented, but read-only zero
                    return 0;
                } else {
                    // CSR doesn't exist/is not implemented/is not readable
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
            }
        }
    }

    public void tryWrite(int csr, long value) {
        boolean isReadOnly = ((csr >> 10) & 0b11) == 0b11;
        int minimumMode = (csr >> 8) & 0b11;
        if (mode.value < minimumMode || isReadOnly) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }

        switch (csr) {
            // Machine-level CSRs
            case Csr.MSTATUS -> {
                // Mask out invalid fields
                mstatus = value & MSTATUS_FIELDS_MASK;
                // Set SXL and UXL to 64-bit
                mstatus |= 0b1010L << 32L;
            }
            case Csr.MISA -> { /* Do nothing */ }
            case Csr.MEDELEG -> medeleg = value & EXCEPTION_MASK;
            case Csr.MIDELEG -> mideleg = value & INTERRUPT_MASK;
            case Csr.MIE -> mie = value & INTERRUPT_MASK;
            case Csr.MTVEC -> mtvec = value;
            case Csr.MCOUNTEREN -> mcounteren = value & COUNTER_MASK;

            case Csr.MSCRATCH -> mscratch = value;
            case Csr.MEPC -> mepc = value;
            case Csr.MCAUSE -> mcause = value;
            case Csr.MTVAL -> mtval = value;
            case Csr.MIP -> mip = value;

            case Csr.MENVCFG -> menvcfg = value & 0;

            case Csr.MCOUNTINHIBIT -> mcountinhibit = value & COUNTER_MASK;

            default -> {
                if (Csr.MCOUNTINHIBIT + 3 <= csr && csr <= Csr.MCOUNTINHIBIT + 31) {
                    // Hardware performance counters; not implemented, but read-only zero
                } else {
                    // CSR doesn't exist/is not implemented/is not writeable
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
            }
        }
    }

    public long handleTrap(RiscvTrapException trap) {
        return handleException(trap.cause.value, trap.getPc(), 0);
    }

    private long handleException(long exception, long epc, long tval) {
        // Switch to machine mode, storing the previous privilege in MPP
        mstatus = mstatus & ~(0b11 << 11) | ((long) mode.value << 11);
        mode = PrivilegeMode.Machine;

        // Disable interrupts, storing the previous interrupts-set bit in MPIE
        mstatus = mstatus & ~(0b10001 << 3) | ((mstatus & (0b1 << 3)) << 5);

        // Store the exception information in the relevant registers
        mcause = exception;
        mepc = epc;
        mtval = tval;

        // Return the program counter to jump to
        if (mtvec < 0) {
            // Vectored interrupt mode: add the exception number to the program counter
            return ((mtvec & Long.MAX_VALUE) + exception) << 2;
        } else {
            // Bare interrupt mode: jump to mtvec
            return mtvec << 2;
        }
    }
}
