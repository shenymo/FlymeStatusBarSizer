package com.example.flymestatusbarsizer.feature.clock;

final class ClockDetailSystemStatusSnapshot {
    static final ClockDetailSystemStatusSnapshot EMPTY =
            new ClockDetailSystemStatusSnapshot(
                    new MemoryRow[] {
                            new MemoryRow("RAM", "-- / --", "--"),
                            new MemoryRow("ZRAM", "-- / --", "--"),
                            new MemoryRow("WB", "-- / --", "--")
                    },
                    "--",
                    "不可用");

    final MemoryRow[] memoryRows;
    final String temperatureValue;
    final String powerValue;

    ClockDetailSystemStatusSnapshot(
            MemoryRow[] memoryRows,
            String temperatureValue,
            String powerValue) {
        this.memoryRows = sanitizeRows(memoryRows);
        this.temperatureValue = sanitize(temperatureValue, "--");
        this.powerValue = sanitize(powerValue, "不可用");
    }

    private static MemoryRow[] sanitizeRows(MemoryRow[] rows) {
        if (rows == null || rows.length == 0) {
            return EMPTY.memoryRows;
        }
        MemoryRow[] safeRows = new MemoryRow[rows.length];
        for (int i = 0; i < rows.length; i++) {
            MemoryRow row = rows[i];
            safeRows[i] = row != null
                    ? row
                    : new MemoryRow("--", "-- / --", "--");
        }
        return safeRows;
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    static final class MemoryRow {
        final String label;
        final String value;
        final String percent;

        MemoryRow(String label, String value, String percent) {
            this.label = sanitize(label, "--");
            this.value = sanitize(value, "-- / --");
            this.percent = sanitize(percent, "--");
        }
    }
}
