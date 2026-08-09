package au.mrcat.rivet.riscv;

import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives;

public class Sv39AddressSpace extends AddressSpace {
    private final long rootPageTableAddr;

    public Sv39AddressSpace(long rootPageTablePpn) {
        this.rootPageTableAddr = rootPageTablePpn;
    }

    @Override
    public long toPhysicalAddress(long virtualAddress, AccessType accessType, PrivilegedState privilegedState, PhysicalMemory memory) {
        // Decode and check the virtual address
        long vte = virtualAddress >> 12;
        if (vte < -(1 << 27) || vte > (1 << 26)) {
            throw new RiscvTrapException(accessType.pageFaultCause);
        }

        long currentAddr = rootPageTableAddr;
        long pteAddr = rootPageTableAddr;
        long pte = 0;
        int pageLevel = 3;
        while ((pte & 0b1010) == 0) {
            if (pageLevel == 0) {
                throw new RiscvTrapException(ExceptionCause.InstructionPageFault);
            }
            pageLevel -= 1;
            pteAddr = currentAddr + ((vte & 0b111111111L << pageLevel * 9) >> pageLevel * 9) * Long.BYTES;
            try {
                pte = memory.readLong(pteAddr);
            } catch (RiscvTrapException e) {
                assert e.cause != ExceptionCause.LoadAddressMisaligned;
                throw new RiscvTrapException(accessType.pageFaultCause);
            }
            if ((pte & 0b1) != 1 || (pte & 0b110) == 0b100 || ((pte >> 54) & 0b1111111) != 0) {
                throw new RiscvTrapException(accessType.pageFaultCause);
            }
            currentAddr = (pte & ((1L << 44) - 1) << 10) << 2;
        }

        // Check access permissions
        switch (privilegedState.currentMode()) {
            case User -> {
                if ((pte & (1 << 4)) == 0) {
                    throw new RiscvTrapException(accessType.pageFaultCause);
                }
            }
            case Supervisor -> {
                if ((pte & (1 << 4)) != 0 && (!privilegedState.allowUserMemoryAccessFromSMode() || accessType == AccessType.EXECUTE)) {
                    throw new RiscvTrapException(accessType.pageFaultCause);
                }
            }
        }
        if ((pte & accessType.pteBit) == 0) {
            throw new RiscvTrapException(accessType.pageFaultCause);
        }

        // Validate superpage alignment
        long superpageMask = (1L << pageLevel * 9) - 1;
        if ((pte & superpageMask << 10) != 0) {
            throw new RiscvTrapException(accessType.pageFaultCause);
        }

        // Check accessed and dirty flags
        if ((pte & 1 << 6) == 0 || accessType == AccessType.WRITE && (pte & 1 << 7) == 0) {
            if (privilegedState.shouldUpdatePteAccessDirtyBits()) {
                // Svade extension
                throw new RiscvTrapException(accessType.pageFaultCause);
            }

            // Svadu extension
            // Skip checking the page table hasn't been modified, because we don't
            // support multi-hart yet. Theoretically it could be modified through
            // DMA, but we'll just pretend the guest is never going to do that...
            pte |= 1 << 6;
            if (accessType == AccessType.WRITE) {
                pte |= 1 << 7;
            }
            memory.writeLong(pteAddr, pte);
        }

        currentAddr |= (vte & superpageMask) << 12;
        currentAddr |= virtualAddress & (1 << 12) - 1;
        return currentAddr;
    }
}
