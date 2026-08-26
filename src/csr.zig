// SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");
const riscv = @import("riscv.zig");
const Hart = @import("Hart.zig");
const Trap = Hart.Trap;

pub const CsrMapping = packed struct {
    /// The remainder of the address
    address: u8,
    /// The lowest privilege level that can access the CSR
    privilege: u2,
    /// Whether the register is read-only (0b11) or read-write (anything else)
    access: u2,
};

pub fn Field(comptime T: type, comptime toLegal: fn (comptime U: type, prev: T, new: T) T) type {
    return packed struct {
        value: T,

        const backing_type = T;

        pub fn write(self: anytype, value: T) void {
            self.value = toLegal(T, self.value, value);
        }
    };
}

pub fn discard(comptime T: type, prev: T, _: T) T {
    return prev;
}

pub fn keep(comptime T: type, _: T, new: T) T {
    return new;
}

pub fn keep_ialigned(comptime T: type, _: T, new: T) T {
    return new & 0xFFFFFFFFFFFFFFFE;
}

pub fn keep_only(comptime T: type, comptime values: anytype) fn (comptime U: type, prev: T, new: T) T {
    const value_type = @typeInfo(@TypeOf(values));
    return struct {
        fn function(comptime _: type, prev: T, new: T) T {
            if (value_type.@"struct".is_tuple) {
                inline for (values) |value| {
                    if (new == value) {
                        return new;
                    }
                }
            } else {
                inline for (value_type.@"struct".fields) |field| {
                    if (new == @field(values, field.name)) {
                        return new;
                    }
                }
            }
            return prev;
        }
    }.function;
}

pub fn Wpri(comptime T: type) type {
    return Field(T, keep);
}

pub fn CsRegister(comptime T: type) type {
    const info = @typeInfo(T);
    std.debug.assert(info.@"struct".layout == std.builtin.Type.ContainerLayout.@"packed");

    const CsrField = @Type(std.builtin.Type{ .@"enum" = .{
        .tag_type = u8,
        .fields = blk: {
            var fields: [info.@"struct".fields.len]std.builtin.Type.EnumField = undefined;
            for (info.@"struct".fields, 0..) |field, i| {
                fields[i] = std.builtin.Type.EnumField{
                    .name = field.name,
                    .value = i,
                };
            }
            break :blk &fields;
        },
        .decls = &[0]std.builtin.Type.Declaration{},
        .is_exhaustive = true,
    } });

    const IntRepr = @Type(std.builtin.Type{ .int = .{ .signedness = .unsigned, .bits = @bitSizeOf(T) } });

    return packed struct {
        fields: T,

        const Self = @This();

        pub fn init(values: T) Self {
            return @bitCast(values);
        }

        pub fn read(self: *Self) IntRepr {
            return @bitCast(self.*);
        }

        pub fn write(self: *Self, value: IntRepr) void {
            const values: T = @bitCast(value);
            inline for (info.@"struct".fields) |field| {
                @field(self.fields, field.name).write(@bitCast(@field(values, field.name)));
            }
        }

        pub fn get(
            self: *Self,
            comptime field: CsrField,
        ) info.@"struct".fields[@intFromEnum(field)].type.backing_type {
            return @field(self.fields, info.@"struct".fields[@intFromEnum(field)].name).value;
        }

        pub fn set(
            self: *Self,
            comptime field: CsrField,
            new_value: info.@"struct".fields[@intFromEnum(field)].type.backing_type,
        ) void {
            @field(self.fields, info.@"struct".fields[@intFromEnum(field)].name).value = new_value;
        }
    };
}
