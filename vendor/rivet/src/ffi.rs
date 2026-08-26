// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::mem::MaybeUninit;
use std::sync::{Arc, Weak};

use crate::devices::IoWidth;
use crate::emulator::DeviceMap;

pub type Hart = i32;
pub type Memory = i32;

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

    match run.load(addr, width) {
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

    if !run.store(addr, value, width) {
        eprintln!("store: bad write: {bits} bits @ 0x{addr:x}");
        false
    } else {
        true
    }
}

#[unsafe(no_mangle)]
unsafe extern "C" fn rust_amo(
    ctx: *const MaybeUninit<DeviceMap>,
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
