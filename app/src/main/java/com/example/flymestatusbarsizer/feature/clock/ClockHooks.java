package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.WeakHashMap;

public final class ClockHooks {
    private static final WeakHashMap<TextView, Float> ORIGINAL_TEXT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> ORIGINAL_INCLUDE_FONT_PADDING =
            new WeakHashMap<>();
    private static final WeakHashMap<TextView, Float> ORIGINAL_TEXT_TRANSLATION_Y =
            new WeakHashMap<>();
    private static final WeakHashMap<TextView, int[]> ORIGINAL_LAYOUT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, int[]> ORIGINAL_PADDINGS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> TRACKED_CLOCK_AND_CARRIER_TEXT_VIEWS =
            new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> CLOCK_SECOND_REFRESH_VIEWS =
            new WeakHashMap<>();
    private static final WeakHashMap<TextView, ClockDetailPopupController> CLOCK_DETAIL_POPUPS =
            new WeakHashMap<>();
    private static final Runnable CLOCK_SECOND_REFRESH_RUNNABLE =
            ClockHooks::refreshClockViewsForSecondTick;

    private ClockHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookClockWeekday(module, loader);
        hookClockAndCarrierTextSize(module, loader);
        hookClockPaddingRefresh(module, loader);
    }

    public static void refreshTrackedViews() {
        Handler handler = FlymeStatusBarSizer.getMainHandler();
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<TextView> textViews =
                    new ArrayList<>(TRACKED_CLOCK_AND_CARRIER_TEXT_VIEWS.keySet());
            for (TextView textView : textViews) {
                if (textView == null) {
                    continue;
                }
                updateClockSecondRefreshTracking(textView);
                refreshClockTextIfNeeded(textView);
                syncClockDetailPopup(textView);
                applyClockFontWeight(textView);
                applyClockAndCarrierTextSize(textView);
            }
        });
    }

    private static void hookClockWeekday(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.android.systemui.statusbar.policy.Clock", false, loader);
            Method method = clazz.getDeclaredMethod("getSmallTime");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (!(thisObject instanceof TextView) || !(result instanceof CharSequence)) {
                    return result;
                }
                TextView clock = (TextView) thisObject;
                FlymeStatusBarSizer.ClockConfigSnapshot config =
                        FlymeStatusBarSizer.loadClockConfig(clock.getContext());
                if (!config.enabled || !isPrimaryStatusBarClockView(clock)) {
                    return result;
                }
                Calendar calendar = resolveClockCalendar(clock);
                CharSequence customText = buildCustomClockText(clock, config, calendar);
                return customText != null ? customText : result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to hook Clock weekday", t);
        }
    }

    private static void hookClockAndCarrierTextSize(FlymeStatusBarSizer module, ClassLoader loader) {
        hookConstructors(module, loader, "com.android.systemui.statusbar.policy.Clock", view -> {
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                trackClockAndCarrierTextView(textView);
                installClockDetailPopupIfNeeded(textView);
                scheduleClockAndCarrierTextRelayout(textView);
                applyClockFontWeight(textView);
                applyClockAndCarrierTextSize(textView);
            }
        });
        hookConstructors(module, loader, "com.android.keyguard.CarrierText", view -> {
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                trackClockAndCarrierTextView(textView);
                scheduleClockAndCarrierTextRelayout(textView);
                applyClockAndCarrierTextSize(textView);
            }
        });
    }

    private static void hookClockPaddingRefresh(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.android.systemui.statusbar.policy.Clock", false, loader);
            Method method = clazz.getDeclaredMethod("reloadDimens");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof TextView) {
                    syncClockDetailPopup((TextView) thisObject);
                    applyClockFontWeight((TextView) thisObject);
                    applyClockAndCarrierTextSize((TextView) thisObject);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to hook Clock.reloadDimens", t);
        }
    }

    private static void hookConstructors(
            FlymeStatusBarSizer module, ClassLoader loader, String className, ViewAction action) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    Object thisObject = chain.getThisObject();
                    if (thisObject instanceof View) {
                        View view = (View) thisObject;
                        view.post(() -> action.apply(view));
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to hook " + className, t);
        }
    }

    private static CharSequence buildCustomClockText(
            TextView clock, FlymeStatusBarSizer.ClockConfigSnapshot config, Calendar calendar) {
        if (clock == null || config == null) {
            return null;
        }
        String format = config.clockCustomFormat;
        if (format == null || format.trim().isEmpty()) {
            return null;
        }
        Locale locale = resolveClockLocale(clock);
        return renderClockExpression(format, calendar, locale);
    }

    private static CharSequence renderClockExpression(
            String format, Calendar calendar, Locale locale) {
        if (format == null || format.isEmpty()) {
            return "";
        }
        Calendar safeCalendar = calendar != null ? calendar : Calendar.getInstance();
        Locale safeLocale = locale != null ? locale : Locale.getDefault();
        String result = format;
        result = result.replace("{HH}", pad2(safeCalendar.get(Calendar.HOUR_OF_DAY)));
        result = result.replace("{H}", Integer.toString(safeCalendar.get(Calendar.HOUR_OF_DAY)));
        result = result.replace("{hh}", pad2(resolveHour12(safeCalendar)));
        result = result.replace("{h}", Integer.toString(resolveHour12(safeCalendar)));
        result = result.replace("{mm}", pad2(safeCalendar.get(Calendar.MINUTE)));
        result = result.replace("{ss}", pad2(safeCalendar.get(Calendar.SECOND)));
        result = result.replace("{week}", resolveWeekdayLabel(safeCalendar, safeLocale, true));
        result = result.replace("{week_short}", resolveWeekdayLabel(safeCalendar, safeLocale, false));
        result = result.replace("{week_1}", resolveWeekdaySingleLabel(safeCalendar, safeLocale));
        result = result.replace("{ampm}", resolveAmPmLabel(safeCalendar));
        result = result.replace("{period}", resolveTraditionalPeriod(safeCalendar));
        result = result.replace("{branch}", resolveEarthlyBranch(safeCalendar));
        result = result.replace("{branch_alias}", resolveEarthlyBranchAlias(safeCalendar));
        return result;
    }

    private static Calendar resolveClockCalendar(TextView clock) {
        if (clock == null) {
            return Calendar.getInstance();
        }
        Object calendar = FlymeStatusBarSizer.getFieldCompat(clock, "mCalendar");
        if (calendar instanceof Calendar) {
            return (Calendar) calendar;
        }
        return Calendar.getInstance();
    }

    private static Locale resolveClockLocale(TextView clock) {
        if (clock == null) {
            return Locale.getDefault();
        }
        Object locale = FlymeStatusBarSizer.getFieldCompat(clock, "mLocale");
        if (locale instanceof Locale) {
            return (Locale) locale;
        }
        Configuration configuration = clock.getContext() != null
                ? clock.getContext().getResources().getConfiguration()
                : null;
        if (configuration != null && configuration.locale != null) {
            return configuration.locale;
        }
        return Locale.getDefault();
    }

    private static int resolveHour12(Calendar calendar) {
        int hour = calendar == null ? 0 : calendar.get(Calendar.HOUR);
        return hour == 0 ? 12 : hour;
    }

    private static String pad2(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static String resolveWeekdayLabel(Calendar calendar, Locale locale, boolean full) {
        DateFormatSymbols symbols = new DateFormatSymbols(locale != null ? locale : Locale.getDefault());
        String[] labels = full ? symbols.getWeekdays() : symbols.getShortWeekdays();
        int dayOfWeek = calendar == null ? Calendar.SUNDAY : calendar.get(Calendar.DAY_OF_WEEK);
        if (labels == null || dayOfWeek < 0 || dayOfWeek >= labels.length) {
            return "";
        }
        String label = labels[dayOfWeek];
        if (label == null) {
            return "";
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (full && !trimmed.startsWith("星期") && !trimmed.startsWith("周") && !trimmed.startsWith("週")
                && isChineseLocale(locale)) {
            return "星期" + resolveWeekdaySingleLabel(calendar, locale);
        }
        return trimmed;
    }

    private static String resolveWeekdaySingleLabel(Calendar calendar, Locale locale) {
        String shortLabel = trimWeekdayLabel(resolveWeekdayLabel(calendar, locale, false));
        if (!shortLabel.isEmpty()) {
            return shortLabel;
        }
        switch (calendar == null ? Calendar.SUNDAY : calendar.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                return "一";
            case Calendar.TUESDAY:
                return "二";
            case Calendar.WEDNESDAY:
                return "三";
            case Calendar.THURSDAY:
                return "四";
            case Calendar.FRIDAY:
                return "五";
            case Calendar.SATURDAY:
                return "六";
            case Calendar.SUNDAY:
            default:
                return "日";
        }
    }

    private static boolean isChineseLocale(Locale locale) {
        return locale != null && "zh".equalsIgnoreCase(locale.getLanguage());
    }

    private static String trimWeekdayLabel(String weekday) {
        if (weekday == null) {
            return "";
        }
        String normalized = weekday.trim();
        if (normalized.startsWith("星期") && normalized.length() > 2) {
            normalized = normalized.substring(2);
        } else if ((normalized.startsWith("周") || normalized.startsWith("週"))
                && normalized.length() > 1) {
            normalized = normalized.substring(1);
        }
        if ("天".equals(normalized)) {
            return "日";
        }
        return normalized;
    }

    private static String resolveAmPmLabel(Calendar calendar) {
        int index = calendar == null ? 0 : calendar.get(Calendar.AM_PM);
        return index == Calendar.AM ? "AM" : "PM";
    }

    private static String resolveTraditionalPeriod(Calendar calendar) {
        int hour = calendar == null ? 0 : calendar.get(Calendar.HOUR_OF_DAY);
        if (hour <= 4) {
            return "凌晨";
        }
        if (hour <= 7) {
            return "早晨";
        }
        if (hour <= 11) {
            return "上午";
        }
        if (hour == 12) {
            return "中午";
        }
        if (hour <= 17) {
            return "下午";
        }
        if (hour == 18) {
            return "傍晚";
        }
        return "晚上";
    }

    private static String resolveEarthlyBranch(Calendar calendar) {
        switch (resolveEarthlyBranchIndex(calendar)) {
            case 0:
                return "子";
            case 1:
                return "丑";
            case 2:
                return "寅";
            case 3:
                return "卯";
            case 4:
                return "辰";
            case 5:
                return "巳";
            case 6:
                return "午";
            case 7:
                return "未";
            case 8:
                return "申";
            case 9:
                return "酉";
            case 10:
                return "戌";
            default:
                return "亥";
        }
    }

    private static String resolveEarthlyBranchAlias(Calendar calendar) {
        switch (resolveEarthlyBranchIndex(calendar)) {
            case 0:
                return "夜半";
            case 1:
                return "鸡鸣";
            case 2:
                return "平旦";
            case 3:
                return "日出";
            case 4:
                return "食时";
            case 5:
                return "隅中";
            case 6:
                return "日中";
            case 7:
                return "日昳";
            case 8:
                return "哺时";
            case 9:
                return "日入";
            case 10:
                return "黄昏";
            default:
                return "人定";
        }
    }

    private static int resolveEarthlyBranchIndex(Calendar calendar) {
        int hour = calendar == null ? 0 : calendar.get(Calendar.HOUR_OF_DAY);
        if (hour == 23 || hour == 0) {
            return 0;
        }
        return Math.min(11, Math.max(0, (hour + 1) / 2));
    }

    private static void rememberOriginalTextSize(TextView view) {
        if (view == null || ORIGINAL_TEXT_SIZES.containsKey(view)) {
            return;
        }
        ORIGINAL_TEXT_SIZES.put(view, view.getTextSize());
    }

    private static void rememberOriginalIncludeFontPadding(TextView view) {
        if (view == null || ORIGINAL_INCLUDE_FONT_PADDING.containsKey(view)) {
            return;
        }
        ORIGINAL_INCLUDE_FONT_PADDING.put(view, view.getIncludeFontPadding());
    }

    private static void rememberOriginalTextVerticalAnchor(TextView view) {
        if (view == null || ORIGINAL_TEXT_TRANSLATION_Y.containsKey(view)) {
            return;
        }
        ORIGINAL_TEXT_TRANSLATION_Y.put(view, view.getTranslationY());
    }

    private static void rememberOriginalLayout(TextView view) {
        if (view == null || ORIGINAL_LAYOUT_SIZES.containsKey(view)) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        ORIGINAL_LAYOUT_SIZES.put(view, new int[]{lp.width, lp.height});
    }

    private static void rememberOriginalPadding(TextView view) {
        if (view == null || ORIGINAL_PADDINGS.containsKey(view)) {
            return;
        }
        ORIGINAL_PADDINGS.put(view, new int[]{
                view.getPaddingStart(),
                view.getPaddingTop(),
                view.getPaddingEnd(),
                view.getPaddingBottom()
        });
    }

    private static void applyClockAndCarrierTextSize(TextView view) {
        if (view == null || !isClockOrLockscreenCarrierText(view)) {
            return;
        }
        rememberOriginalTextSize(view);
        rememberOriginalIncludeFontPadding(view);
        rememberOriginalTextVerticalAnchor(view);
        rememberOriginalLayout(view);
        rememberOriginalPadding(view);
        Float originalSize = ORIGINAL_TEXT_SIZES.get(view);
        if (originalSize == null || originalSize <= 0f) {
            return;
        }
        FlymeStatusBarSizer.ClockConfigSnapshot config =
                FlymeStatusBarSizer.loadClockConfig(view.getContext());
        float scale = config.enabled ? config.clockAndCarrierTextScale : 1f;
        boolean changed = false;
        float targetSize = originalSize * scale;
        if (Math.abs(view.getTextSize() - targetSize) > 0.5f) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetSize);
            changed = true;
        }
        if (resetClockAndCarrierTextViewScale(view)) {
            changed = true;
        }
        if (restoreOriginalTextLayoutWidth(view)) {
            changed = true;
        }
        if (restoreOriginalClockAndCarrierFontPadding(view)) {
            changed = true;
        }
        if (applyPrimaryStatusBarClockEndPadding(view)) {
            changed = true;
        }
        if (applyClockAndCarrierTextMetrics(view)) {
            changed = true;
        }
        if (applyClockAndCarrierVerticalAnchor(view)) {
            changed = true;
        }
        if (changed) {
            view.requestLayout();
        }
        view.invalidate();
    }

    private static boolean resetClockAndCarrierTextViewScale(TextView view) {
        if (view == null) {
            return false;
        }
        FlymeStatusBarSizer.disableAncestorClipping(view, 4);
        boolean changed = false;
        if (Math.abs(view.getScaleX() - 1f) > 0.001f) {
            view.setScaleX(1f);
            changed = true;
        }
        if (Math.abs(view.getScaleY() - 1f) > 0.001f) {
            view.setScaleY(1f);
            changed = true;
        }
        return changed;
    }

    private static boolean restoreOriginalClockAndCarrierFontPadding(TextView view) {
        if (view == null) {
            return false;
        }
        Boolean original = ORIGINAL_INCLUDE_FONT_PADDING.get(view);
        boolean targetValue = original != null && original;
        if (view.getIncludeFontPadding() == targetValue) {
            return false;
        }
        view.setIncludeFontPadding(targetValue);
        return true;
    }

    private static void scheduleClockAndCarrierTextRelayout(TextView view) {
        if (view == null) {
            return;
        }
        view.post(() -> {
            applyClockFontWeight(view);
            applyClockAndCarrierTextSize(view);
        });
        view.postDelayed(() -> {
            applyClockFontWeight(view);
            applyClockAndCarrierTextSize(view);
        }, 32L);
    }

    private static void trackClockAndCarrierTextView(TextView view) {
        if (view == null || TRACKED_CLOCK_AND_CARRIER_TEXT_VIEWS.containsKey(view)) {
            return;
        }
        TRACKED_CLOCK_AND_CARRIER_TEXT_VIEWS.put(view, Boolean.TRUE);
        FlymeStatusBarSizer.ensureConfigRefreshObserver(view.getContext());
        updateClockSecondRefreshTracking(view);
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!(v instanceof TextView)) {
                return;
            }
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                return;
            }
            v.post(() -> applyClockAndCarrierTextSize((TextView) v));
        });
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (v instanceof TextView) {
                    updateClockSecondRefreshTracking((TextView) v);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (v instanceof TextView) {
                    CLOCK_SECOND_REFRESH_VIEWS.remove((TextView) v);
                    scheduleNextClockSecondRefresh();
                }
            }
        });
    }

    private static boolean restoreOriginalTextLayoutWidth(TextView view) {
        if (view == null) {
            return false;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        int[] originalSize = ORIGINAL_LAYOUT_SIZES.get(view);
        if (lp == null || originalSize == null) {
            return false;
        }
        int originalWidth = originalSize[0];
        if (lp.width != originalWidth) {
            lp.width = originalWidth;
            view.setLayoutParams(lp);
            return true;
        }
        return false;
    }

    private static boolean applyClockAndCarrierTextMetrics(TextView view) {
        if (view == null) {
            return false;
        }
        boolean changed = false;
        Paint.FontMetricsInt fontMetrics = view.getPaint().getFontMetricsInt();
        int targetTextBoundsHeight = fontMetrics == null
                ? Math.max(1, Math.round(view.getTextSize()))
                : Math.max(1, fontMetrics.bottom - fontMetrics.top);
        int targetLineHeight = fontMetrics == null
                ? targetTextBoundsHeight
                : Math.max(targetTextBoundsHeight,
                fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading);
        if (applyTextViewLineHeight(view, targetLineHeight)) {
            changed = true;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        int[] originalSize = ORIGINAL_LAYOUT_SIZES.get(view);
        if (lp == null || originalSize == null) {
            return changed;
        }
        int[] originalPadding = ORIGINAL_PADDINGS.get(view);
        int verticalPadding = 0;
        if (originalPadding != null) {
            verticalPadding = originalPadding[1] + originalPadding[3];
        }
        int originalHeight = originalSize[1];
        if (originalHeight > 0) {
            int targetHeight = Math.max(originalHeight, targetTextBoundsHeight + verticalPadding);
            if (lp.height != targetHeight) {
                lp.height = targetHeight;
                view.setLayoutParams(lp);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean applyClockAndCarrierVerticalAnchor(TextView view) {
        if (view == null) {
            return false;
        }
        Float originalTranslationY = ORIGINAL_TEXT_TRANSLATION_Y.get(view);
        if (originalTranslationY == null) {
            return false;
        }
        float targetTranslationY = originalTranslationY;
        if (Math.abs(view.getTranslationY() - targetTranslationY) <= 0.5f) {
            return false;
        }
        view.setTranslationY(targetTranslationY);
        return true;
    }

    private static boolean applyPrimaryStatusBarClockEndPadding(TextView view) {
        if (!isPrimaryStatusBarClockView(view)) {
            return false;
        }
        int[] originalPadding = ORIGINAL_PADDINGS.get(view);
        if (originalPadding == null || originalPadding.length < 4) {
            return false;
        }
        FlymeStatusBarSizer.ClockConfigSnapshot config =
                FlymeStatusBarSizer.loadClockConfig(view.getContext());
        int targetPaddingStart = Math.max(0, originalPadding[0]);
        int targetPaddingEnd = Math.max(0, originalPadding[2] + config.clockRightPaddingOffsetPx);
        if (view.getPaddingStart() == targetPaddingStart
                && view.getPaddingEnd() == targetPaddingEnd) {
            return false;
        }
        view.setPaddingRelative(
                targetPaddingStart,
                view.getPaddingTop(),
                targetPaddingEnd,
                view.getPaddingBottom());
        return true;
    }

    private static boolean applyTextViewLineHeight(TextView view, int targetLineHeight) {
        if (view == null) {
            return false;
        }
        int normalizedLineHeight = Math.max(1, targetLineHeight);
        int currentLineHeight = view.getLineHeight();
        if (Math.abs(currentLineHeight - normalizedLineHeight) <= 1) {
            return false;
        }
        Object result = FlymeStatusBarSizer.invokeMethodCompat(
                view,
                "setLineHeight",
                new Class[]{int.class, float.class},
                TypedValue.COMPLEX_UNIT_PX,
                (float) normalizedLineHeight);
        if (result != null || Math.abs(view.getLineHeight() - normalizedLineHeight) <= 1) {
            return true;
        }
        FlymeStatusBarSizer.invokeMethodCompat(
                view,
                "setLineHeight",
                new Class[]{int.class},
                normalizedLineHeight);
        return Math.abs(view.getLineHeight() - normalizedLineHeight) <= 1;
    }

    private static void applyClockFontWeight(TextView view) {
        if (view == null || !isStatusBarClockView(view)) {
            return;
        }
        FlymeStatusBarSizer.ClockConfigSnapshot config =
                FlymeStatusBarSizer.loadClockConfig(view.getContext());
        int fontWeight = config.enabled ? config.clockFontWeight : 400;
        Typeface baseTypeface = view.getTypeface();
        boolean italic = baseTypeface != null && baseTypeface.isItalic();
        Typeface newTypeface;
        try {
            newTypeface = Typeface.create(baseTypeface, fontWeight, italic);
        } catch (Throwable ignored) {
            newTypeface = Typeface.defaultFromStyle(fontWeight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (newTypeface != null) {
            view.setTypeface(newTypeface);
        }
        view.getPaint().setFakeBoldText(fontWeight >= 600);
        view.requestLayout();
        view.invalidate();
    }

    private static boolean isStatusBarClockView(TextView view) {
        if (view == null) {
            return false;
        }
        if (!"com.android.systemui.statusbar.policy.Clock".equals(view.getClass().getName())) {
            return false;
        }
        String idName = FlymeStatusBarSizer.getSystemUiIdNameCompat(view);
        return "clock".equals(idName) || "keyguard_clock".equals(idName) || "mz_clock".equals(idName);
    }

    private static boolean isPrimaryStatusBarClockView(TextView view) {
        return view != null
                && "com.android.systemui.statusbar.policy.Clock".equals(view.getClass().getName())
                && "clock".equals(FlymeStatusBarSizer.getSystemUiIdNameCompat(view));
    }

    private static boolean isClockOrLockscreenCarrierText(TextView view) {
        if (view == null) {
            return false;
        }
        String className = view.getClass().getName();
        String idName = FlymeStatusBarSizer.getSystemUiIdNameCompat(view);
        if ("com.android.systemui.statusbar.policy.Clock".equals(className)) {
            return "clock".equals(idName) || "keyguard_clock".equals(idName) || "mz_clock".equals(idName);
        }
        if ("com.android.keyguard.CarrierText".equals(className)) {
            return "keyguard_carrier_text".equals(idName) || "carrier_text".equals(idName);
        }
        return false;
    }

    private static void installClockDetailPopupIfNeeded(TextView view) {
        if (!isPrimaryStatusBarClockView(view)) {
            return;
        }
        ClockDetailPopupController controller = CLOCK_DETAIL_POPUPS.get(view);
        if (controller == null) {
            controller = new ClockDetailPopupController(view);
            CLOCK_DETAIL_POPUPS.put(view, controller);
        }
        controller.syncWithConfig(FlymeStatusBarSizer.loadClockConfig(view.getContext()));
    }

    private static void syncClockDetailPopup(TextView view) {
        if (!isPrimaryStatusBarClockView(view)) {
            return;
        }
        ClockDetailPopupController controller = CLOCK_DETAIL_POPUPS.get(view);
        if (controller == null) {
            installClockDetailPopupIfNeeded(view);
            return;
        }
        controller.syncWithConfig(FlymeStatusBarSizer.loadClockConfig(view.getContext()));
    }

    private static void refreshClockTextIfNeeded(TextView textView) {
        if (!isPrimaryStatusBarClockView(textView)) {
            return;
        }
        FlymeStatusBarSizer.invokeMethodCompat(textView, "updateClock", new Class<?>[0]);
    }

    private static void updateClockSecondRefreshTracking(TextView view) {
        if (!isPrimaryStatusBarClockView(view)) {
            return;
        }
        FlymeStatusBarSizer.ClockConfigSnapshot config =
                FlymeStatusBarSizer.loadClockConfig(view.getContext());
        if (shouldUseCustomClockSecondRefresh(config)) {
            CLOCK_SECOND_REFRESH_VIEWS.put(view, Boolean.TRUE);
        } else {
            CLOCK_SECOND_REFRESH_VIEWS.remove(view);
        }
        scheduleNextClockSecondRefresh();
    }

    private static boolean shouldUseCustomClockSecondRefresh(
            FlymeStatusBarSizer.ClockConfigSnapshot config) {
        if (config == null || !config.enabled) {
            return false;
        }
        String format = config.clockCustomFormat;
        return format != null && format.contains("{ss}");
    }

    private static void refreshClockViewsForSecondTick() {
        Handler handler = FlymeStatusBarSizer.getMainHandler();
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(CLOCK_SECOND_REFRESH_RUNNABLE);
        ArrayList<TextView> clocks = new ArrayList<>(CLOCK_SECOND_REFRESH_VIEWS.keySet());
        boolean hasActiveClock = false;
        for (TextView clock : clocks) {
            if (clock == null || !clock.isAttachedToWindow()) {
                CLOCK_SECOND_REFRESH_VIEWS.remove(clock);
                continue;
            }
            FlymeStatusBarSizer.ClockConfigSnapshot config =
                    FlymeStatusBarSizer.loadClockConfig(clock.getContext());
            if (!shouldUseCustomClockSecondRefresh(config)) {
                CLOCK_SECOND_REFRESH_VIEWS.remove(clock);
                continue;
            }
            hasActiveClock = true;
            refreshClockTextIfNeeded(clock);
            applyClockAndCarrierTextSize(clock);
        }
        if (hasActiveClock) {
            scheduleNextClockSecondRefresh();
        }
    }

    private static void scheduleNextClockSecondRefresh() {
        Handler handler = FlymeStatusBarSizer.getMainHandler();
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(CLOCK_SECOND_REFRESH_RUNNABLE);
        if (CLOCK_SECOND_REFRESH_VIEWS.isEmpty()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long next = ((now / 1000L) + 1L) * 1000L;
        handler.postAtTime(CLOCK_SECOND_REFRESH_RUNNABLE, next);
    }

    private interface ViewAction {
        void apply(View view);
    }
}
