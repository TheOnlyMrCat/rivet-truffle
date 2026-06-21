OUTPUT_ARCH( "riscv" )
ENTRY(_start)

SECTIONS
{
  . = 0x80000000;
  .text : { *(.text) *(.text.*) }
  . = ALIGN(0x4000);
  .data : { *(.data) }
  . = ALIGN(0x100000);
  _end = .;
}
