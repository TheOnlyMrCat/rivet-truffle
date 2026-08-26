# SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
#
# SPDX-License-Identifier: MPL-2.0
{
  description = "A toy RISC-V emulator";

  inputs = {
    nixpkgs.url = "https://channels.nixos.org/nixpkgs-unstable/nixexprs.tar.xz";
    flake-utils.url = "github:numtide/flake-utils";
    crane.url = "github:ipetkov/crane";
    rust-overlay.url = "github:oxalica/rust-overlay";
    zig-overlay.url = "github:mitchellh/zig-overlay";
    zls.url = "github:zigtools/zls";
  };

  outputs = {
    self,
    nixpkgs,
    flake-utils,
    crane,
    rust-overlay,
    zig-overlay,
    zls,
    ...
  }: let
    overlays = [rust-overlay.overlays.default zig-overlay.overlays.default];
    systems = builtins.attrNames rust-overlay.packages;
  in
    flake-utils.lib.eachSystem systems (
      system: let
        pkgs = import nixpkgs {inherit overlays system;};
        rvPkgs = pkgs.pkgsCross.riscv64-embedded;

        # Package definitions for dev shells
        ubootQemuRiscv64SmodeWithDebug = pkgs.pkgsCross.riscv64.ubootQemuRiscv64Smode.override {
          filesToInstall = [
            "u-boot.bin"
            "u-boot.sym"
            "u-boot.map"
            "u-boot"
          ];
        };

        riscv-arch-test-version = "3.10.0";
        riscv-arch-test = pkgs.fetchFromGitHub {
          owner = "riscv-non-isa";
          repo = "riscv-arch-test";
          tag = riscv-arch-test-version;
          hash = "sha256-nhmKQJGyqbtAt51yDE1YdcD9GSQQv77VmLYpO85j120=";
        };

        toolchain = [
          pkgs.zigpkgs."0.15.2"
          zls.packages.${pkgs.system}.default
          pkgs.dtc
          pkgs.glib
          pkgs.libslirp
          pkgs.pkg-config
        ];

        rvToolchain = [
          (pkgs.writeShellScriptBin "riscv64-unknown-elf-gcc" ''exec ${rvPkgs.lib.getExe' rvPkgs.stdenv.cc "riscv64-none-elf-gcc"} "$@"'')
          (pkgs.writeShellScriptBin "riscv64-unknown-elf-objdump" ''exec ${rvPkgs.lib.getExe' rvPkgs.stdenv.cc "riscv64-none-elf-objdump"} "$@"'')
        ];

        rpathLibs = [
          pkgs.glib
          pkgs.libslirp
        ];

        riscof-tests = [
          (pkgs.python3.withPackages (ps: [
            (ps.riscof.overrideAttrs (prevAttrs: {
              patches = prevAttrs.patches ++ [./riscof/0001-Add-json-report-output.patch];
            }))
            ps.distutils
          ]))
          pkgs.sail-riscv
        ];

        # Rust package built with crane
        craneLib = (crane.mkLib pkgs).overrideToolchain (
          p: p.rust-bin.stable.latest.default
        );

        commonArgs = {
          src = pkgs.lib.fileset.toSource {
            root = ./.;
            fileset = pkgs.lib.fileset.unions [
              ./Cargo.toml
              ./Cargo.lock
              ./build.zig
              ./build.zig.zon
              ./build.rs
              ./softfp
              ./src
            ];
          };
          strictDeps = true;

          nativeBuildInputs =
            toolchain
            ++ [
              pkgs.zig.hook
            ];
          buildInputs = rpathLibs;
          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath rpathLibs;
        };

        cargoArtifacts = craneLib.buildDepsOnly commonArgs;

        rivet = craneLib.buildPackage (
          commonArgs
          // {
            inherit cargoArtifacts;

            meta = {
              description = "Toy RV64GC emulator";
              homepage = "https://sr.ht/~theonlymrcat/rivet";
              license = pkgs.lib.licenses.mpl20;
              # maintainers = [pkgs.lib.maintainers.theonlymrcat];
              mainProgram = "rivet";
              platforms = pkgs.lib.platforms.unix ++ pkgs.lib.platforms.windows;
            };
          }
        );
      in {
        packages = {
          inherit rivet;
          default = rivet;
        };

        apps = let
          rivet-app =
            flake-utils.lib.mkApp {
              drv = rivet;
            }
            // {
              inherit (rivet) meta;
            };
          rivet-opensbi-app =
            flake-utils.lib.mkApp {
              drv = pkgs.writeShellScriptBin "rivet-opensbi.sh" ''
                exec ${pkgs.lib.getExe rivet} ${(pkgs.pkgsCross.riscv64.opensbi.override {
                  withPayload = "${pkgs.pkgsCross.riscv64.ubootQemuRiscv64Smode}/u-boot.bin";
                })}/share/opensbi/lp64/generic/firmware/fw_payload.bin "$@"
              '';
            }
            // {
              inherit (rivet) meta;
            };
        in {
          default = rivet-app;
          rivet = rivet-app;
          rivet-opensbi = rivet-opensbi-app;
        };

        checks = {
          inherit rivet;
          rivet-clippy = craneLib.cargoClippy (
            commonArgs
            // {
              inherit cargoArtifacts;
              cargoClippyExtraArgs = "--all-targets -- --deny warnings";
            }
          );

          rivet-riscof = pkgs.stdenvNoCC.mkDerivation {
            name = "rivet-riscof-check";
            version = riscv-arch-test-version;

            src = pkgs.lib.fileset.toSource {
              root = ./riscof;
              fileset = pkgs.lib.fileset.unions [
                ./riscof/config.ini
                ./riscof/rivet
                ./riscof/sail_cSim
              ];
            };

            nativeBuildInputs = rvToolchain ++ riscof-tests ++ [rivet pkgs.jq];

            RISCV_ARCH_TEST = riscv-arch-test;

            doCheck = true;

            buildPhase = ''
              riscof run \
                --config ./config.ini \
                --suite $RISCV_ARCH_TEST/riscv-test-suite/rv64i_m/ \
                --env $RISCV_ARCH_TEST/riscv-test-suite/env \
                --no-browser
            '';

            checkPhase = ''
              jq '.results[] | select(.res == "Failed") | .name | ltrimstr("'$RISCV_ARCH_TEST'/")' riscof_work/report.json -r \
                | sort \
                > failures.txt
              cp ${./riscof/expected_failures.txt} ./expected_failures.txt
              diff -u expected_failures.txt failures.txt
            '';

            installPhase = ''
              mkdir $out
              cp riscof_work/report.html $out
              cp riscof_work/report.json $out
            '';
          };
        };

        devShells = {
          default = craneLib.devShell {
            packages = toolchain ++ rvToolchain;
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath rpathLibs;
          };

          riscof = craneLib.devShell {
            packages = [rivet] ++ toolchain ++ rvToolchain ++ riscof-tests;
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath rpathLibs;
            RISCV_ARCH_TEST = riscv-arch-test;
          };

          u-boot = craneLib.devShell {
            packages = toolchain ++ rvToolchain;
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath rpathLibs;

            UBOOT = "${ubootQemuRiscv64SmodeWithDebug}";
            OPENSBI_UBOOT = "${(pkgs.pkgsCross.riscv64.opensbi.override {
              withPayload = "${ubootQemuRiscv64SmodeWithDebug}/u-boot.bin";
            })}/share/opensbi/lp64/generic/firmware";
          };
        };
      }
    );
}
