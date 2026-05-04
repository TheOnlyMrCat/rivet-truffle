package au.mrcat.rivet.riscv;

public enum PrivilegeMode {
    User(0),
    Supervisor(1),
    Machine(3);

    public final int value;

    PrivilegeMode(int value) {
        this.value = value;
    }
}
