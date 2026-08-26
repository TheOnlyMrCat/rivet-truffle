// SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");

pub const OPCODE = .{
    .load = 0b0000011,
    .load_fp = 0b0000111,
    .misc_mem = 0b0001111,
    .op_imm = 0b0010011,
    .auipc = 0b0010111,
    .op_imm_32 = 0b0011011,
    .store = 0b0100011,
    .store_fp = 0b0100111,
    .amo = 0b0101111,
    .op = 0b0110011,
    .lui = 0b0110111,
    .op_32 = 0b0111011,
    .fmadd = 0b1000011,
    .fmsub = 0b1000111,
    .fnmadd = 0b1001011,
    .fnmsub = 0b1001111,
    .op_fp = 0b1010011,
    .branch = 0b1100011,
    .jalr = 0b1100111,
    .jal = 0b1101111,
    .system = 0b1110011,
};

pub const C0 = .{
    .addi4spn = 0b000,
    .fld = 0b001,
    .lw = 0b010,
    .ld = 0b011,
    .fsd = 0b101,
    .sw = 0b110,
    .sd = 0b111,
};

pub const C1 = .{
    .addi = 0b000,
    .addiw = 0b001,
    .li = 0b010,
    .lui = 0b011,
    .arith = 0b100,
    .j = 0b101,
    .beqz = 0b110,
    .bnez = 0b111,
};

pub const C2 = .{
    .slli = 0b000,
    .fldsp = 0b001,
    .lwsp = 0b010,
    .ldsp = 0b011,
    .j = 0b100,
    .fsdsp = 0b101,
    .swsp = 0b110,
    .sdsp = 0b111,
};

pub const AMO = .{
    .lr = 0b00010,
    .sc = 0b00011,
    .amoswap = 0b00001,
    .amoadd = 0b00000,
    .amoxor = 0b00100,
    .amoand = 0b01100,
    .amoor = 0b01000,
    .amomin = 0b10000,
    .amomax = 0b10100,
    .amominu = 0b11000,
    .amomaxu = 0b11100,
};

pub const OP = .{
    .int = 0b0000000,
    .muldiv = 0b0000001,
    .neg = 0b0100000,
};

pub const OP_32 = .{
    .int = 0b0000000,
    .muldiv = 0b0000001,
    .neg = 0b0100000,
};

pub const OP_FP = .{
    .fadd = 0b00000,
    .fsub = 0b00001,
    .fmul = 0b00010,
    .fdiv = 0b00011,
    .fsgnj = 0b00100,
    .fsqrt = 0b01011,
    .fminmax = 0b00101,
    .fcvt_sd = 0b01000,
    .fcvt_i_f = 0b11000,
    .fcmp = 0b10100,
    .fcvt_f_i = 0b11010,
    .fclass = 0b11100,
    .fmv_w_x = 0b11110,
};

pub const ARITH_H = .{
    .srli = 0b00,
    .srai = 0b01,
    .andi = 0b10,
    .l = 0b11,
};

pub const ARITH_L = .{
    .sub = 0b00,
    .xor = 0b01,
    .@"or" = 0b10,
    .@"and" = 0b11,
};

pub const LOAD = .{
    .lb = 0b000,
    .lh = 0b001,
    .lw = 0b010,
    .ld = 0b011,
    .lbu = 0b100,
    .lhu = 0b101,
    .lwu = 0b110,
};

pub const LOAD_FP = .{
    .lw = 0b010,
    .ld = 0b011,
};

pub const MISC_MEM = .{
    .fence = 0b000,
    .fence_i = 0b001,
};

pub const OP_IMM = .{
    .addi = 0b000,
    .slli = 0b001,
    .slti = 0b010,
    .sltiu = 0b011,
    .xori = 0b100,
    .srli = 0b101,
    .ori = 0b110,
    .andi = 0b111,
};

pub const OP_IMM_32 = .{
    .addiw = 0b000,
    .slliw = 0b001,
    .srliw = 0b101,
};

