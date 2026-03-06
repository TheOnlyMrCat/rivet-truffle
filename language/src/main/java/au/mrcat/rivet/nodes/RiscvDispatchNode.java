package au.mrcat.rivet.nodes;

import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.Opcode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

public class RiscvDispatchNode extends RivetNode {
    @CompilationFinal int[] instructions;

    public RiscvDispatchNode(int[] instructions) {
        this.instructions = instructions;
    }

    void execute(VirtualFrame frame) {
        for (int instruction : instructions) {
            int opcode = instruction & 0x7f;
            switch (opcode) {
                case Opcode.OP_IMM -> handleOpImm(frame, instruction);
                case Opcode.OP -> handleOp(frame, instruction);
                default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
            }
            this.currentLanguageContext().dumpRegisterState();
        }
    }

    void handleOpImm(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immSigned = instruction >> 20;
        long immUnsigned = instruction >>> 20;

        ctx.setRegister(rd, switch (funct3) {
            case Opcode.OpInt.ADD -> ctx.getRegister(rs1) + immSigned;
            case Opcode.OpInt.SLT -> ctx.getRegister(rs1) < immSigned ? 1 : 0;
            case Opcode.OpInt.SLTU -> Long.compareUnsigned(ctx.getRegister(rs1), immUnsigned) < 0 ? 1 : 0;
            case Opcode.OpInt.XOR -> ctx.getRegister(rs1) ^ immSigned;
            case Opcode.OpInt.OR -> ctx.getRegister(rs1) | immSigned;
            case Opcode.OpInt.AND -> ctx.getRegister(rs1) & immSigned;
            case Opcode.OpInt.SLL -> {
                if ((immUnsigned & ~0b111111) != 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
                yield ctx.getRegister(rs1) << immUnsigned;
            }
            case Opcode.OpInt.SR -> {
                if ((immUnsigned & 0b101111_00000) != 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }

                long shift = immUnsigned & 0b111111;

                if ((immUnsigned & 0b010000_000000) != 0) {
                    yield ctx.getRegister(rs1) >> shift;
                } else {
                    yield ctx.getRegister(rs1) >>> shift;
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + funct3);
        });
    }

    void handleOp(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        int funct7 = instruction >>> 25;

        ctx.setRegister(rd, switch (funct7) {
            case Opcode.Op.INT -> switch (funct3) {
                case Opcode.OpInt.ADD -> ctx.getRegister(rs1) + ctx.getRegister(rs2);
                case Opcode.OpInt.SLT -> ctx.getRegister(rs1) < ctx.getRegister(rs2) ? 1 : 0;
                case Opcode.OpInt.SLTU -> Long.compareUnsigned(ctx.getRegister(rs1), ctx.getRegister(rs2)) < 0 ? 1 : 0;
                case Opcode.OpInt.XOR -> ctx.getRegister(rs1) ^ ctx.getRegister(rs2);
                case Opcode.OpInt.OR -> ctx.getRegister(rs1) | ctx.getRegister(rs2);
                case Opcode.OpInt.AND -> ctx.getRegister(rs1) & ctx.getRegister(rs2);
                case Opcode.OpInt.SLL -> ctx.getRegister(rs1) << ctx.getRegister(rs2);
                case Opcode.OpInt.SR -> ctx.getRegister(rs1) >> ctx.getRegister(rs2) & 0b111111;
                default -> throw new IllegalStateException("Unexpected value: " + funct3);
            };
            case Opcode.Op.NEG -> switch (funct3) {
                case Opcode.OpInt.ADD -> ctx.getRegister(rs1) - ctx.getRegister(rs2);
                case Opcode.OpInt.SR -> ctx.getRegister(rs1) >>> ctx.getRegister(rs2) & 0b111111;
                default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
            };
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        });
    }
}
