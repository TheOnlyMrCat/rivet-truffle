.section .text.init
.global _start
_start:
        # Give U-mode access to the time CSR
        li t0, 0b10
        csrw mcounteren, t0
        csrw scounteren, t0

        # Set up a hugepage identity mapping
        la t0, root_page
        srli t0, t0, 12
        li t1, 0x8000000000000000
        or t0, t1, t0
        csrw satp, t0

        # Ret to U-mode
        la t0, u_start
        csrw mepc, t0
        li t1, 0b1100000000000
        csrc mstatus, t1
        mret

u_start:
        # Set up the stack pointer
        la sp, _end

        # Call main
        jal main

        # Terminate the emulator
        li t0, 0x100000
        li t1, 0x5555
        sw t1, 0(t0)

.section .data
.p2align 12
root_page:
        .8byte 0x00000000000000ff
        .8byte 0x00000000100000ff
        .8byte 0x00000000200000ff
        .8byte 0x00000000300000ff
        .8byte 0x00000000400000ff
        .8byte 0x00000000500000ff
        .8byte 0x00000000600000ff
        .8byte 0x00000000700000ff
        .8byte 0x00000000800000ff
        .8byte 0x00000000900000ff
        .8byte 0x00000000a00000ff
        .8byte 0x00000000b00000ff
        .8byte 0x00000000c00000ff
        .8byte 0x00000000d00000ff
        .8byte 0x00000000e00000ff
        .8byte 0x00000000f00000ff
