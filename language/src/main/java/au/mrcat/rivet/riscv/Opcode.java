package au.mrcat.rivet.riscv;

public class Opcode {
    public static final int OP_IMM = 0b0010011;

    public static final int OP_IMM_ADDI = 0b000;
    public static final int OP_IMM_SLLI = 0b001;
    public static final int OP_IMM_SLTI = 0b010;
    public static final int OP_IMM_SLTIU = 0b011;
    public static final int OP_IMM_XORI = 0b100;
    public static final int OP_IMM_SRLI = 0b101;
    public static final int OP_IMM_ORI = 0b110;
    public static final int OP_IMM_ANDI = 0b111;
}
