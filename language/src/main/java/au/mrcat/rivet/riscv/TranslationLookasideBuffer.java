package au.mrcat.rivet.riscv;

import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives;

public class TranslationLookasideBuffer extends AddressSpace {
    private record Entry(long virtualPage, long physicalPage, boolean isUserPage) {
    }

    private static final int TLB_SIZE = 1 << 6;

    private final Sv39AddressSpace baseAddressSpace;

    private Entry[] read = new Entry[TLB_SIZE];
    private Entry[] write = new Entry[TLB_SIZE];
    private Entry[] exec = new Entry[TLB_SIZE];

    public TranslationLookasideBuffer(Sv39AddressSpace baseAddressSpace) {
        this.baseAddressSpace = baseAddressSpace;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public long toPhysicalAddress(long virtualAddress, AccessType accessType, PrivilegedContext privilegedState) throws RiscvTrapException {
        int tlb_key = (int) ((virtualAddress >> 12) & TLB_SIZE - 1);
        Entry[] buffer = switch (accessType) {
            case READ, MXR_READ -> read;
            case WRITE -> write;
            case EXECUTE -> exec;
        };
        if (buffer[tlb_key] != null && buffer[tlb_key].virtualPage == (virtualAddress & ~0xfff)) {
            switch (privilegedState.currentEffectiveModeFor(accessType)) {
                case User -> {
                    if (!buffer[tlb_key].isUserPage) {
                        throw new RiscvTrapException(accessType.pageFaultCause);
                    }
                }
                case Supervisor -> {
                    if (buffer[tlb_key].isUserPage && (!privilegedState.allowUserMemoryAccessFromSMode() || accessType == AccessType.EXECUTE)) {
                        throw new RiscvTrapException(accessType.pageFaultCause);
                    }
                }
            }
            return buffer[tlb_key].physicalPage | virtualAddress & 0xfff;
        }

        Sv39AddressSpace.TranslationDetails details = new Sv39AddressSpace.TranslationDetails();
        long physicalAddress = baseAddressSpace.toPhysicalAddress(virtualAddress, accessType, privilegedState, details);

        // If we made it here, we didn't page fault, so update the TLB entry
        buffer[tlb_key] = new Entry(virtualAddress & ~0xfff, physicalAddress & ~0xfff, details.isUserPage());
        return physicalAddress;
    }

    @Override
    public boolean isAccessContiguous(long virtualAddress, MemoryWidth width) {
        return baseAddressSpace.isAccessContiguous(virtualAddress, width);
    }

    public void clear() {
        read = new Entry[TLB_SIZE];
        write = new Entry[TLB_SIZE];
        exec = new Entry[TLB_SIZE];
    }

    public Sv39AddressSpace getBaseAddressSpace() {
        return baseAddressSpace;
    }
}
