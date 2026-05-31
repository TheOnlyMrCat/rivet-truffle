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
import java.util.*;
import java.util.regex.Pattern;

public class RiscvArchTest {
    record TestResults(int run, int passed) {
    }

    record TestBank(String name, SortedMap<String, ArrayList<Source>> tests) {
    }

    record BankResults(String name, int totalRun, int totalPassed, SortedMap<String, TestResults> extensions) {
    }

    static SortedMap<String, ArrayList<Source>> readTestBank(Path bankBase, Pattern extensionPattern) throws IOException {
        var sources = new TreeMap<String, ArrayList<Source>>();

        // $RISCV_ARCH_TEST_ELFS/rv64i contains a bunch of subdirectories, corresponding roughly to extensions
        try (DirectoryStream<Path> extensionDirectoryStream = Files.newDirectoryStream(bankBase, (Path p) -> {
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
        }

        return sources;
    }

    static SortedMap<String, TestBank> readTests(Path base, Pattern extensionPattern) throws IOException {
        var banks = new TreeMap<String, TestBank>();

        try (DirectoryStream<Path> bankDirectoryStream = Files.newDirectoryStream(base, (Path p) -> {
            File f = p.toFile();
            return f.isDirectory();
        })) {
            for (Path bankPath : bankDirectoryStream) {
                var bankName = bankPath.getFileName().toString();
                var bankSources = readTestBank(bankPath, extensionPattern);
                if (bankSources.isEmpty()) {
                    continue;
                }
                banks.put(bankPath.getFileName().toString(), new TestBank(bankName, bankSources));
            }
        }

        return banks;
    }

    static BankResults runBank(Context context, TestBank bank) {
        int totalRun = 0;
        int totalPassed = 0;
        var results = new TreeMap<String, TestResults>();
        for (var entry : bank.tests().entrySet()) {
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
        return new BankResults(bank.name(), totalRun, totalPassed, results);
    }

    static void formatResults(BankResults results) {
        IO.println(String.format("Bank %s results: %d run; %d passed; %d failed",
                results.name(),
                results.totalRun(),
                results.totalPassed(),
                results.totalRun() - results.totalPassed()));

        int firstColWidth = results.extensions().keySet().stream()
                .map(String::length)
                .reduce(0, Integer::max);
        String tableRow = String.format("%%%ds: %%3d run; %%3d passed; %%3d failed", firstColWidth);

        for (var entry : results.extensions().entrySet()) {
            var extension = entry.getKey();
            var extensionResults = entry.getValue();

            IO.println(String.format(tableRow,
                    extension,
                    extensionResults.run(),
                    extensionResults.passed(),
                    extensionResults.run() - extensionResults.passed()));
        }
    }

    static void main(String[] args) throws IOException {
        var riscvTestsDir = System.getenv("RISCV_ARCH_TEST_ELFS");

        Pattern extensionPattern;
        if (args.length > 0) {
            extensionPattern = Pattern.compile(args[0]);
        } else {
            extensionPattern = null;
        }

        var banks = readTests(Path.of(riscvTestsDir), extensionPattern);

        Context context = Context.newBuilder("rv64").out(OutputStream.nullOutputStream()).build();
        var results = new TreeMap<String, BankResults>();
        for (TestBank bank : banks.values()) {
            results.put(bank.name(), runBank(context, bank));
        }

        for (BankResults bankResults : results.values()) {
            IO.println();
            formatResults(bankResults);
        }
    }
}
