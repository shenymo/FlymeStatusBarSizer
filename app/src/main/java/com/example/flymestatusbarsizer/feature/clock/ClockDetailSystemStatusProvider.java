package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

final class ClockDetailSystemStatusProvider {
    private static final String MEMORY_UNAVAILABLE = "--";
    private static final String TEMPERATURE_UNAVAILABLE = "--";
    private static final String POWER_UNAVAILABLE = "不可用";
    private static final String EXTRA_MAX_CHARGING_CURRENT = "max_charging_current";
    private static final String EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage";
    private static final String THERMAL_SERVICE_NAME = "thermalservice";
    private static final String THERMAL_SERVICE_DESCRIPTOR = "android.os.IThermalService";
    private static final String THERMAL_AIDL_DESCRIPTOR = "android.hardware.thermal.IThermal";
    private static final String THERMAL_AIDL_SERVICE = THERMAL_AIDL_DESCRIPTOR + "/default";
    private static final int TEMPERATURE_TYPE_BATTERY = 2;
    private static final int TEMPERATURE_TYPE_SKIN = 3;
    private static final int FIRST_THROTTLING_STATUS_INDEX = 1;
    private static final int LAST_THROTTLING_STATUS_INDEX = 6;
    private static final int MAX_THERMAL_THRESHOLD_QUERY_ATTEMPTS = 3;
    private static final float TEMPERATURE_COMPARE_EPSILON_C = 0.05f;
    private static final long THERMAL_THRESHOLD_RETRY_INTERVAL_MS = 10000L;
    private static final Object BACKGROUND_LOCK = new Object();
    private static final Object THERMAL_CACHE_LOCK = new Object();
    private static Handler backgroundHandler;
    private static boolean thermalServiceReflectionResolved;
    private static boolean thermalServiceReflectionAvailable;
    private static boolean thermalReflectionResolved;
    private static boolean thermalReflectionAvailable;
    private static boolean thermalThresholdResolved;
    private static int thermalThresholdQueryAttempts;
    private static long thermalThresholdNextRetryUptimeMs;
    private static TemperatureThresholdProfile cachedTemperatureThresholdProfile =
            TemperatureThresholdProfile.UNAVAILABLE;
    private static Object frameworkThermalService;
    private static Object thermalAidlService;
    private static Method getServiceMethod;
    private static Method thermalServiceAsInterfaceMethod;
    private static Method getCurrentTemperaturesWithTypeMethod;
    private static Method frameworkTemperatureGetNameMethod;
    private static Method frameworkTemperatureGetValueMethod;
    private static Method waitForDeclaredServiceMethod;
    private static Method thermalAsInterfaceMethod;
    private static Method getTemperatureThresholdsWithTypeMethod;
    private static Field temperatureThresholdNameField;
    private static Field temperatureThresholdHotThresholdsField;

    private final Context context;
    private final ActivityManager activityManager;
    private final BatteryManager batteryManager;
    private final IntentFilter batteryChangedFilter;
    private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

    interface SnapshotCallback {
        void onSnapshot(ClockDetailSystemStatusSnapshot snapshot);
    }

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

