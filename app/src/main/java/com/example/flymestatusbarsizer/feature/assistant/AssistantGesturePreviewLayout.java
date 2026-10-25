package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;

import com.example.flymestatusbarsizer.config.SettingsStore;

/** Owns a touch-through marker on the app window for the lifetime of this settings page. */
public final class AssistantGesturePreviewLayout extends LinearLayout {
    private static final long PREVIEW_DURATION_MS = 5000;
    private final SharedPreferences prefs;
    private final DistanceMarker marker;
    private final Runnable dismiss = this::hidePreview;
    private View overlayHost;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP.equals(key)) showPreview();
        else if (SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED.equals(key)
                || SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP.equals(key)) showVerticalPreview();
    };

    public AssistantGesturePreviewLayout(Context context, SharedPreferences prefs, int accentColor) {
        super(context);
        this.prefs = prefs;
        marker = new DistanceMarker(accentColor);
        setOrientation(VERTICAL);
    }

    public void showPreview() {
        showPreview(false);
    }

    public void showVerticalPreview() {
        showPreview(true);
    }

    private void showPreview(boolean vertical) {
        if (!isAttachedToWindow() || !isShown() || !hasWindowFocus()
                || getWindowVisibility() != VISIBLE) return;
        hidePreview();
        int distance = Math.max(40, Math.min(240, SettingsStore.readInt(prefs,
                SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_DP)));
        overlayHost = getRootView();
        marker.distanceDp = distance;
        marker.vertical = vertical;
        marker.verticalLimitEnabled = SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED);
        marker.verticalLimitDp = SettingsStore.normalizeAssistantGestureVerticalLimitDp(SettingsStore.readInt(prefs,
                SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP));
        marker.setBounds(0, 0, overlayHost.getWidth(), overlayHost.getHeight());
        overlayHost.getOverlay().add(marker);
        postDelayed(dismiss, PREVIEW_DURATION_MS);
    }

    private void hidePreview() {
        removeCallbacks(dismiss);
        if (overlayHost != null) {
            overlayHost.getOverlay().remove(marker);
            overlayHost = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    protected void onDetachedFromWindow() {
        hidePreview();
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // This can be called by the View constructor, before our fields are initialized.
        if (marker != null && !isShown()) hidePreview();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (marker != null && visibility != VISIBLE) hidePreview();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) hidePreview();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (marker != null) hidePreview();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private final class DistanceMarker extends Drawable {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint originPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int distanceDp;
        private boolean vertical, verticalLimitEnabled;
        private int verticalLimitDp;

        DistanceMarker(int accentColor) {
            linePaint.setColor(accentColor);
            linePaint.setStrokeWidth(dp(2));
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(4));
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(14f * getResources().getDisplayMetrics().scaledDensity);
            backgroundPaint.setColor(0xe6222222);
            originPaint.setColor(accentColor);
            originPaint.setStrokeWidth(dp(2));
            originPaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(6)}, 0));
        }

        @Override
        public void draw(Canvas canvas) {
            float top = dp(24);
            float bottom = getBounds().height() - dp(24);
            WindowInsets insets = getRootWindowInsets();
            if (insets != null) {
                top += insets.getSystemWindowInsetTop();
                bottom -= insets.getSystemWindowInsetBottom();
            }
            float center = (top + bottom) / 2f;
            if (vertical) {
                drawVertical(canvas, top, bottom, center);
                return;
            }
            drawSide(canvas, true, top, bottom, center - dp(36));
            drawSide(canvas, false, top, bottom, center + dp(36));
        }

        private void drawVertical(Canvas canvas, float top, float bottom, float center) {
            float upper = center - dp(verticalLimitDp);
            float lower = center + dp(verticalLimitDp);
            float width = getBounds().width();
            canvas.drawLine(0, center, width, center, originPaint);
            for (float y : new float[]{upper, lower}) {
                canvas.drawLine(0, y, width, y, outlinePaint);
                canvas.drawLine(0, y, width, y, linePaint);
            }
            drawVerticalLabel(canvas, "上移 " + verticalLimitDp + "dp 边界"
                            + (upper < top ? "（屏幕外）" : ""),
                    upper - dp(12) - labelPaint.descent(), top, bottom);
            drawVerticalLabel(canvas, "下移 " + verticalLimitDp + "dp 边界"
                            + (lower > bottom ? "（屏幕外）" : ""),
                    lower + dp(12) - labelPaint.ascent(), top, bottom);
            drawVerticalLabel(canvas, verticalLimitEnabled
                            ? "示例起点 · 实际随按下位置" : "示例起点 · 限制未开启",
                    center - (labelPaint.ascent() + labelPaint.descent()) / 2f, top, bottom);
        }

        private void drawVerticalLabel(Canvas canvas, String label, float baseline, float top, float bottom) {
            float originalSize = labelPaint.getTextSize();
            float availableWidth = Math.max(1, getBounds().width() - dp(32));
            float textWidth = labelPaint.measureText(label);
            if (textWidth > availableWidth) {
                labelPaint.setTextSize(originalSize * availableWidth / textWidth);
                textWidth = labelPaint.measureText(label);
            }
            float left = (getBounds().width() - textWidth) / 2f;
            baseline = Math.max(top - labelPaint.ascent() + dp(6),
                    Math.min(bottom - labelPaint.descent() - dp(6), baseline));
            canvas.drawRoundRect(left - dp(8), baseline + labelPaint.ascent() - dp(6),
                    left + textWidth + dp(8), baseline + labelPaint.descent() + dp(6),
                    dp(8), dp(8), backgroundPaint);
            canvas.drawText(label, left, baseline, labelPaint);
            labelPaint.setTextSize(originalSize);
        }

        private void drawSide(Canvas canvas, boolean leftEdge, float top, float bottom, float center) {
            float edge = leftEdge ? 0 : getBounds().width();
            float direction = leftEdge ? 1 : -1;
            float x = edge + direction * dp(distanceDp);
            String label = (leftEdge ? "左侧 " : "右侧 ") + distanceDp + "dp 达标线";
            // Decor coordinates start at the window's left edge, not at the settings card.
            canvas.drawLine(x, top, x, bottom, outlinePaint);
            canvas.drawLine(edge, center, x, center, outlinePaint);
            canvas.drawLine(x, top, x, bottom, linePaint);
            canvas.drawLine(edge, center, x, center, linePaint);
            canvas.drawLine(x - direction * dp(8), center - dp(6), x, center, linePaint);
            canvas.drawLine(x - direction * dp(8), center + dp(6), x, center, linePaint);

            float textWidth = labelPaint.measureText(label);
            float labelLeft = Math.max(dp(8), Math.min(leftEdge ? x + dp(12) : x - dp(12) - textWidth,
                    getBounds().width() - textWidth - dp(24)));
            float baseline = center - dp(18) - labelPaint.descent();
            canvas.drawRoundRect(labelLeft - dp(8), baseline + labelPaint.ascent() - dp(6),
                    labelLeft + textWidth + dp(8), baseline + labelPaint.descent() + dp(6),
                    dp(8), dp(8), backgroundPaint);
            canvas.drawText(label, labelLeft, baseline, labelPaint);
        }

        @Override public void setAlpha(int alpha) {
            linePaint.setAlpha(alpha);
            outlinePaint.setAlpha(alpha);
            labelPaint.setAlpha(alpha);
            backgroundPaint.setAlpha(Math.round(alpha * 0xe6 / 255f));
            originPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {
            linePaint.setColorFilter(filter);
            outlinePaint.setColorFilter(filter);
            labelPaint.setColorFilter(filter);
            backgroundPaint.setColorFilter(filter);
            originPaint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
