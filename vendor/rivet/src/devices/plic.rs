// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

use std::sync::{Arc, Mutex};

use crate::devices::{IoWidth, MmioDevice};
use crate::emulator::{Interrupt, InterruptDestination};
use crate::ffi::AmoKind;

#[derive(Clone, Copy)]
struct BitSet {
    value: u32,
}

impl BitSet {
    fn get(self, bit: usize) -> bool {
        (self.value & (1 << bit)) != 0
    }

    fn set(&mut self, bit: usize, value: bool) {
        if value {
            self.value |= 1 << bit;
        } else {
            self.value &= !(1 << bit);
        }
    }

    fn get_u32(self) -> u32 {
        self.value
    }

    fn set_u32(&mut self, value: u32) {
        self.value = value;
    }
}

#[derive(Clone, Copy)]
struct Source {
    priority: u32,
    triggered: bool,
    claimed: bool,
}

impl Source {
    fn pending(self) -> bool {
        self.triggered && !self.claimed
    }
}

struct Context {
    enable: BitSet,
    threshold: u32,
    interrupt: Interrupt,
}

struct PlicCore {
    sources: Vec<Source>,
    contexts: Vec<Context>,
}

impl PlicCore {
    fn refresh_interrupts(&mut self) {
        for context in &self.contexts {
            context
                .interrupt
                .set_triggered(self.sources.iter().enumerate().skip(1).any(|(i, source)| {
                    source.pending() && context.enable.get(i) && context.threshold < source.priority
                }));
        }
    }

    fn trigger(&mut self, source: usize) {
        self.sources[source].triggered = true;
        self.refresh_interrupts();
    }

    fn untrigger(&mut self, source: usize) {
        self.sources[source].triggered = false;
        self.refresh_interrupts();
    }

    pub fn pending(&self) -> BitSet {
        let mut pending = BitSet { value: 0 };
        for (i, source) in self.sources.iter().enumerate().skip(1) {
            pending.set(i, source.pending());
        }
        pending
    }

    pub fn claim(&mut self, context: usize) -> u32 {
        // Find the highest priority enabled interrupt
        let mut current_interrupt = 0u32;
        let mut current_priority = 0u32;
        for (i, source) in self.sources.iter().enumerate().skip(1) {
            if current_priority < source.priority
                && source.pending()
                && self.contexts[context].enable.get(i)
            {
                current_interrupt = i as u32;
                current_priority = source.priority;
            }
        }

        // Claim the interrupt
        if current_interrupt != 0 {
            self.sources[current_interrupt as usize].claimed = true;
        }
        self.refresh_interrupts();

        current_interrupt
    }

    pub fn complete(&mut self, source: usize) {
        self.sources[source].claimed = false;
        // FIXME: Refresh interrupts?
    }
}

pub struct Plic {
    core: Mutex<PlicCore>,
}

impl Plic {
    const INTERRUPT_MASK: u32 = 0b1110;

    pub fn new(m_external: Interrupt, s_external: Interrupt) -> Self {
        Self {
            core: Mutex::new(PlicCore {
                sources: vec![
                    Source {
                        priority: 0,
                        triggered: false,
                        claimed: false
                    };
                    4
                ],
                contexts: vec![
                    Context {
                        enable: BitSet { value: 0 },
                        threshold: 0,
                        interrupt: m_external,
                    },
                    Context {
                        enable: BitSet { value: 0 },
                        threshold: 0,
                        interrupt: s_external,
                    },
                ],
            }),
        }
    }

    pub fn interrupt_destination(self: Arc<Self>, idx: u32) -> Interrupt {
        assert!(idx != 0, "0 is not a valid PLIC interrupt index");
        Interrupt::from(PlicInterrupt {
            plic: self,
            interrupt: idx,
        })
    }
}

