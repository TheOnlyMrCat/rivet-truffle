// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

mod block;
pub use block::VirtioBlock;
mod net;
use bytemuck::{Pod, Zeroable};
pub use net::VirtioNet;
mod queue;

use std::sync::atomic::{self, AtomicU32};

use queue::VirtQueue;

use crate::devices::{IoWidth, MmioDevice};
use crate::ffi::AmoKind;

pub trait VirtioDevice {
    fn device_id(&self) -> u32;
    fn vendor_id(&self) -> u32;
    fn supported_features(&self, word_sel: u32) -> u32;
    fn set_features(&self, word_sel: u32, feature_bits: u32);
    fn reset(&self);
    fn interrupt_status(&self) -> u32;
    fn ack_interrupt(&self, interrupt: u32);
    fn with_queue<R>(&self, queue_idx: usize, f: impl FnOnce(&mut VirtQueue) -> R) -> Option<R>;
    fn get_config(&self, reg: u64, width: IoWidth) -> Option<u64>;
    fn put_config(&self, reg: u64, value: u64, width: IoWidth) -> bool;
    fn dispatch(&self, queue: usize);
}

pub struct VirtioMmio<T> {
    device: T,
    device_features_sel: AtomicU32,
    driver_features_sel: AtomicU32,
    queue_sel: AtomicU32,
    status: AtomicU32,
}

impl<T> VirtioMmio<T> {
    pub fn new(device: T) -> Self {
        Self {
            device,
            device_features_sel: AtomicU32::new(0),
            driver_features_sel: AtomicU32::new(0),
            queue_sel: AtomicU32::new(0),
            status: AtomicU32::new(0),
        }
    }
}

impl<T: VirtioDevice> VirtioMmio<T> {
    fn with_queue<R>(&self, f: impl FnOnce(&mut VirtQueue) -> R) -> Option<R> {
        self.device
            .with_queue(self.queue_sel.load(atomic::Ordering::Relaxed) as _, f)
    }
}

impl<T: VirtioDevice> MmioDevice for VirtioMmio<T> {
    fn get(&self, reg: u64, width: IoWidth) -> Option<u64> {
        if reg >= 0x100 {
            return self.device.get_config(reg - 0x100, width);
        }

        if reg & 0b11 != 0 || width != IoWidth::Four {
            // 4.2.2.2 Driver Requirements: MMIO Device Register Layout
            // The driver MUST only use 32 bit wide and aligned reads and writes
            // to access the control registers described in table 4.1.
            return None;
        }

        match reg {
            // Magic values
            0x00 => Some(0x74726976),
            0x04 => Some(0x2),
            0x08 => Some(self.device.device_id()),
            0x0c => Some(self.device.vendor_id()),
            // Device supported-feature bits
            0x10 => Some(
                self.device
                    .supported_features(self.device_features_sel.load(atomic::Ordering::Relaxed)),
            ),
            // Maximum virtqueue size
            0x34 => Some(self.with_queue(|queue| queue.max_size()).unwrap_or(0)),
            // Virtqueue ready bit
            0x44 => Some(self.with_queue(|queue| queue.ready).unwrap_or(false) as u32),
            // Interrupt status
            0x60 => Some(self.device.interrupt_status()),
            // Device status
            0x70 => Some(self.status.load(atomic::Ordering::Acquire)),
            // Shared memory regions
            0xb0 | 0xb4 | 0xb8 | 0xbc => Some(0),
            // Virtqueue reset
            0xc0 => Some(0),
            // Device config generation (atomicity value)
            0xfc => Some(0),

            _ => None, // Misaligned accesses and write-only registers are an access fault
        }
        .map(u64::from)
    }

