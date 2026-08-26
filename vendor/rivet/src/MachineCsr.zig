// SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");
const csr = @import("csr.zig");
const ffi = @import("lib.zig");
const riscv = @import("riscv.zig");
const Hart = @import("Hart.zig");
const PmpMap = @import("PmpMap.zig");

const CsRegister = csr.CsRegister;
const Field = csr.Field;
const Trap = Hart.Trap;
const Wpri = csr.Wpri;

const Self = @This();

const MStatus = packed struct {
    a: Wpri(u1),
    sie: Field(u1, csr.keep),
    b: Wpri(u1),
    mie: Field(u1, csr.keep),
    c: Wpri(u1),
    spie: Field(u1, csr.keep),
    ube: Field(u1, csr.discard),
    mpie: Field(u1, csr.keep),
    spp: Field(u1, csr.keep),
    vs: Field(u2, csr.discard), // Requires V extension
    mpp: Field(u2, csr.keep_only(u2, riscv.PRIVILEGE)),
    fs: Field(u2, csr.keep),
    xs: Field(u2, csr.discard), // Requires user extensions
    mprv: Field(u1, csr.keep),
    sum: Field(u1, csr.keep),
    mxr: Field(u1, csr.keep),
    tvm: Field(u1, csr.keep),
    tw: Field(u1, csr.keep),
    tsr: Field(u1, csr.keep),
    d: Wpri(u9),
    uxl: Field(u2, csr.discard),
    sxl: Field(u2, csr.discard),
    sbe: Field(u1, csr.discard),
    mbe: Field(u1, csr.discard),
    e: Wpri(u25),
    sd: Field(u1, csr.discard),

    fn init() MStatus {
        return MStatus{
            .a = @bitCast(@as(u1, 0)),
            .sie = @bitCast(@as(u1, 0)),
            .b = @bitCast(@as(u1, 0)),
            .mie = @bitCast(@as(u1, 0)),
            .c = @bitCast(@as(u1, 0)),
            .spie = @bitCast(@as(u1, 0)),
            .ube = @bitCast(@as(u1, 0)),
            .mpie = @bitCast(@as(u1, 0)),
            .spp = @bitCast(@as(u1, 0)),
            .vs = @bitCast(@as(u2, 0)),
            .mpp = @bitCast(@as(u2, 0b11)),
            .fs = @bitCast(@as(u2, 0b01)),
            .xs = @bitCast(@as(u2, 0)),
            .mprv = @bitCast(@as(u1, 0)),
            .sum = @bitCast(@as(u1, 0)),
            .mxr = @bitCast(@as(u1, 0)),
            .tvm = @bitCast(@as(u1, 0)),
            .tw = @bitCast(@as(u1, 0)),
            .tsr = @bitCast(@as(u1, 0)),
            .d = @bitCast(@as(u9, 0)),
            .uxl = @bitCast(@as(u2, riscv.XLEN.@"64")),
            .sxl = @bitCast(@as(u2, riscv.XLEN.@"64")),
            .sbe = @bitCast(@as(u1, 0)),
            .mbe = @bitCast(@as(u1, 0)),
            .e = @bitCast(@as(u25, 0)),
            .sd = @bitCast(@as(u1, 0)),
        };
    }
};

pub const Interrupts = packed struct {
    a: Field(u1, csr.discard),
    ssi: Field(u1, csr.keep),
    b: Field(u1, csr.discard),
    msi: Field(u1, csr.keep),
    c: Field(u1, csr.discard),
    sti: Field(u1, csr.keep),
    d: Field(u1, csr.discard),
    mti: Field(u1, csr.keep),
    e: Field(u1, csr.discard),
    sei: Field(u1, csr.keep),
    f: Field(u1, csr.discard),
    mei: Field(u1, csr.keep),
    g: Field(u1, csr.discard),
    lcofi: Field(u1, csr.discard), // Requires Sscofpmf extension
    z: Field(u50, csr.discard),
};

