package com.example.flymestatusbarsizer.feature.launcher;

import android.os.Trace;

import com.example.flymestatusbarsizer.BuildConfig;

final class LauncherRecentsTrace {
    static final boolean ENABLED = BuildConfig.ENABLE_LAUNCHER_TRACE;

    private LauncherRecentsTrace() {
    }

    static void beginSection(String name) {
        if (ENABLED) {
            Trace.beginSection(name);
        }
    }

    static void endSection() {
        if (ENABLED) {
            Trace.endSection();
        }
    }

    static void setCounter(String name, int value) {
        if (ENABLED) {
            Trace.setCounter(name, value);
        }
    }
}
