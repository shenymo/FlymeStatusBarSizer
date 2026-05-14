package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.feature.mback.MBackHooks;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;

public final class ClockDetailActionResolver {
    private ClockDetailActionResolver() {
    }

    public static ClockDetailActionEntry[] resolveEntries(
            Context context,
            ClockDetailActionSpec[] specs) {
        ClockDetailActionEntry[] entries = new ClockDetailActionEntry[ClockDetailActionSpec.SLOT_COUNT];
        for (int slot = 0; slot < entries.length; slot++) {
            entries[slot] = resolveEntry(context, specAt(specs, slot));
        }
        return entries;
    }

    public static ClockDetailActionEntry resolveEntry(Context context, ClockDetailActionSpec spec) {
        ClockDetailActionSpec safeSpec = spec != null ? spec : ClockDetailActionSpec.empty(0);
        switch (safeSpec.type) {
            case ClockDetailActionSpec.TYPE_ASSISTANT_ACTION:
                return resolveAssistantEntry(context, safeSpec);
            case ClockDetailActionSpec.TYPE_APP:
                return resolveAppEntry(context, safeSpec);
            case ClockDetailActionSpec.TYPE_INTENT:
                return resolveIntentEntry(context, safeSpec);
            case ClockDetailActionSpec.TYPE_ACTIVITY:
                return resolveActivityEntry(context, safeSpec);
            default:
                return ClockDetailActionEntry.empty(safeSpec.slot);
        }
    }

    private static ClockDetailActionEntry resolveAssistantEntry(
            Context context,
            ClockDetailActionSpec spec) {
        String assistantAction = spec.assistantAction;
        Drawable icon = ClockDetailAssistantActionCatalog.resolveActionIcon(context, assistantAction);
        String label = chooseLabel(
                spec.title,
                ClockDetailAssistantActionCatalog.resolveDisplayLabel(assistantAction),
                "Assistant");
        if (context == null || assistantAction.isEmpty()) {
            return invalidEntry(spec, label, icon);
        }
        Intent launchIntent = ClockDetailAssistantActionCatalog.buildLaunchIntent(
                context,
                assistantAction);
        if (!canResolveIntent(context, launchIntent)) {
            return invalidEntry(spec, label, icon);
        }
        return validEntry(spec, label, launchIntent, icon);
    }

