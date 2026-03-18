package au.mrcat.rivet.riscv;

public class Opcode {
    public static final int LOAD = 0b0000011;
    public static final int MISC_MEM = 0b0001111;
    public static final int OP_IMM = 0b0010011;
    public static final int AUIPC = 0b0010111;
    public static final int OP_IMM_32 = 0b0011011;
    public static final int STORE = 0b0100011;
    public static final int OP = 0b0110011;
    public static final int LUI = 0b0110111;
    public static final int OP_32 = 0b0111011;
    public static final int BRANCH = 0b1100011;
    public static final int JALR = 0b1100111;
    public static final int JAL = 0b1101111;
    public static final int SYSTEM = 0b1110011;

    public static class MemWidth {
        public static final int BYTE = 0b000;
        public static final int HALF = 0b001;
        public static final int WORD = 0b010;
        public static final int DOUBLE = 0b011;
        public static final int BYTE_UNSIGNED = 0b100;
        public static final int HALF_UNSIGNED = 0b101;
        public static final int WORD_UNSIGNED = 0b110;
    }

    public static class MiscMem {
        public static final int FENCE = 0b000;
        public static final int FENCE_I = 0b001;
    }

    public static class Op {
        public static final int INT = 0b0000000;
        public static final int MUL_DIV = 0b0000001;
        public static final int NEG = 0b0100000;
    }

    public static class OpInt {
        public static final int ADD = 0b000;
        public static final int SLL = 0b001;
        public static final int SLT = 0b010;
        public static final int SLTU = 0b011;
        public static final int XOR = 0b100;
        public static final int SR = 0b101;
        public static final int OR = 0b110;
        public static final int AND = 0b111;
    }

    public static class Branch {
        public static final int BEQ = 0b000;
        public static final int BNE = 0b001;
        public static final int BLT = 0b100;
        public static final int BGE = 0b101;
        public static final int BLTU = 0b110;
        public static final int BGEU = 0b111;
    }

    public static class System {
        public static final int PRIV = 0b000;
    }

    public static class Priv {
        public static final int ECALL = 0b000000000000;
        public static final int EBREAK = 0b000000000001;
        public static final int WFI = 0b000100000101;
    }
}