    fn put(&self, reg: u64, value: u64, width: IoWidth) -> bool {
        if reg >= 0x100 {
            return self.device.put_config(reg - 0x100, value, width);
        }

        if reg & 0b11 != 0 || width != IoWidth::Four {
            // 4.2.2.2 Driver Requirements: MMIO Device Register Layout
            // The driver MUST only use 32 bit wide and aligned reads and writes
            // to access the control registers described in table 4.1.
            return false;
        }
        let value = u32::try_from(value).expect("high bits of value should not be set");

        match reg {
            // Device features word selection
            0x14 => self
                .device_features_sel
                .store(value, atomic::Ordering::Relaxed),
            // Driver features activation
            0x20 => self.device.set_features(
                self.driver_features_sel.load(atomic::Ordering::Relaxed),
                value,
            ),
            // Driver features word selection
            0x24 => self
                .driver_features_sel
                .store(value, atomic::Ordering::Relaxed),
            // Virtqueue index
            0x30 => self.queue_sel.store(value, atomic::Ordering::Relaxed),
            // Virtqueue size
            0x38 => self
                .with_queue(|queue| {
                    if value <= queue.max_size() {
                        queue.size = value as u16
                    }
                })
                .unwrap_or(()),
            // Virtqueue ready
            0x44 => self
                .with_queue(|queue| queue.ready = value == 1)
                .unwrap_or(()),
            // Queue notifier
            0x50 => self.device.dispatch(value as usize),
            // Interrupt acknowledge
            0x64 => {
                self.device.ack_interrupt(value);
            }
            // Device status
            0x70 => {
                if value == 0 {
                    // Reset device
                    self.device.reset();
                    self.device_features_sel.store(0, atomic::Ordering::Relaxed);
                    self.driver_features_sel.store(0, atomic::Ordering::Relaxed);
                    self.queue_sel.store(0, atomic::Ordering::Relaxed);
                }
                self.status.store(value, atomic::Ordering::Release);
            }
            // Virtqueue Descriptor Area physical address
            0x80 => self
                .with_queue(|queue| set_le_low(&mut queue.descriptor_address, value))
                .unwrap_or(()),
            0x84 => self
                .with_queue(|queue| set_le_high(&mut queue.descriptor_address, value))
                .unwrap_or(()),
            // Virtqueue Driver Area physical address
            0x90 => self
                .with_queue(|queue| set_le_low(&mut queue.available_address, value))
                .unwrap_or(()),
            0x94 => self
                .with_queue(|queue| set_le_high(&mut queue.available_address, value))
                .unwrap_or(()),
            // Virtqueue Device Area physical address
            0xa0 => self
                .with_queue(|queue| set_le_low(&mut queue.used_address, value))
                .unwrap_or(()),
            0xa4 => self
                .with_queue(|queue| set_le_high(&mut queue.used_address, value))
                .unwrap_or(()),
            // Virtqueue reset bit
            0xc0 => {}

            _ => return false, // Misaligned accesses and read-only registers are an access fault
        }

        true
    }

    fn amo(&self, _reg: u64, _value: u64, _operation: AmoKind, _width: IoWidth) -> Option<u64> {
        None
    }
}

#[derive(Clone, Copy, Debug)]
#[repr(transparent)]
pub struct Le<T>(T);

unsafe impl<T: Pod> Pod for Le<T> {}
unsafe impl<T: Zeroable> Zeroable for Le<T> {}

macro_rules! impl_le {
    ($t:ty) => {
        #[allow(dead_code)]
        impl Le<$t> {
            pub fn from_ne(value: $t) -> Self {
                Self(<$t>::from_ne_bytes(value.to_le_bytes()))
            }

            pub fn to_ne(self) -> $t {
                <$t>::from_le_bytes(self.0.to_ne_bytes())
            }
        }
    };

    ($t:ty, $($ts:ty),* $(,)?) => {
        impl_le!($t);
        impl_le!($($ts),*);
    }
}

impl_le!(u16, u32, u64);

fn set_le_low(int: &mut u64, low: u32) {
    let mut bytes = int.to_le_bytes();
    bytes[..4].copy_from_slice(&low.to_le_bytes());
    *int = u64::from_le_bytes(bytes)
}

fn set_le_high(int: &mut u64, high: u32) {
    let mut bytes = int.to_le_bytes();
    bytes[4..8].copy_from_slice(&high.to_le_bytes());
    *int = u64::from_le_bytes(bytes)
}
