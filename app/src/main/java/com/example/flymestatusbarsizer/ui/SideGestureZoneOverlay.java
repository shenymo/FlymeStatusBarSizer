package com.example.flymestatusbarsizer.ui;

import static com.example.flymestatusbarsizer.config.SettingsStore.SIDE_GESTURE_FAR;
import static com.example.flymestatusbarsizer.config.SettingsStore.SIDE_GESTURE_INHERIT_LONG;
import static com.example.flymestatusbarsizer.config.SettingsStore.SIDE_GESTURE_LONG;
import static com.example.flymestatusbarsizer.config.SettingsStore.SIDE_GESTURE_SHORT;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.feature.assistant.AssistantGestureGeometry;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.SideGestureTargetStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Interactive trigger-zone editor drawn above the settings screen. */
public final class SideGestureZoneOverlay {
    private static final String[] TIER_NAMES = {"近距离", "中距离", "远距离"};
    private static final int[] ACTION_VALUES = {
            SettingsStore.SIDE_GESTURE_ACTION_NONE,
            SettingsStore.SIDE_GESTURE_ACTION_BACK,
            SettingsStore.SIDE_GESTURE_ACTION_HOME,
            SettingsStore.SIDE_GESTURE_ACTION_RECENTS,
            SettingsStore.SIDE_GESTURE_ACTION_LOCK_SCREEN,
            SettingsStore.SIDE_GESTURE_ACTION_END_CURRENT_TASK,
            SettingsStore.SIDE_GESTURE_ACTION_WINDOWMODE_PANEL,
            SettingsStore.SIDE_GESTURE_ACTION_WELT_WINDOW,
            SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER,
            SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT,
            SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE,
            SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS,
            SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL
    };
    private static final String[] ACTION_LABELS = {
            "无动作", "返回", "返回桌面", "最近任务", "锁屏", "结束当前任务（App）",
            "小窗选择面板", "贴边", "任务切换器", "全局负一屏", "多应用工作台",
            "快捷工具", "应用启动器", "打开指定应用", "打开 Activity", "打开 Intent URI", "打开 Scheme URL"
    };
    private static final String[] ACTION_CATEGORIES = {
            "基础控制", "多任务与桌面", "应用与快捷方式", "高级扩展"
    };
    private static final int[] ACTION_CATEGORY_STARTS = {0, 6, 11, 14};
    private static final int[] ACTION_CATEGORY_ENDS = {6, 11, 14, 17};
    private static final class ActionChoice {
        final int value;
        final String label;

        ActionChoice(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }
    private static final int DRAG_NONE = 0;
    private static final int DRAG_SHORT = 1;
    private static final int DRAG_LONG = 2;
    private static final int DRAG_UP_ANGLE = 3;
    private static final int DRAG_DOWN_ANGLE = 4;
    private static final int DRAG_MIN_VERTICAL = 5;
    private static final int DRAG_FAR = 6;

    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final int accentColor;
    private final boolean cornerMode;
    private final Runnable onActionChanged;
    private final Runnable onClosed;
    private final OverlayView view;
    private int[][] actions;
    private int shortDistanceDp;
    private int longDistanceDp;
    private int farDistanceDp;
    private int upAngle;
    private int downAngle;
    private int minVerticalDp;
    private boolean leftSide = true;
    private boolean splitEnabled;
    private boolean farEnabled;
    private boolean attached;

    public SideGestureZoneOverlay(MainActivity activity, SharedPreferences prefs, int accentColor,
            boolean cornerMode,
            Runnable onActionChanged, Runnable onClosed) {
        this.activity = activity;
        this.prefs = prefs;
        this.accentColor = accentColor;
        this.cornerMode = cornerMode;
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
        if (cornerMode) {
            actions = SettingsStore.readCornerGestureActions(prefs, false);
            shortDistanceDp = readBounded(SettingsStore.KEY_CORNER_GESTURE_DISTANCE_DP,
                    SettingsStore.DEFAULT_CORNER_GESTURE_DISTANCE_DP, 0, 240);
            longDistanceDp = readBounded(SettingsStore.KEY_CORNER_GESTURE_LONG_DISTANCE_DP,
                    SettingsStore.DEFAULT_CORNER_GESTURE_LONG_DISTANCE_DP, shortDistanceDp + 1, 480);
            farDistanceDp = readBounded(SettingsStore.KEY_CORNER_GESTURE_FAR_DISTANCE_DP,
                    SettingsStore.DEFAULT_CORNER_GESTURE_FAR_DISTANCE_DP, longDistanceDp + 1, 720);
            splitEnabled = SettingsStore.readBoolean(prefs,
                    SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false);
            farEnabled = splitEnabled && SettingsStore.readBoolean(prefs,
                    SettingsStore.KEY_CORNER_GESTURE_FAR_ENABLED, false);
            return;
        }
        actions = SettingsStore.readSideGestureActions(prefs, false);
        shortDistanceDp = readBounded(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_DP,
                SettingsStore.MIN_ASSISTANT_GESTURE_DISTANCE_DP,
                SettingsStore.MAX_ASSISTANT_GESTURE_DISTANCE_DP);
        longDistanceDp = readBounded(SettingsStore.KEY_ASSISTANT_GESTURE_LONG_DISTANCE_DP,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_LONG_DISTANCE_DP,
                SettingsStore.MIN_ASSISTANT_GESTURE_LONG_DISTANCE_DP,
                SettingsStore.MAX_ASSISTANT_GESTURE_LONG_DISTANCE_DP);
        splitEnabled = SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_SPLIT_ENABLED);
        farEnabled = splitEnabled && SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_ASSISTANT_GESTURE_FAR_ENABLED, false);
        if (farEnabled) {
            longDistanceDp = Math.max(shortDistanceDp + 1, longDistanceDp);
            farDistanceDp = SettingsStore.readSideGestureFarDistance(prefs, longDistanceDp);
        }
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

