package com.example.flymestatusbarsizer.feature.launcher.organizer;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.RemoteSettingsSync;
import com.example.flymestatusbarsizer.ui.PageViewUtils;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.Callable;

public final class LauncherUsageSortPage {
    private final MainActivity activity;
    private final TextView status;
    private final TextView sortButton;
    private final ProgressBar progress;
    private boolean busy;

    public static void bind(MainActivity activity, LinearLayout root) {
        new LauncherUsageSortPage(activity, root);
    }

    private LauncherUsageSortPage(MainActivity activity, LinearLayout root) {
        this.activity = activity;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView permission = activity.filledButton("使用情况访问权限", activity.surfaceSoftColor(), activity.primaryColor());
        activity.setTapClickListener(permission, view -> openUsageSettings());
        content.addView(permission, PageViewUtils.matchWrap());
        sortButton = activity.filledButton("按使用频率整理文件夹", activity.primaryColor(), android.graphics.Color.WHITE);
        activity.setTapClickListener(sortButton, view -> sort());
        content.addView(sortButton, activity.matchWrapWithTop(8));
        status = new TextView(activity);
        status.setText("按最近 30 天使用次数排序，使用次数相同按最近使用时间排序。文件夹位置不变。\n排序会作用于桌面上的所有文件夹。\n\n首次使用需要开启使用情况访问权限。");
        status.setTextSize(14);
        status.setTextColor(activity.subtextColor());
        content.addView(status, activity.matchWrapWithTop(12));
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        content.addView(progress, activity.matchWrapWithTop(8));
        root.addView(activity.buildSectionCard("文件夹应用排序", "独立于 AI 桌面整理，直接调整现有文件夹内应用顺序。", content, true), PageViewUtils.matchWrap());
    }

    private void sort() {
        if (busy) return;
        if (!hasUsageAccess()) {
            new AlertDialog.Builder(activity).setTitle("需要使用情况访问权限")
                    .setMessage("允许本模块查看应用使用记录后，才能按使用频率排列文件夹内应用。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("前往设置", (dialog, which) -> openUsageSettings()).show();
            return;
        }
        run(() -> {
            JSONObject desktop = new JSONObject(command("read", null));
            return command("sort_usage", new JSONObject().put("hash", desktop.getString("hash"))
                    .put("usage", recentUsage()).toString());
        });
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = activity.getSystemService(AppOpsManager.class);
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                activity.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private void openUsageSettings() {
        activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private JSONObject recentUsage() throws Exception {
        UsageStatsManager manager = activity.getSystemService(UsageStatsManager.class);
        long end = System.currentTimeMillis();
        UsageEvents events = manager.queryEvents(end - 30L * 24 * 60 * 60 * 1000, end);
        UsageEvents.Event event = new UsageEvents.Event();
        JSONObject result = new JSONObject();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if (type != UsageEvents.Event.MOVE_TO_FOREGROUND
                    && type != UsageEvents.Event.ACTIVITY_RESUMED) continue;
            String packageName = event.getPackageName();
            JSONObject score = result.optJSONObject(packageName);
            if (score == null) score = new JSONObject().put("count", 0).put("last", 0);
            score.put("count", score.getLong("count") + 1)
                    .put("last", Math.max(score.getLong("last"), event.getTimeStamp()));
            result.put(packageName, score);
        }
        return result;
    }

    private String command(String action, String data) throws Exception {
        SharedPreferences remote = RemoteSettingsSync.remotePrefs();
        if (remote == null) throw new IllegalStateException("尚未连接 LSPosed，请确认模块已启用并重新打开模块");
        Bundle payload = new Bundle();
        payload.putString("data", data == null ? "" : data);
        Bundle submitted = activity.getContentResolver().call(LauncherOrganizerProvider.URI, "submit", action, payload);
        if (submitted == null) throw new IllegalStateException("无法创建桌面操作");
        String id = submitted.getString("id");
        if (!remote.edit().putString(LauncherOrganizerProvider.SIGNAL, id).commit()) throw new IllegalStateException("无法通知桌面");
        long deadline = SystemClock.elapsedRealtime() + 60_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            Bundle state = activity.getContentResolver().call(LauncherOrganizerProvider.URI, "status", id, null);
            String current = state == null ? "" : state.getString("state", "");
            if (id.equals(state == null ? "" : state.getString("id", "")) && "done".equals(current)) {
                String error = state.getString("error", "");
                if (!error.isEmpty()) throw new IllegalStateException(error);
                return state.getString("data", "");
            }
            Thread.sleep(250);
        }
        activity.getContentResolver().call(LauncherOrganizerProvider.URI, "cancel", id, null);
        throw new IllegalStateException("桌面响应超时，请稍后重试");
    }

    private void run(Callable<String> work) {
        busy = true;
        sortButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText("正在整理文件夹...");
        new Thread(() -> {
            String message;
            try { message = work.call(); }
            catch (Exception error) { message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
            String result = message;
            activity.runOnUiThread(() -> {
                busy = false;
                sortButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                status.setText(result);
            });
        }, "FlymeUsageSort").start();
    }
}
