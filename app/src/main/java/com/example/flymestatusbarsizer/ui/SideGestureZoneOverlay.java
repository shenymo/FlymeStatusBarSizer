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
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.SideGestureTargetStore;

import java.util.List;

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
            SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE,
            SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER,
            SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS,
            SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL
    };
    private static final String[] ACTION_LABELS = {
            "无动作", "返回", "主页", "后台多任务界面", "全局负一屏", "多应用工作台", "任务切换器", "快捷工具", "应用启动器",
            "打开指定应用", "打开 Activity", "打开 Intent", "打开 Scheme URL"
    };
    private static final int MAX_DISTANCE_DP = SettingsStore.MAX_ASSISTANT_GESTURE_LONG_DISTANCE_DP;
    private static final int DRAG_NONE = 0;
    private static final int DRAG_SHORT = 1;
    private static final int DRAG_LONG = 2;
    private static final int DRAG_UP_ANGLE = 3;
    private static final int DRAG_DOWN_ANGLE = 4;
    private static final int DRAG_MIN_VERTICAL = 5;

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
    private int upAngle;
    private int downAngle;
    private int minVerticalDp;
    private boolean leftSide = true;
    private boolean splitEnabled;
    private boolean attached;
    private int[] availableActionValues;
    private String[] availableActionLabels;

    public SideGestureZoneOverlay(MainActivity activity, SharedPreferences prefs, int accentColor,
            Runnable onActionChanged, Runnable onClosed) {
        this.activity = activity;
        this.prefs = prefs;
        this.accentColor = accentColor;
        this.onActionChanged = onActionChanged;
        this.onClosed = onClosed;
        this.view = new OverlayView(activity);
        loadFromPreferences();
        loadAvailableActions();
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
        upAngle = readBounded(SettingsStore.KEY_ASSISTANT_GESTURE_UP_ANGLE,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_UP_ANGLE,
                SettingsStore.MIN_ASSISTANT_GESTURE_ANGLE, SettingsStore.MAX_ASSISTANT_GESTURE_ANGLE);
        downAngle = readBounded(SettingsStore.KEY_ASSISTANT_GESTURE_DOWN_ANGLE,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DOWN_ANGLE,
                SettingsStore.MIN_ASSISTANT_GESTURE_ANGLE, SettingsStore.MAX_ASSISTANT_GESTURE_ANGLE);
        minVerticalDp = readBounded(SettingsStore.KEY_ASSISTANT_GESTURE_MIN_VERTICAL_DP,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_MIN_VERTICAL_DP,
                SettingsStore.MIN_ASSISTANT_GESTURE_MIN_VERTICAL_DP,
                SettingsStore.MAX_ASSISTANT_GESTURE_MIN_VERTICAL_DP);
    }

    private void loadAvailableActions() {
        List<SideGestureTargetStore.Target> targets = SideGestureTargetStore.parse(
                SideGestureTargetStore.readRaw(prefs));
        availableActionValues = new int[ACTION_VALUES.length + targets.size()];
        availableActionLabels = new String[ACTION_LABELS.length + targets.size()];
        System.arraycopy(ACTION_VALUES, 0, availableActionValues, 0, ACTION_VALUES.length);
        System.arraycopy(ACTION_LABELS, 0, availableActionLabels, 0, ACTION_LABELS.length);
        for (int i = 0; i < targets.size(); i++) {
            SideGestureTargetStore.Target target = targets.get(i);
            availableActionValues[ACTION_VALUES.length + i] = target.id;
            availableActionLabels[ACTION_LABELS.length + i] = "打开："
                    + SideGestureTargetStore.shortLabel(target);
        }
    }

    private int readDistance(String key, int defaultValue, int min, int max) {
        int value = SettingsStore.readInt(prefs, key, defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    private int readBounded(String key, int defaultValue, int min, int max) {
        int value = SettingsStore.readInt(prefs, key, defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    private void saveToPreferences() {
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, shortDistanceDp)
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_LONG_DISTANCE_DP, longDistanceDp)
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_UP_ANGLE, upAngle)
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DOWN_ANGLE, downAngle)
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_MIN_VERTICAL_DP, minVerticalDp);
        for (int i = 0; i < SHORT_KEYS.length; i++) {
            editor.putInt(SHORT_KEYS[i], shortActions[i]);
            editor.putInt(LONG_KEYS[i], longActions[i]);
        }
        editor.apply();
        SettingsStore.notifyChanged(activity);
        if (onActionChanged != null) onActionChanged.run();
    }

    private final class OverlayView extends View {
        private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longThresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shortRegionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longRegionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint controlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedControlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF leftControl = new RectF();
        private final RectF rightControl = new RectF();
        private final RectF exitControl = new RectF();
        private final RectF saveControl = new RectF();
        private final RectF[][] actionLabelBounds = new RectF[2][6];
        private float density;
        private float scaledDensity;
        private float centerY;
        private float edge;
        private float direction;
        private float originX;
        private float scale;
        private int dragging;
        private int pickerDirection = -1;
        private boolean pickerLongDistance;
        private boolean pickerGestureActive;
        private boolean pickerTouchMoved;
        private boolean sideControlTouchActive;
        private float pickerTouchStartY;
        private int pickerScrollIndex;
        private float dragOffsetX;

        OverlayView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            setFocusable(false);
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
            shortRegionPaint.setColor(0x294b8dff);
            longRegionPaint.setColor(0x29ffad5a);
            handlePaint.setColor(0xff69a9ff);
            longHandlePaint.setColor(0xffffb45f);
            longHandlePaint.setAlpha(220);
            controlPaint.setColor(0xcc20262f);
            selectedControlPaint.setColor(accentColor);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            labelBackgroundPaint.setColor(0xd869a9ff);
            for (int mode = 0; mode < actionLabelBounds.length; mode++) {
                for (int action = 0; action < actionLabelBounds[mode].length; action++) {
                    actionLabelBounds[mode][action] = new RectF();
                }
            }
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
            originX = edge;
            scale = density;

            canvas.drawColor(0xb8000000);
            int save = canvas.save();
            canvas.clipRect(0, top, width, bottom);
            drawZones(canvas);
            canvas.restoreToCount(save);
            drawControls(canvas, width, height, top);
            if (pickerDirection >= 0) drawPicker(canvas, width, height, top);
        }

        private void drawZones(Canvas canvas) {
            for (int mode = 0; mode < actionLabelBounds.length; mode++) {
                for (int action = 0; action < actionLabelBounds[mode].length; action++) {
                    actionLabelBounds[mode][action].setEmpty();
                }
            }
            if (splitEnabled) {
                if (longDistanceDp >= shortDistanceDp) {
                    if (longDistanceDp > shortDistanceDp) {
                        drawTriggerRegion(canvas, shortDistanceDp, shortDistanceDp, longDistanceDp,
                                shortRegionPaint);
                    }
                }
                drawTriggerRegion(canvas, shortDistanceDp,
                        Math.max(shortDistanceDp, longDistanceDp), -1, longRegionPaint);
            } else {
                drawTriggerRegion(canvas, shortDistanceDp, shortDistanceDp, -1, shortRegionPaint);
            }
            drawThreshold(canvas, shortDistanceDp, thresholdPaint, handlePaint);
            int offset = leftSide ? 0 : 3;
            if (!splitEnabled || longDistanceDp > shortDistanceDp) {
                drawActionLabels(canvas, shortDistanceDp, thresholdPaint, offset, false);
            }
            if (splitEnabled) {
                drawThreshold(canvas, longDistanceDp, longThresholdPaint, longHandlePaint);
                drawActionLabels(canvas, Math.max(shortDistanceDp, longDistanceDp),
                        longThresholdPaint, offset, true);
            }
            canvas.drawCircle(originX, centerY, dp(5), handlePaint);
            drawGeometryHandles(canvas);
            textPaint.setTextSize(11f * scaledDensity);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(leftSide ? Paint.Align.LEFT : Paint.Align.RIGHT);
            canvas.drawText("起点", originX + direction * dp(12), centerY - dp(10), textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        private void drawTriggerRegion(Canvas canvas, int readyDistanceDp, int radialMinDp,
                float radialMaxDp, Paint fill) {
            float verticalLimit = Math.max((centerY - statusBarHeight()) / scale,
                    (getHeight() - navigationBarHeight() - centerY) / scale);
            float screenWidthDp = getWidth() / scale;
            float screenRadiusDp = (float) Math.hypot(screenWidthDp, verticalLimit);
            float end = radialMaxDp > 0 ? Math.min(radialMaxDp, screenRadiusDp)
                    : screenRadiusDp;
            if (end <= readyDistanceDp) return;
            drawDiagonalRegion(canvas, readyDistanceDp, radialMinDp, end, verticalLimit,
                    fill, true);
            drawDiagonalRegion(canvas, readyDistanceDp, radialMinDp, end, verticalLimit,
                    fill, false);
            drawHorizontalRegion(canvas, readyDistanceDp, radialMinDp, end, verticalLimit, fill,
                    true);
            drawHorizontalRegion(canvas, readyDistanceDp, radialMinDp, end, verticalLimit, fill,
                    false);
        }

        private void drawDiagonalRegion(Canvas canvas, int readyDistanceDp, int radialMinDp,
                float maxDistanceDp, float verticalLimit, Paint fill, boolean upper) {
            float startX = readyDistanceDp * 0.35f;
            float endX = Math.min(maxDistanceDp, getWidth() / scale);
            Path region = new Path();
            boolean started = false;
            int steps = 96;
            for (int i = 0; i <= steps; i++) {
                float x = startX + (endX - startX) * i / steps;
                float lower = diagonalBoundaryY(x, radialMinDp, upper);
                float upperY = Math.min(verticalLimit,
                        (float) Math.sqrt(Math.max(0f,
                                maxDistanceDp * maxDistanceDp - x * x)));
                if (lower >= upperY) continue;
                float y = upper ? upperY : lower;
                float screenY = centerY + (upper ? -1f : 1f) * y * scale;
                if (!started) {
                    region.moveTo(screenX(x), screenY);
                    started = true;
                } else {
                    region.lineTo(screenX(x), screenY);
                }
            }
            if (!started) return;
            for (int i = steps; i >= 0; i--) {
                float x = startX + (endX - startX) * i / steps;
                float lower = diagonalBoundaryY(x, radialMinDp, upper);
                float upperY = Math.min(verticalLimit,
                        (float) Math.sqrt(Math.max(0f,
                                maxDistanceDp * maxDistanceDp - x * x)));
                if (lower >= upperY) continue;
                float y = upper ? lower : upperY;
                float screenY = centerY + (upper ? -1f : 1f) * y * scale;
                region.lineTo(screenX(x), screenY);
            }
            region.close();
            canvas.drawPath(region, fill);
        }

        private void drawHorizontalRegion(Canvas canvas, int readyDistanceDp, int radialMinDp,
                float maxDistanceDp, float verticalLimit, Paint fill, boolean upper) {
            float startX = readyDistanceDp;
            float endX = Math.min(maxDistanceDp, getWidth() / scale);
            if (endX <= startX) return;
            Path region = new Path();
            int steps = 96;
            boolean started = false;
            for (int i = 0; i <= steps; i++) {
                float x = startX + (endX - startX) * i / steps;
                float inner = radialMinDp > 0 ? (float) Math.sqrt(Math.max(0f,
                        radialMinDp * radialMinDp - x * x)) : 0f;
                float outer = maxDistanceDp > 0 ? (float) Math.sqrt(Math.max(0f,
                        maxDistanceDp * maxDistanceDp - x * x)) : verticalLimit;
                float y = directionBandY(x, upper);
                float edge = Math.min(verticalLimit, Math.min(y, outer));
                if (edge <= inner) continue;
                if (!started) {
                    region.moveTo(screenX(x), centerY + (upper ? -1f : 1f) * edge * scale);
                    started = true;
                } else {
                    region.lineTo(screenX(x), centerY + (upper ? -1f : 1f) * edge * scale);
                }
            }
            if (!started) return;
            for (int i = steps; i >= 0; i--) {
                float x = startX + (endX - startX) * i / steps;
                float inner = radialMinDp > 0 ? (float) Math.sqrt(Math.max(0f,
                        radialMinDp * radialMinDp - x * x)) : 0f;
                float outer = maxDistanceDp > 0 ? (float) Math.sqrt(Math.max(0f,
                        maxDistanceDp * maxDistanceDp - x * x)) : verticalLimit;
                float y = directionBandY(x, upper);
                float edge = Math.min(verticalLimit, Math.min(y, outer));
                if (edge > inner) region.lineTo(screenX(x), centerY + (upper ? -1f : 1f) * inner * scale);
            }
            region.close();
            canvas.drawPath(region, fill);
        }

        private float diagonalBoundaryY(float x, int minDistanceDp, boolean upper) {
            float distanceLimit = x < minDistanceDp
                    ? (float) Math.sqrt(Math.max(0f,
                    minDistanceDp * minDistanceDp - x * x)) : 0f;
            return Math.max(directionBandY(x, upper), distanceLimit);
        }

        private float directionBandY(float x, boolean upper) {
            float angle = (float) Math.tan(Math.toRadians(angleDegrees(upper ? upAngle : downAngle)));
            return Math.max(minVerticalDp, x * angle);
        }

        private float screenX(float distanceDp) {
            return originX + direction * distanceDp * scale;
        }

        private void drawThreshold(Canvas canvas, int distanceDp, Paint paint, Paint handle) {
            drawThresholdContour(canvas, paint, distanceDp);
            float x = screenX(distanceDp);
            float y = centerY;
            canvas.drawCircle(x, y, dp(13), controlPaint);
            canvas.drawCircle(x, y, dp(8), handle);
            String label = distanceDp + "dp";
            textPaint.setTextSize(12f * scaledDensity);
            float halfWidth = textPaint.measureText(label) / 2f + dp(6);
            float labelX = Math.max(halfWidth + dp(8),
                    Math.min(getWidth() - halfWidth - dp(8), x + direction * dp(28)));
            float labelY = y - dp(16);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = labelY - (metrics.ascent + metrics.descent) / 2f;
            labelBackgroundPaint.setColor(Color.argb(0xe0,
                    Color.red(handle.getColor()), Color.green(handle.getColor()),
                    Color.blue(handle.getColor())));
            canvas.drawRoundRect(labelX - halfWidth, baseline + metrics.ascent - dp(3),
                    labelX + halfWidth, baseline + metrics.descent + dp(3),
                    dp(6), dp(6), labelBackgroundPaint);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(label, labelX, baseline, textPaint);
        }

        private void drawGeometryHandles(Canvas canvas) {
            float angleDistance = angleHandleDistanceDp();
            float angleX = screenX(angleDistance);
            float upY = centerY - angleDistance
                    * (float) Math.tan(Math.toRadians(angleDegrees(upAngle))) * scale;
            float downY = centerY + angleDistance
                    * (float) Math.tan(Math.toRadians(angleDegrees(downAngle))) * scale;
            canvas.drawCircle(angleX, upY, dp(13), controlPaint);
            canvas.drawCircle(angleX, upY, dp(8), handlePaint);
            canvas.drawCircle(angleX, downY, dp(13), controlPaint);
            canvas.drawCircle(angleX, downY, dp(8), handlePaint);
            float verticalDistance = Math.max(32f, Math.min(shortDistanceDp,
                    getWidth() / scale * 0.18f));
            float verticalX = screenX(verticalDistance);
            float upperMinY = centerY - minVerticalDp * scale;
            float lowerMinY = centerY + minVerticalDp * scale;
            canvas.drawCircle(verticalX, upperMinY, dp(11), controlPaint);
            canvas.drawCircle(verticalX, upperMinY, dp(6), longHandlePaint);
            canvas.drawCircle(verticalX, lowerMinY, dp(11), controlPaint);
            canvas.drawCircle(verticalX, lowerMinY, dp(6), longHandlePaint);
            drawGeometryLabel(canvas, angleX, upY - dp(18), "上 " + angleText(upAngle) + "°");
            drawGeometryLabel(canvas, angleX, downY + dp(18), "下 " + angleText(downAngle) + "°");
            drawGeometryLabel(canvas, verticalX, upperMinY - dp(16), "竖 ±" + minVerticalDp + "dp");
        }

        private void drawGeometryLabel(Canvas canvas, float x, float y, String text) {
            textPaint.setTextSize(10f * scaledDensity);
            textPaint.setColor(Color.WHITE);
            float halfWidth = textPaint.measureText(text) / 2f + dp(5);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = y - (metrics.ascent + metrics.descent) / 2f;
            labelBackgroundPaint.setColor(0xd820262f);
            canvas.drawRoundRect(x - halfWidth, baseline + metrics.ascent - dp(2),
                    x + halfWidth, baseline + metrics.descent + dp(2),
                    dp(4), dp(4), labelBackgroundPaint);
            canvas.drawText(text, x, baseline, textPaint);
        }

        private float angleHandleDistanceDp() {
            float widthDp = getWidth() / scale;
            float upNeed = minVerticalDp
                    / (float) Math.tan(Math.toRadians(Math.max(1f, angleDegrees(upAngle))));
            float downNeed = minVerticalDp
                    / (float) Math.tan(Math.toRadians(Math.max(1f, angleDegrees(downAngle))));
            float verticalLimit = Math.min((centerY - topInset()) / scale,
                    (getHeight() - navigationBarHeight() - centerY) / scale);
            float upLimit = verticalLimit
                    / (float) Math.tan(Math.toRadians(angleDegrees(upAngle)));
            float downLimit = verticalLimit
                    / (float) Math.tan(Math.toRadians(angleDegrees(downAngle)));
            float availableDistance = Math.min(widthDp * 0.68f, Math.min(upLimit, downLimit));
            float desiredDistance = Math.max(shortDistanceDp + 56f,
                    Math.max(120f, Math.max(upNeed, downNeed) + 16f));
            return Math.max(1f, Math.min(availableDistance, desiredDistance));
        }

        private float angleDegrees(int angleTenths) {
            return angleTenths / 10f;
        }

        private String angleText(int angleTenths) {
            return angleTenths % 10 == 0
                    ? Integer.toString(angleTenths / 10)
                    : (angleTenths / 10) + "." + (angleTenths % 10);
        }

        private void drawThresholdContour(Canvas canvas, Paint paint, int distanceDp) {
            float x = screenX(distanceDp);
            float upperBand = directionBandY(distanceDp, true) * scale;
            float lowerBand = directionBandY(distanceDp, false) * scale;
            canvas.drawLine(x, centerY - upperBand, x, centerY + lowerBand, paint);
            float screenWidthDp = getWidth() / scale;
            float startX = distanceDp * 0.35f;
            float endX = Math.min(screenWidthDp, (float) Math.hypot(screenWidthDp,
                    Math.max((centerY - statusBarHeight()) / scale,
                            (getHeight() - navigationBarHeight() - centerY) / scale)));
            if (startX >= endX) return;
            for (int side = -1; side <= 1; side += 2) {
                Path boundary = new Path();
                int steps = 96;
                for (int i = 0; i <= steps; i++) {
                    float dx = startX + (endX - startX) * i / steps;
                    float radialLimit = dx < distanceDp
                            ? (float) Math.sqrt(Math.max(0f,
                            distanceDp * distanceDp - dx * dx)) : 0f;
                    float dy = Math.max(directionBandY(dx, side < 0), radialLimit);
                    float screenY = centerY + side * dy * scale;
                    if (i == 0) boundary.moveTo(screenX(dx), screenY);
                    else boundary.lineTo(screenX(dx), screenY);
                }
                canvas.drawPath(boundary, paint);
            }
        }

        private void drawActionLabels(Canvas canvas, int distanceDp, Paint arcPaint,
                int actionOffset, boolean longDistance) {
            float horizontalX = Math.min(getWidth() / scale,
                    distanceDp + (splitEnabled && longDistance ? 30f : 46f));
            float diagonalX = Math.min(getWidth() / scale,
                    Math.max(distanceDp * 0.72f, distanceDp * 0.35f + 30f));
            float upDiagonalY = Math.max(directionBandY(diagonalX, true) + dp(24) / scale,
                    (float) Math.sqrt(Math.max(0f, distanceDp * distanceDp
                            - diagonalX * diagonalX)) + dp(24) / scale);
            float downDiagonalY = Math.max(directionBandY(diagonalX, false) + dp(24) / scale,
                    (float) Math.sqrt(Math.max(0f, distanceDp * distanceDp
                            - diagonalX * diagonalX)) + dp(24) / scale);
            int mode = longDistance ? 1 : 0;
            String prefix = splitEnabled ? (longDistance ? "长 " : "短 ") : "";
            drawArcActionLabel(canvas, prefix + actionLabel(actionOffset, longDistance),
                    screenX(horizontalX), centerY, 1, arcPaint, mode, actionOffset);
            drawArcActionLabel(canvas, prefix + actionLabel(actionOffset + 1, longDistance),
                    screenX(diagonalX), centerY - upDiagonalY * scale, 0, arcPaint,
                    mode, actionOffset + 1);
            drawArcActionLabel(canvas, prefix + actionLabel(actionOffset + 2, longDistance),
                    screenX(diagonalX), centerY + downDiagonalY * scale, 2, arcPaint,
                    mode, actionOffset + 2);
        }

        private void drawArcActionLabel(Canvas canvas, String text, float x, float y, int zone,
                Paint arcPaint, int mode, int actionIndex) {
            textPaint.setTextSize(13f * scaledDensity);
            textPaint.setColor(Color.WHITE);
            float labelX = x + direction * dp(24);
            float labelY = y + (zone == 0 ? -dp(10) : zone == 2 ? dp(18) : -dp(18));
            float halfWidth = textPaint.measureText(text) / 2f + dp(6);
            labelX = Math.max(halfWidth + dp(8), Math.min(getWidth() - halfWidth - dp(8), labelX));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = labelY - (metrics.ascent + metrics.descent) / 2f;
            labelBackgroundPaint.setColor(Color.argb(0xd8,
                    Color.red(arcPaint.getColor()), Color.green(arcPaint.getColor()),
                    Color.blue(arcPaint.getColor())));
            float labelTop = baseline + metrics.ascent - dp(4);
            float labelBottom = baseline + metrics.descent + dp(4);
            canvas.drawRoundRect(labelX - halfWidth, labelTop,
                    labelX + halfWidth, labelBottom,
                    dp(6), dp(6), labelBackgroundPaint);
            actionLabelBounds[mode][actionIndex].set(labelX - halfWidth, labelTop,
                    labelX + halfWidth, labelBottom);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(text, labelX, baseline, textPaint);
        }

        private void drawControls(Canvas canvas, float width, float height, float top) {
            float margin = dp(12);
            float controlTop = top + dp(12);
            float controlHeight = dp(40);
            float gap = dp(6);
            float rowWidth = (width - margin * 2f - gap) / 2f;
            leftControl.set(margin, controlTop, margin + rowWidth, controlTop + controlHeight);
            rightControl.set(margin + rowWidth + gap, controlTop, width - margin,
                    controlTop + controlHeight);
            drawControl(canvas, leftControl, "左侧", leftSide);
            drawControl(canvas, rightControl, "右侧", !leftSide);

            float buttonTop = height - navigationBarHeight() - dp(58);
            float buttonHeight = dp(44);
            exitControl.set(margin, buttonTop, margin + dp(96), buttonTop + buttonHeight);
            saveControl.set(width - margin - dp(96), buttonTop, width - margin,
                    buttonTop + buttonHeight);
            drawControl(canvas, exitControl, "退出", false);
            drawControl(canvas, saveControl, "保存", true);
            textPaint.setTextSize(11f * scaledDensity);
            textPaint.setColor(0xeeffffff);
            canvas.drawText("点区域选动作；拖动圆点调整距离、角度和竖向范围", width / 2f,
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
            float panelTop = top + dp(66);
            float rowHeight = dp(44);
            float rowStart = panelTop + dp(38);
            int visibleRows = pickerVisibleRows(getHeight(), rowStart, rowHeight);
            float panelBottom = rowStart + rowHeight * visibleRows;
            controlPaint.setColor(0xe62a313a);
            canvas.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom,
                    dp(14), dp(14), controlPaint);
            int index = (leftSide ? 0 : 3) + pickerDirection;
            boolean longDistance = splitEnabled && pickerLongDistance;
            int action = (longDistance ? longActions : shortActions)[index];
            textPaint.setTextSize(14f * scaledDensity);
            canvas.drawText(directionName(pickerDirection) + " · "
                    + (longDistance ? "长距离动作" : "短距离动作"),
                    (panelLeft + panelRight) / 2f, panelTop + dp(25), textPaint);
            canvas.save();
            canvas.clipRect(panelLeft, rowStart, panelRight, panelBottom);
            for (int row = 0; row < visibleRows; row++) {
                int i = pickerScrollIndex + row;
                if (i >= availableActionLabels.length) break;
                float rowTop = rowStart + row * rowHeight;
                if (availableActionValues[i] == action) {
                    selectedControlPaint.setColor(accentColor);
                    canvas.drawRoundRect(panelLeft + dp(8), rowTop + dp(2),
                            panelRight - dp(8), rowTop + rowHeight - dp(2),
                            dp(8), dp(8), selectedControlPaint);
                }
                String label = availableActionLabels[i];
                float maxLabelWidth = panelRight - panelLeft - dp(32);
                while (textPaint.measureText(label) > maxLabelWidth && label.length() > 8) {
                    label = label.substring(0, label.length() - 4) + "...";
                }
                canvas.drawText(label, (panelLeft + panelRight) / 2f,
                        rowTop + rowHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
                        textPaint);
            }
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            int action = event.getActionMasked();
            if (pickerGestureActive) {
                if (action == MotionEvent.ACTION_MOVE
                        && Math.abs(y - pickerTouchStartY) > ViewConfiguration.get(getContext())
                                .getScaledTouchSlop()) {
                    int rows = Math.max(1, Math.round((y - pickerTouchStartY) / dp(44)));
                    pickerScrollIndex = clamp(pickerScrollIndex - rows, 0,
                            Math.max(0, availableActionLabels.length
                                    - pickerVisibleRows(getHeight(), pickerRowStart(), dp(44))));
                    pickerTouchStartY = y;
                    pickerTouchMoved = true;
                    invalidate();
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (action == MotionEvent.ACTION_UP && !pickerTouchMoved) handlePickerTap(x, y);
                    pickerGestureActive = false;
                    if (action == MotionEvent.ACTION_UP) {
                        pickerDirection = -1;
                        pickerScrollIndex = 0;
                        invalidate();
                    }
                }
                return true;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                if (pickerDirection >= 0) {
                    pickerGestureActive = true;
                    pickerTouchStartY = y;
                    pickerTouchMoved = false;
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
                    sideControlTouchActive = true;
                    invalidate();
                    return true;
                }
                if (rightControl.contains(x, y)) {
                    leftSide = false;
                    sideControlTouchActive = true;
                    invalidate();
                    return true;
                }
                dragging = hitHandle(x, y);
                if (dragging == DRAG_SHORT) {
                    dragOffsetX = x - handleX(shortDistanceDp);
                    return true;
                }
                if (dragging == DRAG_LONG) {
                    dragOffsetX = x - handleX(longDistanceDp);
                    return true;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (dragging != DRAG_NONE) {
                    if (dragging == DRAG_SHORT || dragging == DRAG_LONG) {
                        float handleX = x - dragOffsetX;
                        float inward = Math.max(0f, (handleX - originX) * direction);
                        int distance = Math.round(inward / scale);
                        if (dragging == DRAG_LONG) {
                            longDistanceDp = clamp(distance,
                                    SettingsStore.MIN_ASSISTANT_GESTURE_LONG_DISTANCE_DP,
                                    SettingsStore.MAX_ASSISTANT_GESTURE_LONG_DISTANCE_DP);
                        } else {
                            shortDistanceDp = clamp(distance,
                                    SettingsStore.MIN_ASSISTANT_GESTURE_DISTANCE_DP,
                                    SettingsStore.MAX_ASSISTANT_GESTURE_DISTANCE_DP);
                        }
                    } else if (dragging == DRAG_UP_ANGLE || dragging == DRAG_DOWN_ANGLE) {
                        float inward = Math.max(1f, (x - originX) * direction);
                        float vertical = Math.abs(y - centerY);
                        int angle = Math.round((float) Math.toDegrees(Math.atan2(vertical, inward))
                                * 10f);
                        angle = clamp(angle, SettingsStore.MIN_ASSISTANT_GESTURE_ANGLE,
                                SettingsStore.MAX_ASSISTANT_GESTURE_ANGLE);
                        if (dragging == DRAG_UP_ANGLE) upAngle = angle;
                        else downAngle = angle;
                    } else if (dragging == DRAG_MIN_VERTICAL) {
                        minVerticalDp = clamp(Math.round(Math.abs(y - centerY) / scale),
                                SettingsStore.MIN_ASSISTANT_GESTURE_MIN_VERTICAL_DP,
                                SettingsStore.MAX_ASSISTANT_GESTURE_MIN_VERTICAL_DP);
                    }
                    invalidate();
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if (sideControlTouchActive) {
                    sideControlTouchActive = false;
                    activity.performTapHaptic(this);
                    return true;
                }
                if (dragging != DRAG_NONE) {
                    dragging = DRAG_NONE;
                    activity.performTapHaptic(this);
                    return true;
                }
                int actionLabel = hitActionLabel(x, y);
                if (actionLabel >= 0) {
                    int directionIndex = actionLabel % 3;
                    boolean longDistance = actionLabel >= 3;
                    int slot = (leftSide ? 0 : 3) + directionIndex;
                    int selectedAction = longDistance ? longActions[slot] : shortActions[slot];
                    if (SideGestureTargetStore.isDynamicAction(selectedAction)) {
                        SideGestureTargetStore.Target target = SideGestureTargetStore.find(
                                SideGestureTargetStore.readRaw(prefs), selectedAction);
                        if (target != null) {
                            activity.showSideGestureTargetDetails(target, () ->
                                    activity.configureSideGestureTarget(target.type, targetId -> {
                                        if (longDistance) longActions[slot] = targetId;
                                        else shortActions[slot] = targetId;
                                        loadAvailableActions();
                                        invalidate();
                                    }));
                            return true;
                        }
                    }
                    pickerDirection = actionLabel % 3;
                    pickerLongDistance = actionLabel >= 3;
                    pickerScrollIndex = 0;
                    invalidate();
                    return true;
                }
                float inward = (x - originX) * direction;
                if (inward > 0 && y > topInset() && y < getHeight() - navigationBarHeight()) {
                    float dy = y - centerY;
                    float distanceDp = (float) Math.hypot(inward, dy) / scale;
                    float angle = angleDegrees(dy < 0 ? upAngle : downAngle);
                    int directionIndex = Math.abs(dy) < Math.max(minVerticalDp * scale,
                            inward * (float) Math.tan(Math.toRadians(angle)))
                            ? 0 : dy < 0 ? 1 : 2;
                    if (distanceDp > 8f) {
                        pickerDirection = directionIndex;
                        pickerLongDistance = splitEnabled && distanceDp >= longDistanceDp;
                        pickerScrollIndex = 0;
                        invalidate();
                    }
                }
                return true;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                sideControlTouchActive = false;
                dragging = DRAG_NONE;
            }
            return true;
        }

        private boolean handlePickerTap(float x, float y) {
            float panelLeft = dp(22);
            float panelRight = getWidth() - dp(22);
            float rowHeight = dp(44);
            float rowStart = pickerRowStart();
            int visibleRows = pickerVisibleRows(getHeight(), rowStart, rowHeight);
            if (x < panelLeft || x > panelRight || y < rowStart
                    || y >= rowStart + rowHeight * visibleRows) return false;
            int actionIndex = pickerScrollIndex + (int) ((y - rowStart) / rowHeight);
            if (actionIndex >= availableActionValues.length) return false;
            int index = (leftSide ? 0 : 3) + pickerDirection;
            int selected = availableActionValues[actionIndex];
            if (selected == SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP
                    || selected == SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY
                    || selected == SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI
                    || selected == SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL) {
                int slot = index;
                activity.configureSideGestureTarget(selected, targetId -> {
                    if (splitEnabled && pickerLongDistance) longActions[slot] = targetId;
                    else shortActions[slot] = targetId;
                    loadAvailableActions();
                    pickerDirection = -1;
                    invalidate();
                });
            } else if (splitEnabled && pickerLongDistance) {
                longActions[index] = selected;
            } else {
                shortActions[index] = selected;
            }
            pickerDirection = -1;
            pickerScrollIndex = 0;
            invalidate();
            activity.performTapHaptic(this);
            return true;
        }

        private float pickerRowStart() {
            return topInset() + dp(66 + 38);
        }

        private int pickerVisibleRows(float height, float rowStart, float rowHeight) {
            int room = (int) ((height - navigationBarHeight() - dp(74) - rowStart) / rowHeight);
            return Math.max(3, Math.min(availableActionLabels.length, room));
        }

        private int hitHandle(float x, float y) {
            float hit = dp(28);
            float angleDistance = angleHandleDistanceDp();
            float angleX = screenX(angleDistance);
            float upY = centerY - angleDistance
                    * (float) Math.tan(Math.toRadians(angleDegrees(upAngle))) * scale;
            float downY = centerY + angleDistance
                    * (float) Math.tan(Math.toRadians(angleDegrees(downAngle))) * scale;
            if (Math.hypot(x - angleX, y - upY) <= hit) return DRAG_UP_ANGLE;
            if (Math.hypot(x - angleX, y - downY) <= hit) return DRAG_DOWN_ANGLE;
            float verticalDistance = Math.max(32f, Math.min(shortDistanceDp,
                    getWidth() / scale * 0.18f));
            float verticalX = screenX(verticalDistance);
            float minY = centerY - minVerticalDp * scale;
            float maxY = centerY + minVerticalDp * scale;
            if (Math.hypot(x - verticalX, y - minY) <= hit
                    || Math.hypot(x - verticalX, y - maxY) <= hit) return DRAG_MIN_VERTICAL;
            float shortX = handleX(shortDistanceDp);
            float shortY = centerY;
            float longX = handleX(longDistanceDp);
            float longY = centerY;
            boolean shortHit = Math.hypot(x - shortX, y - shortY) <= hit;
            boolean longHit = splitEnabled && Math.hypot(x - longX, y - longY) <= hit;
            if (!shortHit && !longHit) return DRAG_NONE;
            if (shortHit && longHit) {
                return Math.hypot(x - shortX, y - shortY)
                        <= Math.hypot(x - longX, y - longY) ? DRAG_SHORT : DRAG_LONG;
            }
            return shortHit ? DRAG_SHORT : DRAG_LONG;
        }

        private float handleX(int distanceDp) {
            return screenX(distanceDp);
        }

        private int hitActionLabel(float x, float y) {
            for (int mode = splitEnabled ? 1 : 0; mode >= 0; mode--) {
                for (int index = 0; index < 6; index++) {
                    if (actionLabelBounds[mode][index].contains(x, y)) {
                        return mode * 3 + index % 3;
                    }
                }
            }
            return -1;
        }

        private String actionLabel(int index, boolean longDistance) {
            int action = longDistance ? longActions[index] : shortActions[index];
            switch (action) {
                case SettingsStore.SIDE_GESTURE_ACTION_BACK: return "返回";
                case SettingsStore.SIDE_GESTURE_ACTION_HOME: return "主页";
                case SettingsStore.SIDE_GESTURE_ACTION_RECENTS: return "多任务";
                case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT: return "全局负一屏";
                case SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE: return "多应用工作台";
                case SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER: return "任务切换器";
                case SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS: return "快捷工具";
                case SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER: return "应用启动器";
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP: return "指定应用";
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY: return "指定 Activity";
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI: return "Intent URI";
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL: return "Scheme URL";
                default:
                    SideGestureTargetStore.Target target = SideGestureTargetStore.find(
                            SettingsStore.readString(prefs,
                                    SettingsStore.KEY_SIDE_GESTURE_TARGETS_JSON, "[]"), action);
                    if (target != null) {
                        switch (target.type) {
                            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP: return "打开应用";
                            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY: return "打开 Activity";
                            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI: return "打开 Intent";
                            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL:
                                return "打开 Scheme URL";
                        }
                    }
                    return "无动作";
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
