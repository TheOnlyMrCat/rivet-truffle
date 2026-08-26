// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::cell::RefCell;
use std::net::{Ipv4Addr, Ipv6Addr};
use std::os::unix::io::RawFd;
use std::rc::Rc;
use std::sync::mpsc::{self, Sender};
use std::sync::{Arc, Condvar, Mutex};

use bytemuck::{Pod, Zeroable};
use libslirp::Handler;
use quanta::Clock;
use shared_slab::Slab;

use crate::devices::IoWidth;
use crate::emulator::{EmulatorControl, Interrupt, InterruptDestination};
use crate::timer::Timer;

use super::queue::{DescriptorRing, VirtQueue, VirtioError};
use super::{Le, VirtioDevice};

#[derive(Clone, Copy)]
#[repr(C)]
struct VirtioNetHeader {
    flags: u8,
    gso_type: u8,
    hdr_len: Le<u16>,
    gso_size: Le<u16>,
    csum_start: Le<u16>,
    csum_offset: Le<u16>,
    num_buffers: Le<u16>,
}

unsafe impl Pod for VirtioNetHeader {}
unsafe impl Zeroable for VirtioNetHeader {}

#[derive(Clone)]
struct EventFdSender<T> {
    fd: RawFd,
    sender: Sender<T>,
}

impl<T> EventFdSender<T> {
    fn send(&self, value: T) -> Result<(), mpsc::SendError<T>> {
        self.sender.send(value)?;
        unsafe {
            // SAFETY: self.fd is a valid file descriptor
            libc::eventfd_write(self.fd, 1);
        }
        Ok(())
    }
}

#[derive(Clone)]
enum SlirpMessage {
    TimerDone(usize),
    GuestTransmit(Box<[u8]>),
}

#[derive(Clone, Copy)]
pub struct ForwardedPort {
    host_port: u16,
    guest_port: u16,
}

struct InterruptStatus {
    status: u32,
    interrupt: Interrupt,
}

pub struct VirtioNet {
    interrupt_status: Arc<Mutex<InterruptStatus>>,
    receive: Arc<Mutex<VirtQueue>>,
    transmit: Arc<Mutex<VirtQueue>>,
    transmit_notify: Arc<Condvar>,
}

impl VirtioNet {
    const MAC_ADDRESS_A: u64 = 0;
    const MAC_ADDRESS_B: u64 = 1;
    const MAC_ADDRESS_C: u64 = 2;
    const MAC_ADDRESS_D: u64 = 3;
    const MAC_ADDRESS_E: u64 = 4;
    const MAC_ADDRESS_F: u64 = 5;

    const VIRTIO_F_VERSION_1: u32 = 1;
    const VIRTIO_NET_F_MAC: u32 = 1 << 5;

    pub fn new(
        memory: Arc<EmulatorControl>,
        interrupt: Interrupt,
        tcp_forwards: Vec<ForwardedPort>,
    ) -> Self {
        let interrupt_status = Arc::new(Mutex::new(InterruptStatus {
            status: 0,
            interrupt,
        }));

        let receive = Arc::new(Mutex::new(VirtQueue::new()));
        let transmit = Arc::new(Mutex::new(VirtQueue::new()));
        let transmit_notify = Arc::new(Condvar::new());

        let slirp_sender = spawn_slirp_thread(
            memory.clone(),
            interrupt_status.clone(),
            receive.clone(),
            tcp_forwards,
        );
        spawn_transmitter_thread(
            interrupt_status.clone(),
            memory,
            transmit.clone(),
            transmit_notify.clone(),
            slirp_sender,
        );

        Self {
            interrupt_status,
            receive,
            transmit,
            transmit_notify,
        }
    }
}

impl VirtioDevice for VirtioNet {
    fn device_id(&self) -> u32 {
        1
    }

    fn vendor_id(&self) -> u32 {
        0
    }

    fn supported_features(&self, word_sel: u32) -> u32 {
        match word_sel {
            0 => Self::VIRTIO_NET_F_MAC,
            1 => Self::VIRTIO_F_VERSION_1,
            2.. => 0,
        }
    }

    fn set_features(&self, _word_sel: u32, _feature_bits: u32) {}

