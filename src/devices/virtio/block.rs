// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::fs::OpenOptions;
use std::io::{Seek, SeekFrom};
use std::path::Path;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;

use bytemuck::{NoUninit, Pod, Zeroable};
use memmap::{MmapMut, MmapOptions};

use crate::devices::IoWidth;
use crate::emulator::{EmulatorControl, Interrupt};

use super::queue::{DescriptorRing, VirtQueue, VirtioError};
use super::{Le, VirtioDevice};

#[derive(Clone, Copy, Debug)]
#[repr(C)]
struct VirtioBlockRequestHeader {
    kind: Le<u32>,
    _a: u32,
    sector: Le<u64>,
}

unsafe impl Pod for VirtioBlockRequestHeader {}
unsafe impl Zeroable for VirtioBlockRequestHeader {}

#[derive(Clone, Copy, Debug)]
#[repr(u8)]
enum VirtioBlockRequestStatus {
    Ok = 0,
    IoError = 1,
    Unsupported = 2,
}

unsafe impl NoUninit for VirtioBlockRequestStatus {}

struct InterruptStatus {
    status: u32,
    interrupt: Interrupt,
}

pub struct VirtioBlock {
    capacity: u64,

    interrupt_status: Arc<Mutex<InterruptStatus>>,
    queue: Arc<Mutex<VirtQueue>>,
    notify_available: Arc<Condvar>,
}

impl VirtioBlock {
    const ADDR_CAPACITY_L: u64 = 0;
    const ADDR_CAPACITY_H: u64 = 4;

    const VIRTIO_F_VERSION_1: u32 = 1;
    const VIRTIO_BLK_F_FLUSH: u32 = 1 << 9;

    pub fn new(
        path: impl AsRef<Path>,
        memory: Arc<EmulatorControl>,
        interrupt: Interrupt,
    ) -> std::io::Result<Self> {
        let mut file = OpenOptions::new().read(true).write(true).open(path)?;
        let capacity = file.seek(SeekFrom::End(0))?.div_ceil(512);
        let map = Arc::new(Mutex::new(unsafe {
            // SAFETY: There aren't very many precautions I am even able to take to prevent
            // the user from modifying the mapped file while it is in use.
            MmapOptions::new().map_mut(&file)?
        }));

        let interrupt_status = Arc::new(Mutex::new(InterruptStatus {
            status: 0,
            interrupt,
        }));
        let queue = Arc::new(Mutex::new(VirtQueue::new()));
        let notify_available = Arc::new(Condvar::new());
        spawn_io_thread(
            map.clone(),
            interrupt_status.clone(),
            memory,
            queue.clone(),
            notify_available.clone(),
        );

        Ok(Self {
            capacity,
            interrupt_status,
            queue,
            notify_available,
        })
    }
}

impl VirtioDevice for VirtioBlock {
    fn device_id(&self) -> u32 {
        2
    }

    fn vendor_id(&self) -> u32 {
        0
    }

    fn supported_features(&self, word_sel: u32) -> u32 {
        match word_sel {
            0 => Self::VIRTIO_BLK_F_FLUSH,
            1 => Self::VIRTIO_F_VERSION_1,
            2.. => 0,
        }
    }

    fn set_features(&self, _word_sel: u32, _feature_bits: u32) {}

    fn reset(&self) {
        self.queue.lock().unwrap().reset();
    }

    fn interrupt_status(&self) -> u32 {
        self.interrupt_status.lock().unwrap().status
    }

    fn ack_interrupt(&self, interrupt: u32) {
        let mut interrupt_status = self.interrupt_status.lock().unwrap();
        interrupt_status.status &= !interrupt;
        interrupt_status
            .interrupt
            .set_triggered(interrupt_status.status.count_ones() != 0);
    }

    fn with_queue<R>(&self, queue_idx: usize, f: impl FnOnce(&mut VirtQueue) -> R) -> Option<R> {
        (queue_idx == 0).then_some(&self.queue).map(|queue| {
            let mut lock = queue.lock().unwrap();
            f(&mut lock)
        })
    }

    fn get_config(&self, reg: u64, width: IoWidth) -> Option<u64> {
        Some(match reg {
            Self::ADDR_CAPACITY_L if width == IoWidth::Four => self.capacity as u32 as u64,
            Self::ADDR_CAPACITY_H if width == IoWidth::Four => self.capacity >> 32,
            _ => 0,
        })
    }

    fn put_config(&self, _reg: u64, _value: u64, _width: IoWidth) -> bool {
        false
    }

    fn dispatch(&self, queue_idx: usize) {
        if queue_idx == 0 {
            // Lock the queue before notifying, to avoid race condition
            let _lock = self.queue.lock().unwrap();
            self.notify_available.notify_all();
        }
    }
}

