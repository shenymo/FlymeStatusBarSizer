package com.example.flymestatusbarsizer.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;

/** Interactive trigger-zone editor drawn above the settings screen. */
public final class SideGestureZoneOverlay {
    private static final String[] SHORT_KEYS = {
            SettingsStore.KEY_SIDE_GESTURE_LEFT_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_LEFT_UP_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_LEFT_DOWN_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_RIGHT_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_RIGHT_UP_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_RIGHT_DOWN_ACTION
    };
    private static final String[] LEGACY_KEYS = {
            SettingsStore.KEY_SIDE_GESTURE_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_UP_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_DOWN_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_UP_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_DOWN_ACTION
    };
    private static final String[] LONG_KEYS = {
            SettingsStore.KEY_SIDE_GESTURE_LEFT_LONG_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_LEFT_UP_LONG_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_LEFT_DOWN_LONG_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_RIGHT_LONG_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_RIGHT_UP_LONG_ACTION,
            SettingsStore.KEY_SIDE_GESTURE_RIGHT_DOWN_LONG_ACTION
    };
    private static final int[] ACTION_VALUES = {
            SettingsStore.SIDE_GESTURE_ACTION_NONE,
            SettingsStore.SIDE_GESTURE_ACTION_BACK,
            SettingsStore.SIDE_GESTURE_ACTION_HOME,
            SettingsStore.SIDE_GESTURE_ACTION_RECENTS,
            SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT,
            SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE
    };
    private static final String[] ACTION_LABELS = {
            "无动作", "返回", "主页", "后台多任务界面", "全局负一屏", "多应用工作台"
    };
    private static final int MAX_DISTANCE_DP = SettingsStore.MAX_ASSISTANT_GESTURE_LONG_DISTANCE_DP;
    private static final int DRAG_NONE = 0;
    private static final int DRAG_SHORT = 1;
    private static final int DRAG_LONG = 2;

    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final int accentColor;
    private final Runnable onActionChanged;
    private final Runnable onClosed;
    private final OverlayView view;
    private final int[] shortActions = new int[6];
    private final int[] longActions = new int[6];
    private int shortDistanceDp;
    private int longDistanceDp;
    private boolean leftSide = true;
    private boolean splitEnabled;
    private boolean longDistanceMode;
    private boolean attached;

    public SideGestureZoneOverlay(MainActivity activity, SharedPreferences prefs, int accentColor,
            Runnable onActionChanged, Runnable onClosed) {
        this.activity = activity;
        this.prefs = prefs;
        this.accentColor = accentColor;
        this.onActionChanged = onActionChanged;
        this.onClosed = onClosed;
        this.view = new OverlayView(activity);
        loadFromPreferences();
    }

    public boolean show() {
        if (attached) return true;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return false;
        ((ViewGroup) decor).addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        attached = true;
        return true;
    }

    public void close(boolean save) {
        if (!attached) return;
        if (save) saveToPreferences();
        attached = false;
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        if (onClosed != null) onClosed.run();
    }

