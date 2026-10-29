package com.example.flymestatusbarsizer.feature.assistant;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.SideGestureTargetStore;

final class SideGestureLaunchAction {
    private SideGestureLaunchAction() { }

    static SideGestureActions.Action action(Context context, int actionId) {
        return new SideGestureActions.Action() {
            @Override public boolean isReady() {
                SideGestureTargetStore.Target target = findTarget(context, actionId);
                if (target != null && target.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY
                        && target.root) {
                    return ComponentName.unflattenFromString(target.value) != null;
                }
                Intent intent = resolveIntent(context, actionId);
                return intent != null && context.getPackageManager().resolveActivity(
                        intent, PackageManager.MATCH_DEFAULT_ONLY) != null;
            }

            @Override public void execute(boolean fromLeft, Runnable onSuccess) {
                SideGestureTargetStore.Target target = findTarget(context, actionId);
                if (target != null && target.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY
                        && target.root) {
                    ComponentName component = ComponentName.unflattenFromString(target.value);
                    if (component == null) return;
                    String command = "am start -n " + shellQuote(component.flattenToString());
                    new Thread(() -> {
                        try {
                            Process process = new ProcessBuilder("su", "-c", command)
                                    .redirectErrorStream(true).start();
                            byte[] buffer = new byte[1024];
                            while (process.getInputStream().read(buffer) != -1) { }
                            if (process.waitFor() == 0 && onSuccess != null) onSuccess.run();
                            else AssistantHooks.warn("Root Activity launch failed", null);
                        } catch (Throwable error) {
                            AssistantHooks.warn("Root Activity launch failed", error);
                        }
                    }, "side-gesture-root-activity").start();
                    return;
                }
                Intent intent = resolveIntent(context, actionId);
                if (intent == null) return;
                if (target == null || target.type != SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY
                        || target.newTask) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                try {
                    context.startActivity(intent);
                    if (onSuccess != null) onSuccess.run();
                } catch (Throwable error) {
                    AssistantHooks.warn("Cannot launch side gesture target", error);
                }
            }
        };
    }

    private static Intent resolveIntent(Context context, int actionId) {
        SideGestureTargetStore.Target target = findTarget(context, actionId);
        if (target != null) return resolveTargetIntent(context, target);
        ModuleConfig config = ModuleConfig.load(context);
        switch (actionId) {
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP:
                return config.sideGestureTargetApp.isEmpty() ? null
                        : context.getPackageManager().getLaunchIntentForPackage(config.sideGestureTargetApp);
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY:
                ComponentName component = ComponentName.unflattenFromString(
                        config.sideGestureTargetActivity);
                return component == null ? null : new Intent().setComponent(component);
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI:
                try {
                    return config.sideGestureTargetIntentUri.trim().isEmpty() ? null
                            : Intent.parseUri(config.sideGestureTargetIntentUri.trim(),
                                    Intent.URI_INTENT_SCHEME);
                } catch (Exception ignored) {
                    return null;
                }
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL:
                String url = config.sideGestureTargetSchemeUrl.trim();
                return url.isEmpty() ? null : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            default:
                return null;
        }
    }

    private static Intent resolveTargetIntent(Context context, SideGestureTargetStore.Target target) {
        switch (target.type) {
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP:
                return context.getPackageManager().getLaunchIntentForPackage(target.value);
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY:
                ComponentName component = ComponentName.unflattenFromString(target.value);
                if (component == null) return null;
                Intent intent = (target.action.isEmpty()
                        ? new Intent() : new Intent(target.action)).setComponent(component);
                if (!target.dataUri.isEmpty()) intent.setData(Uri.parse(target.dataUri));
                Bundle extras = new Bundle();
                for (String line : target.extras.split("\\r?\\n")) {
                    int separator = line.indexOf('=');
                    if (separator <= 0) continue;
                    String key = line.substring(0, separator).trim();
                    if (!key.isEmpty()) extras.putString(key, line.substring(separator + 1).trim());
                }
                if (!extras.isEmpty()) intent.putExtras(extras);
                return intent;
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI:
                try {
                    return Intent.parseUri(target.value, Intent.URI_INTENT_SCHEME);
                } catch (Exception ignored) {
                    return null;
                }
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL:
                return new Intent(Intent.ACTION_VIEW, Uri.parse(target.value));
            default:
                return null;
        }
    }

    private static SideGestureTargetStore.Target findTarget(Context context, int actionId) {
        return SideGestureTargetStore.find(ModuleConfig.load(context).sideGestureTargetsJson, actionId);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
