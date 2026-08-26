// SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");
const softfp = @import("softfp");
const csr = @import("csr.zig");
const riscv = @import("riscv.zig");
const MachineCsr = @import("MachineCsr.zig");
const Memory = @import("Memory.zig");

const Self = @This();

const CsrMapping = csr.CsrMapping;

const FloatRegister = packed struct {
    l: u32,
    h: u32,
};

pub const AccessType = enum {
    r,
    w,
    x,
};

const PageTableEntry = packed struct(u64) {
    v: bool,
    r: bool,
    w: bool,
    x: bool,
    u: bool,
    g: bool,
    a: bool,
    d: bool,
    rsw: u2,
    ppn: u44,
    reserved: u10,
};

const TlbEntry = packed struct(u64) {
    ppn: u27 = 0,
    vpn: u27 = 0,
    skip_pmp_check: bool = false,
    u: bool = false,
    padding: u7 = 0,
    valid: bool = false,
};

const TLB_SIZE = 1 << 6;

const IALIGN = 0b10; // 16

pub const Controller = struct {
    mip: std.atomic.Value(u64) = std.atomic.Value(u64).init(0),
    running: std.atomic.Value(bool) = std.atomic.Value(bool).init(true),
    interrupt_fd: i32,

    pub fn stop(self: *Controller) void {
        self.running.store(false, .monotonic);
        _ = std.posix.write(self.interrupt_fd, @as([8]u8, @bitCast(@as(u64, 1)))[0..]) catch unreachable;
    }

    fn wfi(self: *Controller) void {
        while (self.mip.load(.acquire) == 0 and self.running.load(.monotonic)) {
            var buf = std.mem.zeroes([8]u8);
            _ = std.posix.read(self.interrupt_fd, &buf) catch unreachable;
        }
    }
};

/// The 32 general-purpose registers, including x0. x[0] must always be 0.
x: [32]u64 = std.mem.zeroes([32]u64),

/// The 32 floating-point registers, inside u64's for easier NaN-boxing
f: [32]FloatRegister = std.mem.zeroes([32]FloatRegister),

/// The program counter. Must be aligned to 16 bits
pc: u64 = 0,

/// Control and Status Registers
mcsr: MachineCsr,

/// The current processor privilege mode
priv: u2 = riscv.PRIVILEGE.m,

/// The value to write into mtval or stval when the next trap is taken
tval: u64 = 0,

/// The frame containing the previous instruction executed
instruction_frame: struct {
    page_addr: u64 = 0,
    frame_addr: u64 = 0,
    frame: ?Memory.GranularMemory = null,
    skip_pmp_check: bool = false,
} = .{},

read_tlb: [TLB_SIZE]TlbEntry = [1]TlbEntry{.{}} ** TLB_SIZE,
write_tlb: [TLB_SIZE]TlbEntry = [1]TlbEntry{.{}} ** TLB_SIZE,
instruction_tlb: [TLB_SIZE]TlbEntry = [1]TlbEntry{.{}} ** TLB_SIZE,

/// The frame currently reserved by an LR instruction
reserved_frame: ?*align(4096) [4096]u8 = null,

/// Interrupt controller and message channel, unique to this Hart
controller: Controller,

/// Main memory, shared with all Harts
memory: *Memory,

/// Countdown until next interrupt check
interrupt_countdown: u8 = 0,

pub fn init(allocator: std.mem.Allocator, memory: *Memory) Self {
    return Self{
        .mcsr = MachineCsr.init(allocator),
        .controller = Controller{
            .interrupt_fd = std.posix.eventfd(0, 0) catch unreachable,
        },
        .memory = memory,
    };
}

pub fn deinit(self: *Self) void {
    self.mcsr.deinit();
    std.posix.close(self.controller.interrupt_fd);
}

pub fn getUnsigned(self: *Self, register: u5) u64 {
    return self.x[register];
}

pub fn getSigned(self: *Self, register: u5) i64 {
    return @bitCast(self.x[register]);
}

pub fn getFloat(self: *Self, register: u5) u32 {
    if (self.f[register].h != std.math.maxInt(u32)) {
        // Incorrectly NaN-boxed; return canonical NaN
        return 0x7fc00000;
    }
    return self.f[register].l;
}

pub fn getFloatRaw(self: *Self, register: u5) u32 {
    return self.f[register].l;
}

pub fn getDouble(self: *Self, register: u5) u64 {
    return @bitCast(self.f[register]);
}

pub fn setUnsigned(self: *Self, register: u5, value: u64) void {
    if (register != 0) {
        self.x[register] = value;
    }
}

pub fn setSigned(self: *Self, register: u5, value: i64) void {
    if (register != 0) {
        self.x[register] = @bitCast(value);
    }
}

pub fn setFloat(self: *Self, register: u5, value: u32) void {
    self.f[register] = FloatRegister{
        .l = value,
        .h = std.math.maxInt(u32),
    };
}

pub fn setDouble(self: *Self, register: u5, value: u64) void {
    self.f[register] = @bitCast(value);
}

pub fn getPc(self: *Self) u64 {
    return self.pc;
}

pub fn setPc(self: *Self, value: u64) void {
    self.pc = value;
}

pub fn readCsr(self: *Self, register: u12) Trap!u64 {
    const mapping: CsrMapping = @bitCast(register);
    if (self.priv < mapping.privilege) {
        return Trap.IllegalInstruction;
    }
    // Debug mode registers
    if (0x7B0 <= register and register <= 0x7BF) {
        return Trap.IllegalInstruction;
    }

    if (register == riscv.CSR.mip) {
        return self.controller.mip.load(.acquire);
    }

    return self.mcsr.read(register, self);
}

pub fn rmwCsr(self: *Self, register: u12, operation: enum { Xchg, Set, Clear }, operand: u64) Trap!u64 {
    const mapping: CsrMapping = @bitCast(register);
    if (mapping.access == 0b11 or self.priv < mapping.privilege) {
        return Trap.IllegalInstruction;
    }
    // Debug mode registers
    if (0x7B0 <= register and register <= 0x7BF) {
        return Trap.IllegalInstruction;
    }

    if (register == riscv.CSR.mip) {
        return switch (operation) {
            .Xchg => self.controller.mip.swap(@truncate(operand), .acq_rel),
            .Set => self.controller.mip.fetchOr(@truncate(operand), .acq_rel),
            .Clear => self.controller.mip.fetchAdd(@truncate(~operand), .acq_rel),
        };
    }
    if (register == riscv.CSR.sip) {
        const supervisor_operand = operand & self.mcsr.mideleg.read();
        return switch (operation) {
            .Xchg => self.controller.mip.swap(@truncate(supervisor_operand), .acq_rel),
            .Set => self.controller.mip.fetchOr(@truncate(supervisor_operand), .acq_rel),
            .Clear => self.controller.mip.fetchAdd(@truncate(~supervisor_operand), .acq_rel),
        } & self.mcsr.mideleg.read();
    }

    // Some instructions aren't supposed to read the CSR, but doing so anyway
    // has no side effects
    const prev_value = self.readCsr(register) catch unreachable;
    const value = switch (operation) {
        .Xchg => operand,
        .Set => prev_value | operand,
        .Clear => prev_value & ~operand,
    };

    try self.mcsr.write(register, value, self);
    return prev_value;
}

