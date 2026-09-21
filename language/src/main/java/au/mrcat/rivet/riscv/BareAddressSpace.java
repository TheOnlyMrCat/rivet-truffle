package au.mrcat.rivet.riscv;

public class BareAddressSpace extends AddressSpace {
    public static BareAddressSpace SINGLETON = new BareAddressSpace();

    private BareAddressSpace() {}

    @Override
    public long toPhysicalAddress(long virtualAddress, AccessType accessType, PrivilegedContext privilegedState, PhysicalMemory memory) {
        return virtualAddress;
    }

    @Override
    public boolean isAccessContiguous(long virtualAddress, MemoryWidth width) {
        return true;
    }
}