    private int readBounded(String key, int defaultValue, int min, int max) {
        int value = SettingsStore.readInt(prefs, key, defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    private void saveToPreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        if (cornerMode) {
            editor.putInt(SettingsStore.KEY_CORNER_GESTURE_DISTANCE_DP, shortDistanceDp)
                    .putInt(SettingsStore.KEY_CORNER_GESTURE_LONG_DISTANCE_DP, longDistanceDp);
        } else {
            editor.putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, shortDistanceDp)
                    .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_LONG_DISTANCE_DP, longDistanceDp)
                    .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_UP_ANGLE, upAngle)
                    .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DOWN_ANGLE, downAngle)
                    .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_MIN_VERTICAL_DP, minVerticalDp);
        }
        if (farEnabled) {
            editor.putInt(cornerMode ? SettingsStore.KEY_CORNER_GESTURE_FAR_DISTANCE_DP
                    : SettingsStore.KEY_ASSISTANT_GESTURE_FAR_DISTANCE_DP, farDistanceDp);
        }
        for (int tier = 0; tier < actions.length; tier++) {
            for (int slot = 0; slot < actions[tier].length; slot++) {
                String key = cornerMode ? SettingsStore.cornerGestureActionKey(tier, slot)
                        : SettingsStore.sideGestureActionKey(tier, slot);
                int action = actions[tier][slot];
                if (action == SIDE_GESTURE_INHERIT_LONG) editor.remove(key);
                else editor.putInt(key, action);
            }
        }
        List<SideGestureTargetStore.Target> storedTargets = SideGestureTargetStore.parse(
                SideGestureTargetStore.readRaw(prefs));
        List<SideGestureTargetStore.Target> assignedTargets = new ArrayList<>();
        int[][] otherActions = cornerMode ? SettingsStore.readSideGestureActions(prefs, false)
                : SettingsStore.readCornerGestureActions(prefs, false);
        for (SideGestureTargetStore.Target target : storedTargets) {
            boolean assigned = false;
            for (int[] tierActions : actions) {
                for (int action : tierActions) assigned |= action == target.id;
            }
            for (int[] tierActions : otherActions) {
                for (int action : tierActions) assigned |= action == target.id;
            }
            if (assigned) assignedTargets.add(target);
        }
        if (assignedTargets.size() != storedTargets.size()) {
            editor.putString(SettingsStore.KEY_SIDE_GESTURE_TARGETS_JSON,
                    SideGestureTargetStore.encode(assignedTargets));
        }
        editor.apply();
        SettingsStore.notifyChanged(activity);
        if (onActionChanged != null) onActionChanged.run();
    }

    private final class OverlayView extends View {
        private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longThresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint farThresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint farRegionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shortRegionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longRegionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint farHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint controlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedControlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF leftControl = new RectF();
        private final RectF rightControl = new RectF();
        private final RectF exitControl = new RectF();
        private final RectF saveControl = new RectF();
        private final RectF[][] actionLabelBounds = new RectF[3][6];
        private final RectF[] tierControls = {new RectF(), new RectF(), new RectF()};
        private float density;
        private float scaledDensity;
        private float centerY;
        private float edge;
        private float direction;
        private float originX;
        private float scale;
        private int dragging;
        private boolean sideControlTouchActive;
        private RectF pressedButtonControl;
        private float dragOffsetX;
        private float dragOffsetY;
        private final int overlaySurfaceColor;
        private final int overlayTextColor;
        private final int overlayMutedColor;

        OverlayView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            overlaySurfaceColor = activity.surfaceColor();
            overlayTextColor = activity.textColor();
            overlayMutedColor = activity.subtextColor();
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
            farThresholdPaint.set(longThresholdPaint);
            farThresholdPaint.setColor(0xff6de1ad);
            farRegionPaint.setColor(0x2961c69b);
            shortRegionPaint.setColor(0x294b8dff);
            longRegionPaint.setColor(0x29ffad5a);
            handlePaint.setColor(0xff69a9ff);
            longHandlePaint.setColor(0xffffb45f);
            longHandlePaint.setAlpha(220);
            farHandlePaint.setColor(0xff6de1ad);
            farHandlePaint.setAlpha(220);
            controlPaint.setColor(overlaySurfaceColor);
            selectedControlPaint.setColor(accentColor);
            textPaint.setColor(overlayTextColor);
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
            if (cornerMode) {
                centerY = bottom - dp(108);
                int maxDistance = farEnabled ? 720 : splitEnabled ? 480 : 240;
                scale = Math.min(density, Math.min((width - dp(28)) / maxDistance,
                        (centerY - top - dp(118)) / maxDistance));
            }

            int background = activity.backgroundColor();
            canvas.drawColor(Color.argb(0xe6, Color.red(background),
                    Color.green(background), Color.blue(background)));
            int save = canvas.save();
            canvas.clipRect(0, top, width, bottom);
            drawZones(canvas);
            canvas.restoreToCount(save);
            drawControls(canvas, width, height, top);
        }

        private void drawZones(Canvas canvas) {
            for (int mode = 0; mode < actionLabelBounds.length; mode++) {
                for (int action = 0; action < actionLabelBounds[mode].length; action++) {
                    actionLabelBounds[mode][action].setEmpty();
                }
            }
            if (cornerMode) {
                drawCornerZones(canvas);
                return;
            }
            AssistantGestureGeometry geometry = new AssistantGestureGeometry(shortDistanceDp,
                    angleDegrees(upAngle), angleDegrees(downAngle), minVerticalDp);
            int[] distances = {shortDistanceDp, longDistanceDp, farDistanceDp};
            Paint[] fills = {shortRegionPaint, longRegionPaint, farRegionPaint};
            Paint[] strokes = {thresholdPaint, longThresholdPaint, farThresholdPaint};
            int count = farEnabled ? 3 : splitEnabled ? 2 : 1;
            Matrix transform = new Matrix();
            transform.setScale(direction * scale, scale);
            transform.postTranslate(originX, centerY);
            for (int tier = 0; tier < count; tier++) {
                float outer = tier + 1 < count ? distances[tier + 1] : Float.POSITIVE_INFINITY;
                for (int zone = 0; zone < 3; zone++) {
                    Path path = geometry.region(zone, distances[tier], outer, getWidth() / scale,
                            (centerY - statusBarHeight()) / scale,
                            (getHeight() - navigationBarHeight() - centerY) / scale);
                    path.transform(transform);
                    canvas.drawPath(path, fills[tier]);
                    canvas.drawPath(path, strokes[tier]);
                }
            }
            drawThreshold(canvas, shortDistanceDp, thresholdPaint, handlePaint);
            int offset = leftSide ? 0 : 3;
            if (!splitEnabled || longDistanceDp > shortDistanceDp) {
                drawActionLabels(canvas, shortDistanceDp, thresholdPaint, offset, SIDE_GESTURE_SHORT);
            }
            if (splitEnabled) {
                drawThreshold(canvas, longDistanceDp, longThresholdPaint, longHandlePaint);
                drawActionLabels(canvas, Math.max(shortDistanceDp, longDistanceDp),
                        longThresholdPaint, offset, SIDE_GESTURE_LONG);
            }
            if (farEnabled) {
                drawThreshold(canvas, farDistanceDp, farThresholdPaint, farHandlePaint);
                drawActionLabels(canvas, farDistanceDp, farThresholdPaint, offset, SIDE_GESTURE_FAR);
            }
            canvas.drawCircle(originX, centerY, dp(5), handlePaint);
            drawGeometryHandles(canvas);
            textPaint.setTextSize(11f * scaledDensity);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(leftSide ? Paint.Align.LEFT : Paint.Align.RIGHT);
            canvas.drawText("起点（实际跟随落指位置）", originX + direction * dp(12),
                    centerY - dp(10), textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        private void drawCornerZones(Canvas canvas) {
            int slot = leftSide ? 0 : 1;
            int count = farEnabled ? 3 : splitEnabled ? 2 : 1;
            int[] distances = {shortDistanceDp, longDistanceDp, farDistanceDp};
            Paint[] fills = {shortRegionPaint, longRegionPaint, farRegionPaint};
            Paint[] strokes = {thresholdPaint, longThresholdPaint, farThresholdPaint};
            Paint[] handles = {handlePaint, longHandlePaint, farHandlePaint};
            float outerLimit = (float) Math.hypot(getWidth(), centerY - statusBarHeight()) / scale;
            float startAngle = leftSide ? 270f : 180f;
            for (int tier = 0; tier < count; tier++) {
                float inner = distances[tier] * scale;
                float outer = (tier + 1 < count ? distances[tier + 1] : outerLimit) * scale;
                Path region = new Path();
                region.arcTo(originX - outer, centerY - outer, originX + outer,
                        centerY + outer, startAngle, 90f, true);
                if (inner > 0f) {
                    region.arcTo(originX - inner, centerY - inner, originX + inner,
                            centerY + inner, startAngle + 90f, -90f, false);
                } else {
                    region.lineTo(originX, centerY);
                }
                region.close();
                canvas.drawPath(region, fills[tier]);
                drawThreshold(canvas, distances[tier], strokes[tier], handles[tier]);
                float labelRadius = tier + 1 < count
                        ? (distances[tier] + distances[tier + 1]) * 0.5f * scale
                        : inner + dp(48);
                drawArcActionLabel(canvas, (splitEnabled ? TIER_NAMES[tier] + " · " : "")
                                + actionLabel(slot, tier),
                        originX + direction * labelRadius * 0.995f,
                        centerY - labelRadius * 0.1f, 1, strokes[tier], tier, slot);
            }
            canvas.drawCircle(originX, centerY, dp(5), handlePaint);
            textPaint.setTextSize(11f * scaledDensity);
            textPaint.setTextAlign(leftSide ? Paint.Align.LEFT : Paint.Align.RIGHT);
            canvas.drawText("底角起点（示意）", originX + direction * dp(12),
                    centerY + dp(22), textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        private float directionBandY(float x, boolean upper) {
            float angle = (float) Math.tan(Math.toRadians(angleDegrees(upper ? upAngle : downAngle)));
            return Math.max(minVerticalDp, x * angle);
        }

        private float screenX(float distanceDp) {
            return originX + direction * distanceDp * scale;
        }

        private void drawThreshold(Canvas canvas, int distanceDp, Paint paint, Paint handle) {
            if (cornerMode) {
                float radius = distanceDp * scale;
                canvas.drawArc(originX - radius, centerY - radius, originX + radius,
                        centerY + radius, leftSide ? 270f : 180f, 90f, false, paint);
            }
            float x = handleX(distanceDp);
            float y = cornerMode ? centerY - distanceDp * scale * 0.7071f : centerY;
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

        private void drawActionLabels(Canvas canvas, int distanceDp, Paint arcPaint,
                int actionOffset, int tier) {
            float horizontalX = Math.min(getWidth() / scale,
                    distanceDp + (tier == SIDE_GESTURE_SHORT ? 46f : 30f));
            float diagonalX = Math.min(getWidth() / scale,
                    Math.max(distanceDp * 0.72f, distanceDp * 0.35f + 30f));
            float upDiagonalY = Math.max(directionBandY(diagonalX, true) + dp(24) / scale,
                    (float) Math.sqrt(Math.max(0f, distanceDp * distanceDp
                            - diagonalX * diagonalX)) + dp(24) / scale);
            float downDiagonalY = Math.max(directionBandY(diagonalX, false) + dp(24) / scale,
                    (float) Math.sqrt(Math.max(0f, distanceDp * distanceDp
                            - diagonalX * diagonalX)) + dp(24) / scale);
            String prefix = splitEnabled ? TIER_NAMES[tier].substring(0, 1) + " " : "";
            float labelOffset = farEnabled ? dp(28) * (tier - 1) : 0f;
            drawArcActionLabel(canvas, prefix + actionLabel(actionOffset, tier),
                    screenX(horizontalX), centerY + labelOffset, 1, arcPaint, tier, actionOffset);
            drawArcActionLabel(canvas, prefix + actionLabel(actionOffset + 1, tier),
                    screenX(diagonalX), centerY - upDiagonalY * scale - labelOffset,
                    0, arcPaint, tier, actionOffset + 1);
            drawArcActionLabel(canvas, prefix + actionLabel(actionOffset + 2, tier),
                    screenX(diagonalX), centerY + downDiagonalY * scale + labelOffset,
                    2, arcPaint, tier, actionOffset + 2);
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
            drawControl(canvas, leftControl, cornerMode ? "左底角" : "左侧", leftSide);
            drawControl(canvas, rightControl, cornerMode ? "右底角" : "右侧", !leftSide);

            float tierTop = controlTop + controlHeight + gap;
            int count = farEnabled ? 3 : splitEnabled ? 2 : 1;
            int[] distances = {shortDistanceDp, longDistanceDp, farDistanceDp};
            float tierWidth = (width - margin * 2f - gap * (count - 1)) / count;
            for (int tier = 0; tier < tierControls.length; tier++) {
                RectF rect = tierControls[tier];
                if (tier >= count) {
                    rect.setEmpty();
                    continue;
                }
                float left = margin + tier * (tierWidth + gap);
                rect.set(left, tierTop, left + tierWidth, tierTop + dp(48));
                controlPaint.setColor(overlaySurfaceColor);
                canvas.drawRoundRect(rect, dp(10), dp(10), controlPaint);
                textPaint.setColor(overlayTextColor);
                textPaint.setTextSize(14f * scaledDensity);
                canvas.drawText(splitEnabled ? TIER_NAMES[tier] : "触发设置",
                        rect.centerX(), rect.centerY() - dp(3), textPaint);
                textPaint.setTextSize(11f * scaledDensity);
                canvas.drawText(distances[tier] + " dp", rect.centerX(),
                        rect.centerY() + dp(14), textPaint);
            }
            float buttonTop = height - navigationBarHeight() - dp(58);
            float buttonHeight = dp(44);
            float buttonWidth = (width - margin * 2f - gap) / 2f;
            exitControl.set(margin, buttonTop, margin + buttonWidth, buttonTop + buttonHeight);
            saveControl.set(width - margin - buttonWidth, buttonTop, width - margin,
                    buttonTop + buttonHeight);
            drawControl(canvas, exitControl, "退出", false);
            drawControl(canvas, saveControl, "保存", true);
            textPaint.setTextSize(11f * scaledDensity);
            textPaint.setColor(overlayMutedColor);
            canvas.drawText(cornerMode ? "拖动圆点调整距离 · 点区域设置动作"
                            : "拖动圆点调整范围 · 点区域设置动作", width / 2f,
                    buttonTop - dp(10), textPaint);
            textPaint.setColor(overlayTextColor);
        }

        private void drawControl(Canvas canvas, RectF rect, String text, boolean selected) {
            controlPaint.setColor(selected ? accentColor : overlaySurfaceColor);
            canvas.drawRoundRect(rect, dp(10), dp(10), controlPaint);
            textPaint.setColor(selected ? Color.WHITE : overlayTextColor);
            textPaint.setTextSize(14f * scaledDensity);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, rect.centerX(), baseline, textPaint);
            textPaint.setColor(overlayTextColor);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (exitControl.contains(x, y)) {
                    pressedButtonControl = exitControl;
                    return true;
                }
                if (saveControl.contains(x, y)) {
                    pressedButtonControl = saveControl;
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
                for (int tier = 0; tier < tierControls.length; tier++) {
                    if (tierControls[tier].contains(x, y)) return true;
                }
                dragging = hitHandle(x, y);
                if (cornerMode && dragging != DRAG_NONE) {
                    int distance = dragging == DRAG_SHORT ? shortDistanceDp
                            : dragging == DRAG_LONG ? longDistanceDp : farDistanceDp;
                    dragOffsetX = x - handleX(distance);
                    dragOffsetY = y - (centerY - distance * scale * 0.7071f);
                    return true;
                }
                if (dragging == DRAG_SHORT) {
                    dragOffsetX = x - handleX(shortDistanceDp);
                    return true;
                }
                if (dragging == DRAG_LONG) {
                    dragOffsetX = x - handleX(longDistanceDp);
                    return true;
                }
                if (dragging == DRAG_FAR) {
                    dragOffsetX = x - handleX(farDistanceDp);
                    return true;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (dragging != DRAG_NONE) {
                    if (dragging == DRAG_SHORT || dragging == DRAG_LONG || dragging == DRAG_FAR) {
                        float handleX = x - dragOffsetX;
                        float inward = Math.max(0f, (handleX - originX) * direction);
                        int distance = Math.round(inward / scale);
                        if (cornerMode) {
                            float upward = Math.max(0f, centerY - (y - dragOffsetY));
                            distance = Math.round((float) Math.hypot(inward, upward) / scale);
                        }
                        int tier = dragging == DRAG_SHORT ? SIDE_GESTURE_SHORT
                                : dragging == DRAG_LONG ? SIDE_GESTURE_LONG : SIDE_GESTURE_FAR;
                        int[] bounds = distanceBounds(tier);
                        distance = clamp(distance, bounds[0], bounds[1]);
                        if (tier == SIDE_GESTURE_SHORT) shortDistanceDp = distance;
                        else if (tier == SIDE_GESTURE_LONG) longDistanceDp = distance;
                        else farDistanceDp = distance;
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
                if (pressedButtonControl != null) {
                    RectF control = pressedButtonControl;
                    pressedButtonControl = null;
                    if (control.contains(x, y)) {
                        activity.performTapHaptic(this);
                        close(control == saveControl);
                    }
                    return true;
                }
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
                for (int tier = 0; tier < tierControls.length; tier++) {
                    if (tierControls[tier].contains(x, y)) {
                        activity.performTapHaptic(this);
                        showTierSettings(tier);
                        return true;
                    }
                }
                int actionLabel = hitActionLabel(x, y);
                if (actionLabel >= 0) {
                    openActionForSlot(actionLabel % 3, actionLabel / 3);
                    return true;
                }
                float inward = (x - originX) * direction;
                if (cornerMode) {
                    float distance = (float) Math.hypot(inward, y - centerY) / scale;
                    if (inward > 0f && y < centerY && y > topInset()
                            && distance >= shortDistanceDp) {
                        openActionForSlot(0, SettingsStore.sideGestureTier(distance,
                                splitEnabled ? longDistanceDp : Float.POSITIVE_INFINITY,
                                farEnabled ? farDistanceDp : Float.POSITIVE_INFINITY));
                    }
                    return true;
                }
                if (inward > 0 && y > topInset() && y < getHeight() - navigationBarHeight()) {
                    float dy = y - centerY;
                    float distanceDp = (float) Math.hypot(inward, dy) / scale;
                    AssistantGestureGeometry geometry = new AssistantGestureGeometry(shortDistanceDp,
                            angleDegrees(upAngle), angleDegrees(downAngle), minVerticalDp);
                    int directionIndex = geometry.direction(inward / scale, dy / scale);
                    if (geometry.ready(inward / scale, dy / scale)) {
                        openActionForSlot(directionIndex, SettingsStore.sideGestureTier(distanceDp,
                                splitEnabled ? longDistanceDp : Float.POSITIVE_INFINITY,
                                farEnabled ? farDistanceDp : Float.POSITIVE_INFINITY));
                    }
                }
                return true;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                pressedButtonControl = null;
                sideControlTouchActive = false;
                dragging = DRAG_NONE;
            }
            return true;
        }

        private int[] distanceBounds(int tier) {
            if (tier == SIDE_GESTURE_SHORT) {
                int max = SettingsStore.MAX_ASSISTANT_GESTURE_DISTANCE_DP;
                if ((cornerMode && splitEnabled) || farEnabled) max = Math.min(max, longDistanceDp - 1);
                return new int[]{SettingsStore.MIN_ASSISTANT_GESTURE_DISTANCE_DP, max};
            }
            if (tier == SIDE_GESTURE_LONG) {
                int min = cornerMode || farEnabled ? shortDistanceDp + 1
                        : SettingsStore.MIN_ASSISTANT_GESTURE_LONG_DISTANCE_DP;
                int max = SettingsStore.MAX_ASSISTANT_GESTURE_LONG_DISTANCE_DP;
                if (farEnabled) max = Math.min(max, farDistanceDp - 1);
                return new int[]{min, max};
            }
            return new int[]{longDistanceDp + 1, SettingsStore.MAX_ASSISTANT_GESTURE_FAR_DISTANCE_DP};
        }

        private void showDistanceInput(int tier) {
            int[] distances = {shortDistanceDp, longDistanceDp, farDistanceDp};
            int[] bounds = distanceBounds(tier);
            activity.showIntInputDialog(tier == SIDE_GESTURE_SHORT ? "最短触发距离"
                            : TIER_NAMES[tier] + "阈值", distances[tier],
                    bounds[0], bounds[1], "dp", value -> {
                        if (tier == SIDE_GESTURE_SHORT) shortDistanceDp = value;
                        else if (tier == SIDE_GESTURE_LONG) longDistanceDp = value;
                        else farDistanceDp = value;
                        invalidate();
                    });
        }

        private void showTierSettings(int tier) {
            int[] distances = {shortDistanceDp, longDistanceDp, farDistanceDp};
            int offset = cornerMode ? (leftSide ? 0 : 1) : (leftSide ? 0 : 3);
            String[] items = new String[cornerMode ? 2 : 4];
            items[0] = (tier == SIDE_GESTURE_SHORT ? "最短触发距离" : "分档切换阈值")
                    + " · " + distances[tier] + " dp";
            for (int i = 1; i < items.length; i++) {
                items[i] = (cornerMode ? "滑动动作" : directionName(i - 1))
                        + " · " + actionLabel(offset + i - 1, tier);
            }
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle((cornerMode ? (leftSide ? "左底角" : "右底角")
                                    : (leftSide ? "左侧" : "右侧"))
                            + " · " + (splitEnabled ? TIER_NAMES[tier] : "触发设置"))
                    .setItems(items, (d, which) -> {
                        if (which == 0) showDistanceInput(tier);
                        else openActionForSlot(which - 1, tier);
                    })
                    .setNegativeButton("完成", null)
                    .show();
            activity.attachDialogButtonHaptics(dialog);
        }

        private void openActionForSlot(int directionIndex, int tier) {
            int slot = cornerMode ? (leftSide ? 0 : 1)
                    : (leftSide ? 0 : 3) + directionIndex;
            int action = actions[tier][slot];
            if (SideGestureTargetStore.isDynamicAction(action)) {
                SideGestureTargetStore.Target target = SideGestureTargetStore.find(
                        SideGestureTargetStore.readRaw(prefs), action);
                if (target != null) {
                    showCustomTargetEditor(directionIndex, tier, slot, action, target);
                    return;
                }
            }
            showActionPicker(directionIndex, tier);
        }

        private void showActionPicker(int directionIndex, int tier) {
            showActionPicker(directionIndex, tier, null);
        }

        private void showActionPicker(int directionIndex, int tier, IntConsumer onSelected) {
            int slot = cornerMode ? (leftSide ? 0 : 1)
                    : (leftSide ? 0 : 3) + directionIndex;
            int currentAction = actions[tier][slot];
            boolean inheritAvailable = tier == SIDE_GESTURE_FAR
                    || (cornerMode && tier > SIDE_GESTURE_SHORT);
            SideGestureTargetStore.Target currentTarget = SideGestureTargetStore.isDynamicAction(currentAction)
                    ? SideGestureTargetStore.find(SideGestureTargetStore.readRaw(prefs), currentAction)
                    : null;
            int initialCategory = 0;
            if (currentTarget != null) {
                initialCategory = currentTarget.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP ? 2 : 3;
            } else if (currentAction != SIDE_GESTURE_INHERIT_LONG) {
                for (int category = 0; category < ACTION_CATEGORIES.length; category++) {
                    for (int i = ACTION_CATEGORY_STARTS[category]; i < ACTION_CATEGORY_ENDS[category]; i++) {
                        if (ACTION_VALUES[i] == currentAction) initialCategory = category;
                    }
                }
            }
            List<ActionChoice> choices = new ArrayList<>();
            final int[] categoryState = {initialCategory};
            ListView list = new ListView(activity);
            list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            list.setDivider(null);
            BaseAdapter adapter = new BaseAdapter() {
                @Override public int getCount() { return choices.size(); }
                @Override public Object getItem(int position) { return choices.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    ActionChoice choice = choices.get(position);
                    CheckedTextView row = convertView instanceof CheckedTextView
                            ? (CheckedTextView) convertView
                            : (CheckedTextView) activity.getLayoutInflater().inflate(
                                    android.R.layout.simple_list_item_single_choice, parent, false);
                    row.setText(choice.value == currentAction
                            ? choice.label + "  ·  当前" : choice.label);
                    row.setTextColor(choice.value == currentAction
                            ? activity.primaryColor() : activity.textColor());
                    row.setMinHeight(Math.round(dp(48)));
                    row.setPadding(Math.round(dp(14)), 0, Math.round(dp(14)), 0);
                    row.setBackground(activity.roundRect(choice.value == currentAction
                            ? activity.surfaceSoftColor() : Color.TRANSPARENT, 8));
                    return row;
                }
            };
            list.setAdapter(adapter);

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(Math.round(dp(8)), 0, Math.round(dp(8)), Math.round(dp(6)));

            View inheritContainer = null;
            if (inheritAvailable) {
                inheritContainer = activity.getLayoutInflater().inflate(
                        android.R.layout.simple_list_item_single_choice, content, false);
                CheckedTextView inheritRow = (CheckedTextView) inheritContainer
                        .findViewById(android.R.id.text1);
                inheritRow.setText(tier == SIDE_GESTURE_FAR
                        ? "沿用中距离动作" : "沿用近距离动作");
                inheritRow.setChecked(currentAction == SIDE_GESTURE_INHERIT_LONG);
                inheritRow.setTextColor(currentAction == SIDE_GESTURE_INHERIT_LONG
                        ? activity.primaryColor() : activity.textColor());
                inheritRow.setMinHeight(Math.round(dp(48)));
                inheritRow.setPadding(Math.round(dp(14)), 0, Math.round(dp(14)), 0);
                inheritRow.setBackground(activity.roundRect(currentAction == SIDE_GESTURE_INHERIT_LONG
                        ? activity.surfaceSoftColor() : Color.TRANSPARENT, 8));
                content.addView(inheritContainer, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.round(dp(48))));
            }

            HorizontalScrollView tabScroll = new HorizontalScrollView(activity);
            tabScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout tabs = new LinearLayout(activity);
            tabs.setOrientation(LinearLayout.HORIZONTAL);
            tabScroll.addView(tabs, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            List<TextView> tabLabels = new ArrayList<>();
            List<View> tabViews = new ArrayList<>();
            for (int category = 0; category < ACTION_CATEGORIES.length; category++) {
                TextView label = new TextView(activity);
                label.setText(ACTION_CATEGORIES[category]);
                label.setTextSize(13f);
                label.setGravity(Gravity.CENTER);
                label.setPadding(Math.round(dp(14)), 0, Math.round(dp(14)), 0);
                label.setMinHeight(Math.round(dp(36)));
                LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(dp(36)));
                tabParams.rightMargin = Math.round(dp(8));
                tabs.addView(label, tabParams);
                tabLabels.add(label);
                tabViews.add(label);
            }
            tabs.setPadding(Math.round(dp(4)), Math.round(dp(4)), Math.round(dp(4)),
                    Math.round(dp(4)));
            content.addView(tabScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(dp(44))));

            int visibleRows = 5;
            content.addView(list, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(dp(48 * visibleRows))));
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle(directionName(directionIndex) + " · " + TIER_NAMES[tier] + "动作")
                    .setView(content)
                    .setNegativeButton("取消", null);
            if (SideGestureTargetStore.isDynamicAction(currentAction)) {
                if (currentTarget != null) {
                    builder.setNeutralButton("查看当前目标", (d, which) ->
                            activity.showSideGestureTargetDetails(currentTarget, () ->
                                    activity.configureSideGestureTarget(currentTarget.type, targetId -> {
                                        actions[tier][slot] = targetId;
                                        invalidate();
                                    }), targetId -> {
                                actions[tier][slot] = targetId;
                                invalidate();
                            }));
                }
            }
            AlertDialog dialog = builder.create();
            list.setOnItemClickListener((parent, row, position, id) -> {
                ActionChoice choice = choices.get(position);
                int selected = choice.value;
                dialog.dismiss();
                if (SideGestureTargetStore.isTargetType(selected)) {
                    activity.configureSideGestureTarget(selected, targetId -> {
                        if (onSelected != null) {
                            onSelected.accept(targetId);
                        } else {
                            actions[tier][slot] = targetId;
                            invalidate();
                        }
                    });
                } else {
                    if (onSelected != null) {
                        onSelected.accept(selected);
                    } else {
                        actions[tier][slot] = selected;
                        invalidate();
                    }
                }
                activity.performTapHaptic(this);
            });
            if (inheritContainer != null) {
                inheritContainer.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onSelected != null) {
                        onSelected.accept(SIDE_GESTURE_INHERIT_LONG);
                    } else {
                        actions[tier][slot] = SIDE_GESTURE_INHERIT_LONG;
                        invalidate();
                    }
                    activity.performTapHaptic(this);
                });
            }
            final Runnable[] refreshCategory = new Runnable[1];
            refreshCategory[0] = () -> {
                int category = categoryState[0];
                choices.clear();
                for (int i = ACTION_CATEGORY_STARTS[category]; i < ACTION_CATEGORY_ENDS[category]; i++) {
                    choices.add(new ActionChoice(ACTION_VALUES[i], ACTION_LABELS[i]));
                }
                adapter.notifyDataSetChanged();
                list.clearChoices();
                for (int i = 0; i < choices.size(); i++) {
                    if (choices.get(i).value == currentAction) list.setItemChecked(i, true);
                }
                for (int i = 0; i < tabLabels.size(); i++) {
                    boolean selected = i == category;
                    tabLabels.get(i).setTextColor(selected
                            ? Color.WHITE : activity.subtextColor());
                    tabLabels.get(i).setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    tabLabels.get(i).setBackground(activity.roundRect(selected
                            ? activity.primaryColor() : activity.surfaceColor(), 18));
                }
                tabScroll.post(() -> tabScroll.smoothScrollTo(
                        Math.max(0, tabViews.get(category).getLeft() - Math.round(dp(12))), 0));
            };
            for (int category = 0; category < tabViews.size(); category++) {
                final int categoryIndex = category;
                tabViews.get(category).setOnClickListener(v -> {
                    categoryState[0] = categoryIndex;
                    refreshCategory[0].run();
                });
            }
            refreshCategory[0].run();
            dialog.show();
            styleBottomDialog(dialog);
            list.post(() -> {
                for (int i = 0; i < choices.size(); i++) {
                    if (choices.get(i).value == currentAction) list.setSelection(i);
                }
            });
            activity.attachDialogButtonHaptics(dialog);
        }

        private void showCustomTargetEditor(int directionIndex, int tier, int slot,
                int action, SideGestureTargetStore.Target target) {
            final SideGestureTargetStore.Target[] draftTarget = {target};
            final int[] draftAction = {action};
            LinearLayout form = new LinearLayout(activity);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(Math.round(dp(16)), Math.round(dp(4)),
                    Math.round(dp(16)), Math.round(dp(8)));

            TextView summary = new TextView(activity);
            summary.setText(targetSummary(target));
            summary.setTextColor(activity.subtextColor());
            summary.setTextSize(13f);
            summary.setPadding(Math.round(dp(12)), Math.round(dp(10)),
                    Math.round(dp(12)), Math.round(dp(10)));
            summary.setBackground(activity.roundRect(activity.surfaceSoftColor(), 8));
            form.addView(summary, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView nameLabel = new TextView(activity);
            nameLabel.setText("动作名称");
            nameLabel.setTextColor(activity.subtextColor());
            nameLabel.setTextSize(13f);
            nameLabel.setPadding(Math.round(dp(2)), Math.round(dp(12)),
                    Math.round(dp(2)), Math.round(dp(4)));
            form.addView(nameLabel);
            EditText nameInput = editorInput("动作名称（可选）", target.customName, false);
            form.addView(nameInput);
            EditText valueInput = null;
            EditText dataInput = null;
            EditText extrasInput = null;
            EditText customActionInput = null;
            RadioGroup actionGroup = null;
            CheckBox newTaskInput = null;
            CheckBox rootInput = null;
            final EditText[] editorData = {null};
            final EditText[] editorExtras = {null};
            final EditText[] editorCustomAction = {null};
            final CheckBox[] editorNewTask = {null};
            final CheckBox[] editorRoot = {null};
            final RadioButton[] editorViewAction = {null};
            final RadioButton[] editorMainAction = {null};
            final RadioButton[] editorCustomActionChoice = {null};

            if (target.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP) {
                TextView replace = activity.filledButton("更换应用",
                        activity.surfaceStrongColor(), activity.textColor());
                form.addView(replace, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.round(dp(44))));
                replace.setOnClickListener(v -> activity.configureSideGestureTarget(
                        SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP, id -> {
                            SideGestureTargetStore.Target replacement = SideGestureTargetStore.find(
                                    SideGestureTargetStore.readRaw(prefs), id);
                            if (replacement == null) return;
                            draftTarget[0] = replacement;
                            draftAction[0] = replacement.id;
                            nameInput.setText(replacement.customName);
                            summary.setText(targetSummary(replacement));
                        }));
            } else if (target.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY) {
                TextView replace = activity.filledButton("更换 Activity",
                        activity.surfaceStrongColor(), activity.textColor());
                form.addView(replace, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.round(dp(44))));
                replace.setOnClickListener(v -> activity.configureSideGestureTarget(
                        SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY, id -> {
                            SideGestureTargetStore.Target replacement = SideGestureTargetStore.find(
                                    SideGestureTargetStore.readRaw(prefs), id);
                            if (replacement == null) return;
                            draftTarget[0] = replacement;
                            draftAction[0] = replacement.id;
                            nameInput.setText(replacement.customName);
                            editorData[0].setText(replacement.dataUri);
                            editorExtras[0].setText(replacement.extras);
                            editorCustomAction[0].setText(replacement.action);
                            editorNewTask[0].setChecked(replacement.newTask);
                            editorRoot[0].setChecked(replacement.root);
                            if ("android.intent.action.MAIN".equals(replacement.action)) {
                                editorMainAction[0].setChecked(true);
                            } else if ("android.intent.action.VIEW".equals(replacement.action)
                                    || replacement.action.isEmpty()) {
                                editorViewAction[0].setChecked(true);
                            } else {
                                editorCustomActionChoice[0].setChecked(true);
                            }
                            summary.setText(targetSummary(replacement));
                        }));
                dataInput = editorInput("附加 Data（Uri，可选）", target.dataUri, false);
                extrasInput = editorInput("附加 Extra（每行 key=value，可选）", target.extras, true);
                editorData[0] = dataInput;
                editorExtras[0] = extrasInput;
                form.addView(dataInput);
                form.addView(extrasInput);
                TextView actionTitle = new TextView(activity);
                actionTitle.setText("启动 Action");
                actionTitle.setTextColor(activity.subtextColor());
                actionTitle.setTextSize(13f);
                actionTitle.setPadding(0, Math.round(dp(6)), 0, 0);
                form.addView(actionTitle);
                actionGroup = new RadioGroup(activity);
                actionGroup.setOrientation(RadioGroup.VERTICAL);
                RadioButton viewAction = new RadioButton(activity);
                viewAction.setText("android.intent.action.VIEW");
                RadioButton mainAction = new RadioButton(activity);
                mainAction.setText("android.intent.action.MAIN");
                RadioButton customAction = new RadioButton(activity);
                customAction.setId(View.generateViewId());
                customAction.setText("自定义 Action");
                editorViewAction[0] = viewAction;
                editorMainAction[0] = mainAction;
                editorCustomActionChoice[0] = customAction;
                actionGroup.addView(viewAction);
                actionGroup.addView(mainAction);
                actionGroup.addView(customAction);
                if ("android.intent.action.MAIN".equals(target.action)) mainAction.setChecked(true);
                else if ("android.intent.action.VIEW".equals(target.action) || target.action.isEmpty()) {
                    viewAction.setChecked(true);
                } else customAction.setChecked(true);
                form.addView(actionGroup);
                customActionInput = editorInput("输入自定义 Action", target.action, false);
                editorCustomAction[0] = customActionInput;
                customActionInput.setVisibility(customAction.isChecked() ? View.VISIBLE : View.GONE);
                form.addView(customActionInput);
                EditText finalCustomActionInput = customActionInput;
                actionGroup.setOnCheckedChangeListener((group, checkedId) ->
                        finalCustomActionInput.setVisibility(checkedId == customAction.getId()
                                ? View.VISIBLE : View.GONE));
                newTaskInput = new CheckBox(activity);
                newTaskInput.setText("使用 NEW_TASK");
                newTaskInput.setChecked(target.newTask);
                editorNewTask[0] = newTaskInput;
                form.addView(newTaskInput);
                rootInput = new CheckBox(activity);
                rootInput.setText("使用 Root 打开");
                rootInput.setChecked(target.root);
                editorRoot[0] = rootInput;
                form.addView(rootInput);
            } else {
                valueInput = editorInput(target.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI
                        ? "Intent URI" : "Scheme URL", target.value, true);
                form.addView(valueInput);
            }

            ScrollView scroll = new ScrollView(activity);
            scroll.addView(form);
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("编辑当前动作")
                    .setView(scroll)
                    .setNegativeButton("取消", null)
                    .setNeutralButton("切换动作", null)
                    .setPositiveButton("保存", null)
                    .create();
            dialog.show();
            styleBottomDialog(dialog);
            EditText finalValueInput = valueInput;
            EditText finalDataInput = dataInput;
            EditText finalExtrasInput = extrasInput;
            EditText finalCustomActionInput2 = customActionInput;
            RadioGroup finalActionGroup = actionGroup;
            CheckBox finalNewTaskInput = newTaskInput;
            CheckBox finalRootInput = rootInput;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String customName = nameInput.getText().toString().trim();
                SideGestureTargetStore.Target old = draftTarget[0];
                SideGestureTargetStore.Target saved;
                if (old.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY) {
                    String selectedAction = finalActionGroup.getCheckedRadioButtonId() ==
                            finalActionGroup.getChildAt(1).getId()
                            ? "android.intent.action.MAIN"
                            : finalActionGroup.getCheckedRadioButtonId() ==
                            finalActionGroup.getChildAt(0).getId()
                            ? "android.intent.action.VIEW"
                            : finalCustomActionInput2.getText().toString().trim();
                    saved = new SideGestureTargetStore.Target(old.id, old.type, old.label, old.value,
                            finalDataInput.getText().toString().trim(), finalExtrasInput.getText().toString(),
                            selectedAction, finalNewTaskInput.isChecked(), finalRootInput.isChecked(), customName);
                } else if (old.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP) {
                    saved = new SideGestureTargetStore.Target(old.id, old.type, old.label, old.value,
                            customName);
                } else {
                    String value = finalValueInput.getText().toString().trim();
                    if (value.isEmpty()) {
                        finalValueInput.setError("请输入目标内容");
                        return;
                    }
                    saved = new SideGestureTargetStore.Target(old.id, old.type, value, value,
                            customName);
                }
                activity.commitSideGestureTarget(cornerMode, tier, slot, draftAction[0], saved);
                actions[tier][slot] = draftAction[0];
                invalidate();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                Runnable switchAction = () -> {
                    dialog.dismiss();
                    showActionPicker(directionIndex, tier, selected -> {
                        draftAction[0] = selected;
                        if (SideGestureTargetStore.isDynamicAction(selected)) {
                            SideGestureTargetStore.Target next = SideGestureTargetStore.find(
                                    SideGestureTargetStore.readRaw(prefs), selected);
                            if (next != null) showCustomTargetEditor(directionIndex, tier, slot,
                                    selected, next);
                        } else {
                            showPendingActionEditor(directionIndex, tier, slot, selected);
                        }
                    });
                };
                if (editorHasChanges(target, draftTarget[0], draftAction[0], action, nameInput,
                        finalValueInput, finalDataInput, finalExtrasInput, finalCustomActionInput2,
                        finalActionGroup, finalNewTaskInput, finalRootInput)) {
                    confirmDiscard(dialog, switchAction);
                } else switchAction.run();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                if (editorHasChanges(target, draftTarget[0], draftAction[0], action, nameInput,
                        finalValueInput, finalDataInput, finalExtrasInput, finalCustomActionInput2,
                        finalActionGroup, finalNewTaskInput, finalRootInput)) {
                    confirmDiscard(dialog, dialog::dismiss);
                } else dialog.dismiss();
            });
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnKeyListener((d, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) {
                    return false;
                }
                if (editorHasChanges(target, draftTarget[0], draftAction[0], action, nameInput,
                        finalValueInput, finalDataInput, finalExtrasInput, finalCustomActionInput2,
                        finalActionGroup, finalNewTaskInput, finalRootInput)) {
                    confirmDiscard(dialog, dialog::dismiss);
                } else {
                    dialog.dismiss();
                }
                return true;
            });
            activity.attachDialogButtonHaptics(dialog);
        }

        private void showPendingActionEditor(int directionIndex, int tier, int slot, int action) {
            TextView summary = new TextView(activity);
            String label = action == SIDE_GESTURE_INHERIT_LONG
                    ? (tier == SIDE_GESTURE_FAR ? "沿用中距离动作" : "沿用近距离动作")
                    : actionLabelForAction(action);
            summary.setText("将当前触发动作切换为\n" + label);
            summary.setTextColor(activity.textColor());
            summary.setTextSize(16f);
            summary.setPadding(Math.round(dp(24)), Math.round(dp(12)),
                    Math.round(dp(24)), Math.round(dp(18)));
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("切换动作")
                    .setView(summary)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("保存", null)
                    .create();
            dialog.show();
            styleBottomDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                activity.commitSideGestureAction(cornerMode, tier, slot, action);
                actions[tier][slot] = action;
                invalidate();
                dialog.dismiss();
            });
            activity.attachDialogButtonHaptics(dialog);
        }

        private EditText editorInput(String hint, String value, boolean multiline) {
            EditText input = new EditText(activity);
            input.setHint(hint);
            input.setText(value == null ? "" : value);
            input.setTextColor(activity.textColor());
            input.setHintTextColor(activity.subtextColor());
            input.setTextSize(15f);
            input.setPadding(Math.round(dp(12)), Math.round(dp(6)),
                    Math.round(dp(12)), Math.round(dp(6)));
            input.setBackground(activity.outlinedRect(activity.surfaceColor(),
                    activity.strokeColor(), 1, 8));
            if (multiline) {
                input.setGravity(Gravity.TOP | Gravity.START);
                input.setMinHeight(Math.round(dp(88)));
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            } else {
                input.setSingleLine(true);
                input.setMinHeight(Math.round(dp(48)));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = Math.round(dp(8));
            input.setLayoutParams(params);
            return input;
        }

        private String targetSummary(SideGestureTargetStore.Target target) {
            switch (target.type) {
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP:
                    return "当前目标 · 指定应用\n" + target.label + "\n" + target.value;
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY:
                    return "当前目标 · Activity\n" + target.label + "\n" + target.value;
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI:
                    return "当前目标 · Intent URI\n" + target.value;
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL:
                    return "当前目标 · Scheme URL\n" + target.value;
                default:
                    return target.label;
            }
        }

        private boolean editorHasChanges(SideGestureTargetStore.Target original,
                SideGestureTargetStore.Target draft, int draftAction, int originalAction,
                EditText name, EditText value, EditText data, EditText extras, EditText customAction,
                RadioGroup actionGroup, CheckBox newTask, CheckBox root) {
            if (draftAction != originalAction || !name.getText().toString().trim().equals(draft.customName)) return true;
            if (draft.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI
                    || draft.type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL) {
                return value != null && !value.getText().toString().trim().equals(draft.value);
            }
            if (draft.type != SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY) return false;
            String selectedAction = actionGroup.getCheckedRadioButtonId() ==
                    actionGroup.getChildAt(1).getId()
                    ? "android.intent.action.MAIN"
                    : actionGroup.getCheckedRadioButtonId() == actionGroup.getChildAt(0).getId()
                    ? "android.intent.action.VIEW" : customAction.getText().toString().trim();
            return !selectedAction.equals(draft.action)
                    || !data.getText().toString().trim().equals(draft.dataUri)
                    || !extras.getText().toString().equals(draft.extras)
                    || newTask.isChecked() != draft.newTask || root.isChecked() != draft.root;
        }

        private void confirmDiscard(AlertDialog owner, Runnable onDiscard) {
            new AlertDialog.Builder(activity)
                    .setMessage("放弃本次修改？")
                    .setNegativeButton("继续编辑", null)
                    .setPositiveButton("放弃", (d, which) -> onDiscard.run())
                    .show();
        }

        private void styleBottomDialog(AlertDialog dialog) {
            activity.styleDialog(dialog);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setGravity(Gravity.BOTTOM);
                dialog.getWindow().setBackgroundDrawable(activity.roundRect(
                        activity.surfaceSoftColor(), 20));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        private int hitHandle(float x, float y) {
            float hit = dp(28);
            if (cornerMode) {
                int[] distances = {shortDistanceDp, longDistanceDp, farDistanceDp};
                int[] targets = {DRAG_SHORT, DRAG_LONG, DRAG_FAR};
                int count = farEnabled ? 3 : splitEnabled ? 2 : 1;
                int target = DRAG_NONE;
                for (int tier = 0; tier < count; tier++) {
                    float distance = (float) Math.hypot(x - handleX(distances[tier]),
                            y - (centerY - distances[tier] * scale * 0.7071f));
                    if (distance <= hit) {
                        hit = distance;
                        target = targets[tier];
                    }
                }
                return target;
            }
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
            float nearest = (float) Math.hypot(x - handleX(shortDistanceDp), y - centerY);
            int target = nearest <= hit ? DRAG_SHORT : DRAG_NONE;
            if (splitEnabled) {
                float distance = (float) Math.hypot(x - handleX(longDistanceDp), y - centerY);
                if (distance <= hit && distance < nearest) {
                    nearest = distance;
                    target = DRAG_LONG;
                }
            }
            if (farEnabled) {
                float distance = (float) Math.hypot(x - handleX(farDistanceDp), y - centerY);
                if (distance <= hit && distance < nearest) target = DRAG_FAR;
            }
            return target;
        }

        private float handleX(int distanceDp) {
            return screenX(cornerMode ? distanceDp * 0.7071f : distanceDp);
        }

        private int hitActionLabel(float x, float y) {
            for (int mode = farEnabled ? SIDE_GESTURE_FAR : splitEnabled ? SIDE_GESTURE_LONG : SIDE_GESTURE_SHORT; mode >= 0; mode--) {
                for (int index = 0; index < 6; index++) {
                    if (actionLabelBounds[mode][index].contains(x, y)) {
                        return mode * 3 + (cornerMode ? 0 : index % 3);
                    }
                }
            }
            return -1;
        }

        private String actionLabel(int index, int tier) {
            int action = actions[tier][index];
            if (cornerMode) {
                while (action == SIDE_GESTURE_INHERIT_LONG && tier > 0) {
                    action = actions[--tier][index];
                }
            } else if (action == SIDE_GESTURE_INHERIT_LONG) {
                action = actions[SIDE_GESTURE_LONG][index];
            }
            return actionLabelForAction(action);
        }

        private String actionLabelForAction(int action) {
            switch (action) {
                case SettingsStore.SIDE_GESTURE_ACTION_BACK: return "返回";
                case SettingsStore.SIDE_GESTURE_ACTION_HOME: return "返回桌面";
                case SettingsStore.SIDE_GESTURE_ACTION_RECENTS: return "最近任务";
                case SettingsStore.SIDE_GESTURE_ACTION_LOCK_SCREEN: return "锁屏";
                case SettingsStore.SIDE_GESTURE_ACTION_END_CURRENT_TASK: return "结束当前任务（App）";
                case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT: return "全局负一屏";
                case SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE: return "多应用工作台";
                case SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER: return "任务切换器";
                case SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS: return "快捷工具";
                case SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER: return "应用启动器";
                case SettingsStore.SIDE_GESTURE_ACTION_WINDOWMODE_PANEL: return "小窗选择面板";
                case SettingsStore.SIDE_GESTURE_ACTION_WELT_WINDOW: return "贴边";
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP: return "指定应用";
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY: return "指定 Activity";
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI: return "Intent URI";
                case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL: return "Scheme URL";
                default:
                    SideGestureTargetStore.Target target = SideGestureTargetStore.find(
                            SettingsStore.readString(prefs,
                                    SettingsStore.KEY_SIDE_GESTURE_TARGETS_JSON, "[]"), action);
                    if (target != null) {
                        if (!target.customName.isEmpty()) return target.customName;
                        switch (target.type) {
                            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP: return "打开应用";
                            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY: return "打开 Activity";
                            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI: return "打开 Intent URI";
                            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL:
                                return "打开 Scheme URL";
                        }
                    }
                    return "无动作";
            }
        }

        private String directionName(int direction) {
            if (cornerMode) return leftSide ? "左底角" : "右底角";
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
