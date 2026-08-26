// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::mem::MaybeUninit;
use std::os::fd::AsRawFd;
use std::sync::atomic::{self, AtomicU8};
use std::sync::{Arc, Mutex};

use crossbeam_channel::{Receiver, Sender};

use crate::devices::{IoWidth, MmioDevice};
use crate::emulator::{EmulatorControl, Interrupt};
use crate::ffi::AmoKind;

pub struct Ns16550a {
    input: Receiver<u8>,
    ready_input: Mutex<Option<u8>>,
    output: Sender<u8>,

    line_control: AtomicU8,
    interrupt_status: Arc<Mutex<InterruptStatus>>,

    div_l: AtomicU8,
    div_h: AtomicU8,
}

impl Ns16550a {
    pub fn new(interrupt: Interrupt, control: Arc<EmulatorControl>) -> Self {
        let (input_send, input) = crossbeam_channel::bounded(64);
        let (output, output_recv) = crossbeam_channel::bounded(64);

        let interrupt_status = Arc::new(Mutex::new(InterruptStatus::new(input.clone(), interrupt)));

        spawn_reader(input_send, interrupt_status.clone(), control);
        spawn_writer(output_recv, interrupt_status.clone());

        Self {
            input,
            ready_input: Mutex::new(None),
            output,
            line_control: AtomicU8::new(0),
            interrupt_status,
            div_l: AtomicU8::new(0),
            div_h: AtomicU8::new(0),
        }
    }
}

impl MmioDevice for Ns16550a {
    fn get(&self, reg: u64, width: IoWidth) -> Option<u64> {
        if width != IoWidth::One {
            return None;
        }

        match reg {
            0 if self.line_control.load(atomic::Ordering::Relaxed) & 0b10000000 != 0 => {
                Some(self.div_l.load(atomic::Ordering::Relaxed).into())
            }
            0 => Some({
                if let Some(b) = self.ready_input.lock().unwrap().take() {
                    self.interrupt_status.lock().unwrap().refresh();
                    b.into()
                } else if let Ok(b) = self.input.try_recv() {
                    self.interrupt_status.lock().unwrap().refresh();
                    b.into()
                } else {
                    0
                }
            }),
            1 if self.line_control.load(atomic::Ordering::Relaxed) & 0b10000000 != 0 => {
                Some(self.div_h.load(atomic::Ordering::Relaxed) as _)
            }
            1 => Some(self.interrupt_status.lock().unwrap().enable.into()),
            2 => Some(
                self.interrupt_status
                    .lock()
                    .unwrap()
                    .identification(&self.input)
                    .into(),
            ),

            3 => Some(self.line_control.load(atomic::Ordering::Relaxed).into()),
            5 => Some({
                let mut ready_input = self.ready_input.lock().unwrap();
                if ready_input.is_some() {
                    0x61
                } else if let Ok(b) = self.input.try_recv() {
                    *ready_input = Some(b);
                    0x61
                } else {
                    0x60
                }
            }),
            _ => Some(0),
        }
    }

    fn put(&self, reg: u64, value: u64, width: IoWidth) -> bool {
        if width != IoWidth::One {
            return false;
        }
        let value = value as u8;

        match reg {
            0 if self.line_control.load(atomic::Ordering::Relaxed) & 0b10000000 != 0 => {
                self.div_l.store(value, atomic::Ordering::Relaxed)
            }
            0 => self.output.send(value).unwrap(),
            1 if self.line_control.load(atomic::Ordering::Relaxed) & 0b10000000 != 0 => {
                self.div_h.store(value, atomic::Ordering::Relaxed)
            }
            1 => self.interrupt_status.lock().unwrap().enable = value,
            2 => self.interrupt_status.lock().unwrap().trigger_level = value,
            3 => self.line_control.store(value, atomic::Ordering::Relaxed),
            _ => {}
        }

        true
    }

