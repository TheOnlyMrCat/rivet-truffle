package au.mrcat.rivet.launcher;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.File;
import java.io.IOException;

public class TruffleTests {
    static void main(String[] args) throws IOException {
        byte[] program = new byte[] {

        };
        Source source = Source.newBuilder("rv64", ByteSequence.create(program), "<literal>").build();

        Context context = Context.newBuilder("rv64").build();
        Value result = context.eval(source);
        System.exit((int) result.asLong());
    }
}
