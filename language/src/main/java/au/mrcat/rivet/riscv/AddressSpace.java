package au.mrcat.rivet.riscv;

public abstract class AddressSpace {
    public abstract long toPhysicalAddress(long virtualAddress, AccessType accessType, PrivilegedContext privilegedState);
    public abstract boolean isAccessContiguous(long virtualAddress, MemoryWidth width);
}