pub const STORE = .{
    .sb = 0b000,
    .sh = 0b001,
    .sw = 0b010,
    .sd = 0b011,
};

pub const STORE_FP = .{
    .sw = 0b010,
    .sd = 0b011,
};

pub const AMO_WIDTH = .{
    .w = 0b010,
    .d = 0b011,
};

pub const OP_INT = .{
    .add = 0b000,
    .sll = 0b001,
    .slt = 0b010,
    .sltu = 0b011,
    .xor = 0b100,
    .srl = 0b101,
    .@"or" = 0b110,
    .@"and" = 0b111,
};

pub const OP_MULDIV = .{
    .mul = 0b000,
    .mulh = 0b001,
    .mulhsu = 0b010,
    .mulhu = 0b011,
    .div = 0b100,
    .divu = 0b101,
    .rem = 0b110,
    .remu = 0b111,
};

pub const OP_NEG = .{
    .sub = 0b000,
    .sra = 0b101,
};

pub const OP_32_INT = .{
    .addw = 0b000,
    .sllw = 0b001,
    .srlw = 0b101,
};

pub const OP_32_MULDIV = .{
    .mulw = 0b000,
    .divw = 0b100,
    .divuw = 0b101,
    .remw = 0b110,
    .remuw = 0b111,
};

pub const OP_32_NEG = .{
    .subw = 0b000,
    .sraw = 0b101,
};

pub const OP_FP_FMT = .{
    .s = 0b00,
    .d = 0b01,
};

pub const OP_FP_RM = .{
    .rne = 0b000,
    .rtz = 0b001,
    .rdn = 0b010,
    .rup = 0b011,
    .rmm = 0b100,
    .dyn = 0b111,
};

pub const OP_FP_SGNJ = .{
    .j = 0b000,
    .jn = 0b001,
    .jx = 0b010,
};

pub const OP_FP_MINMAX = .{
    .min = 0b000,
    .max = 0b001,
};

pub const OP_FP_CVT = .{
    .w = 0b00000,
    .wu = 0b00001,
    .l = 0b00010,
    .lu = 0b00011,
};

pub const OP_FP_CMP = .{
    .le = 0b000,
    .lt = 0b001,
    .eq = 0b010,
};

pub const OP_FP_CLASS = .{
    .mv_x_w = 0b000,
    .class = 0b001,
};

pub const BRANCH = .{
    .beq = 0b000,
    .bne = 0b001,
    .blt = 0b100,
    .bge = 0b101,
    .bltu = 0b110,
    .bgeu = 0b111,
};

pub const SYSTEM = .{
    .priv = 0b000,
    .csrrw = 0b001,
    .csrrs = 0b010,
    .csrrc = 0b011,
    .csrrwi = 0b101,
    .csrrsi = 0b110,
    .csrrci = 0b111,
};

pub const PRIV = .{
    .ecall = 0b000000000000,
    .ebreak = 0b000000000001,
    .sret = 0b000100000010,
    .mret = 0b001100000010,
    .wfi = 0b000100000101,
    .sfence_vma = 0b0001001,
};

pub const REGISTER = std.StaticStringMap(u5).initComptime(.{
    .{ "zero", 0 },
    .{ "ra", 1 },
    .{ "sp", 2 },
    .{ "gp", 3 },
    .{ "tp", 4 },
    .{ "t0", 5 },
    .{ "t1", 6 },
    .{ "t2", 7 },
    .{ "s0", 8 },
    .{ "fp", 8 },
    .{ "s1", 9 },
    .{ "a0", 10 },
    .{ "a1", 11 },
    .{ "a2", 12 },
    .{ "a3", 13 },
    .{ "a4", 14 },
    .{ "a5", 15 },
    .{ "a6", 16 },
    .{ "a7", 17 },
    .{ "s2", 18 },
    .{ "s3", 19 },
    .{ "s4", 20 },
    .{ "s5", 21 },
    .{ "s6", 22 },
    .{ "s7", 23 },
    .{ "s8", 24 },
    .{ "s9", 25 },
    .{ "s10", 26 },
    .{ "s11", 27 },
    .{ "t3", 28 },
    .{ "t4", 29 },
    .{ "t5", 30 },
    .{ "t6", 31 },
});

