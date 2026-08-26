// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");
const riscv = @import("riscv.zig");
const Hart = @import("Hart.zig");
const Memory = @import("Memory.zig");

extern fn rust_alloc(len: usize, ptr_align: u8) ?[*]u8;
extern fn rust_realloc(ptr: [*]u8, len: usize, ptr_align: u8, new_len: usize) ?[*]u8;
extern fn rust_dealloc(ptr: [*]u8, len: usize, ptr_align: u8) void;
extern fn rust_load(ctx: *anyopaque, hart: *Hart, addr: u64, value: *u64, bits: u8) bool;
extern fn rust_store(ctx: *anyopaque, hart: *Hart, addr: u64, value: u64, bits: u8) bool;
extern fn rust_amo(ctx: *anyopaque, hart: *Hart, addr: u64, value: *u64, kind: Memory.AmoKind, bits: u8) bool;
extern fn rust_get_time(ctx: *anyopaque) u64;
extern fn rust_set_stimecmp(ctx: *anyopaque, hart: *Hart, value: u64) void;

fn global_alloc(_: *anyopaque, len: usize, ptr_align: std.mem.Alignment, _: usize) ?[*]u8 {
    return rust_alloc(len, @intFromEnum(ptr_align));
}

fn global_resize(_: *anyopaque, _: []u8, _: std.mem.Alignment, _: usize, _: usize) bool {
    // There's no resizing API in Rust that doesn't invalidate the old pointer unconditionally
    return false;
}

fn global_remap(_: *anyopaque, buf: []u8, ptr_align: std.mem.Alignment, new_len: usize, _: usize) ?[*]u8 {
    // The documentation for this method says it should return null if the allocator would be
    // copying bytes, but there's no resizing API in Rust that does that.
    return rust_realloc(buf.ptr, buf.len, @intFromEnum(ptr_align), new_len);
}

fn global_free(_: *anyopaque, buf: []u8, buf_align: std.mem.Alignment, _: usize) void {
    rust_dealloc(buf.ptr, buf.len, @intFromEnum(buf_align));
}

const global_allocator = std.mem.Allocator{
    .ptr = @ptrFromInt(1),
    .vtable = &std.mem.Allocator.VTable{
        .alloc = &global_alloc,
        .resize = &global_resize,
        .remap = &global_remap,
        .free = &global_free,
    },
};

pub fn load(
    ffi_ctx: *anyopaque,
    hart: *Hart,
    comptime bits: comptime_int,
    addr: u64,
) ?std.meta.Int(.unsigned, bits) {
    var value: u64 = 0;
    if (rust_load(ffi_ctx, hart, addr, &value, bits)) {
        return @intCast(value);
    }
    return null;
}

pub fn store(
    ffi_ctx: *anyopaque,
    hart: *Hart,
    comptime bits: comptime_int,
    addr: u64,
    value: std.meta.Int(.unsigned, bits),
) bool {
    return rust_store(ffi_ctx, hart, addr, value, bits);
}

pub fn amo(
    ffi_ctx: *anyopaque,
    hart: *Hart,
    comptime bits: comptime_int,
    addr: u64,
    kind: Memory.AmoKind,
    value: std.meta.Int(.unsigned, bits),
) ?std.meta.Int(.unsigned, bits) {
    var updated_value: u64 = value;
    if (rust_amo(ffi_ctx, hart, addr, &updated_value, kind, bits)) {
        return @intCast(updated_value);
    }
    return null;
}

pub fn get_time(ffi_ctx: *anyopaque) u64 {
    return rust_get_time(ffi_ctx);
}

pub fn set_stimecmp(ffi_ctx: *anyopaque, hart: *Hart, value: u64) void {
    rust_set_stimecmp(ffi_ctx, hart, value);
}

export fn memory_new(memory_ptr: [*]u8, memory_length: u64, ctx: *anyopaque) ?*Memory {
    const memory = global_allocator.create(Memory) catch return null;
    memory.* = Memory.init(
        @alignCast(memory_ptr[0..memory_length]),
        ctx,
    );

    return memory;
}

export fn memory_free(memory: *Memory) void {
    memory.deinit();
    global_allocator.destroy(memory);
}

export fn memory_read(memory: *Memory, addr: u64, len: u64, buf: [*]u8) bool {
    return memory.read(addr, buf[0..len]);
}

export fn memory_write(memory: *Memory, addr: u64, len: u64, buf: [*]const u8) bool {
    return memory.write(addr, buf[0..len]);
}

export fn hart_new(memory: *Memory) ?*Hart {
    const hart = global_allocator.create(Hart) catch return null;
    hart.* = Hart.init(
        global_allocator,
        memory,
    );

    std.mem.copyForwards(u8, memory.getUnsynchronised(0xfffff000).?, @embedFile("rivet.dtb"));
    hart.x[comptime riscv.REGISTER.get("a1") orelse unreachable] = 0xfffff000;

    return hart;
}

export fn hart_free(hart: *Hart) void {
    hart.deinit();
    global_allocator.destroy(hart);
}

export fn hart_set_pc(hart: *Hart, pc: u64) void {
    hart.pc = pc;
}

export fn hart_run(hart: *Hart) void {
    hart.run();
}

export fn hart_trigger(hart: *Hart, interrupt: u32) void {
    _ = hart.controller.mip.bitSet(@intCast(interrupt), .release);
    _ = std.posix.write(hart.controller.interrupt_fd, @as([8]u8, @bitCast(@as(u64, 1)))[0..]) catch unreachable;
}

export fn hart_untrigger(hart: *Hart, interrupt: u32) void {
    _ = hart.controller.mip.bitReset(@intCast(interrupt), .release);
}

export fn hart_stop(hart: *Hart) void {
    hart.controller.stop();
}
