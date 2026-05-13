package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.Locale;

final class ClockDetailSystemStatusProvider {
    private static final String MEMORY_UNAVAILABLE = "--";
    private static final String TEMPERATURE_UNAVAILABLE = "--";
    private static final String POWER_UNAVAILABLE = "不可用";
    private static final String EXTRA_MAX_CHARGING_CURRENT = "max_charging_current";
    private static final String EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage";

    private final Context context;
    private final ActivityManager activityManager;
    private final BatteryManager batteryManager;
    private final IntentFilter batteryChangedFilter;
    private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

    ClockDetailSystemStatusProvider(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
        this.activityManager = this.context != null
                ? this.context.getSystemService(ActivityManager.class)
                : null;
        this.batteryManager = this.context != null
                ? this.context.getSystemService(BatteryManager.class)
                : null;
        this.batteryChangedFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    }

    ClockDetailSystemStatusSnapshot querySnapshot() {
        BatterySnapshot batterySnapshot = readBatterySnapshot();
        return new ClockDetailSystemStatusSnapshot(
                readMemoryValue(),
                formatTemperatureValue(batterySnapshot),
                formatPowerValue(batterySnapshot));
    }

    private String readMemoryValue() {
        if (activityManager == null) {
            return MEMORY_UNAVAILABLE;
        }
        try {
            activityManager.getMemoryInfo(memoryInfo);
            long displayTotalBytes = memoryInfo.advertisedMem > 0L
                    ? memoryInfo.advertisedMem
                    : memoryInfo.totalMem;
            long actualTotalBytes = memoryInfo.totalMem > 0L
                    ? memoryInfo.totalMem
                    : displayTotalBytes;
            if (displayTotalBytes <= 0L || actualTotalBytes <= 0L) {
                return MEMORY_UNAVAILABLE;
            }
            long usedBytes = Math.max(0L, actualTotalBytes - Math.max(0L, memoryInfo.availMem));
            Locale locale = resolveLocale();
            return String.format(
                    locale,
                    "%.1f / %.1f GB",
                    toGigabytes(usedBytes),
                    toGigabytes(displayTotalBytes));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to read clock detail memory state", t);
            return MEMORY_UNAVAILABLE;
        }
    }

    private BatterySnapshot readBatterySnapshot() {
        BatterySnapshot snapshot = new BatterySnapshot();
        try {
            Intent batteryIntent = context != null
                    ? context.registerReceiver(null, batteryChangedFilter)
                    : null;
            if (batteryIntent != null) {
                snapshot.status = batteryIntent.getIntExtra(
                        BatteryManager.EXTRA_STATUS,
                        Integer.MIN_VALUE);
                snapshot.temperatureTenthsCelsius = batteryIntent.getIntExtra(
                        BatteryManager.EXTRA_TEMPERATURE,
                        Integer.MIN_VALUE);
                snapshot.voltageMillivolts = batteryIntent.getIntExtra(
                        BatteryManager.EXTRA_VOLTAGE,
                        Integer.MIN_VALUE);
                snapshot.maxChargingCurrentMicroamps = batteryIntent.getIntExtra(
                        EXTRA_MAX_CHARGING_CURRENT,
                        Integer.MIN_VALUE);
                snapshot.maxChargingVoltageMicrovolts = batteryIntent.getIntExtra(
                        EXTRA_MAX_CHARGING_VOLTAGE,
                        Integer.MIN_VALUE);
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to read sticky battery intent", t);
        }
        if (batteryManager != null) {
            snapshot.currentNowMicroamps = queryLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            snapshot.currentAverageMicroamps = queryLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
            if (snapshot.status == Integer.MIN_VALUE) {
                snapshot.status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            }
        }
        return snapshot;
    }

    private long queryLongProperty(int propertyId) {
        if (batteryManager == null) {
            return Long.MIN_VALUE;
        }
        try {
            return batteryManager.getLongProperty(propertyId);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to read battery long property " + propertyId,
                    t);
            return Long.MIN_VALUE;
        }
    }

    private String formatTemperatureValue(BatterySnapshot snapshot) {
        if (snapshot.temperatureTenthsCelsius == Integer.MIN_VALUE) {
            return TEMPERATURE_UNAVAILABLE;
        }
        Locale locale = resolveLocale();
        return String.format(
                locale,
                "%.1f\u00B0C",
                snapshot.temperatureTenthsCelsius / 10.0d);
    }

