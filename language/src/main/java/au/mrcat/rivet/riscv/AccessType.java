package au.mrcat.rivet.riscv;

public enum AccessType {
    READ((byte) 0b10, ExceptionCause.LoadPageFault, ExceptionCause.LoadAccessFault),
    MXR_READ((byte) 0b1010, ExceptionCause.LoadPageFault, ExceptionCause.LoadAccessFault),
    WRITE((byte) 0b100, ExceptionCause.StoreAmoPageFault, ExceptionCause.StoreAmoAccessFault),
    EXECUTE((byte) 0b1000, ExceptionCause.InstructionPageFault, ExceptionCause.InstructionAccessFault);
    
    public final byte pteBit;
    public final ExceptionCause pageFaultCause;
    public final ExceptionCause accessFaultCause;

    AccessType(byte pteBit, ExceptionCause pageFaultCause, ExceptionCause accessFaultCause) {
        this.pteBit = pteBit;
        this.pageFaultCause = pageFaultCause;
        this.accessFaultCause = accessFaultCause;
    }
}
