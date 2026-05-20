#ifndef _RVMODEL_MACROS_H
#define _RVMODEL_MACROS_H

#define RVMODEL_DATA_SECTION \
        .pushsection .tempr4,"aw",@progbits;                \
        .align 8; .global tempr4; tempr4: .dword 0;         \
        .popsection;

##### STARTUP #####

# Perform boot operations. Can be empty or left undefined unless needed for
# DUT-specific behavior such as turning on a memory controller or
# initializing custom state.
//#define RVMODEL_BOOT

// Custom RVMODEL_BOOT_TO_MMODE overrides default RVTEST_BOOT_TO_MMODE
// if defined.  For most DUTs, the default should work and this macro
// should not be defined.  If no M-mode or CSRs are implemented, define this
// macro as blank to bypass the boot process.  If a nonconforming
// M-mode is implemented, define this macro to set up the necessary
// state in a fashion similar to RVTEST_BOOT_TO_MMODE.
//#define RVMODEL_BOOT_TO_MMODE

##### TERMINATION #####

# Terminate test with a pass indication.
# When the test is run in simulation, this should end the simulation.
#define RVMODEL_HALT_PASS \
  li t0, 0x100000;        \
  li t1, 0x5555;          \
  sw t1, 0(t0);

# Terminate test with a fail indication.
# When the test is run in simulation, this should end the simulation.
#define RVMODEL_HALT_FAIL \
  li t0, 0x100000;        \
  li t1, 0x13333;         \
  sw t1, 0(t0);

##### IO #####

.EQU UART_BASE_ADDR, 0x10000000
.EQU UART_TXDATA, (UART_BASE_ADDR + 0)
.EQU UART_TXCTRL, (UART_BASE_ADDR + 8)

# Initialization steps needed prior to writing to the console
# _R1, _R2, and _R3 can be used as temporary registers if needed.
# Do not modify any other registers (or make sure to restore them).
# Can be empty or left undefined if no initialization is needed.
#define RVMODEL_IO_INIT(_R1, _R2, _R3) \
  uart_init:                           \
    li _R1, UART_TXCTRL;               \
    li _R2, 1;                         \
    sw _R2, 0(_R1);

# Prints a null-terminated string using a DUT specific mechanism.
# A pointer to the string is passed in _STR_PTR.
# _R1, _R2, and _R3 can be used as temporary registers if needed.
# Do not modify any other registers (or make sure to restore them).
#define RVMODEL_IO_WRITE_STR(_R1, _R2, _R3, _STR_PTR) \
1:                                                    \
  lbu _R1, 0(_STR_PTR);        /* Load byte */        \
  beqz _R1, 3f;                /* Exit if null */     \
2: /* uart_putc */                                    \
    li _R2, UART_TXDATA; /* transmit character */     \
    sw _R1, 0(_R2) ;                                  \
  addi _STR_PTR, _STR_PTR, 1 ;/* Next char */         \
  j 1b                       ;/* Loop */              \
3:

##### Access Fault #####

#define RVMODEL_ACCESS_FAULT_ADDRESS 0x00000000

##### Interrupt Latency #####

#define RVMODEL_INTERRUPT_LATENCY 10

##### Machine Timer #####

#define RVMODEL_TIMER_INT_SOON_DELAY 100

#define RVMODEL_MTIME_ADDRESS  0x0200BFF8  /* Address of mtime CSR */

#define RVMODEL_MTIMECMP_ADDRESS 0x02004000 /* Address of mtimecmp CSR */

##### Machine Interrupts #####

#define CLINT_BASE_ADDRESS 0x02000000
#define RVMODEL_MSIP_ADDRESS (CLINT_BASE_ADDRESS + 0x0)

#define RVMODEL_SET_MEXT_INT(_R1, _R2)

#define RVMODEL_CLR_MEXT_INT(_R1, _R2)

#define RVMODEL_SET_MSW_INT(_R1, _R2) \
  li _R1, 1; \
  li _R2, RVMODEL_MSIP_ADDRESS; \
  sw _R1, 0(_R2);

#define RVMODEL_CLR_MSW_INT(_R1, _R2) \
  li _R2, RVMODEL_MSIP_ADDRESS; \
  sw zero, 0(_R2);

##### Supervisor Interrupts #####

#define CVW_SSIP_ADDRESS (CLINT_BASE_ADDRESS + 0xC000)

#define RVMODEL_SET_SEXT_INT(_R1, _R2)

#define RVMODEL_CLR_SEXT_INT(_R1, _R2)

#define RVMODEL_SET_SSW_INT(_R1, _R2) \
  li _R1, 1; \
  li _R2, CVW_SSIP_ADDRESS; \
  sw _R1, 0(_R2);

#define RVMODEL_CLR_SSW_INT(_R1, _R2) \
  li _R2, CVW_SSIP_ADDRESS; \
  sw zero, 0(_R2);

#endif // _RVMODEL_MACROS_H