const PmpCfg = packed struct {
    r: u1,
    w: u1,
    x: u1,
    a: u2,
    z: u2,
    l: u1,
};

fn edeleg(comptime T: type, _: T, new: T) T {
    return new & 0b1100_1011_0011_1111_1111;
}

fn pmp(comptime T: type, prev: T, new: T) T {
    if (prev.l != 1) {
        var next = new;
        next.z = 0;
        return next;
    }
    return prev;
}

const sstatus_mask = 0x80000002000DE762;

misa: CsRegister(packed struct { extensions: Field(u26, csr.discard), z: Field(u36, csr.discard), mxl: Field(u2, csr.discard) }),
mvendorid: CsRegister(packed struct { offset: Field(u7, csr.discard), bank: Field(u25, csr.discard), z: Field(u32, csr.discard) }),
marchid: CsRegister(packed struct { marchid: Field(u64, csr.discard) }),
mimpid: CsRegister(packed struct { mimpid: Field(u64, csr.discard) }),
mhartid: CsRegister(packed struct { mhartid: Field(u64, csr.discard) }),
mstatus: CsRegister(MStatus),
mtvec: CsRegister(packed struct { mode: Field(u2, csr.keep_only(u2, .{ 0, 1 })), base: Field(u62, csr.keep) }),
medeleg: CsRegister(packed struct { medeleg: Field(u64, edeleg) }),
mideleg: CsRegister(Interrupts),
mie: CsRegister(Interrupts),
mcycle: CsRegister(packed struct { mcycle: Field(u64, csr.keep) }),
minstret: CsRegister(packed struct { minstret: Field(u64, csr.keep) }),
mcounteren: CsRegister(packed struct { cy: Field(u1, csr.keep), tm: Field(u1, csr.keep), ir: Field(u1, csr.keep), hpmn: Field(u29, csr.discard), z: Field(u32, csr.discard) }),
mcountinhibit: CsRegister(packed struct { cy: Field(u1, csr.keep), tm_z: Field(u1, csr.discard), ir: Field(u1, csr.keep), hpmn: Field(u29, csr.discard), z: Field(u32, csr.discard) }),
mscratch: CsRegister(packed struct { mscratch: Field(u64, csr.keep) }),
mepc: CsRegister(packed struct { mepc: Field(u64, csr.keep_ialigned) }),
mcause: CsRegister(packed struct { mcause: Field(u64, csr.keep_only(u64, riscv.EXCEPTION)) }),
mtval: CsRegister(packed struct { mtval: Field(u64, csr.keep) }),
mconfigptr: CsRegister(packed struct { mconfigptr: Field(u64, csr.discard) }),
menvcfg: CsRegister(packed struct {
    fiom: Field(u1, csr.keep),
    a: Wpri(u59),
    cde: Field(u1, csr.discard), // Requires Smcdeleg extension
    adue: Field(u1, csr.keep),
    pbmte: Field(u1, csr.discard), // Requires Svpbmt extension
    b: Wpri(u1),
}),
pmpcfg: [8]CsRegister(packed struct {
    pmp0cfg: Field(PmpCfg, pmp),
    pmp1cfg: Field(PmpCfg, pmp),
    pmp2cfg: Field(PmpCfg, pmp),
    pmp3cfg: Field(PmpCfg, pmp),
    pmp4cfg: Field(PmpCfg, pmp),
    pmp5cfg: Field(PmpCfg, pmp),
    pmp6cfg: Field(PmpCfg, pmp),
    pmp7cfg: Field(PmpCfg, pmp),
}),
pmpaddr: [64]CsRegister(packed struct { address4: Field(u53, csr.keep), z: Field(u11, csr.discard) }),

