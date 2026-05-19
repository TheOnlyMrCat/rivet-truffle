package au.mrcat.rivet.launcher;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class RiscvArchTest {
    static record TestResults(int count, int passed) {
    }

    static void main(String[] args) {
        var riscvTestsDir = System.getenv("RISCV_ARCH_TEST_ELFS");

        Pattern extensionPattern;
        if (args.length > 0) {
            extensionPattern = Pattern.compile(args[0]);
        } else {
            extensionPattern = null;
        }

        var rv64iBasePath = Path.of(riscvTestsDir, "rv64i");
        var sources = new TreeMap<String, ArrayList<Source>>();

        // $RISCV_ARCH_TEST_ELFS/rv64i contains a bunch of subdirectories, one for each extension
        try (DirectoryStream<Path> extensionDirectoryStream = Files.newDirectoryStream(rv64iBasePath, (Path p) -> {
            if (extensionPattern != null && !extensionPattern.matcher(p.getFileName().toString()).find()) {
                return false;
            }
            File f = p.toFile();
            return f.isDirectory();
        })) {
            // Loop over each extension
            for (Path extensionPath : extensionDirectoryStream) {
                var extensionSources = new ArrayList<Source>();

                // Look for elf files in this extension subdirectory
                try (DirectoryStream<Path> executableDirectoryStream = Files.newDirectoryStream(extensionPath, (Path p) -> {
                    if (extensionPattern != null && !extensionPattern.matcher(p.getFileName().toString()).find()) {
                        return false;
                    }
                    File f = p.toFile();
                    return f.isFile() && f.canExecute();
                })) {
                    // Add the elf files to this extension's source
                    for (Path p : executableDirectoryStream) {
                        extensionSources.add(Source.newBuilder("rv64", p.toFile()).build());
                    }
                }

                extensionSources.sort(Comparator.comparing(Source::getName));

                // Record the elfs as being from this extension
                sources.put(extensionPath.getFileName().toString(), extensionSources);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Context context = Context.newBuilder("rv64").out(OutputStream.nullOutputStream()).build();
        int totalRun = 0;
        int totalPassed = 0;
        var results = new TreeMap<String, TestResults>();
        for (var entry : sources.entrySet()) {
            var extension = entry.getKey();
            var extensionSources = entry.getValue();

            IO.println("Testing " + extension + ":");
            int passed = 0;
            for (Source s : extensionSources) {
                IO.print(s.getName());
                IO.print(": ...");
                totalRun++;

                try {
                    long result = context.eval(s).asLong();

                    IO.print("\010\010\010");
                    if (result == 0) {
                        IO.println("\033[32mpass\033[0m");
                        passed++;
                        totalPassed++;
                    } else {
                        IO.println("\033[31mfail\033[0m");
                    }
                } catch (PolyglotException e) {
                    IO.print("\010\010\010\033[31mthrows ");
                    IO.print(e.toString());
                    IO.println("\033[0m");
                }
            }

            results.put(extension, new TestResults(extensionSources.size(), passed));
            IO.println();
        }

        IO.println(String.format("Test summary: %3d run; %3d passed; %3d failed",
                totalRun,
                totalPassed,
                totalRun - totalPassed));
        for (var entry : results.entrySet()) {
            var extension = entry.getKey();
            var extensionResults = entry.getValue();

            IO.println(String.format("%12s: %3d run; %3d passed; %3d failed",
                    extension,
                    extensionResults.count,
                    extensionResults.passed,
                    extensionResults.count - extensionResults.passed));
        }
    }
}
