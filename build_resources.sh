#/usr/bin/env bash

set -euxo pipefail

mkdir -p language/generated_resources/au/mrcat/rivet

# Device tree
dtc -o language/generated_resources/au/mrcat/rivet/rivet-truffle.dtb language/src/main/devicetree/rivet-truffle.dts

# OpenSBI firmware
pushd vendor/opensbi
make PLATFORM=generic
popd

cp vendor/opensbi/build/platform/generic/firmware/fw_dynamic.bin language/generated_resources/au/mrcat/rivet/fw_dynamic.bin
