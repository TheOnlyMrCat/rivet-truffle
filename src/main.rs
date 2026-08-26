// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0
#![deny(unsafe_op_in_unsafe_fn)]

mod devices;
mod emulator;
mod ffi;
mod timer;

use std::io::ErrorKind;
use std::path::PathBuf;
use std::process::ExitCode;
use std::str::FromStr;

use emulator::{Emulator, EmulatorCreateError, WriteSignatureError};

/// Toy RV64GC emulator
struct RivetCli {
    /// Disk image file to use read/write as a virtual hard drive
    blk: [Option<PathBuf>; 4],

    /// Write a riscof test signature after execution finishes
    signature_out: Option<PathBuf>,

    /// TCP ports to forward between the host and guest networks
    tcp_forwards: Vec<ForwardedPort>,

    /// The ELF file to emulate
    file: PathBuf,
}

#[derive(Clone, Copy)]
struct ForwardedPort {
    host_port: u16,
    guest_port: u16,
}

impl FromStr for ForwardedPort {
    type Err = std::num::ParseIntError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let (host_port, guest_port) = s
            .split_once(':')
            .map(|(host_port, guest_port)| (host_port, Some(guest_port)))
            .unwrap_or((s, None));
        let host_port = host_port.parse()?;
        let guest_port = guest_port.map(str::parse).transpose()?.unwrap_or(host_port);
        Ok(ForwardedPort {
            host_port,
            guest_port,
        })
    }
}

fn parse_args() -> Result<RivetCli, lexopt::Error> {
    use lexopt::prelude::*;

    let mut blk = [const { None }; 4];
    let mut blk_index = 0;
    let mut signature_out = None;
    let mut tcp_forwards = vec![];
    let mut file = None;

    let mut parser = lexopt::Parser::from_env();
    while let Some(arg) = parser.next()? {
        match arg {
            Long("blk") => {
                if blk_index == blk.len() {
                    return Err("maximum of 4 blk devices".into());
                }
                blk[blk_index] = Some(parser.value()?.parse()?);
                blk_index += 1;
            }
            Long("signature_out") | Long("signature-out") => {
                signature_out = Some(parser.value()?.parse()?);
            }
            Long("forward_tcp") | Long("forward-tcp") => {
                tcp_forwards.push(parser.value()?.parse()?);
            }
            Value(f) if file.is_none() => file = Some(f.parse()?),
            _ => return Err(arg.unexpected()),
        }
    }

    Ok(RivetCli {
        blk,
        signature_out,
        tcp_forwards,
        file: file.ok_or("missing argument FILE")?,
    })
}

fn main() -> ExitCode {
    // Constants from sysexits.h
    const EX_USAGE: u8 = 64;
    const EX_DATAERR: u8 = 65;
    const EX_NOINPUT: u8 = 66;
    const EX_SOFTWARE: u8 = 70;
    const EX_OSERR: u8 = 71;
    const EX_CANTCREAT: u8 = 73;
    const EX_IOERR: u8 = 74;

    let opts = match parse_args() {
        Ok(opts) => opts,
        Err(e) => {
            eprintln!("rivet: {e}");
            return ExitCode::from(EX_USAGE);
        }
    };

    let mut emulator = match Emulator::new(opts.blk.clone(), opts.tcp_forwards) {
        Ok(emulator) => emulator,
        Err(EmulatorCreateError::MmapError(e)) => {
            eprintln!("rivet: failed to create emulator: {e}");
            return ExitCode::from(EX_OSERR);
        }
        Err(EmulatorCreateError::ZigError) => {
            eprintln!("rivet: failed to create emulator");
            return ExitCode::from(EX_SOFTWARE);
        }
        Err(EmulatorCreateError::BlkCreateError { file, err }) => {
            eprintln!("rivet: couldn't open {} for reading: {err}", file.display());
            return ExitCode::from(EX_NOINPUT);
        }
    };

    match emulator.load_file(&opts.file) {
        Ok(()) => {}
        Err(elf::ParseError::IOError(e)) => match e.kind() {
            ErrorKind::NotFound | ErrorKind::PermissionDenied => {
                eprintln!(
                    "rivet: couldn't open {} for reading: {e}",
                    opts.file.display()
                );
                return ExitCode::from(EX_NOINPUT);
            }
            _ => {
                eprintln!("rivet: couldn't read {}: {e}", opts.file.display());
                return ExitCode::from(EX_IOERR);
            }
        },
        Err(e) => {
            eprintln!("rivet: couldn't parse {}: {e}", opts.file.display());
            return ExitCode::from(EX_DATAERR);
        }
    }

    let exit_code = emulator.run();

    if let Some(signature_out) = opts.signature_out {
        match emulator.write_signature(&signature_out) {
            Ok(()) => {}
            Err(WriteSignatureError::CreateError(e)) => {
                eprintln!("rivet: couldn't create {}: {e}", signature_out.display());
                return ExitCode::from(EX_CANTCREAT);
            }
            Err(WriteSignatureError::WriteError(e)) => {
                eprintln!("rivet: couldn't write {}: {e}", signature_out.display());
                return ExitCode::from(EX_IOERR);
            }
            Err(WriteSignatureError::MissingSignature) => {
                eprintln!("rivet: {} has no signature region", opts.file.display());
                return ExitCode::from(EX_DATAERR);
            }
        }
    }

    ExitCode::from(exit_code as u8)
}