    void requestSnapshot(Handler resultHandler, SnapshotCallback callback) {
        if (callback == null) {
            return;
        }
        Handler workerHandler = getBackgroundHandler();
        if (workerHandler == null) {
            deliverSnapshot(resultHandler, callback, ClockDetailSystemStatusSnapshot.EMPTY);
            return;
        }
        workerHandler.post(() -> {
            ClockDetailSystemStatusSnapshot snapshot = querySnapshot();
            deliverSnapshot(resultHandler, callback, snapshot);
        });
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
            long usedBytes = normalizeDisplayUsedBytes(
                    displayTotalBytes,
                    actualTotalBytes,
                    memoryInfo.availMem);
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

    private static long normalizeDisplayUsedBytes(
            long displayTotalBytes,
            long actualTotalBytes,
            long availableBytes) {
        long safeDisplayTotalBytes = Math.max(0L, displayTotalBytes);
        long safeActualTotalBytes = Math.max(0L, actualTotalBytes);
        long safeAvailableBytes = Math.max(0L, availableBytes);
        if (safeDisplayTotalBytes <= 0L || safeActualTotalBytes <= 0L) {
            return 0L;
        }
        long clampedAvailableBytes = Math.min(safeAvailableBytes, safeActualTotalBytes);
        if (safeDisplayTotalBytes == safeActualTotalBytes) {
            return Math.max(0L, safeDisplayTotalBytes - clampedAvailableBytes);
        }
        double usedRatio = 1.0d - (((double) clampedAvailableBytes) / ((double) safeActualTotalBytes));
        long scaledUsedBytes = Math.round(safeDisplayTotalBytes * usedRatio);
        return Math.max(0L, Math.min(safeDisplayTotalBytes, scaledUsedBytes));
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
        float batteryTemperatureCelsius = snapshot.temperatureTenthsCelsius == Integer.MIN_VALUE
                ? Float.NaN
                : snapshot.temperatureTenthsCelsius / 10.0f;
        TemperatureThresholdProfile profile = resolveTemperatureThresholdProfile();
        if (profile.available) {
            float currentTemperature = queryCurrentThermalTemperatureCelsius(
                    profile.sensorType,
                    profile.sensorName);
            if (Float.isNaN(currentTemperature) && profile.sensorType == TEMPERATURE_TYPE_BATTERY) {
                currentTemperature = batteryTemperatureCelsius;
            }
            if (!Float.isNaN(currentTemperature)) {
                return formatTemperatureWindow(currentTemperature, profile);
            }
        }
        if (Float.isNaN(batteryTemperatureCelsius)) {
            return TEMPERATURE_UNAVAILABLE;
        }
        return formatTemperatureMagnitude(batteryTemperatureCelsius, "");
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

    private TemperatureThresholdProfile resolveTemperatureThresholdProfile() {
        synchronized (THERMAL_CACHE_LOCK) {
            if (cachedTemperatureThresholdProfile.available) {
                return cachedTemperatureThresholdProfile;
            }
            if (thermalThresholdResolved) {
                return cachedTemperatureThresholdProfile;
            }
            long now = SystemClock.uptimeMillis();
            if (thermalThresholdQueryAttempts > 0
                    && now < thermalThresholdNextRetryUptimeMs) {
                return TemperatureThresholdProfile.UNAVAILABLE;
            }
            TemperatureThresholdProfile profile = queryTemperatureThresholdProfileLocked();
            if (profile.available) {
                cachedTemperatureThresholdProfile = profile;
                thermalThresholdResolved = true;
                thermalThresholdQueryAttempts = 0;
                thermalThresholdNextRetryUptimeMs = 0L;
                return cachedTemperatureThresholdProfile;
            }
            thermalThresholdQueryAttempts++;
            if (thermalThresholdQueryAttempts >= MAX_THERMAL_THRESHOLD_QUERY_ATTEMPTS) {
                cachedTemperatureThresholdProfile = TemperatureThresholdProfile.UNAVAILABLE;
                thermalThresholdResolved = true;
                thermalThresholdNextRetryUptimeMs = 0L;
                return cachedTemperatureThresholdProfile;
            }
            thermalThresholdNextRetryUptimeMs = now + THERMAL_THRESHOLD_RETRY_INTERVAL_MS;
            return TemperatureThresholdProfile.UNAVAILABLE;
        }
    }

    private static TemperatureThresholdProfile queryTemperatureThresholdProfileLocked() {
        TemperatureThresholdProfile batteryProfile = queryThresholdProfileLocked(
                TEMPERATURE_TYPE_BATTERY,
                "");
        if (batteryProfile.available) {
            return batteryProfile;
        }
        TemperatureThresholdProfile skinProfile = queryThresholdProfileLocked(
                TEMPERATURE_TYPE_SKIN,
                "\u673A\u8EAB");
        if (skinProfile.available) {
            return skinProfile;
        }
        return TemperatureThresholdProfile.UNAVAILABLE;
    }

    private static TemperatureThresholdProfile queryThresholdProfileLocked(
            int type,
            String currentPrefix) {
        Object thermalService = getThermalAidlServiceLocked();
        if (thermalService == null || getTemperatureThresholdsWithTypeMethod == null) {
            return TemperatureThresholdProfile.UNAVAILABLE;
        }
        try {
            Object thresholds = getTemperatureThresholdsWithTypeMethod.invoke(thermalService, type);
            if (thresholds == null || !thresholds.getClass().isArray()) {
                return TemperatureThresholdProfile.UNAVAILABLE;
            }
            TemperatureThresholdProfile bestProfile = TemperatureThresholdProfile.UNAVAILABLE;
            int length = Array.getLength(thresholds);
            for (int i = 0; i < length; i++) {
                Object threshold = Array.get(thresholds, i);
                float[] candidates = extractHotThresholdsLocked(threshold);
                if (candidates.length == 0) {
                    continue;
                }
                String sensorName = extractStringFieldLocked(
                        threshold,
                        temperatureThresholdNameField,
                        "name");
                TemperatureThresholdProfile candidateProfile = new TemperatureThresholdProfile(
                        true,
                        type,
                        sensorName,
                        currentPrefix,
                        candidates);
                if (!bestProfile.available
                        || candidateProfile.hotThresholds[0] < bestProfile.hotThresholds[0]) {
                    bestProfile = candidateProfile;
                }
            }
            return bestProfile;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to query thermal threshold type " + type,
                    t);
            return TemperatureThresholdProfile.UNAVAILABLE;
        }
    }

    private static float[] extractHotThresholdsLocked(Object threshold) {
        if (threshold == null) {
            return new float[0];
        }
        if (temperatureThresholdHotThresholdsField == null) {
            return new float[0];
        }
        try {
            Object value = temperatureThresholdHotThresholdsField.get(threshold);
            if (!(value instanceof float[])) {
                return new float[0];
            }
            float[] thresholds = (float[]) value;
            float[] filtered = new float[LAST_THROTTLING_STATUS_INDEX - FIRST_THROTTLING_STATUS_INDEX + 1];
            int count = 0;
            for (int i = FIRST_THROTTLING_STATUS_INDEX;
                    i <= LAST_THROTTLING_STATUS_INDEX && i < thresholds.length;
                    i++) {
                float candidate = thresholds[i];
                if (Float.isNaN(candidate) || candidate <= 0.0f) {
                    continue;
                }
                if (count > 0
                        && Math.abs(filtered[count - 1] - candidate) < TEMPERATURE_COMPARE_EPSILON_C) {
                    continue;
                }
                filtered[count] = candidate;
                count++;
            }
            return Arrays.copyOf(filtered, count);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to read thermal threshold entry",
                    t);
        }
        return new float[0];
    }