stvec: CsRegister(packed struct {
    mode: Field(u2, csr.keep_only(u2, .{ 0, 1 })),
    base: Field(u62, csr.keep),
}),
scounteren: CsRegister(packed struct {
    cy: Field(u1, csr.keep),
    tm: Field(u1, csr.keep),
    ir: Field(u1, csr.keep),
    hpmn: Field(u29, csr.discard),
    z: Field(u32, csr.discard),
}),
sscratch: CsRegister(packed struct { sscratch: Field(u64, csr.keep) }),
sepc: CsRegister(packed struct { sepc: Field(u64, csr.keep_ialigned) }),
scause: CsRegister(packed struct { scause: Field(u64, csr.keep_only(u64, riscv.EXCEPTION)) }),
stval: CsRegister(packed struct { stval: Field(u64, csr.keep) }),
senvcfg: CsRegister(packed struct { fiom: Field(u1, csr.keep), a: Wpri(u63) }),
satp: CsRegister(packed struct {
    ppn: Field(u44, csr.keep),
    asid: Field(u16, csr.discard),
    mode: Field(u4, csr.keep_only(u4, riscv.SATP_MODE)),
}),
stimecmp: CsRegister(packed struct { stimecmp: Field(u64, csr.keep) }),

fcsr: CsRegister(packed struct {
    flags: Field(u5, csr.keep),
    rm: Field(u3, csr.keep),
    z: Field(u24, csr.discard),
}),

pmpmap: PmpMap,
inhibited_cycle: u64,
inhibited_instret: u64,

pub fn init(allocator: std.mem.Allocator) Self {
    const fields = @typeInfo(Self).@"struct".fields;
    return std.mem.zeroInit(Self, .{
        .misa = fields[0].type.init(.{ .extensions = .{ .value = 0b00000101000001000100101101 }, .z = .{ .value = 0 }, .mxl = .{ .value = riscv.XLEN.@"64" } }),
        .mstatus = @as(fields[5].type, @bitCast(MStatus.init())),
        .pmpmap = PmpMap.init(allocator),
    });
}

pub fn deinit(self: *Self) void {
    self.pmpmap.deinit();
}

