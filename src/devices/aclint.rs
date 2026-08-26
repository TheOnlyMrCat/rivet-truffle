// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::sync::atomic::{self, AtomicU64};

use quanta::Clock;

use crate::devices::{IoWidth, MmioDevice};
use crate::emulator::Interrupt;
use crate::ffi::AmoKind;
use crate::timer::Timer;

pub struct Aclint {
    clock: Clock,
    base_now: AtomicU64,
    offset: AtomicU64,
    mtimecmp: AtomicU64,
    stimecmp: AtomicU64,

    mtimer: Timer,
    stimer: Timer,
}

impl Aclint {
    pub fn new(m_timer: Interrupt, s_timer: Interrupt) -> Self {
        let clock = Clock::new();
        let now = clock.raw();

        let mtimer = Timer::new(m_timer, clock.clone(), now);
        let stimer = Timer::new(s_timer, clock.clone(), now);

        Self {
            clock,
            base_now: AtomicU64::new(now),
            offset: AtomicU64::new(0),
            mtimecmp: AtomicU64::new(0),
            stimecmp: AtomicU64::new(0),
            mtimer,
            stimer,
        }
    }

    pub fn get_time(&self) -> u64 {
        self.clock.delta_as_nanos(
            self.base_now.load(atomic::Ordering::Relaxed),
            self.clock.raw(),
        ) + self.offset.load(atomic::Ordering::Relaxed)
    }

    pub fn set_stimecmp(&self, value: u64) {
        // This is racy for the same reason as mtimecmp
        self.stimecmp.store(value, atomic::Ordering::Relaxed);
        self.stimer.set_cmp(value);
    }
}

impl MmioDevice for Aclint {
    fn get(&self, reg: u64, width: IoWidth) -> Option<u64> {
        if width != IoWidth::Eight {
            return None;
        }

        Some(match reg {
            0x0 => self.get_time(),
            0x1000 => self.mtimecmp.load(atomic::Ordering::Relaxed),
            _ => 0,
        })
    }

    fn put(&self, reg: u64, value: u64, width: IoWidth) -> bool {
        if width != IoWidth::Eight {
            return false;
        }

        match reg {
            0x0 => {
                // FIXME: This allows for a partial update of the clock to be observed, if another
                // hart is reading mtime in parallel with this update
                self.offset.store(value, atomic::Ordering::Relaxed);
                let base_now = self.clock.raw();
                self.base_now.store(base_now, atomic::Ordering::Relaxed);
                self.mtimer.set_time(base_now, value);
            }
            0x1000 => {
                // FIXME: This is racy
                self.mtimecmp.store(value, atomic::Ordering::Relaxed);
                self.mtimer.set_cmp(value);
            }
            _ => {}
        }

        true
    }

    fn amo(&self, _reg: u64, _value: u64, _operation: AmoKind, _width: IoWidth) -> Option<u64> {
        None
    }
}
