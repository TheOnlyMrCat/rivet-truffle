package au.mrcat.rivet.parser;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.nodes.*;
import au.mrcat.rivet.nodes.arith.*;
import au.mrcat.rivet.nodes.control.*;
import au.mrcat.rivet.nodes.data.*;
import au.mrcat.rivet.nodes.priv.*;
import au.mrcat.rivet.riscv.Opcode;
import net.fornwall.jelf.ElfFile;
import net.fornwall.jelf.ElfSegment;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.*;

public final class RivetParser {
    public static RivetStartupNode loadProgramHeader(ByteSequence elfFile) {
        var elf = ElfFile.from(elfFile.toByteArray());

        var segments = new HashMap<Long, ByteSequence>();
        for (int i = 0; i < elf.e_phnum; i++) {
            var segment = elf.getProgramHeader(i);

            if (segment.p_type != ElfSegment.PT_LOAD) {
                continue;
            }

            segments.put(segment.p_vaddr, elfFile.subSequence((int) segment.p_offset, (int) (segment.p_offset + segment.p_filesz)));
        }

        return new RivetStartupNode(segments, elf.e_entry);
    }

    public static RivetCallTargetNode extractCallTarget(RivetLanguage language, RivetContext context, long initialPc) {
        var currentBlock = new ArrayList<RivetNode>();
        var basicBlocks = new TreeMap<Long, RivetBasicBlockNode>();
        var frontier = new ArrayDeque<Long>();
        frontier.addLast(initialPc);

        while (!frontier.isEmpty()) {
            long basePc = frontier.removeFirst();
            if (basicBlocks.containsKey(basePc)) {
                continue;
            }

            long currentPc = basePc;
            currentBlock.clear();
            bb: while (true) {
                int instruction = context.readIntMisaligned(currentPc);
                var node = Objects.requireNonNull(parseInstruction(instruction, currentPc));

                if ((instruction & 0b11) != 0b11) {
                    currentPc += 2;
                } else {
                    currentPc += 4;
                }

                switch (node) {
                    case HintNode ignored -> {
                    }
                    case RivetDivergentNode divergentNode -> {
                        currentBlock.add(divergentNode);
                        frontier.addAll(List.of(divergentNode.callTargetContinuations()));
                        break bb;
                    }
                    default -> {
                        currentBlock.add(node);
                    }
                }
            }
            basicBlocks.put(basePc, new RivetBasicBlockNode(currentBlock.toArray(new RivetNode[0]), currentPc));
        }

        return new RivetCallTargetNode(language, basicBlocks);
    }

    public static RivetNode parseInstruction(int instruction, long pc) {
        int compressed_opcode = instruction & 0b11;
        return switch (compressed_opcode) {
            case 0b00 -> parseC0((short) instruction, pc);
            case 0b01 -> parseC1((short) instruction, pc);
            case 0b10 -> parseC2((short) instruction, pc);
            case 0b11 -> {
                int opcode = instruction & 0x7f;
                yield switch (opcode) {
                    case Opcode.LOAD -> parseLoad(instruction, pc);
                    case Opcode.MISC_MEM -> parseMiscMem(instruction, pc);
                    case Opcode.OP_IMM -> parseOpImm(instruction, pc);
                    case Opcode.AUIPC -> parseAuipc(instruction, pc);
                    case Opcode.OP_IMM_32 -> parseOpImm32(instruction, pc);
                    case Opcode.STORE -> parseStore(instruction, pc);
                    case Opcode.AMO -> parseAmo(instruction, pc);
                    case Opcode.OP -> parseOp(instruction, pc);
                    case Opcode.LUI -> parseLui(instruction);
                    case Opcode.OP_32 -> parseOp32(instruction, pc);
                    case Opcode.BRANCH -> parseBranch(instruction, pc);
                    case Opcode.JAL -> parseJal(instruction, pc);
                    case Opcode.JALR -> parseJalr(instruction, pc);
                    case Opcode.SYSTEM -> parseSystem(instruction, pc);
                    default -> new IllegalInstructionNode(instruction, pc);
                };
            }
            default -> throw new IllegalStateException("Unexpected value of compressed_opcode");
        };
    }

