package com.example.flymestatusbarsizer.feature.openwith;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.feature.share.ShareTargetRules;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class OpenWithTargetsHooks {
    public static final String PACKAGE = "android";
    private static final String RESOLVER = "com.android.internal.app.ResolverActivity";
    private static final String TAG = "FlymeStatusBarSizer";

    private OpenWithTargetsHooks() { }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> base = Class.forName("com.android.internal.chooser.BaseResolverHooker", false, loader);
            Class<?> item = Class.forName("com.android.internal.chooser.bean.DisplayResolveInfo", false, loader);
            Field activityField = base.getDeclaredField("mActivity");
            Field resolveInfoField = item.getDeclaredField("resolveInfo");
            activityField.setAccessible(true);
            resolveInfoField.setAccessible(true);
            Method filter = base.getDeclaredMethod("filterResolve", List.class);
            filter.setAccessible(true);
            module.intercept(filter, chain -> {
                Object original = chain.proceed();
                ModuleConfig config = ModuleConfig.load(null);
                if (!config.enabled || !config.openWithTargetsEnabled || !(original instanceof List<?>)) {
                    return original;
                }
                try {
                    Object activity = activityField.get(chain.getThisObject());
                    if (!(activity instanceof Activity) || !RESOLVER.equals(activity.getClass().getName())) {
                        return original;
                    }
                    ShareTargetRules rules = rulesFor(config, (Activity) activity);
                    if (rules.hidden().isEmpty()) return original;
                    return rules.filterHidden((List<?>) original,
                            value -> componentOf(value, resolveInfoField));
                } catch (Throwable error) {
                    Log.w(TAG, "Open-with list rules failed; using system targets", error);
                    return original;
                }
            });
            deoptimize(module, base, "showNormalTargets");
            installAdapter(module, loader, resolveInfoField);
            Log.i(TAG, "Open-with list rules installed in ResolverActivity");
        } catch (Throwable error) {
            Log.w(TAG, "Open-with hooks unavailable on this system", error);
        }
    }

    private static void installAdapter(FlymeStatusBarSizer module, ClassLoader loader,
            Field resolveInfoField) {
        try {
            Class<?> adapter = Class.forName("com.android.internal.chooser.adapter.BaseResolverAdapter", false, loader);
            Field activityField = adapter.getDeclaredField("mActivity");
            Field isChooser = adapter.getDeclaredField("mIsChooserActivity");
            activityField.setAccessible(true);
            isChooser.setAccessible(true);
            Method rebuild = adapter.getDeclaredMethod("rebuildData", List.class);
            rebuild.setAccessible(true);
            module.intercept(rebuild, chain -> {
                Object[] args = chain.getArgs().toArray();
                Object[] replacement = args;
                try {
                    ModuleConfig config = ModuleConfig.load(null);
                    if (config.enabled && config.openWithTargetsEnabled
                            && !isChooser.getBoolean(chain.getThisObject())
                            && args.length > 0 && args[0] instanceof List<?>) {
                        Activity activity = (Activity) activityField.get(chain.getThisObject());
                        if (activity != null && RESOLVER.equals(activity.getClass().getName())) {
                            ShareTargetRules rules = rulesFor(config, activity);
                            if (!rules.isEmpty()) {
                                replacement = new Object[]{rules.apply((List<?>) args[0],
                                        value -> componentOf(value, resolveInfoField))};
                            }
                        }
                    }
                } catch (Throwable error) {
                    Log.w(TAG, "Open-with adapter rules failed; using system targets", error);
                }
                return chain.proceed(replacement);
            });
            deoptimize(module, adapter, "setData", "refresh", "-$$Nest$mrebuildData");
        } catch (Throwable error) {
            Log.w(TAG, "Open-with adapter hook unavailable", error);
        }
    }

    private static ShareTargetRules rulesFor(ModuleConfig config, Activity activity) {
        android.content.Intent intent = activity.getIntent();
        if (intent == null || (!android.content.Intent.ACTION_VIEW.equals(intent.getAction())
                && !android.content.Intent.ACTION_EDIT.equals(intent.getAction()))) {
            return ShareTargetRules.EMPTY;
        }
        OpenWithContentType type = OpenWithContentType.resolve(activity, intent);
        return config.openWithTargetProfiles.rulesFor(type, config.openWithTargetRules);
    }

    private static void deoptimize(FlymeStatusBarSizer module, Class<?> type, String... names) {
        try {
            for (Method method : type.getDeclaredMethods()) {
                for (String name : names) {
                    if (name.equals(method.getName())) module.deoptimize(method);
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot deoptimize open-with callers", error);
        }
    }

    private static String componentOf(Object target, Field field) {
        if (target == null) return "";
        try {
            Object info = field.get(target);
            if (!(info instanceof ResolveInfo)) return "";
            ActivityInfo activity = ((ResolveInfo) info).activityInfo;
            return activity == null ? "" : activity.packageName + "/" + activity.name;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }
}
