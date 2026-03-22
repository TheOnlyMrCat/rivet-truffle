package au.mrcat.rivet.launcher;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;

public class Main {
    static void main(String[] args) {
        Source source;
        try {
            source = Source.newBuilder("rv64", new File(args[0])).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Context context = Context.newBuilder("rv64").build();
        Value result = context.eval(source);
        System.exit((int) result.asLong());
    }
}
