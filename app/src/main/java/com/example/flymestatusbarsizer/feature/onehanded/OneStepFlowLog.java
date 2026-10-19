package com.example.flymestatusbarsizer.feature.onehanded;

import android.util.Log;

import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared log format for the side-gesture app workspace flow. */
public final class OneStepFlowLog {
    public static final String TAG = "FlymeOneStep";
    private static final String PREFIX = "flow=side-workbench";
    private static final ConcurrentHashMap<String, AtomicInteger> HOOK_TRIGGERS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> HOOK_STATES =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> HOOK_OUTCOMES =
            new ConcurrentHashMap<>();

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
        if (isEnabled() && message != null && isHookDiagnostic(message)) {
            String state = isUnavailableDiagnostic(message, error) ? "unavailable" : "failed";
            incrementState(state);
            Log.w(TAG, format("hook", -1,
                    "state=" + state + " point=" + safe(sourceTag + ":" + message)), error);
        }
        return 0;
    }

    public static void hookResolved(Member member) {
        if (!isEnabled() || member == null || !isWorkbenchTarget(member.getDeclaringClass())) return;
        incrementState("found");
        incrementState("usable");
        Log.i(TAG, format("hook", -1,
                "state=found usable=true point=" + safe(memberName(member))
                        + " process=" + safe(processName())));
    }

    public static void hookInstalled(Member member) {
        if (!isEnabled() || member == null || !isWorkbenchTarget(member.getDeclaringClass())) return;
        incrementState("installed");
        Log.i(TAG, format("hook", -1,
                "state=installed point=" + safe(memberName(member))
                        + " process=" + safe(processName())));
    }

    public static void hookTriggered(String point) {
        if (!isEnabled() || point == null || point.isEmpty()) return;
        AtomicInteger count = HOOK_TRIGGERS.computeIfAbsent(point, key -> new AtomicInteger());
        int value = count.incrementAndGet();
        if (value == 1 || value % 25 == 0) {
            Log.i(TAG, format("hook", -1,
                    "state=triggered point=" + safe(point)
                            + " count=" + value
                            + " process=" + safe(processName())));
        }
    }

    public static void hookMatched(String point, boolean matched, String reason) {
        if (!isEnabled() || point == null || point.isEmpty()) return;
        incrementState(matched ? "matched" : "bypassed");
        outcome("matched", point, (matched ? "true" : "false") + ",reason=" + safe(reason));
    }

    public static void hookEffective(String point, boolean effective, String reason) {
        if (!isEnabled() || point == null || point.isEmpty()) return;
        incrementState(effective ? "effective" : "ineffective");
        outcome("effective", point, (effective ? "true" : "false") + ",reason=" + safe(reason));
    }

    public static void hookBypassed(String point, String reason) {
        hookMatched(point, false, reason);
        outcome("bypassed", point, safe(reason));
    }

    public static void hookUnavailable(String point, Throwable error) {
        if (!isEnabled()) return;
        incrementState("unavailable");
        Log.w(TAG, format("hook", -1,
                "state=unavailable point=" + safe(point)
                        + " process=" + safe(processName())), error);
    }

    public static boolean isEnabled() {
        try {
            return ModuleConfig.load(null).workspaceFlowLoggingEnabled;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void hookSummary(String owner) {
        if (!isEnabled()) return;
        Log.i(TAG, format("hook_summary", -1,
                "owner=" + safe(owner)
                        + " found=" + stateCount("found")
                        + " usable=" + stateCount("usable")
                        + " installed=" + stateCount("installed")
                        + " matched=" + stateCount("matched")
                        + " effective=" + stateCount("effective")
                        + " bypassed=" + stateCount("bypassed")
                        + " ineffective=" + stateCount("ineffective")
                        + " unavailable=" + stateCount("unavailable")
                        + " failed=" + stateCount("failed")
                        + " process=" + safe(processName())));
    }

    private static String format(String event, int session, String fields) {
        return PREFIX + " event=" + event
                + " session=" + (session < 0 ? "none" : session)
                + (fields == null || fields.isEmpty() ? "" : " " + fields);
    }

    private static boolean isHookDiagnostic(String message) {
        String value = message.toLowerCase(java.util.Locale.ROOT);
        return value.contains("hook") || value.contains("unavailable")
                || value.contains("install") || value.contains("cannot");
    }

    private static boolean isUnavailableDiagnostic(String message, Throwable error) {
        String value = message.toLowerCase(java.util.Locale.ROOT);
        return value.contains("unavailable") || error instanceof ClassNotFoundException
                || error instanceof NoSuchMethodException || error instanceof NoSuchFieldException;
    }

    private static void incrementState(String state) {
        HOOK_STATES.computeIfAbsent(state, key -> new AtomicInteger()).incrementAndGet();
    }

    private static int stateCount(String state) {
        AtomicInteger value = HOOK_STATES.get(state);
        return value == null ? 0 : value.get();
    }

    private static boolean isWorkbenchTarget(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        return name.startsWith("com.android.wm.shell.")
                || name.startsWith("com.android.server.wm.")
                || name.startsWith("com.android.server.inputmethod.")
                || name.startsWith("com.android.systemui.statusbar.")
                || name.startsWith("com.android.launcher3.")
                || name.startsWith("com.meizu.flyme.launcher.")
                || name.startsWith("com.flyme.systemui.navigationbar.");
    }

    private static String memberName(Member member) {
        StringBuilder result = new StringBuilder(member.getDeclaringClass().getName())
                .append('.').append(member.getName()).append('(');
        Class<?>[] parameters = member instanceof Method
                ? ((Method) member).getParameterTypes()
                : member instanceof Constructor ? ((Constructor<?>) member).getParameterTypes() : new Class<?>[0];
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) result.append(',');
            result.append(parameters[i].getName());
        }
        result.append(')');
        if (member instanceof Method) result.append(':').append(((Method) member).getReturnType().getName());
        return result.toString();
    }

    private static void outcome(String state, String point, String detail) {
        String key = state + '|' + point + '|' + detail;
        AtomicInteger count = HOOK_OUTCOMES.computeIfAbsent(key, value -> new AtomicInteger());
        int current = count.incrementAndGet();
        if (current == 1 || current % 25 == 0) {
            Log.i(TAG, format("hook", -1,
                    "state=" + state + " point=" + safe(point)
                            + " detail=" + detail + " count=" + current
                            + " process=" + safe(processName())));
        }
    }

    private static String processName() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                return android.app.Application.getProcessName();
            }
        } catch (Throwable ignored) { }
        return "pid-" + android.os.Process.myPid();
    }

    private static String safe(String value) {
        return value == null ? "null" : value.replace(' ', '_');
    }
}
