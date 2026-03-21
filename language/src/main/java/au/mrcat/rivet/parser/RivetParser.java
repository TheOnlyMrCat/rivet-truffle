package au.mrcat.rivet.parser;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.nodes.RiscvDispatchNode;
import au.mrcat.rivet.nodes.RiscvStartupNode;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.nodes.arith.*;
import au.mrcat.rivet.nodes.data.*;
import au.mrcat.rivet.nodes.priv.IllegalInstructionNode;
import au.mrcat.rivet.riscv.Opcode;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.fornwall.jelf.ElfFile;
import net.fornwall.jelf.ElfSegment;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.ArrayList;
import java.util.HashMap;

public final class RivetParser {
    public static RiscvStartupNode loadProgramHeader(ByteSequence elfFile) {
        var elf = ElfFile.from(elfFile.toByteArray());

        var segments = new HashMap<Long, ByteSequence>();
        for (int i = 0; i < elf.e_phnum; i++) {
            var segment = elf.getProgramHeader(i);

            if (segment.p_type != ElfSegment.PT_LOAD) {
                continue;
            }

            segments.put(segment.p_vaddr, elfFile.subSequence((int) segment.p_offset, (int) (segment.p_offset + segment.p_filesz)));
        }

        return new RiscvStartupNode(segments);
    }

    public static RiscvDispatchNode extractBasicBlock(RivetContext context, long baseAddress) {
        var instructions = new ArrayList<Integer>();

        // Cap basic block length at 1024 for interrupt checking, etc.
        bb: for (int i = 0; instructions.size() < 1024; i += 4) {
            int instruction = context.readInt(baseAddress + i);
            instructions.add(instruction);
            int opcode = instruction & 0b1111111;

            if ((opcode & 0b11) != 0b11) {
                // We don't support C yet, so break basic blocks at any non-32-bit instruction (will always instruction fault)
                break;
            }

            switch (opcode) {
                case Opcode.BRANCH, Opcode.JAL, Opcode.JALR -> {
                    // Break basic blocks at jump and branch instructions
                    break bb;
                }
            }
        }

        return new RiscvDispatchNode(instructions.stream().map(RivetParser::parseInstruction).toArray(RivetNode[]::new), baseAddress);
    }

    public static RivetNode parseInstruction(int instruction) {
        int opcode = instruction & 0x7f;
        return switch (opcode) {
            case Opcode.OP_IMM -> parseOpImm(instruction);
            case Opcode.OP_IMM_32 -> parseOpImm32(instruction);
            case Opcode.OP -> parseOp(instruction);
            case Opcode.OP_32 -> parseOp32(instruction);
            default -> new EncodedInstructionNode(instruction);
        };
    }

    private static RivetNode parseOpImm(int instruction) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immSigned = instruction >> 20;
        long immUnsigned = instruction >>> 20;

