package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class ClockDetailPopupController {
    private static final long AUTO_DISMISS_DELAY_MS = 8000L;
    private static final long MILLIS_REFRESH_INTERVAL_MS = 33L;
    private static final long SECOND_REFRESH_INTERVAL_MS = 1000L;
    private static final long THERMAL_POWER_REFRESH_INTERVAL_MS = 2000L;
    private static final long MEMORY_REFRESH_INTERVAL_MS = 10000L;
    private static final long POPUP_EXPAND_DURATION_MS = 320L;
    private static final long POPUP_COLLAPSE_DURATION_MS = 220L;
    private static final int HORIZONTAL_MARGIN_DP = 16;
    private static final int STATUS_TILE_GAP_DP = 8;
    private static final int RECENT_APPS_TOP_MARGIN_DP = 10;
    private static final int RECENT_APP_ICON_SIZE_DP = 32;
    private static final int RECENT_APP_ITEM_SIZE_DP = 40;
    private static final int RECENT_APP_ICON_PADDING_DP = 4;
    private static final int RECENT_APP_GAP_DP = 10;
    private static final int POPUP_SURFACE_OFFSET_Y_DP = 8;
    private static final int POPUP_SURFACE_RADIUS_DP = 24;
    private static final int POPUP_SHADOW_PADDING_DP = 10;
    private static final int POPUP_SHADOW_ELEVATION_DP = 26;
    private static final int POPUP_SHADOW_TRANSLATION_Z_DP = 6;
    private static final int POPUP_HEADER_MIN_HEIGHT_DP = 56;
    private static final int POPUP_BACKGROUND_BLUR_RADIUS_DP = 32;
    private static final int POPUP_BACKGROUND_BLUR_Z_ORDER_BOTTOM = -1;
    private static final int INVALID_POPUP_SESSION_ID = -1;
    private static final int INTERNAL_WINDOW_TYPE_UNSET = 0;
    private static final int INTERNAL_WINDOW_TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int INTERNAL_WINDOW_TYPE_NOTIFICATION_SHADE = 2040;
    private static final int INTERNAL_WINDOW_TYPE_STATUS_BAR_ADDITIONAL = 2041;
    private static final int INTERNAL_WINDOW_PRIVATE_FLAG_TRUSTED_OVERLAY = 16777216;
    private static final int[] INTERNAL_WINDOW_TYPE_CANDIDATES = new int[]{
            INTERNAL_WINDOW_TYPE_STATUS_BAR_ADDITIONAL,
            INTERNAL_WINDOW_TYPE_STATUS_BAR_SUB_PANEL,
            INTERNAL_WINDOW_TYPE_NOTIFICATION_SHADE
    };
    private static final String INTERNAL_WINDOW_TITLE = "ClockDetailPanel";
    private static final int CLOCK_HIGHLIGHT_VERTICAL_INSET_DP = 1;
    private static final int CLOCK_HIGHLIGHT_HORIZONTAL_INSET_DP = 0;
    private static final int CLOCK_HIGHLIGHT_EXTRA_START_PADDING_DP = 6;
    private static final int CLOCK_HIGHLIGHT_EXTRA_END_PADDING_DP = 10;
    private static final int CLOCK_HIGHLIGHT_EXTRA_VERTICAL_PADDING_DP = 2;
    private static final String[] MILLISECOND_TEXT_CACHE = buildMillisecondTextCache();
    private static final OvershootInterpolator POPUP_SCALE_IN_INTERPOLATOR =
            new OvershootInterpolator(0.72f);
    private static final PathInterpolator POPUP_ALPHA_IN_INTERPOLATOR =
            new PathInterpolator(0.18f, 0f, 0.12f, 1f);
    private static final PathInterpolator POPUP_TRANSLATION_IN_INTERPOLATOR =
            new PathInterpolator(0.16f, 1f, 0.28f, 1f);
    private static final PathInterpolator POPUP_OUT_INTERPOLATOR =
            new PathInterpolator(0.4f, 0f, 0.82f, 0.72f);
    private static int cachedInternalWindowType = INTERNAL_WINDOW_TYPE_UNSET;
    private static boolean trustedOverlayMethodResolved;
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;
    private static Field trustedOverlayPrivateFlagsField;

    private final WeakReference<TextView> anchorRef;
    private final Handler handler;
    private final FrameLayout overlayView;
    private final FrameLayout popupRootView;
    private final View popupBackgroundView;
    private final LinearLayout contentView;
    private final FrameLayout headerView;
    private final LinearLayout timeRowView;
    private final TextView timeView;
    private final TextView millisecondsView;
    private final TextView pinToggleView;
    private final TextView dateView;
    private final LinearLayout statusGridView;
    private final MemoryStatTile memoryTile;
    private final BatteryInfoTile batteryInfoTile;
    private final RecentAppsStrip recentAppsStrip;
    private final ClockDetailSystemStatusProvider systemStatusProvider;
    private final ClockDetailRecentAppsProvider recentAppsProvider;
    private final ActivityManager activityManager;
    private final Runnable refreshRunnable = this::refreshVisibleContent;
    private final Runnable thermalPowerRefreshRunnable = this::refreshVisibleThermalPowerStatus;
    private final Runnable memoryRefreshRunnable = this::refreshVisibleMemoryStatus;
    private final Runnable autoDismissRunnable = this::dismiss;
    private final Runnable panelLongPressRunnable = this::handlePanelLongPressTimeout;
    private final int dragTouchSlop;
    private final Date reusableDate = new Date();

    private boolean enabled;
    private boolean showMilliseconds = false;
    private boolean thermalPowerQueryInFlight;
    private boolean memoryQueryInFlight;
    private boolean recentAppsQueryInFlight;
    private boolean dismissAnimationRunning;
    private boolean enterAnimationRunning;
    private boolean popupTargetShowing;
    private boolean overlayAttached;
    private boolean popupLayoutUpdatePending;
    private boolean panelPinned;
    private boolean manualPositionActive;
    private boolean panelTouchActive;
    private boolean panelLongPressTriggered;
    private boolean dragGestureActive;
    private Palette currentPalette;
    private int popupSessionId = INVALID_POPUP_SESSION_ID;
    private int nextPopupSessionId;
    private int thermalPowerRequestSessionId = INVALID_POPUP_SESSION_ID;
    private int memoryRequestSessionId = INVALID_POPUP_SESSION_ID;
    private int recentAppsRequestSessionId = INVALID_POPUP_SESSION_ID;
    private ClockDetailSystemStatusSnapshot.MemoryRow[] latestMemoryRows =
            ClockDetailSystemStatusSnapshot.EMPTY.memoryRows;
    private String latestTemperatureValue = ClockDetailSystemStatusSnapshot.EMPTY.temperatureValue;
    private String latestPowerValue = ClockDetailSystemStatusSnapshot.EMPTY.powerValue;
    private String latestRemainingCapacityValue =
            ClockDetailSystemStatusSnapshot.EMPTY.remainingCapacityValue;
    private String latestEstimatedFullCapacityValue =
            ClockDetailSystemStatusSnapshot.EMPTY.estimatedFullCapacityValue;
    private ClockDetailRecentApp[] latestRecentApps = ClockDetailRecentApp.EMPTY_ARRAY;
    private Locale cachedLocale;
    private TimeZone cachedTimeZone;
    private boolean cached24HourMode;
    private SimpleDateFormat timeFormatter;
    private SimpleDateFormat dateFormatter;
    private Calendar reusableDateKeyCalendar;
    private long lastFormatterValidationSecond = Long.MIN_VALUE;
    private long lastRenderedSecond = Long.MIN_VALUE;
    private int lastRenderedMillisBucket = Integer.MIN_VALUE;
    private long lastDateRefreshSecond = Long.MIN_VALUE;
    private long lastRenderedDateKey = Long.MIN_VALUE;
    private int popupWidth;
    private int popupHeight;
    private int popupLeft;
    private int popupTop;
    private Animator popupAnimator;
    private Drawable originalAnchorBackground;
    private int[] originalAnchorPadding;
    private boolean originalAnchorBackgroundCaptured;
    private boolean anchorHighlighted;
    private WindowManager overlayWindowManager;
    private float panelTouchDownRawX;
    private float panelTouchDownRawY;
    private int dragStartPopupLeft;
    private int dragStartPopupTop;
    private boolean nativePopupBlurCapabilityResolved;
    private boolean nativePopupBlurSupported;
    private boolean nativePopupBlurCheckPending;
    private boolean overlayInsetsListenerAttached;
    private boolean overlayInsetsReflectionResolved;
    private Object overlayInsetsListener;
    private Method addOverlayInsetsListenerMethod;
    private Method removeOverlayInsetsListenerMethod;
    private Method overlayInsetsSetTouchableInsetsMethod;
    private Field overlayInsetsTouchableRegionField;
    private int overlayTouchableInsetsRegionValue;

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
        this.overlayView = buildOverlayView(context, popupRootView);
        this.headerView = buildHeaderView(context);
        this.timeRowView = buildTimeRowView(context);
        this.timeView = buildTimeView(context);
        this.millisecondsView = buildMillisecondsView(context);
        this.pinToggleView = buildPinToggleView(context);
        this.dateView = buildDateView(context);
        this.memoryTile = buildMemoryStatTile(
                context,
                "系统内存",
                ClockDetailSystemStatusSnapshot.EMPTY.memoryRows);
        this.batteryInfoTile = buildBatteryInfoTile(context);
        this.statusGridView = buildStatusGrid(context, memoryTile, batteryInfoTile);
        this.recentAppsStrip = buildRecentAppsStrip(context);
        this.timeRowView.addView(timeView, wrapContent());
        this.timeRowView.addView(millisecondsView, wrapContentWithStart(context, 2));
        this.headerView.addView(timeRowView, frameCentered());
        this.headerView.addView(pinToggleView, frameTopEnd(context, 2));
        this.contentView.addView(headerView, matchWidth());
        this.contentView.addView(dateView, matchWidthWithTop(context, 5));
        this.contentView.addView(statusGridView, matchWidthWithTop(context, 12));
        this.contentView.addView(
                recentAppsStrip.root,
                matchWidthWithTop(context, RECENT_APPS_TOP_MARGIN_DP));
        this.systemStatusProvider = new ClockDetailSystemStatusProvider(context);
        this.recentAppsProvider = new ClockDetailRecentAppsProvider(context);
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.dragTouchSlop = Math.max(
                dp(context, 4),
                ViewConfiguration.get(context).getScaledTouchSlop());
        timeView.setOnTouchListener(this::handleTimeTextTouch);
        millisecondsView.setOnTouchListener(this::handleTimeTextTouch);
        pinToggleView.setOnClickListener(v -> {
            performClockHaptic(v);
            setPanelPinned(!panelPinned);
        });
        refreshPinToggleView();
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
            if (!isPopupShowing() || dismissAnimationRunning) {
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
        invalidatePopupSession();
        cancelPanelLongPress();
        panelTouchActive = false;
        panelLongPressTriggered = false;
        dragGestureActive = false;
        popupTargetShowing = false;
        if (!isPopupShowing()) {
            clearPopupUiState();
            return;
        }
        if (!animate || !popupRootView.isLaidOut()) {
            cancelPopupAnimator();
            detachOverlay();
            handlePopupDismissed();
            return;
        }
        animatePopupOut();
    }

    private void toggle() {
        if (!enabled) {
            return;
        }
        if (isPopupShowing() && popupTargetShowing) {
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
        boolean wasShowing = isPopupShowing();
        if (!attachOverlay(anchor)) {
            return;
        }
        popupTargetShowing = true;
        dismissAnimationRunning = false;
        popupLayoutUpdatePending = false;
        if (!wasShowing) {
            resetTransientPopupState();
        }
        startPopupSession();
        FlymeStatusBarSizer.disableAncestorClipping(anchor, 6);
        applyPalette(resolvePalette());
        refreshPinToggleView();
        showMilliseconds = false;
        long nowMillis = System.currentTimeMillis();
        ensureFormattersForTimestamp(nowMillis, true);
        refreshTimeText(nowMillis, true);
        refreshDateTextIfNeeded(nowMillis, true);
        applyLatestSystemStatusViews();
        applyLatestRecentAppsView();
        measureContent();
        applyAnchorHighlight(anchor);
        updatePopupPosition();
        preparePopupEnterVisualState(anchor);
        installNativePopupBlurIfPossible();
        requestThermalPowerRefresh();
        requestMemoryStatusRefresh();
        requestRecentAppsRefresh();
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
        if (!isPopupShowing() || !anchor.isAttachedToWindow()) {
            dismissImmediately();
            return;
        }
        long nowMillis = System.currentTimeMillis();
        boolean formattersChanged = ensureFormattersForTimestamp(nowMillis, false);
        refreshTimeText(nowMillis, formattersChanged);
        boolean dateChanged = refreshDateTextIfNeeded(nowMillis, formattersChanged);
        if (formattersChanged || dateChanged) {
            requestPopupLayoutRefresh();
        }
        scheduleRefresh();
    }

    private boolean ensureFormattersForTimestamp(long nowMillis, boolean forceCheck) {
        long validationSecond = nowMillis / SECOND_REFRESH_INTERVAL_MS;
        if (!forceCheck && validationSecond == lastFormatterValidationSecond) {
            return false;
        }
        lastFormatterValidationSecond = validationSecond;
        return ensureFormatters();
    }

    private void refreshVisibleThermalPowerStatus() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismissImmediately();
            return;
        }
        if (!isPopupShowing() || !anchor.isAttachedToWindow()) {
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
        if (!isPopupShowing() || !anchor.isAttachedToWindow()) {
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
        reusableDateKeyCalendar = Calendar.getInstance(timeZone, locale);
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
            reusableDate.setTime(nowMillis);
            String baseTime = timeFormatter != null
                    ? timeFormatter.format(reusableDate)
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
        reusableDate.setTime(nowMillis);
        String dateText = dateFormatter != null
                ? dateFormatter.format(reusableDate)
                : "";
        boolean changed = setTextIfChanged(dateView, dateText);
        lastRenderedDateKey = dateKey;
        return changed;
    }

    private long resolveDateKey(long nowMillis) {
        Calendar calendar = reusableDateKeyCalendar;
        if (calendar == null) {
            calendar = Calendar.getInstance(
                    cachedTimeZone != null ? cachedTimeZone : TimeZone.getDefault(),
                    cachedLocale != null ? cachedLocale : Locale.getDefault());
            reusableDateKeyCalendar = calendar;
        }
        calendar.setTimeInMillis(nowMillis);
        return (calendar.get(Calendar.YEAR) * 1000L) + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private String formatMilliseconds(long nowMillis) {
        int milliseconds = (int) (nowMillis % SECOND_REFRESH_INTERVAL_MS);
        return MILLISECOND_TEXT_CACHE[milliseconds];
    }

    private void scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable);
        if (!isPopupShowing()) {
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
        if (!isPopupShowing()) {
            return;
        }
        handler.postAtTime(
                thermalPowerRefreshRunnable,
                SystemClock.uptimeMillis() + THERMAL_POWER_REFRESH_INTERVAL_MS);
    }

    private void scheduleMemoryRefresh() {
        handler.removeCallbacks(memoryRefreshRunnable);
        if (!isPopupShowing()) {
            return;
        }
        handler.postAtTime(
                memoryRefreshRunnable,
                SystemClock.uptimeMillis() + MEMORY_REFRESH_INTERVAL_MS);
    }

    private void scheduleAutoDismiss() {
        handler.removeCallbacks(autoDismissRunnable);
        if (!isPopupShowing() || AUTO_DISMISS_DELAY_MS <= 0L || panelPinned) {
            return;
        }
        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DELAY_MS);
    }

    private void requestPopupLayoutRefresh() {
        if (!isPopupShowing()) {
            return;
        }
        measureContent();
        if (enterAnimationRunning) {
            popupLayoutUpdatePending = true;
            return;
        }
        popupLayoutUpdatePending = false;
        updatePopupPosition();
    }

    private void updatePopupPosition() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismissImmediately();
            return;
        }
        if (!isPopupShowing() || dismissAnimationRunning) {
            return;
        }
        applyPopupPosition(
                resolveTargetPopupLeft(anchor),
                resolveTargetPopupTop(anchor));
        updatePopupAnimationPivot(anchor);
    }

    private void measureContent() {
        Context context = popupRootView.getContext();
        int margin = dp(context, HORIZONTAL_MARGIN_DP);
        int rootHorizontalPadding = popupRootView.getPaddingLeft() + popupRootView.getPaddingRight();
        int rootVerticalPadding = popupRootView.getPaddingTop() + popupRootView.getPaddingBottom();
        int popupMaxWidth = Math.max(1, getOverlayWidth() - (margin * 2));
        int contentWidth = Math.max(1, popupMaxWidth - rootHorizontalPadding);
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popupWidth = Math.max(1, contentView.getMeasuredWidth() + rootHorizontalPadding);
        popupHeight = Math.max(1, contentView.getMeasuredHeight() + rootVerticalPadding);
        popupRootView.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(popupHeight, View.MeasureSpec.EXACTLY));
    }

    private int resolveTargetPopupLeft(TextView anchor) {
        return manualPositionActive
                ? clampPopupLeft(popupLeft)
                : calculateAnchoredPopupLeft(anchor);
    }

    private int resolveTargetPopupTop(TextView anchor) {
        return manualPositionActive
                ? clampPopupTop(popupTop)
                : calculateAnchoredPopupTop(anchor);
    }

    private int calculateAnchoredPopupLeft(TextView anchor) {
        if (anchor == null) {
            return clampPopupLeft(0);
        }
        int[] anchorLocation = new int[2];
        int[] hostLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        fillOverlayLocationOnScreen(hostLocation);
        return clampPopupLeft(anchorLocation[0] - hostLocation[0]);
    }

    private int calculateAnchoredPopupTop(TextView anchor) {
        if (anchor == null) {
            return clampPopupTop(0);
        }
        int[] anchorLocation = new int[2];
        int[] hostLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        fillOverlayLocationOnScreen(hostLocation);
        int desiredTop = anchorLocation[1]
                - hostLocation[1]
                + anchor.getHeight()
                + dp(anchor.getContext(), POPUP_SURFACE_OFFSET_Y_DP)
                - dp(anchor.getContext(), POPUP_SHADOW_PADDING_DP);
        return clampPopupTop(desiredTop);
    }

    private int clampPopupLeft(int desiredLeft) {
        Context context = popupRootView.getContext();
        int margin = dp(context, HORIZONTAL_MARGIN_DP);
        int maxLeft = Math.max(margin, getOverlayWidth() - margin - popupWidth);
        return Math.max(margin, Math.min(desiredLeft, maxLeft));
    }

    private int clampPopupTop(int desiredTop) {
        Context context = popupRootView.getContext();
        int topInset = overlayView.getPaddingTop();
        int bottomInset = overlayView.getPaddingBottom();
        int hostHeight = getOverlayHeight();
        int minTop = Math.max(0, topInset);
        int maxTop = Math.max(
                minTop,
                hostHeight - bottomInset - popupHeight - dp(context, HORIZONTAL_MARGIN_DP));
        return Math.max(minTop, Math.min(desiredTop, maxTop));
    }

    private void applyPopupPosition(int desiredLeft, int desiredTop) {
        popupLeft = clampPopupLeft(desiredLeft);
        popupTop = clampPopupTop(desiredTop);
        FrameLayout.LayoutParams params = popupRootView.getLayoutParams()
                instanceof FrameLayout.LayoutParams
                ? (FrameLayout.LayoutParams) popupRootView.getLayoutParams()
                : frameWrapContent();
        params.width = popupWidth;
        params.height = popupHeight;
        params.gravity = Gravity.START | Gravity.TOP;
        params.leftMargin = popupLeft;
        params.topMargin = popupTop;
        popupRootView.setLayoutParams(params);
    }

    private void animatePopupIn() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            return;
        }
        runPopupAnimationWhenReady(() -> {
            if (!isPopupShowing() || !popupTargetShowing) {
                return;
            }
            dismissAnimationRunning = false;
            enterAnimationRunning = true;
            updatePopupAnimationPivot(anchor);
            float startScaleX = popupRootView.getScaleX();
            float startScaleY = popupRootView.getScaleY();
            float startAlpha = popupRootView.getAlpha();
            float startTranslationY = popupRootView.getTranslationY();

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(popupRootView, View.SCALE_X, startScaleX, 1f);
            scaleX.setDuration(POPUP_EXPAND_DURATION_MS);
            scaleX.setInterpolator(POPUP_SCALE_IN_INTERPOLATOR);

            ObjectAnimator scaleY = ObjectAnimator.ofFloat(popupRootView, View.SCALE_Y, startScaleY, 1f);
            scaleY.setDuration(POPUP_EXPAND_DURATION_MS);
            scaleY.setInterpolator(POPUP_SCALE_IN_INTERPOLATOR);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(popupRootView, View.ALPHA, startAlpha, 1f);
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
            startPopupAnimation(animator, () -> {
                enterAnimationRunning = false;
                if (popupLayoutUpdatePending && popupTargetShowing) {
                    popupLayoutUpdatePending = false;
                    requestPopupLayoutRefresh();
                } else {
                    popupLayoutUpdatePending = false;
                }
            });
        });
    }

    private void animatePopupOut() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            detachOverlay();
            handlePopupDismissed();
            return;
        }
        dismissAnimationRunning = true;
        enterAnimationRunning = false;
        runPopupAnimationWhenReady(() -> {
            if (!isPopupShowing()) {
                dismissAnimationRunning = false;
                clearPopupUiState();
                return;
            }
            if (popupTargetShowing) {
                dismissAnimationRunning = false;
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
                detachOverlay();
                handlePopupDismissed();
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
        int popupVisualWidth = getPopupVisualWidth();
        if (anchor == null || popupVisualWidth <= 0) {
            return;
        }
        int[] anchorLocation = new int[2];
        int[] hostLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        fillOverlayLocationOnScreen(hostLocation);
        float popupLeftOnScreen = hostLocation[0] + popupLeft;
        float anchorCenterX = anchorLocation[0] + (anchor.getWidth() * 0.5f);
        float shadowPadding = dp(anchor.getContext(), POPUP_SHADOW_PADDING_DP);
        float maxPivot = Math.max(shadowPadding, popupVisualWidth - shadowPadding);
        float pivotX = clamp(anchorCenterX - popupLeftOnScreen, shadowPadding, maxPivot);
        popupRootView.setPivotX(pivotX);
        popupRootView.setPivotY(shadowPadding);
    }

    private float resolveCollapsedScaleX(TextView anchor) {
        int popupVisualWidth = getPopupVisualWidth();
        if (anchor == null || popupVisualWidth <= 0) {
            return 0.32f;
        }
        return clamp(
                anchor.getWidth() / (float) popupVisualWidth,
                0.32f,
                0.82f);
    }

    private float resolveCollapsedScaleY(TextView anchor) {
        int popupVisualHeight = getPopupVisualHeight();
        if (anchor == null || popupVisualHeight <= 0) {
            return 0.18f;
        }
        return clamp(
                anchor.getHeight() / (float) popupVisualHeight,
                0.18f,
                0.56f);
    }

    private void applyPalette(Palette palette) {
        Context context = contentView.getContext();
        currentPalette = palette;
        popupBackgroundView.setBackground(buildPopupBackgroundDrawable(context, palette));
        applyPopupShadowStyle(context, palette);
        timeView.setTextColor(palette.primaryTextColor);
        millisecondsView.setTextColor(palette.accentColor);
        dateView.setTextColor(palette.secondaryTextColor);
        applyPinTogglePalette(context, palette);
        applyMemoryTilePalette(memoryTile, palette);
        applyBatteryInfoTilePalette(batteryInfoTile, palette);
        applyRecentAppsStripPalette(recentAppsStrip, palette);
    }

    private void applyPinTogglePalette(Context context, Palette palette) {
        if (pinToggleView == null || palette == null) {
            return;
        }
        int fillColor = panelPinned
                ? adjustAlpha(palette.accentColor, 0.96f)
                : mixColors(palette.surfaceColor, palette.strokeColor, 0.24f);
        int strokeColor = panelPinned
                ? adjustAlpha(palette.accentColor, 1f)
                : adjustAlpha(palette.strokeColor, 0.9f);
        int textColor = panelPinned
                ? (isLightForeground(fillColor) ? Color.parseColor("#16202B") : Color.WHITE)
                : palette.primaryTextColor;
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 12));
        background.setColor(fillColor);
        background.setStroke(Math.max(1, dp(context, 1)), strokeColor);
        pinToggleView.setBackground(background);
        pinToggleView.setTextColor(textColor);
    }

    private Drawable buildPopupBackgroundDrawable(Context context, Palette palette) {
        return buildPopupSurfaceDrawable(context, palette);
    }

    private Drawable buildPopupBlurBackgroundDrawable(Context context, Palette palette) {
        Drawable nativeBlurDrawable = tryCreateNativePopupBlurDrawable(popupBackgroundView, palette);
        if (nativeBlurDrawable == null) {
            return null;
        }
        return new LayerDrawable(new Drawable[]{
                nativeBlurDrawable,
                buildPopupStrokeDrawable(context, palette)
        });
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
        if (!isPopupShowing() || currentPalette == null) {
            return;
        }
        if (nativePopupBlurCapabilityResolved) {
            if (nativePopupBlurSupported) {
                applyNativePopupBlurBackground();
            }
            return;
        }
        if (nativePopupBlurCheckPending) {
            return;
        }
        nativePopupBlurCheckPending = true;
        popupBackgroundView.post(() -> {
            nativePopupBlurCheckPending = false;
            if (!isPopupShowing() || currentPalette == null) {
                return;
            }
            Drawable blurBackground = buildPopupBlurBackgroundDrawable(
                    popupBackgroundView.getContext(),
                    currentPalette);
            nativePopupBlurCapabilityResolved = true;
            nativePopupBlurSupported = blurBackground != null;
            if (blurBackground != null) {
                popupBackgroundView.setBackground(blurBackground);
            }
        });
    }

    private void applyNativePopupBlurBackground() {
        if (currentPalette == null) {
            return;
        }
        Drawable blurBackground = buildPopupBlurBackgroundDrawable(
                popupBackgroundView.getContext(),
                currentPalette);
        if (blurBackground != null) {
            popupBackgroundView.setBackground(blurBackground);
            return;
        }
        nativePopupBlurCapabilityResolved = false;
        nativePopupBlurSupported = false;
        installNativePopupBlurIfPossible();
    }

    private void handlePopupDismissed() {
        popupTargetShowing = false;
        dismissAnimationRunning = false;
        enterAnimationRunning = false;
        popupLayoutUpdatePending = false;
        cancelPopupAnimator();
        resetTransientPopupState();
        clearPopupUiState();
    }

    private void clearPopupUiState() {
        dismissAnimationRunning = false;
        if (currentPalette != null) {
            popupBackgroundView.setBackground(
                    buildPopupBackgroundDrawable(popupBackgroundView.getContext(), currentPalette));
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

    private void preparePopupEnterVisualState(TextView anchor) {
        if (anchor == null) {
            return;
        }
        updatePopupAnimationPivot(anchor);
        popupRootView.setAlpha(0.16f);
        popupRootView.setScaleX(resolveCollapsedScaleX(anchor));
        popupRootView.setScaleY(resolveCollapsedScaleY(anchor));
        popupRootView.setTranslationY(-dp(anchor.getContext(), 14));
    }

    private void runPopupAnimationWhenReady(Runnable action) {
        if (action == null) {
            return;
        }
        if (popupRootView.isAttachedToWindow() && popupRootView.isLaidOut()) {
            action.run();
            return;
        }
        popupRootView.post(action);
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
        capsule.setCornerRadius(Math.max(
                anchor.getHeight(),
                dp(context, 20)));
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

    private static void performLongPressHaptic(View view) {
        if (view == null) {
            return;
        }
        try {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
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

    private static String sanitizeStatusText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String[] buildMillisecondTextCache() {
        String[] cache = new String[1000];
        char[] chars = new char[]{'.', '0', '0', '0'};
        for (int milliseconds = 0; milliseconds < cache.length; milliseconds++) {
            chars[1] = (char) ('0' + (milliseconds / 100));
            chars[2] = (char) ('0' + ((milliseconds / 10) % 10));
            chars[3] = (char) ('0' + (milliseconds % 10));
            cache[milliseconds] = new String(chars);
        }
        return cache;
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

    private int getOverlayWidth() {
        int width = overlayView.getWidth();
        if (width > 0) {
            return width;
        }
        return popupRootView.getContext().getResources().getDisplayMetrics().widthPixels;
    }

    private int getOverlayHeight() {
        int height = overlayView.getHeight();
        if (height > 0) {
            return height;
        }
        return popupRootView.getContext().getResources().getDisplayMetrics().heightPixels;
    }

    private void fillOverlayLocationOnScreen(int[] location) {
        if (location == null || location.length < 2) {
            return;
        }
        location[0] = 0;
        location[1] = 0;
        if (overlayView.isAttachedToWindow()) {
            overlayView.getLocationOnScreen(location);
        }
    }

    private boolean isPopupShowing() {
        return overlayAttached;
    }

    private boolean attachOverlay(TextView anchor) {
        if (anchor == null) {
            return false;
        }
        if (overlayAttached) {
            if (overlayView.isAttachedToWindow()) {
                return true;
            }
            detachOverlay();
        }
        Object windowManagerObject = anchor.getContext().getSystemService(Context.WINDOW_SERVICE);
        if (!(windowManagerObject instanceof WindowManager)) {
            return false;
        }
        removeOverlayFromViewGroupParent();
        WindowManager windowManager = (WindowManager) windowManagerObject;
        Throwable lastError = null;
        int preferredWindowType = cachedInternalWindowType;
        if (preferredWindowType != INTERNAL_WINDOW_TYPE_UNSET) {
            lastError = tryAttachOverlayWindow(
                    windowManager,
                    anchor.getContext(),
                    preferredWindowType);
            if (overlayAttached) {
                return true;
            }
            if (cachedInternalWindowType == preferredWindowType) {
                cachedInternalWindowType = INTERNAL_WINDOW_TYPE_UNSET;
            }
        }
        for (int windowType : INTERNAL_WINDOW_TYPE_CANDIDATES) {
            if (windowType == preferredWindowType) {
                continue;
            }
            lastError = tryAttachOverlayWindow(
                    windowManager,
                    anchor.getContext(),
                    windowType);
            if (overlayAttached) {
                return true;
            }
        }
        FlymeStatusBarSizer.logClockWarning(
                "Failed to attach clock detail panel internal window",
                lastError);
        overlayWindowManager = null;
        overlayAttached = false;
        return false;
    }

    private Throwable tryAttachOverlayWindow(
            WindowManager windowManager,
            Context context,
            int windowType) {
        WindowManager.LayoutParams params = buildInternalOverlayLayoutParams(context, windowType);
        try {
            windowManager.addView(overlayView, params);
            overlayWindowManager = windowManager;
            overlayAttached = true;
            cachedInternalWindowType = windowType;
            attachOverlayTouchableInsetsListener();
            return null;
        } catch (Throwable throwable) {
            overlayWindowManager = null;
            overlayAttached = false;
            try {
                windowManager.removeViewImmediate(overlayView);
            } catch (Throwable ignored) {
            }
            return throwable;
        }
    }

    private void detachOverlay() {
        detachOverlayTouchableInsetsListener();
        WindowManager windowManager = overlayWindowManager;
        overlayWindowManager = null;
        if (windowManager != null) {
            try {
                windowManager.removeViewImmediate(overlayView);
            } catch (Throwable ignored) {
            }
        }
        removeOverlayFromViewGroupParent();
        overlayAttached = false;
    }

    private void removeOverlayFromViewGroupParent() {
        ViewParent parent = overlayView.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(overlayView);
        }
    }

    private WindowManager.LayoutParams buildInternalOverlayLayoutParams(
            Context context,
            int windowType) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                flags,
                PixelFormat.TRANSLUCENT);
        applyTrustedOverlayFlags(params);
        params.token = new Binder();
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.setTitle(INTERNAL_WINDOW_TITLE);
        params.packageName = context.getPackageName();
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return params;
    }

    private void applyTrustedOverlayFlags(WindowManager.LayoutParams params) {
        if (params == null) {
            return;
        }
        Method setTrustedOverlay = resolveTrustedOverlayMethod();
        if (setTrustedOverlay != null) {
            try {
                setTrustedOverlay.invoke(params);
            } catch (Throwable ignored) {
            }
        }
        Field privateFlagsField = resolveTrustedOverlayPrivateFlagsField();
        if (privateFlagsField == null) {
            return;
        }
        try {
            int currentFlags = privateFlagsField.getInt(params);
            privateFlagsField.setInt(
                    params,
                    currentFlags | INTERNAL_WINDOW_PRIVATE_FLAG_TRUSTED_OVERLAY);
        } catch (Throwable ignored) {
        }
    }

    private static Method resolveTrustedOverlayMethod() {
        if (!trustedOverlayMethodResolved) {
            try {
                trustedOverlayMethod = WindowManager.LayoutParams.class.getMethod("setTrustedOverlay");
            } catch (Throwable ignored) {
                trustedOverlayMethod = null;
            }
            trustedOverlayMethodResolved = true;
        }
        return trustedOverlayMethod;
    }

    private static Field resolveTrustedOverlayPrivateFlagsField() {
        if (!trustedOverlayPrivateFlagsFieldResolved) {
            try {
                trustedOverlayPrivateFlagsField =
                        WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
                trustedOverlayPrivateFlagsField.setAccessible(true);
            } catch (Throwable ignored) {
                trustedOverlayPrivateFlagsField = null;
            }
            trustedOverlayPrivateFlagsFieldResolved = true;
        }
        return trustedOverlayPrivateFlagsField;
    }

    private void resetTransientPopupState() {
        cancelPanelLongPress();
        panelPinned = false;
        manualPositionActive = false;
        panelTouchActive = false;
        panelLongPressTriggered = false;
        dragGestureActive = false;
        popupLeft = 0;
        popupTop = 0;
        dragStartPopupLeft = 0;
        dragStartPopupTop = 0;
        refreshPinToggleView();
    }

    private void startPopupSession() {
        popupSessionId = nextPopupSessionId++;
        thermalPowerQueryInFlight = false;
        memoryQueryInFlight = false;
        recentAppsQueryInFlight = false;
        thermalPowerRequestSessionId = INVALID_POPUP_SESSION_ID;
        memoryRequestSessionId = INVALID_POPUP_SESSION_ID;
        recentAppsRequestSessionId = INVALID_POPUP_SESSION_ID;
    }

    private void invalidatePopupSession() {
        popupSessionId = nextPopupSessionId++;
        thermalPowerQueryInFlight = false;
        memoryQueryInFlight = false;
        recentAppsQueryInFlight = false;
        thermalPowerRequestSessionId = INVALID_POPUP_SESSION_ID;
        memoryRequestSessionId = INVALID_POPUP_SESSION_ID;
        recentAppsRequestSessionId = INVALID_POPUP_SESSION_ID;
    }

    private void cancelPanelLongPress() {
        handler.removeCallbacks(panelLongPressRunnable);
    }

    private void handlePanelLongPressTimeout() {
        if (!panelTouchActive || !isPopupShowing()) {
            return;
        }
        panelLongPressTriggered = true;
        dragGestureActive = true;
        dragStartPopupLeft = popupLeft;
        dragStartPopupTop = popupTop;
        handler.removeCallbacks(autoDismissRunnable);
        performLongPressHaptic(popupRootView);
    }

    private void observePanelTouch(MotionEvent event) {
        if (event == null || !isPopupShowing()) {
            return;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                panelTouchActive = true;
                panelLongPressTriggered = false;
                dragGestureActive = false;
                panelTouchDownRawX = event.getRawX();
                panelTouchDownRawY = event.getRawY();
                dragStartPopupLeft = popupLeft;
                dragStartPopupTop = popupTop;
                cancelPanelLongPress();
                handler.postDelayed(
                        panelLongPressRunnable,
                        ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (!panelTouchActive) {
                    return;
                }
                if (dragGestureActive) {
                    return;
                }
                if (hasDragMovedEnough(event)) {
                    cancelPanelLongPress();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelPanelLongPress();
                break;
            default:
                break;
        }
    }

    private boolean shouldInterceptPanelTouch(MotionEvent event) {
        if (event == null) {
            return false;
        }
        int action = event.getActionMasked();
        if (!panelTouchActive && action != MotionEvent.ACTION_CANCEL) {
            return false;
        }
        return dragGestureActive || panelLongPressTriggered || action == MotionEvent.ACTION_CANCEL;
    }

    private boolean handlePanelTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (dragGestureActive) {
                    updateDraggedPopupPosition(event);
                    return true;
                }
                return panelLongPressTriggered;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean handled = dragGestureActive || panelLongPressTriggered;
                finishPanelTouchGesture();
                return handled;
            default:
                return dragGestureActive || panelLongPressTriggered;
        }
    }

    private void finishPanelTouchGesture() {
        cancelPanelLongPress();
        panelTouchActive = false;
        panelLongPressTriggered = false;
        dragGestureActive = false;
        if (!panelPinned) {
            scheduleAutoDismiss();
        }
    }

    private boolean hasDragMovedEnough(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float dx = event.getRawX() - panelTouchDownRawX;
        float dy = event.getRawY() - panelTouchDownRawY;
        return Math.max(Math.abs(dx), Math.abs(dy)) >= dragTouchSlop;
    }

    private void updateDraggedPopupPosition(MotionEvent event) {
        if (event == null) {
            return;
        }
        int desiredLeft = dragStartPopupLeft + Math.round(event.getRawX() - panelTouchDownRawX);
        int desiredTop = dragStartPopupTop + Math.round(event.getRawY() - panelTouchDownRawY);
        manualPositionActive = true;
        applyPopupPosition(desiredLeft, desiredTop);
    }

    private boolean handleOverlayTouch(MotionEvent event) {
        if (event == null || !isPopupShowing()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_OUTSIDE) {
            if (!panelPinned) {
                dismiss();
            }
            return false;
        }
        if (panelPinned || action != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (isPointInsidePopup(event.getX(), event.getY())) {
            return false;
        }
        dismiss();
        return true;
    }

    private void setPanelPinned(boolean pinned) {
        if (panelPinned == pinned) {
            return;
        }
        panelPinned = pinned;
        if (panelPinned) {
            handler.removeCallbacks(autoDismissRunnable);
        } else {
            scheduleAutoDismiss();
        }
        refreshPinToggleView();
    }

    private void refreshPinToggleView() {
        if (pinToggleView == null) {
            return;
        }
        setTextIfChanged(pinToggleView, panelPinned ? "固定" : "自动");
        pinToggleView.setActivated(panelPinned);
        if (currentPalette != null) {
            applyPinTogglePalette(pinToggleView.getContext(), currentPalette);
        }
    }

    private void attachOverlayTouchableInsetsListener() {
        ensureOverlayInsetsReflectionResolved();
        if (overlayInsetsListener == null || addOverlayInsetsListenerMethod == null) {
            return;
        }
        if (overlayInsetsListenerAttached) {
            return;
        }
        ViewTreeObserver observer = overlayView.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) {
            return;
        }
        try {
            addOverlayInsetsListenerMethod.invoke(observer, overlayInsetsListener);
            overlayInsetsListenerAttached = true;
            overlayView.requestLayout();
        } catch (Throwable ignored) {
        }
    }

    private void detachOverlayTouchableInsetsListener() {
        if (!overlayInsetsListenerAttached) {
            return;
        }
        ensureOverlayInsetsReflectionResolved();
        ViewTreeObserver observer = overlayView.getViewTreeObserver();
        if (observer != null
                && observer.isAlive()
                && overlayInsetsListener != null
                && removeOverlayInsetsListenerMethod != null) {
            try {
                removeOverlayInsetsListenerMethod.invoke(observer, overlayInsetsListener);
            } catch (Throwable ignored) {
            }
        }
        overlayInsetsListenerAttached = false;
    }

    private void ensureOverlayInsetsReflectionResolved() {
        if (overlayInsetsReflectionResolved) {
            return;
        }
        overlayInsetsReflectionResolved = true;
        try {
            Class<?> listenerClass =
                    Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
            Class<?> infoClass =
                    Class.forName("android.view.ViewTreeObserver$InternalInsetsInfo");
            addOverlayInsetsListenerMethod = ViewTreeObserver.class.getDeclaredMethod(
                    "addOnComputeInternalInsetsListener",
                    listenerClass);
            removeOverlayInsetsListenerMethod = ViewTreeObserver.class.getDeclaredMethod(
                    "removeOnComputeInternalInsetsListener",
                    listenerClass);
            addOverlayInsetsListenerMethod.setAccessible(true);
            removeOverlayInsetsListenerMethod.setAccessible(true);
            overlayInsetsSetTouchableInsetsMethod =
                    infoClass.getDeclaredMethod("setTouchableInsets", int.class);
            overlayInsetsSetTouchableInsetsMethod.setAccessible(true);
            overlayInsetsTouchableRegionField = infoClass.getDeclaredField("touchableRegion");
            overlayInsetsTouchableRegionField.setAccessible(true);
            Field touchableInsetsRegionField =
                    infoClass.getDeclaredField("TOUCHABLE_INSETS_REGION");
            touchableInsetsRegionField.setAccessible(true);
            overlayTouchableInsetsRegionValue =
                    touchableInsetsRegionField.getInt(null);
            ClassLoader proxyClassLoader = ClockDetailPopupController.class.getClassLoader();
            overlayInsetsListener = Proxy.newProxyInstance(
                    proxyClassLoader != null ? proxyClassLoader : listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onComputeInternalInsets".equals(method.getName())
                                && args != null
                                && args.length > 0) {
                            applyOverlayTouchableInsetsCompat(args[0]);
                        }
                        return null;
                    });
        } catch (Throwable ignored) {
            overlayInsetsListener = null;
            addOverlayInsetsListenerMethod = null;
            removeOverlayInsetsListenerMethod = null;
            overlayInsetsSetTouchableInsetsMethod = null;
            overlayInsetsTouchableRegionField = null;
        }
    }

    private void applyOverlayTouchableInsetsCompat(Object infoObject) {
        if (infoObject == null
                || overlayInsetsSetTouchableInsetsMethod == null
                || overlayInsetsTouchableRegionField == null) {
            return;
        }
        try {
            overlayInsetsSetTouchableInsetsMethod.invoke(
                    infoObject,
                    overlayTouchableInsetsRegionValue);
            Object regionObject = overlayInsetsTouchableRegionField.get(infoObject);
            if (!(regionObject instanceof Region)) {
                return;
            }
            Region touchableRegion = (Region) regionObject;
            if (!isPopupShowing()
                    || popupRootView.getWidth() <= 0
                    || popupRootView.getHeight() <= 0) {
                touchableRegion.setEmpty();
                return;
            }
            touchableRegion.set(
                    popupRootView.getLeft(),
                    popupRootView.getTop(),
                    popupRootView.getRight(),
                    popupRootView.getBottom());
        } catch (Throwable ignored) {
        }
    }

    private boolean isPointInsidePopup(float x, float y) {
        return x >= popupRootView.getLeft()
                && x <= popupRootView.getRight()
                && y >= popupRootView.getTop()
                && y <= popupRootView.getBottom();
    }

    private FrameLayout buildOverlayView(
            Context context,
            View popupContentView) {
        FrameLayout overlay = new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (handleOverlayTouch(event)) {
                    return true;
                }
                return super.dispatchTouchEvent(event);
            }
        };
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        overlay.addView(popupContentView, frameWrapContent());
        return overlay;
    }

    private FrameLayout buildPopupRootView(
            Context context,
            View backgroundView,
            LinearLayout contentView) {
        FrameLayout root = new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                observePanelTouch(event);
                return super.dispatchTouchEvent(event);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                return shouldInterceptPanelTouch(event) || super.onInterceptTouchEvent(event);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return handlePanelTouchEvent(event) || super.onTouchEvent(event);
            }
        };
        root.setClickable(true);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        int shadowPadding = dp(context, POPUP_SHADOW_PADDING_DP);
        root.setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding);
        root.addView(backgroundView, frameMatchParent());
        root.addView(contentView, frameMatchWidthWrapContent());
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

    private static FrameLayout buildHeaderView(Context context) {
        FrameLayout header = new FrameLayout(context);
        header.setMinimumHeight(dp(context, POPUP_HEADER_MIN_HEIGHT_DP));
        header.setClipChildren(false);
        header.setClipToPadding(false);
        return header;
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

    private static TextView buildPinToggleView(Context context) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setClickable(true);
        view.setGravity(Gravity.CENTER);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setMinHeight(dp(context, 24));
        view.setPadding(
                dp(context, 10),
                dp(context, 5),
                dp(context, 10),
                dp(context, 5));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setText("自动");
        return view;
    }

    private boolean handleTimeTextTouch(View view, MotionEvent event) {
        if (!(view instanceof TextView) || event == null) {
            return false;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return false;
        }
        if (!isPopupShowing()) {
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
        long nowMillis = System.currentTimeMillis();
        ensureFormattersForTimestamp(nowMillis, true);
        refreshTimeText(nowMillis, true);
        refreshDateTextIfNeeded(nowMillis, false);
        requestPopupLayoutRefresh();
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

    private void applyLatestSystemStatusViews() {
        updateMemoryStatusView(latestMemoryRows);
        updateBatteryInfoStatusView(
                latestTemperatureValue,
                latestPowerValue,
                latestRemainingCapacityValue,
                latestEstimatedFullCapacityValue);
    }

    private void applyLatestRecentAppsView() {
        updateRecentAppsView(latestRecentApps);
    }

    private void updateMemoryStatusView(ClockDetailSystemStatusSnapshot.MemoryRow[] rows) {
        updateMemoryTileRows(memoryTile, rows);
    }

    private void updateBatteryInfoStatusView(
            String temperatureValue,
            String powerValue,
            String remainingCapacityValue,
            String estimatedFullCapacityValue) {
        setTextIfChanged(
                batteryInfoTile.leftValueView,
                buildThermalPowerValue(temperatureValue, powerValue));
        setTextIfChanged(
                batteryInfoTile.rightValueView,
                buildBatteryCapacityValue(remainingCapacityValue, estimatedFullCapacityValue));
    }

    private void requestThermalPowerRefresh() {
        if (thermalPowerQueryInFlight) {
            return;
        }
        final int requestSessionId = popupSessionId;
        thermalPowerQueryInFlight = true;
        thermalPowerRequestSessionId = requestSessionId;
        systemStatusProvider.requestBatteryStatus(
                handler,
                (temperatureValue,
                        powerValue,
                        remainingCapacityValue,
                        estimatedFullCapacityValue) -> {
            if (thermalPowerRequestSessionId != requestSessionId) {
                return;
            }
            thermalPowerQueryInFlight = false;
            thermalPowerRequestSessionId = INVALID_POPUP_SESSION_ID;
            latestTemperatureValue = temperatureValue;
            latestPowerValue = powerValue;
            latestRemainingCapacityValue = remainingCapacityValue;
            latestEstimatedFullCapacityValue = estimatedFullCapacityValue;
            if (!isPopupShowing()) {
                return;
            }
            updateBatteryInfoStatusView(
                    latestTemperatureValue,
                    latestPowerValue,
                    latestRemainingCapacityValue,
                    latestEstimatedFullCapacityValue);
        });
    }

    private void requestMemoryStatusRefresh() {
        if (memoryQueryInFlight) {
            return;
        }
        final int requestSessionId = popupSessionId;
        memoryQueryInFlight = true;
        memoryRequestSessionId = requestSessionId;
        systemStatusProvider.requestMemoryRows(handler, memoryRows -> {
            if (memoryRequestSessionId != requestSessionId) {
                return;
            }
            memoryQueryInFlight = false;
            memoryRequestSessionId = INVALID_POPUP_SESSION_ID;
            latestMemoryRows = memoryRows != null && memoryRows.length > 0
                    ? memoryRows
                    : ClockDetailSystemStatusSnapshot.EMPTY.memoryRows;
            if (!isPopupShowing()) {
                return;
            }
            updateMemoryStatusView(latestMemoryRows);
        });
    }

    private void requestRecentAppsRefresh() {
        if (recentAppsQueryInFlight) {
            return;
        }
        final int requestSessionId = popupSessionId;
        recentAppsQueryInFlight = true;
        recentAppsRequestSessionId = requestSessionId;
        recentAppsProvider.requestRecentApps(handler, recentApps -> {
            if (recentAppsRequestSessionId != requestSessionId) {
                return;
            }
            recentAppsQueryInFlight = false;
            recentAppsRequestSessionId = INVALID_POPUP_SESSION_ID;
            latestRecentApps = recentApps != null && recentApps.length > 0
                    ? recentApps
                    : ClockDetailRecentApp.EMPTY_ARRAY;
            if (!isPopupShowing()) {
                return;
            }
            updateRecentAppsView(latestRecentApps);
            requestPopupLayoutRefresh();
        });
    }

    private void applyBatteryInfoTilePalette(BatteryInfoTile tile, Palette palette) {
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
        tile.leftLabelView.setTextColor(palette.secondaryTextColor);
        tile.leftValueView.setTextColor(palette.primaryTextColor);
        tile.rightLabelView.setTextColor(palette.secondaryTextColor);
        tile.rightValueView.setTextColor(palette.primaryTextColor);
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

    private void applyRecentAppsStripPalette(RecentAppsStrip strip, Palette palette) {
        if (strip == null) {
            return;
        }
        Context context = strip.root.getContext();
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 14));
        background.setColor(mixColors(palette.surfaceColor, palette.strokeColor, 0.22f));
        background.setStroke(Math.max(1, dp(context, 1)), adjustAlpha(palette.strokeColor, 0.9f));
        strip.root.setBackground(background);
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

    private void updateRecentAppsView(ClockDetailRecentApp[] recentApps) {
        if (recentAppsStrip == null) {
            return;
        }
        ClockDetailRecentApp[] safeRecentApps = recentApps != null && recentApps.length > 0
                ? recentApps
                : ClockDetailRecentApp.EMPTY_ARRAY;
        boolean hasApps = safeRecentApps.length > 0;
        setVisibilityIfChanged(recentAppsStrip.root, hasApps ? View.VISIBLE : View.GONE);
        recentAppsStrip.contentView.removeAllViews();
        if (!hasApps) {
            recentAppsStrip.scrollView.scrollTo(0, 0);
            return;
        }
        Context context = recentAppsStrip.root.getContext();
        for (int i = 0; i < safeRecentApps.length; i++) {
            ClockDetailRecentApp app = safeRecentApps[i];
            ImageView iconView = buildRecentAppIconView(context);
            iconView.setImageDrawable(app.icon);
            iconView.setContentDescription(app.label);
            iconView.setOnClickListener(v -> {
                performClockHaptic(v);
                launchRecentTask(app);
            });
            recentAppsStrip.contentView.addView(
                    iconView,
                    i == 0
                            ? recentAppItemLayoutParams(context)
                            : recentAppItemLayoutParamsWithStart(context, RECENT_APP_GAP_DP));
        }
        recentAppsStrip.scrollView.scrollTo(0, 0);
    }

    private void launchRecentTask(ClockDetailRecentApp app) {
        if (app == null
                || app.taskId < 0
                || activityManager == null
                || !isPopupShowing()
                || !popupTargetShowing
                || dismissAnimationRunning) {
            return;
        }
        dismiss();
        moveRecentTaskToFront(app.taskId);
    }

    private void moveRecentTaskToFront(int taskId) {
        if (taskId < 0 || activityManager == null) {
            return;
        }
        try {
            activityManager.moveTaskToFront(taskId, 0);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to move recent task to front: " + taskId,
                    t);
        }
    }

    private static String buildThermalPowerValue(String temperatureValue, String powerValue) {
        return sanitizeStatusText(
                temperatureValue,
                ClockDetailSystemStatusSnapshot.EMPTY.temperatureValue)
                + "\n"
                + sanitizeStatusText(
                        powerValue,
                        ClockDetailSystemStatusSnapshot.EMPTY.powerValue);
    }

    private static String buildBatteryCapacityValue(
            String remainingCapacityValue,
            String estimatedFullCapacityValue) {
        String remainingValue = sanitizeStatusText(
                remainingCapacityValue,
                ClockDetailSystemStatusSnapshot.EMPTY.remainingCapacityValue);
        String estimatedValue = sanitizeStatusText(
                estimatedFullCapacityValue,
                ClockDetailSystemStatusSnapshot.EMPTY.estimatedFullCapacityValue);
        return formatBatteryCapacityLine(remainingValue)
                + "\n"
                + formatBatteryCapacityLine(estimatedValue);
    }

    private static String stripBatteryCapacityUnit(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (!trimmed.endsWith("mAh")) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.length() - 3).trim();
    }

    private static boolean isUnavailableBatteryCapacityValue(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "--".equals(trimmed) || "不可用".equals(trimmed);
    }

    private static String formatBatteryCapacityLine(String value) {
        if (isUnavailableBatteryCapacityValue(value)) {
            return "--";
        }
        String numberOnly = stripBatteryCapacityUnit(value);
        if (!numberOnly.equals(value)) {
            return numberOnly + "mAh";
        }
        return value;
    }

    private static LinearLayout buildStatusGrid(
            Context context,
            MemoryStatTile memoryTile,
            BatteryInfoTile batteryInfoTile) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);

        grid.addView(memoryTile.root, matchWidth());
        grid.addView(batteryInfoTile.root, matchWidthWithTop(context, STATUS_TILE_GAP_DP));
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

    private static BatteryInfoTile buildBatteryInfoTile(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(dp(context, 64));
        root.setPadding(
                dp(context, 12),
                dp(context, 10),
                dp(context, 12),
                dp(context, 10));

        LinearLayout leftColumn = buildBatteryInfoColumn(context);
        TextView leftLabelView = buildBatteryInfoLabelView(context, "电池温度 / 功率", true);
        TextView leftValueView = buildBatteryInfoValueView(context);
        leftValueView.setText(buildThermalPowerValue(
                ClockDetailSystemStatusSnapshot.EMPTY.temperatureValue,
                ClockDetailSystemStatusSnapshot.EMPTY.powerValue));
        leftColumn.addView(leftLabelView, matchWidth());
        leftColumn.addView(leftValueView, matchWidthWithTop(context, 4));

        LinearLayout rightColumn = buildBatteryInfoColumn(context);
        TextView rightLabelView = buildBatteryInfoLabelView(
                context,
                "电池容量",
                true);
        TextView rightValueView = buildBatteryInfoValueView(context);
        rightValueView.setText(buildBatteryCapacityValue(
                ClockDetailSystemStatusSnapshot.EMPTY.remainingCapacityValue,
                ClockDetailSystemStatusSnapshot.EMPTY.estimatedFullCapacityValue));
        rightColumn.addView(rightLabelView, matchWidth());
        rightColumn.addView(rightValueView, matchWidthWithTop(context, 4));

        root.addView(leftColumn, weightCell(1.08f));
        root.addView(rightColumn, weightCellWithStart(context, 10, 0.92f));
        return new BatteryInfoTile(
                root,
                leftLabelView,
                leftValueView,
                rightLabelView,
                rightValueView);
    }

    private static LinearLayout buildBatteryInfoColumn(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return column;
    }

    private static TextView buildBatteryInfoLabelView(
            Context context,
            String text,
            boolean singleLine) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setSingleLine(singleLine);
        if (!singleLine) {
            view.setMaxLines(2);
            view.setLineSpacing(0f, 1.08f);
        }
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private static TextView buildBatteryInfoValueView(Context context) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setMaxLines(4);
        view.setLineSpacing(0f, 1.08f);
        view.setText("--");
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return view;
    }

    private static RecentAppsStrip buildRecentAppsStrip(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(
                dp(context, 12),
                dp(context, 10),
                dp(context, 12),
                dp(context, 10));
        root.setVisibility(View.GONE);

        HorizontalScrollView scrollView = new HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setFillViewport(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);

        scrollView.addView(
                content,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, matchWidth());
        return new RecentAppsStrip(root, scrollView, content);
    }

    private static ImageView buildRecentAppIconView(Context context) {
        ImageView iconView = new ImageView(context);
        iconView.setAdjustViewBounds(false);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int maxSize = dp(context, RECENT_APP_ICON_SIZE_DP);
        iconView.setMaxWidth(maxSize);
        iconView.setMaxHeight(maxSize);
        int padding = dp(context, RECENT_APP_ICON_PADDING_DP);
        iconView.setPadding(padding, padding, padding, padding);
        return iconView;
    }

    private static LinearLayout.LayoutParams recentAppItemLayoutParams(Context context) {
        int size = dp(context, RECENT_APP_ITEM_SIZE_DP);
        return new LinearLayout.LayoutParams(size, size);
    }

    private static LinearLayout.LayoutParams recentAppItemLayoutParamsWithStart(
            Context context,
            int startMarginDp) {
        LinearLayout.LayoutParams params = recentAppItemLayoutParams(context);
        params.leftMargin = dp(context, startMarginDp);
        return params;
    }

    private static FrameLayout.LayoutParams frameWrapContent() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.START | Gravity.TOP;
        return params;
    }

    private static FrameLayout.LayoutParams frameCentered() {
        FrameLayout.LayoutParams params = frameWrapContent();
        params.gravity = Gravity.CENTER;
        return params;
    }

    private static FrameLayout.LayoutParams frameTopEnd(Context context, int topMarginDp) {
        FrameLayout.LayoutParams params = frameWrapContent();
        params.gravity = Gravity.TOP | Gravity.END;
        params.topMargin = dp(context, topMarginDp);
        return params;
    }

    private static FrameLayout.LayoutParams frameMatchParent() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams frameMatchWidthWrapContent() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.START | Gravity.TOP;
        return params;
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

    private int getPopupVisualWidth() {
        int width = popupRootView.getWidth();
        if (width > 0) {
            return width;
        }
        width = popupRootView.getMeasuredWidth();
        if (width > 0) {
            return width;
        }
        return popupWidth;
    }

    private int getPopupVisualHeight() {
        int height = popupRootView.getHeight();
        if (height > 0) {
            return height;
        }
        height = popupRootView.getMeasuredHeight();
        if (height > 0) {
            return height;
        }
        return popupHeight;
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

    private static final class BatteryInfoTile {
        final LinearLayout root;
        final TextView leftLabelView;
        final TextView leftValueView;
        final TextView rightLabelView;
        final TextView rightValueView;

        BatteryInfoTile(
                LinearLayout root,
                TextView leftLabelView,
                TextView leftValueView,
                TextView rightLabelView,
                TextView rightValueView) {
            this.root = root;
            this.leftLabelView = leftLabelView;
            this.leftValueView = leftValueView;
            this.rightLabelView = rightLabelView;
            this.rightValueView = rightValueView;
        }
    }

    private static final class RecentAppsStrip {
        final LinearLayout root;
        final HorizontalScrollView scrollView;
        final LinearLayout contentView;

        RecentAppsStrip(
                LinearLayout root,
                HorizontalScrollView scrollView,
                LinearLayout contentView) {
            this.root = root;
            this.scrollView = scrollView;
            this.contentView = contentView;
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
