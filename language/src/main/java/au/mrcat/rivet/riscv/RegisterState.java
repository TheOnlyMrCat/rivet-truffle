package au.mrcat.rivet.riscv;

import com.oracle.truffle.api.interop.TruffleObject;

import java.util.Arrays;

public final class RegisterState implements TruffleObject {
    public static final int ZERO = 0;
    public static final int RA = 1;
    public static final int SP = 2;
    public static final int GP = 3;
    public static final int TP = 4;
    public static final int T0 = 5;
    public static final int T1 = 6;
    public static final int T2 = 7;
    public static final int S0 = 8;
    public static final int S1 = 9;
    public static final int A0 = 10;
    public static final int A1 = 11;
    public static final int A2 = 12;
    public static final int A3 = 13;
    public static final int A4 = 14;
    public static final int A5 = 15;
    public static final int A6 = 16;
    public static final int A7 = 17;
    public static final int S2 = 18;
    public static final int S3 = 19;
    public static final int S4 = 20;
    public static final int S5 = 21;
    public static final int S6 = 22;
    public static final int S7 = 23;
    public static final int S8 = 24;
    public static final int S9 = 25;
    public static final int S10 = 26;
    public static final int S11 = 27;
    public static final int T3 = 28;
    public static final int T4 = 29;
    public static final int T5 = 30;
    public static final int T6 = 31;

    private final long[] registers;

    public RegisterState() {
        this.registers = new long[32];
    }

    public long getRegister(int r) {
        return registers[r];
    }

    public void setRegister(int r, long value) {
        if (r == 0) {
            return;
        }
        registers[r] = value;
    }

    public void dumpRegisterState() {
        System.err.println(Arrays.toString(registers));
    }
}