    public static RivetNode parseC0(short instruction, long pc) {
        int funct3 = instruction >>> 13 & 0b111;

        return switch (funct3) {
            case Opcode.C0.ADDI4SPN -> {
                int imm = ((instruction >> 6) & 0b1) << 2
                        | ((instruction >> 5) & 0b1) << 3
                        | ((instruction >> 11) & 0b11) << 4
                        | ((instruction >> 7) & 0b1111) << 6;

                if (imm == 0) {
                    yield new IllegalInstructionNode(instruction, pc);
                }

                int rd = ((instruction >> 2) & 0b111) + 8;

                yield new SetRegisterNode(rd, new AddNode(GetRegisterNode.create(2), new ConstantNode(imm)));
            }
            case Opcode.C0.LW -> {
                int offset = ((instruction >> 6) & 0b1) << 2
                        | ((instruction >> 10) & 0b111) << 3
                        | ((instruction >> 5) & 0b1) << 6;

                int rd = ((instruction >> 2) & 0b111) + 8;
                int rs1 = ((instruction >> 7) & 0b111) + 8;

                yield new SetRegisterNode(rd, new LoadWordNode(GetRegisterNode.create(rs1), offset, pc));
            }
            case Opcode.C0.LD -> {
                int offset = ((instruction >> 10) & 0b111) << 3
                        | ((instruction >> 5) & 0b11) << 6;

                int rd = ((instruction >> 2) & 0b111) + 8;
                int rs1 = ((instruction >> 7) & 0b111) + 8;

                yield new SetRegisterNode(rd, new LoadDoubleNode(GetRegisterNode.create(rs1), offset, pc));
            }
            case Opcode.C0.SW -> {
                int offset = ((instruction >> 6) & 0b1) << 2
                        | ((instruction >> 10) & 0b111) << 3
                        | ((instruction >> 5) & 0b1) << 6;

                int rs2 = ((instruction >> 2) & 0b111) + 8;
                int rs1 = ((instruction >> 7) & 0b111) + 8;

                yield new StoreWordNode(GetRegisterNode.create(rs1), offset, GetRegisterNode.create(rs2), pc);
            }
            case Opcode.C0.SD -> {
                int offset = ((instruction >> 10) & 0b111) << 3
                        | ((instruction >> 5) & 0b11) << 6;

                int rs2 = ((instruction >> 2) & 0b111) + 8;
                int rs1 = ((instruction >> 7) & 0b111) + 8;

                yield new StoreDoubleNode(GetRegisterNode.create(rs1), offset, GetRegisterNode.create(rs2), pc);
            }
            default -> new IllegalInstructionNode(instruction, pc);
        };
    }

