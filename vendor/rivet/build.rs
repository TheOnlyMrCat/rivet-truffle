// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::process::{Command, ExitCode};

fn main() -> ExitCode {
    let out_dir = std::env::var("OUT_DIR").expect("cargo build environment should provide OUT_DIR");

    let mut zig_build = Command::new("zig");
    zig_build.args(["build", "-p", &out_dir]);

    if std::env::var("OPT_LEVEL")
        .ok()
        .and_then(|s| s.parse::<u8>().ok())
        .unwrap_or(0)
        > 0
    {
        zig_build.arg("-Doptimize=ReleaseFast");
    } else {
        zig_build.arg("-Doptimize=ReleaseSafe");
    }

    if let Ok(num_jobs) = std::env::var("NUM_JOBS") {
        zig_build.arg(format!("-j{num_jobs}"));
    }

    let zig_status = zig_build.status().expect("zig should be executable");

    println!("cargo::rerun-if-changed=build.rs");
    println!("cargo::rerun-if-changed=build.zig");
    println!("cargo::rerun-if-changed=lib.zig");

    println!("cargo::rustc-link-search={out_dir}/lib");
    println!("cargo::rustc-link-lib=rivet");
    println!("cargo::rustc-link-lib=softfp");

    if zig_status.success() {
        ExitCode::SUCCESS
    } else {
        ExitCode::FAILURE
    }
}