fn translateAddr(self: *Self, addr: u64, len: u4, access_type: AccessType) error{ AccessFault, PageFault }!u64 {
    const priv = self.effectivePrivilege(access_type);
    if (priv != riscv.PRIVILEGE.m and self.mcsr.satp.get(.mode) != riscv.SATP_MODE.bare or len == 5) {
        // Sv39 virtual address translation is enabled.

        // Decode and check the virtual address
        const virt_addr: packed struct {
            pgoff: u12,
            vte: u27,
            sign_ext: u25,
        } = @bitCast(addr);

        if (virt_addr.vte > std.math.maxInt(u26)) {
            if (virt_addr.sign_ext != std.math.maxInt(u25)) {
                return error.PageFault;
            }
        } else if (virt_addr.sign_ext != 0) {
            return error.PageFault;
        }

        // Check the TLB
        const tlb = switch (access_type) {
            .r => &self.read_tlb,
            .w => &self.write_tlb,
            .x => &self.instruction_tlb,
        };
        const tlb_index = virt_addr.vte % TLB_SIZE;
        const tlb_entry = &tlb[tlb_index];
        if (tlb_entry.valid and tlb_entry.vpn == virt_addr.vte) {
            @branchHint(.likely);
            // TLB hit; used the cached physical address
            switch (priv) {
                riscv.PRIVILEGE.u => if (!tlb_entry.u) {
                    return error.PageFault;
                },
                riscv.PRIVILEGE.s => if (tlb_entry.u and (self.mcsr.mstatus.get(.sum) == 0 or access_type == .x)) {
                    return error.PageFault;
                },
                else => if (len != 5) unreachable,
            }

            const translated_addr = @shlExact(@as(u64, tlb_entry.ppn), 12) | virt_addr.pgoff;
            if (!tlb_entry.skip_pmp_check and !self.checkPmp(translated_addr, len, access_type)) {
                @branchHint(.cold);
                return error.AccessFault;
            }

            return translated_addr;
        }

        // TLB miss; Walk the page table
        var current_addr = @as(u64, self.mcsr.satp.get(.ppn)) << 12;
        var pte_addr: u64 = undefined;
        var pte = std.mem.zeroInit(PageTableEntry, .{});
        var page_level: u5 = 3;
        while (!(pte.r or pte.x)) {
            if (page_level == 0) {
                return error.PageFault;
            }
            page_level -= 1;
            pte_addr = current_addr + ((virt_addr.vte & @as(u27, 0b111111111) << page_level * 9) >> page_level * 9) * @sizeOf(PageTableEntry);
            if (!self.checkPmp(pte_addr, @sizeOf(PageTableEntry), .r)) {
                @branchHint(.cold);
                return error.AccessFault;
            }
            pte = @bitCast(if (self.memory.getFrameContaining(pte_addr)) |frame| pte: {
                break :pte frame.load(64, pte_addr & @as(u64, 0xfff));
            } else {
                return error.PageFault;
            });
            if (!pte.v or !pte.r and pte.w or pte.reserved != 0) {
                return error.PageFault;
            }
            current_addr = pte.ppn << 12;
        }

        // Check access permissions
        switch (priv) {
            riscv.PRIVILEGE.u => if (!pte.u) {
                return error.PageFault;
            },
            riscv.PRIVILEGE.s => if (pte.u and (self.mcsr.mstatus.get(.sum) == 0 or access_type == .x)) {
                return error.PageFault;
            },
            else => if (len != 5) unreachable,
        }
        switch (access_type) {
            .r => if (!(pte.r or self.mcsr.mstatus.get(.mxr) == 1 and pte.x)) {
                return error.PageFault;
            },
            .w => if (!pte.w) {
                return error.PageFault;
            },
            .x => if (!pte.x) {
                return error.PageFault;
            },
        }

        // Validate superpage alignment
        const superpage_mask = (@as(u27, 1) << page_level * 9) - 1;
        if (pte.ppn & superpage_mask != 0) {
            return error.PageFault;
        }

        // Check accessed and dirty flags
        if (!pte.a or access_type == .w and !pte.d) {
            if (self.mcsr.menvcfg.get(.adue) == 0) {
                // Svade extension
                return error.PageFault;
            }

            // Svadu extension
            // Skip checking the page table hasn't been modified, because we don't
            // support multi-hart yet
            pte.a = true;
            if (access_type == .w) {
                pte.d = true;
            }
            if (!self.checkPmp(pte_addr, @sizeOf(PageTableEntry), .w)) {
                @branchHint(.cold);
                return error.AccessFault;
            }
            std.debug.assert(self.memory.write(
                pte_addr,
                &@as([8]u8, @bitCast(std.mem.nativeToLittle(u64, @bitCast(pte)))),
            ));
        }

        // Add this access to the TLB
        current_addr |= (virt_addr.vte & superpage_mask) << 12;
        if (self.checkPmp(current_addr, 1 << 12, access_type)) {
            // If this entry is in a contiguous PMP region, remember this so we don't have
            // to re-check PMPs every single time
            tlb_entry.skip_pmp_check = true;
        } else {
            if (!self.checkPmp(current_addr, len, access_type)) {
                @branchHint(.cold);
                return error.AccessFault;
            }
            tlb_entry.skip_pmp_check = false;
        }
        errdefer comptime unreachable; // Don't allow any errors once the entry is marked valid
        tlb_entry.valid = true;
        tlb_entry.u = pte.u;
        tlb_entry.vpn = virt_addr.vte;
        tlb_entry.ppn = @truncate(current_addr >> 12);

        current_addr |= virt_addr.pgoff;
        return current_addr;
    } else {
        // No address translation. Just check PMPs.
        if (!self.checkPmp(addr, len, access_type)) {
            @branchHint(.cold);
            return error.AccessFault;
        }
        return addr;
    }
}

pub fn loadUnsigned(
    self: *Self,
    comptime bits: comptime_int,
    virt_addr: u64,
) Trap!std.meta.Int(.unsigned, bits) {
    const bytes = @divExact(bits, 8);
    const phys_addr = self.translateAddr(virt_addr, bytes, .r) catch |trap| {
        self.tval = virt_addr;
        switch (trap) {
            error.AccessFault => return Trap.LoadAccessFault,
            error.PageFault => return Trap.LoadPageFault,
        }
    };
    if (phys_addr & (bytes - 1) != 0) {
        self.tval = virt_addr;
        return Trap.LoadAccessMisaligned;
    }

    if (self.memory.load(self, bits, phys_addr)) |value| {
        return value;
    } else |trap| {
        self.tval = virt_addr;
        return trap;
    }
}

pub fn loadSigned(
    self: *Self,
    comptime bits: comptime_int,
    addr: u64,
) Trap!std.meta.Int(.signed, bits) {
    return @bitCast(try self.loadUnsigned(bits, addr));
}

pub fn loadReserved(
    self: *Self,
    comptime bits: comptime_int,
    virt_addr: u64,
) Trap!std.meta.Int(.signed, bits) {
    const bytes = @divExact(bits, 8);

    if (virt_addr & (bytes - 1) != 0) {
        self.tval = virt_addr;
        return Trap.LoadAccessMisaligned;
    }

    const phys_addr = self.translateAddr(virt_addr, bytes, .r) catch |trap| {
        self.tval = virt_addr;
        switch (trap) {
            error.AccessFault => return Trap.LoadAccessFault,
            error.PageFault => return Trap.LoadPageFault,
        }
    };

    if (self.memory.loadReserved(self, bits, phys_addr)) |value| {
        return @bitCast(value);
    } else |trap| {
        self.tval = virt_addr;
        return trap;
    }
}

pub fn loadInstr(self: *Self, pc: u64) Trap!u32 {
    std.debug.assert(pc & 0b1 == 0); // Misaligned instruction accesses are impossible

    const frame_addr, const frame, const skip_pmp_check = frame: {
        if (self.instruction_frame.frame) |frame| {
            if (self.instruction_frame.page_addr == pc & ~@as(u64, 0xfff)) {
                // We're in the same virtual page as the previous instruction,
                // so skip all the address translation.

                const offset: u12 = @truncate(pc);
                const phys_addr = self.instruction_frame.frame_addr | offset;

                // We still have to check PMPs, though
                if (!self.instruction_frame.skip_pmp_check and !self.checkPmp(phys_addr, 2, .x)) {
                    @branchHint(.cold);
                    self.tval = pc;
                    return Trap.InstructionAccessFault;
                }
                break :frame .{
                    self.instruction_frame.frame_addr,
                    frame,
                    self.instruction_frame.skip_pmp_check,
                };
            }
        }

        const phys_addr = self.translateAddr(pc, 2, .x) catch |trap| {
            self.tval = pc;
            switch (trap) {
                error.AccessFault => return Trap.InstructionAccessFault,
                error.PageFault => return Trap.InstructionPageFault,
            }
        };

        const frame_addr = phys_addr & ~@as(u64, 0xfff);
        if (self.memory.getFrame(frame_addr)) |frame| {
            break :frame .{
                frame_addr,
                frame,
                self.checkPmp(frame_addr, 1 << 12, .x),
            };
        } else {
            self.tval = pc;
            return Trap.InstructionAccessFault;
        }
    };
    const page_addr = pc & ~@as(u64, 0xfff);
    self.instruction_frame = .{
        .page_addr = page_addr,
        .frame_addr = frame_addr,
        .frame = frame,
        .skip_pmp_check = skip_pmp_check,
    };

    const offset: u12 = @truncate(pc);
    const granule_addr: Memory.GranuleAddr = @bitCast(@as(u64, offset));
    const phys_addr = frame_addr | offset;

    const granule = frame.getGranule(offset);

    var value = [4]u8{ 0, 0, 0, 0 };
    value[0] = granule[granule_addr.sub];
    value[1] = granule[granule_addr.sub + 1];
    if (value[0] & 0b11 == 0b11) {
        // This is a 4-byte (non-compressed) instruction
        var other_offset = offset +% 2;
        var other_granule = granule;

        if (offset == 0xffe) {
            // Instruction straddles two pages, load the second one
            const other_phys_addr = self.translateAddr(pc + 2, 2, .x) catch |trap| {
                self.tval = pc + 2;
                switch (trap) {
                    error.AccessFault => return Trap.InstructionAccessFault,
                    error.PageFault => return Trap.InstructionPageFault,
                }
            };
            other_offset = @truncate(other_phys_addr);
            const other_frame = if (self.memory.getFrame(other_phys_addr)) |f| other_frame: {
                break :other_frame f;
            } else {
                self.tval = pc + 2;
                return Trap.InstructionAccessFault;
            };
            const other_page_addr = (pc + 2) & ~@as(u64, 0xfff);
            self.instruction_frame = .{
                .page_addr = other_page_addr,
                .frame_addr = other_phys_addr & ~@as(u64, 0xfff),
                .frame = other_frame,
                .skip_pmp_check = self.checkPmp(other_page_addr, 1 << 12, .x),
            };

            other_granule = other_frame.getGranule(other_offset);
        } else {
            // Same page, but check we're allowed to do a 4-byte access here
            if (!self.instruction_frame.skip_pmp_check and !self.checkPmp(phys_addr, 4, .x)) {
                @branchHint(.cold);
                self.tval = pc + 2;
                return Trap.InstructionAccessFault;
            }

            if (granule_addr.sub == 0b110) {
                // Instruction straddles two atomicity granules, though. Load the second one
                other_granule = frame.getGranule(other_offset);
            }
        }

        const other_granule_addr: Memory.GranuleAddr = @bitCast(@as(u64, other_offset));
        value[2] = other_granule[other_granule_addr.sub];
        value[3] = other_granule[other_granule_addr.sub + 1];
    }
    return std.mem.littleToNative(u32, @bitCast(value));
}

pub fn storeUnsigned(
    self: *Self,
    comptime bits: comptime_int,
    virt_addr: u64,
    value: std.meta.Int(.unsigned, bits),
) Trap!void {
    const bytes = @divExact(bits, 8);
    const phys_addr = self.translateAddr(virt_addr, bytes, .w) catch |trap| {
        self.tval = virt_addr;
        switch (trap) {
            error.AccessFault => return Trap.StoreAccessFault,
            error.PageFault => return Trap.StorePageFault,
        }
    };
    if (phys_addr & (bytes - 1) != 0) {
        self.tval = virt_addr;
        return Trap.StoreAccessMisaligned;
    }

    if (self.memory.store(self, bits, phys_addr, value)) {
        return;
    } else |trap| {
        self.tval = phys_addr;
        return trap;
    }
}