    fn reset(&self) {
        self.receive.lock().unwrap().reset();
        self.transmit.lock().unwrap().reset();
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

    fn with_queue<R>(
        &self,
        queue_idx: usize,
        f: impl FnOnce(&mut super::queue::VirtQueue) -> R,
    ) -> Option<R> {
        match queue_idx {
            0 => Some(f(&mut self.receive.lock().unwrap())),
            1 => Some(f(&mut self.transmit.lock().unwrap())),
            _ => None,
        }
    }

    fn get_config(&self, reg: u64, width: IoWidth) -> Option<u64> {
        Some(match reg {
            Self::MAC_ADDRESS_A if width == IoWidth::One => 0x52,
            Self::MAC_ADDRESS_B if width == IoWidth::One => 0x55,
            Self::MAC_ADDRESS_C if width == IoWidth::One => 0x0a,
            Self::MAC_ADDRESS_D if width == IoWidth::One => 0x00,
            Self::MAC_ADDRESS_E if width == IoWidth::One => 0x02,
            Self::MAC_ADDRESS_F if width == IoWidth::One => 0x0e,
            _ => 0,
        })
    }

    fn put_config(&self, _reg: u64, _value: u64, _width: IoWidth) -> bool {
        false
    }

    fn dispatch(&self, queue: usize) {
        match queue {
            0 => {}
            1 => {
                // Lock the queue before notifying, to avoid race condition
                let _lock = self.transmit.lock().unwrap();
                self.transmit_notify.notify_all();
            }
            2.. => {}
        }
    }
}

fn spawn_transmitter_thread(
    interrupt_status: Arc<Mutex<InterruptStatus>>,
    memory: Arc<EmulatorControl>,
    queue: Arc<Mutex<VirtQueue>>,
    notification: Arc<Condvar>,
    slirp_sender: EventFdSender<SlirpMessage>,
) {
    std::thread::Builder::new()
        .name("virtio-net-transmit".to_owned())
        .spawn(move || {
            let mut last_handled = None;
            loop {
                let (chain_start, descriptor_ring) = {
                    // Get the next available descriptor chain
                    let mut lock = queue.lock().unwrap();
                    if let Some((handled_chain, bytes_written)) = last_handled.take() {
                        match lock.push_used(&memory, handled_chain, bytes_written) {
                            Ok(()) => {}
                            Err(e) => {
                                eprintln!("virtio-net-transmit: {e}");
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
                                eprintln!("virtio-net-transmit: {e}");
                                // FIXME: Set DEVICE_NEEDS_RESET
                            }
                        }
                    }
                    loop {
                        match lock.pop_available(&memory) {
                            Ok(Some(chain_start)) => break (chain_start, lock.descriptor_ring()),
                            Ok(None) => {
                                // If none is available immediately, wait for the next dispatch() call
                                lock = notification.wait(lock).unwrap();
                            }
                            Err(e) => {
                                eprintln!("virtio-net-transmit: {e}");
                                // FIXME: Set DEVICE_NEEDS_RESET
                            }
                        }
                    }
                };

                last_handled = match handle_transmit_chain(
                    chain_start,
                    descriptor_ring,
                    &slirp_sender,
                    &memory,
                ) {
                    Ok(bytes_written) => Some((chain_start, bytes_written)),
                    Err(e) => {
                        eprintln!("virtio-net-transmit: {e}");
                        Some((chain_start, 0))
                    }
                }
            }
        })
        .unwrap();
}

fn handle_transmit_chain(
    chain_start: u16,
    descriptor_ring: DescriptorRing,
    slirp_sender: &EventFdSender<SlirpMessage>,
    memory: &EmulatorControl,
) -> Result<u32, VirtioError> {
    let chain = descriptor_ring.get_chain(memory, chain_start)?;

    let header = chain.read::<VirtioNetHeader>(memory, 0)?;
    let data_len = chain.len() - header.hdr_len.to_ne() as usize;

    let Ok(mut buf) = bytemuck::allocation::try_zeroed_slice_box(data_len) else {
        std::alloc::handle_alloc_error(std::alloc::Layout::array::<u8>(data_len).unwrap())
    };
    chain.read_into(memory, size_of::<VirtioNetHeader>() as u32, &mut buf)?;
    slirp_sender.send(SlirpMessage::GuestTransmit(buf)).unwrap();

    Ok(0)
}

type TimerCallback = Box<dyn FnMut()>;

struct VirtioNetHandler {
    clock: Clock,
    base_now: u64,
    memory: Arc<EmulatorControl>,
    interrupt_status: Arc<Mutex<InterruptStatus>>,
    receive_queue: Arc<Mutex<VirtQueue>>,
    channel: EventFdSender<SlirpMessage>,
    timer_callbacks: Rc<RefCell<Slab<TimerCallback>>>,
}

struct SlirpInterrupt {
    channel: EventFdSender<SlirpMessage>,
    callback_index: usize,
}

impl InterruptDestination for SlirpInterrupt {
    fn trigger(&self) {
        self.channel
            .send(SlirpMessage::TimerDone(self.callback_index))
            .unwrap();
    }

