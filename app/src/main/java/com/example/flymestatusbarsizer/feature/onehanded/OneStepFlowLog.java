package com.example.flymestatusbarsizer.feature.onehanded;

import android.util.Log;

import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;

/** Shared LSPosed log for workspace hook initialization and errors. */
public final class OneStepFlowLog {
    public static final String TAG = "FlymeOneStep";
    private static final String PREFIX = "flow=side-workbench";
    private static final ConcurrentHashMap<String, HookStats> HOOK_STATS = new ConcurrentHashMap<>();
    private static volatile XposedInterface logger;

    private OneStepFlowLog() {}

    public static void attachLogger(XposedInterface module) { logger = module; }

    public static void info(String event, int session, String fields) {
        if (isInitializationEvent(event)) emit(Log.INFO, event, session, fields, null);
    }

    public static void info(String event, String fields) { info(event, -1, fields); }

    public static void warn(String event, int session, String fields, Throwable error) {
        emit(Log.WARN, event, session, fields, error);
    }

    public static void warn(String event, String fields, Throwable error) {
        warn(event, -1, fields, error);
    }

    public static int i(String sourceTag, String message) {
        if (isInitializationMessage(message)) {
            emit(Log.INFO, "hook_install", -1,
                    "source=" + safe(sourceTag) + " detail=" + safe(message), null);
        }
        return 0;
    }

    public static int w(String sourceTag, String message) {
        return w(sourceTag, message, null);
    }

    public static int w(String sourceTag, String message, Throwable error) {
        if (!isEnabled()) return 0;
        boolean hookError = message != null && isHookDiagnostic(message);
        String event = hookError ? "hook_install" : "error";
        String owner = ownerForPoint(sourceTag);
        if (hookError) {
            HookStats stats = stats(owner);
            if (isUnavailableDiagnostic(message, error)) stats.unavailable.incrementAndGet();
            else stats.failed.incrementAndGet();
        }
        emit(Log.WARN, event, -1,
                "owner=" + safe(owner) + " source=" + safe(sourceTag)
                        + " detail=" + safe(message) + " process=" + safe(processName()), error);
        return 0;
    }

    public static void hookResolved(Member member) {
        if (member == null || !isWorkbenchTarget(member.getDeclaringClass())) return;
        String point = memberName(member);
        String owner = ownerForPoint(point);
        stats(owner).resolved.incrementAndGet();
        emit(Log.INFO, "hook_install", -1,
                "owner=" + safe(owner) + " point=" + safe(point)
                        + " status=resolved process=" + safe(processName()), null);
    }

    public static void hookInstalled(Member member) {
        if (member == null || !isWorkbenchTarget(member.getDeclaringClass())) return;
        String point = memberName(member);
        String owner = ownerForPoint(point);
        stats(owner).installed.incrementAndGet();
        emit(Log.INFO, "hook_install", -1,
                "owner=" + safe(owner) + " point=" + safe(point)
                        + " status=installed process=" + safe(processName()), null);
    }

    public static void hookTriggered(String point) {}
    public static void hookMatched(String point, boolean matched, String reason) {}
    public static void hookEffective(String point, boolean effective, String reason) {}
    public static void hookBypassed(String point, String reason) {}

    public static void hookUnavailable(String point, Throwable error) {
        if (!isEnabled()) return;
        String owner = ownerForPoint(point);
        stats(owner).unavailable.incrementAndGet();
        emit(Log.WARN, "hook_install", -1,
                "owner=" + safe(owner) + " point=" + safe(point)
                        + " status=unavailable process=" + safe(processName()), error);
    }

    public static boolean isEnabled() {
        try { return ModuleConfig.load(null).workspaceFlowLoggingEnabled; }
        catch (Throwable ignored) { return true; }
    }

