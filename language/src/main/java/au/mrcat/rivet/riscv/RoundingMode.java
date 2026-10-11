package au.mrcat.rivet.riscv;

public enum RoundingMode {
    RoundNearestTiesEven(0),
    RoundTowardsZero(1),
    RoundDown(2),
    RoundUp(3),
    RoundNearestTiesAwayZero(4),
    DynamicRoundingMode(7);

    private final int encoding;

    RoundingMode(int encoding) {
        this.encoding = encoding;
    }

    public int toRoundingModeEnum(PrivilegedState state) {
        if (this == DynamicRoundingMode) {
            return Math.toIntExact((state.getFcsr() >> 5) & 0b111);
        }
        return encoding;
    }

    public static RoundingMode fromEncoding(int encoding) {
        return switch (encoding) {
            case 0 -> RoundNearestTiesEven;
            case 1 -> RoundTowardsZero;
            case 2 -> RoundDown;
            case 3 -> RoundUp;
            case 4 -> RoundNearestTiesAwayZero;
            case 7 -> DynamicRoundingMode;
            default -> null;
        };
    }
}
