{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-26.05";
  };

  outputs = {self, nixpkgs}:
  let
    forAllSystems = function:
      nixpkgs.lib.genAttrs [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ] (system: function (import nixpkgs {
        inherit system;
        config.allowUnfree = true;
      }));
  in {
    devShells = forAllSystems (pkgs: let
      rvPkgs = pkgs.pkgsCross.riscv64-embedded;
      rv64-binutils = pkgs.runCommandLocal "riscv64-unknown-elf-gcc-${rvPkgs.stdenv.cc.version}" {} ''
          mkdir -p $out/bin

          for path in ${rvPkgs.stdenv.cc}/bin/*; do
            base=$(basename $path)
            ln -s $(realpath $path) $out/bin/''${base/#riscv64-none-elf-/riscv64-unknown-elf-}
          done
        '';
    in {
      default = pkgs.mkShell {
        packages = [
          # Java toolchain
          pkgs.graalvmPackages.graalvm-oracle_25
          pkgs.maven
          pkgs.just

          # RISC-V toolchains
          pkgs.llvmPackages_22.clang-unwrapped
          pkgs.llvmPackages_22.lld
          pkgs.llvmPackages_22.libllvm
          rv64-binutils

          # Other toolchains
          pkgs.dtc
        ];
      };
    });
  };
}
