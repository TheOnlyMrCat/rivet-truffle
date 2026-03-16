package au.mrcat.launcher;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.IOException;

public class Main {
    static void main() {
        byte[] testProgram = new byte[] {
                0x17, 0x03, 0x00, 0x00, // auipc t1, 0
                (byte) 0x83, 0x25, 0x03, 0x00, // lw a1, 0(t1)
                0x13, 0x05, (byte) 0xa5, 0x00, // addi a0, a0, 10
                0x13, 0x05, (byte) 0xa5, 0x00, // addi a0, a0, 10
                0x73, 0x00, 0x50, 0x10, // wfi
                0x6f, 0x00, 0x00, 0x00, // j 0
        };

        Source source;
        try {
            source = Source.newBuilder("rv64", ByteSequence.create(testProgram), "<literal>").build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Context context = Context.newBuilder("rv64").build();
        Value result = context.eval(source);
    }
}
