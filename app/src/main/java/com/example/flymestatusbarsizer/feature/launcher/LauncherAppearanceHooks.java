package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Method;

public final class LauncherAppearanceHooks {
    private static final String THEME_ICON_UTILS_CLASS =
            "com.meizu.flyme.launcher.utils.ThemeIconUtils";
    private static final String PAGE_INDICATOR_CLASS =
            "com.meizu.flyme.launcher.view.MzIconPageIndicator";
    private static final String COMMON_UTILS_CLASS =
            "com.meizu.flyme.launcher.utils.MzCommonUtils";

    private LauncherAppearanceHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookFolderBackgroundColor(module, loader);
        hookAicyEntryEnabled(module, loader);
        hookAicyEntryClick(module, loader);
    }

    private static void hookFolderBackgroundColor(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(THEME_ICON_UTILS_CLASS, false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"getFolderBgColor".equals(method.getName())
                        || method.getReturnType() != int.class
                        || method.getParameterTypes().length == 0) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Context context = chain.getArg(0) instanceof Context
                            ? (Context) chain.getArg(0)
                            : null;
                    FlymeStatusBarSizer.LauncherAppearanceConfigSnapshot config =
                            FlymeStatusBarSizer.loadLauncherAppearanceConfig(context);
                    if (!config.enabled || !config.launcherFolderBgColorEnabled) {
                        return chain.proceed();
                    }
                    return config.launcherFolderBgColor;
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook launcher folder background color",
                    t);
        }
    }

    private static void hookAicyEntryEnabled(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGE_INDICATOR_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("searchEnabled");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                FlymeStatusBarSizer.LauncherAppearanceConfigSnapshot config =
                        FlymeStatusBarSizer.loadLauncherAppearanceConfig(null);
                if (!config.launcherAicyEntryEnabled) {
                    return chain.proceed();
                }
                Object target = chain.getThisObject();
                if (target instanceof View) {
                    applyAicyText((View) target, config.launcherAicyEntryText);
                }
                return true;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning("Failed to hook launcher Aicy entry state", t);
        }
    }

    private static void hookAicyEntryClick(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(COMMON_UTILS_CLASS, false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"startAISearchActivity".equals(method.getName())
                        || method.getParameterTypes().length != 1) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object launcher = chain.getArg(0);
                    FlymeStatusBarSizer.LauncherAppearanceConfigSnapshot config =
                            FlymeStatusBarSizer.loadLauncherAppearanceConfig(
                                    launcher instanceof Context ? (Context) launcher : null);
                    if (!config.launcherAicyEntryEnabled
                            || TextUtils.isEmpty(config.launcherAicyEntryTarget)
                            || !(launcher instanceof Context)) {
                        return chain.proceed();
                    }
                    try {
                        launchTarget((Context) launcher, config.launcherAicyEntryTarget);
                        return null;
                    } catch (Throwable t) {
                        FlymeStatusBarSizer.logLauncherWarning(
                                "Failed to launch custom Aicy entry target", t);
                        return chain.proceed();
                    }
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning("Failed to hook launcher Aicy entry click", t);
        }
    }

    private static void applyAicyText(View root, String text) {
        int id = root.getResources().getIdentifier(
                "ai_search_text", "id", root.getContext().getPackageName());
        View view = id == 0 ? null : root.findViewById(id);
        if (view instanceof TextView) {
            ((TextView) view).setText(text == null ? "" : text);
        }
    }

    private static Intent buildTargetIntent(String value) throws Exception {
        String target = value.trim();
        Intent intent;
        if (target.startsWith("intent:") || target.contains("#Intent;")) {
            intent = Intent.parseUri(target, Intent.URI_INTENT_SCHEME);
        } else {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static void launchTarget(Context context, String value) throws Exception {
        String target = value.trim();
        if (!target.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            context.startActivity(buildTargetIntent(target));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.getPackageManager()
                    .getLaunchIntentSenderForPackage(target)
                    .sendIntent(context, 0, null, null, null);
            return;
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(target);
        if (intent == null) {
            throw new IllegalArgumentException("No launchable activity for " + target);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
