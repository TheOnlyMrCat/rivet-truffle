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
#define RVMODEL_HALT_PASS  \
  li a7, 93;               \
  la a0, 0;                \
  ecall;

# Terminate test with a fail indication.
# When the test is run in simulation, this should end the simulation.
#define RVMODEL_HALT_FAIL \
  li a7, 93;              \
  la a0, 1;               \
  ecall;

##### IO #####

# Prints a null-terminated string using a DUT specific mechanism.
# A pointer to the string is passed in _STR_PTR.
# _R1, _R2, and _R3 can be used as temporary registers if needed.
# Do not modify any other registers (or make sure to restore them).
#define RVMODEL_IO_WRITE_STR(_R1, _R2, _R3, _STR_PTR)               \
  /* save registers */                                \
  la _R1, tempr4;                                     \
  sd a7, 0(_R1);                                      \
  mv _R1, a0;                                         \
  mv _R2, a1;                                         \
  mv _R3, a2;                                         \
  /* set up syscall */                                \
  li a7, 64; /* sys_write */                          \
  mv a1, _STR_PTR; /* buf parameter */                \
  li a2, 0; /* count parameter */                     \
1: /* strlen */                                       \
  lbu a0, 0(_STR_PTR); /* Load byte */                \
  beqz a0, 2f; /* Exit if null */                     \
  addi a2, a2, 1; /* Increment count */               \
  addi _STR_PTR, _STR_PTR, 1; /* Next char */         \
  j 1b; /* Loop */                                    \
2: /* syscall */                                      \
  li a0, 1; /* fd parameter (stdout) */               \
  ecall;                                              \
  /* restore registers */                             \
  mv a2, _R3;                                         \
  mv a1, _R2;                                         \
  mv a0, _R1;                                         \
  la _R1, tempr4;                                     \
  ld a7, 0(_R1);

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
