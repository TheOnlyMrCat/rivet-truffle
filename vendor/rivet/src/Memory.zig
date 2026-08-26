// SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");
const Hart = @import("Hart.zig");
const ffi = @import("lib.zig");
const riscv = @import("riscv.zig");
const RwLock = @import("RwLock.zig");

const Memory = @This();

pub const GranuleAddr = packed struct {
    sub: u3,
    granule: u61,
};

pub const GranularMemory = struct {
    memory: []std.atomic.Value(u64),

    pub fn fromSlice(memory: []u8) GranularMemory {
        return GranularMemory{
            .memory = @alignCast(std.mem.bytesAsSlice(std.atomic.Value(u64), memory)),
        };
    }

    pub fn getGranule(self: GranularMemory, offset: u64) [8]u8 {
        const addr: GranuleAddr = @bitCast(offset);
        const granule_ptr: *std.atomic.Value(u64) = &self.memory[addr.granule];
        return @bitCast(std.mem.nativeToLittle(u64, granule_ptr.load(.monotonic)));
    }

    pub fn load(
        self: GranularMemory,
        comptime bits: comptime_int,
        offset: u64,
    ) std.meta.Int(.unsigned, bits) {
        const bytes = @divExact(bits, 8);

        const addr: GranuleAddr = @bitCast(offset);
        const granule_bytes: [8]u8 = self.getGranule(offset);
        return std.mem.littleToNative(
            std.meta.Int(.unsigned, bits),
            @bitCast(granule_bytes[addr.sub..][0..bytes].*),
        );
    }
};

const NO_RESERVATION = std.math.maxInt(u64);

memory: []align(std.heap.page_size_min) u8,
ffi_ctx: *anyopaque,
reserved_lock: RwLock,
reserved_granule: std.atomic.Value(u64),

pub fn init(memory: []align(std.heap.page_size_min) u8, ffi_ctx: *anyopaque) Memory {
    return Memory{
        .memory = memory,
        .ffi_ctx = ffi_ctx,
        .reserved_lock = .{},
        .reserved_granule = std.atomic.Value(u64).init(NO_RESERVATION),
    };
}

pub fn deinit(_: *Memory) void {}

fn toOffset(_: Memory, addr: u64) ?u64 {
    if (0x80000000 <= addr and addr < 0x100000000) {
        return addr - 0x80000000;
    }
    return null;
}

pub fn getUnsynchronised(self: *Memory, addr: u64) ?[]u8 {
    if (self.toOffset(addr)) |offset| {
        return self.memory[offset..];
    }
    return null;
}

pub fn getFrame(self: *Memory, addr: u64) ?GranularMemory {
    std.debug.assert(addr & 0xfff == 0);
    const memory = self.getUnsynchronised(addr) orelse return null;
    return GranularMemory.fromSlice(memory[0..4096]);
}

pub fn getFrameContaining(self: *Memory, addr: u64) ?GranularMemory {
    const frame_base = addr & ~@as(u64, 0xfff);
    return self.getFrame(frame_base);
}

pub fn load(
    self: *Memory,
    hart: *Hart,
    comptime bits: comptime_int,
    addr: u64,
) Hart.Trap!std.meta.Int(.unsigned, bits) {
    if (self.toOffset(addr)) |offset| {
        const granular = GranularMemory.fromSlice(self.memory);
        return granular.load(bits, offset);
    }

    if (ffi.load(self.ffi_ctx, hart, bits, addr)) |value| {
        return value;
    }

    return Hart.Trap.LoadAccessFault;
}

pub fn loadReserved(
    self: *Memory,
    hart: *Hart,
    comptime bits: comptime_int,
    addr: u64,
) Hart.Trap!std.meta.Int(.unsigned, bits) {
    if (self.toOffset(addr)) |offset| {
        self.reserved_lock.lockExclusive();
        defer self.reserved_lock.unlockExclusive();
        self.reserved_granule.store(offset & ~@as(u64, 0b111), .monotonic);
    }

    return self.load(hart, bits, addr);
}