pub fn read(self: *Self, register: u12, hart: *Hart) Trap!u64 {
    switch (register) {
        riscv.CSR.misa => return self.misa.read(),
        riscv.CSR.mvendorid => return self.mvendorid.read(),
        riscv.CSR.marchid => return self.marchid.read(),
        riscv.CSR.mimpid => return self.mimpid.read(),
        riscv.CSR.mhartid => return self.mhartid.read(),
        riscv.CSR.mstatus => {
            self.mstatus.set(.sd, @intFromBool(self.mstatus.get(.fs) == 0b11));
            return self.mstatus.read();
        },
        riscv.CSR.mtvec => return self.mtvec.read(),
        riscv.CSR.medeleg => return self.medeleg.read(),
        riscv.CSR.mideleg => return self.mideleg.read(),
        riscv.CSR.mie => return self.mie.read(),
        riscv.CSR.mcycle => return self.mcycle.read(),
        riscv.CSR.minstret => return self.minstret.read(),
        riscv.CSR.mcounteren => return self.mcounteren.read(),
        riscv.CSR.mcountinhibit => return self.mcountinhibit.read(),
        riscv.CSR.mscratch => return self.mscratch.read(),
        riscv.CSR.mepc => return self.mepc.read(),
        riscv.CSR.mcause => return self.mcause.read(),
        riscv.CSR.mtval => return self.mtval.read(),
        riscv.CSR.mconfigptr => return self.mconfigptr.read(),
        riscv.CSR.menvcfg => return self.menvcfg.read(),
        riscv.CSR.pmpcfg0...riscv.CSR.pmpcfg15 => {
            if (register & 0b1 != 0) {
                return Trap.IllegalInstruction;
            } else {
                return self.pmpcfg[(register - 0x3A0) / 2].read();
            }
        },
        riscv.CSR.pmpaddr0...riscv.CSR.pmpaddr63 => return self.pmpaddr[register - 0x3B0].read(),
        riscv.CSR.sstatus => return self.mstatus.read() & sstatus_mask,
        riscv.CSR.stvec => return self.stvec.read(),
        riscv.CSR.sie => return self.mie.read() & self.mideleg.read(),
        riscv.CSR.scounteren => return self.scounteren.read(),
        riscv.CSR.sscratch => return self.sscratch.read(),
        riscv.CSR.sepc => return self.sepc.read(),
        riscv.CSR.scause => return self.scause.read(),
        riscv.CSR.stval => return self.stval.read(),
        riscv.CSR.senvcfg => return self.senvcfg.read(),
        riscv.CSR.stimecmp => return self.stimecmp.read(),
        riscv.CSR.satp => {
            if (self.mstatus.get(.tvm) == 1) {
                return Trap.IllegalInstruction;
            }
            return self.satp.read();
        },
        riscv.CSR.fflags => return self.fcsr.read() & 0b11111,
        riscv.CSR.frm => return (self.fcsr.read() & 0b11100000) >> 5,
        riscv.CSR.fcsr => return self.fcsr.read(),
        riscv.CSR.cycle => {
            if (hart.priv == riscv.PRIVILEGE.m or self.mcounteren.get(.cy) == 1) {
                return hart.mcsr.get_cycle();
            } else {
                return Trap.IllegalInstruction;
            }
        },
        riscv.CSR.time => {
            if (hart.priv == riscv.PRIVILEGE.m or self.mcounteren.get(.tm) == 1) {
                return ffi.get_time(hart.memory.ffi_ctx);
            } else {
                return Trap.IllegalInstruction;
            }
        },
        riscv.CSR.instret => {
            if (hart.priv == riscv.PRIVILEGE.m or self.mcounteren.get(.ir) == 1) {
                return hart.mcsr.get_instret();
            } else {
                return Trap.IllegalInstruction;
            }
        },
        0xC03...0xC1F => {
            if (hart.priv == riscv.PRIVILEGE.m) {
                return 0;
            } else {
                return Trap.IllegalInstruction;
            }
        },
        else => return 0,
    }
}

