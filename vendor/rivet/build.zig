// SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    // SoftFP (software floating point)
    const softfp_lib_mod = b.createModule(.{
        .target = target,
        .optimize = optimize,
    });
    softfp_lib_mod.addCSourceFile(.{ .file = b.path("softfp/softfp.c") });

    const softfp_lib = b.addLibrary(.{
        .name = "softfp",
        .root_module = softfp_lib_mod,
    });
    softfp_lib.linkLibC();
    const softfp_artifact = b.addInstallArtifact(softfp_lib, .{});

    const softfp_header = b.addTranslateC(.{
        .root_source_file = b.path("softfp/softfp.h"),
        .target = target,
        .optimize = optimize,
    }).createModule();

    // Device tree (requires dtc to be installed)
    const dtc = b.addSystemCommand(&[_][]const u8{"dtc"});
    dtc.addFileArg(b.path("src/rivet.dts"));
    dtc.addArg("-o");
    const dtb_path = dtc.addOutputFileArg("rivet.dtb");

    // Library target
    const rivet_lib_mod = b.addModule("rivet", .{
        .root_source_file = b.path("src/lib.zig"),
        .target = target,
        .optimize = optimize,
        .pic = true,
    });
    rivet_lib_mod.addIncludePath(b.path("include"));
    rivet_lib_mod.addAnonymousImport("rivet.dtb", .{
        .root_source_file = dtb_path,
    });
    rivet_lib_mod.addImport("softfp", softfp_header);

    const rivet_lib = b.addLibrary(.{
        .linkage = .static,
        .name = "rivet",
        .root_module = rivet_lib_mod,
    });
    rivet_lib.linkLibrary(softfp_lib);
    switch (optimize) {
        .Debug, .ReleaseSafe => rivet_lib.bundle_compiler_rt = true,
        .ReleaseFast, .ReleaseSmall => {},
    }
    const lib_artifact = b.addInstallArtifact(rivet_lib, .{});
    lib_artifact.step.dependOn(&softfp_artifact.step);
    b.getInstallStep().dependOn(&lib_artifact.step);
}
