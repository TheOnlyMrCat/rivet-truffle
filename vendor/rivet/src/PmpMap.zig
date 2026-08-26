// SPDX-FileCopyrightText: © 2024 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");
const Hart = @import("Hart.zig");
const MachineCsr = @import("MachineCsr.zig");

const PmpMap = @This();

pub const Access = struct {
    read: bool,
    write: bool,
    execute: bool,
    locked: bool,
};

const Region = packed struct {
    r: u1,
    x: u1,
    low_addr: u62,
    w: u1,
    l: u1,
    high_addr: u62,

    pub fn init(permissions: anytype, low_addr: u64, high_addr: u64) Region {
        return Region{
            .r = permissions.r,
            .w = permissions.w,
            .x = permissions.x,
            .l = permissions.l,
            .low_addr = @intCast(@shrExact(low_addr, 2)),
            .high_addr = @intCast(@shrExact(high_addr, 2)),
        };
    }

    pub fn lowAddr(self: Region) u64 {
        return @as(u64, self.low_addr) << 2;
    }

    pub fn setLowAddr(self: *Region, low_addr: u64) void {
        self.low_addr = @intCast(@shrExact(low_addr, 2));
    }

    pub fn highAddr(self: Region) u64 {
        return @as(u64, self.high_addr) << 2;
    }

    pub fn setHighAddr(self: *Region, high_addr: u64) void {
        self.high_addr = @intCast(@shrExact(high_addr, 2));
    }

    pub fn access(self: Region) Access {
        return Access{
            .read = self.r == 1,
            .write = self.w == 1,
            .execute = self.x == 1,
            .locked = self.l == 1,
        };
    }
};

const Node = struct {
    region: Region,
    left: ?*Node = null,
    right: ?*Node = null,
};

root: *Node,
arena: std.heap.ArenaAllocator,

pub fn init(base_allocator: std.mem.Allocator) PmpMap {
    var arena = std.heap.ArenaAllocator.init(base_allocator);
    const allocator = arena.allocator();

    const root = allocator.create(Node) catch unreachable;
    root.* = Node{
        .region = .{
            .r = 0,
            .w = 0,
            .x = 0,
            .l = 0,
            .low_addr = 0,
            .high_addr = std.math.maxInt(u62),
        },
    };

    return PmpMap{
        .root = root,
        .arena = arena,
    };
}

pub fn deinit(self: *PmpMap) void {
    self.arena.deinit();
}

fn searchOverlapping(root: *Node, low: u64, high: u64) *Node {
    if (high <= root.region.lowAddr()) {
        return @call(.always_tail, searchOverlapping, .{ root.left.?, low, high });
    }
    if (root.region.highAddr() < low) {
        return @call(.always_tail, searchOverlapping, .{ root.right.?, low, high });
    }
    return root;
}

