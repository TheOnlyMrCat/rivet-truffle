package au.mrcat.rivet.riscv;

import au.mrcat.rivet.runtime.RiscvTrapException;

public final class PrivilegedState {
    private PrivilegeMode mode = PrivilegeMode.Machine;
    private long mscratch;

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
            case Csr.MSCRATCH -> { return mscratch; }

            // CSR doesn't exist/is not implemented/is not readable
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
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
            case Csr.MSCRATCH -> mscratch = value;

            // CSR doesn't exist/is not implemented/is not writeable
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }
    }
}