    public static RivetNode parseC1(short instruction, long pc) {
        int funct3 = instruction >>> 13 & 0b111;

        return switch (funct3) {
            case Opcode.C1.ADDI -> {
                int rd = (instruction >> 7) & 0b11111;
                if (rd == 0) {
                    yield new HintNode(instruction);
                }
                int imm = (instruction >> 2) & 0b11111
                        | ((instruction << 19) & 0x8000_0000) >> 26;

                yield new SetRegisterNode(rd, new AddNode(GetRegisterNode.create(rd), new ConstantNode(imm)));
            }
            case Opcode.C1.ADDIW -> {
                int rd = (instruction >> 7) & 0b11111;
                if (rd == 0) {
                    yield new HintNode(instruction);
                }
                int imm = (instruction >> 2) & 0b11111
                        | ((instruction << 19) & 0x8000_0000) >> 26;

                yield new SetRegisterNode(rd, new SignExtendIntNode(new AddNode(GetRegisterNode.create(rd), new ConstantNode(imm))));
            }
            case Opcode.C1.LI -> {
                int rd = (instruction >> 7) & 0b11111;
                if (rd == 0) {
                    yield new HintNode(instruction);
                }

                int imm = (instruction >> 2) & 0b11111
                        | ((instruction << 19) & 0x8000_0000) >> 26;

                yield new SetRegisterNode(rd, new ConstantNode(imm));
            }
            case Opcode.C1.LUI -> {
                int rd = (instruction >> 7) & 0b11111;
                if (rd == 2) {
                    // ADDI16SP
                    int imm = ((instruction >> 6) & 0b1) << 4
                            | ((instruction >> 2) & 0b1) << 5
                            | ((instruction >> 5) & 0b1) << 6
                            | ((instruction >> 3) & 0b11) << 7
                            | ((instruction << 19) & 0x8000_0000) >> 22;
                    if (imm == 0) {
                        yield new IllegalInstructionNode(instruction, pc);
                    }
                    yield new SetRegisterNode(2, new AddNode(GetRegisterNode.create(2), new ConstantNode(imm)));
                } else {
                    // LUI
                    int imm = ((instruction >> 2) & 0b11111) << 12
                            | ((instruction << 19) & 0x8000_0000) >> 14;
                    if (imm == 0) {
                        yield new IllegalInstructionNode(instruction, pc);
                    }
                    if (rd == 0) {
                        yield new HintNode(instruction);
                    }
                    yield new SetRegisterNode(rd, new ConstantNode(imm));
                }
            }
            case Opcode.C1.ARITH -> {
                int funct3l = (instruction >> 10) & 0b111;
                yield switch (funct3l) {
                    case 0b000, 0b100 -> {
                        // SRLI
                        int rd = ((instruction >> 7) & 0b111) + 8;
                        int shiftAmount = ((instruction >> 2) & 0b11111)
                                | ((instruction >> 12) & 0b1) << 5;
                        if (shiftAmount == 0) {
                            yield new HintNode(instruction);
                        }
                        yield new SetRegisterNode(rd, new ShiftRightLogicalNode(GetRegisterNode.create(rd), new ConstantNode(shiftAmount)));
                    }
                    case 0b001, 0b101 -> {
                        // SRAI
                        int rd = ((instruction >> 7) & 0b111) + 8;
                        int shiftAmount = ((instruction >> 2) & 0b11111)
                                | ((instruction >> 12) & 0b1) << 5;
                        if (shiftAmount == 0) {
                            yield new HintNode(instruction);
                        }
                        yield new SetRegisterNode(rd, new ShiftRightArithmeticNode(GetRegisterNode.create(rd), new ConstantNode(shiftAmount)));
                    }
                    case 0b010, 0b110 -> {
                        // ANDI
                        int rd = ((instruction >> 7) & 0b111) + 8;
                        long imm = ((instruction >> 2) & 0b11111)
                                | ((instruction << 19) & 0x8000_0000) >> 26;
                        yield new SetRegisterNode(rd, new AndNode(GetRegisterNode.create(rd), new ConstantNode(imm)));
                    }
                    case 0b011 -> {
                        int funct2 = (instruction >> 5) & 0b11;
                        int rd = ((instruction >> 7) & 0b111) + 8;
                        int rs2 = ((instruction >> 2) & 0b111) + 8;
                        yield new SetRegisterNode(rd, switch (funct2) {
                            case 0b00 -> new SubNode(GetRegisterNode.create(rd), GetRegisterNode.create(rs2));
                            case 0b01 -> new XorNode(GetRegisterNode.create(rd), GetRegisterNode.create(rs2));
                            case 0b10 -> new OrNode(GetRegisterNode.create(rd), GetRegisterNode.create(rs2));
                            case 0b11 -> new AndNode(GetRegisterNode.create(rd), GetRegisterNode.create(rs2));
                            default -> throw new IllegalStateException("Unexpected value: " + funct2);
                        });
                    }
                    case 0b111 -> {
                        int funct2 = (instruction >> 5) & 0b11;
                        if (funct2 > 1) {
                            yield new IllegalInstructionNode(instruction, pc);
                        }
                        int rd = ((instruction >> 7) & 0b111) + 8;
                        int rs2 = ((instruction >> 2) & 0b111) + 8;
                        yield new SetRegisterNode(rd, switch (funct2) {
                            case 0b00 -> new SignExtendIntNode(new SubNode(GetRegisterNode.create(rd), GetRegisterNode.create(rs2)));
                            case 0b01 -> new SignExtendIntNode(new AddNode(GetRegisterNode.create(rd), GetRegisterNode.create(rs2)));
                            default -> throw new IllegalStateException("Unexpected value: " + funct2);
                        });
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + funct3l);
                };
            }
            case Opcode.C1.J -> {
                int offset = ((instruction >> 3) & 0b111) << 1
                        | ((instruction >> 11) & 0b1) << 4
                        | ((instruction >> 2) & 0b1) << 5
                        | ((instruction >> 7) & 0b1) << 6
                        | ((instruction >> 6) & 0b1) << 7
                        | ((instruction >> 9) & 0b11) << 8
                        | ((instruction >> 8) & 0b1) << 10
                        | ((instruction << 19) & 0x8000_0000) >> 20;
                yield new JumpNode(new ConstantNode(pc + offset));
            }
            case Opcode.C1.BEQZ -> {
                int offset = ((instruction >> 3) & 0b11) << 1
                        | ((instruction >> 10) & 0b11) << 3
                        | ((instruction >> 2) & 0b1) << 5
                        | ((instruction >> 5) & 0b11) << 6
                        | ((instruction << 19) & 0x8000_0000) >> 23;

                int rs1 = ((instruction >> 7) & 0b111) + 8;
                yield new BranchEqualNode(
                        new ConstantNode(0), GetRegisterNode.create(rs1),
                        pc + offset, pc + 2
                );
            }
            case Opcode.C1.BNEZ -> {
                int offset = ((instruction >> 3) & 0b11) << 1
                        | ((instruction >> 10) & 0b11) << 3
                        | ((instruction >> 2) & 0b1) << 5
                        | ((instruction >> 5) & 0b11) << 6
                        | ((instruction << 19) & 0x8000_0000) >> 23;

                int rs1 = ((instruction >> 7) & 0b111) + 8;
                yield new BranchEqualNode(
                        new ConstantNode(0), GetRegisterNode.create(rs1),
                        pc + 2, pc + offset
                );
            }
            default -> throw new IllegalStateException("Unexpected value: " + funct3);
        };
    }

