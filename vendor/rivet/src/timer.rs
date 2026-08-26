// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::sync::mpsc::{self, RecvTimeoutError, SyncSender};
use std::thread::JoinHandle;
use std::time::Duration;

use quanta::Clock;

use crate::emulator::Interrupt;

enum TimerMessage {
    SetTime { base_now: u64, offset: u64 },
    SetCompare(u64),
}

pub struct Timer {
    channel: SyncSender<TimerMessage>,
    thread: JoinHandle<()>,
}

impl Timer {
    pub fn new(interrupt: Interrupt, clock: Clock, now: u64) -> Self {
        let (send, recv) = mpsc::sync_channel(8);
        let thread = std::thread::Builder::new()
            .name("timer".to_owned())
            .spawn(move || {
                let mut base_now = now;
                let mut offset = 0;
                let mut cmp = u64::MAX;

                loop {
                    let now = clock.delta_as_nanos(base_now, clock.raw()) + offset;
                    interrupt.set_triggered(cmp <= now);

                    match if now < cmp {
                        recv.recv_timeout(Duration::from_nanos(cmp - now))
                    } else {
                        recv.recv().map_err(Into::into)
                    } {
                        Ok(TimerMessage::SetTime {
                            base_now: new_base,
                            offset: new_offset,
                        }) => {
                            base_now = new_base;
                            offset = new_offset;
                        }
                        Ok(TimerMessage::SetCompare(new_cmp)) => {
                            cmp = new_cmp;
                        }
                        Err(RecvTimeoutError::Timeout) => {}
                        Err(RecvTimeoutError::Disconnected) => break,
                    }
                }
            })
            .unwrap();

        Self {
            channel: send,
            thread,
        }
    }

    pub fn set_time(&self, base_now: u64, offset: u64) {
        self.channel
            .send(TimerMessage::SetTime { base_now, offset })
            .expect("timer thread unexpectedly panicked")
    }

    pub fn set_cmp(&self, cmp: u64) {
        self.channel
            .send(TimerMessage::SetCompare(cmp))
            .expect("timer thread unexpectedly panicked")
    }

    pub fn stop(self) {
        drop(self.channel);
        self.thread.join().unwrap();
    }
}
