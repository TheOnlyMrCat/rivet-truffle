package au.mrcat.rivet.riscv;

public class Csr {
    // Unprivileged Counter/Timers
    public static final int CYCLE = 0xC00;
    public static final int TIME = 0xC01;
    public static final int INSTRET = 0xC02;

    // Machine Information Registers
    public static final int MVENDORID = 0xF11;
    public static final int MARCHID = 0xF12;
    public static final int MIMPID = 0xF13;
    public static final int MHARTID = 0xF14;
    public static final int MCONFIGPTR = 0xF15;

    // Machine Trap Setup
    public static final int MSTATUS = 0x300;
    public static final int MISA = 0x301;
    public static final int MEDELEG = 0x302;
    public static final int MIDELEG = 0x303;
    public static final int MIE = 0x304;
    public static final int MTVEC = 0x305;
    public static final int MCOUNTEREN = 0x306;

    // Machine Trap Handling
    public static final int MSCRATCH = 0x340;
    public static final int MEPC = 0x341;
    public static final int MCAUSE = 0x342;
    public static final int MTVAL = 0x343;
    public static final int MIP = 0x344;

    // Machine Configuration
    public static final int MENVCFG = 0x30A;
    public static final int MSECCFG = 0x747;
    public static final int PMPCFG0 = 0x3A0;
    public static final int PMPADDR0 = 0x3B0;

    // Machine Counter/Timers
    public static final int MCYCLE = 0xB00;
    public static final int MINSTRET = 0xB02;

    // Machine Counter Setup
    public static final int MCOUNTINHIBIT = 0x320;
}
