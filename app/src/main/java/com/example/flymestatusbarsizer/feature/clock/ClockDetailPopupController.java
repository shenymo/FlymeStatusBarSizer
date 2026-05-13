package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class ClockDetailPopupController {
    private static final long AUTO_DISMISS_DELAY_MS = 8000L;
    private static final long MILLIS_REFRESH_INTERVAL_MS = 100L;
    private static final long SECOND_REFRESH_INTERVAL_MS = 1000L;
    private static final long THERMAL_POWER_REFRESH_INTERVAL_MS = 2000L;
    private static final long MEMORY_REFRESH_INTERVAL_MS = 10000L;
    private static final long POPUP_EXPAND_DURATION_MS = 320L;
    private static final long POPUP_COLLAPSE_DURATION_MS = 220L;
    private static final int HORIZONTAL_MARGIN_DP = 16;
    private static final int STATUS_TILE_GAP_DP = 8;
    private static final int POPUP_SURFACE_OFFSET_Y_DP = 8;
    private static final int POPUP_SURFACE_RADIUS_DP = 24;
    private static final int POPUP_SHADOW_PADDING_DP = 10;
    private static final int POPUP_SHADOW_ELEVATION_DP = 26;
    private static final int POPUP_SHADOW_TRANSLATION_Z_DP = 6;
    private static final int POPUP_BACKGROUND_BLUR_RADIUS_DP = 32;
    private static final int POPUP_BACKGROUND_BLUR_Z_ORDER_BOTTOM = -1;
    private static final int WINDOW_FLAG_BLUR_BEHIND = 4;
    private static final int CLOCK_HIGHLIGHT_VERTICAL_INSET_DP = 3;
    private static final int CLOCK_HIGHLIGHT_HORIZONTAL_INSET_DP = 1;
    private static final OvershootInterpolator POPUP_SCALE_IN_INTERPOLATOR =
            new OvershootInterpolator(0.72f);
    private static final PathInterpolator POPUP_ALPHA_IN_INTERPOLATOR =
            new PathInterpolator(0.18f, 0f, 0.12f, 1f);
    private static final PathInterpolator POPUP_TRANSLATION_IN_INTERPOLATOR =
            new PathInterpolator(0.16f, 1f, 0.28f, 1f);
    private static final PathInterpolator POPUP_OUT_INTERPOLATOR =
            new PathInterpolator(0.4f, 0f, 0.82f, 0.72f);

    private final WeakReference<TextView> anchorRef;
    private final Handler handler;
    private final FrameLayout popupRootView;
    private final View popupBackgroundView;
    private final LinearLayout contentView;
    private final LinearLayout timeRowView;
    private final TextView timeView;
    private final TextView millisecondsView;
    private final TextView dateView;
    private final LinearLayout statusGridView;
    private final MemoryStatTile memoryTile;
    private final StatTile thermalPowerTile;
    private final PopupWindow popupWindow;
    private final ClockDetailSystemStatusProvider systemStatusProvider;
    private final Runnable refreshRunnable = this::refreshVisibleContent;
    private final Runnable thermalPowerRefreshRunnable = this::refreshVisibleThermalPowerStatus;
    private final Runnable memoryRefreshRunnable = this::refreshVisibleMemoryStatus;
    private final Runnable autoDismissRunnable = this::dismiss;

    private boolean enabled;
    private boolean showMilliseconds = true;
    private boolean thermalPowerQueryInFlight;
    private boolean memoryQueryInFlight;
    private boolean dismissAnimationRunning;
    private Palette currentPalette;
    private ClockDetailSystemStatusSnapshot latestSystemStatusSnapshot =
            ClockDetailSystemStatusSnapshot.EMPTY;
    private Locale cachedLocale;
    private TimeZone cachedTimeZone;
    private boolean cached24HourMode;
    private SimpleDateFormat timeFormatter;
    private SimpleDateFormat dateFormatter;
    private long lastRenderedSecond = Long.MIN_VALUE;
    private int lastRenderedMillisBucket = Integer.MIN_VALUE;
    private long lastDateRefreshSecond = Long.MIN_VALUE;
    private long lastRenderedDateKey = Long.MIN_VALUE;
    private int popupWidth;
    private int popupHeight;
    private Animator popupAnimator;
    private Drawable originalAnchorBackground;
    private int[] originalAnchorPadding;
    private boolean originalAnchorBackgroundCaptured;
    private boolean anchorHighlighted;

    ClockDetailPopupController(TextView anchor) {
        this.anchorRef = new WeakReference<>(anchor);
        Handler mainHandler = FlymeStatusBarSizer.getMainHandler();
        this.handler = mainHandler != null
                ? mainHandler
                : new Handler(anchor.getContext().getMainLooper());
        Context context = anchor.getContext();
        this.contentView = buildContentView(context);
        this.popupBackgroundView = buildPopupBackgroundView(context);
        this.popupRootView = buildPopupRootView(context, popupBackgroundView, contentView);
        this.timeRowView = buildTimeRowView(context);
        this.timeView = buildTimeView(context);
        this.millisecondsView = buildMillisecondsView(context);
        this.dateView = buildDateView(context);
        this.memoryTile = buildMemoryStatTile(
                context,
                "系统内存",
                ClockDetailSystemStatusSnapshot.EMPTY.memoryRows);
        this.thermalPowerTile = buildStatTile(context, "电池温度 / 功率", true);
        this.statusGridView = buildStatusGrid(context, memoryTile, thermalPowerTile);
        this.timeRowView.addView(timeView, wrapContent());
        this.timeRowView.addView(millisecondsView, wrapContentWithStart(context, 2));
        this.contentView.addView(timeRowView, matchWidth());
        this.contentView.addView(dateView, matchWidthWithTop(context, 5));
        this.contentView.addView(statusGridView, matchWidthWithTop(context, 12));
        this.popupWindow = new PopupWindow(
                popupRootView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                false);
        this.systemStatusProvider = new ClockDetailSystemStatusProvider(context);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setAnimationStyle(0);
        popupWindow.setFocusable(false);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setElevation(0f);
        popupWindow.setOnDismissListener(this::handlePopupDismissed);
        disableTouchModal(popupWindow);
        timeView.setOnTouchListener(this::handleTimeTextTouch);
        millisecondsView.setOnTouchListener(this::handleTimeTextTouch);
        anchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                dismissImmediately();
            }
        });
        anchor.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!popupWindow.isShowing() || dismissAnimationRunning) {
                return;
            }
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                return;
            }
            handler.post(this::updatePopupPosition);
        });
    }

    void syncWithConfig(FlymeStatusBarSizer.ClockConfigSnapshot config) {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismissImmediately();
            return;
        }
        boolean shouldEnable = config != null && config.clockDetailPopupEnabled;
        enabled = shouldEnable;
        anchor.setHapticFeedbackEnabled(shouldEnable);
        anchor.setClickable(shouldEnable);
        anchor.setOnClickListener(shouldEnable ? v -> {
            performClockHaptic(v);
            toggle();
        } : null);
        if (!shouldEnable) {
            dismissImmediately();
        }
    }

    void dismiss() {
        dismissInternal(true);
    }

    private void dismissImmediately() {
        dismissInternal(false);
    }

    private void dismissInternal(boolean animate) {
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(thermalPowerRefreshRunnable);
        handler.removeCallbacks(memoryRefreshRunnable);
        handler.removeCallbacks(autoDismissRunnable);
        if (!popupWindow.isShowing()) {
            clearPopupUiState();
            return;
        }
        if (dismissAnimationRunning) {
            return;
        }
        cancelPopupAnimator();
        if (!animate || !popupRootView.isLaidOut()) {
            popupWindow.dismiss();
            return;
        }
        animatePopupOut();
    }

    private void toggle() {
        if (!enabled || dismissAnimationRunning) {
            return;
        }
        if (popupWindow.isShowing()) {
            dismiss();
            return;
        }
        show();
    }

    private void show() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            return;
        }
        if (!enabled || !anchor.isAttachedToWindow()) {
            return;
        }
        dismissAnimationRunning = false;
        cancelPopupAnimator();
        FlymeStatusBarSizer.disableAncestorClipping(anchor, 6);
        applyPalette(resolvePalette());
        ensureFormatters();
        long nowMillis = System.currentTimeMillis();
        refreshTimeText(nowMillis, true);
        refreshDateTextIfNeeded(nowMillis, true);
        updateSystemStatusViews(latestSystemStatusSnapshot);
        measureContent();
        int xOffset = calculateXOffset();
        int yOffset = calculatePopupWindowYOffset(anchor);
        popupWindow.setWidth(popupWidth);
        popupWindow.setHeight(popupHeight);
        applyAnchorHighlight(anchor);
        popupWindow.showAsDropDown(anchor, xOffset, yOffset, Gravity.START);
        installNativePopupBlurIfPossible();
        requestThermalPowerRefresh();
        requestMemoryStatusRefresh();
        animatePopupIn();
        scheduleAutoDismiss();
        scheduleRefresh();
        scheduleThermalPowerRefresh();
        scheduleMemoryRefresh();
    }

    private void refreshVisibleContent() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismissImmediately();
            return;
        }
        if (!popupWindow.isShowing() || !anchor.isAttachedToWindow()) {
            dismissImmediately();
            return;
        }
        boolean formattersChanged = ensureFormatters();
        long nowMillis = System.currentTimeMillis();
        refreshTimeText(nowMillis, formattersChanged);
        boolean dateChanged = refreshDateTextIfNeeded(nowMillis, formattersChanged);
        if ((formattersChanged || dateChanged) && popupWindow.isShowing()) {
            measureContent();
            updatePopupPosition();
        }
        scheduleRefresh();
    }

    private void refreshVisibleThermalPowerStatus() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismissImmediately();
            return;
        }
        if (!popupWindow.isShowing() || !anchor.isAttachedToWindow()) {
            dismissImmediately();
            return;
        }
        requestThermalPowerRefresh();
        handler.postAtTime(
                thermalPowerRefreshRunnable,
                SystemClock.uptimeMillis() + THERMAL_POWER_REFRESH_INTERVAL_MS);
    }

    private void refreshVisibleMemoryStatus() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismissImmediately();
            return;
        }
        if (!popupWindow.isShowing() || !anchor.isAttachedToWindow()) {
            dismissImmediately();
            return;
        }
        requestMemoryStatusRefresh();
        handler.postAtTime(
                memoryRefreshRunnable,
                SystemClock.uptimeMillis() + MEMORY_REFRESH_INTERVAL_MS);
    }

    private boolean ensureFormatters() {
        Locale locale = resolveLocale();
        TimeZone timeZone = resolveTimeZone();
        boolean is24Hour = DateFormat.is24HourFormat(contentView.getContext());
        if (timeFormatter != null
                && dateFormatter != null
                && cached24HourMode == is24Hour
                && cachedLocale != null
                && cachedLocale.equals(locale)
                && cachedTimeZone != null
                && cachedTimeZone.getID().equals(timeZone.getID())) {
            return false;
        }
        cachedLocale = locale;
        cachedTimeZone = timeZone;
        cached24HourMode = is24Hour;
        String timePattern = DateFormat.getBestDateTimePattern(locale, is24Hour ? "Hms" : "hms");
        if (timePattern == null || timePattern.trim().isEmpty()) {
            timePattern = is24Hour ? "HH:mm:ss" : "h:mm:ss a";
        }
        String datePattern = DateFormat.getBestDateTimePattern(locale, "yMMMMdEEE");
        if (datePattern == null || datePattern.trim().isEmpty()) {
            datePattern = "yyyy-MM-dd EEEE";
        }
        timeFormatter = new SimpleDateFormat(timePattern, locale);
        dateFormatter = new SimpleDateFormat(datePattern, locale);
        timeFormatter.setTimeZone(timeZone);
        dateFormatter.setTimeZone(timeZone);
        return true;
    }

    private boolean refreshTimeText(long nowMillis, boolean force) {
        long secondKey = nowMillis / SECOND_REFRESH_INTERVAL_MS;
        int millisBucket = showMilliseconds
                ? (int) ((nowMillis % SECOND_REFRESH_INTERVAL_MS) / MILLIS_REFRESH_INTERVAL_MS)
                : -1;
        if (!force && secondKey == lastRenderedSecond && millisBucket == lastRenderedMillisBucket) {
            return false;
        }
        boolean changed = false;
        if (force || secondKey != lastRenderedSecond) {
            String baseTime = timeFormatter != null
                    ? timeFormatter.format(new Date(nowMillis))
                    : "";
            changed |= setTextIfChanged(timeView, baseTime);
        }
        if (showMilliseconds) {
            changed |= setVisibilityIfChanged(millisecondsView, View.VISIBLE);
            changed |= setTextIfChanged(millisecondsView, formatMilliseconds(nowMillis));
        } else {
            changed |= setVisibilityIfChanged(millisecondsView, View.GONE);
            changed |= setTextIfChanged(millisecondsView, "");
        }
        lastRenderedSecond = secondKey;
        lastRenderedMillisBucket = millisBucket;
        return changed;
    }

    private boolean refreshDateTextIfNeeded(long nowMillis, boolean force) {
        long secondKey = nowMillis / SECOND_REFRESH_INTERVAL_MS;
        if (!force && secondKey == lastDateRefreshSecond) {
            return false;
        }
        lastDateRefreshSecond = secondKey;
        long dateKey = resolveDateKey(nowMillis);
        if (!force && dateKey == lastRenderedDateKey) {
            return false;
        }
        String dateText = dateFormatter != null
                ? dateFormatter.format(new Date(nowMillis))
                : "";
        boolean changed = setTextIfChanged(dateView, dateText);
        lastRenderedDateKey = dateKey;
        return changed;
    }

    private long resolveDateKey(long nowMillis) {
        Calendar calendar = Calendar.getInstance(
                cachedTimeZone != null ? cachedTimeZone : TimeZone.getDefault(),
                cachedLocale != null ? cachedLocale : Locale.getDefault());
        calendar.setTimeInMillis(nowMillis);
        return (calendar.get(Calendar.YEAR) * 1000L) + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private String formatMilliseconds(long nowMillis) {
        int milliseconds = (int) (nowMillis % SECOND_REFRESH_INTERVAL_MS);
        Locale locale = cachedLocale != null ? cachedLocale : Locale.getDefault();
        return String.format(locale, ".%03d", milliseconds);
    }

    private void scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable);
        if (!popupWindow.isShowing()) {
            return;
        }
        long next;
        long now = SystemClock.uptimeMillis();
        if (showMilliseconds) {
            next = ((now / MILLIS_REFRESH_INTERVAL_MS) + 1L) * MILLIS_REFRESH_INTERVAL_MS;
        } else {
            next = ((now / SECOND_REFRESH_INTERVAL_MS) * SECOND_REFRESH_INTERVAL_MS)
                    + SECOND_REFRESH_INTERVAL_MS;
        }
        handler.postAtTime(refreshRunnable, next);
    }

    private void scheduleThermalPowerRefresh() {
        handler.removeCallbacks(thermalPowerRefreshRunnable);
        if (!popupWindow.isShowing()) {
            return;
        }
        handler.postAtTime(
                thermalPowerRefreshRunnable,
                SystemClock.uptimeMillis() + THERMAL_POWER_REFRESH_INTERVAL_MS);
    }

    private void scheduleMemoryRefresh() {
        handler.removeCallbacks(memoryRefreshRunnable);
        if (!popupWindow.isShowing()) {
            return;
        }
        handler.postAtTime(
                memoryRefreshRunnable,
                SystemClock.uptimeMillis() + MEMORY_REFRESH_INTERVAL_MS);
    }

    private void scheduleAutoDismiss() {
        handler.removeCallbacks(autoDismissRunnable);
        if (!popupWindow.isShowing() || AUTO_DISMISS_DELAY_MS <= 0L) {
            return;
        }
        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DELAY_MS);
    }

    private void updatePopupPosition() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismissImmediately();
            return;
        }
        if (!popupWindow.isShowing() || dismissAnimationRunning) {
            return;
        }
        popupWindow.update(
                anchor,
                calculateXOffset(),
                calculatePopupWindowYOffset(anchor),
                popupWidth,
                popupHeight);
        updatePopupAnimationPivot(anchor);
    }

    private void measureContent() {
        Context context = popupRootView.getContext();
        int margin = dp(context, HORIZONTAL_MARGIN_DP);
        popupWidth = Math.max(
                1,
                context.getResources().getDisplayMetrics().widthPixels - (margin * 2));
        popupRootView.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popupHeight = Math.max(popupRootView.getMeasuredHeight(), 0);
    }

    private int calculateXOffset() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            return 0;
        }
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int screenWidth = anchor.getResources().getDisplayMetrics().widthPixels;
        int margin = dp(anchor.getContext(), HORIZONTAL_MARGIN_DP);
        int desiredLeft = anchorLocation[0];
        int maxLeft = Math.max(margin, screenWidth - margin - popupWidth);
        int clampedLeft = Math.max(margin, Math.min(desiredLeft, maxLeft));
        return clampedLeft - desiredLeft;
    }

    private int calculatePopupWindowYOffset(TextView anchor) {
        return dp(anchor.getContext(), POPUP_SURFACE_OFFSET_Y_DP)
                - dp(anchor.getContext(), POPUP_SHADOW_PADDING_DP);
    }

    private void animatePopupIn() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            return;
        }
        popupRootView.post(() -> {
            if (!popupWindow.isShowing()) {
                return;
            }
            updatePopupAnimationPivot(anchor);
            float startScaleX = resolveCollapsedScaleX(anchor);
            float startScaleY = resolveCollapsedScaleY(anchor);
            float startTranslationY = -dp(anchor.getContext(), 14);
            popupRootView.setAlpha(0.16f);
            popupRootView.setScaleX(startScaleX);
            popupRootView.setScaleY(startScaleY);
            popupRootView.setTranslationY(startTranslationY);

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(popupRootView, View.SCALE_X, startScaleX, 1f);
            scaleX.setDuration(POPUP_EXPAND_DURATION_MS);
            scaleX.setInterpolator(POPUP_SCALE_IN_INTERPOLATOR);

            ObjectAnimator scaleY = ObjectAnimator.ofFloat(popupRootView, View.SCALE_Y, startScaleY, 1f);
            scaleY.setDuration(POPUP_EXPAND_DURATION_MS);
            scaleY.setInterpolator(POPUP_SCALE_IN_INTERPOLATOR);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(popupRootView, View.ALPHA, 0.16f, 1f);
            alpha.setDuration(220L);
            alpha.setInterpolator(POPUP_ALPHA_IN_INTERPOLATOR);

            ObjectAnimator translationY = ObjectAnimator.ofFloat(
                    popupRootView,
                    View.TRANSLATION_Y,
                    startTranslationY,
                    0f);
            translationY.setDuration(POPUP_EXPAND_DURATION_MS);
            translationY.setInterpolator(POPUP_TRANSLATION_IN_INTERPOLATOR);

            AnimatorSet animator = new AnimatorSet();
            animator.playTogether(scaleX, scaleY, alpha, translationY);
            startPopupAnimation(animator, null);
        });
    }

    private void animatePopupOut() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            popupWindow.dismiss();
            return;
        }
        dismissAnimationRunning = true;
        popupRootView.post(() -> {
            if (!popupWindow.isShowing()) {
                dismissAnimationRunning = false;
                clearPopupUiState();
                return;
            }
            updatePopupAnimationPivot(anchor);
            float endScaleX = resolveCollapsedScaleX(anchor);
            float endScaleY = resolveCollapsedScaleY(anchor);
            float endTranslationY = -dp(anchor.getContext(), 12);

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(
                    popupRootView,
                    View.SCALE_X,
                    popupRootView.getScaleX(),
                    endScaleX);
            scaleX.setDuration(POPUP_COLLAPSE_DURATION_MS);
            scaleX.setInterpolator(POPUP_OUT_INTERPOLATOR);

            ObjectAnimator scaleY = ObjectAnimator.ofFloat(
                    popupRootView,
                    View.SCALE_Y,
                    popupRootView.getScaleY(),
                    endScaleY);
            scaleY.setDuration(POPUP_COLLAPSE_DURATION_MS);
            scaleY.setInterpolator(POPUP_OUT_INTERPOLATOR);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(
                    popupRootView,
                    View.ALPHA,
                    popupRootView.getAlpha(),
                    0f);
            alpha.setDuration(180L);
            alpha.setInterpolator(POPUP_OUT_INTERPOLATOR);

            ObjectAnimator translationY = ObjectAnimator.ofFloat(
                    popupRootView,
                    View.TRANSLATION_Y,
                    popupRootView.getTranslationY(),
                    endTranslationY);
            translationY.setDuration(POPUP_COLLAPSE_DURATION_MS);
            translationY.setInterpolator(POPUP_OUT_INTERPOLATOR);

            AnimatorSet animator = new AnimatorSet();
            animator.playTogether(scaleX, scaleY, alpha, translationY);
            startPopupAnimation(animator, () -> {
                if (popupWindow.isShowing()) {
                    popupWindow.dismiss();
                } else {
                    handlePopupDismissed();
                }
            });
        });
    }

    private void startPopupAnimation(AnimatorSet animator, Runnable onEnd) {
        cancelPopupAnimator();
        popupAnimator = animator;
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (popupAnimator == animation) {
                    popupAnimator = null;
                }
                if (!cancelled && onEnd != null) {
                    onEnd.run();
                }
            }
        });
        animator.start();
    }

    private void cancelPopupAnimator() {
        Animator animator = popupAnimator;
        popupAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
    }

    private void updatePopupAnimationPivot(TextView anchor) {
        if (anchor == null || popupRootView.getWidth() <= 0) {
            return;
        }
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        float popupLeft = anchorLocation[0] + calculateXOffset();
        float anchorCenterX = anchorLocation[0] + (anchor.getWidth() * 0.5f);
        float shadowPadding = dp(anchor.getContext(), POPUP_SHADOW_PADDING_DP);
        float maxPivot = Math.max(shadowPadding, popupRootView.getWidth() - shadowPadding);
        float pivotX = clamp(anchorCenterX - popupLeft, shadowPadding, maxPivot);
        popupRootView.setPivotX(pivotX);
        popupRootView.setPivotY(shadowPadding);
    }

    private float resolveCollapsedScaleX(TextView anchor) {
        if (anchor == null || popupRootView.getWidth() <= 0) {
            return 0.32f;
        }
        return clamp(
                anchor.getWidth() / (float) popupRootView.getWidth(),
                0.32f,
                0.82f);
    }

    private float resolveCollapsedScaleY(TextView anchor) {
        if (anchor == null || popupRootView.getHeight() <= 0) {
            return 0.18f;
        }
        return clamp(
                anchor.getHeight() / (float) popupRootView.getHeight(),
                0.18f,
                0.56f);
    }

    private void applyPalette(Palette palette) {
        Context context = contentView.getContext();
        currentPalette = palette;
        contentView.setBackground(null);
        popupBackgroundView.setBackground(buildPopupBackgroundDrawable(context, palette, false));
        applyPopupShadowStyle(context, palette);
        timeView.setTextColor(palette.primaryTextColor);
        millisecondsView.setTextColor(palette.accentColor);
        dateView.setTextColor(palette.secondaryTextColor);
        applyMemoryTilePalette(memoryTile, palette);
        applyStatTilePalette(thermalPowerTile, palette);
    }

    private Drawable buildPopupBackgroundDrawable(
            Context context,
            Palette palette,
            boolean preferNativeBlur) {
        Drawable nativeBlurDrawable = preferNativeBlur
                ? tryCreateNativePopupBlurDrawable(popupBackgroundView, palette)
                : null;
        if (nativeBlurDrawable == null) {
            return buildPopupSurfaceDrawable(context, palette);
        }
        LayerDrawable layered = new LayerDrawable(new Drawable[]{
                nativeBlurDrawable,
                buildPopupStrokeDrawable(context, palette)
        });
        return layered;
    }

    private GradientDrawable buildPopupSurfaceDrawable(Context context, Palette palette) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(adjustAlpha(palette.surfaceColor, 0.88f));
        background.setCornerRadius(dp(context, POPUP_SURFACE_RADIUS_DP));
        background.setStroke(
                Math.max(1, dp(context, 1)),
                adjustAlpha(palette.strokeColor, 0.86f));
        return background;
    }

    private GradientDrawable buildPopupStrokeDrawable(Context context, Palette palette) {
        GradientDrawable stroke = new GradientDrawable();
        stroke.setShape(GradientDrawable.RECTANGLE);
        stroke.setColor(Color.TRANSPARENT);
        stroke.setCornerRadius(dp(context, POPUP_SURFACE_RADIUS_DP));
        stroke.setStroke(
                Math.max(1, dp(context, 1)),
                adjustAlpha(palette.strokeColor, 0.86f));
        return stroke;
    }

    private void applyPopupShadowStyle(Context context, Palette palette) {
        float backgroundElevation = dp(context, POPUP_SHADOW_ELEVATION_DP);
        float backgroundTranslationZ = dp(context, POPUP_SHADOW_TRANSLATION_Z_DP);
        popupBackgroundView.setElevation(backgroundElevation);
        popupBackgroundView.setTranslationZ(backgroundTranslationZ);
        contentView.setElevation(backgroundElevation + dp(context, 2));
        contentView.setTranslationZ(backgroundTranslationZ + dp(context, 2));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int ambientShadow = adjustAlpha(
                    isLightForeground(palette.primaryTextColor)
                            ? Color.parseColor("#120902")
                            : Color.parseColor("#261304"),
                    0.72f);
            int spotShadow = adjustAlpha(
                    isLightForeground(palette.primaryTextColor)
                            ? Color.parseColor("#201003")
                            : Color.parseColor("#341906"),
                    0.88f);
            popupBackgroundView.setOutlineAmbientShadowColor(ambientShadow);
            popupBackgroundView.setOutlineSpotShadowColor(spotShadow);
        }
    }

    private void installNativePopupBlurIfPossible() {
        if (!popupWindow.isShowing() || currentPalette == null) {
            return;
        }
        popupBackgroundView.post(() -> {
            if (!popupWindow.isShowing() || currentPalette == null) {
                return;
            }
            Drawable blurBackground = buildPopupBackgroundDrawable(
                    popupBackgroundView.getContext(),
                    currentPalette,
                    true);
            popupBackgroundView.setBackground(blurBackground);
            if (blurBackground == null || blurBackground instanceof GradientDrawable) {
                applyPopupWindowBlurBehind(
                        popupRootView,
                        dp(popupRootView.getContext(), POPUP_BACKGROUND_BLUR_RADIUS_DP));
            }
        });
    }

    private void handlePopupDismissed() {
        dismissAnimationRunning = false;
        cancelPopupAnimator();
        clearPopupUiState();
    }

    private void clearPopupUiState() {
        dismissAnimationRunning = false;
        if (currentPalette != null) {
            popupBackgroundView.setBackground(
                    buildPopupBackgroundDrawable(popupBackgroundView.getContext(), currentPalette, false));
        }
        clearAnchorHighlight();
        resetPopupVisualState();
    }

    private void resetPopupVisualState() {
        popupRootView.setAlpha(1f);
        popupRootView.setScaleX(1f);
        popupRootView.setScaleY(1f);
        popupRootView.setTranslationY(0f);
    }

    private void applyAnchorHighlight(TextView anchor) {
        if (anchor == null) {
            return;
        }
        Context context = anchor.getContext();
        if (!originalAnchorBackgroundCaptured) {
            originalAnchorBackground = anchor.getBackground();
            originalAnchorPadding = captureAnchorPadding(anchor);
            originalAnchorBackgroundCaptured = true;
        }
        GradientDrawable capsule = new GradientDrawable();
        capsule.setShape(GradientDrawable.RECTANGLE);
        capsule.setColor(resolveAnchorHighlightFillColor(anchor.getCurrentTextColor()));
        capsule.setCornerRadius(Math.max(anchor.getHeight(), dp(context, 18)));
        capsule.setStroke(
                Math.max(1, dp(context, 1)),
                resolveAnchorHighlightStrokeColor(anchor.getCurrentTextColor()));
        InsetDrawable highlight = new InsetDrawable(
                capsule,
                dp(context, CLOCK_HIGHLIGHT_HORIZONTAL_INSET_DP),
                dp(context, CLOCK_HIGHLIGHT_VERTICAL_INSET_DP),
                dp(context, CLOCK_HIGHLIGHT_HORIZONTAL_INSET_DP),
                dp(context, CLOCK_HIGHLIGHT_VERTICAL_INSET_DP));
        anchor.setBackground(highlight);
        restoreAnchorPadding(anchor);
        anchorHighlighted = true;
        anchor.invalidate();
    }

    private void clearAnchorHighlight() {
        TextView anchor = getAnchor();
        if (anchor != null && anchorHighlighted) {
            anchor.setBackground(originalAnchorBackground);
            restoreAnchorPadding(anchor);
            anchor.invalidate();
        }
        anchorHighlighted = false;
        originalAnchorBackground = null;
        originalAnchorPadding = null;
        originalAnchorBackgroundCaptured = false;
    }

    private static int[] captureAnchorPadding(TextView anchor) {
        if (anchor == null) {
            return null;
        }
        return new int[]{
                anchor.getPaddingStart(),
                anchor.getPaddingTop(),
                anchor.getPaddingEnd(),
                anchor.getPaddingBottom()
        };
    }

    private void restoreAnchorPadding(TextView anchor) {
        if (anchor == null || originalAnchorPadding == null || originalAnchorPadding.length < 4) {
            return;
        }
        anchor.setPaddingRelative(
                originalAnchorPadding[0],
                originalAnchorPadding[1],
                originalAnchorPadding[2],
                originalAnchorPadding[3]);
    }

    private void applyPopupWindowBlurBehind(View popupContentRoot, int blurRadiusPx) {
        if (popupContentRoot == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        View decorView = popupContentRoot.getRootView();
        if (decorView == null) {
            return;
        }
        Object layoutParamsObject = decorView.getLayoutParams();
        if (!(layoutParamsObject instanceof WindowManager.LayoutParams)) {
            return;
        }
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) layoutParamsObject;
        params.flags |= WINDOW_FLAG_BLUR_BEHIND;
        try {
            params.setBlurBehindRadius(Math.max(0, blurRadiusPx));
        } catch (Throwable ignored) {
            return;
        }
        Object windowManager = popupContentRoot.getContext().getSystemService(Context.WINDOW_SERVICE);
        if (!(windowManager instanceof WindowManager)) {
            return;
        }
        try {
            ((WindowManager) windowManager).updateViewLayout(decorView, params);
        } catch (Throwable ignored) {
        }
    }

    private Drawable tryCreateNativePopupBlurDrawable(View targetView, Palette palette) {
        if (targetView == null || palette == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        Object viewRoot = FlymeStatusBarSizer.invokeNoArgCompat(targetView, "getViewRootImpl");
        if (viewRoot == null) {
            return null;
        }
        Object blurDrawable = FlymeStatusBarSizer.invokeNoArgCompat(
                viewRoot,
                "createBackgroundBlurDrawable");
        if (!(blurDrawable instanceof Drawable)) {
            return null;
        }
        Context context = targetView.getContext();
        FlymeStatusBarSizer.invokeMethodCompat(
                blurDrawable,
                "setCornerRadius",
                new Class[]{float.class},
                (float) dp(context, POPUP_SURFACE_RADIUS_DP));
        FlymeStatusBarSizer.invokeMethodCompat(
                blurDrawable,
                "setBlurRadius",
                new Class[]{int.class},
                dp(context, POPUP_BACKGROUND_BLUR_RADIUS_DP));
        FlymeStatusBarSizer.invokeMethodCompat(
                blurDrawable,
                "setZAdjustment",
                new Class[]{int.class},
                POPUP_BACKGROUND_BLUR_Z_ORDER_BOTTOM);
        FlymeStatusBarSizer.invokeMethodCompat(
                blurDrawable,
                "setColor",
                new Class[]{int.class},
                adjustAlpha(palette.surfaceColor, 0.52f));
        return (Drawable) blurDrawable;
    }

    private static int resolveAnchorHighlightFillColor(int anchorTextColor) {
        return isLightForeground(anchorTextColor)
                ? Color.parseColor("#C97515")
                : Color.parseColor("#F6A623");
    }

    private static int resolveAnchorHighlightStrokeColor(int anchorTextColor) {
        return isLightForeground(anchorTextColor)
                ? Color.parseColor("#FFD796")
                : Color.parseColor("#FFF2CF");
    }

    private static void performClockHaptic(View view) {
        if (view == null) {
            return;
        }
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        } catch (Throwable ignored) {
        }
    }

    private Palette resolvePalette() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            return new Palette(
                    Color.parseColor("#FCFDFE"),
                    Color.parseColor("#D6DCE8"),
                    Color.parseColor("#191C1E"),
                    Color.parseColor("#56606C"),
                    Color.parseColor("#005CAE"));
        }
        return isLightForeground(anchor.getCurrentTextColor())
                ? new Palette(
                        Color.parseColor("#20262C"),
                        Color.parseColor("#4F5966"),
                        Color.parseColor("#F5F8FB"),
                        Color.parseColor("#C7D0DA"),
                        Color.parseColor("#7DB7FF"))
                : new Palette(
                        Color.parseColor("#FCFDFE"),
                        Color.parseColor("#D6DCE8"),
                        Color.parseColor("#191C1E"),
                        Color.parseColor("#56606C"),
                        Color.parseColor("#005CAE"));
    }

    private static boolean isLightForeground(int color) {
        double red = Color.red(color) / 255.0d;
        double green = Color.green(color) / 255.0d;
        double blue = Color.blue(color) / 255.0d;
        double luminance = (0.2126d * linearize(red))
                + (0.7152d * linearize(green))
                + (0.0722d * linearize(blue));
        return luminance > 0.6d;
    }

    private static double linearize(double value) {
        return value <= 0.03928d ? value / 12.92d : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private Locale resolveLocale() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            return Locale.getDefault();
        }
        try {
            Locale locale = anchor.getResources().getConfiguration().locale;
            if (locale != null) {
                return locale;
            }
        } catch (Throwable ignored) {
        }
        return Locale.getDefault();
    }

    private TimeZone resolveTimeZone() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            return TimeZone.getDefault();
        }
        Object calendar = FlymeStatusBarSizer.getFieldCompat(anchor, "mCalendar");
        if (calendar instanceof Calendar) {
            TimeZone timeZone = ((Calendar) calendar).getTimeZone();
            if (timeZone != null) {
                return timeZone;
            }
        }
        return TimeZone.getDefault();
    }

    private static FrameLayout buildPopupRootView(
            Context context,
            View backgroundView,
            LinearLayout contentView) {
        FrameLayout root = new FrameLayout(context);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        int shadowPadding = dp(context, POPUP_SHADOW_PADDING_DP);
        root.setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding);
        root.addView(backgroundView, frameMatchParent());
        root.addView(contentView, frameMatchWidth());
        return root;
    }

    private static View buildPopupBackgroundView(Context context) {
        View background = new View(context);
        background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return background;
    }

    private static LinearLayout buildContentView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(
                dp(context, 18),
                dp(context, 14),
                dp(context, 18),
                dp(context, 14));
        return root;
    }

    private static LinearLayout buildTimeRowView(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        row.setBaselineAligned(true);
        return row;
    }

    private static TextView buildTimeView(Context context) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setClickable(true);
        view.setGravity(Gravity.CENTER);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.01f);
        return view;
    }

    private static TextView buildMillisecondsView(Context context) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setClickable(true);
        view.setGravity(Gravity.BOTTOM);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.01f);
        return view;
    }

    private boolean handleTimeTextTouch(View view, MotionEvent event) {
        if (!(view instanceof TextView) || event == null) {
            return false;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return false;
        }
        if (!popupWindow.isShowing()) {
            return false;
        }
        boolean tappedMilliseconds = view == millisecondsView;
        if (showMilliseconds != tappedMilliseconds) {
            return false;
        }
        ((TextView) view).performClick();
        toggleMillisecondsVisibility();
        return true;
    }

    private void toggleMillisecondsVisibility() {
        showMilliseconds = !showMilliseconds;
        ensureFormatters();
        long nowMillis = System.currentTimeMillis();
        refreshTimeText(nowMillis, true);
        refreshDateTextIfNeeded(nowMillis, false);
        if (popupWindow.isShowing()) {
            measureContent();
            updatePopupPosition();
        }
        scheduleRefresh();
        scheduleAutoDismiss();
    }

    private static TextView buildDateView(Context context) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return view;
    }

    private void updateSystemStatusViews(ClockDetailSystemStatusSnapshot snapshot) {
        ClockDetailSystemStatusSnapshot safeSnapshot = snapshot != null
                ? snapshot
                : ClockDetailSystemStatusSnapshot.EMPTY;
        updateMemoryTileRows(memoryTile, safeSnapshot.memoryRows);
        setTextIfChanged(thermalPowerTile.valueView, buildThermalPowerValue(safeSnapshot));
    }

    private void requestThermalPowerRefresh() {
        if (thermalPowerQueryInFlight) {
            return;
        }
        thermalPowerQueryInFlight = true;
        systemStatusProvider.requestThermalPower(handler, (temperatureValue, powerValue) -> {
            latestSystemStatusSnapshot = latestSystemStatusSnapshot.withThermalPower(
                    temperatureValue,
                    powerValue);
            thermalPowerQueryInFlight = false;
            if (!popupWindow.isShowing()) {
                return;
            }
            updateSystemStatusViews(latestSystemStatusSnapshot);
        });
    }

    private void requestMemoryStatusRefresh() {
        if (memoryQueryInFlight) {
            return;
        }
        memoryQueryInFlight = true;
        systemStatusProvider.requestMemoryRows(handler, memoryRows -> {
            latestSystemStatusSnapshot = latestSystemStatusSnapshot.withMemoryRows(memoryRows);
            memoryQueryInFlight = false;
            if (!popupWindow.isShowing()) {
                return;
            }
            updateSystemStatusViews(latestSystemStatusSnapshot);
        });
    }

    private static void disableTouchModal(PopupWindow popupWindow) {
        if (popupWindow == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popupWindow.setTouchModal(false);
            return;
        }
        try {
            Method method = PopupWindow.class.getDeclaredMethod("setTouchModal", boolean.class);
            method.setAccessible(true);
            method.invoke(popupWindow, false);
        } catch (Throwable ignored) {
        }
    }

    private void applyStatTilePalette(StatTile tile, Palette palette) {
        if (tile == null) {
            return;
        }
        Context context = tile.root.getContext();
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 14));
        background.setColor(mixColors(palette.surfaceColor, palette.strokeColor, 0.22f));
        background.setStroke(Math.max(1, dp(context, 1)), adjustAlpha(palette.strokeColor, 0.9f));
        tile.root.setBackground(background);
        tile.labelView.setTextColor(palette.secondaryTextColor);
        tile.valueView.setTextColor(palette.primaryTextColor);
    }

    private void applyMemoryTilePalette(MemoryStatTile tile, Palette palette) {
        if (tile == null) {
            return;
        }
        Context context = tile.root.getContext();
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 14));
        background.setColor(mixColors(palette.surfaceColor, palette.strokeColor, 0.22f));
        background.setStroke(Math.max(1, dp(context, 1)), adjustAlpha(palette.strokeColor, 0.9f));
        tile.root.setBackground(background);
        tile.labelView.setTextColor(palette.secondaryTextColor);
        for (MemoryStatRowView rowView : tile.rowViews) {
            rowView.nameView.setTextColor(palette.secondaryTextColor);
            rowView.valueView.setTextColor(palette.primaryTextColor);
            rowView.percentView.setTextColor(palette.primaryTextColor);
        }
    }

    private void updateMemoryTileRows(
            MemoryStatTile tile,
            ClockDetailSystemStatusSnapshot.MemoryRow[] rows) {
        if (tile == null) {
            return;
        }
        ClockDetailSystemStatusSnapshot.MemoryRow[] safeRows = rows != null && rows.length > 0
                ? rows
                : ClockDetailSystemStatusSnapshot.EMPTY.memoryRows;
        int count = Math.min(tile.rowViews.length, safeRows.length);
        for (int i = 0; i < tile.rowViews.length; i++) {
            MemoryStatRowView rowView = tile.rowViews[i];
            if (i < count) {
                ClockDetailSystemStatusSnapshot.MemoryRow row = safeRows[i];
                setVisibilityIfChanged(rowView.root, View.VISIBLE);
                setTextIfChanged(rowView.nameView, row.label);
                setTextIfChanged(rowView.valueView, row.value);
                setTextIfChanged(rowView.percentView, row.percent);
            } else {
                setVisibilityIfChanged(rowView.root, View.GONE);
            }
        }
    }

    private String buildThermalPowerValue(ClockDetailSystemStatusSnapshot snapshot) {
        ClockDetailSystemStatusSnapshot safeSnapshot = snapshot != null
                ? snapshot
                : ClockDetailSystemStatusSnapshot.EMPTY;
        return "温度 " + safeSnapshot.temperatureValue
                + "\n"
                + "功率 " + safeSnapshot.powerValue;
    }

    private static LinearLayout buildStatusGrid(
            Context context,
            MemoryStatTile memoryTile,
            StatTile thermalPowerTile) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);

        grid.addView(memoryTile.root, matchWidth());
        grid.addView(thermalPowerTile.root, matchWidthWithTop(context, STATUS_TILE_GAP_DP));
        return grid;
    }

    private static MemoryStatTile buildMemoryStatTile(
            Context context,
            String label,
            ClockDetailSystemStatusSnapshot.MemoryRow[] initialRows) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(dp(context, 64));
        root.setPadding(
                dp(context, 12),
                dp(context, 10),
                dp(context, 12),
                dp(context, 10));

        TextView labelView = new TextView(context);
        labelView.setIncludeFontPadding(false);
        labelView.setSingleLine(true);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        ClockDetailSystemStatusSnapshot.MemoryRow[] rows =
                initialRows != null && initialRows.length > 0
                        ? initialRows
                        : ClockDetailSystemStatusSnapshot.EMPTY.memoryRows;
        LinearLayout rowsContainer = new LinearLayout(context);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);

        MemoryStatRowView[] rowViews = new MemoryStatRowView[rows.length];
        for (int i = 0; i < rows.length; i++) {
            MemoryStatRowView rowView = buildMemoryStatRowView(context);
            rowViews[i] = rowView;
            rowsContainer.addView(
                    rowView.root,
                    i == 0 ? matchWidth() : matchWidthWithTop(context, 5));
        }
        root.addView(labelView, matchWidth());
        root.addView(rowsContainer, matchWidthWithTop(context, 6));

        MemoryStatTile tile = new MemoryStatTile(root, labelView, rowViews);
        ClockDetailSystemStatusSnapshot.MemoryRow[] safeRows = rows;
        for (int i = 0; i < tile.rowViews.length; i++) {
            MemoryStatRowView rowView = tile.rowViews[i];
            ClockDetailSystemStatusSnapshot.MemoryRow row = safeRows[i];
            rowView.nameView.setText(row.label);
            rowView.valueView.setText(row.value);
            rowView.percentView.setText(row.percent);
        }
        return tile;
    }

    private static MemoryStatRowView buildMemoryStatRowView(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(context);
        nameView.setIncludeFontPadding(false);
        nameView.setSingleLine(true);
        nameView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        nameView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView valueView = new TextView(context);
        valueView.setIncludeFontPadding(false);
        valueView.setSingleLine(true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        valueView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        TextView percentView = new TextView(context);
        percentView.setIncludeFontPadding(false);
        percentView.setSingleLine(true);
        percentView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        percentView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        percentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        percentView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        row.addView(nameView, weightCell(0.8f));
        row.addView(valueView, weightCellWithStart(context, 8, 1.4f));
        row.addView(percentView, weightCellWithStart(context, 8, 0.8f));
        return new MemoryStatRowView(row, nameView, valueView, percentView);
    }

    private static StatTile buildStatTile(Context context, String label) {
        return buildStatTile(context, label, false);
    }

    private static StatTile buildStatTile(
            Context context,
            String label,
            boolean multiLineValue) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(dp(context, 64));
        root.setPadding(
                dp(context, 12),
                dp(context, 10),
                dp(context, 12),
                dp(context, 10));

        TextView labelView = new TextView(context);
        labelView.setIncludeFontPadding(false);
        labelView.setSingleLine(true);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView valueView = new TextView(context);
        valueView.setIncludeFontPadding(false);
        valueView.setSingleLine(!multiLineValue);
        if (multiLineValue) {
            valueView.setMaxLines(4);
            valueView.setLineSpacing(0f, 1.08f);
        }
        valueView.setText("--");
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        valueView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        root.addView(labelView, matchWidth());
        root.addView(valueView, matchWidthWithTop(context, 4));
        return new StatTile(root, labelView, valueView);
    }

    private static FrameLayout.LayoutParams frameMatchWidth() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
    }

    private static FrameLayout.LayoutParams frameMatchParent() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams matchWidthWithTop(
            Context context,
            int topMarginDp) {
        LinearLayout.LayoutParams params = matchWidth();
        params.topMargin = dp(context, topMarginDp);
        return params;
    }

    private static LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapContentWithStart(
            Context context,
            int startMarginDp) {
        LinearLayout.LayoutParams params = wrapContent();
        params.leftMargin = dp(context, startMarginDp);
        return params;
    }

    private static LinearLayout.LayoutParams weightCell(float weight) {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight);
    }

    private static LinearLayout.LayoutParams weightCellWithStart(
            Context context,
            int startMarginDp,
            float weight) {
        LinearLayout.LayoutParams params = weightCell(weight);
        params.leftMargin = dp(context, startMarginDp);
        return params;
    }

    private static int adjustAlpha(int color, float factor) {
        int alpha = Math.min(255, Math.max(0, Math.round(Color.alpha(color) * factor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int mixColors(int startColor, int endColor, float amount) {
        float ratio = Math.max(0f, Math.min(1f, amount));
        int alpha = Math.round(Color.alpha(startColor) * (1f - ratio) + Color.alpha(endColor) * ratio);
        int red = Math.round(Color.red(startColor) * (1f - ratio) + Color.red(endColor) * ratio);
        int green = Math.round(Color.green(startColor) * (1f - ratio) + Color.green(endColor) * ratio);
        int blue = Math.round(Color.blue(startColor) * (1f - ratio) + Color.blue(endColor) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static boolean setTextIfChanged(TextView view, CharSequence text) {
        if (view == null) {
            return false;
        }
        CharSequence target = text != null ? text : "";
        CharSequence current = view.getText();
        if (current != null && current.toString().contentEquals(target)) {
            return false;
        }
        view.setText(target);
        return true;
    }

    private static boolean setVisibilityIfChanged(View view, int visibility) {
        if (view == null || view.getVisibility() == visibility) {
            return false;
        }
        view.setVisibility(visibility);
        return true;
    }

    private TextView getAnchor() {
        return anchorRef.get();
    }

    private static final class MemoryStatTile {
        final LinearLayout root;
        final TextView labelView;
        final MemoryStatRowView[] rowViews;

        MemoryStatTile(
                LinearLayout root,
                TextView labelView,
                MemoryStatRowView[] rowViews) {
            this.root = root;
            this.labelView = labelView;
            this.rowViews = rowViews;
        }
    }

    private static final class MemoryStatRowView {
        final LinearLayout root;
        final TextView nameView;
        final TextView valueView;
        final TextView percentView;

        MemoryStatRowView(
                LinearLayout root,
                TextView nameView,
                TextView valueView,
                TextView percentView) {
            this.root = root;
            this.nameView = nameView;
            this.valueView = valueView;
            this.percentView = percentView;
        }
    }

    private static final class StatTile {
        final LinearLayout root;
        final TextView labelView;
        final TextView valueView;

        StatTile(LinearLayout root, TextView labelView, TextView valueView) {
            this.root = root;
            this.labelView = labelView;
            this.valueView = valueView;
        }
    }

    private static final class Palette {
        final int surfaceColor;
        final int strokeColor;
        final int primaryTextColor;
        final int secondaryTextColor;
        final int accentColor;

        Palette(
                int surfaceColor,
                int strokeColor,
                int primaryTextColor,
                int secondaryTextColor,
                int accentColor) {
            this.surfaceColor = surfaceColor;
            this.strokeColor = strokeColor;
            this.primaryTextColor = primaryTextColor;
            this.secondaryTextColor = secondaryTextColor;
            this.accentColor = accentColor;
        }
    }
}
