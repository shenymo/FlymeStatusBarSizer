package com.example.flymestatusbarsizer.feature.assistant;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.SideGestureTargetStore;

final class SideGestureLaunchAction {
    private SideGestureLaunchAction() { }

    static SideGestureActions.Action action(Context context, int actionId) {
        return new SideGestureActions.Action() {
            @Override public boolean isReady() {
                Intent intent = resolveIntent(context, actionId);
                return intent != null && context.getPackageManager().resolveActivity(
                        intent, PackageManager.MATCH_DEFAULT_ONLY) != null;
            }

            @Override public void execute(boolean fromLeft, Runnable onSuccess) {
                Intent intent = resolveIntent(context, actionId);
                if (intent == null) return;
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
        ModuleConfig config = ModuleConfig.load(context);
        SideGestureTargetStore.Target target = SideGestureTargetStore.find(
                config.sideGestureTargetsJson, actionId);
        if (target != null) return resolveTargetIntent(context, target);
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
                return component == null ? null : new Intent().setComponent(component);
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
}
