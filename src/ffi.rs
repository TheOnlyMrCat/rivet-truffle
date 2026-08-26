// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::alloc::Layout;
use std::mem::MaybeUninit;
use std::sync::atomic::{self, AtomicI8, AtomicU8};
use std::sync::{Arc, Weak};

use crate::devices::IoWidth;
use crate::emulator::DeviceMap;

pub type Hart = std::ffi::c_void;
pub type Memory = std::ffi::c_void;

// Corresponds with AmoKind in Memory.zig
#[derive(Clone, Copy, Debug)]
#[repr(u8)]
#[allow(dead_code)]
pub enum AmoKind {
    Swap,
    Add,
    Xor,
    And,
    Or,
    Min,
    Max,
    Minu,
    Maxu,
}

impl AmoKind {
    pub fn operate_u32_unordered(self, lhs: u32, rhs: u32) -> u32 {
        match self {
            AmoKind::Swap => rhs,
            AmoKind::Add => lhs.wrapping_add(rhs),
            AmoKind::Xor => lhs ^ rhs,
            AmoKind::And => lhs & rhs,
            AmoKind::Or => lhs | rhs,
            AmoKind::Min => lhs.cast_signed().min(rhs.cast_signed()).cast_unsigned(),
            AmoKind::Max => lhs.cast_signed().max(rhs.cast_signed()).cast_unsigned(),
            AmoKind::Minu => lhs.min(rhs),
            AmoKind::Maxu => lhs.max(rhs),
        }
    }

    pub fn operate_u8(self, atomic: &AtomicU8, operand: u8, order: atomic::Ordering) -> u8 {
        match self {
            AmoKind::Swap => atomic.swap(operand, order),
            AmoKind::Add => atomic.fetch_add(operand, order),
            AmoKind::Xor => atomic.fetch_xor(operand, order),
            AmoKind::And => atomic.fetch_and(operand, order),
            AmoKind::Or => atomic.fetch_or(operand, order),
            AmoKind::Min => unsafe { AtomicI8::from_ptr(atomic.as_ptr() as *mut i8) }
                .fetch_min(operand.cast_signed(), order)
                .cast_unsigned(),
            AmoKind::Max => unsafe { AtomicI8::from_ptr(atomic.as_ptr() as *mut i8) }
                .fetch_max(operand.cast_signed(), order)
                .cast_unsigned(),
            AmoKind::Minu => atomic.fetch_min(operand, order),
            AmoKind::Maxu => atomic.fetch_max(operand, order),
        }
    }

    pub fn operate_u8_unordered(self, lhs: u8, rhs: u8) -> u8 {
        match self {
            AmoKind::Swap => rhs,
            AmoKind::Add => lhs.wrapping_add(rhs),
            AmoKind::Xor => lhs ^ rhs,
            AmoKind::And => lhs & rhs,
            AmoKind::Or => lhs | rhs,
            AmoKind::Min => lhs.cast_signed().min(rhs.cast_signed()).cast_unsigned(),
            AmoKind::Max => lhs.cast_signed().max(rhs.cast_signed()).cast_unsigned(),
            AmoKind::Minu => lhs.min(rhs),
            AmoKind::Maxu => lhs.max(rhs),
        }
    }
}

unsafe extern "C" {
    #[allow(improper_ctypes)]
    pub fn memory_new(
        memory: *const u8,
        memory_length: u64,
        ctx: *const MaybeUninit<DeviceMap>,
    ) -> *mut Memory;
    pub fn memory_free(memory: *mut Memory);
    pub fn memory_read(memory: *mut Memory, addr: u64, len: u64, buffer: *mut u8) -> bool;
    pub fn memory_write(memory: *mut Memory, addr: u64, len: u64, buffer: *const u8) -> bool;
    pub fn hart_new(memory: *mut Memory) -> *mut Hart;
    pub fn hart_free(hart: *mut Hart);
    pub fn hart_set_pc(hart: *mut Hart, pc: u64);
    pub fn hart_run(hart: *mut Hart);
    pub fn hart_trigger(hart: *mut Hart, interrupt: u32);
    pub fn hart_untrigger(hart: *mut Hart, interrupt: u32);
    pub fn hart_stop(hart: *mut Hart);
}

#[unsafe(no_mangle)]
extern "C" fn rust_alloc(len: usize, ptr_align: u8) -> *mut u8 {
    if len == 0 {
        return std::ptr::null_mut();
    }
    let Ok(layout) = Layout::from_size_align(len, 1 << ptr_align) else {
        return std::ptr::null_mut();
    };
    unsafe {
        // SAFETY: Layout has non-zero size
        std::alloc::alloc(layout)
    }
}

