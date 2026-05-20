.section .text.init
.global _start
_start:
        la sp, _end
        jal main
        li t0, 0x100000
        li t1, 0x5555
        sw t1, 0(t0)
