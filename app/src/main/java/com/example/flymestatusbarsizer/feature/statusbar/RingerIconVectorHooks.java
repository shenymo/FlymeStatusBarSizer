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

/** Replaces silent, vibrate, alarm and Bluetooth status icons with vector drawables. */
public final class RingerIconVectorHooks {
    private static final String TAG = "RingerIconVector";
    private static final Map<View, Integer> ICON_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static int silentDrawableId;
    private static int vibrateDrawableId;
    private static int alarmDrawableId;
    private static int alarmDimDrawableId;

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
                if (!"mute".equals(slot) && !"volume".equals(slot) && !"alarm_clock".equals(slot)
                        && !"bluetooth".equals(slot)) {
                    return chain.proceed();
                }
                RingerIconLibrary.Target target = switch (slot) {
                    case "mute" -> RingerIconLibrary.Target.SILENT;
                    case "volume" -> RingerIconLibrary.Target.VIBRATE;
                    case "alarm_clock" -> RingerIconLibrary.Target.ALARM;
                    default -> null;
                };
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
                RingerVectorXml custom = switch (target) {
                    case SILENT -> config.silentIconVector;
                    case VIBRATE -> config.vibrateIconVector;
                    case ALARM -> config.alarmIconVector;
                };
                boolean alarmDim = target == RingerIconLibrary.Target.ALARM
                        && isAlarmDim(context, chain.getArg(2));
                if (custom != null) {
                    try {
                        Drawable drawable = custom.newDrawable(context);
                        if (alarmDim) drawable.setAlpha(77);
                        return compactDrawable(drawable, context, custom.iconDrawingBounds());
                    } catch (IllegalArgumentException invalid) {
                        Log.w(TAG, "Ignoring unsupported custom ringer icon", invalid);
                    }
                }
                String drawableName = switch (target) {
                    case SILENT -> "ic_legacy_speaker_mute";
                    case VIBRATE -> "ic_legacy_volume_ringer_vibrate";
                    case ALARM -> "ic_alarm";
                };
                int drawableId;
                if (target == RingerIconLibrary.Target.SILENT) {
                    if (silentDrawableId == 0) silentDrawableId = context.getResources().getIdentifier(
                            drawableName, "drawable", "com.android.systemui");
                    drawableId = silentDrawableId;
                } else if (target == RingerIconLibrary.Target.VIBRATE) {
                    if (vibrateDrawableId == 0) vibrateDrawableId = context.getResources().getIdentifier(
                            drawableName, "drawable", "com.android.systemui");
                    drawableId = vibrateDrawableId;
                } else {
                    if (alarmDrawableId == 0) alarmDrawableId = context.getResources().getIdentifier(
                            drawableName, "drawable", "com.android.systemui");
                    drawableId = alarmDrawableId;
                }
                if (drawableId == 0) {
                    return chain.proceed();
                }
                RingerVectorDrawable drawable = new RingerVectorDrawable(context.getDrawable(drawableId), context,
                        target);
                if (alarmDim) drawable.setAlpha(77);
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
                if ("mute".equals(slot) || "volume".equals(slot) || "alarm_clock".equals(slot)
                        || "bluetooth".equals(slot)) {
                    FlymeStatusBarSizer.invokeNoArgCompat(view, "updateDrawable");
                }
            }
        });
    }

    public static Drawable builtInPreview(Context context, boolean silent) {
        return builtInPreview(context, silent ? RingerIconLibrary.Target.SILENT
                : RingerIconLibrary.Target.VIBRATE);
    }

    public static Drawable builtInPreview(Context context, RingerIconLibrary.Target target) {
        int drawableId = switch (target) {
            case SILENT -> R.drawable.ringer_builtin_silent;
            case VIBRATE -> R.drawable.ringer_builtin_vibrate;
            case ALARM -> R.drawable.ringer_builtin_alarm;
        };
        return new RingerVectorDrawable(context.getDrawable(drawableId), context, target);
    }

    private static boolean isAlarmDim(Context context, Object statusBarIcon) {
        if (alarmDimDrawableId == 0) {
            alarmDimDrawableId = context.getResources().getIdentifier(
                    "stat_sys_alarm_dim", "drawable", "com.android.systemui");
        }
        if (alarmDimDrawableId == 0 || statusBarIcon == null) return false;
        Icon sourceIcon = (Icon) ReflectUtils.getField(statusBarIcon, "icon");
        return sourceIcon != null && sourceIcon.getType() == Icon.TYPE_RESOURCE
                && sourceIcon.getResId() == alarmDimDrawableId;
    }

    private static Drawable compactDrawable(Drawable source, Context context, RectF drawingBounds) {
        if (drawingBounds == null) return source;
        if (drawingBounds.left == 0f && drawingBounds.top == 0f
                && drawingBounds.right == 1f && drawingBounds.bottom == 1f) {
            return source;
        }
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
        RectF visual = IconMetrics.resolveCenteredVisualCanvas(new RectF(0, 0, width, height),
                drawingBounds.width() / drawingBounds.height(), null).rect;
        int compactWidth = (int) Math.ceil(visual.width() + 2f * padding);
        if (compactWidth >= width) return source;
        return new CompactIconDrawable(source, compactWidth, width, height,
                visual.left - padding);
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

        RingerVectorDrawable(Drawable source, Context context, RingerIconLibrary.Target target) {
            super(source.mutate());
            // The view applies the configured icon scale; keep the intrinsic box unscaled.
            intrinsicSize = IconMetrics.resolveSignalBoxHeight(context, 1f);
            // Visible path extents in the built-in SystemUI vectors' 24 x 24 viewports.
            pathBounds = switch (target) {
                case SILENT -> new RectF(1.4f, 2.8f, 21.2f, 22.6f);
                case VIBRATE -> new RectF(1f, 4f, 23f, 20f);
                case ALARM -> new RectF(2f, 1.8f, 22f, 22f);
            };
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