    private float queryCurrentThermalTemperatureCelsius(int type, String sensorName) {
        synchronized (THERMAL_CACHE_LOCK) {
            Object thermalService = getFrameworkThermalServiceLocked();
            if (thermalService == null || getCurrentTemperaturesWithTypeMethod == null) {
                return Float.NaN;
            }
            try {
                Object temperatures = getCurrentTemperaturesWithTypeMethod.invoke(thermalService, type);
                if (temperatures == null || !temperatures.getClass().isArray()) {
                    return Float.NaN;
                }
                float fallback = Float.NaN;
                int length = Array.getLength(temperatures);
                for (int i = 0; i < length; i++) {
                    Object temperature = Array.get(temperatures, i);
                    if (temperature == null) {
                        continue;
                    }
                    float candidate = invokeFloatMethodLocked(
                            temperature,
                            frameworkTemperatureGetValueMethod,
                            "getValue");
                    if (Float.isNaN(candidate) || candidate <= 0.0f) {
                        continue;
                    }
                    if (Float.isNaN(fallback)) {
                        fallback = candidate;
                    }
                    String name = invokeStringMethodLocked(
                            temperature,
                            frameworkTemperatureGetNameMethod,
                            "getName");
                    if (!sensorName.isEmpty() && sensorName.equals(name)) {
                        return candidate;
                    }
                }
                return fallback;
            } catch (Throwable t) {
                FlymeStatusBarSizer.logClockWarning(
                        "Failed to query framework thermal current temperature type " + type,
                        t);
                return Float.NaN;
            }
        }
    }

