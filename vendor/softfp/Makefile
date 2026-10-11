CC=gcc
CFLAGS=-O2 -Wall -g -Werror -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -MMD -fpic -shared
CFLAGS+=-DCONFIG_VERSION=\"$(shell cat VERSION)\"
LDFLAGS=-fpic -shared

PROGS=softfptest
LIBS=libsoftfp.so

all: $(LIBS)

softfptest: softfptest.o softfp.o softfloat.o
	$(CC) $(LDFLAGS) -o $@ $^ -lm

libsoftfp.so: softfp.o softfloat.o
	$(CC) $(LDFLAGS) -o $@ $^

%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

clean:
	rm -f *.o *.d *~ $(PROGS)

-include $(wildcard *.d)

