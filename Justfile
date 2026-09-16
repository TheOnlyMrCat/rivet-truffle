import? '.local.just'

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

    # Devices implementation
    cd vendor/rivet && cargo build

    # OpenSBI firmware
    make -C vendor/opensbi PLATFORM=generic LLVM=1
    cp vendor/opensbi/build/platform/generic/firmware/fw_dynamic.bin {{resources_path / "fw_dynamic.bin"}}

clean-coremark:
    make -C vendor/coremark clean PORT_DIR=rivet

build-coremark:
    make -C vendor/coremark link PORT_DIR=rivet

build:
    mvn compile
    mvn dependency:build-classpath -pl launcher -Dmdep.outputFile={{classpath_path}}

[env("RISCV_ARCH_TEST_ELFS", "vendor/riscv-arch-test/work/rivet-rv64imac/elfs")]
test: build
    java -p $(<{{"launcher" / classpath_path}}):launcher/target/classes -m au.mrcat.rivet.launcher/au.mrcat.rivet.launcher.RiscvArchTest

bench: build build-coremark
    java -p $(<{{"launcher" / classpath_path}}):launcher/target/classes -m au.mrcat.rivet.launcher/au.mrcat.rivet.launcher.Main vendor/coremark/coremark.elf
    


# Most of this file is designed to be executed within the `nix develop` shell, but nixpkgs doesn't
# have a recent enough `sail-riscv` to build the tests (and upstream changes have made it harder to
# package) so this recipe has to be run on a system or in a container set up according to the
# instructions in vendor/riscv-arch-test/README.md

build-tests:
    # Extension exclusion justifications:
    # - Sm: Excluded by default
    # - Ssstrict{Sm,S,U}: Assembler errors with unrecognised opcodes "CSRW(mtvec, t0)"
    make -C vendor/riscv-arch-test --jobs $(nproc) CONFIG_FILES=config/rivet/rivet-rv64imac/test_config.yaml EXCLUDE_EXTENSIONS=SsstrictS,SsstrictU,SsstrictSm
