all: build/decloop.elf build/decloop_dummycall.elf

AS := riscv64-unknown-elf-as
LD := riscv64-unknown-elf-ld

build/decloop.o: decloop.s
	@mkdir -p $(@D)
	$(AS) decloop.s -o build/decloop.o

build/decloop.elf: link.ld build/decloop.o
	@mkdir -p $(@D)
	$(LD) -T link.ld build/decloop.o -o build/decloop.elf

build/decloop_dummycall.o: decloop_dummycall.s
	@mkdir -p $(@D)
	$(AS) decloop_dummycall.s -o build/decloop_dummycall.o

build/decloop_dummycall.elf: link.ld build/decloop_dummycall.o
	@mkdir -p $(@D)
	$(LD) -T link.ld build/decloop_dummycall.o -o build/decloop_dummycall.elf
