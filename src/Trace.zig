// SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");
const options = @import("options");
const riscv = @import("riscv.zig");

const unistd = @cImport({
    // I have to launder these macro arguments through an extra function call
    // so the numbers get substituted properly, don't mind me
    @cDefine("__SYSCALL_CONST(x, y)", "const char *syscall_ ## x = #y;");
    @cDefine("__SYSCALL(x, y)", "__SYSCALL_CONST(x, y)");
    @cInclude("unistd.h");
});

fn syscall_name(no: u64) ?[]const u8 {
    if (no < 512) {
        // I don't want to make zig do an inline else over every possible u64
        switch (@as(u9, @intCast(no))) {
            inline else => |expected_no| {
                const field_name = std.fmt.comptimePrint("syscall_{}", .{expected_no});
                if (@hasDecl(unistd, field_name)) {
                    if (no == expected_no) {
                        return std.mem.sliceTo(@field(unistd, field_name)[4..], 0);
                    }
                }
            },
        }
    }
    return null;
}

const Self = @This();

pub const Events = packed struct {
    exec: bool,
    trap: bool,
    jump: bool,
    call: bool,
    vmem: bool,
    sbicall: bool,
    syscall: bool,
    elf: bool,
};

out: std.fs.File.Writer,
events: Events,

pub fn init(events: Events) Self {
    return Self{
        .out = std.io.getStdErr().writer(),
        .events = events,
    };
}

pub fn exec(self: *const Self, pc: u64, instr: u32) void {
    if (options.trace) {
        if (!self.events.exec) {
            return;
        }

        if (instr & 0b11 != 0b11) {
            self.out.print("Hart 0: Executing instruction {x:0>4} @ {x}\r\n", .{ instr, pc }) catch {};
        } else {
            self.out.print("Hart 0: Executing instruction {x:0>8} @ {x}\r\n", .{ instr, pc }) catch {};
        }
    }
}

pub fn trap(self: *const Self, exception: u64, current_privilege: u2, target_privilege: u2, tval: u64) void {
    if (options.trace) {
        if (!self.events.trap) {
            return;
        }

        const explanation = switch (exception) {
            riscv.EXCEPTION.ssi => "Supervisor software interrupt",
            riscv.EXCEPTION.msi => "Machine software interrupt",
            riscv.EXCEPTION.sti => "Supervisor timer interrupt",
            riscv.EXCEPTION.mti => "Machine timer interrupt",
            riscv.EXCEPTION.sei => "Supervisor external interrupt",
            riscv.EXCEPTION.mei => "Machine external interrupt",
            riscv.EXCEPTION.coi => "Counter-overflow interrupt",
            riscv.EXCEPTION.iam => "Instruction address misaligned",
            riscv.EXCEPTION.iaf => "Instruction access fault",
            riscv.EXCEPTION.ill => "Illegal instruction",
            riscv.EXCEPTION.brk => "Breakpoint",
            riscv.EXCEPTION.lam => "Load address misaligned",
            riscv.EXCEPTION.laf => "Load access fault",
            riscv.EXCEPTION.sam => "Store/AMO address misaligned",
            riscv.EXCEPTION.saf => "Store/AMO access fault",
            riscv.EXCEPTION.envu => "Environment call from U-mode",
            riscv.EXCEPTION.envs => "Environment call from S-mode",
            riscv.EXCEPTION.envm => "Environment call from M-mode",
            riscv.EXCEPTION.ipf => "Instruction page fault",
            riscv.EXCEPTION.lpf => "Load page fault",
            riscv.EXCEPTION.spf => "Store/AMO page fault",
            riscv.EXCEPTION.sck => "Software check",
            riscv.EXCEPTION.herr => "Hardware error",
            else => "Unknown",
        };
        self.out.print("Hart 0: Taking exception {x} ({s}) from {s} -> {s}: tval={x}\r\n", .{
            exception,
            explanation,
            switch (current_privilege) {
                riscv.PRIVILEGE.u => "U",
                riscv.PRIVILEGE.s => "S",
                riscv.PRIVILEGE.m => "M",
                else => unreachable,
            },
            if (target_privilege == riscv.PRIVILEGE.s) "S" else "M",
            tval,
        }) catch {};
    }
}

pub fn jump(self: *const Self, from_pc: u64, to_pc: u64) void {
    if (options.trace) {
        if (!self.events.jump) {
            return;
        }

        self.out.print("Hart 0: Jumping {x} --> {x}\r\n", .{ from_pc, to_pc }) catch {};
    }
}

pub fn call(self: *const Self, to_pc: u64, to_symbol: []const u8) void {
    if (options.trace) {
        if (!self.events.call) {
            return;
        }

        self.out.print("Hart 0: Calling {x} <{s}>\r\n", .{ to_pc, to_symbol }) catch {};
    }
}

pub fn ret(self: *const Self, args: *[32]u64) void {
    if (options.trace) {
        if (!self.events.call) {
            return;
        }

        self.out.print(
            "Hart 0: Returning a0={x}\r\n",
            .{@as(u64, @bitCast(args[comptime riscv.REGISTER.get("a0").?]))},
        ) catch {};
    }
}

