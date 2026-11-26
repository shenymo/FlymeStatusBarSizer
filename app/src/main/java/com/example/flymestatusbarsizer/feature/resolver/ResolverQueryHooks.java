package com.example.flymestatusbarsizer.feature.resolver;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.openwith.OpenWithContentType;
import com.example.flymestatusbarsizer.feature.openwith.OpenWithTargetProfiles;
import com.example.flymestatusbarsizer.feature.share.ShareContentType;
import com.example.flymestatusbarsizer.feature.share.ShareTargetRules;
import com.example.flymestatusbarsizer.feature.share.ShareTargetProfiles;

import java.lang.reflect.Method;
import java.util.List;

public final class ResolverQueryHooks {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final String MODULE_PACKAGE = "com.fiyme.statusbarsizer";
    private static volatile int moduleUid = Integer.MIN_VALUE;
    private static volatile ResolverConfig config = ResolverConfig.EMPTY;

    private ResolverQueryHooks() { }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            SharedPreferences prefs = module.getRemotePreferences("status_bar_sizer");
            refreshConfig(prefs);
            if (prefs != null) {
                prefs.registerOnSharedPreferenceChangeListener(
                        (sharedPreferences, key) -> refreshConfig(sharedPreferences));
            }
            Class<?> computer = Class.forName("com.android.server.pm.ComputerEngine", false, loader);
            Method query = null;
            for (Method method : computer.getDeclaredMethods()) {
                if ("queryIntentActivitiesInternal".equals(method.getName())
                        && method.getParameterTypes().length == 9
                        && List.class.isAssignableFrom(method.getReturnType())) {
                    query = method;
                    break;
                }
            }
            if (query == null) throw new NoSuchMethodException("queryIntentActivitiesInternal");
            query.setAccessible(true);
            Method target = query;
            module.intercept(target, chain -> {
                Object original = chain.proceed();
                if (!(original instanceof List<?>)) return original;
                if (Binder.getCallingUid() == Process.myUid() || isModuleCaller()) return original;
                ResolverConfig current = config;
                if (!current.enabled || (!current.openWithTargetsEnabled
                        && !current.shareTargetsEnabled)) {
                    return original;
                }
                Object[] args = chain.getArgs().toArray();
                Intent intent = args.length == 0 || !(args[0] instanceof Intent)
                        ? null : (Intent) args[0];
                ShareTargetRules rules = rulesFor(current, intent);
                return rules.hidden().isEmpty()
                        ? original : rules.filterHidden((List<?>) original, ResolverQueryHooks::componentOf);
            });
            module.deoptimize(target);
            Log.i(TAG, "PackageManager resolver result filter installed");
        } catch (Throwable error) {
            Log.w(TAG, "PackageManager resolver result filter unavailable", error);
        }
    }

    private static ShareTargetRules rulesFor(ResolverConfig config, Intent source) {
        Intent intent = unwrap(source);
        if (intent == null || intent.getComponent() != null || intent.getPackage() != null) {
            return ShareTargetRules.EMPTY;
        }
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            return config.openWithTargetProfiles.rulesFor(openWithType(intent), config.openWithTargetRules);
        }
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            return config.shareTargetProfiles.rulesFor(
                    ShareContentType.fromMime(intent.getType()), config.shareTargetRules);
        }
        return ShareTargetRules.EMPTY;
    }

    private static Intent unwrap(Intent source) {
        Intent intent = source;
        for (int depth = 0; intent != null && depth < 4; depth++) {
            if (Intent.ACTION_CHOOSER.equals(intent.getAction())) {
                Object nested = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (!(nested instanceof Intent)) return intent;
                intent = (Intent) nested;
                continue;
            }
            Intent selector = intent.getSelector();
            if (selector != null) intent = selector;
            break;
        }
        return intent;
    }

    private static OpenWithContentType openWithType(Intent intent) {
        if (intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                return OpenWithContentType.WEB;
            }
        }
        String mime = intent.getType();
        if (mime == null || mime.isEmpty() || "*/*".equals(mime)
                || "application/*".equals(mime) || "application/octet-stream".equals(mime)) {
            return OpenWithContentType.DEFAULT;
        }
        return OpenWithContentType.fromMime(mime);
    }

    private static String componentOf(Object value) {
        if (!(value instanceof ResolveInfo)) return "";
        ActivityInfo info = ((ResolveInfo) value).activityInfo;
        return info == null ? "" : info.packageName + "/" + info.name;
    }

    private static boolean isModuleCaller() {
        int callerUid = Binder.getCallingUid();
        int cachedUid = moduleUid;
        if (cachedUid != Integer.MIN_VALUE) return callerUid == cachedUid;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method current = activityThread.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            Object thread = current.invoke(null);
            Method systemContext = activityThread.getDeclaredMethod("getSystemContext");
            systemContext.setAccessible(true);
            Object context = systemContext.invoke(thread);
            if (!(context instanceof android.content.Context)) return false;
            int resolvedUid = ((android.content.Context) context).getPackageManager()
                    .getPackageUid(MODULE_PACKAGE, 0);
            moduleUid = resolvedUid;
            return callerUid == resolvedUid;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void refreshConfig(SharedPreferences prefs) {
        if (prefs == null) {
            config = ResolverConfig.EMPTY;
            return;
        }
        try {
            config = new ResolverConfig(
                    prefs.getBoolean(SettingsStore.KEY_ENABLED, SettingsStore.DEFAULT_ENABLED),
                    prefs.getBoolean(SettingsStore.KEY_OPEN_WITH_TARGETS_ENABLED,
                            SettingsStore.DEFAULT_OPEN_WITH_TARGETS_ENABLED),
                    prefs.getBoolean(SettingsStore.KEY_SHARE_TARGETS_ENABLED,
                            SettingsStore.DEFAULT_SHARE_TARGETS_ENABLED),
                    ShareTargetRules.parse(
                            prefs.getString(SettingsStore.KEY_OPEN_WITH_TARGET_ORDER, ""),
                            prefs.getString(SettingsStore.KEY_OPEN_WITH_HIDDEN_TARGETS, "")),
                    OpenWithTargetProfiles.parse(prefs.getString(
                            SettingsStore.KEY_OPEN_WITH_TARGET_PROFILES, "")),
                    ShareTargetRules.parse(
                            prefs.getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""),
                            prefs.getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, "")),
                    ShareTargetProfiles.parse(prefs.getString(
                            SettingsStore.KEY_SHARE_TARGET_PROFILES, "")));
        } catch (Throwable error) {
            Log.w(TAG, "Resolver settings unavailable", error);
            config = ResolverConfig.EMPTY;
        }
    }

    private static final class ResolverConfig {
        static final ResolverConfig EMPTY = new ResolverConfig(false, false, false,
                ShareTargetRules.EMPTY, new OpenWithTargetProfiles(), ShareTargetRules.EMPTY,
                new ShareTargetProfiles());

        final boolean enabled;
        final boolean openWithTargetsEnabled;
        final boolean shareTargetsEnabled;
        final ShareTargetRules openWithTargetRules;
        final OpenWithTargetProfiles openWithTargetProfiles;
        final ShareTargetRules shareTargetRules;
        final ShareTargetProfiles shareTargetProfiles;

        ResolverConfig(boolean enabled, boolean openWithTargetsEnabled, boolean shareTargetsEnabled,
                ShareTargetRules openWithTargetRules, OpenWithTargetProfiles openWithTargetProfiles,
                ShareTargetRules shareTargetRules, ShareTargetProfiles shareTargetProfiles) {
            this.enabled = enabled;
            this.openWithTargetsEnabled = openWithTargetsEnabled;
            this.shareTargetsEnabled = shareTargetsEnabled;
            this.openWithTargetRules = openWithTargetRules;
            this.openWithTargetProfiles = openWithTargetProfiles;
            this.shareTargetRules = shareTargetRules;
            this.shareTargetProfiles = shareTargetProfiles;
        }
    }
}
