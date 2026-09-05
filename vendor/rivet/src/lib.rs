#![deny(unsafe_op_in_unsafe_fn)]

use std::mem::MaybeUninit;
use std::path::PathBuf;
use std::sync::Arc;

use rangemap::RangeMap;

use crate::devices::virtio::{VirtioBlock, VirtioMmio, VirtioNet};
use crate::devices::{Aclint, MmioDevice, Plic, Rom, Rtc};
use crate::emulator::{DeviceMap, EmulatorControl, HartInterrupt, Interrupt, PtrEqArc};
use crate::ffi::{Hart, Memory};

mod devices;
mod emulator;
mod ffi;
mod timer;

#[unsafe(no_mangle)]
unsafe extern "C" fn rust_create_devices(
    memory: Memory,
    _hart_count: u64,
    memory_read: unsafe extern "C" fn(memory: Memory, addr: u64, len: u64, buffer: *mut u8) -> bool,
    memory_write: unsafe extern "C" fn(
        memory: Memory,
        addr: u64,
        len: u64,
        buffer: *const u8,
    ) -> bool,
    hart_trigger: unsafe extern "C" fn(hart: Hart, interrupt: u32),
    hart_untrigger: unsafe extern "C" fn(hart: Hart, interrupt: u32),
) -> *const MaybeUninit<DeviceMap> {
    let device_map = Arc::new_uninit();
    let mut devices = RangeMap::<_, PtrEqArc<dyn MmioDevice>>::new();

    unsafe {
        memory_read(memory, 0, 0, &mut 0);
    }

    let control = Arc::new(EmulatorControl::new(
        memory,
        Arc::downgrade(&device_map),
        memory_read,
        memory_write,
    ));

    let device_map = Arc::into_raw(device_map);

    let plic = Arc::new(Plic::new(
        Interrupt::from(HartInterrupt {
            memory,
            interrupt: 0b10_11,
            hart_trigger,
            hart_untrigger,
        }),
        Interrupt::from(HartInterrupt {
            memory,
            interrupt: 0b10_01,
            hart_trigger,
            hart_untrigger,
        }),
    ));
    devices.insert(
        0xc00_0000..0x1000_0000,
        (plic.clone() as Arc<dyn MmioDevice>).into(),
    );

    let virtio_net = Arc::new(VirtioMmio::new(VirtioNet::new(
        control.clone(),
        plic.clone().interrupt_destination(2),
        vec![],
    )));
    devices.insert(
        0x1000_1000..0x1000_2000,
        (virtio_net as Arc<dyn MmioDevice>).into(),
    );

    // FIXME: This list should instead be passed as an argument
    let blk_images = vec![Some(PathBuf::from("dqib/image.img")), None, None, None];

    for (i, path) in blk_images.into_iter().enumerate() {
        if let Some(path) = path {
            let Ok(virtio_blk) = VirtioBlock::new(
                &path,
                control.clone(),
                plic.clone().interrupt_destination(3 + i as u32),
            )
            .map(VirtioMmio::new)
            .map(Arc::new) else {
                return std::ptr::null();
            };
            devices.insert(
                0x1000_2000 + 0x1000 * i as u64..0x1000_3000 + 0x1000 * i as u64,
                (virtio_blk as Arc<dyn MmioDevice>).into(),
            );
        } else {
            let rom = Arc::new(Rom::new(vec![0; 0x1000]));
            devices.insert(
                0x1000_2000 + 0x1000 * i as u64..0x1000_3000 + 0x1000 * i as u64,
                (rom as Arc<dyn MmioDevice>).into(),
            );
        }
    }

    let rtc = Arc::new(Rtc::new());
    devices.insert(0x10_1000..0x10_2000, (rtc as Arc<dyn MmioDevice>).into());

    let aclint = Arc::new(Aclint::new(
        Interrupt::from(HartInterrupt {
            memory,
            interrupt: 0b1_11,
            hart_trigger,
            hart_untrigger,
        }),
        Interrupt::from(HartInterrupt {
            memory,
            interrupt: 0b1_01,
            hart_trigger,
            hart_untrigger,
        }),
    ));
    devices.insert(
        0x20_0000..0x21_0000,
        (aclint.clone() as Arc<dyn MmioDevice>).into(),
    );

    unsafe {
        // SAFETY: device_map is a valid pointer. All pointers to this allocation are
        // raw pointers at this stage, but I'm not entirely certain how that interacts
        // with the aliasing model (either stacked or tree borrows).
        (device_map as *mut DeviceMap).write(DeviceMap { aclint, devices })
    };

    device_map
}
