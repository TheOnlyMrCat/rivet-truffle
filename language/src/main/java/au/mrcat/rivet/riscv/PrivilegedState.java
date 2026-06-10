package au.mrcat.rivet.riscv;

import au.mrcat.rivet.nodes.RivetBasicBlockNode;
import au.mrcat.rivet.runtime.RiscvDoubleTrapException;
import au.mrcat.rivet.runtime.RiscvTrapException;

public final class PrivilegedState {
    private PrivilegeMode mode = PrivilegeMode.Machine;

    private static final long MSTATUS_FIELDS_MASK =
            0b0100_0000_0000_0000_0000_0111_1110_0001_1001_1010_1010L;
    private static final long MSTATUS_RESET_VALUE = 0b0100_0000_1010_0000_0000_0000_0000_0001_1000_1010_0000L;
    private static final long EXCEPTION_MASK = 0b1101_1011_1011_1111_1111;
    private static final long INTERRUPT_MASK = 0;
    private static final long COUNTER_MASK = 0b101;

    private long mstatus = MSTATUS_RESET_VALUE;
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

    private long mcycle;
    private long minstret;

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
            case Csr.CYCLE -> {
                if (mode != PrivilegeMode.Machine && (mcounteren & (1 << 0)) == 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
                return mcycle;
            }
            case Csr.TIME -> {
                if (mode != PrivilegeMode.Machine && (mcounteren & (1 << 1)) == 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
                return System.nanoTime();
            }
            case Csr.INSTRET -> {
                if (mode != PrivilegeMode.Machine && (mcounteren & (1 << 2)) == 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
                return minstret;
            }

            // Machine-level CSRs
            case Csr.MVENDORID -> { return 0; }
            case Csr.MARCHID -> { return 0; }
            case Csr.MIMPID -> { return 0; }
            case Csr.MHARTID -> { return 0; }
            case Csr.MCONFIGPTR -> { return 0; }

            case Csr.MSTATUS -> { return mstatus; }
            case Csr.MISA -> { return 0x8000000000000000L | 0b00_0001_0000_0001_0001_0000_0101; }
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

            case Csr.MCYCLE -> {
                return mcycle;
            }
            case Csr.MINSTRET -> {
                return minstret;
            }

            case Csr.MCOUNTINHIBIT -> { return mcountinhibit; }

            default -> {
                if (Csr.MCYCLE + 3 <= csr && csr <= Csr.MCYCLE + 31 || Csr.MCOUNTINHIBIT + 3 <= csr && csr <= Csr.MCOUNTINHIBIT + 31) {
                    // Hardware performance counters: not implemented, but read-only zero
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
                long prevMstatus = mstatus;
                // Mask out invalid fields
                mstatus = value & MSTATUS_FIELDS_MASK;
                // Set SXL and UXL to 64-bit
                mstatus |= 0b1010L << 32L;
                // Restrict MPP to valid privilege modes only
                long newMpp = ((mstatus >> 11) & 0b11);
                if (!(newMpp == 0b00 || newMpp == 0b11)) {
                    mstatus = mstatus & ~(0b11 << 11) | prevMstatus & (0b11 << 11);
                }
                // If MDT was just set, clear MIE
                if ((prevMstatus & (1L << 42)) == 0 && (mstatus & (1L << 42)) != 0) {
                    mstatus &= ~(1 << 3);
                }
            }
            case Csr.MISA -> { /* Do nothing */ }
            case Csr.MEDELEG -> medeleg = value & EXCEPTION_MASK;
            case Csr.MIDELEG -> mideleg = value & INTERRUPT_MASK;
            case Csr.MIE -> mie = value & INTERRUPT_MASK;
            case Csr.MTVEC -> mtvec = value;
            case Csr.MCOUNTEREN -> mcounteren = value & COUNTER_MASK;

            case Csr.MSCRATCH -> mscratch = value;
            case Csr.MEPC -> mepc = value & ~0b1;
            case Csr.MCAUSE -> mcause = value;
            case Csr.MTVAL -> mtval = value;
            case Csr.MIP -> mip = value;

            case Csr.MENVCFG -> menvcfg = value & 0;

            case Csr.MCYCLE -> mcycle = value;
            case Csr.MINSTRET -> minstret = value;

            case Csr.MCOUNTINHIBIT -> mcountinhibit = value & COUNTER_MASK;

            default -> {
                if (Csr.MCYCLE + 3 <= csr && csr <= Csr.MCYCLE + 31 || Csr.MCOUNTINHIBIT + 3 <= csr && csr <= Csr.MCOUNTINHIBIT + 31) {
                    // Hardware performance counters: not implemented, but read-only zero
                } else {
                    // CSR doesn't exist/is not implemented/is not writeable
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
            }
        }
    }

    public long handleTrap(RiscvTrapException trap) {
        return handleException(trap.cause.value, trap.getPc(), trap.getTval());
    }

    private long handleException(long exception, long epc, long tval) {
        // If MDT is 1, this is a double-trap. Abort execution
        if ((mstatus & (1L << 42)) != 0) {
            throw new RiscvDoubleTrapException();
        }

        // Otherwise, set MDT to 1
        mstatus = mstatus | (1L << 42);

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
        long mtvec_mode = mtvec & 0b11;
        long mtvec_addr = mtvec & ~0b11;
        if (mtvec_mode == 1) {
            return mtvec_addr + exception * 4;
        } else {
            return mtvec_addr;
        }
    }

    public long handleMret() {
        // Switch to the privilege mode specified by MPP
        mode = PrivilegeMode.fromValue((int) ((mstatus >> 11) & 0b11));

        // Set MPP to the lowest supported privilege mode (U mode)
        mstatus = mstatus & ~(0b11 << 11);

        // Set MIE to MPIE and MPIE to 1
        mstatus = mstatus & ~(1 << 3) | (mstatus >> 4) & 0b1 | (1 << 7);

        // Clear MDT
        mstatus = mstatus & ~(1L << 42);

        // Set MPRV to 0 if our new privilege mode is less than M
        if (mode != PrivilegeMode.Machine) {
            mstatus = mstatus & ~(1 << 17);
        }

        return mepc;
    }

    public void stepPerformanceCounters(short instructionsRetired) {
        mcycle += instructionsRetired;
        minstret += instructionsRetired;
    }

    public void stepPerformanceCounters(short cycles, short instructionsRetired) {
        mcycle += cycles;
        minstret += instructionsRetired;
    }

    public void reset() {
        mstatus = MSTATUS_RESET_VALUE;
        mode = PrivilegeMode.Machine;
        mcause = 0;
    }
}