    public static RivetNode parseC2(short instruction, long pc) {
        int funct3 = instruction >>> 13 & 0b111;

        return switch (funct3) {
            case Opcode.C2.SLLI -> {
                int rd = (instruction >> 7) & 0b11111;
                int shiftAmount = ((instruction >> 2) & 0b11111)
                        | ((instruction >> 12) & 0b1) << 5;
                if (shiftAmount == 0) {
                    yield new HintNode(instruction);
                }
                yield new SetRegisterNode(rd, new ShiftLeftLogicalNode(GetRegisterNode.create(rd), new ConstantNode(shiftAmount)));
            }
            case Opcode.C2.LWSP -> {
                int rd = (instruction >> 7) & 0b11111;
                if (rd == 0) {
                    yield new IllegalInstructionNode(instruction, pc);
                }

                int offset = ((instruction >> 4) & 0b111) << 2
                        | ((instruction >> 12) & 0b1) << 5
                        | ((instruction >> 2) & 0b11) << 6;
                yield new SetRegisterNode(rd, new LoadWordNode(GetRegisterNode.create(2), offset, pc));
            }
            case Opcode.C2.LDSP -> {
                int rd = (instruction >> 7) & 0b11111;
                if (rd == 0) {
                    yield new IllegalInstructionNode(instruction, pc);
                }

                int offset = ((instruction >> 5) & 0b11) << 3
                        | ((instruction >> 12) & 0b1) << 5
                        | ((instruction >> 2) & 0b111) << 6;
                yield new SetRegisterNode(rd, new LoadDoubleNode(GetRegisterNode.create(2), offset, pc));
            }
            case Opcode.C2.J -> {
                int rs2 = (instruction >> 2) & 0b11111;
                if ((instruction >> 12 & 0b1) == 0) {
                    if (rs2 == 0) {
                        // JR
                        int rs1 = (instruction >> 7) & 0b11111;
                        if (rs1 == 0) {
                            yield new IllegalInstructionNode(instruction, pc);
                        }
                        yield new JumpNode(GetRegisterNode.create(rs1));
                    } else {
                        // MV
                        int rd = (instruction >> 7) & 0b11111;
                        if (rd == 0) {
                            yield new HintNode(instruction);
                        }
                        yield new SetRegisterNode(rd, GetRegisterNode.create(rs2));
                    }
                } else {
                    if (rs2 == 0) {
                        int rs1 = (instruction >> 7) & 0b11111;
                        if (rs1 == 0) {
                            // EBREAK
                            yield new BreakpointNode(pc);
                        } else {
                            // JALR
                            yield new JumpAndLinkNode(
                                    GetRegisterNode.create(rs1),
                                    new SetRegisterNode(1, new ConstantNode(pc + 2))
                            );
                        }
                    } else {
                        // ADD
                        int rd = (instruction >> 7) & 0b11111;
                        if (rd == 0) {
                            yield new HintNode(instruction);
                        }
                        yield new SetRegisterNode(rd, new AddNode(GetRegisterNode.create(rd), GetRegisterNode.create(rs2)));
                    }
                }
            }
            case Opcode.C2.SWSP -> {
                int rs2 = (instruction >> 2) & 0b11111;
                int offset = ((instruction >> 9) & 0b1111) << 2
                        | ((instruction >> 7) & 0b11) << 6;
                yield new StoreWordNode(GetRegisterNode.create(2), offset, GetRegisterNode.create(rs2), pc);
            }
            case Opcode.C2.SDSP -> {
                int rs2 = (instruction >> 2) & 0b11111;
                int offset = ((instruction >> 10) & 0b111) << 3
                        | ((instruction >> 7) & 0b111) << 6;
                yield new StoreDoubleNode(GetRegisterNode.create(2), offset, GetRegisterNode.create(rs2), pc);
            }
            default -> new IllegalInstructionNode(instruction, pc);
        };
    }

    private static RivetNode parseLoad(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int immSigned = instruction >> 20;

        RivetOpNode op;
        switch (funct3) {
            case Opcode.MemWidth.BYTE -> op = new LoadByteNode(GetRegisterNode.create(rs1), immSigned, pc);
            case Opcode.MemWidth.BYTE_UNSIGNED -> op = new LoadByteUnsignedNode(GetRegisterNode.create(rs1), immSigned, pc);
            case Opcode.MemWidth.HALF -> op = new LoadHalfNode(GetRegisterNode.create(rs1), immSigned, pc);
            case Opcode.MemWidth.HALF_UNSIGNED -> op = new LoadHalfUnsignedNode(GetRegisterNode.create(rs1), immSigned, pc);
            case Opcode.MemWidth.WORD -> op = new LoadWordNode(GetRegisterNode.create(rs1), immSigned, pc);
            case Opcode.MemWidth.WORD_UNSIGNED -> op = new LoadWordUnsignedNode(GetRegisterNode.create(rs1), immSigned, pc);
            case Opcode.MemWidth.DOUBLE -> op = new LoadDoubleNode(GetRegisterNode.create(rs1), immSigned, pc);
            default -> {
                return new IllegalInstructionNode(instruction, pc);
            }
        }

        if (rd == 0) {
            return op;
        } else {
            return new SetRegisterNode(rd, op);
        }
    }

