// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::collections::HashMap;
use std::io::{Read, Seek, Write};
use std::mem::MaybeUninit;
use std::ops::Deref;
use std::path::{Path, PathBuf};
use std::ptr::NonNull;
use std::sync::atomic::AtomicU16;
use std::sync::{Arc, Weak};

use bytemuck::{AnyBitPattern, NoUninit};
use elf::ElfStream;
use elf::endian::LittleEndian;
use memmap::MmapMut;
use rangemap::RangeMap;

use crate::ForwardedPort;
use crate::devices::virtio::{VirtioBlock, VirtioMmio, VirtioNet};
use crate::devices::{Aclint, IoWidth, MmioDevice, Ns16550a, Plic, Rom, Rtc, Syscon};
use crate::ffi::{self, AmoKind, Hart, Memory};

pub struct Emulator {
    mapped_memory: MmapMut,
    symbols: HashMap<String, u64>,
    control: Arc<EmulatorControl>,
    device_map: *const MaybeUninit<DeviceMap>,
}

impl Emulator {
    pub fn new(
        blk: [Option<PathBuf>; 4],
        tcp_forwards: Vec<ForwardedPort>,
    ) -> Result<Self, EmulatorCreateError> {
        // Create an uninitialised device map, to pass to EmulatorControl.
        let device_map = Arc::new_uninit();
        let mut devices = RangeMap::<_, PtrEqArc<dyn MmioDevice>>::new();

        let mapped_memory =
            MmapMut::map_anon(2 * 1024 * 1024 * 1024).map_err(EmulatorCreateError::MmapError)?;
        let control = Arc::new(EmulatorControl::new(
            &mapped_memory,
            Arc::downgrade(&device_map),
        )?);

        let device_map = Arc::into_raw(device_map);

        let plic = Arc::new(Plic::new(
            Interrupt::from(HartInterrupt {
                hart: control.hart,
                interrupt: 0b10_11,
            }),
            Interrupt::from(HartInterrupt {
                hart: control.hart,
                interrupt: 0b10_01,
            }),
        ));
        devices.insert(
            0xc00_0000..0x1000_0000,
            (plic.clone() as Arc<dyn MmioDevice>).into(),
        );

        let serial = Arc::new(Ns16550a::new(
            plic.clone().interrupt_destination(1),
            control.clone(),
        ));
        devices.insert(
            0x1_0000_2000..0x1_0000_2100,
            (serial as Arc<dyn MmioDevice>).into(),
        );

        let virtio_net = Arc::new(VirtioMmio::new(VirtioNet::new(
            control.clone(),
            plic.clone().interrupt_destination(2),
            tcp_forwards,
        )));
        devices.insert(
            0x1000_1000..0x1000_2000,
            (virtio_net as Arc<dyn MmioDevice>).into(),
        );

        for (i, path) in blk.into_iter().enumerate() {
            if let Some(path) = path {
                let virtio_blk = Arc::new(VirtioMmio::new(
                    VirtioBlock::new(
                        &path,
                        control.clone(),
                        plic.clone().interrupt_destination(3 + i as u32),
                    )
                    .map_err(|err| EmulatorCreateError::BlkCreateError { file: path, err })?,
                ));
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

        let syscon = Arc::new(Syscon::new(control.clone()));
        devices.insert(0x1000..0x2000, (syscon as Arc<dyn MmioDevice>).into());
        let rtc = Arc::new(Rtc::new());
        devices.insert(0x10_1000..0x10_2000, (rtc as Arc<dyn MmioDevice>).into());

        let aclint = Arc::new(Aclint::new(
            Interrupt::from(HartInterrupt {
                hart: control.hart,
                interrupt: 0b1_11,
            }),
            Interrupt::from(HartInterrupt {
                hart: control.hart,
                interrupt: 0b1_01,
            }),
        ));
        devices.insert(
            0x1_0000_7000..0x1_0001_0000,
            (aclint.clone() as Arc<dyn MmioDevice>).into(),
        );

        unsafe {
            // SAFETY: device_map is a valid pointer. All pointers to this allocation are
            // raw pointers at this stage, but I'm not entirely certain how that interacts
            // with the aliasing model (either stacked or tree borrows).
            (device_map as *mut DeviceMap).write(DeviceMap { aclint, devices })
        };

        Ok(Self {
            mapped_memory,
            symbols: HashMap::new(),
            control,
            device_map,
        })
    }

    pub fn load_file(&mut self, path: impl AsRef<Path>) -> Result<(), elf::ParseError> {
        let file = std::fs::File::open(path)?;
        match ElfStream::<LittleEndian, _>::open_stream(&file) {
            Ok(elf) => self.load_elf(&file, elf),
            Err(elf::ParseError::BadMagic(_)) => self.load_bin(&file),
            Err(e) => Err(e),
        }
    }

    pub fn load_elf(
        &mut self,
        mut file: &std::fs::File,
        mut elf: ElfStream<LittleEndian, &std::fs::File>,
    ) -> Result<(), elf::ParseError> {
        if elf.ehdr.e_machine != elf::abi::EM_RISCV {
            panic!("ELF file wasn't compiled for RISC-V!")
        }

        unsafe {
            // SAFETY: The passed pointer came from hart_new(), is not null, and hasn't
            // already been deinitialised.

            ffi::hart_set_pc(self.control.hart.as_ptr(), elf.ehdr.e_entry);
        }

        for segment in elf.segments().iter() {
            if segment.p_type != elf::abi::PT_LOAD {
                continue;
            }
            if segment.p_vaddr == 0x1000 {
                // .tohost sections in test executables
                continue;
            }

            file.seek(std::io::SeekFrom::Start(segment.p_offset))?;
            file.read_exact(
                &mut self.mapped_memory[usize::try_from(segment.p_vaddr)
                    .unwrap()
                    .checked_sub(0x8000_0000)
                    .ok_or(elf::ParseError::BadOffset(segment.p_vaddr))?..]
                    [..usize::try_from(segment.p_filesz).unwrap()],
            )?;
        }

        let Some((symbols, strings)) = elf.symbol_table()? else {
            return Ok(());
        };

        for symbol in symbols.iter() {
            let Ok(name) = strings.get(symbol.st_name.try_into()?) else {
                continue;
            };
            self.symbols.insert(name.to_owned(), symbol.st_value);
        }

        Ok(())
    }

    pub fn load_bin(&mut self, mut file: &std::fs::File) -> Result<(), elf::ParseError> {
        unsafe {
            // SAFETY: The passed pointer came from hart_new(), is not null, and hasn't
            // already been deinitialised.

            ffi::hart_set_pc(self.control.hart.as_ptr(), 0x8000_0000);
        }

        let length = file.seek(std::io::SeekFrom::End(0))?;
        file.seek(std::io::SeekFrom::Start(0))?;
        file.read_exact(&mut self.mapped_memory[..length as usize])?;
        Ok(())
    }

    pub fn write_signature(&mut self, path: impl AsRef<Path>) -> Result<(), WriteSignatureError> {
        let mut file = std::fs::File::create(path).map_err(WriteSignatureError::CreateError)?;
        let begin_signature = self
            .symbols
            .get("begin_signature")
            .ok_or(WriteSignatureError::MissingSignature)?
            / u64::try_from(std::mem::size_of::<u32>()).unwrap();
        let end_signature = self
            .symbols
            .get("end_signature")
            .ok_or(WriteSignatureError::MissingSignature)?
            / u64::try_from(std::mem::size_of::<u32>()).unwrap();
        for i in begin_signature..end_signature {
            let addr = i * u64::try_from(std::mem::size_of::<u32>()).unwrap();
            writeln!(
                file,
                "{:0>8x}",
                u32::from_le_bytes(
                    self.mapped_memory[usize::try_from(addr).unwrap() - 0x8000_0000..][0..4]
                        .try_into()
                        .unwrap()
                )
            )
            .map_err(WriteSignatureError::WriteError)?;
        }

        Ok(())
    }

    pub fn run(&mut self) -> u16 {
        unsafe {
            // SAFETY: The passed pointer came from hart_new(), is not null, and hasn't
            // already been deinitialised.
            ffi::hart_run(self.control.hart.as_ptr())
        }
        self.control
            .exit_code
            .load(std::sync::atomic::Ordering::Relaxed)
    }
}

impl Drop for Emulator {
    fn drop(&mut self) {
        drop(unsafe {
            // SAFETY: self.device_map was previously created by Arc::into_raw.
            // It has also been initialised at this point, and needs to be dropped.
            Arc::from_raw(self.device_map).assume_init()
        });
    }
}

pub struct DeviceMap {
    aclint: Arc<Aclint>,
    devices: RangeMap<u64, PtrEqArc<dyn MmioDevice>>,
}

impl DeviceMap {
    pub fn load(&self, _hart: *mut Hart, addr: u64, width: IoWidth) -> Option<u64> {
        match self.devices.get_key_value(&addr) {
            Some((region, device)) => device.get(addr - region.start, width),
            None => None,
        }
    }

    pub fn store(&self, _hart: *mut Hart, addr: u64, value: u64, width: IoWidth) -> bool {
        match self.devices.get_key_value(&addr) {
            Some((region, device)) => device.put(addr - region.start, value, width),
            None => false,
        }
    }

    pub fn amo(
        &self,
        _hart: *mut Hart,
        addr: u64,
        value: u64,
        operation: AmoKind,
        width: IoWidth,
    ) -> Option<u64> {
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
    memory: NonNull<Memory>,
    hart: NonNull<Hart>,
    device_map: *const MaybeUninit<DeviceMap>,
    pub exit_code: AtomicU16,
}

// SAFETY: The Zig types Hart and Memory are equipped to handle concurrency
unsafe impl Send for EmulatorControl {}
unsafe impl Sync for EmulatorControl {}

impl EmulatorControl {
    pub fn new(
        mapped_memory: &MmapMut,
        device_map: Weak<MaybeUninit<DeviceMap>>,
    ) -> Result<Self, EmulatorCreateError> {
        let device_map = device_map.into_raw();

        let memory = NonNull::new(unsafe {
            // SAFETY: memory and run are allocated and zero-initialized, and will remain
            // so for the lifetime of the Memory.
            ffi::memory_new(
                mapped_memory.as_ptr(),
                mapped_memory.len().try_into().unwrap(),
                device_map,
            )
        })
        .ok_or(EmulatorCreateError::ZigError)?;

        let hart = NonNull::new(unsafe {
            // SAFETY: memory was initialised and is non-null
            ffi::hart_new(memory.as_ptr())
        })
        .ok_or(EmulatorCreateError::ZigError)?;

        Ok(Self {
            memory,
            hart,
            device_map,
            exit_code: AtomicU16::new(0),
        })
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
            ffi::memory_read(
                self.memory.as_ptr(),
                addr,
                buf.len() as u64,
                buf.as_mut_ptr(),
            )
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
            ffi::memory_write(self.memory.as_ptr(), addr, buf.len() as u64, buf.as_ptr())
        }
    }

    #[must_use]
    pub fn write_indexed<T: NoUninit>(&self, base_addr: u64, idx: usize, value: &T) -> bool {
        self.write(base_addr + (std::mem::size_of::<T>() * idx) as u64, value)
    }

    pub fn stop_all_harts(&self) {
        unsafe {
            // SAFETY: The passed pointer came from hart_new(), is not null, and hasn't
            // already been deinitialised.
            ffi::hart_stop(self.hart.as_ptr());
        }
    }
}

impl Drop for EmulatorControl {
    fn drop(&mut self) {
        // First, free our FFI types
        unsafe {
            // SAFETY: The passed pointer came from hart_new(), is not null, and hasn't
            // already been deinitialised.
            ffi::hart_free(self.hart.as_ptr());
        }
        unsafe {
            // SAFETY: The passed pointer came from memory_new(), is not null, and hasn't
            // already been deinitialised.
            // All Harts referring to this Memory have also been freed at this point.
            ffi::memory_free(self.memory.as_ptr());
        }

        // Finally, decrement the weak reference count on our device map
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

struct HartInterrupt {
    hart: NonNull<Hart>,
    interrupt: u32,
}

// SAFETY: The Zig type Hart is equipped to handle concurrency
unsafe impl Send for HartInterrupt {}
unsafe impl Sync for HartInterrupt {}

impl InterruptDestination for HartInterrupt {
    fn trigger(&self) {
        unsafe {
            ffi::hart_trigger(self.hart.as_ptr(), self.interrupt);
        }
    }

    fn untrigger(&self) {
        unsafe {
            ffi::hart_untrigger(self.hart.as_ptr(), self.interrupt);
        }
    }
}

#[derive(Debug)]
#[repr(transparent)]
struct PtrEqArc<T: ?Sized>(Arc<T>);

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

#[allow(clippy::enum_variant_names)]
#[derive(Debug)]
pub enum EmulatorCreateError {
    MmapError(std::io::Error),
    ZigError,
    BlkCreateError { file: PathBuf, err: std::io::Error },
}

impl std::fmt::Display for EmulatorCreateError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::MmapError(_) => write!(f, "failed to mmap memory"),
            Self::ZigError => write!(f, "failed to create hart/memory"),
            Self::BlkCreateError { file, err: _ } => {
                write!(f, "failed to create block device from {}", file.display())
            }
        }
    }
}

impl std::error::Error for EmulatorCreateError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            EmulatorCreateError::MmapError(io_error) => Some(io_error),
            EmulatorCreateError::ZigError => None,
            EmulatorCreateError::BlkCreateError { file: _, err } => Some(err),
        }
    }
}