    private void loadFromPreferences() {
        for (int i = 0; i < SHORT_KEYS.length; i++) {
            shortActions[i] = SettingsStore.readSideGestureAction(prefs,
                    SHORT_KEYS[i], LEGACY_KEYS[i]);
            longActions[i] = SettingsStore.readSideGestureActionWithFallback(prefs,
                    LONG_KEYS[i], SHORT_KEYS[i], LEGACY_KEYS[i]);
        }
        shortDistanceDp = readDistance(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_DP,
                SettingsStore.MIN_ASSISTANT_GESTURE_DISTANCE_DP,
                SettingsStore.MAX_ASSISTANT_GESTURE_DISTANCE_DP);
        longDistanceDp = readDistance(SettingsStore.KEY_ASSISTANT_GESTURE_LONG_DISTANCE_DP,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_LONG_DISTANCE_DP,
                SettingsStore.MIN_ASSISTANT_GESTURE_LONG_DISTANCE_DP,
                SettingsStore.MAX_ASSISTANT_GESTURE_LONG_DISTANCE_DP);
        splitEnabled = SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED);
        if (!splitEnabled) longDistanceMode = false;
    }

    private int readDistance(String key, int defaultValue, int min, int max) {
        int value = SettingsStore.readInt(prefs, key, defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    private void saveToPreferences() {
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, shortDistanceDp)
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_LONG_DISTANCE_DP, longDistanceDp);
        for (int i = 0; i < SHORT_KEYS.length; i++) {
            editor.putInt(SHORT_KEYS[i], shortActions[i]);
            editor.putInt(LONG_KEYS[i], longActions[i]);
        }
        editor.apply();
        SettingsStore.notifyChanged(activity);
        if (onActionChanged != null) onActionChanged.run();
    }

    private final class OverlayView extends View {
        private final Paint zoneFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint zoneStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longThresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint controlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedControlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path zonePath = new Path();
        private final RectF leftControl = new RectF();
        private final RectF rightControl = new RectF();
        private final RectF shortControl = new RectF();
        private final RectF longControl = new RectF();
        private final RectF exitControl = new RectF();
        private final RectF saveControl = new RectF();
        private float density;
        private float scaledDensity;
        private float centerY;
        private float edge;
        private float direction;
        private float depth;
        private float originX;
        private float scale;
        private int dragging;
        private int pickerDirection = -1;
        private boolean pickerGestureActive;

        OverlayView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            setFocusable(false);
            zoneFill.setColor(0x3a4f9dff);
            zoneStroke.setColor(0x9a8db7ff);
            zoneStroke.setStyle(Paint.Style.STROKE);
            zoneStroke.setStrokeWidth(dp(1));
            zoneStroke.setStrokeCap(Paint.Cap.ROUND);
            zoneStroke.setStrokeJoin(Paint.Join.ROUND);
            thresholdPaint.setColor(0xff69a9ff);
            thresholdPaint.setStyle(Paint.Style.STROKE);
            thresholdPaint.setStrokeWidth(dp(2));
            thresholdPaint.setStrokeCap(Paint.Cap.ROUND);
            thresholdPaint.setStrokeJoin(Paint.Join.ROUND);
            longThresholdPaint.setColor(0xffffb45f);
            longThresholdPaint.setStyle(Paint.Style.STROKE);
            longThresholdPaint.setStrokeWidth(dp(2));
            longThresholdPaint.setStrokeCap(Paint.Cap.ROUND);
            longThresholdPaint.setStrokeJoin(Paint.Join.ROUND);
            handlePaint.setColor(0xff69a9ff);
            longHandlePaint.setColor(0xffffb45f);
            longHandlePaint.setAlpha(220);
            controlPaint.setColor(0xcc20262f);
            selectedControlPaint.setColor(accentColor);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            linePaint.setColor(0x90ffffff);
            linePaint.setStrokeWidth(dp(1));
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float top = statusBarHeight();
            float bottom = height - navigationBarHeight();
            centerY = (top + bottom) / 2f;
            edge = leftSide ? 0f : width;
            direction = leftSide ? 1f : -1f;
            depth = Math.min(width * 0.92f, dp(MAX_DISTANCE_DP));
            originX = edge;
            scale = depth / MAX_DISTANCE_DP;

            drawZones(canvas, top, bottom);
            drawControls(canvas, width, height, top);
            if (pickerDirection >= 0) drawPicker(canvas, width, height, top);
        }

        private void drawZones(Canvas canvas, float top, float bottom) {
            float cutStart = Math.min(dp(24), depth * 0.24f);
            float cutEnd = Math.max(cutStart, depth * 0.45f);
            drawZone(canvas, originX, top, bottom, centerY, cutStart, cutEnd, 1, 0x2f5e9dff);
            drawZone(canvas, originX, top, bottom, centerY, cutStart, cutEnd, 0, 0x2f48c5a0);
            drawZone(canvas, originX, top, bottom, centerY, cutStart, cutEnd, 2, 0x2fffaa62);
            drawConnectedZoneBoundaries(canvas, top, bottom, centerY, cutStart, cutEnd);
            canvas.drawLine(originX, centerY, originX + direction * depth, centerY, linePaint);
            drawThreshold(canvas, shortDistanceDp, thresholdPaint, handlePaint, -dp(16));
            drawThreshold(canvas, longDistanceDp, longThresholdPaint, longHandlePaint, dp(16));
            int offset = leftSide ? 0 : 3;
            drawActionLabel(canvas, actionLabel(offset + 1), 1, top, bottom);
            drawActionLabel(canvas, actionLabel(offset), 0, top, bottom);
            drawActionLabel(canvas, actionLabel(offset + 2), 2, top, bottom);
        }

        private void drawZone(Canvas canvas, float origin, float top, float bottom, float mid,
                float cutStart, float cutEnd, int zone, int color) {
            zonePath.reset();
            if (zone == 1) {
                zonePath.moveTo(origin, top);
                zonePath.lineTo(origin + direction * depth, top);
                zonePath.lineTo(origin + direction * depth, mid - cutEnd);
                appendCurvedBoundary(zonePath, origin + direction * depth, mid - cutEnd,
                        origin, mid - cutStart);
            } else if (zone == 0) {
                zonePath.moveTo(origin, mid - cutStart);
                appendCurvedBoundary(zonePath, origin, mid - cutStart,
                        origin + direction * depth, mid - cutEnd);
                zonePath.lineTo(origin + direction * depth, mid + cutEnd);
                appendCurvedBoundary(zonePath, origin + direction * depth, mid + cutEnd,
                        origin, mid + cutStart);
            } else {
                zonePath.moveTo(origin, mid + cutStart);
                appendCurvedBoundary(zonePath, origin, mid + cutStart,
                        origin + direction * depth, mid + cutEnd);
                zonePath.lineTo(origin + direction * depth, bottom);
                zonePath.lineTo(origin, bottom);
            }
            zonePath.close();
            zoneFill.setColor(color);
            canvas.drawPath(zonePath, zoneFill);
            canvas.drawPath(zonePath, zoneStroke);
        }

        private void drawConnectedZoneBoundaries(Canvas canvas, float top, float bottom,
                float mid, float cutStart, float cutEnd) {
            float farX = originX + direction * depth;
            Path boundaries = new Path();
            boundaries.moveTo(originX, mid - cutStart);
            appendCurvedBoundary(boundaries, originX, mid - cutStart, farX, mid - cutEnd);
            boundaries.lineTo(farX, mid + cutEnd);
            appendCurvedBoundary(boundaries, farX, mid + cutEnd, originX, mid + cutStart);
            boundaries.lineTo(originX, mid - cutStart);
            canvas.drawPath(boundaries, zoneStroke);
        }

        private void appendCurvedBoundary(Path path, float startX, float startY,
                float endX, float endY) {
            boolean startsAtOrigin = Math.abs(startX - originX) < Math.abs(endX - originX);
            float originY = startsAtOrigin ? startY : endY;
            float farY = startsAtOrigin ? endY : startY;
            float span = (startsAtOrigin ? endX : startX) - originX;
            float firstControlX = originX + span * 0.2f;
            float firstControlY = originY;
            float secondControlX = originX + span * 0.85f;
            float secondControlY = originY + (farY - originY) * 0.85f;
            if (startsAtOrigin) {
                path.cubicTo(firstControlX, firstControlY, secondControlX, secondControlY,
                        endX, endY);
            } else {
                path.cubicTo(secondControlX, secondControlY, firstControlX, firstControlY,
                        endX, endY);
            }
        }

        private void drawThreshold(Canvas canvas, int distanceDp, Paint paint, Paint handle,
                float handleOffset) {
            float radius = Math.min(depth, dp(distanceDp));
            float x = originX + direction * radius;
            float startAngle = (float) Math.atan(0.45f) + 0.01f;
            float endAngle = (float) Math.acos(0.35f);
            drawDiagonalThreshold(canvas, paint, startAngle, endAngle, -1f, distanceDp);
            drawCurvedThresholdMiddle(canvas, paint, startAngle, distanceDp);
            drawDiagonalThreshold(canvas, paint, startAngle, endAngle, 1f, distanceDp);
            canvas.drawCircle(x, centerY + handleOffset, dp(13), controlPaint);
            canvas.drawCircle(x, centerY + handleOffset, dp(8), handle);
            String label = distanceDp + "dp";
            textPaint.setTextSize(12f * scaledDensity);
            float labelX = x + direction * dp(24);
            float labelY = centerY + handleOffset - dp(16);
            canvas.drawText(label, labelX, labelY, textPaint);
        }

        private void drawDiagonalThreshold(Canvas canvas, Paint paint, float startAngle,
                float endAngle, float sign, int distanceDp) {
            Path path = new Path();
            int steps = 26;
            for (int i = 0; i <= steps; i++) {
                float angle = startAngle + (endAngle - startAngle) * i / steps;
                float radius = Math.min(MAX_DISTANCE_DP, thresholdRadiusDp(angle, distanceDp))
                        * density;
                float x = originX + direction * radius * (float) Math.cos(angle);
                float y = centerY + sign * radius * (float) Math.sin(angle);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            canvas.drawPath(path, paint);
        }

        private void drawCurvedThresholdMiddle(Canvas canvas, Paint paint, float startAngle,
                int distanceDp) {
            float radius = Math.min(MAX_DISTANCE_DP, thresholdRadiusDp(startAngle, distanceDp))
                    * density;
            float inset = radius * (float) Math.cos(startAngle);
            float halfHeight = radius * (float) Math.sin(startAngle);
            float endpointX = originX + direction * inset;
            float topY = centerY - halfHeight;
            float bottomY = centerY + halfHeight;
            float targetX = originX + direction * Math.min(depth, dp(distanceDp));
            float controlX = endpointX + (targetX - endpointX) / 0.75f;
            float controlOffsetY = halfHeight * 0.35f;
            Path path = new Path();
            path.moveTo(endpointX, topY);
            path.cubicTo(controlX, centerY - controlOffsetY,
                    controlX, centerY + controlOffsetY, endpointX, bottomY);
            canvas.drawPath(path, paint);
        }

        private float thresholdRadiusDp(float angle, int distanceDp) {
            return Math.max(distanceDp, Math.max(
                    24f / (float) Math.sin(angle),
                    0.35f * distanceDp / (float) Math.cos(angle)));
        }

        private void drawActionLabel(Canvas canvas, String text, int zone, float top, float bottom) {
            textPaint.setTextSize(14f * scaledDensity);
            float y = zone == 1 ? top + (centerY - top) * 0.48f
                    : zone == 0 ? centerY : centerY + (bottom - centerY) * 0.52f;
            float x = originX + direction * Math.min(depth * 0.62f, dp(250));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = y - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, x, baseline, textPaint);
        }

        private void drawControls(Canvas canvas, float width, float height, float top) {
            float margin = dp(12);
            float controlTop = top + dp(12);
            float controlHeight = dp(42);
            float gap = dp(6);
            float sideWidth = dp(72);
            float modeWidth = dp(104);
            leftControl.set(margin, controlTop, margin + sideWidth, controlTop + controlHeight);
            rightControl.set(leftControl.right + gap, controlTop,
                    leftControl.right + gap + sideWidth, controlTop + controlHeight);
            shortControl.set(rightControl.right + gap, controlTop,
                    rightControl.right + gap + modeWidth, controlTop + controlHeight);
            longControl.set(shortControl.right + gap, controlTop,
                    Math.min(width - margin, shortControl.right + gap + modeWidth),
                    controlTop + controlHeight);
            drawControl(canvas, leftControl, "左侧", leftSide);
            drawControl(canvas, rightControl, "右侧", !leftSide);
            drawControl(canvas, shortControl, "短距离", !longDistanceMode);
            drawControl(canvas, longControl, "长距离", splitEnabled && longDistanceMode);

            float buttonTop = height - navigationBarHeight() - dp(58);
            float buttonHeight = dp(44);
            exitControl.set(margin, buttonTop, margin + dp(96), buttonTop + buttonHeight);
            saveControl.set(width - margin - dp(96), buttonTop, width - margin,
                    buttonTop + buttonHeight);
            drawControl(canvas, exitControl, "退出", false);
            drawControl(canvas, saveControl, "保存", true);
            textPaint.setTextSize(12f * scaledDensity);
            textPaint.setColor(0xeeffffff);
            canvas.drawText("点击彩色区域设置动作，拖动圆点调整距离", width / 2f,
                    buttonTop - dp(10), textPaint);
            textPaint.setColor(Color.WHITE);
        }

        private void drawControl(Canvas canvas, RectF rect, String text, boolean selected) {
            controlPaint.setColor(selected ? accentColor : 0xcc20262f);
            canvas.drawRoundRect(rect, dp(10), dp(10), controlPaint);
            textPaint.setTextSize(14f * scaledDensity);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, rect.centerX(), baseline, textPaint);
        }

        private void drawPicker(Canvas canvas, float width, float height, float top) {
            float panelLeft = dp(22);
            float panelRight = width - dp(22);
            float panelTop = top + dp(70);
            float rowHeight = dp(44);
            float panelBottom = panelTop + dp(38) + rowHeight * ACTION_LABELS.length;
            controlPaint.setColor(0xe62a313a);
            canvas.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom,
                    dp(14), dp(14), controlPaint);
            int index = (leftSide ? 0 : 3) + pickerDirection;
            boolean longDistance = splitEnabled && longDistanceMode;
            int action = (longDistance ? longActions : shortActions)[index];
            textPaint.setTextSize(14f * scaledDensity);
            canvas.drawText(directionName(pickerDirection) + " · "
                    + (longDistance ? "长距离动作" : "短距离动作"),
                    (panelLeft + panelRight) / 2f, panelTop + dp(25), textPaint);
            for (int i = 0; i < ACTION_LABELS.length; i++) {
                float rowTop = panelTop + dp(38) + i * rowHeight;
                if (ACTION_VALUES[i] == action) {
                    selectedControlPaint.setColor(accentColor);
                    canvas.drawRoundRect(panelLeft + dp(8), rowTop + dp(2),
                            panelRight - dp(8), rowTop + rowHeight - dp(2),
                            dp(8), dp(8), selectedControlPaint);
                }
                canvas.drawText(ACTION_LABELS[i], (panelLeft + panelRight) / 2f,
                        rowTop + rowHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
                        textPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            int action = event.getActionMasked();
            if (pickerGestureActive) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    pickerGestureActive = false;
                }
                return true;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                if (pickerDirection >= 0) {
                    pickerGestureActive = true;
                    if (handlePickerTap(x, y)) return true;
                    pickerDirection = -1;
                    invalidate();
                    return true;
                }
                if (exitControl.contains(x, y)) {
                    close(false);
                    return true;
                }
                if (saveControl.contains(x, y)) {
                    close(true);
                    return true;
                }
                if (leftControl.contains(x, y)) {
                    leftSide = true;
                    invalidate();
                    return true;
                }
                if (rightControl.contains(x, y)) {
                    leftSide = false;
                    invalidate();
                    return true;
                }
                if (shortControl.contains(x, y)) {
                    longDistanceMode = false;
                    invalidate();
                    return true;
                }
                if (longControl.contains(x, y) && splitEnabled) {
                    longDistanceMode = true;
                    invalidate();
                    return true;
                }
                dragging = hitHandle(x, y);
                if (dragging != DRAG_NONE) return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (dragging != DRAG_NONE) {
                    int distance = Math.round(((x - originX) * direction) / density);
                    if (dragging == DRAG_LONG) {
                        longDistanceDp = clamp(distance,
                                SettingsStore.MIN_ASSISTANT_GESTURE_LONG_DISTANCE_DP,
                                SettingsStore.MAX_ASSISTANT_GESTURE_LONG_DISTANCE_DP);
                    } else {
                        shortDistanceDp = clamp(distance,
                                SettingsStore.MIN_ASSISTANT_GESTURE_DISTANCE_DP,
                                SettingsStore.MAX_ASSISTANT_GESTURE_DISTANCE_DP);
                    }
                    invalidate();
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if (dragging != DRAG_NONE) {
                    dragging = DRAG_NONE;
                    activity.performTapHaptic(this);
                    return true;
                }
                float inward = (x - originX) * direction;
                if (inward > 0 && y > topInset() && y < getHeight() - navigationBarHeight()) {
                    float dy = y - centerY;
                    float distanceDp = inward / density;
                    int directionIndex = Math.abs(dy) < Math.max(dp(24), inward * 0.45f)
                            ? 0 : dy < 0 ? 1 : 2;
                    if (distanceDp > 8f) {
                        pickerDirection = directionIndex;
                        invalidate();
                    }
                }
                return true;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                dragging = DRAG_NONE;
            }
            return true;
        }

        private boolean handlePickerTap(float x, float y) {
            float panelLeft = dp(22);
            float panelRight = getWidth() - dp(22);
            float panelTop = topInset() + dp(70);
            float rowHeight = dp(44);
            float rowStart = panelTop + dp(38);
            if (x < panelLeft || x > panelRight || y < rowStart
                    || y >= rowStart + rowHeight * ACTION_LABELS.length) return false;
            int actionIndex = (int) ((y - rowStart) / rowHeight);
            int index = (leftSide ? 0 : 3) + pickerDirection;
            if (splitEnabled && longDistanceMode) longActions[index] = ACTION_VALUES[actionIndex];
            else shortActions[index] = ACTION_VALUES[actionIndex];
            pickerDirection = -1;
            invalidate();
            activity.performTapHaptic(this);
            return true;
        }

        private int hitHandle(float x, float y) {
            float hit = dp(28);
            float shortX = originX + direction * Math.min(depth, dp(shortDistanceDp));
            float longX = originX + direction * Math.min(depth, dp(longDistanceDp));
            float shortY = centerY - dp(16);
            float longY = centerY + dp(16);
            boolean shortHit = Math.hypot(x - shortX, y - shortY) <= hit;
            boolean longHit = splitEnabled && Math.hypot(x - longX, y - longY) <= hit;
            if (!shortHit && !longHit) return DRAG_NONE;
            if (shortHit && longHit) {
                return Math.hypot(x - shortX, y - shortY)
                        <= Math.hypot(x - longX, y - longY) ? DRAG_SHORT : DRAG_LONG;
            }
            return shortHit ? DRAG_SHORT : DRAG_LONG;
        }

        private String actionLabel(int index) {
            int action = (splitEnabled && longDistanceMode) ? longActions[index] : shortActions[index];
            switch (action) {
                case SettingsStore.SIDE_GESTURE_ACTION_BACK: return "返回";
                case SettingsStore.SIDE_GESTURE_ACTION_HOME: return "主页";
                case SettingsStore.SIDE_GESTURE_ACTION_RECENTS: return "多任务";
                case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT: return "全局负一屏";
                case SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE: return "多应用工作台";
                default: return "无动作";
            }
        }

        private String directionName(int direction) {
            return direction == 0 ? "横向滑动" : direction == 1 ? "上斜滑动" : "下斜滑动";
        }

        private int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private float topInset() {
            return statusBarHeight();
        }

        private int statusBarHeight() {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            return id == 0 ? Math.round(dp(24)) : getResources().getDimensionPixelSize(id);
        }

        private int navigationBarHeight() {
            int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            return id == 0 ? Math.round(dp(24)) : getResources().getDimensionPixelSize(id);
        }

        private float dp(float value) {
            return value * density;
        }
    }
}
