all: build/decloop.elf build/decloop_dummycall.elf

build/decloop.o: decloop.s
	riscv64-unknown-elf-as decloop.s -o build/decloop.o

build/decloop.elf: link.ld build/decloop.o
	riscv64-unknown-elf-ld -T link.ld build/decloop.o -o build/decloop.elf

build/decloop_dummycall.o: decloop_dummycall.s
	riscv64-unknown-elf-as decloop_dummycall.s -o build/decloop_dummycall.o

build/decloop_dummycall.elf: link.ld build/decloop_dummycall.o
	riscv64-unknown-elf-ld -T link.ld build/decloop_dummycall.o -o build/decloop_dummycall.elf
