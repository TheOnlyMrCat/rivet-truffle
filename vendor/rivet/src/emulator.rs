// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::mem::MaybeUninit;
use std::ops::Deref;
use std::sync::{Arc, Weak};

use bytemuck::{AnyBitPattern, NoUninit};
use rangemap::RangeMap;

use crate::devices::{Aclint, IoWidth, MmioDevice};
use crate::ffi::{AmoKind, Memory};

pub struct DeviceMap {
    pub aclint: Arc<Aclint>,
    pub serial_interrupt: Interrupt,
    pub devices: RangeMap<u64, PtrEqArc<dyn MmioDevice>>,
}

impl DeviceMap {
    pub fn load(&self, addr: u64, width: IoWidth) -> Option<u64> {
        match self.devices.get_key_value(&addr) {
            Some((region, device)) => device.get(addr - region.start, width),
            None => None,
        }
    }

    pub fn store(&self, addr: u64, value: u64, width: IoWidth) -> bool {
        match self.devices.get_key_value(&addr) {
            Some((region, device)) => device.put(addr - region.start, value, width),
            None => false,
        }
    }

    pub fn amo(&self, addr: u64, value: u64, operation: AmoKind, width: IoWidth) -> Option<u64> {
        match self.devices.get_key_value(&addr) {
            Some((region, device)) => device.amo(addr - region.start, value, operation, width),
            None => None,
        }
    }

    pub fn get_time(&self) -> u64 {
        self.aclint.get_time()
    }

    pub fn set_stimecmp(&self, value: u64) {
        self.aclint.set_stimecmp(value)
    }
}

pub struct EmulatorControl {
    pub memory: Memory,
    pub device_map: *const MaybeUninit<DeviceMap>,
    memory_read: unsafe extern "C" fn(memory: Memory, addr: u64, len: u64, buffer: *mut u8) -> bool,
    memory_write:
        unsafe extern "C" fn(memory: Memory, addr: u64, len: u64, buffer: *const u8) -> bool,
}

// SAFETY: The Zig types Hart and Memory are equipped to handle concurrency
unsafe impl Send for EmulatorControl {}
unsafe impl Sync for EmulatorControl {}

impl EmulatorControl {
    pub fn new(
        memory: Memory,
        device_map: Weak<MaybeUninit<DeviceMap>>,
        memory_read: unsafe extern "C" fn(
            memory: Memory,
            addr: u64,
            len: u64,
            buffer: *mut u8,
        ) -> bool,
        memory_write: unsafe extern "C" fn(
            memory: Memory,
            addr: u64,
            len: u64,
            buffer: *const u8,
        ) -> bool,
    ) -> Self {
        let device_map = device_map.into_raw();

        Self {
            memory,
            device_map,
            memory_read,
            memory_write,
        }
    }

    pub fn read<T: NoUninit + AnyBitPattern>(&self, addr: u64) -> Option<T> {
        let mut value = T::zeroed();
        if self.read_into(addr, bytemuck::bytes_of_mut(&mut value)) {
            Some(value)
        } else {
            None
        }
    }

    #[must_use]
    pub fn read_into(&self, addr: u64, buf: &mut [u8]) -> bool {
        unsafe {
            // SAFETY: self.memory is a valid Memory, and value is a valid place to write the given
            // number of bytes
            (self.memory_read)(self.memory, addr, buf.len() as u64, buf.as_mut_ptr())
        }
    }

    pub fn read_indexed<T: NoUninit + AnyBitPattern>(
        &self,
        base_addr: u64,
        idx: usize,
    ) -> Option<T> {
        self.read(base_addr + (std::mem::size_of::<T>() * idx) as u64)
    }

    #[must_use]
    pub fn write<T: NoUninit>(&self, addr: u64, value: &T) -> bool {
        self.write_buf(addr, bytemuck::bytes_of(value))
    }

    #[must_use]
    pub fn write_buf(&self, addr: u64, buf: &[u8]) -> bool {
        unsafe {
            // SAFETY: self.memory is a valid Memory, and buf is a valid place to read the given
            // number of bytes
            (self.memory_write)(self.memory, addr, buf.len() as u64, buf.as_ptr())
        }
    }

    #[must_use]
    pub fn write_indexed<T: NoUninit>(&self, base_addr: u64, idx: usize, value: &T) -> bool {
        self.write(base_addr + (std::mem::size_of::<T>() * idx) as u64, value)
    }
}

impl Drop for EmulatorControl {
    fn drop(&mut self) {
        // Decrement the weak reference count on our device map
        drop(unsafe {
            // SAFETY: self.device_map came from calling into_raw on a weak reference.
            // The Memory referring to this weak reference has been freed at this point.
            Weak::from_raw(self.device_map)
        })
    }
}

#[derive(Clone)]
pub struct Interrupt {
    destination: Arc<dyn InterruptDestination>,
}

impl Interrupt {
    pub fn trigger(&self) {
        self.destination.trigger();
    }

    pub fn untrigger(&self) {
        self.destination.untrigger();
    }

    pub fn set_triggered(&self, triggered: bool) {
        if triggered {
            self.trigger();
        } else {
            self.untrigger();
        }
    }
}

impl<T: InterruptDestination + 'static> From<T> for Interrupt {
    fn from(value: T) -> Self {
        Self {
            destination: Arc::new(value),
        }
    }
}

pub trait InterruptDestination: Send + Sync {
    fn trigger(&self);
    fn untrigger(&self);
}

pub struct HartInterrupt {
    pub memory: Memory,
    pub interrupt: u32,
    pub hart_trigger: unsafe extern "C" fn(memory: Memory, interrupt: u32),
    pub hart_untrigger: unsafe extern "C" fn(memory: Memory, interrupt: u32),
}

// SAFETY: The Zig type Hart is equipped to handle concurrency
unsafe impl Send for HartInterrupt {}
unsafe impl Sync for HartInterrupt {}

impl InterruptDestination for HartInterrupt {
    fn trigger(&self) {
        unsafe {
            (self.hart_trigger)(self.memory, self.interrupt);
        }
    }

    fn untrigger(&self) {
        unsafe {
            (self.hart_untrigger)(self.memory, self.interrupt);
        }
    }
}

#[derive(Debug)]
#[repr(transparent)]
pub struct PtrEqArc<T: ?Sized>(Arc<T>);

impl<T: ?Sized> From<Arc<T>> for PtrEqArc<T> {
    fn from(value: Arc<T>) -> Self {
        Self(value)
    }
}

impl<T: ?Sized> Clone for PtrEqArc<T> {
    fn clone(&self) -> Self {
        Self(Arc::clone(&self.0))
    }
}

impl<T: ?Sized> Deref for PtrEqArc<T> {
    type Target = T;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl<T: ?Sized> PartialEq for PtrEqArc<T> {
    fn eq(&self, other: &Self) -> bool {
        Arc::ptr_eq(&self.0, &other.0)
    }
}