pub fn store(
    self: *Memory,
    hart: *Hart,
    comptime bits: comptime_int,
    addr: u64,
    value: std.meta.Int(.unsigned, bits),
) Hart.Trap!void {
    const bytes = @divExact(bits, 8);

    if (self.toOffset(addr)) |offset| {
        const granular = GranularMemory.fromSlice(self.memory);

        const granule_addr: GranuleAddr = @bitCast(offset);
        var granule_bytes = granular.getGranule(offset);
        while (true) {
            const original_value = std.mem.littleToNative(u64, @bitCast(granule_bytes));
            granule_bytes[granule_addr.sub..][0..bytes].* = @bitCast(
                std.mem.nativeToLittle(std.meta.Int(.unsigned, bits), value),
            );
            const new_value = std.mem.littleToNative(u64, @bitCast(granule_bytes));

            self.reserved_lock.lockShared();
            defer self.reserved_lock.unlockShared();
            if (self.reserved_granule.load(.monotonic) == addr & ~@as(u12, 0b111)) {
                self.reserved_granule.store(NO_RESERVATION, .monotonic);
            }

            if (granular.memory[granule_addr.granule].cmpxchgWeak(
                original_value,
                new_value,
                .monotonic,
                .monotonic,
            )) |new_original_value| {
                granule_bytes = @bitCast(std.mem.nativeToLittle(u64, new_original_value));
                continue;
            }
            return;
        }
    }

    if (ffi.store(self.ffi_ctx, hart, bits, addr, value)) {
        return;
    }

    return Hart.Trap.StoreAccessFault;
}

pub fn storeConditional(
    self: *Memory,
    _: *Hart,
    comptime bits: comptime_int,
    addr: u64,
    value: std.meta.Int(.unsigned, bits),
) Hart.Trap!bool {
    const bytes = @divExact(bits, 8);

    if (self.toOffset(addr)) |offset| {
        const granular = GranularMemory.fromSlice(self.memory);

        const granule_addr: GranuleAddr = @bitCast(offset);
        var u64_bytes = u64_bytes: {
            self.reserved_lock.lockExclusive();
            defer self.reserved_lock.unlockExclusive();

            const u64_bytes: [8]u8 = granular.getGranule(offset);

            const reserved_granule = self.reserved_granule.swap(NO_RESERVATION, .monotonic);
            if (reserved_granule != offset & ~@as(u64, 0b111)) {
                return false;
            }

            break :u64_bytes u64_bytes;
        };

        const original_value = std.mem.littleToNative(u64, @bitCast(u64_bytes));
        u64_bytes[granule_addr.sub..][0..bytes].* = @bitCast(
            std.mem.nativeToLittle(std.meta.Int(.unsigned, bits), value),
        );
        const new_value = std.mem.littleToNative(u64, @bitCast(u64_bytes));

        // This is redundant in the single-hart case, but will be important in the multi-hart case.
        self.reserved_lock.lockShared();
        defer self.reserved_lock.unlockShared();
        if (self.reserved_granule.load(.monotonic) == addr & ~@as(u12, 0b111)) {
            self.reserved_granule.store(NO_RESERVATION, .monotonic);
        }

        // Only try this one once, and don't allow sporadic failures ("strong").
        // If it fails, then something has stored since we unset the reservation set, so fail the SC.
        if (granular.memory[granule_addr.granule].cmpxchgStrong(
            original_value,
            new_value,
            .monotonic,
            .monotonic,
        )) |_| {
            return false;
        }

        return true;
    }

    return Hart.Trap.StoreAccessFault;
}

// Corresponds with AmoKind in ffi.rs
pub const AmoKind = enum(u8) {
    Swap,
    Add,
    Xor,
    And,
    Or,
    Min,
    Max,
    Minu,
    Maxu,

    pub fn fromRiscv(func5: u5) ?AmoKind {
        return switch (func5) {
            riscv.AMO.amoswap => .Swap,
            riscv.AMO.amoadd => .Add,
            riscv.AMO.amoxor => .Xor,
            riscv.AMO.amoand => .And,
            riscv.AMO.amoor => .Or,
            riscv.AMO.amomin => .Min,
            riscv.AMO.amomax => .Max,
            riscv.AMO.amominu => .Minu,
            riscv.AMO.amomaxu => .Maxu,
            else => null,
        };
    }
};

