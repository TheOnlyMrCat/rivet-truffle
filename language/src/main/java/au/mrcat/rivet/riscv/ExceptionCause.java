package au.mrcat.rivet.riscv;

public enum ExceptionCause {
    InstructionAddressMisaligned(0),
    InstructionAccessFault(1),
    IllegalInstruction(2),
    Breakpoint(3),
    LoadAddressMisaligned(4),
    LoadAccessFault(5),
    StoreAmoAddressMisaligned(6),
    StoreAmoAccessFault(7),
    EnvironmentCallFromUMode(8),
    EnvironmentCallFromSMode(9),
    EnvironmentCallFromMMode(11),
    InstructionPageFault(12),
    LoadPageFault(13),
    StoreAmoPageFault(15),
    DoubleTrap(16),
    SoftwareCheck(18),
    HardwareError(19),
    SupervisorSoftwareInterrupt(Long.MIN_VALUE + 1),
    MachineSoftwareInterrupt(Long.MIN_VALUE + 3),
    SupervisorTimerInterrupt(Long.MIN_VALUE + 5),
    MachineTimerInterrupt(Long.MIN_VALUE + 7),
    SupervisorExternalInterrupt(Long.MIN_VALUE + 9),
    MachineExternalInterrupt(Long.MIN_VALUE + 11),
    CounterOverflowInterrupt(Long.MIN_VALUE + 13),
    ;

    public final long value;


    ExceptionCause(long value) {
        this.value = value;
    }
}