fn spawn_io_thread(
    file: Arc<Mutex<MmapMut>>,
    interrupt_status: Arc<Mutex<InterruptStatus>>,
    memory: Arc<EmulatorControl>,
    queue: Arc<Mutex<VirtQueue>>,
    notification: Arc<Condvar>,
) {
    std::thread::Builder::new()
        .name("virtio-block-io".to_owned())
        .spawn(move || {
            let mut last_handled = None;
            loop {
                let (chain_start, descriptor_ring) = {
                    let mut timed_out = false;

                    // Get the next available descriptor chain
                    let mut lock = queue.lock().unwrap();
                    if let Some((handled_chain, bytes_written)) = last_handled.take() {
                        match lock.push_used(&memory, handled_chain, bytes_written) {
                            Ok(()) => {}
                            Err(e) => {
                                eprintln!("virtio-block-io: {e}");
                                // FIXME: Set DEVICE_NEEDS_RESET
                            }
                        }
                        match lock.notifications_needed(&memory) {
                            Ok(true) => {
                                let mut interrupt_status = interrupt_status.lock().unwrap();
                                interrupt_status.status = 1;
                                interrupt_status.interrupt.trigger();
                            }
                            Ok(false) => {}
                            Err(e) => {
                                eprintln!("virtio-block-io: {e}");
                                // FIXME: Set DEVICE_NEEDS_RESET
                            }
                        }
                    }
                    loop {
                        match lock.pop_available(&memory) {
                            Ok(Some(chain_start)) => {
                                if timed_out {
                                    eprintln!("rivet: timed out waiting for descriptor chain");
                                }
                                break (chain_start, lock.descriptor_ring());
                            }
                            Ok(None) => {
                                // If none is available immediately, wait for the next dispatch() call
                                let (new_lock, result) = notification
                                    .wait_timeout(lock, Duration::from_secs(5))
                                    .unwrap();
                                lock = new_lock;
                                timed_out = result.timed_out();
                            }
                            Err(e) => {
                                eprintln!("virtio-block-io: {e}");
                                // FIXME: Set DEVICE_NEEDS_RESET
                            }
                        }
                    }
                };

                last_handled =
                    match handle_descriptor_chain(chain_start, descriptor_ring, &file, &memory) {
                        Ok(bytes_written) => Some((chain_start, bytes_written)),
                        Err(e) => {
                            eprintln!("virtio-block-io: {e}");
                            Some((chain_start, 0))
                        }
                    }
            }
        })
        .unwrap();
}

fn handle_descriptor_chain(
    chain_start: u16,
    descriptor_ring: DescriptorRing,
    file: &Mutex<MmapMut>,
    memory: &EmulatorControl,
) -> Result<u32, VirtioError> {
    const VIRTIO_BLK_T_IN: u32 = 0;
    const VIRTIO_BLK_T_OUT: u32 = 1;
    const VIRTIO_BLK_T_FLUSH: u32 = 4;

    let chain = descriptor_ring.get_chain(memory, chain_start)?;

    let header = chain.read::<VirtioBlockRequestHeader>(memory, 0)?;
    let data_len = chain.len() - size_of::<VirtioBlockRequestHeader>() - 1;

    let mut status = VirtioBlockRequestStatus::Ok;
    let mut bytes_written = 0;
    match header.kind.to_ne() {
        VIRTIO_BLK_T_IN => {
            // Lock the file to avoid conflicting accesses from other threads
            let map = file.lock().unwrap();
            let offset = header.sector.to_ne() as usize * 512;

            // Write the data into the VM
            chain.write_buf(
                memory,
                size_of::<VirtioBlockRequestHeader>() as u32,
                &map[offset..][..data_len],
            )?;
            bytes_written += data_len;
        }
        VIRTIO_BLK_T_OUT => {
            // Lock the file to avoid conflicting accesses from other threads
            let mut map = file.lock().unwrap();
            let offset = header.sector.to_ne() as usize * 512;

            // Read the data from the VM
            chain.read_into(
                memory,
                size_of::<VirtioBlockRequestHeader>() as u32,
                &mut map[offset..][..data_len],
            )?;
        }
        VIRTIO_BLK_T_FLUSH => {
            if file.lock().unwrap().flush().is_err() {
                status = VirtioBlockRequestStatus::IoError;
            }
        }
        _ => {
            status = VirtioBlockRequestStatus::Unsupported;
        }
    }

    // Write the status to the last byte of the last descriptor
    chain.write(memory, chain.len() as u32 - 1, status)?;

    Ok(bytes_written as u32)
}