pub fn amoWord(self: *Memory, hart: *Hart, addr: u64, op: AmoKind, operand: u32, ordering: std.builtin.AtomicOrder) Hart.Trap!u32 {
    if (self.toOffset(addr)) |offset| {
        const granular = GranularMemory.fromSlice(self.memory);

        const granule_addr: GranuleAddr = @bitCast(offset);
        if (granule_addr.sub > 4) {
            return Hart.Trap.StoreAccessMisaligned;
        }

        var granule_bytes = granular.getGranule(offset);
        while (true) {
            const original_value = std.mem.littleToNative(u64, @bitCast(granule_bytes));
            const mem_operand = std.mem.littleToNative(u32, @bitCast(granule_bytes[granule_addr.sub..][0..4].*));
            granule_bytes[granule_addr.sub..][0..4].* = @bitCast(
                std.mem.nativeToLittle(u32, switch (op) {
                    AmoKind.Swap => operand,
                    AmoKind.Add => operand +% mem_operand,
                    AmoKind.Xor => operand ^ mem_operand,
                    AmoKind.And => operand & mem_operand,
                    AmoKind.Or => operand | mem_operand,
                    AmoKind.Min => @bitCast(@min(@as(i32, @bitCast(operand)), @as(i32, @bitCast(mem_operand)))),
                    AmoKind.Max => @bitCast(@max(@as(i32, @bitCast(operand)), @as(i32, @bitCast(mem_operand)))),
                    AmoKind.Minu => @min(operand, mem_operand),
                    AmoKind.Maxu => @max(operand, mem_operand),
                }),
            );
            const new_value = std.mem.littleToNative(u64, @bitCast(granule_bytes));

            self.reserved_lock.lockShared();
            defer self.reserved_lock.unlockShared();
            if (self.reserved_granule.load(.monotonic) == addr & ~@as(u12, 0b111)) {
                self.reserved_granule.store(NO_RESERVATION, .monotonic);
            }

            const xchg_result = switch (ordering) {
                .unordered, .seq_cst => unreachable,
                inline else => |comptime_ordering| granular.memory[granule_addr.granule].cmpxchgWeak(
                    original_value,
                    new_value,
                    comptime_ordering,
                    .monotonic,
                ),
            };

            if (xchg_result) |new_original_value| {
                granule_bytes = @bitCast(std.mem.nativeToLittle(u64, new_original_value));
                continue;
            }
            return mem_operand;
        }
    }

    if (ffi.amo(self.ffi_ctx, hart, 32, addr, op, operand)) |value| {
        return value;
    }

    return Hart.Trap.StoreAccessFault;
}

pub fn amoDoubleWord(self: *Memory, hart: *Hart, addr: u64, op: AmoKind, operand: u64, ordering: std.builtin.AtomicOrder) Hart.Trap!u64 {
    if (self.toOffset(addr)) |offset| {
        const granular = GranularMemory.fromSlice(self.memory);

        const granule_addr: GranuleAddr = @bitCast(offset);
        if (granule_addr.sub != 0) {
            return Hart.Trap.StoreAccessMisaligned;
        }

        self.reserved_lock.lockShared();
        defer self.reserved_lock.unlockShared();
        if (self.reserved_granule.load(.monotonic) == addr & ~@as(u12, 0b111)) {
            self.reserved_granule.store(NO_RESERVATION, .monotonic);
        }

        return switch (ordering) {
            .unordered, .seq_cst => unreachable,
            inline else => |comptime_ordering| switch (op) {
                .Swap => granular.memory[granule_addr.granule].swap(operand, comptime_ordering),
                .Add => granular.memory[granule_addr.granule].fetchAdd(operand, comptime_ordering),
                .Xor => granular.memory[granule_addr.granule].fetchXor(operand, comptime_ordering),
                .And => granular.memory[granule_addr.granule].fetchAnd(operand, comptime_ordering),
                .Or => granular.memory[granule_addr.granule].fetchOr(operand, comptime_ordering),
                .Min => @as(u64, @bitCast(@atomicRmw(
                    i64,
                    @as(*i64, @ptrCast(&granular.memory[granule_addr.granule].raw)),
                    .Min,
                    @bitCast(operand),
                    comptime_ordering,
                ))),
                .Max => @as(u64, @bitCast(@atomicRmw(
                    i64,
                    @as(*i64, @ptrCast(&granular.memory[granule_addr.granule].raw)),
                    .Max,
                    @bitCast(operand),
                    comptime_ordering,
                ))),
                .Minu => granular.memory[granule_addr.granule].fetchMin(operand, comptime_ordering),
                .Maxu => granular.memory[granule_addr.granule].fetchMax(operand, comptime_ordering),
            },
        };
    }

    if (ffi.amo(self.ffi_ctx, hart, 64, addr, op, operand)) |value| {
        return value;
    }

    return Hart.Trap.StoreAccessFault;
}