pub const XLEN = .{
    .@"32" = 1,
    .@"64" = 2,
    .@"128" = 3,
};

pub const PRIVILEGE = .{
    .u = 0b00,
    .s = 0b01,
    .m = 0b11,
};

pub const CSR = .{
    .fflags = 0x001,
    .frm = 0x002,
    .fcsr = 0x003,
    .cycle = 0xC00,
    .time = 0xC01,
    .instret = 0xC02,

    .sstatus = 0x100,
    .sie = 0x104,
    .stvec = 0x105,
    .scounteren = 0x106,
    .senvcfg = 0x10A,
    .sscratch = 0x140,
    .sepc = 0x141,
    .scause = 0x142,
    .stval = 0x143,
    .sip = 0x144,
    .stimecmp = 0x14D,
    .scountovf = 0xDA0,
    .satp = 0x180,

    .mvendorid = 0xF11,
    .marchid = 0xF12,
    .mimpid = 0xF13,
    .mhartid = 0xF14,
    .mconfigptr = 0xF15,
    .mstatus = 0x300,
    .misa = 0x301,
    .medeleg = 0x302,
    .mideleg = 0x303,
    .mie = 0x304,
    .mtvec = 0x305,
    .mcounteren = 0x306,
    .mscratch = 0x340,
    .mepc = 0x341,
    .mcause = 0x342,
    .mtval = 0x343,
    .mip = 0x344,
    .menvcfg = 0x30A,
    .mcycle = 0xB00,
    .minstret = 0xB02,
    .mcountinhibit = 0x320,
    .pmpcfg0 = 0x3A0,
    .pmpcfg15 = 0x3AF,
    .pmpaddr0 = 0x3B0,
    .pmpaddr63 = 0x3EF,
};

pub const CSR_NAMES = std.StaticStringMap(u12).initComptime(blk: {
    const fields = @typeInfo(@TypeOf(CSR)).@"struct".fields;
    var kvs = std.mem.zeroes([fields.len]struct { []const u8, u12 });
    for (0.., fields) |i, field| {
        kvs[i] = .{ field.name, @field(CSR, field.name) };
    }
    break :blk kvs;
});

pub const MIP = .{
    .ssi = 1,
    .msi = 3,
    .sti = 5,
    .mti = 7,
    .sei = 9,
    .mei = 11,
    .coi = 13,
};

pub const EXCEPTION = .{
    .ssi = 0x8000000000000001,
    .msi = 0x8000000000000003,
    .sti = 0x8000000000000005,
    .mti = 0x8000000000000007,
    .sei = 0x8000000000000009,
    .mei = 0x800000000000000B,
    .coi = 0x800000000000000D,
    .iam = 0x0,
    .iaf = 0x1,
    .ill = 0x2,
    .brk = 0x3,
    .lam = 0x4,
    .laf = 0x5,
    .sam = 0x6,
    .saf = 0x7,
    .envu = 0x8,
    .envs = 0x9,
    .envm = 0xB,
    .ipf = 0xC,
    .lpf = 0xD,
    .spf = 0xF,
    .sck = 0x12,
    .herr = 0x13,
};

pub const SATP_MODE = .{
    .bare = 0,
    .sv39 = 8,
    // .sv48 = 9,
    // .sv57 = 10,
};

