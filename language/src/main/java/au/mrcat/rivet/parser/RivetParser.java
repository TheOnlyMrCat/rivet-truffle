package au.mrcat.rivet.parser;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.nodes.RiscvDispatchNode;
import au.mrcat.rivet.riscv.Opcode;

import java.util.ArrayList;

public final class RivetParser {
    public static RiscvDispatchNode extractBasicBlock(RivetContext context, long baseAddress) {
        var instructions = new ArrayList<Integer>();

        // Cap basic block length at 1024 for interrupt checking, etc.
        bb: while (instructions.size() < 1024) {
            int instruction = context.readInt(baseAddress);
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

        return new RiscvDispatchNode(instructions.stream().mapToInt(i->i).toArray(), baseAddress);
    }
}
