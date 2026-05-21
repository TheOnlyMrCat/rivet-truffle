package au.mrcat.rivet.riscv;

public enum PrivilegeMode {
    User(0),
    Supervisor(1),
    Machine(3);

    public final int value;

    PrivilegeMode(int value) {
        this.value = value;
    }

    public static PrivilegeMode fromValue(int value) {
        switch (value) {
            case 0 -> { return User; }
            case 1 -> { return Supervisor; }
            case 3 -> { return Machine; }
            default -> throw new IllegalStateException("Unexpected value: " + value);
        }
    }
}
