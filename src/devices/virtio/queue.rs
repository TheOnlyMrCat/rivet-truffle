// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use bytemuck::{AnyBitPattern, NoUninit, Pod, Zeroable};

use crate::emulator::EmulatorControl;

use super::Le;

#[derive(Clone, Copy)]
#[repr(C)]
pub struct Descriptor {
    pub address: Le<u64>,
    pub length: Le<u32>,
    pub flags: Le<u16>,
    pub next: Le<u16>,
}

impl Descriptor {
    const FLAG_NEXT: u16 = 1 << 0;
    const FLAG_WRITE: u16 = 1 << 1;

    pub fn next(self) -> bool {
        self.flags.to_ne() & Self::FLAG_NEXT != 0
    }

    pub fn write(self) -> bool {
        self.flags.to_ne() & Self::FLAG_WRITE != 0
    }
}

unsafe impl Pod for Descriptor {}
unsafe impl Zeroable for Descriptor {}

#[derive(Clone, Copy)]
#[repr(C)]
struct AvailableRingHeader {
    flags: Le<u16>,
    index: Le<u16>,
}

unsafe impl Pod for AvailableRingHeader {}
unsafe impl Zeroable for AvailableRingHeader {}

#[derive(Clone, Copy)]
#[repr(C)]
struct UsedRingHeader {
    flags: Le<u16>,
    index: Le<u16>,
}

unsafe impl Pod for UsedRingHeader {}
unsafe impl Zeroable for UsedRingHeader {}

#[derive(Clone, Copy)]
#[repr(C)]
struct UsedElement {
    id: Le<u32>,
    len: Le<u32>,
}

unsafe impl Pod for UsedElement {}
unsafe impl Zeroable for UsedElement {}

#[derive(Default, Clone, Copy)]
pub struct VirtQueue {
    pub size: u16,
    pub ready: bool,
    pub current_idx: u16,
    pub descriptor_address: u64,
    pub available_address: u64,
    pub used_address: u64,
}

impl VirtQueue {
    const FLAG_NOTIFICATIONS_NOT_NEEDED: u16 = 1 << 0;

    pub const fn new() -> Self {
        Self {
            size: 0,
            ready: false,
            current_idx: 0,
            descriptor_address: 0,
            available_address: 0,
            used_address: 0,
        }
    }

    pub fn max_size(&self) -> u32 {
        256
    }

    pub fn reset(&mut self) {
        *self = Self::new();
    }

    pub fn descriptor_ring(&self) -> DescriptorRing {
        DescriptorRing {
            address: self.descriptor_address,
        }
    }

    pub fn notifications_needed(&self, ctx: &EmulatorControl) -> Result<bool, VirtioError> {
        let header = ctx
            .read::<AvailableRingHeader>(self.available_address)
            .ok_or(VirtioError::InvalidDataAddress(self.available_address))?;
        Ok(header.flags.to_ne() & Self::FLAG_NOTIFICATIONS_NOT_NEEDED == 0)
    }

    pub fn pop_available(&mut self, ctx: &EmulatorControl) -> Result<Option<u16>, VirtioError> {
        if !self.ready {
            return Ok(None);
        }

        let header = ctx
            .read::<AvailableRingHeader>(self.available_address)
            .ok_or(VirtioError::InvalidDataAddress(self.available_address))?;
        if self.current_idx != header.index.to_ne() {
            let next = self.current_idx % self.size;
            self.current_idx = self.current_idx.wrapping_add(1);

            let available_ring =
                self.available_address + std::mem::size_of::<AvailableRingHeader>() as u64;
            Some(
                ctx.read_indexed(available_ring, next as usize)
                    .ok_or(VirtioError::InvalidDataAddress(available_ring)),
            )
            .transpose()
        } else {
            Ok(None)
        }
    }

    pub fn push_used(
        &mut self,
        ctx: &EmulatorControl,
        chain: u16,
        bytes_written: u32,
    ) -> Result<(), VirtioError> {
        let mut header = ctx
            .read::<UsedRingHeader>(self.used_address)
            .ok_or(VirtioError::InvalidDataAddress(self.used_address))?;
        let index = header.index.to_ne();

        let used_element = UsedElement {
            id: Le::<u32>::from_ne(chain as u32),
            len: Le::<u32>::from_ne(bytes_written),
        };

        let used_ring = self.used_address + std::mem::size_of::<UsedRingHeader>() as u64;
        ctx.write_indexed(used_ring, (index % self.size) as usize, &used_element)
            .then_some(())
            .ok_or(VirtioError::InvalidDataAddress(used_ring))?;

        header.index = Le::<u16>::from_ne(header.index.to_ne().wrapping_add(1));
        ctx.write(self.used_address, &header)
            .then_some(())
            .ok_or(VirtioError::InvalidDataAddress(self.used_address))?;

        Ok(())
    }
}

pub struct DescriptorRing {
    address: u64,
}

