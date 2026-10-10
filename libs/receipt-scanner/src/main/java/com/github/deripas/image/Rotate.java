package com.github.deripas.image;

/**
 * Clockwise rotation by quarter turns; the constants are ordered by the number of turns, 0-3.
 */
public enum Rotate {
    ROTATE_NONE,
    ROTATE_90_CLOCKWISE,
    ROTATE_180,
    ROTATE_90_COUNTERCLOCKWISE;

    /**
     * @return number of clockwise quarter turns, 0-3
     */
    public int quarterTurns() {
        return ordinal();
    }

    /**
     * @param quarterTurns number of clockwise quarter turns, any integer
     */
    public static Rotate fromQuarterTurns(int quarterTurns) {
        return values()[Math.floorMod(quarterTurns, 4)];
    }

    /**
     * @param degrees clockwise degrees, a multiple of 90, any sign
     */
    public static Rotate fromDegrees(int degrees) {
        return fromQuarterTurns(degrees / 90);
    }
}