pub fn storeConditional(
    self: *Self,
    comptime bits: comptime_int,
    virt_addr: u64,
    value: std.meta.Int(.unsigned, bits),
) Trap!bool {
    const bytes = @divExact(bits, 8);

    if (virt_addr & (bytes - 1) != 0) {
        self.tval = virt_addr;
        return Trap.StoreAccessMisaligned;
    }

    const phys_addr = self.translateAddr(virt_addr, bytes, .w) catch |trap| {
        self.tval = virt_addr;
        switch (trap) {
            error.AccessFault => return Trap.StoreAccessFault,
            error.PageFault => return Trap.StorePageFault,
        }
    };

    if (self.memory.storeConditional(self, bits, phys_addr, value)) |result| {
        return result;
    } else |trap| {
        self.tval = virt_addr;
        return trap;
    }
}

pub fn effectivePrivilege(self: *Self, access_type: AccessType) u2 {
    if (access_type != .x and self.mcsr.mstatus.get(.mprv) == 1) {
        return self.mcsr.mstatus.get(.mpp);
    }
    return self.priv;
}

pub fn checkPmp(self: *Self, addr: u64, len: u64, access_type: AccessType) bool {
    if (self.mcsr.pmpmap.get(addr, len)) |access| {
        if (access.locked or self.effectivePrivilege(access_type) != riscv.PRIVILEGE.m) {
            switch (access_type) {
                .r => return access.read,
                .w => return access.write,
                .x => return access.execute,
            }
        } else {
            return true;
        }
    }
    // Access not in a valid PMP region; reject
    return false;
}

pub const Trap = error{
    InstructionMisaligned,
    InstructionAccessFault,
    IllegalInstruction,
    Breakpoint,
    LoadAccessMisaligned,
    LoadAccessFault,
    StoreAccessMisaligned,
    StoreAccessFault,
    EnvironmentCall,
    InstructionPageFault,
    LoadPageFault,
    StorePageFault,
};

fn handle_trap(self: *Self, trap: Trap, instr: ?u64) void {
    @branchHint(.cold);
    switch (trap) {
        Trap.InstructionMisaligned => {
            self.trigger_exception(riscv.EXCEPTION.iam);
        },
        Trap.InstructionAccessFault => {
            self.trigger_exception(riscv.EXCEPTION.iaf);
        },
        Trap.IllegalInstruction => {
            self.tval = instr.?;
            self.trigger_exception(riscv.EXCEPTION.ill);
        },
        Trap.Breakpoint => {
            self.tval = self.pc;
            self.trigger_exception(riscv.EXCEPTION.brk);
        },
        Trap.LoadAccessMisaligned => {
            self.trigger_exception(riscv.EXCEPTION.lam);
        },
        Trap.LoadAccessFault => {
            self.trigger_exception(riscv.EXCEPTION.laf);
        },
        Trap.StoreAccessMisaligned => {
            self.trigger_exception(riscv.EXCEPTION.sam);
        },
        Trap.StoreAccessFault => {
            self.trigger_exception(riscv.EXCEPTION.saf);
        },
        Trap.EnvironmentCall => {
            self.trigger_exception(switch (self.priv) {
                riscv.PRIVILEGE.u => riscv.EXCEPTION.envu,
                riscv.PRIVILEGE.s => riscv.EXCEPTION.envs,
                riscv.PRIVILEGE.m => riscv.EXCEPTION.envm,
                else => unreachable,
            });
        },
        Trap.InstructionPageFault => {
            self.trigger_exception(riscv.EXCEPTION.ipf);
        },
        Trap.LoadPageFault => {
            self.trigger_exception(riscv.EXCEPTION.lpf);
        },
        Trap.StorePageFault => {
            self.trigger_exception(riscv.EXCEPTION.spf);
        },
    }
}

fn trigger_exception(self: *Self, exception: u64) void {
    const exception_code: u4 = @truncate(exception);
    const is_interrupt = exception - exception_code != 0;

    const edeleg = self.mcsr.medeleg.read() & (@as(u16, 1) << exception_code) != 0;
    const ideleg = self.mcsr.mideleg.read() & (@as(u16, 1) << exception_code) != 0;
    const target_priv: u2 = if (is_interrupt and ideleg or !is_interrupt and edeleg) riscv.PRIVILEGE.s else riscv.PRIVILEGE.m;

    if (self.priv <= riscv.PRIVILEGE.s and target_priv == riscv.PRIVILEGE.s) {
        self.mcsr.mstatus.set(.spp, @truncate(self.priv));
        self.priv = riscv.PRIVILEGE.s;
        self.mcsr.mstatus.set(.spie, self.mcsr.mstatus.get(.sie));
        self.mcsr.mstatus.set(.sie, 0);

        self.mcsr.scause.set(.scause, exception);
        self.mcsr.sepc.set(.sepc, self.getPc());
        self.mcsr.stval.set(.stval, self.tval);
        self.tval = 0;

        switch (self.mcsr.stvec.get(.mode)) {
            0 => self.setPc(@as(u64, self.mcsr.stvec.get(.base)) << 2),
            1 => self.setPc((@as(u64, self.mcsr.stvec.get(.base)) + exception) << 2),
            else => unreachable,
        }
    } else {
        self.mcsr.mstatus.set(.mpp, self.priv);
        self.priv = riscv.PRIVILEGE.m;
        self.mcsr.mstatus.set(.mpie, self.mcsr.mstatus.get(.mie));
        self.mcsr.mstatus.set(.mie, 0);

        self.mcsr.mcause.set(.mcause, exception);
        self.mcsr.mepc.set(.mepc, self.getPc());
        self.mcsr.mtval.set(.mtval, self.tval);
        self.tval = 0;

        switch (self.mcsr.mtvec.get(.mode)) {
            0 => self.setPc(@as(u64, self.mcsr.mtvec.get(.base)) << 2),
            1 => self.setPc((@as(u64, self.mcsr.mtvec.get(.base)) + exception) << 2),
            else => unreachable,
        }
    }

    self.instruction_frame.frame = null;
}

fn triggerInterrupts(self: *Self) void {
    const mip = self.controller.mip.load(.acquire);
    const m_interrupt = @ctz(mip & self.mcsr.mie.read() & ~self.mcsr.mideleg.read());
    const s_interrupt = @ctz(mip & self.mcsr.mie.read() & self.mcsr.mideleg.read());
    if ((self.priv < riscv.PRIVILEGE.m or self.mcsr.mstatus.get(.mie) == 1) and m_interrupt != 64) {
        self.trigger_exception(m_interrupt | @as(u64, 1) << 63);
    } else if ((self.priv < riscv.PRIVILEGE.s or self.priv == riscv.PRIVILEGE.s and self.mcsr.mstatus.get(.sie) == 1) and s_interrupt != 64) {
        self.trigger_exception(s_interrupt | @as(u64, 1) << 63);
    }
}

fn loadNextInstr(self: *Self) ?u64 {
    if (!self.controller.running.load(.monotonic)) {
        @branchHint(.cold);
        return null;
    }

    if (self.interrupt_countdown == 0) {
        self.triggerInterrupts();
        self.interrupt_countdown = 100;
    } else {
        self.interrupt_countdown -= 1;
    }

    while (true) {
        self.mcsr.step_cycle();
        if (self.loadInstr(self.pc)) |instr| {
            return instr;
        } else |trap| {
            self.handle_trap(trap, null);
        }
    }
}

