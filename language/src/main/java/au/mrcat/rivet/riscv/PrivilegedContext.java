package au.mrcat.rivet.riscv;

import au.mrcat.rivet.RivetContext;

public class PrivilegedContext {
    public final PrivilegeMode mode;
    public final PrivilegeMode effectiveMode;
    public final AddressSpace addressSpace;
    public final long mstatus;
    public final long menvcfg;

    public PrivilegedContext(PrivilegeMode mode, PrivilegeMode effectiveMode, AddressSpace addressSpace, long mstatus, long menvcfg) {
        this.mode = mode;
        this.effectiveMode = effectiveMode;
        this.mstatus = mstatus;
        this.addressSpace = addressSpace;
        this.menvcfg = menvcfg;
    }

    public PrivilegeMode currentMode() {
        return mode;
    }

    public PrivilegeMode currentEffectiveMode() {
        if (mode == PrivilegeMode.Machine && (mstatus & (1L << 17)) != 0) {
            return PrivilegeMode.fromValue((int) ((mstatus >> 11) & 0b11));
        }
        return currentMode();
    }

    public PrivilegeMode currentEffectiveModeFor(AccessType accessType) {
        if (accessType == AccessType.EXECUTE) {
            return currentMode();
        }
        return currentEffectiveMode();
    }

    public AddressSpace currentAddressSpace(AccessType accessType) {
        if (currentEffectiveModeFor(accessType) == PrivilegeMode.Machine) {
            return BareAddressSpace.SINGLETON;
        }
        return addressSpace;
    }

    public boolean shouldTrapSret() {
        return mode == PrivilegeMode.User || (mode == PrivilegeMode.Supervisor && (mstatus & (1L << 22)) != 0);
    }

    public boolean shouldTrapSatpAccess() {
        return mode != PrivilegeMode.Machine && (mstatus & (1L << 20)) != 0;
    }

    public AccessType readAccessType() {
        if ((mstatus & (1 << 19)) != 0) {
            return AccessType.MXR_READ;
        }
        return AccessType.READ;
    }

    public boolean allowUserMemoryAccessFromSMode() {
        return (mstatus & (1L << 18)) != 0;
    }

    public boolean shouldUpdatePteAccessDirtyBits() {
        return (menvcfg & (1L << 61)) == 0;
    }

    public long translateReadAddress(long virtualAddress, RivetContext ctx) {
        return currentAddressSpace(readAccessType()).toPhysicalAddress(virtualAddress, readAccessType(), this, ctx.physicalMemory);
    }

    public long translateWriteAddress(long virtualAddress, RivetContext ctx) {
        return currentAddressSpace(AccessType.WRITE).toPhysicalAddress(virtualAddress, AccessType.WRITE, this, ctx.physicalMemory);
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof PrivilegedContext that)) return false;

        return mstatus == that.mstatus && menvcfg == that.menvcfg && mode == that.mode && effectiveMode == that.effectiveMode && addressSpace.equals(that.addressSpace);
    }

    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + effectiveMode.hashCode();
        result = 31 * result + addressSpace.hashCode();
        result = 31 * result + Long.hashCode(mstatus);
        result = 31 * result + Long.hashCode(menvcfg);
        return result;
    }
}
