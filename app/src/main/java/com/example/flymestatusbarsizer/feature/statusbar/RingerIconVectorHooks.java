package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Icon;
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

/** Replaces silent, vibrate and Bluetooth status icons with vector drawables. */
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
                if (!"mute".equals(slot) && !"volume".equals(slot) && !"bluetooth".equals(slot)) {
                    return chain.proceed();
                }
                String drawableName;
                if ("mute".equals(slot)) {
                    drawableName = "ic_legacy_speaker_mute";
                } else if ("volume".equals(slot)) {
                    drawableName = "ic_legacy_volume_ringer_vibrate";
                } else {
                    drawableName = null;
                }
                ICON_VIEWS.putIfAbsent(iconView, null);

                ModuleConfig config = ModuleConfig.load(iconView.getContext());
                if (!config.enabled) {
                    return chain.proceed();
                }

                Context context = (Context) chain.getArg(0);
                if ("bluetooth".equals(slot)) {
                    if (!config.statusBarBluetoothVectorIconsEnabled) return chain.proceed();
                    Object statusBarIcon = chain.getArg(2);
                    Icon sourceIcon = (Icon) ReflectUtils.getField(statusBarIcon, "icon");
                    if (sourceIcon == null || sourceIcon.getType() != Icon.TYPE_RESOURCE) {
                        return chain.proceed();
                    }
                    BluetoothIconState state;
                    try {
                        String resourceName = context.getResources().getResourceEntryName(sourceIcon.getResId());
                        state = BluetoothIconState.fromResourceName(resourceName);
                    } catch (Resources.NotFoundException invalidResource) {
                        return chain.proceed();
                    }
                    if (state == null) return chain.proceed();
                    return compactDrawable(config.bluetoothIcons.newDrawable(context, state),
                            context, config.bluetoothIcons.iconDrawingBounds());
                }
                if (!config.statusBarRingerVectorIconsEnabled) {
                    return chain.proceed();
                }
                RingerVectorXml custom = "mute".equals(slot) ? config.silentIconVector : config.vibrateIconVector;
                if (custom != null) {
                    try {
                        return compactDrawable(custom.newDrawable(context), context,
                                custom.iconDrawingBounds());
                    } catch (IllegalArgumentException invalid) {
                        Log.w(TAG, "Ignoring unsupported custom ringer icon", invalid);
                    }
                }
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
                RingerVectorDrawable drawable = new RingerVectorDrawable(context.getDrawable(drawableId), context,
                        "mute".equals(slot));
                RectF bounds = new RectF(0, 0, 1, 1);
                IconMetrics.VisualCanvas visual = IconMetrics.resolveCenteredVisualCanvas(bounds,
                        drawable.pathBounds.width() / drawable.pathBounds.height(), null);
                return compactDrawable(drawable, context, visual.rect);
            });
            module.intercept(type.getDeclaredMethod("onMeasure", int.class, int.class), chain -> {
                ImageView view = (ImageView) chain.getThisObject();
                Drawable drawable = view.getDrawable();
                if (drawable instanceof AnimationDrawable animation && animation.getNumberOfFrames() > 0) {
                    drawable = animation.getFrame(0);
                }
                int heightSpec = (Integer) chain.getArg(1);
                if (!(drawable instanceof CompactIconDrawable)
                        || View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.EXACTLY) {
                    return chain.proceed();
                }
                // The system slot can be shorter than the shared module icon box.
                ModuleConfig config = ModuleConfig.load(view.getContext());
                int height = IconMetrics.resolveSignalBoxHeight(view.getContext(),
                        config.statusBarIconScalePercent / 100f);
                heightSpec = View.MeasureSpec.makeMeasureSpec(
                        height + view.getPaddingTop() + view.getPaddingBottom(),
                        View.MeasureSpec.EXACTLY);
                int width = (int) Math.ceil(height * (double) drawable.getIntrinsicWidth()
                        / drawable.getIntrinsicHeight()) + view.getPaddingLeft() + view.getPaddingRight();
                int widthSpec = (Integer) chain.getArg(0);
                if (View.MeasureSpec.getMode(widthSpec) != View.MeasureSpec.UNSPECIFIED) {
                    width = Math.min(width, View.MeasureSpec.getSize(widthSpec));
                }
                return chain.proceed(new Object[]{
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightSpec});
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
                if (tint != null) {
                    applyTint(view, tint.getColorForState(view.getDrawableState(), tint.getDefaultColor()));
                }
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
        if (drawable instanceof RingerVectorXml.VectorIcon
                || drawable instanceof RingerVectorXml.NativeIcon
                || drawable instanceof CompactIconDrawable
                || drawable instanceof AnimationDrawable) {
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
                if ("mute".equals(slot) || "volume".equals(slot) || "bluetooth".equals(slot)) {
                    FlymeStatusBarSizer.invokeNoArgCompat(view, "updateDrawable");
                }
            }
        });
    }

    public static Drawable builtInPreview(Context context, boolean silent) {
        return new RingerVectorDrawable(context.getDrawable(silent ? R.drawable.ringer_builtin_silent
                : R.drawable.ringer_builtin_vibrate), context, silent);
    }

    private static Drawable compactDrawable(Drawable source, Context context, RectF drawingBounds) {
        if (drawingBounds == null) return source;
        if (source instanceof AnimationDrawable animation) {
            AnimationDrawable compact = new AnimationDrawable();
            compact.setOneShot(animation.isOneShot());
            for (int i = 0; i < animation.getNumberOfFrames(); i++) {
                compact.addFrame(compactDrawable(animation.getFrame(i), context, drawingBounds),
                        animation.getDuration(i));
            }
            return compact;
        }
        int width = source.getIntrinsicWidth();
        int height = source.getIntrinsicHeight();
        float padding = 2f * context.getResources().getDisplayMetrics().density;
        int compactWidth = (int) Math.ceil(drawingBounds.width() * width + 2f * padding);
        if (compactWidth >= width) return source;
        return new CompactIconDrawable(source, compactWidth, width, height,
                drawingBounds.left * width - padding);
    }

    private static final class CompactIconDrawable extends DrawableWrapper {
        private final int width, sourceWidth, sourceHeight;
        private final float sourceLeft;

        CompactIconDrawable(Drawable source, int width, int sourceWidth, int sourceHeight,
                            float sourceLeft) {
            super(source.mutate());
            this.width = width;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.sourceLeft = sourceLeft;
            getDrawable().setBounds(0, 0, sourceWidth, sourceHeight);
        }

        @Override protected void onBoundsChange(Rect bounds) {
            getDrawable().setBounds(0, 0, sourceWidth, sourceHeight);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float scale = bounds.height() / (float) sourceHeight;
            int save = canvas.save();
            canvas.translate(bounds.left - sourceLeft * scale, bounds.top);
            canvas.scale(scale, scale);
            getDrawable().draw(canvas);
            canvas.restoreToCount(save);
        }

        @Override public int getIntrinsicWidth() { return width; }
        @Override public int getIntrinsicHeight() { return sourceHeight; }
        @Override public ConstantState getConstantState() { return null; }
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