pub fn vmem(self: *const Self, kind: enum { tlb, translated }, from_addr: u64, to_addr: u64) void {
    if (options.trace) {
        if (!self.events.vmem) {
            return;
        }

        self.out.print("Hart 0: Translated addr {x} --> {x}{s}\r\n", .{
            from_addr, to_addr, switch (kind) {
                .tlb => " (cache hit)",
                .translated => "",
            },
        }) catch {};
    }
}

pub fn vmem_fail(self: *const Self, from_addr: u64, msg: []const u8) void {
    if (options.trace) {
        if (!self.events.vmem) {
            return;
        }

        self.out.print("Hart 0: Translated addr {x} --> {s}\r\n", .{ from_addr, msg }) catch {};
    }
}

pub fn sbicall(self: *const Self, args: *[32]u64) void {
    if (options.trace) {
        if (!self.events.sbicall) {
            return;
        }

        self.out.print("Hart 0: Environment call: EID=0x{x}, SID=0x{x}, a0={x}, a1={x}, a2={x}, a3={x}, a4={x}, a5={x}\r\n", .{
            args[comptime riscv.REGISTER.get("a7").?],
            args[comptime riscv.REGISTER.get("a6").?],
            args[comptime riscv.REGISTER.get("a0").?],
            args[comptime riscv.REGISTER.get("a1").?],
            args[comptime riscv.REGISTER.get("a2").?],
            args[comptime riscv.REGISTER.get("a3").?],
            args[comptime riscv.REGISTER.get("a4").?],
            args[comptime riscv.REGISTER.get("a5").?],
        }) catch {};
    }
}

pub fn mret(self: *const Self, args: *[32]u64) void {
    if (options.trace) {
        if (!self.events.sbicall) {
            return;
        }

        self.out.print("Hart 0: Returned from M-mode: error={s}, value=0x{x}\r\n", .{
            switch (@as(i64, @bitCast(args[comptime riscv.REGISTER.get("a0").?]))) {
                0 => "SBI_SUCCESS",
                -1 => "SBI_ERR_FAILED",
                -2 => "SBI_ERR_NOT_SUPPORTED",
                -3 => "SBI_ERR_INVALID_PARAM",
                -4 => "SBI_ERR_DENIED",
                -5 => "SBI_ERR_INVALID_ADDRESS",
                -6 => "SBI_ERR_ALREADY_AVAILABLE",
                -7 => "SBI_ERR_ALREADY_STARTED",
                -8 => "SBI_ERR_ALREADY_STOPPED",
                -9 => "SBI_ERR_NO_SHMEM",
                else => "UNKNOWN",
            },
            args[comptime riscv.REGISTER.get("a1").?],
        }) catch {};
    }
}

pub fn syscall(self: *const Self, args: *[32]u64) void {
    if (options.trace) {
        if (!self.events.syscall) {
            return;
        }

        self.out.print("Hart 0: System call {}: {s} a0={x}, a1={x}, a2={x}, a3={x}, a4={x}, a5={x} a6={x}\r\n", .{
            args[comptime riscv.REGISTER.get("a7").?],
            syscall_name(args[comptime riscv.REGISTER.get("a7").?]) orelse "(unknown)",
            args[comptime riscv.REGISTER.get("a0").?],
            args[comptime riscv.REGISTER.get("a1").?],
            args[comptime riscv.REGISTER.get("a2").?],
            args[comptime riscv.REGISTER.get("a3").?],
            args[comptime riscv.REGISTER.get("a4").?],
            args[comptime riscv.REGISTER.get("a5").?],
            args[comptime riscv.REGISTER.get("a6").?],
        }) catch {};
    }
}

pub fn sret(self: *const Self, args: *[32]u64) void {
    if (options.trace) {
        if (!self.events.syscall) {
            return;
        }

        const returned_value = args[comptime riscv.REGISTER.get("a0").?];
        if (std.meta.intToEnum(std.posix.E, -@as(i64, @bitCast(returned_value)))) |errno| {
            self.out.print("Hart 0: Returned from S-mode: error={s}\r\n", .{
                @tagName(errno),
            }) catch {};
        } else |_| {
            self.out.print("Hart 0: Returned from S-mode: value=0x{x}\r\n", .{
                args[comptime riscv.REGISTER.get("a0").?],
            }) catch {};
        }
    }
}

pub fn load(self: *const Self, offset: u64, size: u64, addr: u64) void {
    if (options.trace) {
        if (!self.events.elf) {
            return;
        }

        self.out.print("ELF: Loading section from {x}..{x} to {x}\r\n", .{ offset, offset + size, addr }) catch {};
    }
}

pub fn symbol(self: *const Self, name: []const u8, idx: usize, addr: u64) void {
    if (options.trace) {
        if (!self.events.elf) {
            return;
        }

        self.out.print("ELF: Symbol {}: {s} @ {x}\r\n", .{ idx, name, addr }) catch {};
    }
}