pub fn reconstruct(self: *PmpMap, csr: *MachineCsr) void {
    std.debug.assert(self.arena.reset(.retain_capacity));
    const allocator = self.arena.allocator();

    self.root = allocator.create(Node) catch unreachable;
    self.root.* = Node{
        .region = .{
            .r = 0,
            .w = 0,
            .x = 0,
            .l = 0,
            .low_addr = 0,
            .high_addr = std.math.maxInt(u62),
        },
    };

    for (0..8) |rev_register| {
        const register = 7 - rev_register;
        inline for (0..8) |rev_entry| {
            const entry = 7 - rev_entry;
            const pmp = csr.pmpcfg[register].get(@enumFromInt(entry));
            const pmpaddr = @as(u64, csr.pmpaddr[register * 8 + entry].get(.address4));

            const range: ?struct { low: u64, high: u64 } = switch (pmp.a) {
                0b00 => null, // Off -- dummy range
                0b01 => .{ // Top of Range
                    .low = if (register == 0 and entry == 0)
                        0
                    else
                        @as(u64, csr.pmpaddr[register * 8 + entry - 1].get(.address4)),
                    .high = pmpaddr,
                },
                0b10 => .{ // 4-byte range
                    .low = pmpaddr,
                    .high = pmpaddr + 1,
                },
                0b11 => .{ // Naturally-aligned power-of-two range
                    .low = pmpaddr & pmpaddr + 1,
                    .high = (pmpaddr | pmpaddr + 1) + 1,
                },
            };
            if (range) |range_addrs| {
                const low_addr = range_addrs.low << 2;
                const high_addr = range_addrs.high << 2;

                // Find the rootmost node that our insertion node is overlapping. We're eventually
                // going to take this node's place in the tree.
                const overlap_root = searchOverlapping(self.root, low_addr, high_addr);

                // On the left, find the node that contains our lower bound.
                const left_insertion = searchOverlapping(overlap_root, low_addr, low_addr + 1);
                if (left_insertion == overlap_root) {
                    // If it's the node we're already replacing, split it in two.
                    if (overlap_root.region.lowAddr() < low_addr) {
                        // If there's something left over, insert it as our new node's left child
                        const left_remainder = Node{
                            .region = Region.init(
                                overlap_root.region,
                                overlap_root.region.lowAddr(),
                                low_addr,
                            ),
                            .left = overlap_root.left,
                        };
                        overlap_root.left = allocator.create(Node) catch unreachable;
                        overlap_root.left.?.* = left_remainder;
                    }
                    // If there's nothing left, we'll just inherit overlap_root's left child when we
                    // replace it
                } else {
                    // Otherwise, if it isn't the node we're replacing, just shrink it.
                    left_insertion.region.setHighAddr(low_addr);

                    // But get rid of those nodes that we fully overlap on the left
                    left_insertion.right = null;
                    while (low_addr < overlap_root.left.?.region.lowAddr()) {
                        overlap_root.left = overlap_root.left.?.left;
                    }
                }

                // Do the same thing on the right
                const right_insertion = searchOverlapping(overlap_root, high_addr, high_addr);
                if (right_insertion == overlap_root) {
                    if (high_addr < overlap_root.region.highAddr()) {
                        const right_remainder = Node{
                            .region = Region.init(
                                overlap_root.region,
                                high_addr,
                                overlap_root.region.highAddr(),
                            ),
                            .right = overlap_root.right,
                        };
                        overlap_root.right = allocator.create(Node) catch unreachable;
                        overlap_root.right.?.* = right_remainder;
                    }
                } else {
                    right_insertion.region.setLowAddr(high_addr);
                    right_insertion.left = null;
                    while (overlap_root.right.?.region.highAddr() < high_addr) {
                        overlap_root.right = overlap_root.right.?.right;
                    }
                }

                // Finally, take over the root overlap we found.
                overlap_root.region = Region.init(pmp, low_addr, high_addr);
            }
        }
    }
}

fn rotateRight(pivot: **Node) void {
    const middle = pivot.*.left.?.right;
    pivot.*.left.?.right = pivot.*;
    pivot.* = pivot.*.left.?;
    pivot.*.right.?.left = middle;
}

fn rotateLeft(pivot: **Node) void {
    const middle = pivot.*.right.?.left;
    pivot.*.right.?.left = pivot.*;
    pivot.* = pivot.*.right.?;
    pivot.*.left.?.right = middle;
}

fn splayAt(root: **Node, addr: u64) void {
    if (addr < root.*.region.lowAddr()) {
        if (root.*.left) |*left| {
            if (addr < left.*.region.lowAddr()) {
                // Zig-zig
                splayAt(&left.*.left.?, addr);
                rotateRight(root);
                rotateRight(root);
                return;
            }
            if (left.*.region.highAddr() <= addr) {
                // Zag-zig
                splayAt(&left.*.right.?, addr);
                rotateLeft(left);
                rotateRight(root);
                return;
            }
        }
        // Zig
        rotateRight(root);
        return;
    }
    if (root.*.region.highAddr() <= addr) {
        if (root.*.right) |*right| {
            if (right.*.region.highAddr() <= addr) {
                // Zag-zag
                splayAt(&right.*.right.?, addr);
                rotateLeft(root);
                rotateLeft(root);
                return;
            }
            if (addr < right.*.region.lowAddr()) {
                // Zig-zag
                splayAt(&right.*.left.?, addr);
                rotateRight(right);
                rotateLeft(root);
                return;
            }
        }
        // Zag
        rotateLeft(root);
        return;
    }
}

pub fn get(self: *PmpMap, addr: u64, len: u64) ?Access {
    splayAt(&self.root, addr);
    if (addr +| len < self.root.region.highAddr()) {
        return self.root.region.access();
    }
    return null;
}
