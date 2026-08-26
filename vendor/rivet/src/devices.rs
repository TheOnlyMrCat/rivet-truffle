// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use crate::ffi::AmoKind;

pub mod virtio;

mod aclint;
pub use aclint::Aclint;
mod goldfish;
pub use goldfish::Rtc;
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
