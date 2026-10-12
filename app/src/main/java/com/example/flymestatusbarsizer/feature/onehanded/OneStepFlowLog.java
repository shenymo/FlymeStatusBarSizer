package com.example.flymestatusbarsizer.feature.onehanded;

import android.util.Log;

import com.example.flymestatusbarsizer.config.ModuleConfig;

/** Shared log format for the side-gesture app workspace flow. */
public final class OneStepFlowLog {
    public static final String TAG = "FlymeOneStep";
    private static final String PREFIX = "flow=side-workbench";

    private OneStepFlowLog() {}

    public static void info(String event, int session, String fields) {
        if (isEnabled()) Log.i(TAG, format(event, session, fields));
    }

    public static void info(String event, String fields) {
        info(event, -1, fields);
    }

    public static void warn(String event, int session, String fields, Throwable error) {
        if (isEnabled()) Log.w(TAG, format(event, session, fields), error);
    }

    public static void warn(String event, String fields, Throwable error) {
        warn(event, -1, fields, error);
    }

    public static int i(String sourceTag, String message) {
        if (isEnabled()) Log.i(TAG, format("diagnostic", -1,
                "source=" + sourceTag + " detail=" + message));
        return 0;
    }

    public static int w(String sourceTag, String message) {
        if (isEnabled()) Log.w(TAG, format("diagnostic", -1,
                "source=" + sourceTag + " detail=" + message));
        return 0;
    }

    public static int w(String sourceTag, String message, Throwable error) {
        if (isEnabled()) Log.w(TAG, format("diagnostic", -1,
                "source=" + sourceTag + " detail=" + message), error);
        return 0;
    }

    public static boolean isEnabled() {
        try {
            return ModuleConfig.load(null).workspaceFlowLoggingEnabled;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static String format(String event, int session, String fields) {
        return PREFIX + " event=" + event
                + " session=" + (session < 0 ? "none" : session)
                + (fields == null || fields.isEmpty() ? "" : " " + fields);
    }
}