    private static RivetNode parseMiscMem(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immUnsigned = instruction >>> 20;

        return switch (funct3) {
            // Fences aren't technically hints, but we impose a total order anyway so they don't do anything here
            case Opcode.MiscMem.FENCE -> new HintNode(instruction);
            case Opcode.MiscMem.FENCE_I -> new InstructionFenceNode(pc + 4);
            default -> new IllegalInstructionNode(instruction, pc);
        };
    }

    private static RivetNode parseOpImm(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immSigned = instruction >> 20;
        long immUnsigned = instruction >>> 20;

        RivetOpNode op;
        switch (funct3) {
            case Opcode.OpInt.ADD -> op = new AddNode(GetRegisterNode.create(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.SLT -> op = new SetLessThanNode(GetRegisterNode.create(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.SLTU -> op = new SetLessThanUnsignedNode(GetRegisterNode.create(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.XOR -> op = new XorNode(GetRegisterNode.create(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.OR -> op = new OrNode(GetRegisterNode.create(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.AND -> op = new AndNode(GetRegisterNode.create(rs1), new ConstantNode(immSigned));
            case Opcode.OpInt.SLL -> {
                if ((immUnsigned & ~0b111111) != 0) {
                    return new IllegalInstructionNode(instruction, pc);
                }
                op = new ShiftLeftLogicalNode(GetRegisterNode.create(rs1), new ConstantNode(immUnsigned));
            }
            case Opcode.OpInt.SR -> {
                if ((immUnsigned & 0b101111_000000) != 0) {
                    return new IllegalInstructionNode(instruction, pc);
                }

                long shift = immUnsigned & 0b111111;

                if ((immUnsigned & 0b010000_000000) != 0) {
                    op = new ShiftRightArithmeticNode(GetRegisterNode.create(rs1), new ConstantNode(shift));
                } else {
                    op = new ShiftRightLogicalNode(GetRegisterNode.create(rs1), new ConstantNode(shift));
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + funct3);
        }

        if (rd == 0) {
            return new HintNode(instruction);
        }
        return new SetRegisterNode(rd, op);
    }

    private static RivetNode parseAuipc(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        long immSigned = (instruction >> 12) << 12;

        if (rd == 0) {
            return new HintNode(instruction);
        }
        return new SetRegisterNode(rd, new ConstantNode(pc + immSigned));
    }

    private static RivetNode parseOpImm32(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int immSigned = instruction >> 20;
        int immUnsigned = instruction >>> 20;

        RivetOpNode op;
        switch (funct3) {
            case Opcode.OpInt.ADD -> op = new SignExtendIntNode(new AddNode(
                    GetRegisterNode.create(rs1),
                    new ConstantNode(immSigned)
            ));
            case Opcode.OpInt.SLL -> {
                if ((immUnsigned & ~0b111111_1) != 0) {
                    return new IllegalInstructionNode(instruction, pc);
                }
                op = new SignExtendIntNode(new ShiftLeftLogicalNode(
                        GetRegisterNode.create(rs1),
                        new ConstantNode(immSigned)
                ));
            }
            case Opcode.OpInt.SR -> {
                if ((immUnsigned & 0b1011111_00000) != 0) {
                    return new IllegalInstructionNode(instruction, pc);
                }

                long shift = immUnsigned & 0b11111;

                if ((immUnsigned & 0b0100000_00000) != 0) {
                    op = new ShiftRightArithmeticNode(
                            new SignExtendIntNode(GetRegisterNode.create(rs1)),
                            new ConstantNode(shift)
                    );
                } else {
                    op = new SignExtendIntNode(new ShiftRightLogicalNode(
                            new IntTruncateNode(GetRegisterNode.create(rs1)),
                            new ConstantNode(shift)
                    ));
                }
            }
            default -> {
                return new IllegalInstructionNode(instruction, pc);
            }
        }

        if (rd == 0) {
            return new HintNode(instruction);
        }
        return new SetRegisterNode(rd, op);
    }

    private static RivetNode parseStore(int instruction, long pc) {
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        int offset = (instruction >> 7) & 0b11111
                | (instruction >> 25) << 5;

        return switch (funct3) {
            case Opcode.MemWidth.BYTE -> new StoreByteNode(GetRegisterNode.create(rs1), offset, GetRegisterNode.create(rs2), pc);
            case Opcode.MemWidth.HALF -> new StoreHalfNode(GetRegisterNode.create(rs1), offset, GetRegisterNode.create(rs2), pc);
            case Opcode.MemWidth.WORD -> new StoreWordNode(GetRegisterNode.create(rs1), offset, GetRegisterNode.create(rs2), pc);
            case Opcode.MemWidth.DOUBLE -> new StoreDoubleNode(GetRegisterNode.create(rs1), offset, GetRegisterNode.create(rs2), pc);
            default -> new IllegalInstructionNode(instruction, pc);
        };
    }

    private static RivetNode parseAmo(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        boolean aq = ((instruction >> 25) & 0b1) == 1;
        boolean rl = ((instruction >> 26) & 0b1) == 1;
        int funct5 = instruction >>> 27;

        RivetOpNode op;
        switch (funct5) {
            case Opcode.Amo.LR -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new LoadWordReservedNode(GetRegisterNode.create(rs1), pc);
                    case Opcode.MemWidth.DOUBLE -> op = new LoadDoubleReservedNode(GetRegisterNode.create(rs1), pc);
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.SC -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new StoreWordConditionalNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2), pc);
                    case Opcode.MemWidth.DOUBLE -> op = new StoreDoubleConditionalNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2), pc);
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                };
            }
            case Opcode.Amo.AMOSWAP -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            GetRegisterNode.create(rs2),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            GetRegisterNode.create(rs2),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.AMOADD -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            new AddNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            new AddNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.AMOAND -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            new AndNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            new AndNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.AMOOR -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            new OrNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            new OrNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.AMOXOR -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            new XorNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            new XorNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.AMOMAX -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            new MaxNode(GetRegisterNode.TEMP_REGISTER, new SignExtendIntNode(GetRegisterNode.create(rs2))),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            new MaxNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.AMOMAXU -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            new MaxUnsignedNode(GetRegisterNode.TEMP_REGISTER, new SignExtendIntNode(GetRegisterNode.create(rs2))),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            new MaxUnsignedNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.AMOMIN -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            new MinNode(GetRegisterNode.TEMP_REGISTER, new SignExtendIntNode(GetRegisterNode.create(rs2))),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            new MinNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            case Opcode.Amo.AMOMINU -> {
                switch (funct3) {
                    case Opcode.MemWidth.WORD -> op = new AmoWordNode(
                            GetRegisterNode.create(rs1),
                            new MinUnsignedNode(GetRegisterNode.TEMP_REGISTER, new SignExtendIntNode(GetRegisterNode.create(rs2))),
                            pc
                    );
                    case Opcode.MemWidth.DOUBLE -> op = new AmoDoubleNode(
                            GetRegisterNode.create(rs1),
                            new MinUnsignedNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs2)),
                            pc
                    );
                    default -> { return new IllegalInstructionNode(instruction, pc); }
                }
            }
            default -> { return new IllegalInstructionNode(instruction, pc); }
        }

        if (rd == 0) {
            // Here we do still want to perform the operation, since it has side effects
            return op;
        }
        return new SetRegisterNode(rd, op);
    }

    private static RivetNode parseOp(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;
        int funct7 = instruction >>> 25;

        RivetOpNode op;
        switch (funct7) {
            case Opcode.Op.INT -> op = switch (funct3) {
                case Opcode.OpInt.ADD -> new AddNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpInt.SLT -> new SetLessThanNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpInt.SLTU -> new SetLessThanUnsignedNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpInt.XOR -> new XorNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpInt.OR -> new OrNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpInt.AND -> new AndNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpInt.SLL -> new ShiftLeftLogicalNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpInt.SR -> new ShiftRightLogicalNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                default -> throw new IllegalStateException("Unexpected value: " + funct3);
            };
            case Opcode.Op.MUL_DIV -> op = switch (funct3) {
                case Opcode.OpMulDiv.MUL -> new MultiplyNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpMulDiv.MULH -> new MultiplyHighNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpMulDiv.MULHSU -> new MultiplyHighSignedUnsignedNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpMulDiv.MULHU -> new MultiplyHighUnsignedNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpMulDiv.DIV -> new DivideNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpMulDiv.DIVU -> new DivideUnsignedNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpMulDiv.REM -> new RemainderNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                case Opcode.OpMulDiv.REMU -> new RemainderUnsignedNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                default -> throw new IllegalStateException("Unexpected value: " + funct3);
            };
            case Opcode.Op.NEG -> {
                switch (funct3) {
                    case Opcode.OpInt.ADD -> op = new SubNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                    case Opcode.OpInt.SR -> op = new ShiftRightArithmeticNode(GetRegisterNode.create(rs1), GetRegisterNode.create(rs2));
                    default -> {
                        return new IllegalInstructionNode(instruction, pc);
                    }
                }
            }
            default -> {
                return new IllegalInstructionNode(instruction, pc);
            }
        }

        if (rd == 0) {
            return new HintNode(instruction);
        }
        return new SetRegisterNode(rd, op);
    }

    private static RivetNode parseLui(int instruction) {
        int rd = (instruction >> 7) & 0b11111;
        long immSigned = (instruction >> 12) << 12;

        if (rd == 0) {
            return new HintNode(instruction);
        }
        return new SetRegisterNode(rd, new ConstantNode(immSigned));
    }

    private static RivetNode parseOp32(int instruction, long pc) {
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
                            GetRegisterNode.create(rs1),
                            GetRegisterNode.create(rs2)
                    ));
                    case Opcode.OpInt.SLL -> op = new SignExtendIntNode(new ShiftLeftLogicalNode(
                            GetRegisterNode.create(rs1),
                            GetRegisterNode.create(rs2),
                            0b11_111
                    ));
                    case Opcode.OpInt.SR -> op = new SignExtendIntNode(new ShiftRightLogicalNode(
                            new IntTruncateNode(GetRegisterNode.create(rs1)),
                            GetRegisterNode.create(rs2),
                            0b11_111
                    ));
                    default -> {
                        return new IllegalInstructionNode(instruction, pc);
                    }
                }
            }
            case Opcode.Op.MUL_DIV -> {
                switch (funct3) {
                    case Opcode.OpMulDiv.MUL -> op = new SignExtendIntNode(new MultiplyNode(
                            GetRegisterNode.create(rs1),
                            GetRegisterNode.create(rs2)
                    ));
                    case Opcode.OpMulDiv.DIV -> op = new SignExtendIntNode(new DivideNode(
                            new SignExtendIntNode(GetRegisterNode.create(rs1)),
                            new SignExtendIntNode(GetRegisterNode.create(rs2))
                    ));
                    case Opcode.OpMulDiv.DIVU -> op = new SignExtendIntNode(new DivideUnsignedNode(
                            new IntTruncateNode(GetRegisterNode.create(rs1)),
                            new IntTruncateNode(GetRegisterNode.create(rs2))
                    ));
                    case Opcode.OpMulDiv.REM -> op = new SignExtendIntNode(new RemainderNode(
                            new SignExtendIntNode(GetRegisterNode.create(rs1)),
                            new SignExtendIntNode(GetRegisterNode.create(rs2))
                    ));
                    case Opcode.OpMulDiv.REMU -> op = new SignExtendIntNode(new RemainderUnsignedNode(
                            new IntTruncateNode(GetRegisterNode.create(rs1)),
                            new IntTruncateNode(GetRegisterNode.create(rs2))
                    ));
                    default -> {
                        return new IllegalInstructionNode(instruction, pc);
                    }
                }
            }
            case Opcode.Op.NEG -> {
                switch (funct3) {
                    case Opcode.OpInt.ADD -> op = new SignExtendIntNode(new SubNode(
                            GetRegisterNode.create(rs1),
                            GetRegisterNode.create(rs2)
                    ));
                    case Opcode.OpInt.SR -> op = new ShiftRightArithmeticNode(
                            new SignExtendIntNode(GetRegisterNode.create(rs1)),
                            GetRegisterNode.create(rs2),
                            0b11_111
                    );
                    default -> {
                        return new IllegalInstructionNode(instruction, pc);
                    }
                }
            }
            default -> {
                return new IllegalInstructionNode(instruction, pc);
            }
        }

        if (rd == 0) {
            return new HintNode(instruction);
        }
        return new SetRegisterNode(rd, op);
    }

    private static RivetNode parseBranch(int instruction, long pc) {
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int rs2 = (instruction >> 20) & 0b11111;

        long jumpOffset = ((instruction >> 8) & 0b1111) << 1
                | ((instruction >> 25) & 0b111111) << 5
                | ((instruction >> 7) & 0b1) << 11
                | (instruction >> 31) << 12;

        long branchPc = pc + jumpOffset;
        long nextInstrPc = pc + 4;

        return switch (funct3) {
            case Opcode.Branch.BEQ -> new BranchEqualNode(
                    GetRegisterNode.create(rs1), GetRegisterNode.create(rs2),
                    branchPc, nextInstrPc
            );
            case Opcode.Branch.BNE -> new BranchEqualNode(
                    GetRegisterNode.create(rs1), GetRegisterNode.create(rs2),
                    nextInstrPc, branchPc
            );
            case Opcode.Branch.BLT -> new BranchLessThanNode(
                    GetRegisterNode.create(rs1), GetRegisterNode.create(rs2),
                    branchPc, nextInstrPc
            );
            case Opcode.Branch.BLTU -> new BranchLessThanUnsignedNode(
                    GetRegisterNode.create(rs1), GetRegisterNode.create(rs2),
                    branchPc, nextInstrPc
            );
            case Opcode.Branch.BGE -> new BranchLessThanNode(
                    GetRegisterNode.create(rs1), GetRegisterNode.create(rs2),
                    nextInstrPc, branchPc
            );
            case Opcode.Branch.BGEU -> new BranchLessThanUnsignedNode(
                    GetRegisterNode.create(rs1), GetRegisterNode.create(rs2),
                    nextInstrPc, branchPc
            );
            default -> new IllegalInstructionNode(instruction, pc);
        };
    }

    private static RivetNode parseJal(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        long jumpOffset = (instruction >> 12 & 0b1111_1111) << 12
                | ((instruction >> 20 & 0b1) << 11)
                | ((instruction >> 21 & 0b11_1111_1111) << 1)
                | ((instruction >> 31) << 20);

        long newPc = pc + jumpOffset;

        if (rd == 0) {
            return new JumpNode(new ConstantNode(newPc));
        }
        return new JumpAndLinkNode(new ConstantNode(newPc), new SetRegisterNode(rd, new ConstantNode(pc + 4)));
    }

    private static RivetNode parseJalr(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immSigned = instruction >> 20;

        if (funct3 != 0) {
            return new IllegalInstructionNode(instruction, pc);
        }

        var newPc = new AddNode(GetRegisterNode.create(rs1), new ConstantNode(immSigned));

        if (rd == 0) {
            return new JumpNode(newPc);
        }
        return new JumpAndLinkNode(newPc, new SetRegisterNode(rd, new ConstantNode(pc + 4)));
    }

    private static RivetNode parseSystem(int instruction, long pc) {
        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int funct12 = instruction >>> 20;

        switch (funct3) {
            case Opcode.System.PRIV -> {
                if (rd != 0 || rs1 != 0) {
                    return new IllegalInstructionNode(instruction, pc);
                }

                return switch (funct12) {
                    case Opcode.Priv.EBREAK -> new BreakpointNode(pc);
                    case Opcode.Priv.ECALL -> new EnvironmentCallNode(pc);
                    case Opcode.Priv.MRET -> new MachineReturn(instruction, pc);
                    // Not technically a hint, but we don't have a mechanism for waiting on interrupts yet.
                    case Opcode.Priv.WFI -> new HintNode(instruction);
                    default -> new IllegalInstructionNode(instruction, pc);
                };
            }
            case Opcode.System.CSRRW -> {
                if (rd == 0) {
                    return new SetCsrNode(GetRegisterNode.create(rs1), funct12, pc, instruction);
                }
                return new CsrRmwNode(GetRegisterNode.create(rs1), funct12, rd, pc, instruction);
            }
            case Opcode.System.CSRRS -> {
                if (rs1 == 0) {
                    return new SetRegisterNode(rd, new GetCsrNode(funct12, pc, instruction));
                }
                return new CsrRmwNode(new OrNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs1)), funct12, rd, pc, instruction);
            }
            case Opcode.System.CSRRC -> {
                if (rs1 == 0) {
                    return new SetRegisterNode(rd, new GetCsrNode(funct12, pc, instruction));
                }
                return new CsrRmwNode(new MaskNode(GetRegisterNode.TEMP_REGISTER, GetRegisterNode.create(rs1)), funct12, rd, pc, instruction);
            }
            case Opcode.System.CSRRWI -> {
                if (rd == 0) {
                    return new SetCsrNode(new ConstantNode(rs1), funct12, pc, instruction);
                }
                return new CsrRmwNode(new ConstantNode(rs1), funct12, rd, pc, instruction);
            }
            case Opcode.System.CSRRSI -> {
                if (rs1 == 0) {
                    return new SetRegisterNode(rd, new GetCsrNode(funct12, pc, instruction));
                }
                return new CsrRmwNode(new OrNode(GetRegisterNode.TEMP_REGISTER, new ConstantNode(rs1)), funct12, rd, pc, instruction);
            }
            case Opcode.System.CSRRCI -> {
                if (rs1 == 0) {
                    return new SetRegisterNode(rd, new GetCsrNode(funct12, pc, instruction));
                }
                return new CsrRmwNode(new MaskNode(GetRegisterNode.TEMP_REGISTER, new ConstantNode(rs1)), funct12, rd, pc, instruction);
            }
            default -> {
                return new IllegalInstructionNode(instruction, pc);
            }
        }
    }
}
