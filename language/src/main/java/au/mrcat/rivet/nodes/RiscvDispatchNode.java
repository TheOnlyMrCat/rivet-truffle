package au.mrcat.rivet.nodes;

import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.Opcode;
import au.mrcat.rivet.runtime.RiscvJumpException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

public class RiscvDispatchNode extends RivetNode {
    @CompilationFinal int[] instructions;
    private final long baseAddress;

    public RiscvDispatchNode(int[] instructions, long baseAddress) {
        this.instructions = instructions;
        this.baseAddress = baseAddress;
    }

    void execute(VirtualFrame frame) {
        for (int i = 0; i < instructions.length; i++) {
            int instruction = instructions[i];
            int opcode = instruction & 0x7f;
            switch (opcode) {
                case Opcode.OP_IMM -> handleOpImm(frame, instruction);
                case Opcode.AUIPC -> handleAuipc(frame, i, instruction);
                case Opcode.OP_IMM_32 -> handleOpImm32(frame, instruction);
                case Opcode.OP -> handleOp(frame, instruction);
                case Opcode.LUI -> handleLui(frame, instruction);
                case Opcode.OP_32 -> handleOp32(frame, instruction);
                case Opcode.BRANCH -> handleBranch(frame, i, instruction);
                case Opcode.JAL -> handleJal(frame, i, instruction);
                case Opcode.JALR -> handleJalr(frame, i, instruction);
                case Opcode.SYSTEM -> handleSystem(frame, instruction);
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
                if ((immUnsigned & 0b101111_000000) != 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }

                long shift = immUnsigned & 0b111111;

                if ((immUnsigned & 0b010000_000000) != 0) {
                    // Arithmetic right shift
                    yield ctx.getRegister(rs1) >> shift;
                } else {
                    // Logical right shift
                    yield ctx.getRegister(rs1) >>> shift;
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + funct3);
        });
    }

    void handleAuipc(VirtualFrame frame, int i, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        long immSigned = (instruction >> 12) << 12;

        ctx.setRegister(rd, baseAddress + i + immSigned);
    }

    void handleOpImm32(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int immSigned = instruction >> 20;
        int immUnsigned = instruction >>> 20;

        ctx.setRegister(rd, switch (funct3) {
            case Opcode.OpInt.ADD -> (int) ctx.getRegister(rs1) + immSigned;
            case Opcode.OpInt.SLL -> {
                if ((immUnsigned & ~0b111111_1) != 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
                yield ctx.getRegister(rs1) << immUnsigned;
            }
            case Opcode.OpInt.SR -> {
                if ((immUnsigned & 0b1011111_00000) != 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }

                long shift = immUnsigned & 0b11111;

                if ((immUnsigned & 0b0100000_00000) != 0) {
                    // Arithmetic right shift
                    yield ctx.getRegister(rs1) >> shift;
                } else {
                    // Logical right shift
                    yield ctx.getRegister(rs1) >>> shift;
                }
            }
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
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
                case Opcode.OpInt.SR -> ctx.getRegister(rs1) >>> (ctx.getRegister(rs2) & 0b111111);
                default -> throw new IllegalStateException("Unexpected value: " + funct3);
            };
            case Opcode.Op.NEG -> switch (funct3) {
                case Opcode.OpInt.ADD -> ctx.getRegister(rs1) - ctx.getRegister(rs2);
                case Opcode.OpInt.SR -> ctx.getRegister(rs1) >> (ctx.getRegister(rs2) & 0b111111);
                default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
            };
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        });
    }

    void handleOp32(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        int funct7 = instruction >>> 25;

        ctx.setRegister(rd, switch (funct7) {
            case Opcode.Op.INT -> switch (funct3) {
                case Opcode.OpInt.ADD -> (int) ctx.getRegister(rs1) + (int) ctx.getRegister(rs2);
                case Opcode.OpInt.SLL -> (int) ctx.getRegister(rs1) << (ctx.getRegister(rs2) & 0b11111);
                case Opcode.OpInt.SR -> (int) ctx.getRegister(rs1) >>> (ctx.getRegister(rs2) & 0b11111);
                default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
            };
            case Opcode.Op.NEG -> switch (funct3) {
                case Opcode.OpInt.ADD -> (int) ctx.getRegister(rs1) - (int) ctx.getRegister(rs2);
                case Opcode.OpInt.SR ->  (int) ctx.getRegister(rs1) >> (ctx.getRegister(rs2) & 0b11111);
                default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
            };
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        });
    }

    void handleLui(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        long immSigned = (instruction >> 12) << 12;

        ctx.setRegister(rd, immSigned);
    }

    void handleBranch(VirtualFrame frame, int i, int instruction) {
        var ctx = currentLanguageContext();

        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;

        if (!switch (funct3) {
            case Opcode.Branch.BEQ -> ctx.getRegister(rs1) == ctx.getRegister(rs2);
            case Opcode.Branch.BNE -> ctx.getRegister(rs1) != ctx.getRegister(rs2);
            case Opcode.Branch.BLT -> ctx.getRegister(rs1) < ctx.getRegister(rs2);
            case Opcode.Branch.BLTU -> Long.compareUnsigned(ctx.getRegister(rs1), ctx.getRegister(rs2)) < 0;
            case Opcode.Branch.BGE -> ctx.getRegister(rs1) >= ctx.getRegister(rs2);
            case Opcode.Branch.BGEU -> Long.compareUnsigned(ctx.getRegister(rs1), ctx.getRegister(rs2)) >= 0;
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }) {
            return;
        }

        long jumpOffset = ((instruction >> 8) & 0b1111) << 1
                | ((instruction >> 25) & 0b111111) << 5
                | ((instruction >> 8) & 0b1) << 11
                | (instruction >> 31) << 12;

        if ((jumpOffset & 0b10) != 0) {
            throw new RiscvTrapException(ExceptionCause.InstructionAddressMisaligned);
        }
    }

    void handleJal(VirtualFrame frame, int i, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        long jumpOffset = (instruction >> 12 & 0b1111_1111) << 12
                | ((instruction >> 20 & 0b1) << 11)
                | ((instruction >> 21 & 0b11_1111_1111) << 1)
                | ((instruction >> 31) << 20);

        if ((jumpOffset & 0b10) != 0) {
            throw new RiscvTrapException(ExceptionCause.InstructionAddressMisaligned);
        }

        long currentPc = baseAddress + i;
        long newPc = currentPc + jumpOffset;

        ctx.setRegister(rd, currentPc + 4);
        throw new RiscvJumpException(newPc);
    }

    void handleJalr(VirtualFrame frame, int i, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immSigned = instruction >> 20;

        if (funct3 != 0) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }

        long currentPc = baseAddress + i;
        long newPc = (ctx.getRegister(rs1) + immSigned) & ~0b1;

        if ((newPc & 0b10) != 0) {
            throw new RiscvTrapException(ExceptionCause.InstructionAddressMisaligned);
        }

        ctx.setRegister(rd, currentPc + 4);
        throw new RiscvJumpException(newPc);
    }

    void handleSystem(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int funct12 = instruction >>> 20;

        switch (funct3) {
            case Opcode.System.PRIV -> {
                switch (funct12) {
                    case Opcode.Priv.EBREAK -> throw new RiscvTrapException(ExceptionCause.Breakpoint);
                    case Opcode.Priv.ECALL -> throw new RiscvTrapException(ExceptionCause.EnvironmentCallFromMMode);
                    case Opcode.Priv.WFI -> {}
                    default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
            }
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }
    }
}
