// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::sync::atomic::{self, AtomicU64};
use std::time::SystemTime;

use crate::devices::{IoWidth, MmioDevice};
use crate::ffi::AmoKind;

pub struct Rtc {
    last_time: AtomicU64,
}

impl Rtc {
    pub fn new() -> Self {
        Self {
            last_time: AtomicU64::new(0),
        }
    }
}

impl MmioDevice for Rtc {
    fn get(&self, reg: u64, width: IoWidth) -> Option<u64> {
        if width != IoWidth::Four {
            return None;
        }

        Some(match reg {
            0x0 => {
                // Get current time, then return low-order 32 bits
                let epoch_time_s = SystemTime::now()
                    .duration_since(SystemTime::UNIX_EPOCH)
                    .unwrap()
                    .as_secs();
                // RTC time has granularity of 1 second, but precision in nanoseconds.
                let epoch_time_ns = epoch_time_s * 1_000_000_000;
                self.last_time
                    .store(epoch_time_ns, atomic::Ordering::Relaxed);
                epoch_time_ns & (u32::MAX as u64)
            }
            0x4 => {
                // Get high-order 32 bits
                let epoch_time_ns = self.last_time.load(atomic::Ordering::Relaxed);
                epoch_time_ns >> 32
            }
            _ => 0,
        })
    }

    fn put(&self, _reg: u64, _value: u64, width: IoWidth) -> bool {
        if width != IoWidth::Four {
            return false;
        }

        true
    }

    fn amo(&self, reg: u64, _value: u64, operation: AmoKind, width: IoWidth) -> Option<u64> {
        if width != IoWidth::Four {
            return None;
        }

        match operation {
            AmoKind::Swap | AmoKind::Xor | AmoKind::And | AmoKind::Or => self.get(reg, width),
            AmoKind::Add | AmoKind::Min | AmoKind::Max | AmoKind::Minu | AmoKind::Maxu => None,
        }
    }
}