    private String formatTemperatureWindow(
            float currentTemperatureCelsius,
            TemperatureThresholdProfile profile) {
        String currentValue = formatTemperatureMagnitude(
                currentTemperatureCelsius,
                profile.currentValuePrefix);
        if (profile.hotThresholds.length == 0) {
            return currentValue;
        }
        int nextIndex = findNextThresholdIndex(profile.hotThresholds, currentTemperatureCelsius);
        if (nextIndex == 0) {
            return currentValue + " / " + formatTemperatureMagnitude(profile.hotThresholds[0], "");
        }
        if (nextIndex > 0) {
            return formatTemperatureMagnitude(profile.hotThresholds[nextIndex - 1], "")
                    + " / "
                    + currentValue
                    + " / "
                    + formatTemperatureMagnitude(profile.hotThresholds[nextIndex], "");
        }
        return formatTemperatureMagnitude(profile.hotThresholds[profile.hotThresholds.length - 1], "")
                + " / "
                + currentValue;
    }

    private int findNextThresholdIndex(float[] thresholds, float currentTemperatureCelsius) {
        for (int i = 0; i < thresholds.length; i++) {
            if (currentTemperatureCelsius + TEMPERATURE_COMPARE_EPSILON_C < thresholds[i]) {
                return i;
            }
        }
        return -1;
    }

    private String formatTemperatureMagnitude(float temperatureCelsius, String prefix) {
        Locale locale = resolveLocale();
        String labelPrefix = prefix == null ? "" : prefix;
        return labelPrefix + String.format(locale, "%.1f\u00B0C", temperatureCelsius);
    }

