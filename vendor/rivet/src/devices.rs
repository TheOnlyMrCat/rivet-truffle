// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::sync::{Arc, atomic};

use crate::emulator::EmulatorControl;
use crate::ffi::AmoKind;

pub mod virtio;

mod aclint;
pub use aclint::Aclint;
mod goldfish;
pub use goldfish::Rtc;
mod ns16550a;
pub use ns16550a::Ns16550a;
mod plic;
pub use plic::Plic;

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[repr(u8)]
pub enum IoWidth {
    One = 0,
    Two,
    Four = 2,
    Eight,
}

impl IoWidth {
    pub fn try_from_bits(bits: u8) -> Option<IoWidth> {
        match bits {
            8 => Some(IoWidth::One),
            16 => Some(IoWidth::Two),
            32 => Some(IoWidth::Four),
            64 => Some(IoWidth::Eight),
            _ => None,
        }
    }
}

pub trait MmioDevice {
    fn get(&self, reg: u64, width: IoWidth) -> Option<u64>;
    fn put(&self, reg: u64, value: u64, width: IoWidth) -> bool;
    fn amo(&self, reg: u64, value: u64, operation: AmoKind, width: IoWidth) -> Option<u64>;
}

pub struct Rom {
    data: Vec<u8>,
}

impl Rom {
    pub fn new(data: Vec<u8>) -> Self {
        Self { data }
    }
}

impl MmioDevice for Rom {
    fn get(&self, reg: u64, width: IoWidth) -> Option<u64> {
        match width {
            IoWidth::One => self.data.get(reg as usize).map(|&b| b as u64),
            IoWidth::Two => self
                .data
                .get(reg as usize..)
                .and_then(|s| s.first_chunk::<2>())
                .map(|&s| u16::from_le_bytes(s) as u64),
            IoWidth::Four => self
                .data
                .get(reg as usize..)
                .and_then(|s| s.first_chunk::<4>())
                .map(|&s| u32::from_le_bytes(s) as u64),
            IoWidth::Eight => self
                .data
                .get(reg as usize..)
                .and_then(|s| s.first_chunk::<8>())
                .map(|&s| u64::from_le_bytes(s)),
        }
    }

    fn put(&self, _reg: u64, _value: u64, _width: IoWidth) -> bool {
        false
    }

    fn amo(&self, _reg: u64, _value: u64, _operation: AmoKind, _width: IoWidth) -> Option<u64> {
        None
    }
}

pub struct Syscon {
    control: Arc<EmulatorControl>,
}

impl Syscon {
    const FAIL: u16 = 0x3333;
    const PASS: u16 = 0x5555;
    const REBOOT: u16 = 0x7777;

    pub fn new(control: Arc<EmulatorControl>) -> Self {
        Self { control }
    }
}

impl MmioDevice for Syscon {
    fn get(&self, _reg: u64, _width: IoWidth) -> Option<u64> {
        Some(0)
    }

    fn put(&self, reg: u64, value: u64, width: IoWidth) -> bool {
        if reg != 0 || width == IoWidth::One {
            return false;
        }

        match value as u16 {
            Self::FAIL => {
                self.control.stop_all_harts();
                self.control
                    .exit_code
                    .store((value >> 16) as u16, atomic::Ordering::Relaxed)
            }
            Self::PASS | Self::REBOOT => {
                self.control.stop_all_harts();
            }
            _ => {}
        }

        true
    }

    fn amo(&self, addr: u64, value: u64, operation: AmoKind, width: IoWidth) -> Option<u64> {
        match operation {
            AmoKind::Swap | AmoKind::Xor | AmoKind::Or => self.put(addr, value, width).then_some(0),
            AmoKind::And => self.put(addr, 0, width).then_some(0),
            AmoKind::Add | AmoKind::Min | AmoKind::Max | AmoKind::Minu | AmoKind::Maxu => None,
        }
    }
}
