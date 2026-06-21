.globl _start
_start:
        li a0, 16777216
1:
        addi a0, a0, -1
        jal dummy
        bnez a0, 1b
exit:
        li t0, 0x100000
        li t1, 0x5555
        sw t1, 0(t0)
dummy:
        ret
