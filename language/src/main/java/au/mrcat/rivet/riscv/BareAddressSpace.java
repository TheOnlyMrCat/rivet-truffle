package au.mrcat.rivet.riscv;

public class BareAddressSpace extends AddressSpace {
    public static BareAddressSpace SINGLETON = new BareAddressSpace();

    private BareAddressSpace() {}

    @Override
    public long toPhysicalAddress(long virtualAddress, AccessType accessType, PrivilegedState privilegedState, PhysicalMemory memory) {
        return virtualAddress;
    }
}
