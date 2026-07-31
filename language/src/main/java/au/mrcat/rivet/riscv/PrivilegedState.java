package au.mrcat.rivet.riscv;

import au.mrcat.rivet.runtime.RiscvDoubleTrapException;
import au.mrcat.rivet.runtime.RiscvTrapException;

public final class PrivilegedState {
    private PrivilegeMode mode = PrivilegeMode.Machine;

    private static final long MSTATUS_WRITABLE_MASK =
            0b00000000_00000000_00000100_00000000_00000000_01111010_00011001_10101010L;
    private static final long SSTATUS_VISIBLE_MASK =
            0b10000000_00000000_00000000_00000011_00000001_10001101_11100111_01100010L;
    private static final long MSTATUS_RESET_VALUE =
            0b00000000_00000000_00000100_00001010_00000000_00000000_00011000_10100000L;
    private static final long EXCEPTION_MASK = 0b1101_1011_1011_1111_1111;
    private static final long INTERRUPT_MASK = 0;
    private static final long COUNTER_MASK = 0b111;

    private long sie;
    private long stvec;
    private long scounteren;

    private long sscratch;
    private long sepc;
    private long scause;
    private long stval;

    private long senvcfg;
    private long satp;

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

    public boolean shouldTrapSret() {
        return mode == PrivilegeMode.User || (mode == PrivilegeMode.Supervisor && (mstatus & (1 << 22)) != 0);
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
        long counterenMask = (mode != PrivilegeMode.Machine ? mcounteren : Long.MAX_VALUE) & (mode == PrivilegeMode.User ? scounteren : Long.MAX_VALUE);
        switch (csr) {
            // User-level CSRs
            case Csr.CYCLE -> {
                if ((counterenMask & (1 << 0)) == 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
                return mcycle;
            }
            case Csr.TIME -> {
                if ((counterenMask & (1 << 1)) == 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
                return System.nanoTime();
            }
            case Csr.INSTRET -> {
                if ((counterenMask & (1 << 2)) == 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
                return minstret;
            }

            // Supervisor-level CSRs
            case Csr.SSTATUS -> { return mstatus & SSTATUS_VISIBLE_MASK; }
            case Csr.SIE -> { return mie & mideleg; }
            case Csr.STVEC -> { return stvec; }
            case Csr.SCOUNTEREN -> { return scounteren; }

            case Csr.SSCRATCH -> { return sscratch; }
            case Csr.SEPC -> { return sepc; }
            case Csr.SCAUSE -> { return scause; }
            case Csr.STVAL -> { return stval; }
            case Csr.SIP -> { return mip & mideleg; }

            case Csr.SENVCFG -> { return senvcfg; }
            case Csr.SATP -> { return satp; }

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
            // Supervisor-level CSRs
            case Csr.SSTATUS -> {
                long prevMstatus = mstatus;
                // Mask out fields that are invisible to S-mode
                mstatus = value & SSTATUS_VISIBLE_MASK & MSTATUS_WRITABLE_MASK;
                // Preserve non-writeable fields
                mstatus |= prevMstatus & ~(SSTATUS_VISIBLE_MASK & MSTATUS_WRITABLE_MASK);
            }
            case Csr.SIE -> sie = value & mideleg;
            case Csr.STVEC -> stvec = value;
            case Csr.SCOUNTEREN -> scounteren = value;

            case Csr.SSCRATCH -> sscratch = value;
            case Csr.SEPC -> sepc = value;
            case Csr.SCAUSE -> scause = value;
            case Csr.STVAL -> stval = value;
            case Csr.SIP -> mip = value & mideleg;

            case Csr.SENVCFG -> senvcfg = value;
            case Csr.SATP -> {
                // Do nothing; SvBare is the only supported legal address translation mode at the moment
            }

            // Machine-level CSRs
            case Csr.MSTATUS -> {
                long prevMstatus = mstatus;
                // Mask out invalid fields
                mstatus = value & MSTATUS_WRITABLE_MASK;
                // Preserve non-writeable fields
                mstatus |= prevMstatus & ~MSTATUS_WRITABLE_MASK;
                // Restrict MPP to valid privilege modes only
                long newMpp = ((mstatus >> 11) & 0b11);
                if (newMpp == 0b10) {
                    mstatus = mstatus & ~(0b11 << 11) | prevMstatus & (0b11 << 11);
                }
                // If MDT was just set, clear MIE
                if ((prevMstatus & (1L << 42)) == 0 && (mstatus & (1L << 42)) != 0) {
                    mstatus &= ~(1 << 3);
                }
                // Only allow MIE to be explicitly set if MDT is 0
                if ((prevMstatus & (1L << 3)) == 0 && (mstatus & (1L << 3)) != 0 && (mstatus & (1L << 42)) != 0) {
                    mstatus &= ~(1 << 3);
                }
            }
            case Csr.MISA -> { /* Do nothing */ }
            case Csr.MEDELEG -> medeleg = value & EXCEPTION_MASK;
            case Csr.MIDELEG -> {
                mideleg = value & INTERRUPT_MASK;
                sie &= mideleg;
            }
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
        long cause = trap.cause.value;
        if (mode != PrivilegeMode.Machine && (medeleg & (1L << cause)) != 0) {
            return handleSupervisorException(cause, trap.getPc(), trap.getTval());
        }
        return handleMachineException(cause, trap.getPc(), trap.getTval());
    }

    private long handleSupervisorException(long exception, long epc, long tval) {
        // Switch to supervisor mode, storing the previous privilege in SPP
        assert mode.value > 2;
        mstatus = mstatus & ~(0b1 << 8) | ((long) mode.value << 8);
        mode = PrivilegeMode.Supervisor;

        // Disable interrupts, storing the previous interrupts-set bit in SPIE
        mstatus = mstatus & ~(0b10001 << 1) | ((mstatus & (0b1 << 1)) << 4);

        // Store the exception information in the relevant registers
        scause = exception;
        sepc = epc;
        stval = tval;

        // Return the program counter to jump to
        long stvec_mode = stvec & 0b11;
        long stvec_addr = stvec & ~0b11;
        if (stvec_mode == 1) {
            // Vectored interrupts mode
            return stvec_addr + (exception & Long.MAX_VALUE) * 4;
        } else {
            return stvec_addr;
        }
    }

    private long handleMachineException(long exception, long epc, long tval) {
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
        mstatus = mstatus & ~(0b10001 << 3) | ((mstatus & (0b1 << 3)) << 4);

        // Store the exception information in the relevant registers
        mcause = exception;
        mepc = epc;
        mtval = tval;

        // Return the program counter to jump to
        long mtvec_mode = mtvec & 0b11;
        long mtvec_addr = mtvec & ~0b11;
        if (mtvec_mode == 1 && exception < 0) {
            // Vectored interrupts mode
            return mtvec_addr + (exception & Long.MAX_VALUE) * 4;
        } else {
            return mtvec_addr;
        }
    }

    public long handleSret() {
        // Switch to the privilege mode specified by SPP
        mode = PrivilegeMode.fromValue((int) ((mstatus >> 8) & 0b1));

        // Set SPP to the lowest supported privilege mode (U mode)
        mstatus = mstatus & ~(0b1 << 8);

        // Set SIE to SPIE and SPIE to 1
        mstatus = mstatus & ~(1 << 1) | (mstatus >> 4) & (1 << 1) | (1 << 5);

        // Set MPRV to 0 if our new privilege mode is less than M
        if (mode != PrivilegeMode.Machine) {
            mstatus = mstatus & ~(1 << 17);
        }

        return sepc;
    }

    public long handleMret() {
        // Switch to the privilege mode specified by MPP
        mode = PrivilegeMode.fromValue((int) ((mstatus >> 11) & 0b11));

        // Set MPP to the lowest supported privilege mode (U mode)
        mstatus = mstatus & ~(0b11 << 11);

        // Set MIE to MPIE and MPIE to 1
        mstatus = mstatus & ~(1 << 3) | (mstatus >> 4) & (1 << 3) | (1 << 7);

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