pub const RType = packed struct {
    opcode: u7,
    rd: u5,
    func3: u3,
    rs1: u5,
    rs2: u5,
    func7: u7,

    pub fn amoFunct7(self: RType) AmoFunct7 {
        return @bitCast(self.func7);
    }

    pub fn opFpFunct7(self: RType) OpFpFunct7 {
        return @bitCast(self.func7);
    }

    pub fn immSgnExt(self: RType) u64 {
        const imm_swizzled: u12 = @intCast(swizzle(
            self,
            .{ .func7 = .{ 11, 10, 9, 8, 7, 6, 5 }, .rd = .{ 4, 3, 2, 1, 0 } },
        ));
        return @bitCast(@as(i64, @as(i12, @bitCast(imm_swizzled))));
    }

    pub fn immBType(self: RType) u64 {
        const offset: u13 = @intCast(swizzle(
            self,
            .{ .func7 = .{ 12, 10, 9, 8, 7, 6, 5 }, .rd = .{ 4, 3, 2, 1, 11 } },
        ));
        return @bitCast(@as(i64, @as(i13, @bitCast(offset))));
    }
};

pub const AmoFunct7 = packed struct {
    rl: u1,
    aq: u1,
    func5: u5,
};

pub const OpFpFunct7 = packed struct {
    fmt: u2,
    func5: u5,
};

pub const IType = packed struct {
    opcode: u7,
    rd: u5,
    func3: u3,
    rs1: u5,
    imm: i12,

    pub fn immUnsigned(self: IType) u12 {
        return @bitCast(self.imm);
    }

    pub fn immSgnExt(self: IType) u64 {
        return @bitCast(@as(i64, self.imm));
    }

    pub fn immShift32(self: IType) packed struct { l: u5, h: u7 } {
        return @bitCast(self.imm);
    }

    pub fn immShift64(self: IType) packed struct { l: u6, h: u6 } {
        return @bitCast(self.imm);
    }
};

pub const UType = packed struct {
    opcode: u7,
    rd: u5,
    imm: u20,

    pub fn immJType(self: UType) u64 {
        const offset: u21 = @intCast(swizzle(
            self,
            .{ .imm = .{ 20, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 11, 19, 18, 17, 16, 15, 14, 13, 12 } },
        ));
        return @as(u64, @bitCast(@as(i64, @as(i21, @bitCast(offset)))));
    }
};

pub const CRType = packed struct {
    opcode: u2,
    rs2: u5,
    rs1: u5,
    funct4: u4,
};

pub const CIType = packed struct {
    opcode: u2,
    imml: u5,
    rs1: u5,
    immh: u1,
    funct3: u3,

    pub fn imm(self: CIType) u6 {
        return @intCast(swizzle(self, .{ .immh = .{5}, .imml = .{ 4, 3, 2, 1, 0 } }));
    }

    pub fn immSgnExt(self: CIType) u64 {
        const i: u6 = @intCast(swizzle(self, .{ .immh = .{5}, .imml = .{ 4, 3, 2, 1, 0 } }));
        return @bitCast(@as(i64, @as(i6, @bitCast(i))));
    }

    pub fn imm_addi16sp(self: CIType) u64 {
        const i: u10 = @intCast(swizzle(self, .{ .immh = .{9}, .imml = .{ 4, 6, 8, 7, 5 } }));
        return @bitCast(@as(i64, @as(i10, @bitCast(i))));
    }

    pub fn offset_lw(self: CIType) u64 {
        return swizzle(self, .{ .immh = .{5}, .imml = .{ 4, 3, 2, 7, 6 } });
    }

    pub fn offset_ld(self: CIType) u64 {
        return swizzle(self, .{ .immh = .{5}, .imml = .{ 4, 3, 8, 7, 6 } });
    }
};

pub const CSSType = packed struct {
    opcode: u2,
    rs2: u5,
    imm: u6,
    funct3: u3,

    pub fn offset_sw(self: CSSType) u64 {
        return swizzle(self, .{ .imm = .{ 5, 4, 3, 2, 7, 6 } });
    }

    pub fn offset_sd(self: CSSType) u64 {
        return swizzle(self, .{ .imm = .{ 5, 4, 3, 8, 7, 6 } });
    }
};