impl MmioDevice for Plic {
    fn get(&self, reg: u64, width: IoWidth) -> Option<u64> {
        if width != IoWidth::Four || !reg.is_multiple_of(0x4) {
            return None;
        }

        Some(
            match reg {
                0x4..0x1000 => self
                    .core
                    .lock()
                    .unwrap()
                    .sources
                    .get(reg as usize / 0x4)
                    .map(|source| source.priority)
                    .unwrap_or(0),
                0x1000 => self.core.lock().unwrap().pending().get_u32(),
                0x2000 => self.core.lock().unwrap().contexts[0].enable.get_u32(),
                0x2080 => self.core.lock().unwrap().contexts[1].enable.get_u32(),
                0x200000 => self.core.lock().unwrap().contexts[0].threshold,
                0x200004 => self.core.lock().unwrap().claim(0),
                0x201000 => self.core.lock().unwrap().contexts[1].threshold,
                0x201004 => self.core.lock().unwrap().claim(1),
                _ => 0,
            }
            .into(),
        )
    }

    fn put(&self, reg: u64, value: u64, width: IoWidth) -> bool {
        if width != IoWidth::Four || !reg.is_multiple_of(0x4) {
            return false;
        }
        let value = value as u32;

        match reg {
            0x4..0x1000 => {
                if let Some(source) = self
                    .core
                    .lock()
                    .unwrap()
                    .sources
                    .get_mut(reg as usize / 0x4)
                {
                    source.priority = value;
                }
            }
            0x2000 => self.core.lock().unwrap().contexts[0]
                .enable
                .set_u32(value & Self::INTERRUPT_MASK),
            0x2080 => self.core.lock().unwrap().contexts[1]
                .enable
                .set_u32(value & Self::INTERRUPT_MASK),
            0x200000 => self.core.lock().unwrap().contexts[0].threshold = value,
            0x200004 => self.core.lock().unwrap().complete(value as usize),
            0x201000 => self.core.lock().unwrap().contexts[1].threshold = value,
            0x201004 => self.core.lock().unwrap().complete(value as usize),
            _ => {}
        }

        true
    }

    fn amo(&self, reg: u64, value: u64, operation: AmoKind, width: IoWidth) -> Option<u64> {
        if width != IoWidth::Four || !reg.is_multiple_of(0x4) {
            return None;
        }
        let value = value as u32;

        Some(
            match reg {
                0x4..0x1000 => self
                    .core
                    .lock()
                    .unwrap()
                    .sources
                    .get_mut(reg as usize / 0x4)
                    .map(|source| {
                        let prev_priority = source.priority;
                        source.priority = operation.operate_u32_unordered(source.priority, value);
                        prev_priority
                    })
                    .unwrap_or(0),
                0x2000 => {
                    let context = &mut self.core.lock().unwrap().contexts[0];
                    let prev_enable = context.enable.get_u32();
                    context.enable.set_u32(
                        operation.operate_u32_unordered(prev_enable, value & Self::INTERRUPT_MASK),
                    );
                    prev_enable
                }
                0x2004..0x2080 => 0,
                0x2080 => {
                    let context = &mut self.core.lock().unwrap().contexts[1];
                    let prev_enable = context.enable.get_u32();
                    context.enable.set_u32(
                        operation.operate_u32_unordered(prev_enable, value & Self::INTERRUPT_MASK),
                    );
                    prev_enable
                }
                0x2084..0x1F2000 => 0,
                0x200000 => {
                    let context = &mut self.core.lock().unwrap().contexts[0];
                    let prev_threshold = context.threshold;
                    operation.operate_u32_unordered(context.threshold, value);
                    prev_threshold
                }
                0x201000 => {
                    let context = &mut self.core.lock().unwrap().contexts[1];
                    let prev_threshold = context.threshold;
                    operation.operate_u32_unordered(context.threshold, value);
                    prev_threshold
                }
                _ => 0,
            }
            .into(),
        )
    }
}

struct PlicInterrupt {
    plic: Arc<Plic>,
    interrupt: u32,
}

impl InterruptDestination for PlicInterrupt {
    fn trigger(&self) {
        self.plic
            .core
            .lock()
            .unwrap()
            .trigger(self.interrupt as usize);
    }

    fn untrigger(&self) {
        self.plic
            .core
            .lock()
            .unwrap()
            .untrigger(self.interrupt as usize);
    }
}
