package com.example.flymestatusbarsizer.feature.assistant;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.net.wifi.WifiManager;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.Display;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.SimpleColorFilter;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.util.HapticFeedbackUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Compact SystemUI-owned quick tools panel opened by a side gesture. */
final class QuickToolsPanel {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
    private static final String TITLE = "FlymeStatusBarSizer:QuickTools";
    private static volatile Object qsHost;
    private static final Map<String, Object> CREATED_TILES = new HashMap<>();
    private static final String[] TOOL_DESCRIPTIONS = {
            "Wi-Fi", "铃声模式", "蓝牙", "自动旋转",
            "截图", "锁屏", "手电筒", "屏幕录制"
    };
    private static QuickToolsPanel instance;

    private final Context context;
    private final WindowManager windowManager;
    private final FrameLayout root;
    private final PanelView view;
    private final AssistantPanelBlur panelBlur;
    private boolean attached;
    private boolean attempted;

    private QuickToolsPanel(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        view = new PanelView(context);
        panelBlur = new AssistantPanelBlur(view,
                context.getResources().getDisplayMetrics().density);
        root = new FrameLayout(context) {
            @Override
            protected void onConfigurationChanged(Configuration configuration) {
                super.onConfigurationChanged(configuration);
                view.updateDarkMode(configuration);
            }
        };
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE
                    || event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                hide();
            }
            return true;
        });
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelWidth(), panelHeight(),
                Gravity.TOP | Gravity.LEFT);
        panelParams.leftMargin = dp(8);
        panelParams.rightMargin = dp(8);
        root.addView(view, panelParams);
    }

    static void install(com.example.flymestatusbarsizer.FlymeStatusBarSizer module,
            ClassLoader loader) throws ReflectiveOperationException {
        Class<?> host = Class.forName("com.android.systemui.qs.QSTileHost", false, loader);
        for (java.lang.reflect.Constructor<?> constructor : host.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            module.intercept(constructor, chain -> {
                Object result = chain.proceed();
                qsHost = chain.getThisObject();
                synchronized (CREATED_TILES) { CREATED_TILES.clear(); }
                return result;
            });
        }
    }

    static synchronized SideGestureActions.Action action(Context context) {
        if (instance == null) instance = new QuickToolsPanel(context);
        return new Action(instance);
    }

    private void show(boolean fromLeft, float anchorY) {
        view.fromLeft = fromLeft;
        view.anchorY = anchorY;
        view.updateDarkMode(context.getResources().getConfiguration());
        view.refreshState();
        if (attached) {
            view.startBrightnessRefresh();
            view.alignToGestureAnchor();
            view.animateIn();
        } else if (!attempted) {
            attempted = true;
            attach();
        }
        view.invalidate();
    }

    private void hide() {
        if (attached) view.animateClose();
    }

    private void attach() {
        if (windowManager == null) return;
        Throwable last = null;
        for (int type : WINDOW_TYPES) {
            WindowManager.LayoutParams params = buildLayoutParams(type);
            try {
                windowManager.addView(root, params);
                attached = true;
                panelBlur.start();
                view.startBrightnessRefresh();
                root.post(() -> {
                    view.alignToGestureAnchor();
                    view.animateIn();
                });
                return;
            } catch (Throwable error) {
                last = error;
                try { windowManager.removeViewImmediate(root); } catch (Throwable ignored) { }
            }
        }
        AssistantHooks.warn("Cannot attach quick tools panel", last);
    }

    private WindowManager.LayoutParams buildLayoutParams(int type) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.TRANSLUCENT);
        params.token = new Binder();
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.setTitle(TITLE);
        params.packageName = context.getPackageName();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        applyTrustedOverlay(params);
        return params;
    }

    private int panelWidth() {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        float density = context.getResources().getDisplayMetrics().density;
        return Math.min(Math.round(193f * density),
                Math.max(Math.round(147f * density), screenWidth - Math.round(22f * density)));
    }

    private int panelHeight() {
        return dp(254);
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void applyTrustedOverlay(WindowManager.LayoutParams params) {
        try {
            Method method = WindowManager.LayoutParams.class.getMethod("setTrustedOverlay");
            method.invoke(params);
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field field = WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
            field.setAccessible(true);
            field.setInt(params, field.getInt(params) | 0x01000000);
        } catch (Throwable ignored) {
        }
    }

    private static final class Action implements SideGestureActions.Action {
        private final QuickToolsPanel panel;

        Action(QuickToolsPanel panel) {
            this.panel = panel;
        }

        @Override public boolean isReady() {
            return true;
        }

        @Override public void execute(boolean fromLeft, Runnable onSuccess) {
            execute(fromLeft, 0f, onSuccess);
        }

        @Override public void execute(boolean fromLeft, float anchorY, Runnable onSuccess) {
            panel.show(fromLeft, anchorY);
            if (onSuccess != null) onSuccess.run();
        }
    }

    private final class PanelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final RectF brightnessTrack = new RectF();
        private final RectF brightnessIcon = new RectF();
        private final RectF volumeCard = new RectF();
        private final RectF volumeTrack = new RectF();
        private final RectF[] mediaButtons = new RectF[5];
        private final RectF[] buttons = new RectF[TOOL_DESCRIPTIONS.length];
        private final Drawable[] buttonIcons = new Drawable[TOOL_DESCRIPTIONS.length];
        private final Drawable ringerNormalIcon;
        private final Drawable ringerVibrateIcon;
        private final Drawable ringerSilentIcon;
        private final Drawable volumeMediaDrawable;
        private final Drawable volumeMuteDrawable;
        private final LottieDrawable brightnessAnimation = new LottieDrawable();
        private final LottieDrawable volumeAnimation = new LottieDrawable();
        private final float density;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Runnable brightnessRefresh = new Runnable() {
            @Override public void run() {
                if (!attached || closing) return;
                if (activeSlider == 0) {
                    refreshBrightnessState();
                }
                if (activeSlider != 2) refreshVolumeState();
                invalidate();
                main.postDelayed(this, 120L);
            }
        };
        private boolean fromLeft = true;
        private float anchorY;
        private boolean darkMode;
        private boolean closing;
        private int activeSlider;
        private int brightness;
        private int brightnessMin = 1;
        private int brightnessMax = 255;
        private boolean brightnessAuto;
        private int volume;
        private int volumeMax;
        private int brightnessAnimationTint;
        private int volumeAnimationTint;
        private boolean wifi;
        private boolean bluetooth;
        private boolean rotationAuto;
        private boolean flashlight;
        private boolean recording;
        private boolean brightnessAnimationReady;
        private boolean volumeAnimationReady;
        private int ringerMode = AudioManager.RINGER_MODE_NORMAL;

        PanelView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            Context resourceContext = moduleResourceContext(context);
            buttonIcons[0] = loadDrawable(resourceContext, R.drawable.quick_tool_wifi);
            buttonIcons[2] = loadDrawable(resourceContext, R.drawable.quick_tool_bluetooth);
            buttonIcons[3] = loadDrawable(resourceContext, R.drawable.quick_tool_rotate);
            buttonIcons[4] = loadDrawable(resourceContext, R.drawable.quick_tool_screenshot);
            buttonIcons[5] = loadDrawable(resourceContext, R.drawable.quick_tool_lock);
            buttonIcons[6] = loadDrawable(resourceContext, R.drawable.quick_tool_flashlight);
            buttonIcons[7] = loadDrawable(resourceContext, R.drawable.quick_tool_screen_record);
            ringerNormalIcon = loadDrawable(resourceContext, R.drawable.quick_tool_bell);
            ringerVibrateIcon = loadDrawable(resourceContext, R.drawable.quick_tool_vibrate);
            ringerSilentIcon = loadDrawable(resourceContext, R.drawable.quick_tool_mute);
            volumeMediaDrawable = loadDrawable(resourceContext, R.drawable.ic_qs_volume_media);
            volumeMuteDrawable = loadDrawable(resourceContext, R.drawable.ic_qs_volume_media_mute);
            try {
                LottieComposition composition = LottieCompositionFactory
                        .fromAssetSync(resourceContext, "brightness.json").getValue();
                if (composition != null) {
                    brightnessAnimation.setComposition(composition);
                    brightnessAnimationReady = true;
                }
            } catch (Throwable ignored) {
            }
            try {
                LottieComposition composition = LottieCompositionFactory
                        .fromAssetSync(resourceContext, "volume.json").getValue();
                if (composition != null) {
                    volumeAnimation.setComposition(composition);
                    volumeAnimationReady = true;
                }
            } catch (Throwable ignored) {
            }
            setFocusable(false);
            darkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            setContentDescription("快捷工具，亮度、音量、上一曲、播放暂停、下一曲、增加音量、减小音量、Wi-Fi、铃声模式、蓝牙、旋转、截图、锁屏、手电筒、录屏");
            setElevation(dp(7));
            updateBackground();
            for (int i = 0; i < buttons.length; i++) buttons[i] = new RectF();
            for (int i = 0; i < mediaButtons.length; i++) mediaButtons[i] = new RectF();
        }

        private Context moduleResourceContext(Context context) {
            try {
                return context.createPackageContext(BuildConfig.APPLICATION_ID,
                        Context.CONTEXT_IGNORE_SECURITY);
            } catch (Throwable ignored) {
                return context;
            }
        }

        private Drawable loadDrawable(Context context, int resourceId) {
            try {
                return context.getDrawable(resourceId).mutate();
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override protected void onConfigurationChanged(Configuration configuration) {
            super.onConfigurationChanged(configuration);
            updateDarkMode(configuration);
        }

        void updateDarkMode(Configuration configuration) {
            boolean nextDarkMode = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            if (darkMode == nextDarkMode) return;
            darkMode = nextDarkMode;
            updateBackground();
            invalidate();
        }

        private void updateBackground() {
            GradientDrawable background = new GradientDrawable();
            int alpha = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 0x98 : 0xf5;
            background.setColor(darkMode ? (0x2a2c2f | (alpha << 24))
                    : (0xffffff | (alpha << 24)));
            background.setCornerRadius(dp(12));
            background.setStroke(Math.round(dp(1)), darkMode ? 0x38ffffff : 0x24000000);
            setBackground(background);
        }

        void alignToGestureAnchor() {
            if (!attached || root.getHeight() <= 0 || getHeight() <= 0) return;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
            params.gravity = Gravity.TOP | (fromLeft ? Gravity.LEFT : Gravity.RIGHT);
            params.leftMargin = Math.round(dp(8));
            params.rightMargin = Math.round(dp(8));
            int maxTop = Math.max(statusBarHeight(), root.getHeight() - navigationBarHeight() - getHeight());
            int desiredTop = anchorY > 0f ? Math.round(anchorY - getHeight() / 2f)
                    : (root.getHeight() - getHeight()) / 2;
            params.topMargin = Math.max(statusBarHeight() + Math.round(dp(8)),
                    Math.min(maxTop - Math.round(dp(8)), desiredTop));
            setLayoutParams(params);
        }

        void animateIn() {
            closing = false;
            animate().cancel();
            setPivotX(fromLeft ? 0f : getWidth());
            setPivotY(Math.max(0f, Math.min(getHeight(), anchorY - getTop())));
            setScaleX(0.04f);
            setScaleY(0.08f);
            setAlpha(0.15f);
            setTranslationX(fromLeft ? -dp(5) : dp(5));
            animate().scaleX(1f).scaleY(1f).alpha(1f).translationX(0f)
                    .setDuration(360L).setInterpolator(new OvershootInterpolator(0.55f)).start();
        }

        void animateClose() {
            if (!attached || closing) return;
            closing = true;
            stopBrightnessRefresh();
            animate().cancel();
            setPivotX(fromLeft ? 0f : getWidth());
            setPivotY(Math.max(0f, Math.min(getHeight(), anchorY - getTop())));
            animate().scaleX(0.04f).scaleY(0.08f).alpha(0f)
                    .translationX(fromLeft ? -dp(5) : dp(5)).setDuration(230L)
                    .setInterpolator(new AccelerateInterpolator(1.35f))
                    .withEndAction(() -> {
                        panelBlur.stop();
                        if (attached) {
                            try { windowManager.removeViewImmediate(root); }
                            catch (Throwable ignored) { }
                            attached = false;
                        }
                        attempted = false;
                        closing = false;
                    }).start();
        }

        void refreshState() {
            refreshBrightnessState();
            AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (audio != null) {
                ringerMode = audio.getRingerMode();
            }
            refreshVolumeState();
            WifiManager wifiManager = (WifiManager) getContext().getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            wifi = wifiManager != null && wifiManager.isWifiEnabled();
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            bluetooth = adapter != null && adapter.isEnabled();
            try {
                rotationAuto = systemInt(Settings.System.ACCELEROMETER_ROTATION, 1) != 0;
            } catch (Throwable ignored) { rotationAuto = true; }
            flashlight = flashlightState();
            recording = screenRecordState();
        }

        private void refreshVolumeState() {
            AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            volumeMax = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        }

        void startBrightnessRefresh() {
            main.removeCallbacks(brightnessRefresh);
            main.post(brightnessRefresh);
        }

        void stopBrightnessRefresh() {
            main.removeCallbacks(brightnessRefresh);
        }

        private void refreshBrightnessState() {
            refreshBrightnessRange();
            int settingBrightness;
            try {
                settingBrightness = clampBrightness(systemInt(Settings.System.SCREEN_BRIGHTNESS, 128));
            } catch (Throwable ignored) {
                settingBrightness = clampBrightness(128);
            }
            float displayBrightness = currentDisplayBrightness();
            brightness = displayBrightness >= 0f
                    ? brightnessFromDisplay(displayBrightness) : settingBrightness;
            brightnessAuto = systemInt(Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            setContentDescription("快捷工具，亮度、自动亮度"
                    + (brightnessAuto ? "开启" : "关闭")
                    + "、音量、上一曲、播放暂停、下一曲、增加音量、减小音量"
                    + "、Wi-Fi、铃声模式、蓝牙、旋转、截图、锁屏、手电筒、录屏");
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            bounds.set(0f, 0f, getWidth(), getHeight());

            float sliderLeft = dp(42);
            float sliderRight = getWidth() - dp(17);
            brightnessTrack.set(sliderLeft, dp(12), sliderRight, dp(24));
            volumeTrack.set(sliderLeft, dp(50), sliderRight, dp(62));
            brightnessIcon.set(sliderLeft - dp(32), dp(2), sliderLeft, dp(34));
            volumeCard.set(dp(8), dp(35), getWidth() - dp(8), dp(112));
            drawSlider(canvas, brightnessTrack, brightnessFraction(), 0, brightnessAuto);
            drawVolumeCard(canvas);

            float gridTop = dp(120);
            float gap = dp(4);
            float cellWidth = (getWidth() - dp(12) - gap * 3) / 4f;
            float cellHeight = (getHeight() - gridTop - dp(10) - gap) / 2f;
            for (int i = 0; i < buttons.length; i++) {
                int row = i / 4;
                int column = i % 4;
                float cellLeft = dp(6) + column * (cellWidth + gap);
                float cellTop = gridTop + row * (cellHeight + gap);
                buttons[i].set(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight);
                drawButton(canvas, buttons[i], buttonActive(i), i);
            }
        }

        private void drawVolumeCard(Canvas canvas) {
            paint.setColor(darkMode ? 0xd52f3236 : 0xe9f1f2f3);
            canvas.drawRoundRect(volumeCard, dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(darkMode ? 0x52ffffff : 0x30000000);
            canvas.drawRoundRect(volumeCard, dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.FILL);
            drawSliderIcon(canvas, volumeTrack.left - dp(16), volumeTrack.centerY(), dp(10), 1, false);
            drawProgressTrack(canvas, volumeTrack, volume / (float) Math.max(1, volumeMax));

            float controlLeft = volumeCard.left + dp(8);
            float controlWidth = (volumeCard.width() - dp(16)) / mediaButtons.length;
            for (int i = 0; i < mediaButtons.length; i++) {
                float left = controlLeft + i * controlWidth;
                mediaButtons[i].set(left, dp(72), left + controlWidth, dp(104));
                paint.setColor(darkMode ? 0xffe5e8ea : 0xff42484e);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.8f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                drawMediaIcon(canvas, mediaButtons[i].centerX(), mediaButtons[i].centerY(), dp(7), i);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        private void drawProgressTrack(Canvas canvas, RectF track, float fraction) {
            float clampedFraction = Math.max(0f, Math.min(1f, fraction));
            float radius = track.height() / 2f;
            paint.setColor(darkMode ? 0xffb9bdc1 : 0xffd4d8dc);
            canvas.drawRoundRect(track, radius, radius, paint);
            float progressRight = track.left + clampedFraction * track.width();
            if (progressRight <= track.left) return;
            int saveCount = canvas.save();
            canvas.clipRect(track.left, track.top, progressRight, track.bottom);
            paint.setColor(darkMode ? 0xfff6f7f8 : 0xff5a626a);
            canvas.drawRoundRect(track, radius, radius, paint);
            canvas.restoreToCount(saveCount);
        }

        private void drawMediaIcon(Canvas canvas, float cx, float cy, float size, int index) {
            Path icon = new Path();
            if (index == 0 || index == 2) {
                float direction = index == 0 ? -1f : 1f;
                canvas.drawLine(cx + direction * size * 0.9f, cy - size,
                        cx + direction * size * 0.9f, cy + size, paint);
                icon.moveTo(cx - direction * size * 0.7f, cy - size);
                icon.lineTo(cx + direction * size * 0.55f, cy);
                icon.lineTo(cx - direction * size * 0.7f, cy + size);
                icon.close();
                canvas.drawPath(icon, paint);
            } else if (index == 1) {
                AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
                if (audio != null && audio.isMusicActive()) {
                    canvas.drawLine(cx - size * 0.35f, cy - size, cx - size * 0.35f,
                            cy + size, paint);
                    canvas.drawLine(cx + size * 0.35f, cy - size, cx + size * 0.35f,
                            cy + size, paint);
                } else {
                    icon.moveTo(cx - size * 0.55f, cy - size);
                    icon.lineTo(cx + size * 0.85f, cy);
                    icon.lineTo(cx - size * 0.55f, cy + size);
                    icon.close();
                    canvas.drawPath(icon, paint);
                }
            } else {
                icon.moveTo(cx - size * 0.75f, cy + (index == 3 ? size * 0.45f : -size * 0.45f));
                icon.lineTo(cx, cy + (index == 3 ? -size * 0.45f : size * 0.45f));
                icon.lineTo(cx + size * 0.75f, cy + (index == 3 ? size * 0.45f : -size * 0.45f));
                canvas.drawPath(icon, paint);
            }
        }

        private void drawSlider(Canvas canvas, RectF track, float fraction, int icon) {
            drawSlider(canvas, track, fraction, icon, false);
        }

        private void drawSlider(Canvas canvas, RectF track, float fraction, int icon, boolean iconActive) {
            float iconCenterX = track.left - dp(16);
            int brightnessIconColor = darkMode ? 0xfff6f7f8 : 0xff3f464b;
            if (icon == 0) {
                paint.setColor(brightnessIconColor);
            } else {
                paint.setColor(iconActive
                        ? (darkMode ? 0xff77c6c1 : 0xff287a78)
                        : (darkMode ? 0xffaeb4b9 : 0xff61666e));
            }
            drawSliderIcon(canvas, iconCenterX, track.centerY(), dp(8), icon, iconActive);
            if (icon == 0 && brightnessAuto) {
                paint.setColor(brightnessIconColor);
                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                paint.setTextSize(dp(7));
                canvas.drawText("A", iconCenterX + dp(8), track.centerY() + dp(8), paint);
            }
            drawProgressTrack(canvas, track, fraction);
        }

        private void drawButton(Canvas canvas, RectF bounds, boolean active, int index) {
            paint.setColor(active ? (darkMode ? 0x4277c6c1 : 0x30287a78)
                    : (darkMode ? 0x18ffffff : 0x10000000));
            canvas.drawRoundRect(bounds, dp(8), dp(8), paint);
            paint.setColor(active ? (darkMode ? 0xffa9ded9 : 0xff216967)
                    : (darkMode ? 0xffe2e6e7 : 0xff3f464b));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            drawIcon(canvas, index, bounds.centerX(), bounds.centerY(),
                    Math.min(dp(18), bounds.width() * 0.32f), paint.getColor());
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawSliderIcon(Canvas canvas, float cx, float cy, float size,
                int index, boolean active) {
            if (index == 1) {
                Drawable icon = volume <= 0 ? volumeMuteDrawable
                        : volumeAnimationReady ? volumeAnimation : volumeMediaDrawable;
                int color = darkMode ? 0xfff6f7f8 : 0xff61666e;
                if (icon == volumeAnimation && volumeAnimationTint != color) {
                    volumeAnimation.addValueCallback(new KeyPath("**"),
                            LottieProperty.COLOR_FILTER,
                            new LottieValueCallback<ColorFilter>(new SimpleColorFilter(color)));
                    volumeAnimationTint = color;
                }
                if (icon != null) {
                    if (volumeAnimationReady && icon == volumeAnimation) {
                        int start = Math.round(volumeAnimation.getMinFrame());
                        int end = Math.round(volumeAnimation.getMaxFrame());
                        int frame = start + Math.round((end - start)
                                * Math.max(0f, Math.min(1f, volume / (float) Math.max(1, volumeMax))));
                        volumeAnimation.setFrame(frame);
                    }
                    drawSliderDrawable(canvas, icon, index, cx, cy, dp(16), color);
                } else {
                    drawVolumeLevelIcon(canvas, cx, cy, size, color);
                }
                return;
            }
            if (brightnessAnimationReady) {
                int color = darkMode ? 0xfff6f7f8 : 0xff3f464b;
                if (brightnessAnimationTint != color) {
                    brightnessAnimation.addValueCallback(new KeyPath("**"),
                            LottieProperty.COLOR_FILTER,
                            new LottieValueCallback<ColorFilter>(new SimpleColorFilter(color)));
                    brightnessAnimationTint = color;
                }
                int start = Math.round(brightnessAnimation.getMinFrame());
                int end = Math.round(brightnessAnimation.getMaxFrame());
                int frame = start + Math.round((end - start)
                        * Math.max(0f, Math.min(1f, brightnessFraction())));
                brightnessAnimation.setFrame(frame);
                drawSliderDrawable(canvas, brightnessAnimation, index, cx, cy, dp(16), color);
                return;
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (index == 0) {
                canvas.drawCircle(cx, cy, size * 0.42f, paint);
                if (active) {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, size * 0.25f, paint);
                    paint.setStyle(Paint.Style.STROKE);
                }
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4d;
                    float x1 = cx + (float) Math.cos(angle) * size * 0.68f;
                    float y1 = cy + (float) Math.sin(angle) * size * 0.68f;
                    float x2 = cx + (float) Math.cos(angle) * size * 0.95f;
                    float y2 = cy + (float) Math.sin(angle) * size * 0.95f;
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
            } else {
                Path speaker = new Path();
                speaker.moveTo(cx - size * 0.9f, cy - size * 0.28f);
                speaker.lineTo(cx - size * 0.35f, cy - size * 0.28f);
                speaker.lineTo(cx + size * 0.25f, cy - size * 0.72f);
                speaker.lineTo(cx + size * 0.25f, cy + size * 0.72f);
                speaker.lineTo(cx - size * 0.35f, cy + size * 0.28f);
                speaker.lineTo(cx - size * 0.9f, cy + size * 0.28f);
                speaker.close();
                canvas.drawPath(speaker, paint);
                canvas.drawArc(new RectF(cx - size * 0.12f, cy - size * 0.58f,
                        cx + size * 0.95f, cy + size * 0.58f), -55f, 110f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawVolumeLevelIcon(Canvas canvas, float cx, float cy, float size, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            Path speaker = new Path();
            speaker.moveTo(cx - size * 0.9f, cy - size * 0.28f);
            speaker.lineTo(cx - size * 0.35f, cy - size * 0.28f);
            speaker.lineTo(cx + size * 0.2f, cy - size * 0.72f);
            speaker.lineTo(cx + size * 0.2f, cy + size * 0.72f);
            speaker.lineTo(cx - size * 0.35f, cy + size * 0.28f);
            speaker.lineTo(cx - size * 0.9f, cy + size * 0.28f);
            speaker.close();
            canvas.drawPath(speaker, paint);
            if (volume <= 0) {
                canvas.drawLine(cx + size * 0.4f, cy - size * 0.48f,
                        cx + size * 0.95f, cy + size * 0.48f, paint);
            } else {
                float fraction = volume / (float) Math.max(1, volumeMax);
                int waveCount = fraction <= 1f / 3f ? 1 : fraction <= 2f / 3f ? 2 : 3;
                for (int i = 0; i < waveCount; i++) {
                    float radius = size * (0.42f + i * 0.28f);
                    canvas.drawArc(new RectF(cx - size * 0.12f, cy - radius,
                            cx + radius, cy + radius), -55f, 110f, false, paint);
                }
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawIcon(Canvas canvas, int index, float cx, float cy, float size, int color) {
            Drawable drawable = buttonDrawable(index);
            if (drawable != null) {
                float visualSize = index == 1
                        ? (ringerMode == AudioManager.RINGER_MODE_VIBRATE ? dp(21)
                                : ringerMode == AudioManager.RINGER_MODE_SILENT ? dp(18) : dp(19))
                        : dp(19);
                drawButtonDrawable(canvas, drawable, index, cx, cy, visualSize, color);
                return;
            }
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (index == 0) {
                canvas.drawArc(new RectF(cx - size, cy - size * 0.7f, cx + size, cy + size * 1.0f),
                        225f, 90f, false, paint);
                canvas.drawArc(new RectF(cx - size * 0.62f, cy - size * 0.28f,
                        cx + size * 0.62f, cy + size * 0.78f), 225f, 90f, false, paint);
                canvas.drawCircle(cx, cy + size * 0.42f, size * 0.12f, paint);
            } else if (index == 1) {
                Path speaker = new Path();
                speaker.moveTo(cx - size * 0.9f, cy - size * 0.28f);
                speaker.lineTo(cx - size * 0.3f, cy - size * 0.28f);
                speaker.lineTo(cx + size * 0.28f, cy - size * 0.74f);
                speaker.lineTo(cx + size * 0.28f, cy + size * 0.74f);
                speaker.lineTo(cx - size * 0.3f, cy + size * 0.28f);
                speaker.lineTo(cx - size * 0.9f, cy + size * 0.28f);
                speaker.close();
                canvas.drawPath(speaker, paint);
                if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                    canvas.drawLine(cx - size * 0.95f, cy - size * 0.95f,
                            cx + size * 0.9f, cy + size * 0.95f, paint);
                } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                    canvas.drawLine(cx + size * 0.55f, cy - size * 0.55f,
                            cx + size * 0.88f, cy - size * 0.35f, paint);
                    canvas.drawLine(cx + size * 0.55f, cy + size * 0.55f,
                            cx + size * 0.88f, cy + size * 0.35f, paint);
                } else {
                    canvas.drawArc(new RectF(cx - size * 0.12f, cy - size * 0.62f,
                            cx + size * 1.0f, cy + size * 0.62f), -55f, 110f, false, paint);
                }
            } else if (index == 2) {
                Path bluetooth = new Path();
                bluetooth.moveTo(cx, cy - size);
                bluetooth.lineTo(cx, cy + size);
                bluetooth.moveTo(cx, cy - size);
                bluetooth.lineTo(cx + size * 0.8f, cy - size * 0.25f);
                bluetooth.lineTo(cx - size * 0.65f, cy + size * 0.7f);
                bluetooth.moveTo(cx, cy + size);
                bluetooth.lineTo(cx + size * 0.8f, cy + size * 0.25f);
                bluetooth.lineTo(cx - size * 0.65f, cy - size * 0.7f);
                canvas.drawPath(bluetooth, paint);
            } else if (index == 3) {
                RectF arc = new RectF(cx - size, cy - size, cx + size, cy + size);
                canvas.drawArc(arc, 225f, 245f, false, paint);
                Path arrow = new Path();
                arrow.moveTo(cx - size * 0.95f, cy - size * 0.05f);
                arrow.lineTo(cx - size * 0.95f, cy - size * 0.7f);
                arrow.lineTo(cx - size * 0.35f, cy - size * 0.38f);
                canvas.drawPath(arrow, paint);
            } else if (index == 4) {
                canvas.drawRect(cx - size * 0.82f, cy - size * 0.66f,
                        cx + size * 0.82f, cy + size * 0.66f, paint);
                canvas.drawLine(cx - size * 0.5f, cy - size * 0.95f,
                        cx - size * 0.95f, cy - size * 0.95f, paint);
                canvas.drawLine(cx - size * 0.95f, cy - size * 0.95f,
                        cx - size * 0.95f, cy - size * 0.5f, paint);
                canvas.drawLine(cx + size * 0.5f, cy + size * 0.95f,
                        cx + size * 0.95f, cy + size * 0.95f, paint);
                canvas.drawLine(cx + size * 0.95f, cy + size * 0.95f,
                        cx + size * 0.95f, cy + size * 0.5f, paint);
            } else if (index == 5) {
                canvas.drawRoundRect(new RectF(cx - size * 0.72f, cy - size * 0.05f,
                        cx + size * 0.72f, cy + size * 0.85f), size * 0.12f, size * 0.12f, paint);
                canvas.drawArc(new RectF(cx - size * 0.48f, cy - size * 0.8f,
                        cx + size * 0.48f, cy + size * 0.25f), 180f, 180f, false, paint);
            } else if (index == 6) {
                Path torch = new Path();
                torch.moveTo(cx - size * 0.48f, cy - size * 0.82f);
                torch.lineTo(cx + size * 0.48f, cy - size * 0.82f);
                torch.lineTo(cx + size * 0.34f, cy - size * 0.35f);
                torch.lineTo(cx + size * 0.2f, cy + size * 0.8f);
                torch.lineTo(cx - size * 0.2f, cy + size * 0.8f);
                torch.lineTo(cx - size * 0.34f, cy - size * 0.35f);
                torch.close();
                canvas.drawPath(torch, paint);
                canvas.drawLine(cx - size * 0.55f, cy - size * 0.35f,
                        cx + size * 0.55f, cy - size * 0.35f, paint);
            } else {
                canvas.drawCircle(cx, cy, size * 0.42f, paint);
                canvas.drawCircle(cx, cy, size * 0.9f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private Drawable buttonDrawable(int index) {
            if (index == 1) {
                if (ringerMode == AudioManager.RINGER_MODE_SILENT) return ringerSilentIcon;
                if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) return ringerVibrateIcon;
                return ringerNormalIcon;
            }
            return index >= 0 && index < buttonIcons.length ? buttonIcons[index] : null;
        }

        private void drawDrawable(Canvas canvas, Drawable drawable, float cx, float cy,
                float maxSize, int color) {
            drawable.setTint(color);
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();
            if (intrinsicWidth <= 0) intrinsicWidth = 1;
            if (intrinsicHeight <= 0) intrinsicHeight = 1;
            float scale = Math.min(maxSize / intrinsicWidth, maxSize / intrinsicHeight);
            int width = Math.max(1, Math.round(intrinsicWidth * scale));
            int height = Math.max(1, Math.round(intrinsicHeight * scale));
            drawable.setBounds(Math.round(cx - width / 2f), Math.round(cy - height / 2f),
                    Math.round(cx + width / 2f), Math.round(cy + height / 2f));
            drawable.draw(canvas);
        }

        private void drawSliderDrawable(Canvas canvas, Drawable drawable, int index,
                float cx, float cy, float visualSize, int color) {
            float left = index == 0 ? 0.0917f : 0.0417f;
            float top = index == 0 ? 0.1f : 0.0833f;
            float right = index == 0 ? 0.9083f : 1f;
            float bottom = index == 0 ? 0.9083f : 0.9167f;
            if (drawable != volumeAnimation) drawable.setTint(color);
            int intrinsicWidth = Math.max(1, drawable.getIntrinsicWidth());
            int intrinsicHeight = Math.max(1, drawable.getIntrinsicHeight());
            float contentWidth = (right - left) * intrinsicWidth;
            float contentHeight = (bottom - top) * intrinsicHeight;
            float scale = visualSize / Math.max(contentWidth, contentHeight);
            float outerWidth = intrinsicWidth * scale;
            float outerHeight = intrinsicHeight * scale;
            float contentCenterX = (left + right) * 0.5f * outerWidth;
            float contentCenterY = (top + bottom) * 0.5f * outerHeight;
            drawable.setBounds(Math.round(cx - contentCenterX), Math.round(cy - contentCenterY),
                    Math.round(cx - contentCenterX + outerWidth),
                    Math.round(cy - contentCenterY + outerHeight));
            drawable.draw(canvas);
        }

        private void drawButtonDrawable(Canvas canvas, Drawable drawable, int index,
                float cx, float cy, float visualSize, int color) {
            float left = 0f;
            float top = 0f;
            float right = 1f;
            float bottom = 1f;
            if (index == 0) {
                left = 0.1125f; top = 0.2f; right = 0.875f; bottom = 0.8f;
            } else if (index == 1 && ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                left = 0.0278f; top = 0.125f; right = 0.9722f; bottom = 0.875f;
            } else if (index == 1 && ringerMode == AudioManager.RINGER_MODE_SILENT) {
                left = 0.225f; top = 0.1083f; right = 0.7833f; bottom = 0.825f;
            } else if (index == 1) {
                left = 0.2083f; top = 0.1667f; right = 0.7833f; bottom = 0.825f;
            } else if (index == 2) {
                left = 0.2f; top = 0.1125f; right = 0.775f; bottom = 0.925f;
            } else if (index == 3) {
                left = 0.125f; top = 0.1667f; right = 0.875f; bottom = 0.8333f;
            } else if (index == 4) {
                left = 0.0694f; top = 0.0556f; right = 0.9306f; bottom = 0.9306f;
            } else if (index == 5) {
                left = 0.1667f; top = 0.0833f; right = 0.8333f; bottom = 0.9167f;
            } else if (index == 6) {
                left = 0.2188f; top = 0.2071f; right = 0.7813f; bottom = 0.8f;
            } else if (index == 7) {
                left = 0.0417f; top = 0.1667f; right = 0.9583f; bottom = 0.8333f;
            }

            drawable.setTint(color);
            int intrinsicWidth = Math.max(1, drawable.getIntrinsicWidth());
            int intrinsicHeight = Math.max(1, drawable.getIntrinsicHeight());
            float contentWidth = (right - left) * intrinsicWidth;
            float contentHeight = (bottom - top) * intrinsicHeight;
            float scale = visualSize / Math.max(contentWidth, contentHeight);
            float outerWidth = intrinsicWidth * scale;
            float outerHeight = intrinsicHeight * scale;
            float contentCenterX = (left + right) * 0.5f * outerWidth;
            float contentCenterY = (top + bottom) * 0.5f * outerHeight;
            drawable.setBounds(Math.round(cx - contentCenterX), Math.round(cy - contentCenterY),
                    Math.round(cx - contentCenterX + outerWidth),
                    Math.round(cy - contentCenterY + outerHeight));
            drawable.draw(canvas);
        }

        private boolean buttonActive(int index) {
            if (index == 0) return wifi;
            if (index == 1) return ringerMode != AudioManager.RINGER_MODE_SILENT;
            if (index == 2) return bluetooth;
            if (index == 3) return rotationAuto;
            if (index == 6) return flashlight;
            return index == 7 && recording;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (!bounds.contains(x, y)) return false;
                if (brightnessIcon.contains(x, y)) activeSlider = 0;
                else if (containsWithInset(brightnessTrack, x, y, dp(16))) activeSlider = 1;
                else if (containsWithInset(volumeTrack, x, y, dp(16))) activeSlider = 2;
                else activeSlider = 0;
                if (activeSlider != 0) updateSlider(x);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && activeSlider != 0) {
                updateSlider(x);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (activeSlider != 0) {
                    updateSlider(x);
                    if (activeSlider == 1) commitBrightness();
                    activeSlider = 0;
                    return true;
                }
                if (brightnessIcon.contains(x, y)) {
                    toggleBrightnessAuto();
                    return true;
                }
                for (int i = 0; i < mediaButtons.length; i++) {
                    if (mediaButtons[i].contains(x, y)) {
                        executeMediaButton(i);
                        return true;
                    }
                }
                for (int i = 0; i < buttons.length; i++) {
                    if (buttons[i].contains(x, y)) {
                        executeButton(i);
                        return true;
                    }
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (activeSlider == 1) commitBrightness();
                activeSlider = 0;
                return true;
            }
            return true;
        }

        private void updateSlider(float x) {
            RectF track = activeSlider == 1 ? brightnessTrack : volumeTrack;
            float fraction = Math.max(0f, Math.min(1f, (x - track.left) / track.width()));
            if (activeSlider == 1) {
                brightness = brightnessFromFraction(fraction);
                setTemporaryBrightness();
            } else {
                volume = Math.round(fraction * volumeMax);
                AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
                if (audio != null) audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
            }
            invalidate();
        }

        private void executeMediaButton(int index) {
            performClickHaptic();
            AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            if (index == 3 || index == 4) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        index == 3 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                refreshVolumeState();
                invalidate();
                return;
            }
            int keyCode = index == 0 ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    : index == 1 ? KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    : KeyEvent.KEYCODE_MEDIA_NEXT;
            long now = SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        }

        private void refreshBrightnessRange() {
            brightnessMin = integerResource("config_screenBrightnessSettingMinimum", 1);
            brightnessMax = integerResource("config_screenBrightnessSettingMaximum", 255);
            if (brightnessMin < 0) brightnessMin = 0;
            if (brightnessMax <= brightnessMin) {
                brightnessMin = 1;
                brightnessMax = 255;
            }
        }

        private int integerResource(String name, int fallback) {
            int id = getResources().getIdentifier(name, "integer", "android");
            if (id == 0) return fallback;
            try {
                return getResources().getInteger(id);
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        private int clampBrightness(int value) {
            return Math.max(brightnessMin, Math.min(brightnessMax, value));
        }

        private float brightnessFraction() {
            return (brightness - brightnessMin) / (float) (brightnessMax - brightnessMin);
        }

        private int brightnessFromFraction(float fraction) {
            return clampBrightness(brightnessMin
                    + Math.round(fraction * (brightnessMax - brightnessMin)));
        }

        private float brightnessFloat() {
            return Math.max(0f, Math.min(1f, brightness / (float) brightnessMax));
        }

        private int brightnessFromDisplay(float displayBrightness) {
            return clampBrightness(Math.round(displayBrightness * brightnessMax));
        }

        private int displayId() {
            Display display = getDisplay();
            return display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
        }

        private DisplayManager displayManager() {
            return (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        }

        private float currentDisplayBrightness() {
            try {
                DisplayManager display = displayManager();
                if (display == null) return -1f;
                Object value = display.getClass().getMethod("getBrightness", int.class)
                        .invoke(display, displayId());
                if (!(value instanceof Float)) return -1f;
                float brightness = (Float) value;
                return brightness >= 0f && brightness <= 1f ? brightness : -1f;
            } catch (Throwable ignored) {
                return -1f;
            }
        }

        private void setTemporaryBrightness() {
            try {
                DisplayManager display = displayManager();
                if (display == null) return;
                display.getClass().getMethod("setTemporaryBrightness", int.class, float.class)
                        .invoke(display, displayId(), brightnessFloat());
            } catch (Throwable ignored) {
            }
        }

        private void commitBrightness() {
            boolean committed = false;
            try {
                DisplayManager display = displayManager();
                if (display != null) {
                    display.getClass().getMethod("setBrightness", int.class, float.class)
                            .invoke(display, displayId(), brightnessFloat());
                    committed = true;
                }
            } catch (Throwable ignored) {
            }
            if (!committed) putSystemInt(Settings.System.SCREEN_BRIGHTNESS, brightness);
            setTemporaryBrightness();
        }

        private void toggleBrightnessAuto() {
            performClickHaptic();
            brightnessAuto = !brightnessAuto;
            putSystemInt(Settings.System.SCREEN_BRIGHTNESS_MODE,
                    brightnessAuto ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                            : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            refreshState();
            invalidate();
        }

        private boolean containsWithInset(RectF bounds, float x, float y, float inset) {
            return x >= bounds.left - inset && x <= bounds.right + inset
                    && y >= bounds.top - inset && y <= bounds.bottom + inset;
        }

        private void executeButton(int index) {
            performClickHaptic();
            try {
                switch (index) {
                    case 0: toggleWifi(); break;
                    case 1: toggleRinger(); break;
                    case 2: toggleBluetooth(); break;
                    case 3: toggleRotation(); break;
                    case 4:
                        hide();
                        main.postDelayed(() -> {
                            try {
                                takeScreenshot();
                            } catch (Throwable t) {
                                AssistantHooks.warn("Quick tool failed: " + TOOL_DESCRIPTIONS[index], t);
                            }
                        }, 260L);
                        return;
                    case 5: lockScreen(); hide(); return;
                    case 6: toggleFlashlight(); break;
                    case 7: hide(); toggleScreenRecord(); return;
                    default: return;
                }
            } catch (Throwable t) {
                AssistantHooks.warn("Quick tool failed: " + TOOL_DESCRIPTIONS[index], t);
                return;
            }
            refreshState();
            invalidate();
            main.postDelayed(() -> {
                refreshState();
                invalidate();
            }, 800L);
        }

        private void performClickHaptic() {
            HapticFeedbackUtils.perform(getContext(), this, HapticFeedbackConstants.CONTEXT_CLICK);
        }

        private void toggleWifi() {
            WifiManager manager = (WifiManager) getContext().getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (manager == null || !manager.setWifiEnabled(!manager.isWifiEnabled())) {
                throw new IllegalStateException("Wi-Fi control unavailable");
            }
        }

        private void toggleRinger() {
            AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            int mode = audio.getRingerMode();
            audio.setRingerMode(mode == AudioManager.RINGER_MODE_NORMAL
                    ? AudioManager.RINGER_MODE_VIBRATE
                    : mode == AudioManager.RINGER_MODE_VIBRATE
                    ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL);
        }

        private void toggleBluetooth() {
            ClassLoader loader = getContext().getClassLoader();
            try {
                Class<?> dependency = Class.forName("com.android.systemui.Dependency", false, loader);
                Class<?> controllerClass = Class.forName(
                        "com.android.systemui.statusbar.policy.BluetoothController", false, loader);
                Object controller = dependency.getMethod("get", Class.class)
                        .invoke(null, controllerClass);
                boolean enabled = invokeBoolean(controller, "isBluetoothEnabled");
                controllerClass.getMethod("setBluetoothEnabled", boolean.class)
                        .invoke(controller, !enabled);
            } catch (Throwable unavailable) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    if (adapter.isEnabled()) adapter.disable(); else adapter.enable();
                }
            }
        }

        private void toggleRotation() {
            boolean auto = systemInt(Settings.System.ACCELEROMETER_ROTATION, 1) != 0;
            if (auto) {
                DisplayManager display = (DisplayManager) getContext()
                        .getSystemService(Context.DISPLAY_SERVICE);
                android.view.Display current = display == null ? null : display.getDisplay(0);
                putSystemInt(Settings.System.USER_ROTATION,
                        current == null ? 0 : current.getRotation());
                putSystemInt(Settings.System.ACCELEROMETER_ROTATION, 0);
            } else {
                putSystemInt(Settings.System.ACCELEROMETER_ROTATION, 1);
            }
        }

        private void toggleFlashlight() throws Exception {
            ClassLoader loader = getContext().getClassLoader();
            try {
                Class<?> dependency = Class.forName("com.android.systemui.Dependency", false, loader);
                Class<?> controllerClass = Class.forName(
                        "com.android.systemui.statusbar.policy.FlashlightController", false, loader);
                Object controller = dependency.getMethod("get", Class.class)
                        .invoke(null, controllerClass);
                flashlight = !invokeBoolean(controller, "isEnabled");
                controllerClass.getMethod("setFlashlight", boolean.class)
                        .invoke(controller, flashlight);
                return;
            } catch (Throwable ignored) {
            }
            CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return;
            String cameraId = null;
            for (String id : manager.getCameraIdList()) {
                Boolean available = manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(available)) { cameraId = id; break; }
            }
            if (cameraId != null) {
                flashlight = !flashlight;
                manager.setTorchMode(cameraId, flashlight);
            }
        }

        private boolean flashlightState() {
            try {
                ClassLoader loader = getContext().getClassLoader();
                Class<?> dependency = Class.forName("com.android.systemui.Dependency", false, loader);
                Class<?> controllerClass = Class.forName(
                        "com.android.systemui.statusbar.policy.FlashlightController", false, loader);
                Object controller = dependency.getMethod("get", Class.class)
                        .invoke(null, controllerClass);
                return invokeBoolean(controller, "isEnabled");
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void takeScreenshot() throws Exception {
            Class<?> helperClass = Class.forName("com.android.internal.util.ScreenshotHelper", false,
                    getContext().getClassLoader());
            Object helper = helperClass.getConstructor(Context.class).newInstance(getContext());
            Class<?> consumerClass = Class.forName("java.util.function.Consumer", false,
                    getContext().getClassLoader());
            helperClass.getMethod("takeScreenshot", int.class, Handler.class, consumerClass)
                    .invoke(helper, 1, main, null);
        }

        private void lockScreen() throws Exception {
            PowerManager power = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            if (power == null) throw new IllegalStateException("Power service unavailable");
            power.getClass().getMethod("goToSleep", long.class)
                    .invoke(power, SystemClock.uptimeMillis());
        }

        private void toggleScreenRecord() throws Exception {
            Object tile = getTile("screenrecord", "ScreenRecordTile");
            if (tile == null) throw new IllegalStateException("Screen recording tile unavailable");
            Class<?> expandable = Class.forName("com.android.systemui.animation.Expandable", false,
                    getContext().getClassLoader());
            tile.getClass().getMethod("handleClick", expandable).invoke(tile, new Object[]{null});
        }

        private boolean screenRecordState() {
            try {
                Object tile = getTile("screenrecord", "ScreenRecordTile");
                Object controller = fieldValue(tile, "mController");
                if (controller == null) return false;
                return invokeBoolean(controller, "isRecordingForQS") || invokeBoolean(controller, "isRecording");
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean invokeBoolean(Object target, String name) throws Exception {
            Object value = target.getClass().getMethod(name).invoke(target);
            return Boolean.TRUE.equals(value);
        }

        private Object getTile(String spec, String simpleClassName) {
            Object host = qsHost;
            if (host == null) return null;
            try {
                Object tiles = host.getClass().getMethod("getTiles").invoke(host);
                if (tiles instanceof Collection) {
                    for (Object tile : (Collection<?>) tiles) {
                        if (tile.getClass().getSimpleName().equals(simpleClassName)) return tile;
                    }
                }
                synchronized (CREATED_TILES) {
                    Object cached = CREATED_TILES.get(spec);
                    if (cached != null) return cached;
                    Object tile = host.getClass().getMethod("createTile", String.class)
                            .invoke(host, spec);
                    if (tile != null) {
                        try { tile.getClass().getMethod("initialize").invoke(tile); }
                        catch (Throwable ignored) { }
                        CREATED_TILES.put(spec, tile);
                    }
                    return tile;
                }
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Object fieldValue(Object target, String name) {
            if (target == null) return null;
            try {
                java.lang.reflect.Field field = target.getClass().getField(name);
                return field.get(target);
            } catch (Throwable ignored) {
                try {
                    java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Throwable alsoIgnored) {
                    return null;
                }
            }
        }

        private int systemInt(String key, int fallback) {
            try {
                Method method = Settings.System.class.getMethod("getIntForUser",
                        android.content.ContentResolver.class, String.class, int.class, int.class);
                Object result = method.invoke(null, getContext().getContentResolver(), key, fallback, -2);
                return result instanceof Integer ? (Integer) result : fallback;
            } catch (Throwable ignored) {
                try {
                    return Settings.System.getInt(getContext().getContentResolver(), key, fallback);
                } catch (Throwable alsoIgnored) {
                    return fallback;
                }
            }
        }

        private void putSystemInt(String key, int value) {
            try {
                Method method = Settings.System.class.getMethod("putIntForUser",
                        android.content.ContentResolver.class, String.class, int.class, int.class);
                method.invoke(null, getContext().getContentResolver(), key, value, -2);
            } catch (Throwable ignored) {
                Settings.System.putInt(getContext().getContentResolver(), key, value);
            }
        }

        private float dp(float value) { return value * density; }

        private int statusBarHeight() {
            int resource = getResources().getIdentifier("status_bar_height", "dimen", "android");
            return resource == 0 ? Math.round(dp(24)) : getResources().getDimensionPixelSize(resource);
        }

        private int navigationBarHeight() {
            int resource = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            return resource == 0 ? Math.round(dp(24)) : getResources().getDimensionPixelSize(resource);
        }
    }
}