#[derive(Debug)]
pub enum WriteSignatureError {
    CreateError(std::io::Error),
    WriteError(std::io::Error),
    MissingSignature,
}

#[cfg(test)]
mod test {
    use std::sync::{Arc, Weak};

    use memmap::MmapMut;
    use proptest::prelude::*;

    use crate::emulator::{EmulatorControl, EmulatorCreateError};

    #[test]
    pub fn read_memory() -> Result<(), Box<dyn std::error::Error>> {
        let data = [
            0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0x9, 0x8, 0x7, 0x6, 0x5, 0x4, 0x3,
            0x2, 0x1,
        ];

        let mut mapped_memory =
            MmapMut::map_anon(2 * 1024 * 1024 * 1024).map_err(EmulatorCreateError::MmapError)?;
        mapped_memory[0..data.len()].copy_from_slice(&data);

        let memory = Arc::new(EmulatorControl::new(&mapped_memory, Weak::new())?);

        let mut read_data = vec![0x0; data.len()];
        assert!(memory.read_into(0x8000_0000, &mut read_data));
        assert_eq!(data[..], read_data[..]);

        Ok(())
    }

    #[test]
    pub fn write_memory() -> Result<(), Box<dyn std::error::Error>> {
        let data = [
            0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0x9, 0x8, 0x7, 0x6, 0x5, 0x4, 0x3,
            0x2, 0x1,
        ];

        let mapped_memory =
            MmapMut::map_anon(2 * 1024 * 1024 * 1024).map_err(EmulatorCreateError::MmapError)?;
        let memory = Arc::new(EmulatorControl::new(&mapped_memory, Weak::new())?);

        assert!(memory.write_buf(0x8000_0000, &data[..]));
        assert_eq!(data[..], mapped_memory[0..data.len()]);

        Ok(())
    }