        RivetOpNode op;
        switch (funct3) {
            case Opcode.OpInt.ADD -> op = new AddNode(new GetRegisterNode(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.SLT -> op = new SetLessThanNode(new GetRegisterNode(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.SLTU -> op = new SetLessThanUnsignedNode(new GetRegisterNode(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.XOR -> op = new XorNode(new GetRegisterNode(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.OR -> op = new OrNode(new GetRegisterNode(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.AND -> op = new AndNode(new GetRegisterNode(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.SLL -> {
                if ((immUnsigned & ~0b111111) != 0) {
                    return new IllegalInstructionNode(instruction);
                }
                op = new ShiftLeftLogicalNode(new GetRegisterNode(rs1), new ConstantNode(immUnsigned));
            }
            case Opcode.OpInt.SR -> {
                if ((immUnsigned & 0b101111_000000) != 0) {
                    return new IllegalInstructionNode(instruction);
                }

                long shift = immUnsigned & 0b111111;

                if ((immUnsigned & 0b010000_000000) != 0) {
                    op = new ShiftRightArithmeticNode(new GetRegisterNode(rs1), new ConstantNode(shift));
                } else {
                    op = new ShiftRightLogicalNode(new GetRegisterNode(rs1), new ConstantNode(shift));
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + funct3);
        }

        if (rd == 0) {
            return op;
        } else {
            return new SetRegisterNode(rd, op);
        }
    }

    private static RivetNode parseOpImm32(int instruction) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int immSigned = instruction >> 20;
        int immUnsigned = instruction >>> 20;

        RivetOpNode op;
        switch (funct3) {
            case Opcode.OpInt.ADD -> op = new SignExtendIntNode(new AddNode(
                    new GetRegisterNode(rs1),
                    new ConstantNode(immSigned)
            ));
            case Opcode.OpInt.SLL -> {
                if ((immUnsigned & ~0b111111_1) != 0) {
                    return new IllegalInstructionNode(instruction);
                }
                op = new SignExtendIntNode(new ShiftLeftLogicalNode(
                        new GetRegisterNode(rs1),
                        new ConstantNode(immSigned)
                ));
            }
            case Opcode.OpInt.SR -> {
                if ((immUnsigned & 0b1011111_00000) != 0) {
                    return new IllegalInstructionNode(instruction);
                }

                long shift = immUnsigned & 0b11111;

                if ((immUnsigned & 0b0100000_00000) != 0) {
                    op = new ShiftRightArithmeticNode(
                            new SignExtendIntNode(new GetRegisterNode(rs1)),
                            new ConstantNode(shift)
                    );
                } else {
                    op = new SignExtendIntNode(new ShiftRightLogicalNode(
                            new IntTruncateNode(new GetRegisterNode(rs1)),
                            new ConstantNode(shift)
                    ));
                }
            }
            default -> {
                return new IllegalInstructionNode(instruction);
            }
        }

        if (rd == 0) {
            return op;
        } else {
            return new SetRegisterNode(rd, op);
        }
    }

    private static RivetNode parseOp(int instruction) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        int funct7 = instruction >>> 25;

        RivetOpNode op;
        switch (funct7) {
            case Opcode.Op.INT -> op = switch (funct3) {
                case Opcode.OpInt.ADD -> new AddNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpInt.SLT -> new SetLessThanNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpInt.SLTU -> new SetLessThanUnsignedNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpInt.XOR -> new XorNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpInt.OR -> new OrNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpInt.AND -> new AndNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpInt.SLL -> new ShiftLeftLogicalNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpInt.SR -> new ShiftRightLogicalNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                default -> throw new IllegalStateException("Unexpected value: " + funct3);
            };
            case Opcode.Op.MUL_DIV -> op = switch (funct3) {
                case Opcode.OpMulDiv.MUL -> new MultiplyNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpMulDiv.MULH -> new MultiplyHighNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpMulDiv.MULHSU -> new MultiplyHighSignedUnsignedNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpMulDiv.MULHU -> new MultiplyHighUnsignedNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpMulDiv.DIV -> new DivideNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpMulDiv.DIVU -> new DivideUnsignedNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpMulDiv.REM -> new RemainderNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                case Opcode.OpMulDiv.REMU -> new RemainderUnsignedNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                default -> throw new IllegalStateException("Unexpected value: " + funct3);
            };
            case Opcode.Op.NEG -> {
                switch (funct3) {
                    case Opcode.OpInt.ADD -> op = new SubNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                    case Opcode.OpInt.SR -> op = new ShiftRightArithmeticNode(new GetRegisterNode(rs1), new GetRegisterNode(rs2));
                    default -> {
                        return new IllegalInstructionNode(instruction);
                    }
                }
            }
            default -> {
                return new IllegalInstructionNode(instruction);
            }
        }

        if (rd == 0) {
            return op;
        } else {
            return new SetRegisterNode(rd, op);
        }
    }

    private static RivetNode parseOp32(int instruction) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        int funct7 = instruction >>> 25;

        RivetOpNode op;
        switch (funct7) {
            case Opcode.Op.INT -> {
                switch (funct3) {
                    case Opcode.OpInt.ADD -> op = new SignExtendIntNode(new AddNode(
                            new GetRegisterNode(rs1),
                            new GetRegisterNode(rs2)
                    ));
                    case Opcode.OpInt.SLL -> op = new SignExtendIntNode(new ShiftLeftLogicalNode(
                            new GetRegisterNode(rs1),
                            new GetRegisterNode(rs2),
                            0b11_111
                    ));
                    case Opcode.OpInt.SR -> op = new SignExtendIntNode(new ShiftRightLogicalNode(
                            new IntTruncateNode(new GetRegisterNode(rs1)),
                            new GetRegisterNode(rs2),
                            0b11_111
                    ));
                    default -> {
                        return new IllegalInstructionNode(instruction);
                    }
                }
            }
            case Opcode.Op.MUL_DIV -> {
                switch (funct3) {
                    case Opcode.OpMulDiv.MUL -> op = new SignExtendIntNode(new MultiplyNode(
                            new GetRegisterNode(rs1),
                            new GetRegisterNode(rs2)
                    ));
                    case Opcode.OpMulDiv.DIV -> op = new SignExtendIntNode(new DivideNode(
                            new SignExtendIntNode(new GetRegisterNode(rs1)),
                            new SignExtendIntNode(new GetRegisterNode(rs2))
                    ));
                    case Opcode.OpMulDiv.DIVU -> op = new SignExtendIntNode(new DivideUnsignedNode(
                            new IntTruncateNode(new GetRegisterNode(rs1)),
                            new IntTruncateNode(new GetRegisterNode(rs2))
                    ));
                    case Opcode.OpMulDiv.REM -> op = new SignExtendIntNode(new RemainderNode(
                            new SignExtendIntNode(new GetRegisterNode(rs1)),
                            new SignExtendIntNode(new GetRegisterNode(rs2))
                    ));
                    case Opcode.OpMulDiv.REMU -> op = new SignExtendIntNode(new RemainderUnsignedNode(
                            new IntTruncateNode(new GetRegisterNode(rs1)),
                            new IntTruncateNode(new GetRegisterNode(rs2))
                    ));
                    default -> {
                        return new IllegalInstructionNode(instruction);
                    }
                }
            }
            case Opcode.Op.NEG -> {
                switch (funct3) {
                    case Opcode.OpInt.ADD -> op = new SignExtendIntNode(new SubNode(
                            new GetRegisterNode(rs1),
                            new GetRegisterNode(rs2)
                    ));
                    case Opcode.OpInt.SR -> op = new ShiftRightArithmeticNode(
                            new SignExtendIntNode(new GetRegisterNode(rs1)),
                            new GetRegisterNode(rs2),
                            0b11_111
                    );
                    default -> {
                        return new IllegalInstructionNode(instruction);
                    }
                }
            }
            default -> {
                return new IllegalInstructionNode(instruction);
            }
        }

        if (rd == 0) {
            return op;
        } else {
            return new SetRegisterNode(rd, op);
        }
    }
}