    public static void hookSummary(String owner) {
        if (!isEnabled()) return;
        HookStats stats = stats(owner);
        emit(Log.INFO, "hook_summary", -1,
                "owner=" + safe(owner) + " resolved=" + stats.resolved.get()
                        + " installed=" + stats.installed.get()
                        + " unavailable=" + stats.unavailable.get()
                        + " failed=" + stats.failed.get()
                        + " process=" + safe(processName()), null);
    }

    private static void emit(int level, String event, int session, String fields,
                             Throwable error) {
        if (!isEnabled()) return;
        XposedInterface current = logger;
        if (current == null) return;
        String message = format(event, session, fields);
        try {
            if (error == null) current.log(level, TAG, message);
            else current.log(level, TAG, message, error);
        } catch (Throwable ignored) { }
    }

    private static boolean isInitializationEvent(String event) {
        return "server_lifecycle".equals(event) || (event != null && event.startsWith("hook_"));
    }

    private static boolean isInitializationMessage(String message) {
        if (message == null) return false;
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("hook") || value.contains("install")
                || value.contains("unavailable") || value.contains("initialized")
                || value.contains("initialization") || value.contains("hooks ready");
    }

    private static boolean isHookDiagnostic(String message) {
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("hook") || value.contains("unavailable")
                || value.contains("install") || value.contains("cannot");
    }

    private static boolean isUnavailableDiagnostic(String message, Throwable error) {
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("unavailable") || error instanceof ClassNotFoundException
                || error instanceof NoSuchMethodException || error instanceof NoSuchFieldException;
    }

    private static boolean isWorkbenchTarget(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        return name.startsWith("com.android.wm.shell.") || name.startsWith("com.android.server.wm.")
                || name.startsWith("com.android.server.inputmethod.")
                || name.startsWith("com.android.systemui.statusbar.")
                || name.startsWith("com.android.systemui.shared.")
                || name.startsWith("com.android.launcher3.")
                || name.startsWith("com.meizu.flyme.launcher.")
                || name.startsWith("com.flyme.systemui.navigationbar.");
    }

    private static String memberName(Member member) {
        StringBuilder result = new StringBuilder(member.getDeclaringClass().getName())
                .append('.').append(member.getName()).append('(');
        Class<?>[] parameters = member instanceof Method ? ((Method) member).getParameterTypes()
                : member instanceof Constructor ? ((Constructor<?>) member).getParameterTypes()
                : new Class<?>[0];
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) result.append(',');
            result.append(parameters[i].getName());
        }
        result.append(')');
        if (member instanceof Method) result.append(':').append(((Method) member).getReturnType().getName());
        return result.toString();
    }

    private static String ownerForPoint(String point) {
        if (point != null) {
            if (point.startsWith("com.android.server.")) return "system_server.workspace_ime";
            if (point.startsWith("com.android.launcher3.")
                    || point.startsWith("com.meizu.flyme.launcher.")) return "launcher.workspace";
        }
        String process = processName().toLowerCase(Locale.ROOT);
        if (process.contains("system_server") || process.equals("android")) {
            return "system_server.workspace_ime";
        }
        if (process.contains("launcher")) return "launcher.workspace";
        return "systemui.workspace";
    }

    private static HookStats stats(String owner) {
        return HOOK_STATS.computeIfAbsent(owner == null ? "unknown" : owner, key -> new HookStats());
    }

    private static String format(String event, int session, String fields) {
        return PREFIX + " event=" + event + " session=" + (session < 0 ? "none" : session)
                + (fields == null || fields.isEmpty() ? "" : " " + fields);
    }

    private static String processName() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) return android.app.Application.getProcessName();
        } catch (Throwable ignored) { }
        return "pid-" + android.os.Process.myPid();
    }

    private static String safe(String value) {
        return value == null ? "null" : value.replace(' ', '_');
    }

    private static final class HookStats {
        final AtomicInteger resolved = new AtomicInteger();
        final AtomicInteger installed = new AtomicInteger();
        final AtomicInteger unavailable = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
    }
}
