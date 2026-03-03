package au.mrcat.rivet.nodes;

import au.mrcat.rivet.riscv.Opcode;
import au.mrcat.rivet.riscv.RegisterState;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;

public class RiscvDispatchNode extends Node {
    @CompilationFinal int[] instructions;

    public RiscvDispatchNode(int[] instructions) {
        this.instructions = instructions;
    }

    Object execute(RegisterState state) {
        for (int instruction : instructions) {
            int opcode = instruction & 0x7f;
            switch (opcode) {
                case Opcode.OP_IMM -> handleOpImm(state, instruction);
                case Opcode.OP -> handleOp(state, instruction);
            }
            state.dumpRegisterState();
        }
        return null;
    }

    void handleOpImm(RegisterState state, int instruction) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immSigned = instruction >> 20;
        long immUnsigned = instruction >>> 20;

        state.setRegister(rd, switch (funct3) {
            case Opcode.OpInt.ADD -> state.getRegister(rs1) + immSigned;
            case Opcode.OpInt.SLT -> state.getRegister(rs1) < immSigned ? 1 : 0;
            case Opcode.OpInt.SLTU -> Long.compareUnsigned(state.getRegister(rs1), immUnsigned) < 0 ? 1 : 0;
            case Opcode.OpInt.XOR -> state.getRegister(rs1) ^ immSigned;
            case Opcode.OpInt.OR -> state.getRegister(rs1) | immSigned;
            case Opcode.OpInt.AND -> state.getRegister(rs1) & immSigned;
            case Opcode.OpInt.SLL -> {
                if ((immUnsigned & ~0b111111) != 0) {
                    // FIXME: Implement traps
                }
                yield state.getRegister(rs1) << immUnsigned;
            }
            case Opcode.OpInt.SR -> {
                if ((immUnsigned & 0b101111_00000) != 0) {
                    // FIXME: Implement traps
                }

                long shift = immUnsigned & 0b111111;

                if ((immUnsigned & 0b010000_000000) != 0) {
                    yield state.getRegister(rs1) >> shift;
                } else {
                    yield state.getRegister(rs1) >>> shift;
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + funct3);
        });
    }

    void handleOp(RegisterState state, int instruction) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        int funct7 = instruction >>> 25;

        state.setRegister(rd, switch (funct7) {
            case Opcode.Op.INT -> switch (funct3) {
                case Opcode.OpInt.ADD -> state.getRegister(rs1) + state.getRegister(rs2);
                case Opcode.OpInt.SLT -> state.getRegister(rs1) < state.getRegister(rs2) ? 1 : 0;
                case Opcode.OpInt.SLTU -> Long.compareUnsigned(state.getRegister(rs1), state.getRegister(rs2)) < 0 ? 1 : 0;
                case Opcode.OpInt.XOR -> state.getRegister(rs1) ^ state.getRegister(rs2);
                case Opcode.OpInt.OR -> state.getRegister(rs1) | state.getRegister(rs2);
                case Opcode.OpInt.AND -> state.getRegister(rs1) & state.getRegister(rs2);
                case Opcode.OpInt.SLL -> state.getRegister(rs1) << state.getRegister(rs2);
                case Opcode.OpInt.SR -> state.getRegister(rs1) >> state.getRegister(rs2) & 0b111111;
                default -> throw new IllegalStateException("Unexpected value: " + funct3);
            };
            case Opcode.Op.NEG -> switch (funct3) {
                case Opcode.OpInt.ADD -> state.getRegister(rs1) - state.getRegister(rs2);
                case Opcode.OpInt.SR -> state.getRegister(rs1) >>> state.getRegister(rs2) & 0b111111;
                default -> /* FIXME: Implement traps */ state.getRegister(rd);
            };
            default -> /* FIXME: Implement traps */ state.getRegister(rd);
        });
    }
}
