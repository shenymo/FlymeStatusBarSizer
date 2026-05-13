package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class ClockDetailPopupController {
    private static final long AUTO_DISMISS_DELAY_MS = 8000L;
    private static final long REFRESH_INTERVAL_MS = 33L;
    private static final long SYSTEM_STATUS_REFRESH_INTERVAL_MS = 1000L;
    private static final int HORIZONTAL_MARGIN_DP = 16;
    private static final int STATUS_TILE_GAP_DP = 8;

    private final WeakReference<TextView> anchorRef;
    private final Handler handler;
    private final LinearLayout contentView;
    private final TextView timeView;
    private final TextView dateView;
    private final LinearLayout statusGridView;
    private final StatTile memoryTile;
    private final StatTile temperatureTile;
    private final StatTile powerTile;
    private final PopupWindow popupWindow;
    private final ClockDetailSystemStatusProvider systemStatusProvider;
    private final Runnable refreshRunnable = this::refreshVisibleContent;
    private final Runnable systemStatusRefreshRunnable = this::refreshVisibleSystemStatus;
    private final Runnable autoDismissRunnable = this::dismiss;

    private boolean enabled;
    private Palette currentPalette;
    private Locale cachedLocale;
    private TimeZone cachedTimeZone;
    private boolean cached24HourMode;
    private SimpleDateFormat timeFormatter;
    private SimpleDateFormat dateFormatter;
    private int popupWidth;
    private int popupHeight;

    ClockDetailPopupController(TextView anchor) {
        this.anchorRef = new WeakReference<>(anchor);
        Handler mainHandler = FlymeStatusBarSizer.getMainHandler();
        this.handler = mainHandler != null
                ? mainHandler
                : new Handler(anchor.getContext().getMainLooper());
        this.contentView = buildContentView(anchor.getContext());
        this.timeView = buildTimeView(anchor.getContext());
        this.dateView = buildDateView(anchor.getContext());
        this.memoryTile = buildStatTile(anchor.getContext(), "系统内存");
        this.temperatureTile = buildStatTile(anchor.getContext(), "电池温度");
        this.powerTile = buildStatTile(anchor.getContext(), "当前功率");
        this.statusGridView = buildStatusGrid(anchor.getContext(), memoryTile, temperatureTile, powerTile);
        this.contentView.addView(timeView, matchWidth());
        this.contentView.addView(dateView, matchWidthWithTop(anchor.getContext(), 5));
        this.contentView.addView(statusGridView, matchWidthWithTop(anchor.getContext(), 12));
        this.popupWindow = new PopupWindow(
                contentView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);
        this.systemStatusProvider = new ClockDetailSystemStatusProvider(anchor.getContext());
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setElevation(dp(anchor.getContext(), 18));
        anchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                dismiss();
            }
        });
        anchor.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!popupWindow.isShowing()) {
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
            dismiss();
            return;
        }
        boolean shouldEnable = config != null && config.clockDetailPopupEnabled;
        enabled = shouldEnable;
        anchor.setHapticFeedbackEnabled(shouldEnable);
        anchor.setClickable(shouldEnable);
        anchor.setOnClickListener(shouldEnable ? v -> toggle() : null);
        if (!shouldEnable) {
            dismiss();
        }
    }

    void dismiss() {
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(systemStatusRefreshRunnable);
        handler.removeCallbacks(autoDismissRunnable);
        contentView.animate().cancel();
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    private void toggle() {
        if (!enabled) {
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
        FlymeStatusBarSizer.disableAncestorClipping(anchor, 6);
        applyPalette(resolvePalette());
        refreshContent();
        refreshSystemStatus();
        measureContent();
        int xOffset = calculateXOffset();
        int yOffset = dp(anchor.getContext(), 8);
        popupWindow.setWidth(popupWidth);
        popupWindow.setHeight(popupHeight);
        popupWindow.showAsDropDown(anchor, xOffset, yOffset, Gravity.START);
        animatePopupIn();
        scheduleAutoDismiss();
        scheduleRefresh();
        scheduleSystemStatusRefresh();
    }

    private void refreshVisibleContent() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismiss();
            return;
        }
        if (!popupWindow.isShowing() || !anchor.isAttachedToWindow()) {
            dismiss();
            return;
        }
        refreshContent();
        handler.postAtTime(refreshRunnable, SystemClock.uptimeMillis() + REFRESH_INTERVAL_MS);
    }

    private void refreshVisibleSystemStatus() {
        TextView anchor = getAnchor();
        if (anchor == null) {
            dismiss();
            return;
        }
        if (!popupWindow.isShowing() || !anchor.isAttachedToWindow()) {
            dismiss();
            return;
        }
        refreshSystemStatus();
        handler.postAtTime(
                systemStatusRefreshRunnable,
                SystemClock.uptimeMillis() + SYSTEM_STATUS_REFRESH_INTERVAL_MS);
    }

    private void refreshContent() {
        boolean formattersChanged = ensureFormatters();
        Date now = new Date(System.currentTimeMillis());
        timeView.setText(buildTimeText(now));
        dateView.setText(dateFormatter != null ? dateFormatter.format(now) : "");
        if (formattersChanged && popupWindow.isShowing()) {
            measureContent();
            updatePopupPosition();
        }
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

    private CharSequence buildTimeText(Date now) {
        String baseTime = timeFormatter != null ? timeFormatter.format(now) : "";
        Calendar calendar = Calendar.getInstance(
                cachedTimeZone != null ? cachedTimeZone : TimeZone.getDefault(),
                cachedLocale != null ? cachedLocale : Locale.getDefault());
        calendar.setTime(now);
        String millis = String.format(
                cachedLocale != null ? cachedLocale : Locale.getDefault(),
                "%03d",
                calendar.get(Calendar.MILLISECOND));
        String fullText = baseTime + "." + millis;
        SpannableStringBuilder builder = new SpannableStringBuilder(fullText);
        int millisStart = baseTime.length();
        builder.setSpan(
                new RelativeSizeSpan(0.52f),
                millisStart,
                fullText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(
                new ForegroundColorSpan(currentPalette != null
                        ? currentPalette.accentColor
                        : Color.parseColor("#005CAE")),
                millisStart,
                fullText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private void scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable);
        if (!popupWindow.isShowing()) {
            return;
        }
        handler.postAtTime(refreshRunnable, SystemClock.uptimeMillis() + REFRESH_INTERVAL_MS);
    }

    private void scheduleSystemStatusRefresh() {
        handler.removeCallbacks(systemStatusRefreshRunnable);
        if (!popupWindow.isShowing()) {
            return;
        }
        handler.postAtTime(
                systemStatusRefreshRunnable,
                SystemClock.uptimeMillis() + SYSTEM_STATUS_REFRESH_INTERVAL_MS);
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
            dismiss();
            return;
        }
        if (!popupWindow.isShowing()) {
            return;
        }
        popupWindow.update(
                anchor,
                calculateXOffset(),
                dp(anchor.getContext(), 8),
                popupWidth,
                popupHeight);
    }

    private void measureContent() {
        android.content.Context context = contentView.getContext();
        int margin = dp(context, HORIZONTAL_MARGIN_DP);
        popupWidth = Math.max(1, context.getResources().getDisplayMetrics().widthPixels - (margin * 2));
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popupHeight = Math.max(contentView.getMeasuredHeight(), 0);
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

    private void animatePopupIn() {
        contentView.animate().cancel();
        contentView.setAlpha(0f);
        contentView.setTranslationY(-dp(contentView.getContext(), 6));
        contentView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160L)
                .start();
    }

    private void applyPalette(Palette palette) {
        android.content.Context context = contentView.getContext();
        currentPalette = palette;
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(palette.surfaceColor);
        background.setCornerRadius(dp(context, 20));
        background.setStroke(Math.max(1, dp(context, 1)), palette.strokeColor);
        contentView.setBackground(background);
        timeView.setTextColor(palette.primaryTextColor);
        dateView.setTextColor(palette.secondaryTextColor);
        applyStatTilePalette(memoryTile, palette);
        applyStatTilePalette(temperatureTile, palette);
        applyStatTilePalette(powerTile, palette);
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

    private static LinearLayout buildContentView(android.content.Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(
                dp(context, 18),
                dp(context, 14),
                dp(context, 18),
                dp(context, 14));
        root.setClickable(true);
        root.setFocusable(true);
        return root;
    }

    private static TextView buildTimeView(android.content.Context context) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.01f);
        return view;
    }

    private static TextView buildDateView(android.content.Context context) {
        TextView view = new TextView(context);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return view;
    }

    private void refreshSystemStatus() {
        ClockDetailSystemStatusSnapshot snapshot = systemStatusProvider.querySnapshot();
        memoryTile.valueView.setText(snapshot.memoryValue);
        temperatureTile.valueView.setText(snapshot.temperatureValue);
        powerTile.valueView.setText(snapshot.powerValue);
    }

    private void applyStatTilePalette(StatTile tile, Palette palette) {
        if (tile == null) {
            return;
        }
        android.content.Context context = tile.root.getContext();
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 14));
        background.setColor(mixColors(palette.surfaceColor, palette.strokeColor, 0.22f));
        background.setStroke(Math.max(1, dp(context, 1)), adjustAlpha(palette.strokeColor, 0.9f));
        tile.root.setBackground(background);
        tile.labelView.setTextColor(palette.secondaryTextColor);
        tile.valueView.setTextColor(palette.primaryTextColor);
    }

    private static LinearLayout buildStatusGrid(
            android.content.Context context,
            StatTile memoryTile,
            StatTile temperatureTile,
            StatTile powerTile) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topRow = buildStatusRow(context);
        topRow.addView(memoryTile.root, weightCell());
        topRow.addView(temperatureTile.root, weightCellWithStart(context, STATUS_TILE_GAP_DP));

        LinearLayout bottomRow = buildStatusRow(context);
        bottomRow.addView(powerTile.root, weightCell());
        View placeholder = new View(context);
        placeholder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bottomRow.addView(placeholder, weightCellWithStart(context, STATUS_TILE_GAP_DP));

        grid.addView(topRow, matchWidth());
        grid.addView(bottomRow, matchWidthWithTop(context, STATUS_TILE_GAP_DP));
        return grid;
    }

    private static LinearLayout buildStatusRow(android.content.Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private static StatTile buildStatTile(android.content.Context context, String label) {
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
        valueView.setSingleLine(true);
        valueView.setText("--");
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        valueView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        root.addView(labelView, matchWidth());
        root.addView(valueView, matchWidthWithTop(context, 4));
        return new StatTile(root, labelView, valueView);
    }

    private static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams matchWidthWithTop(
            android.content.Context context,
            int topMarginDp) {
        LinearLayout.LayoutParams params = matchWidth();
        params.topMargin = dp(context, topMarginDp);
        return params;
    }

    private static LinearLayout.LayoutParams weightCell() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
    }

    private static LinearLayout.LayoutParams weightCellWithStart(
            android.content.Context context,
            int startMarginDp) {
        LinearLayout.LayoutParams params = weightCell();
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

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private TextView getAnchor() {
        return anchorRef.get();
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
