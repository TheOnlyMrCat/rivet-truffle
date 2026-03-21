package au.mrcat.rivet.parser;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.nodes.RiscvDispatchNode;
import au.mrcat.rivet.nodes.RiscvStartupNode;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.nodes.data.EncodedInstructionNode;
import au.mrcat.rivet.riscv.Opcode;
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

        return new RiscvDispatchNode(instructions.stream().map(EncodedInstructionNode::new).toArray(RivetNode[]::new), baseAddress);
    }
}