impl DescriptorRing {
    pub fn get(
        &self,
        ctx: &EmulatorControl,
        descriptor_idx: u16,
    ) -> Result<Descriptor, VirtioError> {
        ctx.read_indexed(self.address, descriptor_idx as usize)
            .ok_or(VirtioError::InvalidDataAddress(self.address))
    }

    pub fn get_chain(
        &self,
        ctx: &EmulatorControl,
        chain_start: u16,
    ) -> Result<DescriptorChain, VirtioError> {
        let mut regions = Vec::new();

        let mut descriptor = self.get(ctx, chain_start)?;
        if !(0x8000_0000..0x1_0000_0000).contains(&descriptor.address.to_ne()) {
            return Err(VirtioError::InvalidDataAddress(descriptor.address.to_ne()));
        }
        regions.push(descriptor.into());

        while descriptor.next() {
            descriptor = self.get(ctx, descriptor.next.to_ne())?;
            regions.push(descriptor.into());
        }

        Ok(DescriptorChain::new(regions))
    }
}

#[derive(Clone, Copy)]
struct Region {
    address: u64,
    length: u32,
    write: bool,
}

impl From<Descriptor> for Region {
    fn from(value: Descriptor) -> Self {
        Self {
            address: value.address.to_ne(),
            length: value.length.to_ne(),
            write: value.write(),
        }
    }
}

pub struct DescriptorChain {
    regions: Vec<Region>,
    length: u32,
}

impl DescriptorChain {
    fn new(regions: Vec<Region>) -> Self {
        let length = regions
            .iter()
            .map(|&region| region.length)
            .reduce(<u32 as std::ops::Add>::add)
            .unwrap_or(0);
        Self { regions, length }
    }

    fn region_offsets(&self) -> impl Iterator<Item = (u32, Region)> {
        let mut current_offset = 0;
        self.regions.iter().copied().map(move |region| {
            let this_offset = current_offset;
            current_offset += region.length;
            (this_offset, region)
        })
    }

    fn regions_from(&self, offset: u32) -> impl Iterator<Item = Region> {
        self.region_offsets()
            .filter_map(move |(region_offset, mut region)| {
                if region_offset + region.length <= offset {
                    // Region is fully before our target offset, skip.
                    None
                } else if offset < region_offset {
                    // Region is fully after our target offset, return it.
                    Some(region)
                } else {
                    // Region contains our target offset, modify it to start at our offset.
                    region.address += u64::from(offset - region_offset);
                    Some(region)
                }
            })
    }

    pub fn len(&self) -> usize {
        self.length as usize
    }

    pub fn read<T: NoUninit + AnyBitPattern>(
        &self,
        ctx: &EmulatorControl,
        offset: u32,
    ) -> Result<T, VirtioError> {
        let mut value = T::zeroed();
        self.read_into(ctx, offset, bytemuck::bytes_of_mut(&mut value))?;
        Ok(value)
    }

    pub fn read_into(
        &self,
        ctx: &EmulatorControl,
        offset: u32,
        mut buf: &mut [u8],
    ) -> Result<(), VirtioError> {
        for region in self.regions_from(offset) {
            if buf.is_empty() {
                break;
            }

            if region.write {
                return Err(VirtioError::BadWriteRegion);
            }

            let read_length = (region.length as usize).min(buf.len());
            ctx.read_into(region.address, &mut buf[..read_length])
                .then_some(())
                .ok_or(VirtioError::InvalidDataAddress(region.address))?;
            buf = &mut buf[read_length..];
        }

        if buf.is_empty() {
            Ok(())
        } else {
            Err(VirtioError::BadWriteRegion)
        }
    }

    pub fn write<T: NoUninit>(
        &self,
        ctx: &EmulatorControl,
        offset: u32,
        value: T,
    ) -> Result<(), VirtioError> {
        self.write_buf(ctx, offset, bytemuck::bytes_of(&value))?;
        Ok(())
    }

    pub fn write_buf(
        &self,
        ctx: &EmulatorControl,
        offset: u32,
        mut buf: &[u8],
    ) -> Result<(), VirtioError> {
        for region in self.regions_from(offset) {
            if buf.is_empty() {
                break;
            }

            if !region.write {
                return Err(VirtioError::BadReadRegion);
            }

            let write_length = (region.length as usize).min(buf.len());
            ctx.write_buf(region.address, &buf[..write_length])
                .then_some(())
                .ok_or(VirtioError::InvalidDataAddress(region.address))?;
            buf = &buf[write_length..];
        }

        if buf.is_empty() {
            Ok(())
        } else {
            Err(VirtioError::TooSmall)
        }
    }
}

#[derive(Debug)]
pub enum VirtioError {
    InvalidDataAddress(u64),
    BadWriteRegion,
    BadReadRegion,
    TooSmall,
}

impl std::fmt::Display for VirtioError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidDataAddress(addr) => write!(f, "address {addr:x} is outside main memory"),
            Self::BadWriteRegion => write!(f, "tried to read from write-only region"),
            Self::BadReadRegion => write!(f, "tried to write into read-only region"),
            Self::TooSmall => write!(f, "tried to access past end of descriptor chain"),
        }
    }
}

impl std::error::Error for VirtioError {}