pub fn run(self: *Self) void {
    var instr = self.loadNextInstr() orelse return;

    loop: switch (@as(u2, @truncate(instr))) {
        0b00 => {
            self.c0(@truncate(instr)) catch |trap| self.handle_trap(trap, instr);
            instr = self.loadNextInstr() orelse return;
            continue :loop @as(u2, @truncate(instr));
        },
        0b01 => {
            self.c1(@truncate(instr)) catch |trap| self.handle_trap(trap, instr);
            instr = self.loadNextInstr() orelse return;
            continue :loop @as(u2, @truncate(instr));
        },
        0b10 => {
            self.c2(@truncate(instr)) catch |trap| self.handle_trap(trap, instr);
            instr = self.loadNextInstr() orelse return;
            continue :loop @as(u2, @truncate(instr));
        },
        0b11 => {
            switch (@as(u7, @truncate(instr))) {
                riscv.OPCODE.load => self.load(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.load_fp => self.load_fp(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.misc_mem => self.misc_mem(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.op_imm => self.op_imm(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.auipc => self.auipc(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.op_imm_32 => self.op_imm_32(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.store => self.store(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.store_fp => self.store_fp(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.amo => self.amo(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.op => self.op(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.lui => self.lui(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.op_32 => self.op_32(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.fmadd...riscv.OPCODE.fnmsub => self.fmadd(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.op_fp => self.op_fp(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.branch => self.branch(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.jalr => self.jalr(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.jal => self.jal(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                riscv.OPCODE.system => self.system(@truncate(instr)) catch |trap| self.handle_trap(trap, instr),
                else => self.handle_trap(Trap.IllegalInstruction, instr),
            }
            instr = self.loadNextInstr() orelse return;
            continue :loop @as(u2, @truncate(instr));
        },
    }
}

pub fn c0(self: *Self, encoded_instr: u16) Trap!void {
    const ciw: riscv.CIWType = @bitCast(encoded_instr);
    const cl: riscv.CLType = @bitCast(encoded_instr);

    switch (ciw.funct3) {
        riscv.C0.addi4spn => {
            const imm = ciw.imm_addi4spn();
            if (imm == 0) {
                return Trap.IllegalInstruction;
            }
            self.setUnsigned(ciw.rd(), self.getUnsigned(2) +% imm);
        },
        riscv.C0.fld => self.setDouble(cl.rd(), try self.loadUnsigned(64, self.getUnsigned(cl.rs1()) +% cl.offset_ld())),
        riscv.C0.lw => self.setSigned(cl.rd(), try self.loadSigned(32, self.getUnsigned(cl.rs1()) +% cl.offset_lw())),
        riscv.C0.ld => self.setUnsigned(cl.rd(), try self.loadUnsigned(64, self.getUnsigned(cl.rs1()) +% cl.offset_ld())),
        riscv.C0.fsd => try self.storeUnsigned(64, self.getUnsigned(cl.rs1()) +% cl.offset_ld(), self.getDouble(cl.rd())),
        riscv.C0.sw => try self.storeUnsigned(32, self.getUnsigned(cl.rs1()) +% cl.offset_lw(), @truncate(self.getUnsigned(cl.rd()))),
        riscv.C0.sd => try self.storeUnsigned(64, self.getUnsigned(cl.rs1()) +% cl.offset_ld(), @truncate(self.getUnsigned(cl.rd()))),
        else => return Trap.IllegalInstruction,
    }

    self.pc += 2;
    self.mcsr.step_instret();
}

pub fn c1(self: *Self, encoded_instr: u16) Trap!void {
    const ci: riscv.CIType = @bitCast(encoded_instr);
    const ca: riscv.CAType = @bitCast(encoded_instr);
    const cb: riscv.CBType = @bitCast(encoded_instr);
    const cj: riscv.CJType = @bitCast(encoded_instr);

    switch (cb.funct3) {
        riscv.C1.addi => {
            self.setUnsigned(ci.rs1, self.getUnsigned(ci.rs1) +% ci.immSgnExt());
            self.pc += 2;
        },
        riscv.C1.addiw => {
            self.setSigned(ci.rs1, @as(i32, @bitCast(
                @as(u32, @truncate(self.getUnsigned(ci.rs1))) +% @as(u32, @truncate(ci.immSgnExt())),
            )));
            self.pc += 2;
        },
        riscv.C1.li => {
            self.setUnsigned(ci.rs1, ci.immSgnExt());
            self.pc += 2;
        },
        riscv.C1.lui => {
            if (ci.rs1 == 2) {
                const imm = ci.imm_addi16sp();
                if (imm == 0) {
                    return Trap.IllegalInstruction;
                }
                self.setUnsigned(2, self.getUnsigned(2) +% imm);
            } else {
                const imm = ci.immSgnExt() << 12;
                if (imm == 0) {
                    return Trap.IllegalInstruction;
                }
                self.setUnsigned(ci.rs1, imm);
            }
            self.pc += 2;
        },
        riscv.C1.arith => {
            switch (@as(u2, @truncate(ca.funct6 & 0b11))) {
                riscv.ARITH_H.srli => self.setUnsigned(cb.rs1(), self.getUnsigned(cb.rs1()) >> cb.shamt()),
                riscv.ARITH_H.srai => {
                    const shamt = cb.shamt();

                    // Perform the shift
                    const orig = self.getUnsigned(cb.rs1());
                    const result = @shrExact(orig & ~((@as(u64, 1) << shamt) - 1), shamt);

                    // Fill in the upper bits with a sign extension, if necessary
                    const upper_bit = @intFromBool(orig & 0x8000000000000000 != 0);
                    self.setUnsigned(cb.rs1(), result | ~((@as(u64, upper_bit) << ~shamt) -% 1));
                },
                riscv.ARITH_H.andi => self.setUnsigned(cb.rs1(), self.getUnsigned(cb.rs1()) & cb.immSgnExt()),
                riscv.ARITH_H.l => if (ca.funct6 & 0b100 == 0) {
                    switch (ca.funct2) {
                        riscv.ARITH_L.sub => self.setUnsigned(
                            ca.rs1(),
                            self.getUnsigned(ca.rs1()) -% self.getUnsigned(ca.rs2()),
                        ),
                        riscv.ARITH_L.xor => self.setUnsigned(
                            ca.rs1(),
                            self.getUnsigned(ca.rs1()) ^ self.getUnsigned(ca.rs2()),
                        ),
                        riscv.ARITH_L.@"or" => self.setUnsigned(
                            ca.rs1(),
                            self.getUnsigned(ca.rs1()) | self.getUnsigned(ca.rs2()),
                        ),
                        riscv.ARITH_L.@"and" => self.setUnsigned(
                            ca.rs1(),
                            self.getUnsigned(ca.rs1()) & self.getUnsigned(ca.rs2()),
                        ),
                    }
                } else {
                    switch (ca.funct2) {
                        riscv.ARITH_L.sub => self.setSigned(ca.rs1(), @as(i32, @bitCast(
                            @as(u32, @truncate(self.getUnsigned(ca.rs1()))) -% @as(u32, @truncate(self.getUnsigned(ca.rs2()))),
                        ))),
                        riscv.ARITH_L.xor => self.setSigned(ca.rs1(), @as(i32, @bitCast(
                            @as(u32, @truncate(self.getUnsigned(ca.rs1()))) +% @as(u32, @truncate(self.getUnsigned(ca.rs2()))),
                        ))),
                        else => return Trap.IllegalInstruction,
                    }
                },
            }
            self.pc += 2;
        },
        riscv.C1.j => {
            self.setPc(self.getPc() +% cj.offset());
        },
        riscv.C1.beqz => {
            if (self.getUnsigned(cb.rs1()) == 0) {
                self.setPc(self.getPc() +% cb.offset());
            } else {
                self.pc += 2;
            }
        },
        riscv.C1.bnez => {
            if (self.getUnsigned(cb.rs1()) != 0) {
                self.setPc(self.getPc() +% cb.offset());
            } else {
                self.pc += 2;
            }
        },
    }

    self.mcsr.step_instret();
}

pub fn c2(self: *Self, encoded_instr: u16) Trap!void {
    const cr: riscv.CRType = @bitCast(encoded_instr);
    const ci: riscv.CIType = @bitCast(encoded_instr);
    const css: riscv.CSSType = @bitCast(encoded_instr);

    switch (ci.funct3) {
        riscv.C2.slli => {
            self.setUnsigned(ci.rs1, self.getUnsigned(ci.rs1) << ci.imm());
            self.pc += 2;
        },
        riscv.C2.fldsp => {
            self.setDouble(ci.rs1, try self.loadUnsigned(64, self.getUnsigned(2) +% ci.offset_ld()));
            self.pc += 2;
        },
        riscv.C2.lwsp => {
            if (ci.rs1 == 0) {
                return Trap.IllegalInstruction;
            }
            self.setSigned(ci.rs1, try self.loadSigned(32, self.getUnsigned(2) +% ci.offset_lw()));
            self.pc += 2;
        },
        riscv.C2.ldsp => {
            if (ci.rs1 == 0) {
                return Trap.IllegalInstruction;
            }
            self.setUnsigned(ci.rs1, try self.loadUnsigned(64, self.getUnsigned(2) +% ci.offset_ld()));
            self.pc += 2;
        },
        riscv.C2.j => {
            if (cr.funct4 == 0b1000) {
                if (cr.rs2 == 0) {
                    // C.JR
                    if (cr.rs1 == 0) {
                        return Trap.IllegalInstruction;
                    }
                    self.setPc(self.getUnsigned(cr.rs1) & ~@as(u64, 1));
                } else {
                    // C.MV
                    self.setUnsigned(cr.rs1, self.getUnsigned(cr.rs2));
                    self.pc += 2;
                }
            } else {
                if (cr.rs2 == 0) {
                    if (cr.rs1 == 0) {
                        // C.EBREAK
                        return Trap.Breakpoint;
                    } else {
                        // C.JALR
                        const ra = self.getPc() + 2;
                        self.setPc(self.getUnsigned(cr.rs1) & ~@as(u64, 1));
                        self.setUnsigned(1, ra);
                    }
                } else {
                    // C.ADD
                    self.setUnsigned(cr.rs1, self.getUnsigned(cr.rs1) +% self.getUnsigned(cr.rs2));
                    self.pc += 2;
                }
            }
        },
        riscv.C2.fsdsp => {
            try self.storeUnsigned(64, self.getUnsigned(2) +% css.offset_sd(), self.getDouble(css.rs2));
            self.pc += 2;
        },
        riscv.C2.swsp => {
            try self.storeUnsigned(32, self.getUnsigned(2) +% css.offset_sw(), @truncate(self.getUnsigned(css.rs2)));
            self.pc += 2;
        },
        riscv.C2.sdsp => {
            try self.storeUnsigned(64, self.getUnsigned(2) +% css.offset_sd(), self.getUnsigned(css.rs2));
            self.pc += 2;
        },
    }

    self.mcsr.step_instret();
}

pub fn load(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.IType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.load);

    self.setUnsigned(instr.rd, switch (instr.func3) {
        riscv.LOAD.lb => @bitCast(@as(i64, try self.loadSigned(8, self.getUnsigned(instr.rs1) +% instr.immSgnExt()))),
        riscv.LOAD.lh => @bitCast(@as(i64, try self.loadSigned(16, self.getUnsigned(instr.rs1) +% instr.immSgnExt()))),
        riscv.LOAD.lw => @bitCast(@as(i64, try self.loadSigned(32, self.getUnsigned(instr.rs1) +% instr.immSgnExt()))),
        riscv.LOAD.ld => try self.loadUnsigned(64, self.getUnsigned(instr.rs1) +% instr.immSgnExt()),
        riscv.LOAD.lbu => try self.loadUnsigned(8, self.getUnsigned(instr.rs1) +% instr.immSgnExt()),
        riscv.LOAD.lhu => try self.loadUnsigned(16, self.getUnsigned(instr.rs1) +% instr.immSgnExt()),
        riscv.LOAD.lwu => try self.loadUnsigned(32, self.getUnsigned(instr.rs1) +% instr.immSgnExt()),
        else => return Trap.IllegalInstruction,
    });

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn load_fp(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.IType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.load_fp);

    if (self.mcsr.mstatus.get(.fs) == 0b00) {
        return Trap.IllegalInstruction;
    }
    // Mark FPU dirty
    self.mcsr.mstatus.set(.fs, 0b11);

    switch (instr.func3) {
        riscv.LOAD_FP.lw => self.setFloat(
            instr.rd,
            try self.loadUnsigned(32, self.getUnsigned(instr.rs1) +% instr.immSgnExt()),
        ),
        riscv.LOAD_FP.ld => self.setDouble(
            instr.rd,
            try self.loadUnsigned(64, self.getUnsigned(instr.rs1) +% instr.immSgnExt()),
        ),
        else => return Trap.IllegalInstruction,
    }

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn misc_mem(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.IType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.misc_mem);

    switch (instr.func3) {
        riscv.MISC_MEM.fence, riscv.MISC_MEM.fence_i => {
            // Nothing to do
        },
        else => return Trap.IllegalInstruction,
    }

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn op_imm(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.IType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.op_imm);

    self.setUnsigned(instr.rd, switch (instr.func3) {
        riscv.OP_IMM.addi => @bitCast(self.getSigned(instr.rs1) +% instr.imm),
        riscv.OP_IMM.slti => @intFromBool(self.getSigned(instr.rs1) < instr.imm),
        riscv.OP_IMM.sltiu => @intFromBool(self.getUnsigned(instr.rs1) < instr.immSgnExt()),
        riscv.OP_IMM.xori => self.getUnsigned(instr.rs1) ^ instr.immSgnExt(),
        riscv.OP_IMM.ori => self.getUnsigned(instr.rs1) | instr.immSgnExt(),
        riscv.OP_IMM.andi => self.getUnsigned(instr.rs1) & instr.immSgnExt(),
        riscv.OP_IMM.slli => a: {
            const imm_split = instr.immShift64();
            if (imm_split.h != 0) {
                return Trap.IllegalInstruction;
            }

            const imm = imm_split.l;
            break :a @shlWithOverflow(self.getUnsigned(instr.rs1), imm)[0];
        },
        riscv.OP_IMM.srli => a: {
            const imm_split = instr.immShift64();
            if (imm_split.h & 0b101111 != 0) {
                return Trap.IllegalInstruction;
            }

            const imm = imm_split.l;

            // Perform the shift
            const orig = self.getUnsigned(instr.rs1);
            const result = @shrExact(orig & ~((@as(u64, 1) << imm) - 1), imm);

            // Fill in the upper bits with a sign extension, if necessary
            const upper_bit = @intFromBool(imm_split.h != 0 and orig & 0x8000000000000000 != 0);
            break :a result | ~((@as(u64, upper_bit) << ~imm) -% 1);
        },
    });

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn auipc(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.UType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.auipc);

    self.setUnsigned(instr.rd, self.getPc() +% @as(u64, @bitCast(@as(i64, @as(i32, instr.imm) << 12))));
    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn op_imm_32(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.IType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.op_imm_32);

    self.setSigned(instr.rd, @as(i32, switch (instr.func3) {
        riscv.OP_IMM_32.addiw => @as(i32, @truncate(self.getSigned(instr.rs1))) +% instr.imm,
        riscv.OP_IMM_32.slliw => a: {
            const imm_split = instr.immShift32();
            if (imm_split.h != 0) {
                return Trap.IllegalInstruction;
            }

            const imm = imm_split.l;
            break :a @bitCast(@shlWithOverflow(@as(u32, @truncate(self.getUnsigned(instr.rs1))), imm)[0]);
        },
        riscv.OP_IMM_32.srliw => a: {
            const imm_split = instr.immShift32();
            if (imm_split.h & 0b1011111 != 0) {
                return Trap.IllegalInstruction;
            }

            const imm = imm_split.l;

            // Perform the shift
            const orig: u32 = @truncate(self.getUnsigned(instr.rs1));
            const result = @shrExact(orig & ~((@as(u32, 1) << imm) - 1), imm);

            // Fill in the upper bits with a sign extension, if necessary
            const upper_bit = @intFromBool(imm_split.h != 0 and orig & 0x80000000 != 0);
            break :a @bitCast(result | ~((@as(u32, upper_bit) << ~imm) -% 1));
        },
        else => {
            return Trap.IllegalInstruction;
        },
    }));

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn store(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.store);

    const addr = self.getUnsigned(instr.rs1) +% instr.immSgnExt();
    switch (instr.func3) {
        riscv.STORE.sb => try self.storeUnsigned(8, addr, @truncate(self.getUnsigned(instr.rs2))),
        riscv.STORE.sh => try self.storeUnsigned(16, addr, @truncate(self.getUnsigned(instr.rs2))),
        riscv.STORE.sw => try self.storeUnsigned(32, addr, @truncate(self.getUnsigned(instr.rs2))),
        riscv.STORE.sd => try self.storeUnsigned(64, addr, @truncate(self.getUnsigned(instr.rs2))),
        else => return Trap.IllegalInstruction,
    }

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn store_fp(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.store_fp);

    if (self.mcsr.mstatus.get(.fs) == 0b00) {
        return Trap.IllegalInstruction;
    }
    // Mark FPU dirty
    self.mcsr.mstatus.set(.fs, 0b11);

    const addr = self.getUnsigned(instr.rs1) +% instr.immSgnExt();
    switch (instr.func3) {
        riscv.STORE.sw => try self.storeUnsigned(32, addr, self.getFloatRaw(instr.rs2)),
        riscv.STORE.sd => try self.storeUnsigned(64, addr, self.getDouble(instr.rs2)),
        else => return Trap.IllegalInstruction,
    }

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn amo(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.amo);

    switch (instr.amoFunct7().func5) {
        riscv.AMO.lr => {
            if (instr.rs2 != 0) {
                return Trap.IllegalInstruction;
            }
            self.setUnsigned(instr.rd, @bitCast(switch (instr.func3) {
                riscv.AMO_WIDTH.w => @as(i64, try self.loadReserved(32, self.getUnsigned(instr.rs1))),
                riscv.AMO_WIDTH.d => try self.loadReserved(64, self.getUnsigned(instr.rs1)),
                else => return Trap.IllegalInstruction,
            }));
        },
        riscv.AMO.sc => {
            const addr = self.getUnsigned(instr.rs1);
            const success = switch (instr.func3) {
                riscv.AMO_WIDTH.w => try self.storeConditional(32, addr, @truncate(self.getUnsigned(instr.rs2))),
                riscv.AMO_WIDTH.d => try self.storeConditional(64, addr, self.getUnsigned(instr.rs2)),
                else => return Trap.IllegalInstruction,
            };
            self.setUnsigned(instr.rd, if (success) 0 else 1);
        },
        riscv.AMO.amoswap,
        riscv.AMO.amoadd,
        riscv.AMO.amoxor,
        riscv.AMO.amoand,
        riscv.AMO.amoor,
        riscv.AMO.amomin,
        riscv.AMO.amomax,
        riscv.AMO.amominu,
        riscv.AMO.amomaxu,
        => {
            const addr = self.getUnsigned(instr.rs1);
            const orig = self.getUnsigned(instr.rs2);

            switch (instr.func3) {
                riscv.AMO_WIDTH.w => {
                    const phys_addr = self.translateAddr(addr, 4, .w) catch |trap| {
                        self.tval = addr;
                        switch (trap) {
                            error.AccessFault => return Trap.StoreAccessFault,
                            error.PageFault => return Trap.StorePageFault,
                        }
                    };

                    const value = self.memory.amoWord(
                        self,
                        phys_addr,
                        Memory.AmoKind.fromRiscv(instr.amoFunct7().func5) orelse return Trap.IllegalInstruction,
                        @truncate(orig),
                        if (instr.amoFunct7().aq == 1)
                            if (instr.amoFunct7().rl == 1)
                                .acq_rel
                            else
                                .acquire
                        else if (instr.amoFunct7().rl == 1)
                            .release
                        else
                            .monotonic,
                    ) catch |trap| {
                        self.tval = addr;
                        return trap;
                    };
                    self.setSigned(instr.rd, @as(i32, @bitCast(value)));
                },
                riscv.AMO_WIDTH.d => {
                    const phys_addr = self.translateAddr(addr, 8, .w) catch |trap| {
                        self.tval = addr;
                        switch (trap) {
                            error.AccessFault => return Trap.StoreAccessFault,
                            error.PageFault => return Trap.StorePageFault,
                        }
                    };

                    const value = self.memory.amoDoubleWord(
                        self,
                        phys_addr,
                        Memory.AmoKind.fromRiscv(instr.amoFunct7().func5) orelse return Trap.IllegalInstruction,
                        orig,
                        if (instr.amoFunct7().aq == 1)
                            if (instr.amoFunct7().rl == 1)
                                .acq_rel
                            else
                                .acquire
                        else if (instr.amoFunct7().rl == 1)
                            .release
                        else
                            .monotonic,
                    ) catch |trap| {
                        self.tval = addr;
                        return trap;
                    };

                    self.setUnsigned(instr.rd, value);
                },
                else => return Trap.IllegalInstruction,
            }
        },
        else => return Trap.IllegalInstruction,
    }

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn op(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.op);

    self.setUnsigned(
        instr.rd,
        switch (instr.func7) {
            riscv.OP.int => switch (instr.func3) {
                riscv.OP_INT.add => self.getUnsigned(instr.rs1) +% self.getUnsigned(instr.rs2),
                riscv.OP_INT.slt => @intFromBool(self.getSigned(instr.rs1) < self.getSigned(instr.rs2)),
                riscv.OP_INT.sltu => @intFromBool(self.getUnsigned(instr.rs1) < self.getUnsigned(instr.rs2)),
                riscv.OP_INT.xor => self.getUnsigned(instr.rs1) ^ self.getUnsigned(instr.rs2),
                riscv.OP_INT.@"or" => self.getUnsigned(instr.rs1) | self.getUnsigned(instr.rs2),
                riscv.OP_INT.@"and" => self.getUnsigned(instr.rs1) & self.getUnsigned(instr.rs2),
                riscv.OP_INT.sll => @shlWithOverflow(self.getUnsigned(instr.rs1), @as(u6, @truncate(self.getUnsigned(instr.rs2))))[0],
                riscv.OP_INT.srl => a: {
                    const shift = @as(u6, @truncate(self.getUnsigned(instr.rs2)));
                    const orig = self.getUnsigned(instr.rs1);
                    break :a @shrExact(orig & ~((@as(u64, 1) << shift) - 1), shift);
                },
            },
            riscv.OP.muldiv => switch (instr.func3) {
                riscv.OP_MULDIV.mul => self.getUnsigned(instr.rs1) *% self.getUnsigned(instr.rs2),
                riscv.OP_MULDIV.mulh => @bitCast(@as(i64, @truncate(@as(i128, self.getSigned(instr.rs1)) * @as(i128, self.getSigned(instr.rs2)) >> 64))),
                riscv.OP_MULDIV.mulhsu => @bitCast(@as(i64, @truncate(@as(i128, self.getSigned(instr.rs1)) * @as(i128, self.getUnsigned(instr.rs2)) >> 64))),
                riscv.OP_MULDIV.mulhu => @bitCast(@as(u64, @truncate(@as(u128, self.getUnsigned(instr.rs1)) * @as(u128, self.getUnsigned(instr.rs2)) >> 64))),
                riscv.OP_MULDIV.div => blk: {
                    const dividend = self.getSigned(instr.rs1);
                    const divisor = self.getSigned(instr.rs2);

                    if (divisor == 0) {
                        break :blk std.math.maxInt(u64);
                    }
                    if (dividend == std.math.minInt(i64) and divisor == -1) {
                        break :blk @bitCast(@as(i64, std.math.minInt(i64)));
                    }
                    break :blk @bitCast(@divTrunc(dividend, divisor));
                },
                riscv.OP_MULDIV.divu => blk: {
                    const dividend = self.getUnsigned(instr.rs1);
                    const divisor = self.getUnsigned(instr.rs2);

                    if (divisor == 0) {
                        break :blk std.math.maxInt(u64);
                    }
                    break :blk dividend / divisor;
                },
                riscv.OP_MULDIV.rem => blk: {
                    const dividend = self.getSigned(instr.rs1);
                    const divisor = self.getSigned(instr.rs2);

                    if (divisor == 0) {
                        break :blk @bitCast(dividend);
                    }
                    if (dividend == std.math.minInt(i64) and divisor == -1) {
                        break :blk 0;
                    }
                    break :blk @bitCast(@rem(dividend, divisor));
                },
                riscv.OP_MULDIV.remu => blk: {
                    const dividend = self.getUnsigned(instr.rs1);
                    const divisor = self.getUnsigned(instr.rs2);

                    if (divisor == 0) {
                        break :blk dividend;
                    }
                    break :blk dividend % divisor;
                },
            },
            riscv.OP.neg => switch (instr.func3) {
                riscv.OP_NEG.sub => self.getUnsigned(instr.rs1) -% self.getUnsigned(instr.rs2),
                riscv.OP_NEG.sra => a: {
                    // Perform the shift
                    const shift = @as(u6, @truncate(self.getUnsigned(instr.rs2)));
                    const orig = self.getUnsigned(instr.rs1);
                    const result = @shrExact(orig & ~((@as(u64, 1) << shift) - 1), shift);

                    // Fill in the upper bits with a sign extension
                    const upper_bit = @intFromBool(orig & 0x8000000000000000 != 0);
                    break :a result | ~((@as(u64, upper_bit) << ~shift) -% 1);
                },
                else => return Trap.IllegalInstruction,
            },
            else => return Trap.IllegalInstruction,
        },
    );

    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn lui(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.UType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.lui);

    self.setUnsigned(instr.rd, @bitCast(@as(i64, @as(i32, instr.imm) << 12)));
    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn op_32(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.op_32);

    self.setSigned(
        instr.rd,
        @as(i32, switch (instr.func7) {
            riscv.OP_32.int => switch (instr.func3) {
                riscv.OP_32_INT.addw => @bitCast(@as(u32, @truncate(self.getUnsigned(instr.rs1))) +% @as(u32, @truncate(self.getUnsigned(instr.rs2)))),
                riscv.OP_32_INT.sllw => @bitCast(@shlWithOverflow(@as(u32, @truncate(self.getUnsigned(instr.rs1))), @as(u5, @truncate(self.getUnsigned(instr.rs2))))[0]),
                riscv.OP_32_INT.srlw => a: {
                    const shift = @as(u5, @truncate(self.getUnsigned(instr.rs2)));
                    const orig: u32 = @truncate(self.getUnsigned(instr.rs1));
                    break :a @bitCast(@shrExact(orig & ~((@as(u32, 1) << shift) - 1), shift));
                },
                else => return Trap.IllegalInstruction,
            },
            riscv.OP_32.muldiv => switch (instr.func3) {
                riscv.OP_32_MULDIV.mulw => @bitCast(@as(i32, @truncate(self.getSigned(instr.rs1))) *% @as(i32, @truncate(self.getSigned(instr.rs2)))),
                riscv.OP_32_MULDIV.divw => @bitCast(blk: {
                    const dividend = @as(i32, @truncate(self.getSigned(instr.rs1)));
                    const divisor = @as(i32, @truncate(self.getSigned(instr.rs2)));

                    if (divisor == 0) {
                        break :blk -1;
                    }
                    if (dividend == std.math.minInt(i32) and divisor == -1) {
                        break :blk std.math.minInt(i32);
                    }
                    break :blk @divTrunc(dividend, divisor);
                }),
                riscv.OP_32_MULDIV.divuw => @bitCast(blk: {
                    const dividend = @as(u32, @truncate(self.getUnsigned(instr.rs1)));
                    const divisor = @as(u32, @truncate(self.getUnsigned(instr.rs2)));

                    if (divisor == 0) {
                        break :blk std.math.maxInt(u32);
                    }
                    break :blk dividend / divisor;
                }),
                riscv.OP_32_MULDIV.remw => @bitCast(blk: {
                    const dividend = @as(i32, @truncate(self.getSigned(instr.rs1)));
                    const divisor = @as(i32, @truncate(self.getSigned(instr.rs2)));

                    if (divisor == 0) {
                        break :blk dividend;
                    }
                    if (dividend == std.math.minInt(i32) and divisor == -1) {
                        break :blk 0;
                    }
                    break :blk @rem(dividend, divisor);
                }),
                riscv.OP_32_MULDIV.remuw => @bitCast(blk: {
                    const dividend = @as(u32, @truncate(self.getUnsigned(instr.rs1)));
                    const divisor = @as(u32, @truncate(self.getUnsigned(instr.rs2)));

                    if (divisor == 0) {
                        break :blk dividend;
                    }
                    break :blk dividend % divisor;
                }),
                else => return Trap.IllegalInstruction,
            },
            riscv.OP_32.neg => switch (instr.func3) {
                riscv.OP_32_NEG.subw => @bitCast(@as(u32, @truncate(self.getUnsigned(instr.rs1))) -% @as(u32, @truncate(self.getUnsigned(instr.rs2)))),
                riscv.OP_32_NEG.sraw => a: {
                    // Perform the shift
                    const shift = @as(u5, @truncate(self.getUnsigned(instr.rs2)));
                    const orig: u32 = @truncate(self.getUnsigned(instr.rs1));
                    const result = @shrExact(orig & ~((@as(u32, 1) << shift) - 1), shift);

                    // Fill in the upper bits with a sign extension
                    const upper_bit = @intFromBool(orig & 0x80000000 != 0);
                    break :a @bitCast(result | ~((@as(u32, upper_bit) << ~shift) -% 1));
                },
                else => return Trap.IllegalInstruction,
            },
            else => return Trap.IllegalInstruction,
        }),
    );
    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn roundingMode(self: *Self, rounding_mode: u3) Trap!u3 {
    switch (rounding_mode) {
        riscv.OP_FP_RM.rne,
        riscv.OP_FP_RM.rtz,
        riscv.OP_FP_RM.rdn,
        riscv.OP_FP_RM.rup,
        riscv.OP_FP_RM.rmm,
        => return rounding_mode,
        riscv.OP_FP_RM.dyn => {
            const dynamic_rounding_mode = self.mcsr.fcsr.get(.rm);
            switch (dynamic_rounding_mode) {
                riscv.OP_FP_RM.rne,
                riscv.OP_FP_RM.rtz,
                riscv.OP_FP_RM.rdn,
                riscv.OP_FP_RM.rup,
                riscv.OP_FP_RM.rmm,
                => return dynamic_rounding_mode,
                else => return Trap.IllegalInstruction,
            }
        },
        else => return Trap.IllegalInstruction,
    }
}

fn id_sf32(f: u32, _: u5, _: *u32) u32 {
    return f;
}

const sf32 = .{
    .Signed = i32,
    .Unsigned = u32,
    .sign_bit = @as(u32, 1 << 31),
    .load = getFloat,
    .get = getFloatRaw,
    .store = setFloat,
    .fma_f = softfp.fma_sf32,
    .add_f = softfp.add_sf32,
    .sub_f = softfp.sub_sf32,
    .mul_f = softfp.mul_sf32,
    .div_f = softfp.div_sf32,
    .sqrt_f = softfp.sqrt_sf32,
    .min_f = softfp.min_sf32,
    .max_f = softfp.max_sf32,
    .eq_quiet_f = softfp.eq_quiet_sf32,
    .lt_f = softfp.lt_sf32,
    .le_f = softfp.le_sf32,
    .fclass_f = softfp.fclass_sf32,
    .cvt_f_i32 = softfp.cvt_sf32_i32,
    .cvt_f_u32 = softfp.cvt_sf32_u32,
    .cvt_f_i64 = softfp.cvt_sf32_i64,
    .cvt_f_u64 = softfp.cvt_sf32_u64,
    .cvt_i32_f = softfp.cvt_i32_sf32,
    .cvt_u32_f = softfp.cvt_u32_sf32,
    .cvt_i64_f = softfp.cvt_i64_sf32,
    .cvt_u64_f = softfp.cvt_u64_sf32,
    .cvt_sf32_f = id_sf32,
    .cvt_sf64_f = softfp.cvt_sf64_sf32,
};

fn id_sf64(d: u64, _: u5, _: *u32) u64 {
    return d;
}

fn cvt_sf32_sf64(f: u32, _: u5, flags: *u32) u64 {
    return softfp.cvt_sf32_sf64(f, flags);
}

const sf64 = .{
    .Signed = i64,
    .Unsigned = u64,
    .sign_bit = @as(u64, 1 << 63),
    .load = getDouble,
    .get = getDouble,
    .store = setDouble,
    .fma_f = softfp.fma_sf64,
    .add_f = softfp.add_sf64,
    .sub_f = softfp.sub_sf64,
    .mul_f = softfp.mul_sf64,
    .div_f = softfp.div_sf64,
    .sqrt_f = softfp.sqrt_sf64,
    .min_f = softfp.min_sf64,
    .max_f = softfp.max_sf64,
    .eq_quiet_f = softfp.eq_quiet_sf64,
    .lt_f = softfp.lt_sf64,
    .le_f = softfp.le_sf64,
    .fclass_f = softfp.fclass_sf64,
    .cvt_f_i32 = softfp.cvt_sf64_i32,
    .cvt_f_u32 = softfp.cvt_sf64_u32,
    .cvt_f_i64 = softfp.cvt_sf64_i64,
    .cvt_f_u64 = softfp.cvt_sf64_u64,
    .cvt_i32_f = softfp.cvt_i32_sf64,
    .cvt_u32_f = softfp.cvt_u32_sf64,
    .cvt_i64_f = softfp.cvt_i64_sf64,
    .cvt_u64_f = softfp.cvt_u64_sf64,
    .cvt_sf32_f = cvt_sf32_sf64,
    .cvt_sf64_f = id_sf64,
};

fn fmadd_sf(self: *Self, comptime fp: anytype, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);

    // Mark FPU dirty
    self.mcsr.mstatus.set(.fs, 0b11);

    switch (instr.opcode) {
        riscv.OPCODE.fmadd => {
            fp.store(self, instr.rd, fp.fma_f(
                fp.load(self, instr.rs1),
                fp.load(self, instr.rs2),
                fp.load(self, instr.opFpFunct7().func5),
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            ));
        },
        riscv.OPCODE.fmsub => {
            fp.store(self, instr.rd, fp.fma_f(
                fp.load(self, instr.rs1),
                fp.load(self, instr.rs2),
                fp.load(self, instr.opFpFunct7().func5) ^ fp.sign_bit,
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            ));
        },
        riscv.OPCODE.fnmadd => {
            fp.store(self, instr.rd, fp.fma_f(
                fp.load(self, instr.rs1),
                fp.load(self, instr.rs2) ^ fp.sign_bit,
                fp.load(self, instr.opFpFunct7().func5),
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            ));
        },
        riscv.OPCODE.fnmsub => {
            fp.store(self, instr.rd, fp.fma_f(
                fp.load(self, instr.rs1),
                fp.load(self, instr.rs2) ^ fp.sign_bit,
                fp.load(self, instr.opFpFunct7().func5) ^ fp.sign_bit,
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            ));
        },
        else => unreachable,
    }
    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn fmadd(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);

    if (self.mcsr.mstatus.get(.fs) == 0b00) {
        return Trap.IllegalInstruction;
    }

    switch (instr.opFpFunct7().fmt) {
        riscv.OP_FP_FMT.s => return self.fmadd_sf(sf32, encoded_instr),
        riscv.OP_FP_FMT.d => return self.fmadd_sf(sf64, encoded_instr),
        else => return Trap.IllegalInstruction,
    }
    self.pc += 4;
    self.mcsr.step_instret();
}

fn op_fp_sf(self: *Self, comptime fp: anytype, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.op_fp);

    // Mark FPU dirty
    self.mcsr.mstatus.set(.fs, 0b11);

    switch (instr.opFpFunct7().func5) {
        riscv.OP_FP.fadd => {
            fp.store(self, instr.rd, (fp.add_f(
                fp.load(self, instr.rs1),
                fp.load(self, instr.rs2),
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            )));
        },
        riscv.OP_FP.fsub => {
            fp.store(self, instr.rd, fp.sub_f(
                fp.load(self, instr.rs1),
                fp.load(self, instr.rs2),
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            ));
        },
        riscv.OP_FP.fmul => {
            fp.store(self, instr.rd, fp.mul_f(
                fp.load(self, instr.rs1),
                fp.load(self, instr.rs2),
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            ));
        },
        riscv.OP_FP.fdiv => {
            fp.store(self, instr.rd, fp.div_f(
                fp.load(self, instr.rs1),
                fp.load(self, instr.rs2),
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            ));
        },
        riscv.OP_FP.fsgnj => switch (instr.func3) {
            riscv.OP_FP_SGNJ.j => fp.store(
                self,
                instr.rd,
                fp.load(self, instr.rs2) & fp.sign_bit // The sign bit from f[rs2]
                | (fp.load(self, instr.rs1) & fp.sign_bit - 1), // The rest from f[rs1]
            ),
            riscv.OP_FP_SGNJ.jn => fp.store(
                self,
                instr.rd,
                ~fp.load(self, instr.rs2) & fp.sign_bit // The inverted sign bit from f[rs2]
                | (fp.load(self, instr.rs1) & fp.sign_bit - 1), // The rest from f[rs1]
            ),
            riscv.OP_FP_SGNJ.jx => fp.store(
                self,
                instr.rd,
                fp.load(self, instr.rs2) & fp.sign_bit // The sign bit from f[rs2]
                ^ fp.load(self, instr.rs1),
            ),
            else => return Trap.IllegalInstruction,
        },
        riscv.OP_FP.fsqrt => {
            if (instr.rs2 != 0) {
                return Trap.IllegalInstruction;
            }
            fp.store(self, instr.rd, fp.sqrt_f(
                fp.load(self, instr.rs1),
                try self.roundingMode(instr.func3),
                @ptrCast(&self.mcsr.fcsr),
            ));
        },
        riscv.OP_FP.fminmax => {
            switch (instr.func3) {
                riscv.OP_FP_MINMAX.min => {
                    fp.store(self, instr.rd, fp.min_f(
                        fp.load(self, instr.rs1),
                        fp.load(self, instr.rs2),
                        @ptrCast(&self.mcsr.fcsr),
                    ));
                },
                riscv.OP_FP_MINMAX.max => {
                    fp.store(self, instr.rd, fp.max_f(
                        fp.load(self, instr.rs1),
                        fp.load(self, instr.rs2),
                        @ptrCast(&self.mcsr.fcsr),
                    ));
                },
                else => return Trap.IllegalInstruction,
            }
        },
        riscv.OP_FP.fcvt_sd => {
            switch (instr.rs2) {
                riscv.OP_FP_FMT.s => fp.store(self, instr.rd, fp.cvt_sf32_f(
                    self.getFloat(instr.rs1),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                riscv.OP_FP_FMT.d => fp.store(self, instr.rd, fp.cvt_sf64_f(
                    self.getDouble(instr.rs1),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                else => return Trap.IllegalInstruction,
            }
        },
        riscv.OP_FP.fcvt_i_f => {
            switch (instr.rs2) {
                riscv.OP_FP_CVT.w => self.setSigned(instr.rd, fp.cvt_f_i32(
                    fp.load(self, instr.rs1),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                riscv.OP_FP_CVT.wu => self.setSigned(instr.rd, @as(i32, @bitCast(fp.cvt_f_u32(
                    fp.load(self, instr.rs1),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )))),
                riscv.OP_FP_CVT.l => self.setSigned(instr.rd, fp.cvt_f_i64(
                    fp.load(self, instr.rs1),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                riscv.OP_FP_CVT.lu => self.setUnsigned(instr.rd, fp.cvt_f_u64(
                    fp.load(self, instr.rs1),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                else => return Trap.IllegalInstruction,
            }
        },
        riscv.OP_FP.fcmp => switch (instr.func3) {
            riscv.OP_FP_CMP.eq => {
                self.setUnsigned(instr.rd, @intCast(fp.eq_quiet_f(
                    fp.load(self, instr.rs1),
                    fp.load(self, instr.rs2),
                    @ptrCast(&self.mcsr.fcsr),
                )));
            },
            riscv.OP_FP_CMP.lt => {
                self.setUnsigned(instr.rd, @intCast(fp.lt_f(
                    fp.load(self, instr.rs1),
                    fp.load(self, instr.rs2),
                    @ptrCast(&self.mcsr.fcsr),
                )));
            },
            riscv.OP_FP_CMP.le => {
                self.setUnsigned(instr.rd, @intCast(fp.le_f(
                    fp.load(self, instr.rs1),
                    fp.load(self, instr.rs2),
                    @ptrCast(&self.mcsr.fcsr),
                )));
            },
            else => return Trap.IllegalInstruction,
        },
        riscv.OP_FP.fcvt_f_i => {
            switch (instr.rs2) {
                riscv.OP_FP_CVT.w => fp.store(self, instr.rd, fp.cvt_i32_f(
                    @truncate(self.getSigned(instr.rs1)),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                riscv.OP_FP_CVT.wu => fp.store(self, instr.rd, fp.cvt_u32_f(
                    @truncate(self.getUnsigned(instr.rs1)),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                riscv.OP_FP_CVT.l => fp.store(self, instr.rd, fp.cvt_i64_f(
                    self.getSigned(instr.rs1),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                riscv.OP_FP_CVT.lu => fp.store(self, instr.rd, fp.cvt_u64_f(
                    self.getUnsigned(instr.rs1),
                    try self.roundingMode(instr.func3),
                    @ptrCast(&self.mcsr.fcsr),
                )),
                else => return Trap.IllegalInstruction,
            }
        },
        riscv.OP_FP.fclass => switch (instr.func3) {
            riscv.OP_FP_CLASS.mv_x_w => {
                if (instr.rs2 != 0) {
                    return Trap.IllegalInstruction;
                }
                self.setSigned(instr.rd, @as(fp.Signed, @bitCast(fp.get(self, instr.rs1))));
            },
            riscv.OP_FP_CLASS.class => {
                if (instr.rs2 != 0) {
                    return Trap.IllegalInstruction;
                }
                self.setUnsigned(
                    instr.rd,
                    fp.fclass_f(fp.load(self, instr.rs1)),
                );
            },
            else => return Trap.IllegalInstruction,
        },
        riscv.OP_FP.fmv_w_x => {
            if (instr.rs2 != 0) {
                return Trap.IllegalInstruction;
            }
            fp.store(self, instr.rd, @bitCast(@as(fp.Unsigned, @truncate(self.getUnsigned(instr.rs1)))));
        },
        else => return Trap.IllegalInstruction,
    }
    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn op_fp(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.op_fp);

    if (self.mcsr.mstatus.get(.fs) == 0b00) {
        return Trap.IllegalInstruction;
    }

    switch (instr.opFpFunct7().fmt) {
        riscv.OP_FP_FMT.s => return self.op_fp_sf(sf32, encoded_instr),
        riscv.OP_FP_FMT.d => return self.op_fp_sf(sf64, encoded_instr),
        else => return Trap.IllegalInstruction,
    }
    self.pc += 4;
    self.mcsr.step_instret();
}

pub fn branch(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.RType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.branch);

    if (switch (instr.func3) {
        riscv.BRANCH.beq => self.getUnsigned(instr.rs1) == self.getUnsigned(instr.rs2),
        riscv.BRANCH.bne => self.getUnsigned(instr.rs1) != self.getUnsigned(instr.rs2),
        riscv.BRANCH.blt => self.getSigned(instr.rs1) < self.getSigned(instr.rs2),
        riscv.BRANCH.bge => self.getSigned(instr.rs1) >= self.getSigned(instr.rs2),
        riscv.BRANCH.bltu => self.getUnsigned(instr.rs1) < self.getUnsigned(instr.rs2),
        riscv.BRANCH.bgeu => self.getUnsigned(instr.rs1) >= self.getUnsigned(instr.rs2),
        else => return Trap.IllegalInstruction,
    }) {
        const new_pc = self.getPc() +% instr.immBType();
        if (new_pc & (IALIGN - 1) != 0) {
            return Trap.InstructionMisaligned;
        }
        self.setPc(new_pc);
    } else {
        self.pc += 4;
    }
    self.mcsr.step_instret();
}

pub fn jalr(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.IType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.jalr);
    if (instr.func3 != 0) {
        return Trap.IllegalInstruction;
    }

    const new_pc = (self.getUnsigned(instr.rs1) +% instr.immSgnExt()) & ~@as(u64, 1);
    if (new_pc & (IALIGN - 1) != 0) {
        return Trap.InstructionMisaligned;
    }
    const ra = self.getPc() + 4;
    self.setUnsigned(instr.rd, ra);
    self.setPc(new_pc);
    self.mcsr.step_instret();
}

pub fn jal(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.UType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.jal);

    const new_pc = self.getPc() +% instr.immJType();
    if (new_pc & (IALIGN - 1) != 0) {
        return Trap.InstructionMisaligned;
    }
    const ra = self.getPc() + 4;
    self.setUnsigned(instr.rd, ra);
    self.setPc(new_pc);
    self.mcsr.step_instret();
}

pub fn system(self: *Self, encoded_instr: u32) Trap!void {
    const instr: riscv.IType = @bitCast(encoded_instr);
    std.debug.assert(instr.opcode == riscv.OPCODE.system);

    switch (instr.func3) {
        riscv.SYSTEM.priv => {
            switch (instr.imm) {
                riscv.PRIV.ecall => {
                    return Trap.EnvironmentCall;
                },
                riscv.PRIV.ebreak => return Trap.Breakpoint,
                riscv.PRIV.sret => {
                    if (self.priv < riscv.PRIVILEGE.s) {
                        return Trap.IllegalInstruction;
                    }
                    if (self.mcsr.mstatus.get(.tsr) == 1) {
                        return Trap.IllegalInstruction;
                    }
                    self.setPc(self.mcsr.sepc.get(.sepc));
                    self.mcsr.mstatus.set(.sie, self.mcsr.mstatus.get(.spie));
                    self.priv = self.mcsr.mstatus.get(.spp);
                    self.mcsr.mstatus.set(.spie, 1);
                    self.mcsr.mstatus.set(.spp, riscv.PRIVILEGE.u);
                    if (self.priv != riscv.PRIVILEGE.m) {
                        self.mcsr.mstatus.set(.mprv, 0);
                    }
                    self.mcsr.step_instret();
                    return;
                },
                riscv.PRIV.mret => {
                    if (self.priv < riscv.PRIVILEGE.m) {
                        return Trap.IllegalInstruction;
                    }
                    self.setPc(self.mcsr.mepc.get(.mepc));
                    self.mcsr.mstatus.set(.mie, self.mcsr.mstatus.get(.mpie));
                    self.priv = self.mcsr.mstatus.get(.mpp);
                    self.mcsr.mstatus.set(.mpie, 1);
                    self.mcsr.mstatus.set(.mpp, riscv.PRIVILEGE.u);
                    if (self.priv != riscv.PRIVILEGE.m) {
                        self.mcsr.mstatus.set(.mprv, 0);
                    }
                    self.mcsr.step_instret();
                    return;
                },
                riscv.PRIV.wfi => {
                    switch (self.priv) {
                        riscv.PRIVILEGE.u => return Trap.IllegalInstruction,
                        riscv.PRIVILEGE.s => {
                            if (self.mcsr.mstatus.get(.tw) == 1) {
                                return Trap.IllegalInstruction;
                            }
                            self.controller.wfi();
                            self.pc += 4;
                            self.triggerInterrupts();
                            self.mcsr.step_instret();
                            return;
                        },
                        riscv.PRIVILEGE.m => {
                            self.controller.wfi();
                            self.pc += 4;
                            self.triggerInterrupts();
                            self.mcsr.step_instret();
                            return;
                        },
                        else => unreachable,
                    }
                },
                else => {
                    if (instr.imm >> 5 == riscv.PRIV.sfence_vma) {
                        if (self.mcsr.mstatus.get(.tvm) == 1) {
                            return Trap.IllegalInstruction;
                        }
                        self.instruction_frame.frame = null;
                        for (&self.read_tlb) |*tlb_entry| {
                            tlb_entry.valid = false;
                        }
                        for (&self.write_tlb) |*tlb_entry| {
                            tlb_entry.valid = false;
                        }
                        for (&self.instruction_tlb) |*tlb_entry| {
                            tlb_entry.valid = false;
                        }
                    } else {
                        return Trap.IllegalInstruction;
                    }
                },
            }
        },
        riscv.SYSTEM.csrrw => {
            const prev_value = try self.rmwCsr(instr.immUnsigned(), .Xchg, self.getUnsigned(instr.rs1));
            self.setUnsigned(instr.rd, if (instr.rd != 0) prev_value else 0);
        },
        riscv.SYSTEM.csrrs => {
            if (instr.rs1 != 0) {
                self.setUnsigned(
                    instr.rd,
                    try self.rmwCsr(instr.immUnsigned(), .Set, self.getUnsigned(instr.rs1)),
                );
            } else {
                self.setUnsigned(instr.rd, try self.readCsr(instr.immUnsigned()));
            }
        },
        riscv.SYSTEM.csrrc => {
            if (instr.rs1 != 0) {
                self.setUnsigned(
                    instr.rd,
                    try self.rmwCsr(instr.immUnsigned(), .Clear, self.getUnsigned(instr.rs1)),
                );
            } else {
                self.setUnsigned(instr.rd, try self.readCsr(instr.immUnsigned()));
            }
        },
        riscv.SYSTEM.csrrwi => {
            const prev_value = try self.rmwCsr(instr.immUnsigned(), .Xchg, instr.rs1);
            self.setUnsigned(instr.rd, if (instr.rd != 0) prev_value else 0);
        },
        riscv.SYSTEM.csrrsi => {
            if (instr.rs1 != 0) {
                self.setUnsigned(
                    instr.rd,
                    try self.rmwCsr(instr.immUnsigned(), .Set, instr.rs1),
                );
            } else {
                self.setUnsigned(instr.rd, try self.readCsr(instr.immUnsigned()));
            }
        },
        riscv.SYSTEM.csrrci => {
            if (instr.rs1 != 0) {
                self.setUnsigned(
                    instr.rd,
                    try self.rmwCsr(instr.immUnsigned(), .Clear, instr.rs1),
                );
            } else {
                self.setUnsigned(instr.rd, try self.readCsr(instr.immUnsigned()));
            }
        },
        else => return Trap.IllegalInstruction,
    }

    self.pc += 4;
    self.mcsr.step_instret();
}
