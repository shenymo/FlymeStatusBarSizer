package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
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
    private static final long DETAILS_EXPAND_DURATION_MS = 240L;
    private static final long DETAILS_COLLAPSE_DURATION_MS = 200L;
    private static final long MEDIA_EXPAND_DURATION_MS = 200L;
    private static final long MEDIA_COLLAPSE_DURATION_MS = 160L;
    private static final long MEDIA_CONTROL_REFRESH_DELAY_MS = 96L;
    private static final long MEDIA_PLAY_PAUSE_ICON_OUT_DURATION_MS = 90L;
    private static final long MEDIA_PLAY_PAUSE_ICON_IN_DURATION_MS = 120L;
    private static final int HORIZONTAL_MARGIN_DP = 16;
    private static final int STATUS_TILE_GAP_DP = 8;
    private static final int DETAILS_TOP_MARGIN_DP = 12;
    private static final int MEDIA_TOP_MARGIN_DP = 10;
    private static final int ACTION_GRID_TOP_MARGIN_DP = 10;
    private static final int RECENT_APPS_TOP_MARGIN_DP = 10;
    private static final int ACTION_GRID_ROW_GAP_DP = 8;
    private static final int ACTION_GRID_COLUMN_GAP_DP = 0;
    private static final int ACTION_GRID_CELL_MIN_HEIGHT_DP = 40;
    private static final int ACTION_GRID_CELL_HORIZONTAL_PADDING_DP = 0;
    private static final int ACTION_GRID_CELL_VERTICAL_PADDING_DP = 0;
    private static final int ACTION_GRID_ICON_SIZE_DP = 32;
    private static final int ACTION_GRID_LABEL_TOP_MARGIN_DP = 5;
    private static final int MEDIA_ARTWORK_SIZE_DP = 42;
    private static final int MEDIA_CONTENT_GAP_DP = 12;
    private static final int MEDIA_CONTROL_BUTTON_SIZE_DP = 34;
    private static final int MEDIA_CONTROL_BUTTON_ICON_SIZE_DP = 18;
    private static final int MEDIA_CONTROL_BUTTON_PADDING_DP = 7;
    private static final int MEDIA_CONTROL_GAP_DP = 6;
    private static final float MEDIA_PLAY_PAUSE_ICON_SWAP_SCALE = 0.76f;
    private static final int MEDIA_REVEAL_OFFSET_DP = 8;
    private static final int RECENT_APP_ICON_SIZE_DP = 32;
    private static final int RECENT_APP_ITEM_SIZE_DP = 40;
    private static final int RECENT_APP_ICON_PADDING_DP = 4;
    private static final int RECENT_APP_GAP_DP = 10;
    private static final int DETAILS_SWIPE_TRIGGER_DP = 20;
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
    private static final int CLOCK_TAP_TARGET_HORIZONTAL_PADDING_DP = 6;
    private static final int CLOCK_TAP_TARGET_VERTICAL_PADDING_DP = 4;
    private static final String[] MILLISECOND_TEXT_CACHE = buildMillisecondTextCache();
    private static final OvershootInterpolator POPUP_SCALE_IN_INTERPOLATOR =
            new OvershootInterpolator(0.72f);
    private static final PathInterpolator POPUP_ALPHA_IN_INTERPOLATOR =
            new PathInterpolator(0.18f, 0f, 0.12f, 1f);
    private static final PathInterpolator POPUP_TRANSLATION_IN_INTERPOLATOR =
            new PathInterpolator(0.16f, 1f, 0.28f, 1f);
    private static final PathInterpolator POPUP_OUT_INTERPOLATOR =
            new PathInterpolator(0.4f, 0f, 0.82f, 0.72f);
    private static final int PLAY_PAUSE_ICON_MODE_UNSET = 0;
    private static final int PLAY_PAUSE_ICON_MODE_PLAY = 1;
    private static final int PLAY_PAUSE_ICON_MODE_PAUSE = 2;
    private static int cachedInternalWindowType = INTERNAL_WINDOW_TYPE_UNSET;
    private static boolean trustedOverlayMethodResolved;
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;
    private static Field trustedOverlayPrivateFlagsField;
    private static boolean listenerInfoReflectionResolved;
    private static Field viewListenerInfoField;
    private static Field listenerInfoOnTouchListenerField;

    private final WeakReference<View> anchorRef;
    private final HostMode hostMode;
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
    private final LinearLayout dateContainerView;
    private final TextView dateView;
    private final TextView lunarDateView;
    private final MediaStrip mediaStrip;
    private final FrameLayout mediaViewportView;
    private final FrameLayout detailsViewportView;
    private final LinearLayout detailsContainerView;
    private final LinearLayout statusGridView;
    private final MemoryStatTile memoryTile;
    private final BatteryInfoTile batteryInfoTile;
    private final ActionGrid actionGrid;
    private final RecentAppsStrip recentAppsStrip;
    private final ClockDetailLunarDateFormatter lunarDateFormatter;
    private final ClockDetailMediaProvider mediaProvider;
    private final ClockDetailSystemStatusProvider systemStatusProvider;
    private final ClockDetailRecentAppsProvider recentAppsProvider;
    private final ActivityManager activityManager;
    private final Runnable refreshRunnable = this::refreshVisibleContent;
    private final Runnable thermalPowerRefreshRunnable = this::refreshVisibleThermalPowerStatus;
    private final Runnable memoryRefreshRunnable = this::refreshVisibleMemoryStatus;
    private final Runnable autoDismissRunnable = this::dismiss;
    private final Runnable panelLongPressRunnable = this::handlePanelLongPressTimeout;
    private final Runnable mediaControlRefreshRunnable = this::refreshMediaSnapshotFromProvider;
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
    private boolean detailsExpanded;
    private boolean manualPositionActive;
    private boolean panelTouchActive;
    private boolean panelLongPressTriggered;
    private boolean dragGestureActive;
    private boolean swipeGestureActive;
    private SwipeMode activeSwipeMode = SwipeMode.NONE;
    private boolean panelTouchStartedInInteractiveZone;
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
    private ClockDetailActionEntry[] latestActionEntries = ClockDetailActionEntry.EMPTY_ARRAY;
    private ClockDetailActionEntry[] renderedActionEntries = ClockDetailActionEntry.EMPTY_ARRAY;
    private ClockDetailRecentApp[] latestRecentApps = ClockDetailRecentApp.EMPTY_ARRAY;
    private ClockDetailRecentApp[] renderedRecentApps = ClockDetailRecentApp.EMPTY_ARRAY;
    private ClockDetailMediaSnapshot latestMediaSnapshot = ClockDetailMediaSnapshot.EMPTY;
    private boolean lunarDateEnabled = true;
    private Locale cachedLocale;
    private TimeZone cachedTimeZone;
    private boolean cached24HourMode;
    private SimpleDateFormat timeFormatter;
    private SimpleDateFormat dateFormatter;
    private Calendar reusableDateKeyCalendar;
    private long lastFormatterValidationSecond = Long.MIN_VALUE;
    private long lastRenderedSecond = Long.MIN_VALUE;
    private int lastRenderedMillisBucket = Integer.MIN_VALUE;
    private long lastRenderedDateKey = Long.MIN_VALUE;
    private int popupWidth;
    private int popupHeight;
    private int popupLeft;
    private int popupTop;
    private Animator popupAnimator;
    private ValueAnimator mediaAnimator;
    private ValueAnimator detailsAnimator;
    private Drawable originalAnchorBackground;
    private int[] originalAnchorPadding;
    private boolean originalAnchorBackgroundCaptured;
    private boolean anchorHighlighted;
    private boolean anchorInteractionInstalled;
    private View.OnTouchListener originalAnchorTouchListener;
    private boolean originalAnchorClickable;
    private boolean anchorTapTracking;
    private boolean anchorSwipeDownTriggered;
    private float anchorTapDownX;
    private float anchorTapDownY;
    private long anchorTapDownTimeMs;
    private WindowManager overlayWindowManager;
    private float panelTouchDownRawX;
    private float panelTouchDownRawY;
    private int dragStartPopupLeft;
    private int dragStartPopupTop;
    private int swipeRevealTargetHeight;
    private float popupDismissGestureProgress;
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
        this(anchor, HostMode.CLOCK);
    }

    ClockDetailPopupController(View anchor, HostMode hostMode) {
        this.anchorRef = new WeakReference<>(anchor);
        this.hostMode = hostMode != null ? hostMode : HostMode.CLOCK;
        Handler mainHandler = FlymeStatusBarSizer.getMainHandler();
        this.handler = mainHandler != null
                ? mainHandler
                : new Handler(anchor.getContext().getMainLooper());
        Context context = anchor.getContext();
        this.contentView = buildContentView(context);
        this.popupBackgroundView = buildPopupBackgroundView(context);
        this.popupRootView = buildPopupRootView(
                context,
                popupBackgroundView,
                contentView);
        this.overlayView = buildOverlayView(context, popupRootView);
        this.headerView = buildHeaderView(context);
        this.timeRowView = buildTimeRowView(context);
        this.timeView = buildTimeView(context);
        this.millisecondsView = buildMillisecondsView(context);
        this.pinToggleView = buildPinToggleView(context);
        this.dateContainerView = buildDateContainerView(context);
        this.dateView = buildDateView(context);
        this.lunarDateView = buildLunarDateView(context);
        this.detailsContainerView = buildDetailsContainerView(context);
        this.detailsViewportView = buildDetailsViewportView(
                context,
                detailsContainerView);
        this.memoryTile = buildMemoryStatTile(
                context,
                "系统内存",
                ClockDetailSystemStatusSnapshot.EMPTY.memoryRows);
        this.batteryInfoTile = buildBatteryInfoTile(context);
        this.statusGridView = buildStatusGrid(context, memoryTile, batteryInfoTile);
        this.mediaStrip = buildMediaStrip(context);
        this.mediaViewportView = buildMediaViewportView(context, mediaStrip.root);
        this.actionGrid = buildActionGrid(context);
        this.recentAppsStrip = buildRecentAppsStrip(context);
        this.detailsContainerView.addView(statusGridView, matchWidth());
        this.detailsContainerView.addView(
                actionGrid.root,
                matchWidthWithTop(context, ACTION_GRID_TOP_MARGIN_DP));
        this.detailsContainerView.addView(
                recentAppsStrip.root,
                matchWidthWithTop(context, RECENT_APPS_TOP_MARGIN_DP));
        this.timeRowView.addView(timeView, wrapContent());
        this.timeRowView.addView(millisecondsView, wrapContentWithStart(context, 2));
        this.headerView.addView(timeRowView, frameCentered());
        this.headerView.addView(pinToggleView, frameTopEnd(context, 2));
        this.dateContainerView.addView(dateView, matchWidth());
        this.dateContainerView.addView(lunarDateView, matchWidthWithTop(context, 2));
        this.contentView.addView(headerView, matchWidth());
        this.contentView.addView(dateContainerView, matchWidthWithTop(context, 5));
        this.contentView.addView(mediaViewportView, matchWidthWithTop(context, MEDIA_TOP_MARGIN_DP));
        this.contentView.addView(
                detailsViewportView,
                matchWidthWithTop(context, DETAILS_TOP_MARGIN_DP));
        this.lunarDateFormatter = new ClockDetailLunarDateFormatter(context.getClassLoader());
        this.mediaProvider = new ClockDetailMediaProvider(
                context,
                dp(context, MEDIA_ARTWORK_SIZE_DP));
        this.systemStatusProvider = new ClockDetailSystemStatusProvider(context);
        this.recentAppsProvider = new ClockDetailRecentAppsProvider(context);
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.dragTouchSlop = Math.max(
                dp(context, 4),
                ViewConfiguration.get(context).getScaledTouchSlop());
        timeView.setOnTouchListener(this::handleTimeTextTouch);
        millisecondsView.setOnTouchListener(this::handleTimeTextTouch);
        mediaStrip.root.setOnClickListener(v -> {
            performClockHaptic(v);
            launchActiveMediaApp();
        });
        mediaStrip.previousButton.setOnClickListener(v -> {
            performClockHaptic(v);
            handleMediaSkipToPrevious();
        });
        mediaStrip.playPauseButton.setOnClickListener(v -> {
            performClockHaptic(v);
            handleMediaPlayPauseToggle();
        });
        mediaStrip.nextButton.setOnClickListener(v -> {
            performClockHaptic(v);
            handleMediaSkipToNext();
        });
        pinToggleView.setOnClickListener(v -> {
            performClockHaptic(v);
            setPanelPinned(!panelPinned);
        });
        refreshPinToggleView();
        refreshPinToggleVisibility();
        applyDetailsExpandedState(isDetailsVisibleByDefault(), false);
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
        if (hostMode != HostMode.CLOCK) {
            return;
        }
        View anchorView = getAnchor();
        if (!(anchorView instanceof TextView)) {
            dismissImmediately();
            return;
        }
        TextView anchor = (TextView) anchorView;
        boolean shouldEnable = config != null && config.clockDetailPopupEnabled;
        enabled = shouldEnable;
        if (shouldEnable) {
            installAnchorInteraction(anchor);
        } else {
            clearAnchorTapState();
            restoreAnchorInteraction(anchor);
        }
        refreshActionGridConfig(config);
        if (isPopupShowing()) {
            refreshLunarDateConfig(config);
            refreshDateTextIfNeeded(System.currentTimeMillis(), true);
            applyLatestActionGridView();
            requestPopupLayoutRefresh();
        }
        if (!shouldEnable) {
            dismissImmediately();
        }
    }

    private void installAnchorInteraction(TextView anchor) {
        if (anchor == null || anchorInteractionInstalled) {
            return;
        }
        originalAnchorClickable = anchor.isClickable();
        originalAnchorTouchListener = getCurrentOnTouchListener(anchor);
        anchor.setClickable(true);
        anchor.setOnTouchListener(this::handleAnchorTouch);
        anchorInteractionInstalled = true;
    }

    private void restoreAnchorInteraction(TextView anchor) {
        if (anchor == null || !anchorInteractionInstalled) {
            return;
        }
        anchor.setOnTouchListener(originalAnchorTouchListener);
        anchor.setClickable(originalAnchorClickable);
        originalAnchorTouchListener = null;
        originalAnchorClickable = false;
        anchorInteractionInstalled = false;
    }

    private static View.OnTouchListener getCurrentOnTouchListener(View view) {
        Object listenerInfo = getViewListenerInfo(view);
        if (listenerInfo == null || listenerInfoOnTouchListenerField == null) {
            return null;
        }
        try {
            Object listener = listenerInfoOnTouchListenerField.get(listenerInfo);
            return listener instanceof View.OnTouchListener
                    ? (View.OnTouchListener) listener
                    : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getViewListenerInfo(View view) {
        if (view == null) {
            return null;
        }
        resolveListenerInfoReflection();
        if (viewListenerInfoField == null) {
            return null;
        }
        try {
            return viewListenerInfoField.get(view);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void resolveListenerInfoReflection() {
        if (listenerInfoReflectionResolved) {
            return;
        }
        listenerInfoReflectionResolved = true;
        try {
            viewListenerInfoField = View.class.getDeclaredField("mListenerInfo");
            viewListenerInfoField.setAccessible(true);
            Class<?> listenerInfoClass = viewListenerInfoField.getType();
            listenerInfoOnTouchListenerField =
                    listenerInfoClass.getDeclaredField("mOnTouchListener");
            listenerInfoOnTouchListenerField.setAccessible(true);
        } catch (Throwable ignored) {
            viewListenerInfoField = null;
            listenerInfoOnTouchListenerField = null;
        }
    }

    void dismiss() {
        dismissInternal(true);
    }

    private boolean handleAnchorTouch(View view, MotionEvent event) {
        if (!(view instanceof TextView) || event == null || hostMode != HostMode.CLOCK || !enabled) {
            return dispatchOriginalAnchorTouch(view, event);
        }
        TextView anchor = (TextView) view;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isPointInsideAnchorTapTarget(anchor, event.getX(), event.getY())) {
                    clearAnchorTapState();
                    return dispatchOriginalAnchorTouch(view, event);
                }
                anchorTapTracking = true;
                anchorSwipeDownTriggered = false;
                anchorTapDownX = event.getX();
                anchorTapDownY = event.getY();
                anchorTapDownTimeMs = event.getEventTime();
                return dispatchOriginalAnchorTouch(view, event);
            case MotionEvent.ACTION_MOVE:
                if (anchorSwipeDownTriggered) {
                    return true;
                }
                if (!anchorTapTracking) {
                    return dispatchOriginalAnchorTouch(view, event);
                }
                if (isAnchorSwipeDown(event)) {
                    anchorSwipeDownTriggered = true;
                    anchorTapTracking = false;
                    dismissImmediately();
                    expandNotificationShade(anchor.getContext());
                    return true;
                }
                if (hasAnchorTapMovedEnough(event)) {
                    clearAnchorTapState();
                }
                return dispatchOriginalAnchorTouch(view, event);
            case MotionEvent.ACTION_UP:
                if (anchorSwipeDownTriggered) {
                    clearAnchorTapState();
                    return true;
                }
                if (!anchorTapTracking) {
                    return dispatchOriginalAnchorTouch(view, event);
                }
                boolean shouldToggle = !hasAnchorTapMovedEnough(event)
                        && event.getEventTime() - anchorTapDownTimeMs <= ViewConfiguration.getTapTimeout()
                        && isPointInsideAnchorTapTarget(anchor, event.getX(), event.getY());
                clearAnchorTapState();
                if (!shouldToggle) {
                    return dispatchOriginalAnchorTouch(view, event);
                }
                performClockHaptic(anchor);
                toggleClockPopup();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                clearAnchorTapState();
                return dispatchOriginalAnchorTouch(view, event);
            default:
                return dispatchOriginalAnchorTouch(view, event);
        }
    }

    private boolean dispatchOriginalAnchorTouch(View view, MotionEvent event) {
        return originalAnchorTouchListener != null
                && originalAnchorTouchListener.onTouch(view, event);
    }

    private void clearAnchorTapState() {
        anchorTapTracking = false;
        anchorSwipeDownTriggered = false;
        anchorTapDownX = 0f;
        anchorTapDownY = 0f;
        anchorTapDownTimeMs = 0L;
    }

    private boolean isAnchorSwipeDown(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float dx = Math.abs(event.getX() - anchorTapDownX);
        float dy = event.getY() - anchorTapDownY;
        return dy >= dragTouchSlop && dy > dx;
    }

    private void expandNotificationShade(Context context) {
        if (context == null) {
            return;
        }
        try {
            Object statusBarManager = context.getSystemService("statusbar");
            if (statusBarManager == null) {
                return;
            }
            Method method = statusBarManager.getClass().getMethod("expandNotificationsPanel");
            method.setAccessible(true);
            method.invoke(statusBarManager);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to expand notification shade", t);
        }
    }

    private boolean hasAnchorTapMovedEnough(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float dx = event.getX() - anchorTapDownX;
        float dy = event.getY() - anchorTapDownY;
        return Math.max(Math.abs(dx), Math.abs(dy)) >= dragTouchSlop;
    }

    private boolean isPointInsideAnchorTapTarget(TextView anchor, float x, float y) {
        if (anchor == null) {
            return false;
        }
        float targetLeft = 0f;
        float targetTop = 0f;
        float targetRight = anchor.getWidth();
        float targetBottom = anchor.getHeight();
        android.text.Layout layout = anchor.getLayout();
        if (layout != null && layout.getLineCount() > 0) {
            float lineLeft = layout.getLineLeft(0);
            float lineRight = layout.getLineRight(0);
            targetLeft = anchor.getTotalPaddingLeft() + Math.min(lineLeft, lineRight);
            targetRight = anchor.getTotalPaddingLeft() + Math.max(lineLeft, lineRight);
            targetTop = anchor.getExtendedPaddingTop();
            targetBottom = anchor.getHeight() - anchor.getExtendedPaddingBottom();
        }
        float horizontalPadding = dp(anchor.getContext(), CLOCK_TAP_TARGET_HORIZONTAL_PADDING_DP);
        float verticalPadding = dp(anchor.getContext(), CLOCK_TAP_TARGET_VERTICAL_PADDING_DP);
        targetLeft = Math.max(0f, targetLeft - horizontalPadding);
        targetRight = Math.min(anchor.getWidth(), targetRight + horizontalPadding);
        targetTop = Math.max(0f, targetTop - verticalPadding);
        targetBottom = Math.min(anchor.getHeight(), targetBottom + verticalPadding);
        return x >= targetLeft && x <= targetRight && y >= targetTop && y <= targetBottom;
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
        stopMediaUpdates();
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

    void showFromMBackTrigger() {
        if (hostMode != HostMode.MBACK) {
            return;
        }
        show();
    }

    private void toggleClockPopup() {
        if (hostMode != HostMode.CLOCK || !enabled) {
            return;
        }
        if (!isPopupShowing()) {
            show();
            return;
        }
        if (dismissAnimationRunning) {
            return;
        }
        dismiss();
    }

    private void show() {
        View anchor = getAnchor();
        if (anchor == null) {
            return;
        }
        if ((hostMode == HostMode.CLOCK && !enabled) || !anchor.isAttachedToWindow()) {
            return;
        }
        if (!attachOverlay(anchor)) {
            return;
        }
        popupTargetShowing = true;
        dismissAnimationRunning = false;
        popupLayoutUpdatePending = false;
        startPopupSession();
        FlymeStatusBarSizer.disableAncestorClipping(anchor, 6);
        FlymeStatusBarSizer.ClockConfigSnapshot clockConfig =
                FlymeStatusBarSizer.loadClockConfig(contentView.getContext());
        refreshLunarDateConfig(clockConfig);
        refreshActionGridConfig(clockConfig);
        applyPalette(resolvePalette());
        resetTransientPopupState();
        showMilliseconds = false;
        long nowMillis = System.currentTimeMillis();
        ensureFormattersForTimestamp(nowMillis, true);
        refreshTimeText(nowMillis, true);
        refreshDateTextIfNeeded(nowMillis, true);
        latestMediaSnapshot = mediaProvider.peekStartupSnapshot();
        applyLatestMediaView();
        applyLatestSystemStatusViews();
        applyLatestActionGridView();
        applyLatestRecentAppsView();
        applyDetailsExpandedState(isDetailsVisibleByDefault(), false);
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
        View anchor = getAnchor();
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
        View anchor = getAnchor();
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
        View anchor = getAnchor();
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
        long dateKey = resolveDateKey(nowMillis);
        if (!force && dateKey == lastRenderedDateKey) {
            return false;
        }
        reusableDate.setTime(nowMillis);
        String dateText = dateFormatter != null
                ? dateFormatter.format(reusableDate)
                : "";
        String lunarDateText = lunarDateEnabled && lunarDateFormatter != null
                ? lunarDateFormatter.format(nowMillis, cachedTimeZone, cachedLocale)
                : "";
        boolean changed = setTextIfChanged(dateView, dateText);
        if (lunarDateText == null || lunarDateText.trim().isEmpty()) {
            changed |= setTextIfChanged(lunarDateView, "");
            changed |= setVisibilityIfChanged(lunarDateView, View.GONE);
        } else {
            changed |= setVisibilityIfChanged(lunarDateView, View.VISIBLE);
            changed |= setTextIfChanged(lunarDateView, lunarDateText);
        }
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
        if (!isPopupShowing()
                || hostMode != HostMode.CLOCK
                || AUTO_DISMISS_DELAY_MS <= 0L
                || panelPinned) {
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
        View anchor = getAnchor();
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
        int rootHorizontalPadding = popupRootView.getPaddingLeft() + popupRootView.getPaddingRight();
        int rootVerticalPadding = popupRootView.getPaddingTop() + popupRootView.getPaddingBottom();
        int margin = dp(context, HORIZONTAL_MARGIN_DP);
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

    private int resolveTargetPopupLeft(View anchor) {
        return manualPositionActive
                ? clampPopupLeft(popupLeft)
                : calculateAnchoredPopupLeft(anchor);
    }

    private int resolveTargetPopupTop(View anchor) {
        return manualPositionActive
                ? clampPopupTop(popupTop)
                : calculateAnchoredPopupTop(anchor);
    }

    private int calculateAnchoredPopupLeft(View anchor) {
        if (anchor == null) {
            return clampPopupLeft(0);
        }
        int[] anchorLocation = new int[2];
        int[] hostLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        fillOverlayLocationOnScreen(hostLocation);
        return clampPopupLeft(anchorLocation[0] - hostLocation[0]);
    }

    private int calculateAnchoredPopupTop(View anchor) {
        if (anchor == null) {
            return clampPopupTop(0);
        }
        int[] anchorLocation = new int[2];
        int[] hostLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        fillOverlayLocationOnScreen(hostLocation);
        int desiredTop;
        if (hostMode == HostMode.MBACK) {
            desiredTop = anchorLocation[1]
                    - hostLocation[1]
                    - popupHeight
                    - dp(anchor.getContext(), POPUP_SURFACE_OFFSET_Y_DP)
                    + dp(anchor.getContext(), POPUP_SHADOW_PADDING_DP);
        } else {
            desiredTop = anchorLocation[1]
                    - hostLocation[1]
                    + anchor.getHeight()
                    + dp(anchor.getContext(), POPUP_SURFACE_OFFSET_Y_DP)
                    - dp(anchor.getContext(), POPUP_SHADOW_PADDING_DP);
        }
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
        View anchor = getAnchor();
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
                if (popupTargetShowing && isPopupShowing()) {
                    startMediaUpdates();
                }
            });
        });
    }

    private void animatePopupOut() {
        View anchor = getAnchor();
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
            float endTranslationY = hostMode == HostMode.MBACK
                    ? dp(anchor.getContext(), 12)
                    : -dp(anchor.getContext(), 12);

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

    private void updatePopupAnimationPivot(View anchor) {
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
        popupRootView.setPivotY(hostMode == HostMode.MBACK
                ? Math.max(shadowPadding, getPopupVisualHeight() - shadowPadding)
                : shadowPadding);
    }

    private float resolveCollapsedScaleX(View anchor) {
        int popupVisualWidth = getPopupVisualWidth();
        if (anchor == null || popupVisualWidth <= 0) {
            return 0.32f;
        }
        return clamp(
                anchor.getWidth() / (float) popupVisualWidth,
                0.32f,
                0.82f);
    }

    private float resolveCollapsedScaleY(View anchor) {
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
        lunarDateView.setTextColor(palette.secondaryTextColor);
        applyPinTogglePalette(context, palette);
        applyMediaStripPalette(mediaStrip, palette);
        applyMemoryTilePalette(memoryTile, palette);
        applyBatteryInfoTilePalette(batteryInfoTile, palette);
        applyActionGridPalette(actionGrid, palette);
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
        cancelMediaAnimator();
        cancelDetailsAnimator();
        resetTransientPopupState();
        clearPopupUiState();
    }

    private void clearPopupUiState() {
        dismissAnimationRunning = false;
        cancelMediaAnimator();
        cancelDetailsAnimator();
        cancelRecentAppsStripAnimations();
        renderedRecentApps = ClockDetailRecentApp.EMPTY_ARRAY;
        resetMediaStripVisualState();
        resetRecentAppsStripVisualState();
        if (recentAppsStrip != null) {
            recentAppsStrip.scrollView.scrollTo(0, 0);
        }
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

    private void preparePopupEnterVisualState(View anchor) {
        if (anchor == null) {
            return;
        }
        updatePopupAnimationPivot(anchor);
        popupRootView.setAlpha(0.16f);
        popupRootView.setScaleX(resolveCollapsedScaleX(anchor));
        popupRootView.setScaleY(resolveCollapsedScaleY(anchor));
        popupRootView.setTranslationY(hostMode == HostMode.MBACK
                ? dp(anchor.getContext(), 14)
                : -dp(anchor.getContext(), 14));
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

    private void applyAnchorHighlight(View anchor) {
        if (!(anchor instanceof TextView) || hostMode != HostMode.CLOCK) {
            return;
        }
        TextView textAnchor = (TextView) anchor;
        Context context = anchor.getContext();
        if (!originalAnchorBackgroundCaptured) {
            originalAnchorBackground = textAnchor.getBackground();
            originalAnchorPadding = captureAnchorPadding(textAnchor);
            originalAnchorBackgroundCaptured = true;
        }
        GradientDrawable capsule = new GradientDrawable();
        capsule.setShape(GradientDrawable.RECTANGLE);
        capsule.setColor(resolveAnchorHighlightFillColor(textAnchor.getCurrentTextColor()));
        capsule.setCornerRadius(Math.max(
                textAnchor.getHeight(),
                dp(context, 20)));
        capsule.setStroke(
                Math.max(1, dp(context, 1)),
                resolveAnchorHighlightStrokeColor(textAnchor.getCurrentTextColor()));
        InsetDrawable highlight = new InsetDrawable(
                capsule,
                dp(context, CLOCK_HIGHLIGHT_HORIZONTAL_INSET_DP),
                dp(context, CLOCK_HIGHLIGHT_VERTICAL_INSET_DP),
                dp(context, CLOCK_HIGHLIGHT_HORIZONTAL_INSET_DP),
                dp(context, CLOCK_HIGHLIGHT_VERTICAL_INSET_DP));
        textAnchor.setBackground(highlight);
        restoreAnchorPadding(textAnchor);
        anchorHighlighted = true;
        textAnchor.invalidate();
    }

    private void clearAnchorHighlight() {
        View anchor = getAnchor();
        if (anchor instanceof TextView && anchorHighlighted) {
            TextView textAnchor = (TextView) anchor;
            textAnchor.setBackground(originalAnchorBackground);
            restoreAnchorPadding(textAnchor);
            textAnchor.invalidate();
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
        if (hostMode == HostMode.MBACK) {
            int sourceColor = resolvePaletteSourceTextColor();
            if (sourceColor != Integer.MIN_VALUE) {
                return buildPaletteFromSourceTextColor(sourceColor);
            }
        }
        View anchor = getAnchor();
        if (!(anchor instanceof TextView)) {
            return new Palette(
                    Color.parseColor("#FCFDFE"),
                    Color.parseColor("#D6DCE8"),
                    Color.parseColor("#191C1E"),
                    Color.parseColor("#56606C"),
                    Color.parseColor("#005CAE"));
        }
        TextView textAnchor = (TextView) anchor;
        return isLightForeground(textAnchor.getCurrentTextColor())
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

    private int resolvePaletteSourceTextColor() {
        TextView primaryClockView = ClockHooks.resolvePrimaryStatusBarClockView();
        if (primaryClockView != null) {
            return primaryClockView.getCurrentTextColor();
        }
        View anchor = getAnchor();
        if (anchor instanceof TextView) {
            return ((TextView) anchor).getCurrentTextColor();
        }
        return Integer.MIN_VALUE;
    }

    private Palette buildPaletteFromSourceTextColor(int sourceTextColor) {
        boolean lightForeground = isLightForeground(sourceTextColor);
        int surfaceColor = lightForeground
                ? Color.parseColor("#20262C")
                : Color.parseColor("#FCFDFE");
        int strokeColor = lightForeground
                ? Color.parseColor("#4F5966")
                : Color.parseColor("#D6DCE8");
        int secondaryTextColor = mixColors(
                sourceTextColor,
                surfaceColor,
                lightForeground ? 0.26f : 0.34f);
        int accentColor = mixColors(
                sourceTextColor,
                lightForeground ? Color.parseColor("#7DB7FF") : Color.parseColor("#005CAE"),
                0.18f);
        return new Palette(
                surfaceColor,
                strokeColor,
                sourceTextColor,
                secondaryTextColor,
                accentColor);
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
        View anchor = getAnchor();
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
        View anchor = getAnchor();
        if (!(anchor instanceof TextView)) {
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

    private boolean attachOverlay(View anchor) {
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
            if (hostMode == HostMode.MBACK) {
                overlayView.setFocusable(true);
                overlayView.setFocusableInTouchMode(true);
                overlayView.requestFocus();
            }
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
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        if (hostMode != HostMode.MBACK) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
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
        detailsExpanded = isDetailsVisibleByDefault();
        manualPositionActive = false;
        panelTouchActive = false;
        panelLongPressTriggered = false;
        dragGestureActive = false;
        swipeGestureActive = false;
        activeSwipeMode = SwipeMode.NONE;
        panelTouchStartedInInteractiveZone = false;
        popupLeft = 0;
        popupTop = 0;
        dragStartPopupLeft = 0;
        dragStartPopupTop = 0;
        swipeRevealTargetHeight = 0;
        popupDismissGestureProgress = 0f;
        latestMediaSnapshot = ClockDetailMediaSnapshot.EMPTY;
        cancelMediaAnimator();
        cancelDetailsAnimator();
        resetMediaStripVisualState();
        cancelRecentAppsStripAnimations();
        resetRecentAppsStripVisualState();
        refreshPinToggleView();
        refreshPinToggleVisibility();
        applyDetailsExpandedState(detailsExpanded, false);
        resetPopupVisualState();
    }

    private void startPopupSession() {
        popupSessionId = nextPopupSessionId++;
        thermalPowerQueryInFlight = false;
        memoryQueryInFlight = false;
        recentAppsQueryInFlight = false;
        latestMediaSnapshot = ClockDetailMediaSnapshot.EMPTY;
        thermalPowerRequestSessionId = INVALID_POPUP_SESSION_ID;
        memoryRequestSessionId = INVALID_POPUP_SESSION_ID;
        recentAppsRequestSessionId = INVALID_POPUP_SESSION_ID;
    }

    private void invalidatePopupSession() {
        popupSessionId = nextPopupSessionId++;
        thermalPowerQueryInFlight = false;
        memoryQueryInFlight = false;
        recentAppsQueryInFlight = false;
        latestMediaSnapshot = ClockDetailMediaSnapshot.EMPTY;
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
                swipeGestureActive = false;
                panelTouchStartedInInteractiveZone = isPointInsideInteractiveZone(event);
                panelTouchDownRawX = event.getRawX();
                panelTouchDownRawY = event.getRawY();
                dragStartPopupLeft = popupLeft;
                dragStartPopupTop = popupTop;
                swipeRevealTargetHeight = 0;
                popupDismissGestureProgress = 0f;
                activeSwipeMode = SwipeMode.NONE;
                cancelPanelLongPress();
                if (isPanelDragEnabled() && !panelTouchStartedInInteractiveZone) {
                    handler.postDelayed(
                            panelLongPressRunnable,
                            ViewConfiguration.getLongPressTimeout());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!panelTouchActive) {
                    return;
                }
                if (dragGestureActive) {
                    return;
                }
                SwipeMode swipeMode = resolveSwipeModeForGesture(event);
                if (swipeMode != SwipeMode.NONE) {
                    swipeGestureActive = true;
                    activeSwipeMode = swipeMode;
                    cancelPanelLongPress();
                    handler.removeCallbacks(autoDismissRunnable);
                    cancelPopupAnimator();
                    enterAnimationRunning = false;
                    dismissAnimationRunning = false;
                    popupLayoutUpdatePending = false;
                    if (swipeMode == SwipeMode.DETAILS_REVEAL) {
                        cancelDetailsAnimator();
                        swipeRevealTargetHeight = Math.max(1, measureDetailsContentHeight());
                        setVisibilityIfChanged(detailsViewportView, View.VISIBLE);
                    }
                    updateSwipeGesture(event);
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
        return swipeGestureActive
                || dragGestureActive
                || panelLongPressTriggered
                || action == MotionEvent.ACTION_CANCEL;
    }

    private boolean handlePanelTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (swipeGestureActive) {
                    updateSwipeGesture(event);
                    return true;
                }
                if (dragGestureActive) {
                    updateDraggedPopupPosition(event);
                    return true;
                }
                return panelLongPressTriggered;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean handled = swipeGestureActive
                        ? handleSwipeGestureRelease(event)
                        : (dragGestureActive || panelLongPressTriggered);
                finishPanelTouchGesture();
                return handled;
            default:
                return swipeGestureActive || dragGestureActive || panelLongPressTriggered;
        }
    }

    private void finishPanelTouchGesture() {
        cancelPanelLongPress();
        panelTouchActive = false;
        panelLongPressTriggered = false;
        dragGestureActive = false;
        swipeGestureActive = false;
        activeSwipeMode = SwipeMode.NONE;
        swipeRevealTargetHeight = 0;
        popupDismissGestureProgress = 0f;
        panelTouchStartedInInteractiveZone = false;
        if (popupTargetShowing && !panelPinned) {
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
            if (shouldDismissFromOutsideTouch()) {
                dismiss();
            }
            return false;
        }
        if (action != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (isPointInsidePopup(event.getX(), event.getY())) {
            return false;
        }
        if (shouldDismissFromOutsideTouch()) {
            dismiss();
            return true;
        }
        return false;
    }

    private boolean handleOverlayKeyEvent(KeyEvent event) {
        if (event == null || hostMode != HostMode.MBACK || !isPopupShowing()) {
            return false;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            return false;
        }
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            return true;
        }
        if (action == KeyEvent.ACTION_UP) {
            dismiss();
            return true;
        }
        return false;
    }

    private void setPanelPinned(boolean pinned) {
        if (hostMode != HostMode.CLOCK) {
            return;
        }
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

    private void refreshPinToggleVisibility() {
        setVisibilityIfChanged(
                pinToggleView,
                hostMode == HostMode.CLOCK ? View.VISIBLE : View.GONE);
    }

    private boolean isDetailsVisibleByDefault() {
        return hostMode == HostMode.MBACK;
    }

    private boolean isPanelDragEnabled() {
        return hostMode == HostMode.CLOCK;
    }

    private boolean shouldDismissFromOutsideTouch() {
        return hostMode == HostMode.MBACK || !panelPinned;
    }

    private SwipeMode resolveSwipeModeForGesture(MotionEvent event) {
        if (event == null
                || panelTouchStartedInInteractiveZone
                || detailsAnimator != null
                || dismissAnimationRunning) {
            return SwipeMode.NONE;
        }
        float dx = event.getRawX() - panelTouchDownRawX;
        float dy = event.getRawY() - panelTouchDownRawY;
        if (Math.abs(dy) < dragTouchSlop || Math.abs(dy) <= Math.abs(dx) * 1.2f) {
            return SwipeMode.NONE;
        }
        if (hostMode == HostMode.CLOCK) {
            if (!detailsExpanded && dy > 0f) {
                return SwipeMode.DETAILS_REVEAL;
            }
            if (detailsExpanded && dy < 0f) {
                return SwipeMode.POPUP_DISMISS;
            }
            return SwipeMode.NONE;
        }
        if (hostMode == HostMode.MBACK && dy > 0f) {
            return SwipeMode.POPUP_DISMISS;
        }
        return SwipeMode.NONE;
    }

    private void updateSwipeGesture(MotionEvent event) {
        if (event == null || activeSwipeMode == SwipeMode.NONE) {
            return;
        }
        switch (activeSwipeMode) {
            case DETAILS_REVEAL:
                updateDetailsRevealGesture(event);
                break;
            case POPUP_DISMISS:
                updatePopupDismissGesture(event);
                break;
            default:
                break;
        }
    }

    private boolean handleSwipeGestureRelease(MotionEvent event) {
        if (event == null) {
            return false;
        }
        if (activeSwipeMode == SwipeMode.DETAILS_REVEAL) {
            return finishDetailsRevealGesture(event);
        }
        if (activeSwipeMode == SwipeMode.POPUP_DISMISS) {
            return finishPopupDismissGesture(event);
        }
        return false;
    }

    private void updateDetailsRevealGesture(MotionEvent event) {
        if (event == null) {
            return;
        }
        int targetHeight = swipeRevealTargetHeight > 0
                ? swipeRevealTargetHeight
                : Math.max(1, measureDetailsContentHeight());
        swipeRevealTargetHeight = targetHeight;
        int revealedHeight = Math.max(
                0,
                Math.min(targetHeight, Math.round(event.getRawY() - panelTouchDownRawY)));
        applyInteractiveDetailsRevealHeight(revealedHeight, targetHeight);
    }

    private boolean finishDetailsRevealGesture(MotionEvent event) {
        if (event == null || detailsAnimator != null) {
            return false;
        }
        int targetHeight = swipeRevealTargetHeight > 0
                ? swipeRevealTargetHeight
                : Math.max(1, measureDetailsContentHeight());
        int currentHeight = Math.max(0, resolveCurrentDetailsViewportHeight());
        int trigger = Math.max(
                dp(detailsViewportView.getContext(), DETAILS_SWIPE_TRIGGER_DP),
                Math.round(targetHeight * 0.28f));
        if (currentHeight >= trigger) {
            applyDetailsExpandedState(true, true);
            scheduleAutoDismiss();
            return true;
        }
        applyDetailsExpandedState(false, true);
        return currentHeight > 0;
    }

    private void updatePopupDismissGesture(MotionEvent event) {
        if (event == null) {
            return;
        }
        float rawDy = event.getRawY() - panelTouchDownRawY;
        float effectiveDy = hostMode == HostMode.MBACK
                ? Math.max(0f, rawDy)
                : Math.max(0f, -rawDy);
        float travel = Math.max(
                dp(popupRootView.getContext(), 120),
                getPopupVisualHeight() * 0.52f);
        float progress = travel <= 0f ? 0f : Math.min(1f, effectiveDy / travel);
        popupDismissGestureProgress = progress;
        applyPopupDismissGestureProgress(progress, effectiveDy);
    }

    private boolean finishPopupDismissGesture(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float dx = event.getRawX() - panelTouchDownRawX;
        float dy = event.getRawY() - panelTouchDownRawY;
        if (Math.abs(dy) <= Math.abs(dx) * 1.2f) {
            restorePopupAfterDismissGesture();
            return popupDismissGestureProgress > 0f;
        }
        boolean directionMatches = hostMode == HostMode.MBACK ? dy > 0f : dy < 0f;
        int triggerDistance = Math.max(
                dragTouchSlop,
                dp(popupRootView.getContext(), DETAILS_SWIPE_TRIGGER_DP));
        if (directionMatches
                && (popupDismissGestureProgress >= 0.34f || Math.abs(dy) >= triggerDistance)) {
            popupTargetShowing = false;
            dismissAnimationRunning = true;
            animatePopupOut();
            return true;
        }
        restorePopupAfterDismissGesture();
        return popupDismissGestureProgress > 0f;
    }

    private void applyInteractiveDetailsRevealHeight(int height, int targetHeight) {
        int safeTargetHeight = Math.max(1, targetHeight);
        int clampedHeight = Math.max(0, Math.min(safeTargetHeight, height));
        float progress = clampedHeight / (float) safeTargetHeight;
        setVisibilityIfChanged(detailsViewportView, View.VISIBLE);
        detailsViewportView.setAlpha(0.18f + (0.82f * progress));
        detailsViewportView.setTranslationY(
                -dp(detailsViewportView.getContext(), 8) * (1f - progress));
        updateDetailsViewportHeight(clampedHeight);
    }

    private void applyPopupDismissGestureProgress(float progress, float travelY) {
        float clampedProgress = clamp(progress, 0f, 1f);
        float direction = hostMode == HostMode.MBACK ? 1f : -1f;
        popupRootView.setAlpha(1f - (0.86f * clampedProgress));
        popupRootView.setScaleX(1f - (0.12f * clampedProgress));
        popupRootView.setScaleY(1f - (0.16f * clampedProgress));
        popupRootView.setTranslationY(direction * Math.abs(travelY));
    }

    private void restorePopupAfterDismissGesture() {
        AnimatorSet animator = new AnimatorSet();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(
                popupRootView,
                View.ALPHA,
                popupRootView.getAlpha(),
                1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(
                popupRootView,
                View.SCALE_X,
                popupRootView.getScaleX(),
                1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(
                popupRootView,
                View.SCALE_Y,
                popupRootView.getScaleY(),
                1f);
        ObjectAnimator translationY = ObjectAnimator.ofFloat(
                popupRootView,
                View.TRANSLATION_Y,
                popupRootView.getTranslationY(),
                0f);
        animator.playTogether(alpha, scaleX, scaleY, translationY);
        animator.setDuration(180L);
        animator.setInterpolator(POPUP_TRANSLATION_IN_INTERPOLATOR);
        startPopupAnimation(animator, this::resetPopupVisualState);
    }

    private void applyDetailsExpandedState(boolean expanded, boolean animate) {
        detailsExpanded = expanded;
        refreshPinToggleVisibility();
        if (!animate || !isPopupShowing()) {
            cancelDetailsAnimator();
            applyDetailsExpandedStateImmediately(expanded);
            return;
        }
        animateDetailsExpandedState(expanded);
    }

    private void applyDetailsExpandedStateImmediately(boolean expanded) {
        cancelDetailsAnimator();
        if (expanded) {
            detailsViewportView.setAlpha(1f);
            detailsViewportView.setTranslationY(0f);
            setVisibilityIfChanged(detailsViewportView, View.VISIBLE);
            updateDetailsViewportHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            detailsViewportView.setAlpha(1f);
            detailsViewportView.setTranslationY(0f);
            updateDetailsViewportHeight(0);
            setVisibilityIfChanged(detailsViewportView, View.GONE);
        }
    }

    private void animateDetailsExpandedState(boolean expanded) {
        cancelDetailsAnimator();
        int startHeight = resolveCurrentDetailsViewportHeight();
        int targetHeight = expanded ? measureDetailsContentHeight() : 0;
        if (startHeight == targetHeight) {
            applyDetailsExpandedStateImmediately(expanded);
            return;
        }
        if (expanded) {
            setVisibilityIfChanged(detailsViewportView, View.VISIBLE);
            detailsViewportView.setAlpha(0.18f);
            detailsViewportView.setTranslationY(-dp(detailsViewportView.getContext(), 8));
        }
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, targetHeight);
        detailsAnimator = animator;
        animator.setDuration(expanded ? DETAILS_EXPAND_DURATION_MS : DETAILS_COLLAPSE_DURATION_MS);
        animator.setInterpolator(expanded
                ? POPUP_TRANSLATION_IN_INTERPOLATOR
                : POPUP_OUT_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            int height = (Integer) animation.getAnimatedValue();
            float fraction = animation.getAnimatedFraction();
            updateDetailsViewportHeight(height);
            if (expanded) {
                detailsViewportView.setAlpha(0.18f + (0.82f * fraction));
                detailsViewportView.setTranslationY(
                        -dp(detailsViewportView.getContext(), 8) * (1f - fraction));
            } else {
                detailsViewportView.setAlpha(Math.max(0f, 1f - fraction));
                detailsViewportView.setTranslationY(
                        -dp(detailsViewportView.getContext(), 8) * fraction);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (detailsAnimator == animation) {
                    detailsAnimator = null;
                }
                if (cancelled) {
                    return;
                }
                detailsViewportView.setAlpha(1f);
                detailsViewportView.setTranslationY(0f);
                if (expanded) {
                    updateDetailsViewportHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                } else {
                    updateDetailsViewportHeight(0);
                    setVisibilityIfChanged(detailsViewportView, View.GONE);
                }
                requestPopupLayoutRefresh();
            }
        });
        animator.start();
    }

    private void cancelDetailsAnimator() {
        ValueAnimator animator = detailsAnimator;
        detailsAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
    }

    private void cancelMediaAnimator() {
        ValueAnimator animator = mediaAnimator;
        mediaAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
    }

    private void resetMediaStripVisualState() {
        resetMediaStripVisualTransform();
        updateMediaViewportHeight(0);
        setVisibilityIfChanged(mediaViewportView, View.GONE);
        setVisibilityIfChanged(mediaStrip.root, View.GONE);
        bindMediaContent(ClockDetailMediaSnapshot.EMPTY);
    }

    private void resetMediaStripVisualTransform() {
        mediaStrip.root.setAlpha(1f);
        mediaStrip.root.setTranslationY(0f);
    }

    private int resolveCurrentMediaViewportHeight() {
        ViewGroup.LayoutParams params = mediaViewportView.getLayoutParams();
        if (params != null && params.height > 0) {
            return params.height;
        }
        int currentHeight = mediaViewportView.getHeight();
        if (currentHeight > 0) {
            return currentHeight;
        }
        return mediaViewportView.getMeasuredHeight();
    }

    private int measureMediaContentHeight() {
        int contentWidth = resolvePopupChildContentWidth(mediaViewportView.getContext());
        mediaStrip.root.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return Math.max(0, mediaStrip.root.getMeasuredHeight());
    }

    private void updateMediaViewportHeight(int height) {
        ViewGroup.LayoutParams params = mediaViewportView.getLayoutParams();
        if (!(params instanceof LinearLayout.LayoutParams)) {
            params = matchWidth();
        }
        params.height = height;
        mediaViewportView.setLayoutParams(params);
        if (isPopupShowing() && !dismissAnimationRunning) {
            measureContent();
            updatePopupPosition();
        }
    }

    private int resolveCurrentDetailsViewportHeight() {
        ViewGroup.LayoutParams params = detailsViewportView.getLayoutParams();
        if (params != null && params.height > 0) {
            return params.height;
        }
        int currentHeight = detailsViewportView.getHeight();
        if (currentHeight > 0) {
            return currentHeight;
        }
        return detailsViewportView.getMeasuredHeight();
    }

    private int measureDetailsContentHeight() {
        int contentWidth = resolvePopupChildContentWidth(detailsViewportView.getContext());
        detailsContainerView.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return Math.max(0, detailsContainerView.getMeasuredHeight());
    }

    private void updateDetailsViewportHeight(int height) {
        ViewGroup.LayoutParams params = detailsViewportView.getLayoutParams();
        if (!(params instanceof LinearLayout.LayoutParams)) {
            params = matchWidth();
        }
        LinearLayout.LayoutParams linearParams = (LinearLayout.LayoutParams) params;
        linearParams.height = height;
        detailsViewportView.setLayoutParams(linearParams);
        if (isPopupShowing() && !dismissAnimationRunning) {
            measureContent();
            updatePopupPosition();
        }
    }

    private int resolvePopupChildContentWidth(Context context) {
        int measuredContentWidth = contentView.getMeasuredWidth();
        if (measuredContentWidth > 0) {
            return Math.max(1, measuredContentWidth - contentView.getPaddingLeft() - contentView.getPaddingRight());
        }
        int overlayWidth = getOverlayWidth();
        int rootHorizontalPadding = popupRootView.getPaddingLeft() + popupRootView.getPaddingRight();
        int margin = dp(context, HORIZONTAL_MARGIN_DP);
        return Math.max(1, overlayWidth - (margin * 2) - rootHorizontalPadding);
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
            if (hostMode == HostMode.MBACK) {
                touchableRegion.set(0, 0, overlayView.getWidth(), overlayView.getHeight());
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

    private boolean isPointInsideInteractiveZone(MotionEvent event) {
        return isPointInsideView(event, actionGrid.root)
                || isPointInsideView(event, recentAppsStrip.root);
    }

    private static boolean isPointInsideView(MotionEvent event, View targetView) {
        if (event == null || targetView == null || targetView.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        targetView.getLocationOnScreen(location);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= location[0]
                && rawX <= location[0] + targetView.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + targetView.getHeight();
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

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (handleOverlayKeyEvent(event)) {
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        overlay.setFocusable(hostMode == HostMode.MBACK);
        overlay.setFocusableInTouchMode(hostMode == HostMode.MBACK);
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
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(
                dp(context, 18),
                dp(context, 14),
                dp(context, 18),
                dp(context, 14));
        return root;
    }

    private static LinearLayout buildDateContainerView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        return root;
    }

    private static FrameLayout buildMediaViewportView(Context context, LinearLayout mediaRoot) {
        FrameLayout viewport = new FrameLayout(context);
        viewport.setClipChildren(true);
        viewport.setClipToPadding(true);
        viewport.setVisibility(View.GONE);
        viewport.addView(mediaRoot, frameMatchWidthWrapContent());
        return viewport;
    }

    private static LinearLayout buildDetailsContainerView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        return root;
    }

    private static FrameLayout buildDetailsViewportView(
            Context context,
            LinearLayout detailsContainerView) {
        FrameLayout viewport = new FrameLayout(context);
        viewport.setClipChildren(true);
        viewport.setClipToPadding(true);
        viewport.addView(
                detailsContainerView,
                frameMatchWidthWrapContent());
        return viewport;
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

    private static TextView buildLunarDateView(Context context) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setVisibility(View.GONE);
        return view;
    }

    private void applyLatestMediaView() {
        bindMediaContent(latestMediaSnapshot);
        applyMediaVisibilityState(latestMediaSnapshot.active, false);
    }

    private void applyLatestSystemStatusViews() {
        updateMemoryStatusView(latestMemoryRows);
        updateBatteryInfoStatusView(
                latestTemperatureValue,
                latestPowerValue,
                latestRemainingCapacityValue,
                latestEstimatedFullCapacityValue);
    }

    private void refreshActionGridConfig(FlymeStatusBarSizer.ClockConfigSnapshot config) {
        if (config == null || !config.enabled || !config.clockDetailActionGridEnabled) {
            latestActionEntries = ClockDetailActionEntry.EMPTY_ARRAY;
            return;
        }
        ClockDetailAssistantActionCatalog.restoreActionCache(
                contentView.getContext(),
                config.clockDetailAssistantActionCacheJson);
        latestActionEntries = ClockDetailActionResolver.resolveEntries(
                contentView.getContext(),
                ClockDetailActionCodec.decode(config.clockDetailActionGridItemsJson));
    }

    private void refreshLunarDateConfig(FlymeStatusBarSizer.ClockConfigSnapshot config) {
        lunarDateEnabled = config == null || config.clockDetailLunarDateEnabled;
    }

    private void applyLatestActionGridView() {
        updateActionGridView(latestActionEntries);
    }

    private void applyLatestRecentAppsView() {
        updateRecentAppsView(latestRecentApps, false);
    }

    private void startMediaUpdates() {
        final int requestSessionId = popupSessionId;
        mediaProvider.startListening(handler, latestMediaSnapshot, mediaSnapshot -> {
            if (popupSessionId != requestSessionId) {
                return;
            }
            latestMediaSnapshot = mediaSnapshot != null && mediaSnapshot.active
                    ? mediaSnapshot
                    : ClockDetailMediaSnapshot.EMPTY;
            if (!isPopupShowing()) {
                return;
            }
            if (updateMediaView(latestMediaSnapshot)) {
                requestPopupLayoutRefresh();
            }
        });
    }

    private void stopMediaUpdates() {
        handler.removeCallbacks(mediaControlRefreshRunnable);
        mediaProvider.stopListening();
    }

    private boolean updateMediaView(ClockDetailMediaSnapshot snapshot) {
        if (mediaStrip == null) {
            return false;
        }
        ClockDetailMediaSnapshot safeSnapshot = snapshot != null && snapshot.active
                ? snapshot
                : ClockDetailMediaSnapshot.EMPTY;
        boolean changed = bindMediaContent(safeSnapshot);
        boolean shouldShow = safeSnapshot.active;
        boolean currentlyVisible = mediaViewportView.getVisibility() == View.VISIBLE;
        if (shouldShow == currentlyVisible) {
            return changed;
        }
        if (!isPopupShowing() || dismissAnimationRunning) {
            return applyMediaVisibilityState(shouldShow, true) || changed;
        }
        animateMediaVisibilityChange(shouldShow);
        return true;
    }

    private boolean bindMediaContent(ClockDetailMediaSnapshot snapshot) {
        ClockDetailMediaSnapshot safeSnapshot = snapshot != null && snapshot.active
                ? snapshot
                : ClockDetailMediaSnapshot.EMPTY;
        if (!safeSnapshot.active) {
            boolean changed = false;
            if (mediaStrip.artworkView.getDrawable() != null) {
                mediaStrip.artworkView.setImageDrawable(null);
                changed = true;
            }
            if (!(mediaStrip.artworkView.getTag() instanceof String)
                    || !((String) mediaStrip.artworkView.getTag()).isEmpty()) {
                mediaStrip.artworkView.setTag("");
                changed = true;
            }
            changed |= setTextIfChanged(mediaStrip.titleView, "");
            changed |= setTextIfChanged(mediaStrip.subtitleView, "");
            changed |= setTextIfChanged(mediaStrip.statusView, "");
            applyMediaControlsState(ClockDetailMediaSnapshot.EMPTY);
            return changed;
        }
        boolean changed = false;
        String currentArtworkKey = mediaStrip.artworkView.getTag() instanceof String
                ? (String) mediaStrip.artworkView.getTag()
                : "";
        if (!currentArtworkKey.equals(safeSnapshot.artworkKey)
                || (safeSnapshot.artwork != null && mediaStrip.artworkView.getDrawable() == null)
                || (safeSnapshot.artwork == null && mediaStrip.artworkView.getDrawable() != null)) {
            mediaStrip.artworkView.setImageDrawable(safeSnapshot.artwork);
            mediaStrip.artworkView.setTag(safeSnapshot.artworkKey);
            changed = true;
        }
        changed |= setTextIfChanged(mediaStrip.titleView, buildMediaTitleText(safeSnapshot));
        changed |= setTextIfChanged(mediaStrip.subtitleView, buildMediaSubtitleText(safeSnapshot));
        changed |= setTextIfChanged(mediaStrip.statusView, safeSnapshot.playbackStateLabel);
        applyMediaControlsState(safeSnapshot);
        return changed;
    }

    private boolean applyMediaVisibilityState(boolean visible, boolean clearContentWhenHidden) {
        cancelMediaAnimator();
        resetMediaStripVisualTransform();
        if (visible) {
            setVisibilityIfChanged(mediaStrip.root, View.VISIBLE);
            setVisibilityIfChanged(mediaViewportView, View.VISIBLE);
            updateMediaViewportHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            return true;
        }
        updateMediaViewportHeight(0);
        setVisibilityIfChanged(mediaViewportView, View.GONE);
        setVisibilityIfChanged(mediaStrip.root, View.GONE);
        if (clearContentWhenHidden) {
            bindMediaContent(ClockDetailMediaSnapshot.EMPTY);
        }
        return true;
    }

    private void animateMediaVisibilityChange(boolean visible) {
        cancelMediaAnimator();
        int startHeight = resolveCurrentMediaViewportHeight();
        int targetHeight = visible ? measureMediaContentHeight() : 0;
        if (startHeight == targetHeight) {
            applyMediaVisibilityState(visible, true);
            return;
        }
        if (visible) {
            setVisibilityIfChanged(mediaStrip.root, View.VISIBLE);
            setVisibilityIfChanged(mediaViewportView, View.VISIBLE);
            mediaStrip.root.setAlpha(0.12f);
            mediaStrip.root.setTranslationY(-dp(mediaStrip.root.getContext(), MEDIA_REVEAL_OFFSET_DP));
            updateMediaViewportHeight(0);
        }
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, targetHeight);
        mediaAnimator = animator;
        animator.setDuration(visible ? MEDIA_EXPAND_DURATION_MS : MEDIA_COLLAPSE_DURATION_MS);
        animator.setInterpolator(visible
                ? POPUP_TRANSLATION_IN_INTERPOLATOR
                : POPUP_OUT_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            int height = (Integer) animation.getAnimatedValue();
            float fraction = animation.getAnimatedFraction();
            updateMediaViewportHeight(height);
            if (visible) {
                mediaStrip.root.setAlpha(0.12f + (0.88f * fraction));
                mediaStrip.root.setTranslationY(
                        -dp(mediaStrip.root.getContext(), MEDIA_REVEAL_OFFSET_DP) * (1f - fraction));
            } else {
                mediaStrip.root.setAlpha(Math.max(0f, 1f - fraction));
                mediaStrip.root.setTranslationY(
                        -dp(mediaStrip.root.getContext(), MEDIA_REVEAL_OFFSET_DP) * fraction);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mediaAnimator == animation) {
                    mediaAnimator = null;
                }
                if (cancelled) {
                    return;
                }
                resetMediaStripVisualTransform();
                if (visible) {
                    setVisibilityIfChanged(mediaStrip.root, View.VISIBLE);
                    updateMediaViewportHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                } else {
                    updateMediaViewportHeight(0);
                    setVisibilityIfChanged(mediaViewportView, View.GONE);
                    setVisibilityIfChanged(mediaStrip.root, View.GONE);
                    bindMediaContent(ClockDetailMediaSnapshot.EMPTY);
                }
                requestPopupLayoutRefresh();
            }
        });
        animator.start();
    }

    private static CharSequence buildMediaTitleText(ClockDetailMediaSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (snapshot.title != null && snapshot.title.length() > 0) {
            return snapshot.title;
        }
        if (snapshot.subtitle != null && snapshot.subtitle.length() > 0) {
            return snapshot.subtitle;
        }
        return isPausedPlaybackState(snapshot.playbackState) ? "音乐已暂停" : "正在播放";
    }

    private static CharSequence buildMediaSubtitleText(ClockDetailMediaSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return (snapshot.title == null || snapshot.title.length() == 0)
                || snapshot.subtitle == null
                || snapshot.subtitle.length() == 0
                ? ""
                : snapshot.subtitle;
    }

    private void handleMediaSkipToPrevious() {
        if (!mediaProvider.skipToPrevious()) {
            return;
        }
        scheduleAutoDismiss();
        requestMediaControlRefresh();
    }

    private void handleMediaSkipToNext() {
        if (!mediaProvider.skipToNext()) {
            return;
        }
        scheduleAutoDismiss();
        requestMediaControlRefresh();
    }

    private void handleMediaPlayPauseToggle() {
        if (!mediaProvider.togglePlayPause()) {
            return;
        }
        applyOptimisticPlayPauseButtonState();
        scheduleAutoDismiss();
        requestMediaControlRefresh();
    }

    private void requestMediaControlRefresh() {
        handler.removeCallbacks(mediaControlRefreshRunnable);
        handler.postDelayed(mediaControlRefreshRunnable, MEDIA_CONTROL_REFRESH_DELAY_MS);
    }

    private void refreshMediaSnapshotFromProvider() {
        if (!isPopupShowing()) {
            return;
        }
        ClockDetailMediaSnapshot snapshot = mediaProvider.peekStartupSnapshot();
        latestMediaSnapshot = snapshot != null && snapshot.active
                ? snapshot
                : ClockDetailMediaSnapshot.EMPTY;
        if (updateMediaView(latestMediaSnapshot)) {
            requestPopupLayoutRefresh();
        }
    }

    private void applyMediaControlsState(ClockDetailMediaSnapshot snapshot) {
        if (mediaStrip == null) {
            return;
        }
        ClockDetailMediaSnapshot safeSnapshot = snapshot != null && snapshot.active
                ? snapshot
                : ClockDetailMediaSnapshot.EMPTY;
        boolean paused = isPausedPlaybackState(safeSnapshot.playbackState);
        updateMediaControlButtonState(
                mediaStrip.previousButton,
                safeSnapshot.active
                        && supportsAction(
                                safeSnapshot.availableActions,
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                "上一曲");
        updateMediaControlButtonState(
                mediaStrip.playPauseButton,
                safeSnapshot.active
                        && (paused
                                ? supportsEitherAction(
                                        safeSnapshot.availableActions,
                                        PlaybackState.ACTION_PLAY,
                                        PlaybackState.ACTION_PLAY_PAUSE)
                                : supportsEitherAction(
                                        safeSnapshot.availableActions,
                                        PlaybackState.ACTION_PAUSE,
                                        PlaybackState.ACTION_PLAY_PAUSE)),
                paused ? "继续播放" : "暂停播放");
        updatePlayPauseButtonIcon(
                mediaStrip,
                paused ? PLAY_PAUSE_ICON_MODE_PLAY : PLAY_PAUSE_ICON_MODE_PAUSE,
                safeSnapshot.active
                        && mediaViewportView.getVisibility() == View.VISIBLE
                        && mediaStrip.root.getVisibility() == View.VISIBLE);
        updateMediaControlButtonState(
                mediaStrip.nextButton,
                safeSnapshot.active
                        && supportsAction(
                                safeSnapshot.availableActions,
                                PlaybackState.ACTION_SKIP_TO_NEXT),
                "下一曲");
    }

    private static void updateMediaControlButtonState(
            ImageView button,
            boolean enabled,
            CharSequence contentDescription) {
        if (button == null) {
            return;
        }
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.42f);
        button.setContentDescription(contentDescription);
    }

    private static boolean supportsAction(long availableActions, long targetAction) {
        return availableActions == 0L || (availableActions & targetAction) != 0L;
    }

    private static boolean supportsEitherAction(
            long availableActions,
            long primaryAction,
            long fallbackAction) {
        return supportsAction(availableActions, primaryAction)
                || supportsAction(availableActions, fallbackAction);
    }

    private static boolean isPausedPlaybackState(int playbackState) {
        switch (playbackState) {
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED:
            case PlaybackState.STATE_NONE:
            case PlaybackState.STATE_ERROR:
                return true;
            default:
                return false;
        }
    }

    private void applyOptimisticPlayPauseButtonState() {
        if (mediaStrip == null || latestMediaSnapshot == null || !latestMediaSnapshot.active) {
            return;
        }
        boolean paused = isPausedPlaybackState(latestMediaSnapshot.playbackState);
        updateMediaControlButtonState(
                mediaStrip.playPauseButton,
                mediaStrip.playPauseButton.isEnabled(),
                paused ? "暂停播放" : "继续播放");
        updatePlayPauseButtonIcon(
                mediaStrip,
                paused ? PLAY_PAUSE_ICON_MODE_PAUSE : PLAY_PAUSE_ICON_MODE_PLAY,
                true);
    }

    private void updatePlayPauseButtonIcon(MediaStrip strip, int targetMode, boolean animate) {
        if (strip == null || strip.playPauseButton == null) {
            return;
        }
        Drawable targetDrawable = resolvePlayPauseDrawable(strip, targetMode);
        if (targetDrawable == null) {
            return;
        }
        if (strip.playPauseIconMode == targetMode) {
            if (strip.playPauseButton.getDrawable() != targetDrawable) {
                strip.playPauseButton.setImageDrawable(targetDrawable);
            }
            return;
        }
        if (!animate || !strip.playPauseButton.isLaidOut()) {
            applyPlayPauseButtonIconImmediately(strip, targetMode, targetDrawable);
            return;
        }
        ImageView button = strip.playPauseButton;
        float targetAlpha = button.isEnabled() ? 1f : 0.42f;
        button.animate().cancel();
        button.animate()
                .alpha(Math.max(0.18f, targetAlpha * 0.24f))
                .scaleX(MEDIA_PLAY_PAUSE_ICON_SWAP_SCALE)
                .scaleY(MEDIA_PLAY_PAUSE_ICON_SWAP_SCALE)
                .setDuration(MEDIA_PLAY_PAUSE_ICON_OUT_DURATION_MS)
                .setInterpolator(POPUP_OUT_INTERPOLATOR)
                .withEndAction(() -> {
                    applyPlayPauseButtonIconImmediately(strip, targetMode, targetDrawable);
                    button.setAlpha(Math.max(0.18f, targetAlpha * 0.24f));
                    button.setScaleX(MEDIA_PLAY_PAUSE_ICON_SWAP_SCALE);
                    button.setScaleY(MEDIA_PLAY_PAUSE_ICON_SWAP_SCALE);
                    button.animate()
                            .alpha(targetAlpha)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(MEDIA_PLAY_PAUSE_ICON_IN_DURATION_MS)
                            .setInterpolator(POPUP_ALPHA_IN_INTERPOLATOR)
                            .start();
                })
                .start();
    }

    private static void applyPlayPauseButtonIconImmediately(
            MediaStrip strip,
            int targetMode,
            Drawable targetDrawable) {
        if (strip == null || strip.playPauseButton == null || targetDrawable == null) {
            return;
        }
        strip.playPauseButton.animate().cancel();
        strip.playPauseButton.setImageDrawable(targetDrawable);
        strip.playPauseButton.setScaleX(1f);
        strip.playPauseButton.setScaleY(1f);
        strip.playPauseIconMode = targetMode;
    }

    private static Drawable resolvePlayPauseDrawable(MediaStrip strip, int targetMode) {
        if (strip == null) {
            return null;
        }
        if (targetMode == PLAY_PAUSE_ICON_MODE_PAUSE) {
            return strip.pauseDrawable;
        }
        return strip.playDrawable;
    }

    private void launchActiveMediaApp() {
        ClockDetailMediaSnapshot snapshot = latestMediaSnapshot;
        if (snapshot == null || !snapshot.active) {
            return;
        }
        dismissImmediately();
        PendingIntent launchIntent = snapshot.launchIntent;
        if (launchIntent != null) {
            try {
                launchIntent.send();
                return;
            } catch (Throwable t) {
                FlymeStatusBarSizer.logClockWarning("Failed to open media session activity", t);
            }
        }
        String packageName = snapshot.packageName != null ? snapshot.packageName.trim() : "";
        if (packageName.isEmpty()) {
            return;
        }
        try {
            Intent launchAppIntent = contentView.getContext()
                    .getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            if (launchAppIntent == null) {
                return;
            }
            launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            contentView.getContext().startActivity(launchAppIntent);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to launch media app: " + packageName,
                    t);
        }
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
            ClockDetailRecentApp[] resolvedRecentApps = recentApps != null
                    ? recentApps
                    : latestRecentApps;
            latestRecentApps = resolvedRecentApps != null && resolvedRecentApps.length > 0
                    ? resolvedRecentApps
                    : ClockDetailRecentApp.EMPTY_ARRAY;
            if (!isPopupShowing()) {
                return;
            }
            if (updateRecentAppsView(latestRecentApps, shouldAnimateRecentAppsRefresh())) {
                requestPopupLayoutRefresh();
            }
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

    private void applyActionGridPalette(ActionGrid grid, Palette palette) {
        if (grid == null) {
            return;
        }
        Context context = grid.root.getContext();
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 14));
        background.setColor(mixColors(palette.surfaceColor, palette.strokeColor, 0.22f));
        background.setStroke(Math.max(1, dp(context, 1)), adjustAlpha(palette.strokeColor, 0.9f));
        grid.root.setBackground(background);
        for (int slot = 0; slot < grid.cellViews.length; slot++) {
            updateActionGridCellAppearance(grid.cellViews[slot], actionEntryAt(renderedActionEntries, slot), palette);
        }
    }

    private void updateActionGridCellAppearance(
            ActionGridCellView cellView,
            ClockDetailActionEntry entry,
            Palette palette) {
        if (cellView == null || palette == null) {
            return;
        }
        boolean hasLabel = entry != null && entry.hasDisplayLabel();
        boolean hasIcon = entry != null && entry.hasIcon();
        boolean hasVisualContent = hasLabel || hasIcon;
        boolean valid = entry != null && entry.valid;
        if (valid) {
            cellView.labelView.setTextColor(palette.primaryTextColor);
            cellView.iconView.setAlpha(1f);
            cellView.labelView.setAlpha(1f);
        } else if (hasVisualContent) {
            cellView.labelView.setTextColor(palette.secondaryTextColor);
            cellView.iconView.setAlpha(0.72f);
            cellView.labelView.setAlpha(0.78f);
        } else {
            cellView.labelView.setTextColor(palette.secondaryTextColor);
            cellView.iconView.setAlpha(0.32f);
            cellView.labelView.setAlpha(0.38f);
        }
        cellView.root.setBackground(null);
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
        if (strip.emptyView != null) {
            strip.emptyView.setTextColor(palette.secondaryTextColor);
        }
    }

    private void applyMediaStripPalette(MediaStrip strip, Palette palette) {
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
        strip.titleView.setTextColor(palette.primaryTextColor);
        strip.subtitleView.setTextColor(palette.secondaryTextColor);
        strip.statusView.setTextColor(palette.accentColor);
        strip.previousButton.setColorFilter(palette.primaryTextColor);
        strip.playPauseButton.setColorFilter(palette.accentColor);
        strip.nextButton.setColorFilter(palette.primaryTextColor);
        strip.previousButton.setBackground(buildMediaControlButtonBackground(context, palette, false));
        strip.playPauseButton.setBackground(buildMediaControlButtonBackground(context, palette, true));
        strip.nextButton.setBackground(buildMediaControlButtonBackground(context, palette, false));
    }

    private static Drawable buildMediaControlButtonBackground(
            Context context,
            Palette palette,
            boolean emphasize) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 12));
        if (emphasize) {
            background.setColor(mixColors(palette.surfaceColor, palette.accentColor, 0.14f));
            background.setStroke(
                    Math.max(1, dp(context, 1)),
                    adjustAlpha(palette.accentColor, 0.42f));
        } else {
            background.setColor(mixColors(palette.surfaceColor, palette.strokeColor, 0.3f));
            background.setStroke(
                    Math.max(1, dp(context, 1)),
                    adjustAlpha(palette.strokeColor, 0.82f));
        }
        return background;
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

    private boolean shouldAnimateRecentAppsRefresh() {
        return popupTargetShowing
                && !dismissAnimationRunning
                && !enterAnimationRunning;
    }

    private boolean updateActionGridView(ClockDetailActionEntry[] entries) {
        if (actionGrid == null) {
            return false;
        }
        bindActionGridContent(entries);
        return true;
    }

    private void bindActionGridContent(ClockDetailActionEntry[] entries) {
        if (actionGrid == null) {
            return;
        }
        ClockDetailActionEntry[] safeEntries = normalizeActionEntries(entries);
        renderedActionEntries = safeEntries;
        setVisibilityIfChanged(
                actionGrid.root,
                hasVisibleActionEntries(safeEntries) ? View.VISIBLE : View.GONE);
        for (int slot = 0; slot < actionGrid.cellViews.length; slot++) {
            final ClockDetailActionEntry entry = safeEntries[slot];
            ActionGridCellView cellView = actionGrid.cellViews[slot];
            boolean hasLabel = entry != null && entry.hasDisplayLabel();
            boolean hasIcon = entry != null && entry.hasIcon();
            boolean hasVisualContent = hasLabel || hasIcon;
            boolean canLaunch = canLaunchActionEntry(entry);
            setVisibilityIfChanged(cellView.root, hasVisualContent ? View.VISIBLE : View.GONE);
            setTextIfChanged(cellView.labelView, hasLabel ? entry.resolvedLabel : "");
            setVisibilityIfChanged(cellView.iconView, hasIcon ? View.VISIBLE : View.GONE);
            cellView.iconView.setImageDrawable(hasIcon ? entry.icon : null);
            cellView.root.setEnabled(canLaunch);
            cellView.root.setClickable(canLaunch);
            cellView.root.setFocusable(canLaunch);
            cellView.root.setContentDescription(hasLabel ? entry.resolvedLabel : "");
            cellView.root.setOnClickListener(canLaunch ? v -> {
                performClockHaptic(v);
                launchActionEntry(entry);
            } : null);
            if (currentPalette != null) {
                updateActionGridCellAppearance(cellView, entry, currentPalette);
            }
        }
    }

    private boolean updateRecentAppsView(ClockDetailRecentApp[] recentApps, boolean animate) {
        if (recentAppsStrip == null) {
            return false;
        }
        ClockDetailRecentApp[] safeRecentApps = recentApps != null && recentApps.length > 0
                ? recentApps
                : ClockDetailRecentApp.EMPTY_ARRAY;
        boolean hasApps = safeRecentApps.length > 0;
        boolean shouldKeepVisible = shouldKeepRecentAppsContainerVisible(hasApps);
        if (areRecentAppsEquivalent(renderedRecentApps, safeRecentApps)) {
            resetRecentAppsStripVisualState();
            boolean visibilityChanged = setVisibilityIfChanged(
                    recentAppsStrip.root,
                    shouldKeepVisible ? View.VISIBLE : View.GONE);
            updateRecentAppsEmptyState(hasApps);
            if (!hasApps) {
                recentAppsStrip.scrollView.scrollTo(0, 0);
            }
            return visibilityChanged;
        }
        if (!animate || !hasApps || !recentAppsStrip.root.isLaidOut()) {
            applyRecentAppsContent(safeRecentApps);
            return true;
        }
        animateRecentAppsViewChange(safeRecentApps);
        return true;
    }

    private boolean shouldKeepRecentAppsContainerVisible(boolean hasApps) {
        return hasApps;
    }

    private void updateRecentAppsEmptyState(boolean hasApps) {
        if (recentAppsStrip == null) {
            return;
        }
        setVisibilityIfChanged(recentAppsStrip.scrollView, hasApps ? View.VISIBLE : View.GONE);
        if (recentAppsStrip.emptyView != null) {
            setVisibilityIfChanged(recentAppsStrip.emptyView, View.GONE);
        }
    }

    private void applyRecentAppsContent(ClockDetailRecentApp[] recentApps) {
        cancelRecentAppsStripAnimations();
        resetRecentAppsStripVisualState();
        bindRecentAppsContent(recentApps);
    }

    private void bindRecentAppsContent(ClockDetailRecentApp[] recentApps) {
        if (recentAppsStrip == null) {
            return;
        }
        ClockDetailRecentApp[] safeRecentApps = recentApps != null && recentApps.length > 0
                ? recentApps
                : ClockDetailRecentApp.EMPTY_ARRAY;
        boolean hasApps = safeRecentApps.length > 0;
        setVisibilityIfChanged(
                recentAppsStrip.root,
                shouldKeepRecentAppsContainerVisible(hasApps) ? View.VISIBLE : View.GONE);
        recentAppsStrip.contentView.removeAllViews();
        renderedRecentApps = safeRecentApps;
        updateRecentAppsEmptyState(hasApps);
        if (!hasApps) {
            recentAppsStrip.scrollView.scrollTo(0, 0);
            return;
        }
        Context context = recentAppsStrip.root.getContext();
        bindClockRecentAppsContent(context, safeRecentApps);
        recentAppsStrip.scrollView.scrollTo(0, 0);
    }

    private void bindClockRecentAppsContent(
            Context context,
            ClockDetailRecentApp[] recentApps) {
        for (int i = 0; i < recentApps.length; i++) {
            ClockDetailRecentApp app = recentApps[i];
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
    }

    private void animateRecentAppsViewChange(ClockDetailRecentApp[] recentApps) {
        if (recentAppsStrip == null) {
            return;
        }
        ClockDetailRecentApp[] safeRecentApps = recentApps != null && recentApps.length > 0
                ? recentApps
                : ClockDetailRecentApp.EMPTY_ARRAY;
        cancelRecentAppsStripAnimations();
        resetRecentAppsStripVisualState();
        bindRecentAppsContent(safeRecentApps);
    }

    private void cancelRecentAppsStripAnimations() {
        if (recentAppsStrip == null) {
            return;
        }
        recentAppsStrip.root.animate().cancel();
        recentAppsStrip.contentView.animate().cancel();
    }

    private void resetRecentAppsStripVisualState() {
        if (recentAppsStrip == null) {
            return;
        }
        recentAppsStrip.root.setAlpha(1f);
        recentAppsStrip.root.setTranslationY(0f);
        recentAppsStrip.contentView.setAlpha(1f);
        recentAppsStrip.contentView.setTranslationY(0f);
    }

    private static boolean areRecentAppsEquivalent(
            ClockDetailRecentApp[] first,
            ClockDetailRecentApp[] second) {
        ClockDetailRecentApp[] safeFirst = first != null ? first : ClockDetailRecentApp.EMPTY_ARRAY;
        ClockDetailRecentApp[] safeSecond = second != null ? second : ClockDetailRecentApp.EMPTY_ARRAY;
        if (safeFirst.length != safeSecond.length) {
            return false;
        }
        for (int i = 0; i < safeFirst.length; i++) {
            if (!areRecentAppsEquivalent(safeFirst[i], safeSecond[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean areRecentAppsEquivalent(
            ClockDetailRecentApp first,
            ClockDetailRecentApp second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        if (first.taskId != second.taskId || first.userId != second.userId) {
            return false;
        }
        String firstLabel = first.label != null ? first.label.toString() : "";
        String secondLabel = second.label != null ? second.label.toString() : "";
        return firstLabel.equals(secondLabel)
                && TextUtils.equals(first.packageName, second.packageName);
    }

    private static ClockDetailActionEntry[] normalizeActionEntries(ClockDetailActionEntry[] entries) {
        ClockDetailActionEntry[] normalized = new ClockDetailActionEntry[ClockDetailActionSpec.SLOT_COUNT];
        for (int slot = 0; slot < normalized.length; slot++) {
            normalized[slot] = actionEntryAt(entries, slot);
        }
        return normalized;
    }

    private static ClockDetailActionEntry actionEntryAt(ClockDetailActionEntry[] entries, int slot) {
        if (entries == null
                || slot < 0
                || slot >= ClockDetailActionSpec.SLOT_COUNT
                || slot >= entries.length) {
            return ClockDetailActionEntry.empty(slot);
        }
        ClockDetailActionEntry entry = entries[slot];
        return entry != null ? entry : ClockDetailActionEntry.empty(slot);
    }

    private static boolean hasVisibleActionEntries(ClockDetailActionEntry[] entries) {
        if (entries == null || entries.length == 0) {
            return false;
        }
        for (ClockDetailActionEntry entry : entries) {
            if (entry != null && (entry.hasDisplayLabel() || entry.hasIcon())) {
                return true;
            }
        }
        return false;
    }

    private static boolean canLaunchActionEntry(ClockDetailActionEntry entry) {
        return entry != null && entry.valid && entry.canAttemptLaunch();
    }

    private void launchActionEntry(ClockDetailActionEntry entry) {
        if (entry == null
                || !entry.valid
                || !isPopupShowing()
                || !popupTargetShowing
                || dismissAnimationRunning) {
            return;
        }
        Intent launchIntent = resolveActionLaunchIntent(entry);
        if (launchIntent == null) {
            return;
        }
        dismissImmediately();
        try {
            Context context = contentView.getContext();
            Context launchContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            launchContext.startActivity(launchIntent);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to launch clock detail action: " + entry.resolvedLabel,
                    t);
        }
    }

    private Intent resolveActionLaunchIntent(ClockDetailActionEntry entry) {
        if (entry == null || !entry.valid) {
            return null;
        }
        if (entry.launchIntent != null) {
            return new Intent(entry.launchIntent);
        }
        if (!ClockDetailActionSpec.TYPE_ASSISTANT_ACTION.equals(entry.type)
                || entry.assistantAction.isEmpty()) {
            return null;
        }
        Intent launchIntent = ClockDetailAssistantActionCatalog.buildLaunchIntent(
                contentView.getContext(),
                entry.assistantAction);
        return launchIntent == null ? null : new Intent(launchIntent);
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

    private static ActionGrid buildActionGrid(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(
                dp(context, 8),
                dp(context, 4),
                dp(context, 8),
                dp(context, 4));
        root.setVisibility(View.GONE);

        ActionGridCellView[] cellViews = new ActionGridCellView[ClockDetailActionSpec.SLOT_COUNT];
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            ActionGridCellView cellView = buildActionGridCellView(context);
            cellViews[slot] = cellView;
            root.addView(
                    cellView.root,
                    slot == 0
                            ? actionGridCellLayoutParams()
                            : actionGridCellLayoutParamsWithStart(
                                    context,
                                    ACTION_GRID_COLUMN_GAP_DP));
        }
        return new ActionGrid(root, cellViews);
    }

    private static ActionGridCellView buildActionGridCellView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setMinimumHeight(dp(context, ACTION_GRID_CELL_MIN_HEIGHT_DP));
        root.setPadding(
                dp(context, ACTION_GRID_CELL_HORIZONTAL_PADDING_DP),
                dp(context, ACTION_GRID_CELL_VERTICAL_PADDING_DP),
                dp(context, ACTION_GRID_CELL_HORIZONTAL_PADDING_DP),
                dp(context, ACTION_GRID_CELL_VERTICAL_PADDING_DP));
        ImageView iconView = new ImageView(context);
        int iconSize = dp(context, ACTION_GRID_ICON_SIZE_DP);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        styleRoundedActionGridIcon(iconView, dp(context, 10));
        root.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView labelView = new TextView(context);
        labelView.setIncludeFontPadding(false);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelView.setVisibility(View.GONE);
        root.addView(labelView, matchWidthWithTop(context, ACTION_GRID_LABEL_TOP_MARGIN_DP));
        return new ActionGridCellView(root, iconView, labelView);
    }

    private static void styleRoundedActionGridIcon(ImageView iconView, int radiusPx) {
        if (iconView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        iconView.setClipToOutline(true);
        iconView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
    }

    private static RecentAppsStrip buildRecentAppsStrip(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.START);
        root.setPadding(
                dp(context, 12),
                dp(context, 10),
                dp(context, 12),
                dp(context, 10));
        root.setVisibility(View.GONE);

        TextView emptyView = null;

        HorizontalScrollView scrollView = new HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);

        scrollView.addView(
                content,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(
                scrollView,
                matchWidth());
        return new RecentAppsStrip(root, emptyView, scrollView, content);
    }

    private static MediaStrip buildMediaStrip(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(
                dp(context, 12),
                dp(context, 10),
                dp(context, 12),
                dp(context, 10));
        root.setVisibility(View.GONE);
        root.setClickable(true);

        ImageView artworkView = new ImageView(context);
        artworkView.setAdjustViewBounds(false);
        artworkView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setIncludeFontPadding(false);
        titleView.setSingleLine(true);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView subtitleView = new TextView(context);
        subtitleView.setIncludeFontPadding(false);
        subtitleView.setSingleLine(true);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        subtitleView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        TextView statusView = new TextView(context);
        statusView.setIncludeFontPadding(false);
        statusView.setSingleLine(true);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        statusView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        LinearLayout controlsContainer = new LinearLayout(context);
        controlsContainer.setOrientation(LinearLayout.HORIZONTAL);
        controlsContainer.setGravity(Gravity.CENTER_VERTICAL);

        int iconSize = dp(context, MEDIA_CONTROL_BUTTON_ICON_SIZE_DP);
        Drawable playDrawable = ClockDetailMediaIcons.createPlayDrawable(iconSize);
        Drawable pauseDrawable = ClockDetailMediaIcons.createPauseDrawable(iconSize);
        ImageView previousButton = buildMediaControlButton(
                context,
                ClockDetailMediaIcons.createPreviousDrawable(iconSize));
        previousButton.setContentDescription("上一曲");
        ImageView playPauseButton = buildMediaControlButton(
                context,
                playDrawable);
        playPauseButton.setContentDescription("继续播放");
        ImageView nextButton = buildMediaControlButton(
                context,
                ClockDetailMediaIcons.createNextDrawable(iconSize));
        nextButton.setContentDescription("下一曲");

        controlsContainer.addView(previousButton, mediaControlButtonLayoutParams(context));
        controlsContainer.addView(
                playPauseButton,
                mediaControlButtonLayoutParamsWithStart(context, MEDIA_CONTROL_GAP_DP));
        controlsContainer.addView(
                nextButton,
                mediaControlButtonLayoutParamsWithStart(context, MEDIA_CONTROL_GAP_DP));

        textContainer.addView(titleView, matchWidth());
        textContainer.addView(subtitleView, matchWidthWithTop(context, 2));
        textContainer.addView(statusView, matchWidthWithTop(context, 3));

        int artworkSize = dp(context, MEDIA_ARTWORK_SIZE_DP);
        root.addView(
                artworkView,
                new LinearLayout.LayoutParams(artworkSize, artworkSize));
        root.addView(
                textContainer,
                weightCellWithStart(context, MEDIA_CONTENT_GAP_DP, 1f));
        root.addView(
                controlsContainer,
                wrapContentWithStart(context, MEDIA_CONTENT_GAP_DP));
        return new MediaStrip(
                root,
                artworkView,
                titleView,
                subtitleView,
                statusView,
                playDrawable,
                pauseDrawable,
                previousButton,
                playPauseButton,
                nextButton);
    }

    private static ImageView buildMediaControlButton(Context context, Drawable drawable) {
        ImageView button = new ImageView(context);
        button.setAdjustViewBounds(false);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setClickable(true);
        button.setFocusable(true);
        int padding = dp(context, MEDIA_CONTROL_BUTTON_PADDING_DP);
        button.setPadding(padding, padding, padding, padding);
        button.setImageDrawable(drawable);
        return button;
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

    private static LinearLayout.LayoutParams actionGridCellLayoutParams() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
    }

    private static LinearLayout.LayoutParams actionGridCellLayoutParamsWithStart(
            Context context,
            int startMarginDp) {
        LinearLayout.LayoutParams params = actionGridCellLayoutParams();
        params.leftMargin = dp(context, startMarginDp);
        return params;
    }

    private static LinearLayout.LayoutParams mediaControlButtonLayoutParams(Context context) {
        int size = dp(context, MEDIA_CONTROL_BUTTON_SIZE_DP);
        return new LinearLayout.LayoutParams(size, size);
    }

    private static LinearLayout.LayoutParams mediaControlButtonLayoutParamsWithStart(
            Context context,
            int startMarginDp) {
        LinearLayout.LayoutParams params = mediaControlButtonLayoutParams(context);
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

    private View getAnchor() {
        return anchorRef.get();
    }

    enum HostMode {
        CLOCK,
        MBACK
    }

    private enum SwipeMode {
        NONE,
        DETAILS_REVEAL,
        POPUP_DISMISS
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

    private static final class ActionGrid {
        final LinearLayout root;
        final ActionGridCellView[] cellViews;

        ActionGrid(LinearLayout root, ActionGridCellView[] cellViews) {
            this.root = root;
            this.cellViews = cellViews;
        }
    }

    private static final class ActionGridCellView {
        final LinearLayout root;
        final ImageView iconView;
        final TextView labelView;

        ActionGridCellView(
                LinearLayout root,
                ImageView iconView,
                TextView labelView) {
            this.root = root;
            this.iconView = iconView;
            this.labelView = labelView;
        }
    }

    private static final class RecentAppsStrip {
        final LinearLayout root;
        final TextView emptyView;
        final HorizontalScrollView scrollView;
        final LinearLayout contentView;

        RecentAppsStrip(
                LinearLayout root,
                TextView emptyView,
                HorizontalScrollView scrollView,
                LinearLayout contentView) {
            this.root = root;
            this.emptyView = emptyView;
            this.scrollView = scrollView;
            this.contentView = contentView;
        }
    }

    private static final class MediaStrip {
        final LinearLayout root;
        final ImageView artworkView;
        final TextView titleView;
        final TextView subtitleView;
        final TextView statusView;
        final Drawable playDrawable;
        final Drawable pauseDrawable;
        final ImageView previousButton;
        final ImageView playPauseButton;
        final ImageView nextButton;
        int playPauseIconMode = PLAY_PAUSE_ICON_MODE_PLAY;

        MediaStrip(
                LinearLayout root,
                ImageView artworkView,
                TextView titleView,
                TextView subtitleView,
                TextView statusView,
                Drawable playDrawable,
                Drawable pauseDrawable,
                ImageView previousButton,
                ImageView playPauseButton,
                ImageView nextButton) {
            this.root = root;
            this.artworkView = artworkView;
            this.titleView = titleView;
            this.subtitleView = subtitleView;
            this.statusView = statusView;
            this.playDrawable = playDrawable;
            this.pauseDrawable = pauseDrawable;
            this.previousButton = previousButton;
            this.playPauseButton = playPauseButton;
            this.nextButton = nextButton;
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