#[unsafe(no_mangle)]
extern "C" fn rust_realloc(ptr: *mut u8, len: usize, ptr_align: u8, new_len: usize) -> *mut u8 {
    if len == 0 {
        return std::ptr::null_mut();
    }
    let Ok(layout) = Layout::from_size_align(len, 1 << ptr_align) else {
        return std::ptr::null_mut();
    };
    unsafe {
        // SAFETY: Layout has non-zero size
        std::alloc::realloc(ptr, layout, new_len)
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn rust_dealloc(ptr: *mut u8, len: usize, ptr_align: u8) {
    let Ok(layout) = Layout::from_size_align(len, 1 << ptr_align) else {
        return;
    };
    unsafe {
        // SAFETY: Caller should verify: ptr, len, ptr_align are from a previous
        // call to rust_alloc
        std::alloc::dealloc(ptr, layout);
    }
}

unsafe fn upgrade_ctx(ctx: *const MaybeUninit<DeviceMap>) -> Option<Arc<DeviceMap>> {
    // Extract the Weak object from ctx
    let ctx = unsafe {
        // SAFETY: ctx originally came from Weak::into_raw()
        Weak::from_raw(ctx)
    };

    // Create a new Arc (strong pointer) from ctx
    let run = ctx.upgrade()?;

    // Convert ctx back into a raw pointer, to avoid decrementing the weak reference count.
    let _ = ctx.into_raw();

    Some(unsafe {
        // SAFETY: run has been initialised.
        run.assume_init()
    })
}

#[unsafe(no_mangle)]
unsafe extern "C" fn rust_load(
    ctx: *const MaybeUninit<DeviceMap>,
    hart: *mut Hart,
    addr: u64,
    value: *mut u64,
    bits: u8,
) -> bool {
    let Some(width) = IoWidth::try_from_bits(bits) else {
        eprintln!("load: invalid width: {bits} bits @ 0x{addr:x}");
        return false;
    };

    let Some(run) = (unsafe { upgrade_ctx(ctx) }) else {
        return false;
    };

    match run.load(hart, addr, width) {
        Some(v) => {
            unsafe {
                // SAFETY: Caller should verify: value is a valid write target
                std::ptr::write(value, v)
            };
            true
        }
        None => {
            eprintln!("load: bad read: {bits} bits @ 0x{addr:x}");
            false
        }
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn rust_store(
    ctx: *const MaybeUninit<DeviceMap>,
    hart: *mut Hart,
    addr: u64,
    value: u64,
    bits: u8,
) -> bool {
    let Some(width) = IoWidth::try_from_bits(bits) else {
        eprintln!("store: invalid width: {bits} bits @ 0x{addr:x}");
        return false;
    };

    let Some(run) = (unsafe { upgrade_ctx(ctx) }) else {
        return false;
    };

    if !run.store(hart, addr, value, width) {
        eprintln!("store: bad write: {bits} bits @ 0x{addr:x}");
        false
    } else {
        true
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn rust_amo(
    ctx: *const MaybeUninit<DeviceMap>,
    hart: *mut Hart,
    addr: u64,
    value: *mut u64,
    operation: AmoKind,
    bits: u8,
) -> bool {
    let Some(width) = IoWidth::try_from_bits(bits) else {
        eprintln!("amo: invalid width: {bits} bits @ 0x{addr:x}");
        return false;
    };

    let Some(run) = (unsafe { upgrade_ctx(ctx) }) else {
        return false;
    };

    match run.amo(
        hart,
        addr,
        unsafe {
            // SAFETY: Caller should verify: value is a valid read target
            std::ptr::read(value)
        },
        operation,
        width,
    ) {
        Some(v) => {
            unsafe {
                // SAFETY: Caller should verify: value is a valid write target
                std::ptr::write(value, v)
            };
            true
        }
        None => {
            eprintln!("amo: bad operation: {bits} bits @ 0x{addr:x}");
            false
        }
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn rust_get_time(ctx: *const MaybeUninit<DeviceMap>) -> u64 {
    let Some(run) = (unsafe { upgrade_ctx(ctx) }) else {
        return 0;
    };

    run.get_time()
}

#[unsafe(no_mangle)]
unsafe extern "C" fn rust_set_stimecmp(
    ctx: *const MaybeUninit<DeviceMap>,
    _hart: *mut Hart,
    value: u64,
) {
    let Some(run) = (unsafe { upgrade_ctx(ctx) }) else {
        return;
    };

    run.set_stimecmp(value)
}