pub fn read(self: *Memory, addr: u64, full_buf: []u8) bool {
    // It'd be nice if I a) didn't have to redeclare this b) didn't have to pick a new name for it
    var buf = full_buf;

    if (addr < 0x8000_0000 or addr + buf.len >= 0x1_0000_0000) {
        return false;
    }

    // Handle the maybe-unaligned first word
    var granule_addr: GranuleAddr = @bitCast(addr);
    granule_addr.granule = 0;

    const base_addr = addr & ~@as(u64, 0b111);
    const granular = GranularMemory.fromSlice(self.getUnsynchronised(base_addr).?);

    var u64_bytes = granular.getGranule(@bitCast(granule_addr));
    var stride = @min(8 - @as(usize, granule_addr.sub), buf.len);
    @memcpy(buf[0..stride], u64_bytes[granule_addr.sub..][0..stride]);

    // Copy the rest of the buffer
    granule_addr.granule += 1;
    buf = buf[stride..];
    while (buf.len > 0) : (granule_addr.granule += 1) {
        u64_bytes = granular.getGranule(@bitCast(granule_addr));
        stride = @min(8, buf.len);
        @memcpy(buf[0..stride], u64_bytes[0..stride]);
        buf = buf[stride..];
    }

    return true;
}

pub fn write(self: *Memory, addr: u64, full_buf: []const u8) bool {
    var buf = full_buf;

    if (addr < 0x8000_0000 or addr + buf.len >= 0x1_0000_0000) {
        return false;
    }

    // Handle the maybe-unaligned first word
    var granule_addr: GranuleAddr = @bitCast(addr);
    granule_addr.granule = 0;

    const base_addr = addr & ~@as(u64, 0b111);
    const granular = GranularMemory.fromSlice(self.getUnsynchronised(base_addr).?);

    var u64_bytes = granular.getGranule(@bitCast(granule_addr));
    const stride = @min(8 - @as(usize, granule_addr.sub), buf.len);
    while (true) {
        const original_value = std.mem.littleToNative(u64, @bitCast(u64_bytes));
        @memcpy(u64_bytes[granule_addr.sub..][0..stride], buf[0..stride]);
        const new_value = std.mem.littleToNative(u64, @bitCast(u64_bytes));

        self.reserved_lock.lockShared();
        defer self.reserved_lock.unlockShared();
        if (self.reserved_granule.load(.monotonic) == base_addr) {
            self.reserved_granule.store(NO_RESERVATION, .monotonic);
        }

        if (granular.memory[0].cmpxchgWeak(
            original_value,
            new_value,
            .monotonic,
            .monotonic,
        )) |new_original_value| {
            u64_bytes = @bitCast(new_original_value);
        }
        break;
    }

    // Copy the rest of the buffer
    granule_addr.granule += 1;
    buf = buf[stride..];
    while (buf.len > 8) : (granule_addr.granule += 1) {
        self.reserved_lock.lockShared();
        defer self.reserved_lock.unlockShared();
        if (self.reserved_granule.load(.monotonic) == @as(u64, granule_addr.granule) << 8) {
            self.reserved_granule.store(NO_RESERVATION, .monotonic);
        }

        granular.memory[granule_addr.granule].store(@bitCast(buf[0..8].*), .monotonic);
        buf = buf[8..];
    }

    // Handle the maybe-unaligned last word
    if (buf.len > 0) {
        while (true) {
            u64_bytes = granular.getGranule(@bitCast(granule_addr));
            const original_value = std.mem.littleToNative(u64, @bitCast(u64_bytes));
            @memcpy(u64_bytes[0..buf.len], buf[0..buf.len]);
            const new_value = std.mem.littleToNative(u64, @bitCast(u64_bytes));

            self.reserved_lock.lockShared();
            defer self.reserved_lock.unlockShared();
            if (self.reserved_granule.load(.monotonic) == @as(u64, granule_addr.granule) << 8) {
                self.reserved_granule.store(NO_RESERVATION, .monotonic);
            }

            if (granular.memory[granule_addr.granule].cmpxchgWeak(
                original_value,
                new_value,
                .monotonic,
                .monotonic,
            )) |new_original_value| {
                u64_bytes = @bitCast(new_original_value);
                continue;
            }
            break;
        }
    }

    return true;
}
