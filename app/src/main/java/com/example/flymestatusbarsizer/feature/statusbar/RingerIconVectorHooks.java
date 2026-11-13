package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.R;
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
    private static final Map<View, Integer> ICON_VIEWS =
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
                ICON_VIEWS.putIfAbsent(iconView, null);

                ModuleConfig config = ModuleConfig.load(iconView.getContext());
                if (!config.enabled || !config.statusBarRingerVectorIconsEnabled) {
                    return chain.proceed();
                }

                Context context = (Context) chain.getArg(0);
                RingerVectorXml custom = "mute".equals(slot) ? config.silentIconVector : config.vibrateIconVector;
                if (custom != null) return custom.newDrawable(context);
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
            module.intercept(type.getDeclaredMethod("setStaticDrawableColor", int.class), chain -> {
                Object result = chain.proceed();
                ImageView view = (ImageView) chain.getThisObject();
                if (ICON_VIEWS.containsKey(view)) applyTint(view, (Integer) chain.getArg(0));
                return result;
            });
            module.intercept(type.getDeclaredMethod("onDarkChanged", ArrayList.class,
                    float.class, int.class), chain -> {
                Object result = chain.proceed();
                ImageView view = (ImageView) chain.getThisObject();
                if (!ICON_VIEWS.containsKey(view)) return result;
                ColorStateList tint = view.getImageTintList();
                applyTint(view, tint.getColorForState(view.getDrawableState(), tint.getDefaultColor()));
                return result;
            });
            module.intercept(type.getDeclaredMethod("updateDrawable", boolean.class), chain -> {
                Object result = chain.proceed();
                ImageView view = (ImageView) chain.getThisObject();
                Integer tint = ICON_VIEWS.get(view);
                if (tint != null) applyTint(view, tint);
                return result;
            });
            // Panel callers can inline the interface's one-color forwarding method.
            module.deoptimize(Class.forName("com.android.systemui.statusbar.StatusIconDisplayable",
                    false, loader).getDeclaredMethod("setStaticDrawableColor", int.class, int.class));
            Class<?> manager = Class.forName(
                    "com.android.systemui.statusbar.phone.ui.TintedIconManager", false, loader);
            for (Method method : manager.getDeclaredMethods()) {
                if ("setTint".equals(method.getName()) || "onIconAdded".equals(method.getName())) {
                    module.deoptimize(method);
                }
            }
            module.deoptimize(type.getDeclaredMethod("updateDrawable"));
        } catch (Throwable error) {
            Log.w(TAG, "Ringer vector icons unavailable", error);
        }
    }

    private static void applyTint(ImageView view, int color) {
        ICON_VIEWS.put(view, color);
        Drawable drawable = view.getDrawable();
        if (drawable instanceof RingerVectorXml.VectorIcon) {
            // A previous native color filter would take precedence over the new tint.
            drawable.clearColorFilter();
            drawable.setTintList(ColorStateList.valueOf(color));
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

    public static Drawable builtInPreview(Context context, boolean silent) {
        return new RingerVectorDrawable(context.getDrawable(silent ? R.drawable.ringer_builtin_silent
                : R.drawable.ringer_builtin_vibrate), context, silent);
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
