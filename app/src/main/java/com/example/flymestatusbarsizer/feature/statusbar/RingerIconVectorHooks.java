package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
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
            Method getIcon = type.getDeclaredMethod("getIcon", Context.class, Context.class,
                    statusBarIconType);
            module.intercept(getIcon, chain -> {
                View iconView = (View) chain.getThisObject();
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
                ICON_VIEWS.put(iconView, Boolean.TRUE);

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
                if (drawableId == 0) {
                    return chain.proceed();
                }
                return new RingerVectorDrawable(context.getDrawable(drawableId), context,
                        "mute".equals(slot));
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

    private static final class RingerVectorDrawable extends DrawableWrapper {
        private final int intrinsicSize;
        private final RectF pathBounds;
        private final IconMetrics.VisualCanvas visualCanvas = new IconMetrics.VisualCanvas();

        RingerVectorDrawable(Drawable source, Context context, boolean silent) {
            super(source.mutate());
            // The view applies the configured icon scale; keep the intrinsic box unscaled.
            intrinsicSize = IconMetrics.resolveSignalBoxHeight(context, 1f);
            // Visible path extents in the two SystemUI vectors' 24 x 24 viewports.
            pathBounds = silent ? new RectF(1.4f, 2.8f, 21.2f, 22.6f)
                    : new RectF(1f, 4f, 23f, 20f);
        }

        @Override
        public void draw(Canvas canvas) {
            IconMetrics.resolveCenteredVisualCanvas(getBounds(),
                    pathBounds.width() / pathBounds.height(), visualCanvas);
            if (visualCanvas.isEmpty()) {
                return;
            }
            float scale = visualCanvas.rect.height() / pathBounds.height();
            int saveCount = canvas.save();
            canvas.translate(visualCanvas.rect.left, visualCanvas.rect.top);
            canvas.scale(scale, scale);
            canvas.translate(-pathBounds.left, -pathBounds.top);
            getDrawable().setBounds(0, 0, 24, 24);
            super.draw(canvas);
            canvas.restoreToCount(saveCount);
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicSize;
        }

    }
}
