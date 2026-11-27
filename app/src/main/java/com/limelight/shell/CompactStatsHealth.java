package com.limelight.shell;

final class CompactStatsHealth {
    enum Level { GOOD, WARNING, ERROR }

    private CompactStatsHealth() {}

    static Level network(int ms) {
        return ms >= 100 ? Level.ERROR : ms >= 60 ? Level.WARNING : Level.GOOD;
    }

    static Level loss(float percent) {
        return percent >= 5f ? Level.ERROR : percent >= 1.5f ? Level.WARNING : Level.GOOD;
    }

    static Level fps(float rendered, float sent) {
        if (!(sent > 0f) || rendered < 0f) return Level.GOOD;
        float ratio = rendered / sent;
        return ratio < .6f ? Level.ERROR : ratio < .85f ? Level.WARNING : Level.GOOD;
    }

    static Level low(float low, float sent) {
        return low >= 0f && sent > 0f && low / sent < .6f ? Level.WARNING : Level.GOOD;
    }
}
