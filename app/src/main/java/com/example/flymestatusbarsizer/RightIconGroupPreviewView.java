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
    private static final int DEFAULT_TEXT_COLOR = Color.rgb(28, 27, 31);

    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surfaceStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panelRect = new RectF();
    private final RectF topStatusStripRect = new RectF();
    private final RectF bottomStatusStripRect = new RectF();
    private final Rect batteryRect = new Rect();
    private final Rect singleSignalRect = new Rect();
    private final Rect mergedSignalRect = new Rect();

    private int previewTintColor = DEFAULT_TEXT_COLOR;
    private int batteryStyle = SettingsStore.DEFAULT_BATTERY_ICON_STYLE;
    private boolean batteryLevelTextEnabled = true;
    private boolean batteryHollowEnabled = SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED;
    private boolean batteryHollowFillFollowsLevel = SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL;
    private int batteryTextFont = SettingsStore.DEFAULT_BATTERY_TEXT_FONT;
    private int iconScalePercent = SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT;
    private int batteryInnerTextScalePercent = SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT;

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
        int signalLeft = Math.round(currentRight - iconSize);
        target.set(signalLeft, iconTop, signalLeft + iconSize, iconTop + iconSize);
        if (mergedDual) {
            SignalPreviewPainter.drawMergedDualSim(canvas, target, previewTintColor);
        } else {
            SignalPreviewPainter.drawSingleSim(canvas, target, previewTintColor);
        }

        currentRight = signalLeft - dp(8);
        dimPaint.setColor(Color.argb(170,
                Color.red(previewTintColor),
                Color.green(previewTintColor),
                Color.blue(previewTintColor)));
        float typeWidth = dp(22);
        canvas.drawRoundRect(currentRight - typeWidth,
                centerY - dp(7),
                currentRight,
                centerY + dp(7),
                dp(4),
                dp(4),
                dimPaint);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(dp(10));
        float typeBaseline = centerY - (labelPaint.descent() + labelPaint.ascent()) / 2f;
        canvas.drawText("5G", currentRight - typeWidth / 2f, typeBaseline, labelPaint);
    }

    private void drawPreviewNotes(Canvas canvas) {
        hintPaint.setColor(Color.argb(215, 255, 255, 255));
        hintPaint.setTextSize(dp(13));
        float firstLineY = panelRect.bottom - dp(28);
        canvas.drawText("两条状态栏都会按实际位置直接画图标，便于调比例", panelRect.centerX(), firstLineY, hintPaint);
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
            OneUiBatteryPainter.draw(canvas, bounds, level, pluggedIn, charging,
                    fillColor, textColor, showLevelText, batteryInnerTextScalePercent / 100f, typeface,
                    batteryHollowEnabled, batteryHollowFillFollowsLevel);
            return;
        }
        IosBatteryPainter.draw(canvas, bounds, level, pluggedIn, charging,
                fillColor, textColor, showLevelText, batteryInnerTextScalePercent / 100f, typeface,
                batteryHollowEnabled, batteryHollowFillFollowsLevel);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private int scalePx(int px) {
        return Math.max(1, Math.round(px * (iconScalePercent / 100f)));
    }
}
