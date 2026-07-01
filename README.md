# rivet-truffle

## Building and running

Rivet-truffle requires a Java 25-compatible JDK to build and run, preferably GraalVM 25.

There are three entry points in the `launcher/` subproject:

- `Main.java`: Runs an arbitrary ELF file, provided as a command-line argument.
- `RiscvTests.java`: Runs the legacy [riscv-tests](https://github.com/riscv-software-src/riscv-tests)
  test suite (as provided by the environment).
- `RiscvArchTest.java`: Runs the [riscv-arch-test](https://github.com/riscv/riscv-arch-test) test
  suite (as provided by the environment).

These can be run from IntelliJ after importing the maven project. (In theory, they can also be run
from the command-line, however, I have been unable to get this working in testing thus far)

To run any programs depending on a device tree or OpenSBI, these must be built and provided as
resources. The `build_resources.sh` script will do this. It requires `dtc` (devicetree compiler) and
a RISC-V-cross-compiling LLVM toolchain to be present on `$PATH`. The `nix develop` shell provides
these.

### Debugging Truffle Code

To debug Truffle's compilation/optimisation, and to dump
[IGV](https://www.graalvm.org/latest/tools/igv/) graphs to the current working directory, add the
following VM options to the GraalVM command line:

```
-Dpolyglot.engine.AllowExperimentalOptions=true
-Djdk.graal.Dump=Truffle:1
-Dpolyglot.engine.BackgroundCompilation=false
-Dpolyglot.engine.TraceCompilation=true
-Dpolyglot.compiler.TraceInlining=true
-Dpolyglot.engine.TraceCompilationAST=true
-Dpolyglot.engine.TraceCompilationDetails=true
```