    private static String extractStringFieldLocked(Object target, Field field, String fieldName) {
        if (target == null || field == null || fieldName == null) {
            return "";
        }
        try {
            Object value = field.get(target);
            return value instanceof String ? (String) value : "";
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to read thermal string field " + fieldName,
                    t);
            return "";
        }
    }

    private static String invokeStringMethodLocked(Object target, Method method, String methodName) {
        if (target == null || method == null || methodName == null) {
            return "";
        }
        try {
            Object value = method.invoke(target);
            return value instanceof String ? (String) value : "";
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to invoke thermal string method " + methodName,
                    t);
            return "";
        }
    }

    private static float invokeFloatMethodLocked(Object target, Method method, String methodName) {
        if (target == null || method == null || methodName == null) {
            return Float.NaN;
        }
        try {
            Object value = method.invoke(target);
            return value instanceof Float ? (Float) value : Float.NaN;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to invoke thermal float method " + methodName,
                    t);
            return Float.NaN;
        }
    }

    private static Object getFrameworkThermalServiceLocked() {
        if (frameworkThermalService != null) {
            return frameworkThermalService;
        }
        if (!ensureThermalServiceReflectionLocked()) {
            return null;
        }
        try {
            Object binderValue = getServiceMethod.invoke(null, THERMAL_SERVICE_NAME);
            if (!(binderValue instanceof IBinder)) {
                return null;
            }
            Object service = thermalServiceAsInterfaceMethod.invoke(null, (IBinder) binderValue);
            if (service != null) {
                frameworkThermalService = service;
            }
            return service;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to connect framework thermalservice", t);
            return null;
        }
    }

    private static Object getThermalAidlServiceLocked() {
        if (thermalAidlService != null) {
            return thermalAidlService;
        }
        if (!ensureThermalReflectionLocked()) {
            return null;
        }
        try {
            Object binderValue = waitForDeclaredServiceMethod.invoke(null, THERMAL_AIDL_SERVICE);
            if (!(binderValue instanceof IBinder)) {
                return null;
            }
            Object service = thermalAsInterfaceMethod.invoke(null, (IBinder) binderValue);
            if (service != null) {
                thermalAidlService = service;
            }
            return service;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to connect thermal HAL AIDL service", t);
            return null;
        }
    }

    private static boolean ensureThermalServiceReflectionLocked() {
        if (thermalServiceReflectionResolved) {
            return thermalServiceReflectionAvailable;
        }
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            getServiceMethod = serviceManagerClass.getMethod("getService", String.class);

            Class<?> thermalServiceClass = Class.forName(THERMAL_SERVICE_DESCRIPTOR);
            getCurrentTemperaturesWithTypeMethod = thermalServiceClass.getMethod(
                    "getCurrentTemperaturesWithType",
                    int.class);

            Class<?> stubClass = Class.forName("android.os.IThermalService$Stub");
            thermalServiceAsInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);

            Class<?> temperatureClass = Class.forName("android.os.Temperature");
            frameworkTemperatureGetNameMethod = temperatureClass.getMethod("getName");
            frameworkTemperatureGetValueMethod = temperatureClass.getMethod("getValue");
            thermalServiceReflectionAvailable = true;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to resolve framework thermalservice reflection accessors",
                    t);
            thermalServiceReflectionAvailable = false;
        }
        thermalServiceReflectionResolved = true;
        return thermalServiceReflectionAvailable;
    }

    private static boolean ensureThermalReflectionLocked() {
        if (thermalReflectionResolved) {
            return thermalReflectionAvailable;
        }
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            waitForDeclaredServiceMethod = serviceManagerClass.getMethod(
                    "waitForDeclaredService",
                    String.class);

            Class<?> thermalInterfaceClass = Class.forName(THERMAL_AIDL_DESCRIPTOR);
            getTemperatureThresholdsWithTypeMethod = thermalInterfaceClass.getMethod(
                    "getTemperatureThresholdsWithType",
                    int.class);

            Class<?> stubClass = Class.forName("android.hardware.thermal.IThermal$Stub");
            thermalAsInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);

            Class<?> thresholdClass = Class.forName("android.hardware.thermal.TemperatureThreshold");
            temperatureThresholdNameField = thresholdClass.getField("name");
            temperatureThresholdHotThresholdsField = thresholdClass.getField(
                    "hotThrottlingThresholds");
            thermalReflectionAvailable = true;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to resolve thermal HAL reflection accessors",
                    t);
            thermalReflectionAvailable = false;
        }
        thermalReflectionResolved = true;
        return thermalReflectionAvailable;
    }

    private static final class TemperatureThresholdProfile {
        static final TemperatureThresholdProfile UNAVAILABLE =
                new TemperatureThresholdProfile(false, -1, "", "", new float[0]);

        final boolean available;
        final int sensorType;
        final String sensorName;
        final String currentValuePrefix;
        final float[] hotThresholds;

        TemperatureThresholdProfile(
                boolean available,
                int sensorType,
                String sensorName,
                String currentValuePrefix,
                float[] hotThresholds) {
            this.available = available;
            this.sensorType = sensorType;
            this.sensorName = sensorName == null ? "" : sensorName;
            this.currentValuePrefix = currentValuePrefix == null ? "" : currentValuePrefix;
            this.hotThresholds = hotThresholds == null ? new float[0] : Arrays.copyOf(
                    hotThresholds,
                    hotThresholds.length);
        }
    }

    private static Handler getBackgroundHandler() {
        synchronized (BACKGROUND_LOCK) {
            if (backgroundHandler != null) {
                return backgroundHandler;
            }
            try {
                HandlerThread thread = new HandlerThread("ClockDetailSystemStatus");
                thread.start();
                backgroundHandler = new Handler(thread.getLooper());
            } catch (Throwable t) {
                FlymeStatusBarSizer.logClockWarning(
                        "Failed to start clock detail system status worker",
                        t);
                backgroundHandler = null;
            }
            return backgroundHandler;
        }
    }

    private static void deliverSnapshot(
            Handler resultHandler,
            SnapshotCallback callback,
            ClockDetailSystemStatusSnapshot snapshot) {
        ClockDetailSystemStatusSnapshot safeSnapshot = snapshot != null
                ? snapshot
                : ClockDetailSystemStatusSnapshot.EMPTY;
        if (resultHandler == null) {
            callback.onSnapshot(safeSnapshot);
            return;
        }
        resultHandler.post(() -> callback.onSnapshot(safeSnapshot));
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
