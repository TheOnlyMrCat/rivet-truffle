#/usr/bin/env bash

set -euxo pipefail

resources_dir=language/src/main/resources/au/mrcat/rivet
mkdir -p $resources_dir

# Device tree
dtc -o $resources_dir/rivet-truffle.dtb language/src/main/devicetree/rivet-truffle.dts

# OpenSBI firmware
pushd vendor/opensbi
make PLATFORM=generic
popd

cp vendor/opensbi/build/platform/generic/firmware/fw_dynamic.bin $resources_dir/fw_dynamic.bin
