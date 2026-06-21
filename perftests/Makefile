all: build/decloop.elf

build/decloop.o: decloop.s
	riscv64-unknown-elf-as decloop.s -o build/decloop.o

build/decloop.elf: link.ld build/decloop.o
	riscv64-unknown-elf-ld -T link.ld build/decloop.o -o build/decloop.elf