    private static ClockDetailActionEntry resolveAppEntry(Context context, ClockDetailActionSpec spec) {
        String packageName = spec.packageName;
        String customTitle = spec.title;
        if (context == null || packageName.isEmpty()) {
            return invalidEntry(spec, chooseLabel(customTitle, packageName, "应用"));
        }
        PackageManager packageManager = context.getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        String label = chooseLabel(customTitle, resolveApplicationLabel(packageManager, packageName), packageName);
        if (launchIntent == null) {
            return invalidEntry(spec, label);
        }
        launchIntent = new Intent(launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return validEntry(spec, label, launchIntent);
    }

    private static ClockDetailActionEntry resolveIntentEntry(
            Context context,
            ClockDetailActionSpec spec) {
        String rawIntent = spec.intentUri;
        String customTitle = spec.title;
        if (context == null || rawIntent.isEmpty()) {
            return invalidEntry(spec, chooseLabel(customTitle, fallbackLabelFromUri(rawIntent), "Intent"));
        }
        Intent launchIntent = null;
        try {
            launchIntent = MBackHooks.buildConfiguredIntent(rawIntent);
        } catch (Throwable ignored) {
            launchIntent = null;
        }
        String autoLabel = resolveIntentLabel(context, launchIntent);
        String label = chooseLabel(customTitle, autoLabel, fallbackLabelFromUri(rawIntent));
        if (!canResolveIntent(context, launchIntent)) {
            return invalidEntry(spec, label);
        }
        return validEntry(spec, label, launchIntent);
    }

    private static ClockDetailActionEntry resolveActivityEntry(
            Context context,
            ClockDetailActionSpec spec) {
        String customTitle = spec.title;
        String componentNameText = spec.componentName;
        if (context == null || componentNameText.isEmpty()) {
            return invalidEntry(spec, chooseLabel(customTitle, componentNameText, "Activity"));
        }
        ComponentName componentName = ComponentName.unflattenFromString(componentNameText);
        if (componentName == null) {
            return invalidEntry(spec, chooseLabel(customTitle, componentNameText, "Activity"));
        }
        PackageManager packageManager = context.getPackageManager();
        String autoLabel = resolveActivityLabel(packageManager, componentName);
        String label = chooseLabel(customTitle, autoLabel, componentName.flattenToShortString());
        if (autoLabel.isEmpty()) {
            return invalidEntry(spec, label);
        }
        Intent launchIntent = new Intent();
        launchIntent.setComponent(componentName);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return validEntry(spec, label, launchIntent);
    }

    private static boolean canResolveIntent(Context context, Intent intent) {
        if (context == null || intent == null) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        ComponentName componentName = intent.getComponent();
        if (componentName != null) {
            return !resolveActivityLabel(packageManager, componentName).isEmpty();
        }
        ResolveInfo resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null && resolveInfo.activityInfo != null;
    }

    private static String resolveIntentLabel(Context context, Intent intent) {
        if (context == null || intent == null) {
            return "";
        }
        PackageManager packageManager = context.getPackageManager();
        ComponentName componentName = intent.getComponent();
        if (componentName != null) {
            String activityLabel = resolveActivityLabel(packageManager, componentName);
            if (!activityLabel.isEmpty()) {
                return activityLabel;
            }
        }
        ResolveInfo resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo != null) {
            CharSequence label = resolveInfo.loadLabel(packageManager);
            if (label != null && label.length() > 0) {
                return label.toString();
            }
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo != null) {
                String activityLabel = resolveActivityLabel(
                        packageManager,
                        new ComponentName(activityInfo.packageName, activityInfo.name));
                if (!activityLabel.isEmpty()) {
                    return activityLabel;
                }
            }
        }
        String packageName = intent.getPackage();
        if (packageName != null && !packageName.trim().isEmpty()) {
            String appLabel = resolveApplicationLabel(packageManager, packageName.trim());
            if (!appLabel.isEmpty()) {
                return appLabel;
            }
            return packageName.trim();
        }
        Uri data = intent.getData();
        return fallbackLabelFromUri(data == null ? "" : data.toString());
    }

    private static String resolveApplicationLabel(PackageManager packageManager, String packageName) {
        if (packageManager == null || packageName == null || packageName.trim().isEmpty()) {
            return "";
        }
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(applicationInfo);
            return label == null ? "" : label.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String resolveActivityLabel(PackageManager packageManager, ComponentName componentName) {
        if (packageManager == null || componentName == null) {
            return "";
        }
        try {
            ActivityInfo activityInfo = packageManager.getActivityInfo(componentName, 0);
            CharSequence label = activityInfo.loadLabel(packageManager);
            if (label != null && label.length() > 0) {
                return label.toString().trim();
            }
            String shortClassName = componentName.getShortClassName();
            return shortClassName == null ? "" : shortClassName.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String chooseLabel(String primary, String secondary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.trim().isEmpty()) {
            return secondary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static String fallbackLabelFromUri(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            Uri uri = Uri.parse(trimmed);
            if (uri != null) {
                String host = uri.getHost();
                if (host != null && !host.trim().isEmpty()) {
                    return host.trim();
                }
                String scheme = uri.getScheme();
                if (scheme != null && !scheme.trim().isEmpty()) {
                    return scheme.trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return trimmed;
    }

    private static ClockDetailActionEntry validEntry(
            ClockDetailActionSpec spec,
            String label,
            Intent launchIntent) {
        return validEntry(spec, label, launchIntent, null);
    }

    private static ClockDetailActionEntry validEntry(
            ClockDetailActionSpec spec,
            String label,
            Intent launchIntent,
            Drawable icon) {
        return new ClockDetailActionEntry(spec.slot, spec.type, label, icon, launchIntent, true);
    }

    private static ClockDetailActionEntry invalidEntry(
            ClockDetailActionSpec spec,
            String label) {
        return invalidEntry(spec, label, null);
    }

    private static ClockDetailActionEntry invalidEntry(
            ClockDetailActionSpec spec,
            String label,
            Drawable icon) {
        return new ClockDetailActionEntry(spec.slot, spec.type, label, icon, null, false);
    }

    private static ClockDetailActionSpec specAt(ClockDetailActionSpec[] specs, int slot) {
        if (specs == null
                || slot < 0
                || slot >= ClockDetailActionSpec.SLOT_COUNT
                || slot >= specs.length) {
            return ClockDetailActionSpec.empty(slot);
        }
        ClockDetailActionSpec spec = specs[slot];
        return spec != null ? spec : ClockDetailActionSpec.empty(slot);
    }
}
