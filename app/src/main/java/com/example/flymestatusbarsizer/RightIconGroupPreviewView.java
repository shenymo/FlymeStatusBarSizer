package com.example.flymestatusbarsizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public final class RightIconGroupPreviewView extends View {
    private static final int PREVIEW_BATTERY_LEVEL = 82;
    private static final int PREVIEW_MERGED_PRIMARY_SIGNAL_LEVEL = 4;
    private static final int PREVIEW_MERGED_SECONDARY_SIGNAL_LEVEL = 2;
    private static final int DEFAULT_TEXT_COLOR = Color.rgb(28, 27, 31);

    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surfaceStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panelRect = new RectF();
    private final RectF topStatusStripRect = new RectF();
    private final RectF bottomStatusStripRect = new RectF();
    private final Rect batteryRect = new Rect();
    private final Rect singleSignalRect = new Rect();
    private final Rect mergedSignalRect = new Rect();
    private final Rect wifiRect = new Rect();

    private int previewTintColor = DEFAULT_TEXT_COLOR;
    private int batteryStyle = SettingsStore.DEFAULT_BATTERY_ICON_STYLE;
    private boolean batteryLevelTextEnabled = true;
    private boolean batteryHollowEnabled = SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED;
    private boolean batteryHollowFillFollowsLevel = SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL;
    private int batteryTextFont = SettingsStore.DEFAULT_BATTERY_TEXT_FONT;
    private int iconScalePercent = SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT;
    private int batteryInnerTextScalePercent = SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT;
    private int batteryIconYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_ICON_Y_OFFSET_DP * 10;
    private int batteryTextYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_TEXT_Y_OFFSET_DP * 10;
    private int batteryBoltYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_BOLT_Y_OFFSET_DP * 10;
    private int signalSingleYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP * 10;
    private int signalBadgeYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP * 10;
    private int signalDualYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP * 10;
    private int wifiYOffsetTenthDp = SettingsStore.DEFAULT_WIFI_Y_OFFSET_DP * 10;

    public RightIconGroupPreviewView(Context context) {
        super(context);
        init();
    }

    public RightIconGroupPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RightIconGroupPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        surfaceStrokePaint.setStyle(Paint.Style.STROKE);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
    }

    public void setPreviewTintColor(int color) {
        int resolved = Color.alpha(color) == 0 ? DEFAULT_TEXT_COLOR : color;
        if (previewTintColor == resolved) {
            return;
        }
        previewTintColor = resolved;
        invalidate();
    }

    public void setBatteryLevelTextEnabled(boolean enabled) {
        if (batteryLevelTextEnabled == enabled) {
            return;
        }
        batteryLevelTextEnabled = enabled;
        invalidate();
    }

    public void setBatteryHollowEnabled(boolean enabled) {
        if (batteryHollowEnabled == enabled) {
            return;
        }
        batteryHollowEnabled = enabled;
        invalidate();
    }

    public void setBatteryHollowFillFollowsLevel(boolean enabled) {
        if (batteryHollowFillFollowsLevel == enabled) {
            return;
        }
        batteryHollowFillFollowsLevel = enabled;
        invalidate();
    }

    public void setBatteryStyle(int style) {
        int normalized = SettingsStore.normalizeBatteryStyle(style);
        if (batteryStyle == normalized) {
            return;
        }
        batteryStyle = normalized;
        invalidate();
    }

    public void setBatteryTextFont(int font) {
        int normalized = SettingsStore.normalizeBatteryTextFont(font);
        if (batteryTextFont == normalized) {
            return;
        }
        batteryTextFont = normalized;
        invalidate();
    }

    public void setIconScalePercent(int percent) {
        int normalized = SettingsStore.normalizeScalePercent(percent);
        if (iconScalePercent == normalized) {
            return;
        }
        iconScalePercent = normalized;
        invalidate();
    }

    public void setBatteryInnerTextScalePercent(int percent) {
        int normalized = SettingsStore.normalizeScalePercent(percent);
        if (batteryInnerTextScalePercent == normalized) {
            return;
        }
        batteryInnerTextScalePercent = normalized;
        invalidate();
    }

    public void setBatteryIconYOffsetTenthDp(int offsetTenthDp) {
        int normalized = SettingsStore.normalizeIconYOffsetTenthDp(offsetTenthDp);
        if (batteryIconYOffsetTenthDp == normalized) {
            return;
        }
        batteryIconYOffsetTenthDp = normalized;
        invalidate();
    }

    public void setBatteryTextYOffsetTenthDp(int offsetTenthDp) {
        int normalized = SettingsStore.normalizeIconYOffsetTenthDp(offsetTenthDp);
        if (batteryTextYOffsetTenthDp == normalized) {
            return;
        }
        batteryTextYOffsetTenthDp = normalized;
        invalidate();
    }

    public void setBatteryBoltYOffsetTenthDp(int offsetTenthDp) {
        int normalized = SettingsStore.normalizeIconYOffsetTenthDp(offsetTenthDp);
        if (batteryBoltYOffsetTenthDp == normalized) {
            return;
        }
        batteryBoltYOffsetTenthDp = normalized;
        invalidate();
    }

    public void setSignalSingleYOffsetTenthDp(int offsetTenthDp) {
        int normalized = SettingsStore.normalizeIconYOffsetTenthDp(offsetTenthDp);
        if (signalSingleYOffsetTenthDp == normalized) {
            return;
        }
        signalSingleYOffsetTenthDp = normalized;
        invalidate();
    }

    public void setSignalBadgeYOffsetTenthDp(int offsetTenthDp) {
        int normalized = SettingsStore.normalizeIconYOffsetTenthDp(offsetTenthDp);
        if (signalBadgeYOffsetTenthDp == normalized) {
            return;
        }
        signalBadgeYOffsetTenthDp = normalized;
        invalidate();
    }

    public void setSignalDualYOffsetTenthDp(int offsetTenthDp) {
        int normalized = SettingsStore.normalizeIconYOffsetTenthDp(offsetTenthDp);
        if (signalDualYOffsetTenthDp == normalized) {
            return;
        }
        signalDualYOffsetTenthDp = normalized;
        invalidate();
    }

    public void setWifiYOffsetTenthDp(int offsetTenthDp) {
        int normalized = SettingsStore.normalizeIconYOffsetTenthDp(offsetTenthDp);
        if (wifiYOffsetTenthDp == normalized) {
            return;
        }
        wifiYOffsetTenthDp = normalized;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        panelRect.set(dp(2), dp(8), getWidth() - dp(2), getHeight() - dp(8));
        surfacePaint.setColor(Color.argb(56, 255, 255, 255));
        surfaceStrokePaint.setColor(Color.argb(78, 255, 255, 255));
        surfaceStrokePaint.setStrokeWidth(dp(1));
        canvas.drawRoundRect(panelRect, dp(24), dp(24), surfacePaint);
        canvas.drawRoundRect(panelRect, dp(24), dp(24), surfaceStrokePaint);

        topStatusStripRect.set(panelRect.left + dp(12), panelRect.top + dp(32),
                panelRect.right - dp(12), panelRect.top + dp(70));
        bottomStatusStripRect.set(panelRect.left + dp(12), panelRect.top + dp(104),
                panelRect.right - dp(12), panelRect.top + dp(142));

        drawStatusStripLabel(canvas, topStatusStripRect, "单卡信号");
        drawStatusStripLabel(canvas, bottomStatusStripRect, "双卡合一");
        drawStatusStrip(canvas, topStatusStripRect, false);
        drawStatusStrip(canvas, bottomStatusStripRect, true);
        drawPreviewNotes(canvas);
    }

    private void drawStatusStripLabel(Canvas canvas, RectF stripRect, String text) {
        labelPaint.setColor(Color.argb(220, 255, 255, 255));
        labelPaint.setTextSize(dp(11));
        float baseline = stripRect.top - dp(8);
        canvas.drawText(text, stripRect.centerX(), baseline, labelPaint);
    }

    private void drawStatusStrip(Canvas canvas, RectF stripRect, boolean mergedDual) {
        surfacePaint.setColor(Color.argb(236, 255, 255, 255));
        canvas.drawRoundRect(stripRect, dp(18), dp(18), surfacePaint);

        textPaint.setColor(previewTintColor);
        textPaint.setTextSize(dp(15));
        float timeBaseline = stripRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText("09:41", stripRect.left + dp(16), timeBaseline, textPaint);

        drawRightIconGroup(canvas, stripRect, mergedDual);
    }

    private void drawRightIconGroup(Canvas canvas, RectF stripRect, boolean mergedDual) {
        int iconSize = scalePx(dp(24));
        float centerY = stripRect.centerY();
        float anchorRight = stripRect.right - dp(14);

        int iconTop = Math.round(centerY - iconSize / 2f);
        int batteryLeft = Math.round(anchorRight - iconSize);
        batteryRect.set(batteryLeft, iconTop, batteryLeft + iconSize, iconTop + iconSize);
        drawBattery(canvas, batteryRect, PREVIEW_BATTERY_LEVEL, false, false,
                previewTintColor, resolveBatteryTextColor(previewTintColor), batteryLevelTextEnabled);

        float currentRight = batteryLeft - dp(10);
        Rect target = mergedDual ? mergedSignalRect : singleSignalRect;
        int mobileTypeBadge = SignalPreviewPainter.MOBILE_TYPE_BADGE_5G;
        int signalWidth = SignalPreviewPainter.resolveIntrinsicWidth(iconSize, mobileTypeBadge);
        int signalLeft = Math.round(currentRight - signalWidth);
        target.set(signalLeft, iconTop, signalLeft + signalWidth, iconTop + iconSize);
        if (mergedDual) {
            SignalPreviewPainter.drawMergedDualSim(
                    canvas,
                    target,
                    previewTintColor,
                    null,
                    mobileTypeBadge,
                    PREVIEW_MERGED_PRIMARY_SIGNAL_LEVEL,
                    PREVIEW_MERGED_SECONDARY_SIGNAL_LEVEL,
                    offsetPx(signalDualYOffsetTenthDp),
                    offsetPx(signalBadgeYOffsetTenthDp));
        } else {
            SignalPreviewPainter.drawSingleSim(
                    canvas,
                    target,
                    previewTintColor,
                    null,
                    mobileTypeBadge,
                    PREVIEW_MERGED_PRIMARY_SIGNAL_LEVEL,
                    offsetPx(signalSingleYOffsetTenthDp),
                    offsetPx(signalBadgeYOffsetTenthDp));
        }
        currentRight = signalLeft - dp(8);
        int wifiLeft = Math.round(currentRight - iconSize);
        wifiRect.set(wifiLeft, iconTop, wifiLeft + iconSize, iconTop + iconSize);
        WifiIconDrawable.drawPreview(canvas, wifiRect, previewTintColor, 255, null,
                4, false, 0, offsetPx(wifiYOffsetTenthDp));
    }

    private void drawPreviewNotes(Canvas canvas) {
        hintPaint.setColor(Color.argb(215, 255, 255, 255));
        hintPaint.setTextSize(dp(13));
        float firstLineY = panelRect.bottom - dp(28);
        canvas.drawText("预览会同步显示电池、信号和 Wi-Fi，方便一起看比例和位置", panelRect.centerX(), firstLineY, hintPaint);
    }

    private static int resolveBatteryTextColor(int tintColor) {
        int color = Color.alpha(tintColor) == 0 ? DEFAULT_TEXT_COLOR : tintColor;
        double luminance = (0.299d * Color.red(color)
                + 0.587d * Color.green(color)
                + 0.114d * Color.blue(color)) / 255d;
        return luminance >= 0.5d ? Color.BLACK : Color.WHITE;
    }

    private void drawBattery(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            int fillColor, int textColor, boolean showLevelText) {
        Typeface typeface = BatteryTextFontHelper.resolveTypeface(getContext(), batteryTextFont);
        if (SettingsStore.normalizeBatteryStyle(batteryStyle) == SettingsStore.BATTERY_STYLE_ONEUI) {
            OneUiBatteryPainter.draw(canvas, bounds, level, pluggedIn, charging, false,
                    fillColor, textColor, showLevelText, batteryInnerTextScalePercent / 100f, typeface,
                    offsetPx(batteryIconYOffsetTenthDp), offsetPx(batteryTextYOffsetTenthDp),
                    offsetPx(batteryBoltYOffsetTenthDp),
                    batteryHollowEnabled, batteryHollowFillFollowsLevel);
            return;
        }
        IosBatteryPainter.draw(canvas, bounds, level, pluggedIn, charging, false,
                fillColor, textColor, showLevelText, batteryInnerTextScalePercent / 100f, typeface,
                offsetPx(batteryIconYOffsetTenthDp), offsetPx(batteryTextYOffsetTenthDp),
                offsetPx(batteryBoltYOffsetTenthDp),
                batteryHollowEnabled, batteryHollowFillFollowsLevel);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private int scalePx(int px) {
        return Math.max(1, Math.round(px * (iconScalePercent / 100f)));
    }

    private float offsetPx(int valueTenthDp) {
        return SettingsStore.positionOffsetTenthDpToPx(getContext(), valueTenthDp);
    }
}
