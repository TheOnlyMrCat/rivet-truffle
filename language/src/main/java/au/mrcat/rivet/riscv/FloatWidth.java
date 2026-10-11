package au.mrcat.rivet.riscv;

public enum FloatWidth {
    Single(0b00),
    RawSingle(0b00),
    Double(0b01);

    private final int encoding;

    FloatWidth(int encoding) {
        this.encoding = encoding;
    }

    public static FloatWidth fromEncoding(int encoding) {
        return switch (encoding) {
            case 0b00 -> Single;
            case 0b01 -> Double;
            default -> null;
        };
    }
}
