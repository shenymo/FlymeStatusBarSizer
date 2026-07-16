package com.example.flymestatusbarsizer;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

final class F2fsGcManager {
    private static final String PREF_LAST_TIME = "f2fs_gc_last_time";
    private static final String PREF_LAST_SEGMENTS = "f2fs_gc_last_segments";
    private static final String PREF_LAST_RESULT = "f2fs_gc_last_result";
    private static final long MIN_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private F2fsGcManager() {
    }

    static void refreshStatus(Context context, TextView view) {
        new Thread(() -> {
            String node = findNode();
            boolean charging = isCharging(context);
            boolean locked = context.getSystemService(KeyguardManager.class).isKeyguardLocked();
            boolean idle = context.getSystemService(PowerManager.class).isDeviceIdleMode();
            SharedPreferences prefs = SettingsStore.prefs(context);
            long lastTime = prefs.getLong(PREF_LAST_TIME, 0L);
            long segments = prefs.getLong(PREF_LAST_SEGMENTS, 0L);
            String result = prefs.getString(PREF_LAST_RESULT, "尚未执行");
            String time = lastTime == 0L ? "从未" : DateFormat.getDateTimeInstance().format(new Date(lastTime));
            String text = "Root/F2FS 节点：" + (node == null ? "不可用" : "可用\n" + node)
                    + "\n充电：" + yes(charging)
                    + "　锁屏：" + yes(locked)
                    + "　设备空闲：" + yes(idle)
                    + "\n距离上次超过 24 小时：" + yes(lastTime == 0L
                    || System.currentTimeMillis() - lastTime >= MIN_INTERVAL_MS)
                    + "\n上次回收：" + time
                    + "\n回收结果：" + result
                    + "\n回收量：" + segments + " 段（约 " + (segments * 2L) + " MiB）";
            view.post(() -> view.setText(text));
        }, "f2fs-gc-status").start();
    }

    static void run(Context context, boolean force, Runnable finished) {
        if (!RUNNING.compareAndSet(false, true)) {
            if (finished != null) {
                finished.run();
            }
            return;
        }
        new Thread(() -> {
            SharedPreferences prefs = SettingsStore.prefs(context);
            String result = "执行失败";
            long reclaimed = 0L;
            boolean attempted = false;
            try {
                long lastTime = prefs.getLong(PREF_LAST_TIME, 0L);
                if (!force && (System.currentTimeMillis() - lastTime < MIN_INTERVAL_MS
                        || !isCharging(context)
                        || !context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                        || !context.getSystemService(PowerManager.class).isDeviceIdleMode())) {
                    result = "自动条件未满足";
                    return;
                }
                String node = findNode();
                if (node == null) {
                    result = "Root 或 F2FS 节点不可用";
                    return;
                }
                attempted = true;
                String script = "D='" + node + "'; "
                        + "before=$(cat \"$D/free_segments\"); "
                        + "rb=$(cat \"$D/gc_reclaimed_segments\"); "
                        + "trap 'echo 0 > \"$D/gc_urgent\"' EXIT; "
                        + "echo 1 > \"$D/gc_urgent\"; sleep 60; echo 0 > \"$D/gc_urgent\"; "
                        + "after=$(cat \"$D/free_segments\"); "
                        + "ra=$(cat \"$D/gc_reclaimed_segments\"); "
                        + "echo \"$before $after $rb $ra\"";
                String[] values = execRoot(script).trim().split("\\s+");
                if (values.length < 4) {
                    throw new IllegalStateException("无回收统计");
                }
                long freeDelta = Long.parseLong(values[1]) - Long.parseLong(values[0]);
                long counterDelta = Long.parseLong(values[3]) - Long.parseLong(values[2]);
                reclaimed = Math.max(0L, Math.max(freeDelta, counterDelta));
                result = "完成";
            } catch (Throwable t) {
                result = "失败：" + t.getMessage();
            } finally {
                SharedPreferences.Editor editor = prefs.edit().putString(PREF_LAST_RESULT, result);
                if (attempted) {
                    editor.putLong(PREF_LAST_TIME, System.currentTimeMillis())
                            .putLong(PREF_LAST_SEGMENTS, reclaimed);
                }
                editor.apply();
                RUNNING.set(false);
                if (finished != null) {
                    finished.run();
                }
            }
        }, "f2fs-gc-run").start();
    }

    private static String findNode() {
        try {
            String output = execRoot(
                    "src=$(awk '$2==\"/data\" && $3==\"f2fs\" {print $1; exit}' /proc/mounts); "
                    + "[ -n \"$src\" ] || exit 1; "
                    + "real=$(readlink -f \"$src\" 2>/dev/null); "
                    + "[ -n \"$real\" ] || real=\"$src\"; "
                    + "name=${real##*/}; node=\"/sys/fs/f2fs/$name\"; "
                    + "[ -f \"$node/gc_urgent\" ] && [ -r \"$node/free_segments\" ] || exit 1; "
                    + "echo \"$node\"").trim();
            return output.startsWith("/sys/fs/f2fs/") ? output : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String execRoot(String command) throws Exception {
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        if (process.waitFor() != 0) {
            throw new IllegalStateException(output.toString().trim());
        }
        return output.toString();
    }

    private static boolean isCharging(Context context) {
        Intent battery = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = battery == null ? -1
                : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private static String yes(boolean value) {
        return value ? "满足" : "不满足";
    }
}