pub const CIWType = packed struct {
    opcode: u2,
    rd_3: u3,
    imm: u8,
    funct3: u3,

    pub fn rd(self: CIWType) u5 {
        return @as(u5, self.rd_3) + 8;
    }

    pub fn imm_addi4spn(self: CIWType) u64 {
        return swizzle(self, .{ .imm = .{ 5, 4, 9, 8, 7, 6, 2, 3 } });
    }
};

pub const CLType = packed struct {
    opcode: u2,
    rd_3: u3,
    imml: u2,
    rs1_3: u3,
    immh: u3,
    funct3: u3,

    pub fn rd(self: CLType) u5 {
        return @as(u5, self.rd_3) + 8;
    }

    pub fn rs1(self: CLType) u5 {
        return @as(u5, self.rs1_3) + 8;
    }

    pub fn offset_lw(self: CLType) u64 {
        return swizzle(self, .{ .immh = .{ 5, 4, 3 }, .imml = .{ 2, 6 } });
    }

    pub fn offset_ld(self: CLType) u64 {
        return swizzle(self, .{ .immh = .{ 5, 4, 3 }, .imml = .{ 7, 6 } });
    }
};

pub const CAType = packed struct {
    opcode: u2,
    rs2_3: u3,
    funct2: u2,
    rs1_3: u3,
    funct6: u6,

    pub fn rs1(self: CAType) u5 {
        return @as(u5, self.rs1_3) + 8;
    }

    pub fn rs2(self: CAType) u5 {
        return @as(u5, self.rs2_3) + 8;
    }
};

pub const CBType = packed struct {
    opcode: u2,
    offsetl: u5,
    rs1_3: u3,
    offseth: u3,
    funct3: u3,

    pub fn rs1(self: CBType) u5 {
        return @as(u5, self.rs1_3) + 8;
    }

    pub fn shamt(self: CBType) u6 {
        return @intCast(swizzle(self, .{ .offseth = .{5}, .offsetl = .{ 4, 3, 2, 1, 0 } }));
    }

    pub fn immSgnExt(self: CBType) u64 {
        return @bitCast(@as(i64, @as(i6, @bitCast(self.shamt()))));
    }

    pub fn offset(self: CBType) u64 {
        const offs: u9 = @intCast(swizzle(
            self,
            .{ .offseth = .{ 8, 4, 3 }, .offsetl = .{ 7, 6, 2, 1, 5 } },
        ));
        return @bitCast(@as(i64, @as(i9, @bitCast(offs))));
    }
};

pub const CJType = packed struct {
    opcode: u2,
    target: u11,
    funct3: u3,

    pub fn offset(self: CJType) u64 {
        const offs: u12 = @intCast(swizzle(
            self,
            .{ .target = .{ 11, 4, 9, 8, 10, 6, 7, 3, 2, 1, 5 } },
        ));
        return @bitCast(@as(i64, @as(i12, @bitCast(offs))));
    }
};

/// Construct an integer by swizzling the bits of a bitpacked instruction.
///
/// `swiz` is an anonymous struct of the format:
/// .{ .immh = .{ 5 } .imml = .{ 4, 3, 2, 7, 6 } }
pub fn swizzle(instr: anytype, comptime swiz: anytype) u32 {
    const swizzle_ty = @typeInfo(@TypeOf(swiz));

    var result: u32 = 0;
    inline for (swizzle_ty.@"struct".fields) |field| {
        const src = @field(instr, field.name);
        const mapping = @field(swiz, field.name);

        inline for (1.., mapping) |inv_bit, map| {
            const bit = 1 << (@typeInfo(@TypeOf(src)).int.bits - inv_bit);
            result |= @as(u32, @intFromBool(src & bit != 0)) << map;
        }
    }

    return result;
}

pub const SYSCON = .{
    .fail = 0x3333,
    .pass = 0x5555,
    .reboot = 0x7777,
};

pub const ACLINT = .{
    .mtime = 0,
    .mtimecmp = 0x1000,
};
