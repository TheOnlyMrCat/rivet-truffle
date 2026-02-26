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
                case Opcode.OP_IMM:
                    handleOpImm(state, instruction);
            }
            state.dumpRegisterState();
        }
        return null;
    }

    void handleOpImm(RegisterState state, int instruction) {
        int rd = (instruction >> 7) & 0b11111;
        int func3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immSigned = instruction >> 20;
        long immUnsigned = instruction >>> 20;

        state.setRegister(rd, switch (func3) {
            case Opcode.OP_IMM_ADDI: yield state.getRegister(rs1) + immSigned;
            case Opcode.OP_IMM_SLTI: yield state.getRegister(rs1) < immSigned ? 1 : 0;
            case Opcode.OP_IMM_SLTIU: yield Long.compareUnsigned(state.getRegister(rs1), immUnsigned) < 0 ? 1 : 0;
            case Opcode.OP_IMM_XORI: yield state.getRegister(rs1) ^ immSigned;
            case Opcode.OP_IMM_ORI: yield state.getRegister(rs1) | immSigned;
            case Opcode.OP_IMM_ANDI: yield state.getRegister(rs1) & immSigned;
            case Opcode.OP_IMM_SLLI:
                if ((immUnsigned & ~0b111111) != 0) {
                    // FIXME: Implement traps
                }
                yield state.getRegister(rs1) << immUnsigned;
            case Opcode.OP_IMM_SRLI:
                if ((immUnsigned & 0b101111_00000) != 0) {
                    // FIXME: Implement traps
                }

                long shift = immUnsigned & 0b111111;

                if ((immUnsigned & 0b010000_000000) != 0) {
                    yield state.getRegister(rs1) >> shift;
                } else {
                    yield state.getRegister(rs1) >>> shift;
                }
            default:
                throw new IllegalStateException("Unexpected value: " + func3);
        });
    }
}
