import? '.local.just'

rlib_path := justfile_directory() / "vendor/rivet/target/release"
resources_path := "language/src/main/resources/au/mrcat/rivet"
classpath_path := "target/cp.txt"

default:
  @just --choose

clean:
    mvn clean
    test -d {{resources_path}} && rm -r {{resources_path}} || true
    make -C vendor/opensbi clean

build-resources:
    mkdir -p {{resources_path}}

    # Device tree
    dtc -o {{resources_path / "rivet-truffle.dtb"}} language/src/main/devicetree/rivet-truffle.dts

    # OpenSBI firmware
    make -C vendor/opensbi PLATFORM=generic LLVM=1
    cp vendor/opensbi/build/platform/generic/firmware/fw_dynamic.bin {{resources_path / "fw_dynamic.bin"}}

build-devices:
    cd vendor/rivet && cargo build --release

clean-coremark:
    make -C vendor/coremark clean PORT_DIR=rivet

build-coremark iterations="40000":
    make -C vendor/coremark link PORT_DIR=rivet ITERATIONS={{iterations}}

build-coremark-mmu iterations="40000":
    make -C vendor/coremark link PORT_DIR=rivet ITERATIONS={{iterations}} XASFLAGS='-DMMU=1'

build: build-devices
    mvn install
    mvn dependency:build-classpath -pl launcher -Dmdep.outputFile={{classpath_path}}

[env("RISCV_ARCH_TEST_ELFS", "vendor/riscv-arch-test/work/rivet-rv64imac/elfs")]
test: build
    java -p $(<{{"launcher" / classpath_path}}):launcher/target/classes -m au.mrcat.rivet.launcher/au.mrcat.rivet.launcher.RiscvArchTest

bench:
    LD_LIBRARY_PATH={{rlib_path}} java -p $(<{{"launcher" / classpath_path}}):launcher/target/classes -m au.mrcat.rivet.launcher/au.mrcat.rivet.launcher.Main vendor/coremark/coremark.elf


[script("python")]
bench-full build_target="build-coremark":
    from math import log10, floor
    import os
    import shutil
    import subprocess

    iterations = 10000
    for i in range(5):
        print(f"\rCalibrating: [{i+1}/5] (iterations={iterations})", end="")
        subprocess.run(["just", "clean-coremark"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["just", "{{build_target}}", str(iterations)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        result = subprocess.run("LD_LIBRARY_PATH={{rlib_path}} java -p $(<launcher/target/cp.txt):launcher/target/classes -m au.mrcat.rivet.launcher/au.mrcat.rivet.launcher.Main vendor/coremark/coremark.elf", shell=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        lines = result.stdout.split("\n")
        time = int(lines[2][19:].strip())/1000000
        ips = iterations/time
        iterations = ips * 20
        iterations = int(round(iterations, -int(floor(log10(iterations))) + 1))


    print()
    print(f"Iterations: {iterations}")

    os.makedirs("results", exist_ok=True)
    for i in range(100):
        result = subprocess.run("LD_LIBRARY_PATH={{rlib_path}} java -p $(<launcher/target/cp.txt):launcher/target/classes -m au.mrcat.rivet.launcher/au.mrcat.rivet.launcher.Main vendor/coremark/coremark.elf", shell=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        lines = result.stdout.split("\n")
        time = int(lines[2][19:].strip())/1000000
        iterations = int(lines[5][19:].strip())
        if time < 10:
            print("INVALID RESULT")
        print(f"{iterations/time:.6f}")



# Most of this file is designed to be executed within the `nix develop` shell, but nixpkgs doesn't
# have a recent enough `sail-riscv` to build the tests (and upstream changes have made it harder to
# package) so this recipe has to be run on a system or in a container set up according to the
# instructions in vendor/riscv-arch-test/README.md

build-tests:
    # Extension exclusion justifications:
    # - Sm: Excluded by default
    # - Ssstrict{Sm,S,U}: Assembler errors with unrecognised opcodes "CSRW(mtvec, t0)"
    make -C vendor/riscv-arch-test --jobs $(nproc) CONFIG_FILES=config/rivet/rivet-rv64imac/test_config.yaml EXCLUDE_EXTENSIONS=SsstrictS,SsstrictU,SsstrictSm
