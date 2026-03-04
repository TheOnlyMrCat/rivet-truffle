package au.mrcat.rivet.riscv;

import com.oracle.truffle.api.interop.TruffleObject;

import java.util.Arrays;

public final class RegisterState implements TruffleObject {
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
