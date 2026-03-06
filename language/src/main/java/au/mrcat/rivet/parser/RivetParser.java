package au.mrcat.rivet.parser;

import au.mrcat.rivet.nodes.RiscvDispatchNode;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.ArrayList;

public final class RivetParser {
    public static RiscvDispatchNode parse(ByteSequence memory) {
        var instructions = new ArrayList<Integer>();

        for (int offset = 0; offset < memory.length(); offset += 4) {
            int instruction = (int) memory.byteAt(offset) & 0xff
                    | ((int) memory.byteAt(offset + 1) & 0xff) << 8
                    | ((int) memory.byteAt(offset + 2) & 0xff) << 16
                    | ((int) memory.byteAt(offset + 3) & 0xff) << 24;
            instructions.add(instruction);
        }

        return new RiscvDispatchNode(instructions.stream().mapToInt(i->i).toArray(), 0x8000_0000L);
    }
}
