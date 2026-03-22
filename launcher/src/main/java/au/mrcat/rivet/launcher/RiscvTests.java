package au.mrcat.rivet.launcher;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RiscvTests {
    static void main(String[] args) {
        var riscvTestsDir = System.getenv("RISCV_TESTS");

        Pattern filenamePattern;
        if (args.length > 0) {
            filenamePattern = Pattern.compile(args[0]);
        } else {
            filenamePattern = null;
        }

        var sources = new ArrayList<Source>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Path.of(riscvTestsDir), (Path p) -> {
            if (filenamePattern != null && !filenamePattern.matcher(p.getFileName().toString()).find()) {
                return false;
            }
            File f = p.toFile();
            return f.isFile() && f.canExecute();
        })) {
            for (Iterator<Path> it = directoryStream.iterator(); it.hasNext(); ) {
                Path p = it.next();
                sources.add(Source.newBuilder("rv64", p.toFile()).build());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Context context = Context.newBuilder("rv64").build();
        int passed = 0;
        var failed = new HashMap<String, Long>();
        for (Source source : sources) {
            IO.print(source.getName());
            IO.print(": ...");

            long result = context.eval(source).asLong();

            IO.print("\010\010\010");
            if (result == 0) {
                IO.println("\033[32mpass\033[0m");
            } else {
                IO.print("\033[31mfail ");
                IO.print(result);
                IO.println("\033[0m");
                failed.put(source.getName(), result);
            }
        }

        IO.println();
        IO.println("Test summary:");
        IO.print(sources.size());
        IO.print(" run; ");
        IO.print(passed);
        IO.print(" passed; ");
        IO.print(failed.size());
        IO.println(" failed");

        if (!failed.isEmpty()) {
            IO.println();
            IO.println("Failed tests:");
            for (var failedTest : failed.entrySet()) {
                IO.print("\033[91m");
                IO.print(failedTest.getKey());
                IO.print("\033[0m failed with code ");
                IO.println(failedTest.getValue());
            }
        }
    }
}
