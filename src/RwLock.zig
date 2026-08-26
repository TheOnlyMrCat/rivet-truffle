// SPDX-FileCopyrightText: © 2025 Max Guppy <theonly@mrcat.au>
//
// SPDX-License-Identifier: MPL-2.0

const std = @import("std");
const RwLock = @This();

const Flags = packed struct(u64) {
    lock_exclusive: bool,
    reader_count: u63,
};

state: std.atomic.Value(u64) = .{ .raw = 0 },

pub fn lockExclusive(self: *RwLock) void {
    var orig_state: Flags = @bitCast(self.state.load(.acquire));

    while (true) {
        while (orig_state.lock_exclusive) {
            orig_state = @bitCast(self.state.load(.acquire));
        }

        var new_state = orig_state;
        new_state.lock_exclusive = true;
        if (self.state.cmpxchgWeak(@bitCast(orig_state), @bitCast(new_state), .acquire, .acquire)) |new_orig| {
            orig_state = @bitCast(new_orig);
            continue;
        }
        orig_state = new_state;
        break;
    }

    while (orig_state.reader_count > 0) {
        orig_state = @bitCast(self.state.load(.acquire));
    }
}

pub fn unlockExclusive(self: *RwLock) void {
    var state: Flags = @bitCast(self.state.load(.monotonic));
    std.debug.assert(state.lock_exclusive and state.reader_count == 0);

    state.lock_exclusive = false;
    self.state.store(@bitCast(state), .release);
}

pub fn lockShared(self: *RwLock) void {
    var orig_state: Flags = @bitCast(self.state.load(.acquire));

    while (true) {
        while (orig_state.lock_exclusive) {
            orig_state = @bitCast(self.state.load(.acquire));
        }

        var new_state = orig_state;
        new_state.reader_count += 1;
        if (self.state.cmpxchgWeak(@bitCast(orig_state), @bitCast(new_state), .acquire, .acquire)) |new_orig| {
            orig_state = @bitCast(new_orig);
            continue;
        }
        break;
    }
}

pub fn unlockShared(self: *RwLock) void {
    var orig_state: Flags = @bitCast(self.state.load(.monotonic));
    std.debug.assert(orig_state.reader_count > 0);

    while (true) {
        var new_state = orig_state;
        new_state.reader_count -= 1;
        if (self.state.cmpxchgWeak(@bitCast(orig_state), @bitCast(new_state), .release, .monotonic)) |new_orig| {
            orig_state = @bitCast(new_orig);
            continue;
        }
        break;
    }
}
