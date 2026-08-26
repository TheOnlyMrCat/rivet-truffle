# RISCOF test suite

This directory contains a riscof plugin for rivet, allowing rivet to be tested against
`riscv_sim_rv64`. Documentation for riscof is available
[here](https://riscof.readthedocs.io/en/stable/index.html).

## Running the tests

First, install Python (>= 3.6), `riscof`, the RISCV-GNU Toolchain, and the SAIL C-emulator as
described in riscof's [Quickstart](https://riscof.readthedocs.io/en/stable/installation.html).
(These tools are provided as part of the `nix develop .#riscof` shell).

You can then download the riscv arch tests with:

```
git clone https://github.com/riscv-non-isa/riscv-arch-test
```

And run the test suite from this directory:

```
riscof validateyaml --config=config.ini
riscof testlist --config=config.ini --suite=riscv-arch-test/riscv-test-suite/rv64i_m/ --env=riscv-arch-test/riscv-test-suite/env
riscof run --config=config.ini --suite=riscv-arch-test/riscv-test-suite/rv64i_m/ --env=riscv-arch-test/riscv-test-suite/env
```