    #[test]
    pub fn read_memory_unaligned() -> Result<(), Box<dyn std::error::Error>> {
        let data = [
            0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0x9, 0x8, 0x7, 0x6, 0x5, 0x4, 0x3,
            0x2, 0x1,
        ];

        let mut mapped_memory =
            MmapMut::map_anon(2 * 1024 * 1024 * 1024).map_err(EmulatorCreateError::MmapError)?;
        mapped_memory[1..][..data.len()].copy_from_slice(&data);

        let memory = Arc::new(EmulatorControl::new(&mapped_memory, Weak::new())?);

        let mut read_data = vec![0x0; data.len()];
        assert!(memory.read_into(0x8000_0001, &mut read_data));
        assert_eq!(data[..], read_data[..]);

        Ok(())
    }

    #[test]
    pub fn write_memory_unaligned() -> Result<(), Box<dyn std::error::Error>> {
        let data = [
            0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0x9, 0x8, 0x7, 0x6, 0x5, 0x4, 0x3,
            0x2, 0x1,
        ];

        let mapped_memory =
            MmapMut::map_anon(2 * 1024 * 1024 * 1024).map_err(EmulatorCreateError::MmapError)?;
        let memory = Arc::new(EmulatorControl::new(&mapped_memory, Weak::new())?);

        assert!(memory.write_buf(0x8000_0001, &data[..]));
        assert_eq!(data[..], mapped_memory[1..][..data.len()]);

        Ok(())
    }

    proptest! {
        #[test]
        fn read_write_arbitrary(data: Box<[u8]>, addr in 0x8000_0000_u64..0x1_0000_0000_u64) {
            let mapped_memory =
                MmapMut::map_anon(2 * 1024 * 1024 * 1024).map_err(EmulatorCreateError::MmapError)?;
            let memory = Arc::new(
                EmulatorControl::new(&mapped_memory, Weak::new())?,
            );

            if addr + data.len() as u64 >= 0x1_0000_0000 {
                return Ok(());
            }

            assert!(memory.write_buf(addr, &data[..]));

            let mut read_data = vec![0x0; data.len()];
            assert!(memory.read_into(addr, &mut read_data));
            assert_eq!(data[..], read_data[..]);
        }
    }
}
