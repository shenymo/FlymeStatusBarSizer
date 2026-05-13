package com.example.flymestatusbarsizer.feature.clock;

final class ClockDetailSystemStatusSnapshot {
    static final ClockDetailSystemStatusSnapshot EMPTY =
            new ClockDetailSystemStatusSnapshot("--", "--", "不可用");

    final String memoryValue;
    final String temperatureValue;
    final String powerValue;

    ClockDetailSystemStatusSnapshot(
            String memoryValue,
            String temperatureValue,
            String powerValue) {
        this.memoryValue = sanitize(memoryValue, "--");
        this.temperatureValue = sanitize(temperatureValue, "--");
        this.powerValue = sanitize(powerValue, "不可用");
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