    private String formatPowerValue(BatterySnapshot snapshot) {
        double maxChargeWatts = readMaxChargeWatts(snapshot);
        long currentMicroamps = selectCurrentMicroamps(snapshot);
        if (currentMicroamps != Long.MIN_VALUE && snapshot.voltageMillivolts > 0) {
            double watts = toWattsFromCurrentAndVoltage(currentMicroamps, snapshot.voltageMillivolts);
            if (currentMicroamps > 0L) {
                return formatChargingWatts(watts, maxChargeWatts);
            }
            if (currentMicroamps < 0L) {
                return formatSignedWatts(-1, watts);
            }
            if (isChargingStatus(snapshot.status)) {
                return formatChargingWatts(watts, maxChargeWatts);
            }
            if (isDischargingStatus(snapshot.status)) {
                return formatSignedWatts(-1, watts);
            }
            return formatSignedWatts(0, watts);
        }
        if (isChargingStatus(snapshot.status) && maxChargeWatts > 0.0d) {
            return formatMaxWatts(maxChargeWatts);
        }
        return POWER_UNAVAILABLE;
    }

    private long selectCurrentMicroamps(BatterySnapshot snapshot) {
        if (snapshot.currentNowMicroamps != Long.MIN_VALUE) {
            return snapshot.currentNowMicroamps;
        }
        if (snapshot.currentAverageMicroamps != Long.MIN_VALUE) {
            return snapshot.currentAverageMicroamps;
        }
        return Long.MIN_VALUE;
    }

    private boolean isChargingStatus(int status) {
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private boolean isDischargingStatus(int status) {
        return status == BatteryManager.BATTERY_STATUS_DISCHARGING
                || status == BatteryManager.BATTERY_STATUS_NOT_CHARGING;
    }

    private String formatChargingWatts(double watts, double maxChargeWatts) {
        String currentValue = formatSignedWatts(1, watts);
        if (maxChargeWatts <= 0.0d) {
            return currentValue;
        }
        return currentValue + " / " + formatMaxWatts(maxChargeWatts);
    }

    private String formatMaxWatts(double watts) {
        return formatWattMagnitude(watts) + " W Max";
    }

    private String formatSignedWatts(int sign, double watts) {
        String prefix = sign > 0 ? "+" : sign < 0 ? "-" : "";
        return prefix + formatWattMagnitude(watts) + " W";
    }

    private String formatWattMagnitude(double watts) {
        Locale locale = resolveLocale();
        double absWatts = Math.abs(watts);
        double roundedInteger = Math.rint(absWatts);
        if (Math.abs(absWatts - roundedInteger) < 0.05d) {
            return String.format(locale, "%.0f", roundedInteger);
        }
        return String.format(locale, "%.1f", absWatts);
    }

    private double readMaxChargeWatts(BatterySnapshot snapshot) {
        if (snapshot.maxChargingCurrentMicroamps <= 0 || snapshot.maxChargingVoltageMicrovolts <= 0) {
            return 0.0d;
        }
        return toWattsFromChargeLimits(
                snapshot.maxChargingCurrentMicroamps,
                snapshot.maxChargingVoltageMicrovolts);
    }

    private Locale resolveLocale() {
        if (context == null) {
            return Locale.getDefault();
        }
        try {
            Locale locale = context.getResources().getConfiguration().locale;
            if (locale != null) {
                return locale;
            }
        } catch (Throwable ignored) {
        }
        return Locale.getDefault();
    }

    private static double toGigabytes(long bytes) {
        return bytes / 1073741824.0d;
    }

    private static double toWattsFromCurrentAndVoltage(long currentMicroamps, int voltageMillivolts) {
        return (currentMicroamps * voltageMillivolts) / 1000000000.0d;
    }

    private static double toWattsFromChargeLimits(int currentMicroamps, int voltageMicrovolts) {
        return (((long) currentMicroamps) * voltageMicrovolts) / 1000000000000.0d;
    }

    private static final class BatterySnapshot {
        int status = Integer.MIN_VALUE;
        int temperatureTenthsCelsius = Integer.MIN_VALUE;
        int voltageMillivolts = Integer.MIN_VALUE;
        int maxChargingCurrentMicroamps = Integer.MIN_VALUE;
        int maxChargingVoltageMicrovolts = Integer.MIN_VALUE;
        long currentNowMicroamps = Long.MIN_VALUE;
        long currentAverageMicroamps = Long.MIN_VALUE;
    }
}