    fn untrigger(&self) {}
}

impl Handler for VirtioNetHandler {
    type Timer = (Timer, usize);

    fn clock_get_ns(&mut self) -> i64 {
        self.clock.delta_as_nanos(self.base_now, self.clock.raw()) as i64
    }

    fn send_packet(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        let (chain_start, descriptor_ring) = {
            let mut queue = self.receive_queue.lock().unwrap();
            let Ok(Some(chain_start)) = queue.pop_available(&self.memory) else {
                return Ok(0);
            };
            (chain_start, queue.descriptor_ring())
        };

        let chain = descriptor_ring
            .get_chain(&self.memory, chain_start)
            .map_err(|_| std::io::ErrorKind::HostUnreachable)?;
        chain
            .write(
                &self.memory,
                0,
                VirtioNetHeader {
                    flags: 0,
                    gso_type: 0,
                    hdr_len: Le::<u16>::from_ne(0),
                    gso_size: Le::<u16>::from_ne(0),
                    csum_start: Le::<u16>::from_ne(0),
                    csum_offset: Le::<u16>::from_ne(0),
                    num_buffers: Le::<u16>::from_ne(1),
                },
            )
            .map_err(|_| std::io::ErrorKind::HostUnreachable)?;
        chain
            .write_buf(&self.memory, size_of::<VirtioNetHeader>() as u32, buf)
            .map_err(|_| std::io::ErrorKind::HostUnreachable)?;

        let mut queue = self.receive_queue.lock().unwrap();
        let Ok(()) = queue.push_used(
            &self.memory,
            chain_start,
            (size_of::<VirtioNetHeader>() + buf.len()) as u32,
        ) else {
            return Ok(0);
        };
        if queue.notifications_needed(&self.memory).unwrap_or(false) {
            let mut interrupt_status = self.interrupt_status.lock().unwrap();
            interrupt_status.status = 1;
            interrupt_status.interrupt.trigger();
        }

        Ok(buf.len())
    }

    fn register_poll_fd(&mut self, _fd: RawFd) {
        // Nothing to do
    }

    fn unregister_poll_fd(&mut self, _remove_fd: RawFd) {
        // Nothing to do
    }

    fn guest_error(&mut self, msg: &str) {
        eprintln!("Slirp guest error: {msg}\r");
    }

    fn notify(&mut self) {
        // Nothing to do
    }

    fn timer_new(&mut self, func: TimerCallback) -> Box<Self::Timer> {
        // FIXME: Use timerfds maybe?
        let callback_index = self.timer_callbacks.borrow().insert(func);
        let timer = Timer::new(
            SlirpInterrupt {
                channel: self.channel.clone(),
                callback_index,
            }
            .into(),
            self.clock.clone(),
            self.base_now,
        );
        Box::new((timer, callback_index))
    }

    fn timer_mod(&mut self, timer: &mut Box<Self::Timer>, expire_time: i64) {
        timer.0.set_cmp(expire_time as u64);
    }

