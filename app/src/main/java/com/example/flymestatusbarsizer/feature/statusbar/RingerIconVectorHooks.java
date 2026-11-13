package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Loads SystemUI's vector alternatives for the native silent and vibrate status icons. */
public final class RingerIconVectorHooks {
    private static final String TAG = "RingerIconVector";
    private static final Map<View, Boolean> ICON_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static int silentDrawableId;
    private static int vibrateDrawableId;

    private RingerIconVectorHooks() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.statusbar.StatusBarIconView", false, loader);
            Class<?> statusBarIconType = Class.forName(
                    "com.android.internal.statusbar.StatusBarIcon", false, loader);
            Method loadDrawable = type.getDeclaredMethod("loadDrawable", Context.class,
                    statusBarIconType);
            module.intercept(loadDrawable, chain -> {
                Object receiver = chain.getThisObject();
                if (receiver instanceof View) {
                    ICON_VIEWS.put((View) receiver, Boolean.TRUE);
                }
                if (!(receiver instanceof View) || !(chain.getArg(0) instanceof Context)) {
                    return chain.proceed();
                }

                View iconView = (View) receiver;
                if (ReflectUtils.getField(iconView, "mNotification") != null) {
                    return chain.proceed();
                }
                String slot = (String) ReflectUtils.getField(iconView, "mSlot");
                String drawableName;
                if ("mute".equals(slot)) {
                    drawableName = "ic_legacy_speaker_mute";
                } else if ("volume".equals(slot)) {
                    drawableName = "ic_legacy_volume_ringer_vibrate";
                } else {
                    return chain.proceed();
                }

                ModuleConfig config = ModuleConfig.load(iconView.getContext());
                if (!config.enabled || !config.statusBarRingerVectorIconsEnabled) {
                    return chain.proceed();
                }

                Context context = (Context) chain.getArg(0);
                int drawableId;
                if ("mute".equals(slot)) {
                    if (silentDrawableId == 0) {
                        silentDrawableId = context.getResources().getIdentifier(
                                drawableName, "drawable", "com.android.systemui");
                    }
                    drawableId = silentDrawableId;
                } else {
                    if (vibrateDrawableId == 0) {
                        vibrateDrawableId = context.getResources().getIdentifier(
                                drawableName, "drawable", "com.android.systemui");
                    }
                    drawableId = vibrateDrawableId;
                }
                return drawableId == 0 ? chain.proceed() : context.getDrawable(drawableId);
            });
        } catch (Throwable error) {
            Log.w(TAG, "Ringer vector icons unavailable", error);
        }
    }

    public static void refresh() {
        FlymeStatusBarSizer.postToMainHandler(() -> {
            ArrayList<View> iconViews;
            synchronized (ICON_VIEWS) {
                iconViews = new ArrayList<>(ICON_VIEWS.keySet());
            }
            for (View view : iconViews) {
                if (view == null || !view.isAttachedToWindow()) {
                    continue;
                }
                String slot = (String) ReflectUtils.getField(view, "mSlot");
                if ("mute".equals(slot) || "volume".equals(slot)) {
                    FlymeStatusBarSizer.invokeNoArgCompat(view, "updateDrawable");
                }
            }
        });
    }
}