pub fn write(self: *Self, register: u12, value: u64, hart: *Hart) Trap!void {
    switch (register) {
        riscv.CSR.mstatus => self.mstatus.write(value),
        riscv.CSR.mtvec => self.mtvec.write(value),
        riscv.CSR.medeleg => self.medeleg.write(value),
        riscv.CSR.mideleg => self.mideleg.write(value),
        riscv.CSR.mie => self.mie.write(value),
        riscv.CSR.mcycle => {
            self.mcycle.write(value -% 1);
        },
        riscv.CSR.minstret => {
            self.minstret.write(value -% 1);
        },
        riscv.CSR.mcounteren => self.mcounteren.write(value),
        riscv.CSR.mcountinhibit => {
            const cy = self.mcountinhibit.get(.cy) == 1;
            const ir = self.mcountinhibit.get(.ir) == 1;
            self.mcountinhibit.write(value);

            if (!cy and self.mcountinhibit.get(.cy) == 1) {
                // Just inhibited cy
                self.inhibited_cycle = self.mcycle.get(.mcycle);
            }
            if (cy and self.mcountinhibit.get(.cy) == 0) {
                // Just re-enabled cy
                self.mcycle.set(.mcycle, self.inhibited_cycle);
            }
            if (!ir and self.mcountinhibit.get(.ir) == 1) {
                // Just inhibited ir
                self.inhibited_instret = self.minstret.get(.minstret);
            }
            if (ir and self.mcountinhibit.get(.ir) == 0) {
                // Just re-enabled ir
                self.minstret.set(.minstret, self.inhibited_instret);
            }
        },
        riscv.CSR.mscratch => self.mscratch.write(value),
        riscv.CSR.mepc => self.mepc.write(value),
        riscv.CSR.mcause => self.mcause.write(value),
        riscv.CSR.mtval => self.mtval.write(value),
        riscv.CSR.mconfigptr => self.mconfigptr.write(value),
        riscv.CSR.menvcfg => self.menvcfg.write(value),
        riscv.CSR.pmpcfg0...riscv.CSR.pmpcfg15 => {
            if (register & 0b1 != 0) {
                return Trap.IllegalInstruction;
            } else {
                const pmp_reg: u3 = @intCast((register - riscv.CSR.pmpcfg0) / 2);
                self.pmpcfg[pmp_reg].write(value);
                self.pmpmap.reconstruct(self);
            }
        },
        riscv.CSR.pmpaddr0...riscv.CSR.pmpaddr63 => {
            self.pmpaddr[register - riscv.CSR.pmpaddr0].write(value);
            self.pmpmap.reconstruct(self);
        },
        riscv.CSR.sstatus => self.mstatus.write(value & sstatus_mask),
        riscv.CSR.stvec => self.stvec.write(value),
        riscv.CSR.sie => self.mie.write(value & self.mideleg.read()),
        riscv.CSR.scounteren => self.scounteren.write(value),
        riscv.CSR.sscratch => self.sscratch.write(value),
        riscv.CSR.sepc => self.sepc.write(value),
        riscv.CSR.scause => self.scause.write(value),
        riscv.CSR.stval => self.stval.write(value),
        riscv.CSR.senvcfg => self.senvcfg.write(value),
        riscv.CSR.stimecmp => {
            if (self.mcounteren.get(.tm) == 0 and hart.priv != riscv.PRIVILEGE.m) {
                return Trap.IllegalInstruction;
            }
            ffi.set_stimecmp(hart.memory.ffi_ctx, hart, value);
            self.stimecmp.write(value);
        },
        riscv.CSR.satp => {
            if (hart.mcsr.mstatus.get(.tvm) == 1) {
                return Trap.IllegalInstruction;
            }
            self.satp.write(value);

            // FIXME: I think this is a symptom of me Doing Things Wrong; I
            // shouldn't have to invalidate address-translation caches on
            // satp writes
            hart.instruction_frame.frame = null;
        },
        riscv.CSR.fflags => {
            if (hart.mcsr.mstatus.get(.fs) == 0b00) {
                return Trap.IllegalInstruction;
            }
            hart.mcsr.mstatus.set(.fs, 0b11);

            self.fcsr.write(@as(u32, self.fcsr.get(.rm)) << 5 | @as(u32, @intCast(value & 0b11111)));
        },
        riscv.CSR.frm => {
            if (hart.mcsr.mstatus.get(.fs) == 0b00) {
                return Trap.IllegalInstruction;
            }
            hart.mcsr.mstatus.set(.fs, 0b11);

            self.fcsr.write(@truncate(value << 5 | self.fcsr.get(.flags)));
        },
        riscv.CSR.fcsr => {
            if (hart.mcsr.mstatus.get(.fs) == 0b00) {
                return Trap.IllegalInstruction;
            }
            hart.mcsr.mstatus.set(.fs, 0b11);

            self.fcsr.write(@truncate(value));
        },
        else => {},
    }
}

pub fn get_cycle(self: *Self) u64 {
    if (self.mcountinhibit.get(.cy) == 1) {
        return self.inhibited_cycle;
    }
    return self.mcycle.get(.mcycle);
}

pub fn step_cycle(self: *Self) void {
    self.mcycle.set(.mcycle, self.mcycle.get(.mcycle) + 1);
}

pub fn get_instret(self: *Self) u64 {
    if (self.mcountinhibit.get(.ir) == 1) {
        return self.inhibited_instret;
    }
    return self.minstret.get(.minstret);
}

pub fn step_instret(self: *Self) void {
    self.minstret.set(.minstret, self.minstret.get(.minstret) + 1);
}