    fn timer_free(&mut self, timer: Box<Self::Timer>) {
        timer.0.stop();
        self.timer_callbacks.borrow_mut().remove(timer.1);
    }
}

fn spawn_slirp_thread(
    memory: Arc<EmulatorControl>,
    interrupt_status: Arc<Mutex<InterruptStatus>>,
    receive_queue: Arc<Mutex<VirtQueue>>,
    tcp_forwards: Vec<ForwardedPort>,
) -> EventFdSender<SlirpMessage> {
    let (sender, receiver) = mpsc::channel();
    let event_fd = unsafe {
        // SAFETY: This is always safe to call.
        libc::eventfd(0, 0)
    };

    let channel = EventFdSender {
        fd: event_fd,
        sender: sender.clone(),
    };

    std::thread::Builder::new()
        .name("virtio-net-slirp".to_owned())
        .spawn(move || {
            let clock = Clock::new();
            let base_now = clock.raw();
            let timer_callbacks = Rc::new(RefCell::new(Slab::new(16)));
            let handler = VirtioNetHandler {
                clock,
                base_now,
                memory,
                interrupt_status,
                receive_queue,
                timer_callbacks: timer_callbacks.clone(),
                channel,
            };

            let slirp = libslirp::Context::new(
                false,
                true,
                Ipv4Addr::new(10, 0, 2, 0),
                Ipv4Addr::new(255, 255, 255, 0),
                Ipv4Addr::new(10, 0, 2, 2),
                false,
                Ipv6Addr::new(0xfce0, 0, 0, 0, 0, 0, 0, 0),
                64,
                Ipv6Addr::new(0xfce0, 0, 0, 0, 0, 0, 0, 2),
                None,
                None,
                None,
                None,
                Ipv4Addr::new(10, 0, 2, 15),
                Ipv4Addr::new(10, 0, 2, 3),
                Ipv6Addr::new(0xfce0, 0, 0, 0, 0, 0, 0, 3),
                vec![],
                None,
                handler,
            );

            for tcp_forward in tcp_forwards {
                unsafe {
                    libslirp_sys::slirp_add_hostfwd(
                        slirp.inner.context,
                        0,
                        libslirp_sys::in_addr {
                            s_addr: Ipv4Addr::new(127, 0, 0, 1).to_bits().to_be(),
                        },
                        tcp_forward.host_port.into(),
                        libslirp_sys::in_addr {
                            s_addr: Ipv4Addr::new(10, 0, 2, 15).to_bits().to_be(),
                        },
                        tcp_forward.guest_port.into(),
                    )
                };
            }

            let mut poll_fds = Vec::new();
            loop {
                loop {
                    match receiver.try_recv() {
                        Ok(SlirpMessage::TimerDone(callback)) => {
                            (timer_callbacks.borrow_mut().get_mut(callback).unwrap())()
                        }
                        Ok(SlirpMessage::GuestTransmit(buf)) => {
                            slirp.input(&buf);
                        }
                        Err(mpsc::TryRecvError::Empty) => break,
                        Err(mpsc::TryRecvError::Disconnected) => return,
                    }
                }

                poll_fds.clear();
                poll_fds.push(libc::pollfd {
                    fd: event_fd,
                    events: libc::POLLIN,
                    revents: 0,
                });

                let mut timeout = u32::MAX;
                slirp.pollfds_fill(&mut timeout, |fd, events| {
                    poll_fds.push(libc::pollfd {
                        fd,
                        events: {
                            let mut poll_events = 0;
                            if events.has_in() {
                                poll_events |= libc::POLLIN;
                            }
                            if events.has_out() {
                                poll_events |= libc::POLLOUT;
                            }
                            if events.has_pri() {
                                poll_events |= libc::POLLPRI;
                            }
                            if events.has_err() {
                                poll_events |= libc::POLLERR;
                            }
                            if events.has_hup() {
                                poll_events |= libc::POLLHUP;
                            }
                            poll_events
                        },
                        revents: 0,
                    });
                    (poll_fds.len() - 1) as i32
                });

                let error = unsafe {
                    // SAFETY: poll_fds is a list of struct pollfds, and is poll_fds.len() long.
                    libc::poll(
                        std::ptr::addr_of_mut!(poll_fds[0]),
                        poll_fds.len() as u64,
                        i32::from_ne_bytes(timeout.to_ne_bytes()),
                    )
                };

                slirp.pollfds_poll(error < 0, |i| {
                    let mut events = libslirp::PollEvents::empty();
                    let revents = poll_fds[i as usize].revents;
                    if revents & libc::POLLIN != 0 {
                        events |= libslirp::PollEvents::poll_in();
                    }
                    if revents & libc::POLLOUT != 0 {
                        events |= libslirp::PollEvents::poll_out();
                    }
                    if revents & libc::POLLPRI != 0 {
                        events |= libslirp::PollEvents::poll_pri();
                    }
                    if revents & libc::POLLERR != 0 {
                        events |= libslirp::PollEvents::poll_err();
                    }
                    if revents & libc::POLLHUP != 0 {
                        events |= libslirp::PollEvents::poll_hup();
                    }
                    events
                });

                if poll_fds[0].revents & libc::POLLIN != 0 {
                    // Clear the eventfd if it has been triggered
                    unsafe {
                        // SAFETY: self.fd is a valid file descriptor
                        let mut value = 0;
                        libc::eventfd_read(event_fd, std::ptr::addr_of_mut!(value));
                    }
                }
            }
        })
        .unwrap();

    EventFdSender {
        fd: event_fd,
        sender: sender.clone(),
    }
}