    fn amo(&self, reg: u64, value: u64, operation: AmoKind, width: IoWidth) -> Option<u64> {
        if width != IoWidth::One {
            return None;
        }
        let value = value as u8;

        Some(match reg {
            0 if self.line_control.load(atomic::Ordering::Relaxed) & 0b10000000 != 0 => operation
                .operate_u8(&self.div_l, value, atomic::Ordering::Relaxed)
                .into(),
            1 if self.line_control.load(atomic::Ordering::Relaxed) & 0b10000000 != 0 => operation
                .operate_u8(&self.div_h, value, atomic::Ordering::Relaxed)
                .into(),
            1 => {
                let mut interrupt_status = self.interrupt_status.lock().unwrap();
                let old_enable = interrupt_status.enable;
                interrupt_status.enable = operation.operate_u8_unordered(old_enable, value);
                old_enable.into()
            }
            2 => {
                let mut interrupt_status = self.interrupt_status.lock().unwrap();
                let old_trigger_level = interrupt_status.trigger_level;
                interrupt_status.enable = operation.operate_u8_unordered(old_trigger_level, value);
                old_trigger_level.into()
            }
            3 => operation
                .operate_u8(&self.line_control, value, atomic::Ordering::Relaxed)
                .into(),
            _ => return None,
        })
    }
}

struct InterruptStatus {
    pub enable: u8,
    pub trigger_level: u8,
    input: Receiver<u8>,
    transmission_done: bool,
    interrupt: Interrupt,
}

impl InterruptStatus {
    const INTERRUPT_ENABLE_DATA_AVAILABLE: u8 = 1 << 0;
    const INTERRUPT_ENABLE_TRANSMISSION_DONE: u8 = 1 << 1;

    fn new(input: Receiver<u8>, interrupt: Interrupt) -> Self {
        Self {
            enable: 0,
            trigger_level: 1,
            input,
            transmission_done: false,
            interrupt,
        }
    }

    fn identification(&mut self, input_recv: &Receiver<u8>) -> u8 {
        if self.enable & Self::INTERRUPT_ENABLE_DATA_AVAILABLE != 0 && !input_recv.is_empty() {
            return 0xCC;
        }
        if self.transmission_done {
            self.transmission_done = false;
            self.refresh();
            return 0xC2;
        }

        0xC1
    }

    fn trigger_transmission_done(&mut self) {
        if self.enable & Self::INTERRUPT_ENABLE_TRANSMISSION_DONE != 0 {
            self.transmission_done = true;
            self.refresh();
        }
    }

    fn refresh(&self) {
        self.interrupt.set_triggered(
            self.enable & Self::INTERRUPT_ENABLE_DATA_AVAILABLE != 0 && !self.input.is_empty()
                || self.transmission_done,
        );
    }
}

fn spawn_reader(
    input_send: Sender<u8>,
    interrupt: Arc<Mutex<InterruptStatus>>,
    control: Arc<EmulatorControl>,
) {
    std::thread::Builder::new()
        .name("ns16550a-reader".to_owned())
        .spawn(move || {
            use std::io::Read;

            let stdin = std::io::stdin().lock();
            let stdin_fd = stdin.as_raw_fd();
            let old_termios = set_raw_mode(stdin_fd);
            let prev_panic_hook = std::panic::take_hook();
            std::panic::set_hook(Box::new(move |info| {
                reset_termios(stdin_fd, old_termios);
                prev_panic_hook(info)
            }));

            for b in stdin.bytes() {
                let b = b.unwrap();
                if b == b'\x1c' {
                    // ^\ pressed, quit.
                    reset_termios(stdin_fd, old_termios);
                    control.exit_code.store(63, atomic::Ordering::Relaxed);
                    control.stop_all_harts();
                    return;
                }
                input_send.send(b).unwrap();
                interrupt.lock().unwrap().refresh();
            }
        })
        .unwrap();
}

fn spawn_writer(output_recv: Receiver<u8>, interrupt: Arc<Mutex<InterruptStatus>>) {
    std::thread::Builder::new()
        .name("ns16550a-writer".to_owned())
        .spawn(move || {
            use std::io::Write;

            let mut stdout = std::io::stdout().lock();
            for b in output_recv.iter() {
                stdout.write_all(&[b]).unwrap();
                stdout.flush().unwrap();
                interrupt.lock().unwrap().trigger_transmission_done();
            }
        })
        .unwrap();
}

fn set_raw_mode(fd: i32) -> libc::termios {
    let mut termios = MaybeUninit::zeroed();
    unsafe {
        libc::tcgetattr(fd, termios.as_mut_ptr());
    }

    let mut new_termios = termios;
    unsafe {
        libc::cfmakeraw(new_termios.as_mut_ptr());
        libc::tcsetattr(fd, libc::TCSAFLUSH, new_termios.as_mut_ptr());
    }

    unsafe { termios.assume_init() }
}

fn reset_termios(fd: i32, mut old_termios: libc::termios) {
    unsafe {
        libc::tcsetattr(fd, libc::TCSAFLUSH, std::ptr::addr_of_mut!(old_termios));
    }
}
