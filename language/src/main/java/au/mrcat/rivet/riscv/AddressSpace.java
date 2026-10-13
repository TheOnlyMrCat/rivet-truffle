package au.mrcat.rivet.riscv;

import au.mrcat.rivet.runtime.RiscvTrapException;

public abstract class AddressSpace {
    public abstract long toPhysicalAddress(long virtualAddress, AccessType accessType, PrivilegedContext privilegedState) throws RiscvTrapException;
    public abstract boolean isAccessContiguous(long virtualAddress, MemoryWidth width);
}
