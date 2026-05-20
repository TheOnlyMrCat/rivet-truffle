package au.mrcat.rivet.riscv;

public enum MemoryWidth {
    Byte(1, 0xffL),
    HalfWord(2, 0xffffL),
    Word(4, 0xffffffffL),
    DoubleWord(8, -1L);

    public final int bytes;
    public final long mask;

    MemoryWidth(int bytes, long mask) {
        this.bytes = bytes;
        this.mask = mask;
    }

    public boolean isNaturallyAligned(long address) {
        return (address & bytes - 1) == 0;
    }
}
