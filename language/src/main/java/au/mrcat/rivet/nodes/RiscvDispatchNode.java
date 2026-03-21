package au.mrcat.rivet.nodes;

import au.mrcat.rivet.nodes.data.EncodedInstructionNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.Opcode;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.RiscvExitException;
import au.mrcat.rivet.runtime.RiscvJumpException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;

import java.math.BigInteger;

public class RiscvDispatchNode extends RivetNode implements BlockNode.ElementExecutor<RivetNode> {
    @Child BlockNode<RivetNode> instructions;
    private final long baseAddress;

    public RiscvDispatchNode(RivetNode[] instructions, long baseAddress) {
        this.instructions = BlockNode.create(instructions, this);
        this.baseAddress = baseAddress;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        instructions.executeVoid(frame, BlockNode.NO_ARGUMENT);
        throw new RiscvJumpException(this.baseAddress + 4L * instructions.getElements().length);
    }

    @Override
    public void executeVoid(VirtualFrame frame, RivetNode node, int index, int argument) {
        long pc = 4L * index;
        switch (node) {
            case EncodedInstructionNode instructionNode -> {
                int instruction = instructionNode.instruction;
                System.err.printf("Executing %08x @ 0x%08x\n", instruction, this.baseAddress + pc);
                int opcode = instruction & 0x7f;
                switch (opcode) {
                    case Opcode.LOAD -> handleLoad(frame, instruction);
                    case Opcode.MISC_MEM -> handleMiscMem(frame, index, instruction);
                    case Opcode.AUIPC -> handleAuipc(frame, index, instruction);
                    case Opcode.STORE -> handleStore(frame, instruction);
                    case Opcode.LUI -> handleLui(frame, instruction);
                    case Opcode.BRANCH -> handleBranch(frame, index, instruction);
                    case Opcode.JAL -> handleJal(frame, index, instruction);
                    case Opcode.JALR -> handleJalr(frame, index, instruction);
                    case Opcode.SYSTEM -> handleSystem(frame, instruction);
                    case Opcode.OP_IMM, Opcode.OP_IMM_32, Opcode.OP, Opcode.OP_32 -> throw new IllegalStateException("Instruction should have been parsed");
                    default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
            }
            default -> node.execute(frame);
        }
        this.currentLanguageContext().dumpRegisterState();
    }

    void handleLoad(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immSigned = instruction >> 20;

        long address = ctx.getRegister(rs1) + immSigned;

        ctx.setRegister(rd, switch (funct3) {
            case Opcode.MemWidth.BYTE -> ctx.readByte(address);
            case Opcode.MemWidth.BYTE_UNSIGNED -> Byte.toUnsignedLong(ctx.readByte(address));
            case Opcode.MemWidth.HALF -> ctx.readShortMisaligned(address);
            case Opcode.MemWidth.HALF_UNSIGNED -> Short.toUnsignedLong(ctx.readShortMisaligned(address));
            case Opcode.MemWidth.WORD -> ctx.readIntMisaligned(address);
            case Opcode.MemWidth.WORD_UNSIGNED -> Integer.toUnsignedLong(ctx.readIntMisaligned(address));
            case Opcode.MemWidth.DOUBLE -> ctx.readLongMisaligned(address);
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        });
    }

    void handleMiscMem(VirtualFrame frame, int i, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immUnsigned = instruction >>> 20;

        if (rd != 0 || rs1 != 0) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }

        switch (funct3) {
            case Opcode.MiscMem.FENCE -> {}
            case Opcode.MiscMem.FENCE_I -> throw new RiscvJumpException(this.baseAddress + 4L * (i + 1));
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
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
            case Opcode.OpInt.SLTU -> Long.compareUnsigned(ctx.getRegister(rs1), immSigned) < 0 ? 1 : 0;
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

        ctx.setRegister(rd, baseAddress + 4L * i + immSigned);
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
                //noinspection IntegerMultiplicationImplicitCastToLong
                yield (int) ctx.getRegister(rs1) << immUnsigned;
            }
            case Opcode.OpInt.SR -> {
                if ((immUnsigned & 0b1011111_00000) != 0) {
                    throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }

                long shift = immUnsigned & 0b11111;

                if ((immUnsigned & 0b0100000_00000) != 0) {
                    // Arithmetic right shift
                    yield (int) ctx.getRegister(rs1) >> shift;
                } else {
                    // Logical right shift
                    yield (int) ctx.getRegister(rs1) >>> shift;
                }
            }
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        });
    }

    void handleStore(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        int offset = (instruction >> 7) & 0b11111
            | (instruction >> 25) << 5;

        long address = ctx.getRegister(rs1) + offset;
        long value = ctx.getRegister(rs2);

        switch (funct3) {
            case Opcode.MemWidth.BYTE -> ctx.writeByte(address, (byte) value);
            case Opcode.MemWidth.HALF -> ctx.writeShortMisaligned(address, (short) value);
            case Opcode.MemWidth.WORD -> ctx.writeIntMisaligned(address, (int) value);
            case Opcode.MemWidth.DOUBLE -> ctx.writeLongMisaligned(address, value);
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        };
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
            case Opcode.Op.MUL_DIV -> switch (funct3) {
                case Opcode.OpMulDiv.MUL -> ctx.getRegister(rs1) * ctx.getRegister(rs2);
                case Opcode.OpMulDiv.MULH -> Math.multiplyHigh(ctx.getRegister(rs1), ctx.getRegister(rs2));
                case Opcode.OpMulDiv.MULHSU -> {
                    // If there's a way to do this with multiplyHigh and unsignedMultiplyHigh I don't know it
                    BigInteger s1 = BigInteger.valueOf(ctx.getRegister(rs1));
                    BigInteger s2;

                    // From OpenJDK: https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/master/src/java.base/share/classes/java/lang/Long.java#L241-L252
                    long s2l = ctx.getRegister(rs2);
                    if (s2l >= 0) {
                        s2 = BigInteger.valueOf(s2l);
                    } else {
                        int upper = (int) (s2l >>> 32);
                        int lower = (int) s2l;

                        // return (upper << 32) + lower
                        s2 = (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).
                                add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
                    }

                    yield s1.multiply(s2).shiftRight(64).longValue();
                }
                case Opcode.OpMulDiv.MULHU -> Math.unsignedMultiplyHigh(ctx.getRegister(rs1), ctx.getRegister(rs2));
                case Opcode.OpMulDiv.DIV -> {
                    long dividend = ctx.getRegister(rs1);
                    long divisor = ctx.getRegister(rs2);

                    if (divisor == 0) {
                        yield -1;
                    }
                    if (dividend == Long.MIN_VALUE && divisor == -1) {
                        yield Long.MIN_VALUE;
                    }
                    yield dividend / divisor;
                }
                case Opcode.OpMulDiv.DIVU -> {
                    long dividend = ctx.getRegister(rs1);
                    long divisor = ctx.getRegister(rs2);

                    if (divisor == 0) {
                        yield -1;
                    }
                    yield Long.divideUnsigned(dividend, divisor);
                }
                case Opcode.OpMulDiv.REM -> {
                    long dividend = ctx.getRegister(rs1);
                    long divisor = ctx.getRegister(rs2);

                    if (divisor == 0) {
                        yield dividend;
                    }
                    if (dividend == Long.MIN_VALUE && divisor == -1) {
                        yield 0;
                    }
                    yield dividend % divisor;
                }
                case Opcode.OpMulDiv.REMU -> {
                    long dividend = ctx.getRegister(rs1);
                    long divisor = ctx.getRegister(rs2);

                    if (divisor == 0) {
                        yield dividend;
                    }
                    yield Long.remainderUnsigned(dividend, divisor);
                }
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
            case Opcode.Op.MUL_DIV -> switch (funct3) {
                case Opcode.OpMulDiv.MUL -> (long) ((int) ctx.getRegister(rs1) * (int) ctx.getRegister(rs2));
                case Opcode.OpMulDiv.DIV -> {
                    int dividend = (int) ctx.getRegister(rs1);
                    int divisor = (int) ctx.getRegister(rs2);

                    if (divisor == 0) {
                        yield -1;
                    }
                    if (dividend == Integer.MIN_VALUE && divisor == -1) {
                        yield Integer.MIN_VALUE;
                    }
                    yield dividend / divisor;
                }
                case Opcode.OpMulDiv.DIVU -> {
                    int dividend = (int) ctx.getRegister(rs1);
                    int divisor = (int) ctx.getRegister(rs2);

                    if (divisor == 0) {
                        yield -1;
                    }
                    yield Integer.divideUnsigned(dividend, divisor);
                }
                case Opcode.OpMulDiv.REM -> {
                    int dividend = (int) ctx.getRegister(rs1);
                    int divisor = (int) ctx.getRegister(rs2);

                    if (divisor == 0) {
                        yield dividend;
                    }
                    if (dividend == Integer.MIN_VALUE && divisor == -1) {
                        yield 0;
                    }
                    yield dividend % divisor;
                }
                case Opcode.OpMulDiv.REMU -> {
                    int dividend = (int) ctx.getRegister(rs1);
                    int divisor = (int) ctx.getRegister(rs2);

                    if (divisor == 0) {
                        yield dividend;
                    }
                    yield Integer.remainderUnsigned(dividend, divisor);
                }
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
                | ((instruction >> 7) & 0b1) << 11
                | (instruction >> 31) << 12;

        if ((jumpOffset & 0b10) != 0) {
            throw new RuntimeException("Something went wrong calculating the jump offset");
        }

        long currentPc = baseAddress + 4L * i;
        long newPc = currentPc + jumpOffset;
        throw new RiscvJumpException(newPc);
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

        long currentPc = baseAddress + 4L * i;
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

        long currentPc = baseAddress + 4L * i;
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
                    case Opcode.Priv.ECALL -> {
                        switch ((int) ctx.getRegister(RegisterState.A7)) {
                            case 93 -> throw new RiscvExitException(ctx.getRegister(RegisterState.A0));
                            default -> throw new RuntimeException(String.format("Unimplemented syscall: %d", ctx.getRegister(RegisterState.A7)));
                        }
                    }
                    case Opcode.Priv.WFI -> {}
                    default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
            }
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }
    }
}
