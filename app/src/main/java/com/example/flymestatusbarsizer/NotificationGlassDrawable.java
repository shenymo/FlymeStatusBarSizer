package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class NotificationGlassDrawable extends Drawable {
    private static final int DEFAULT_TINT = Color.rgb(158, 208, 255);

    private final RectF boundsRect = new RectF();
    private final RectF renderRect = new RectF();
    private final RectF bandRect = new RectF();
    private final RectF accentRect = new RectF();
    private final RectF innerRect = new RectF();
    private final Path clipPath = new Path();
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glazePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cornerRadiusPx;
    private int alpha = 255;
    private int tintColor = Color.TRANSPARENT;
    private int actualHeightPx = -1;
    private int clipTopAmountPx;
    private int clipBottomAmountPx;
    private boolean bottomAmountClips = true;
    private boolean expandAnimationRunning;
    private boolean pressed;
    private boolean activated;
    private boolean focused;
    private boolean hovered;
    private boolean enabled = true;

    NotificationGlassDrawable(float cornerRadiusPx) {
        basePaint.setStyle(Paint.Style.FILL);
        glazePaint.setStyle(Paint.Style.FILL);
        topGlowPaint.setStyle(Paint.Style.FILL);
        lensPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        bandPaint.setStyle(Paint.Style.FILL);
        accentPaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.STROKE);
        innerEdgePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStyle(Paint.Style.FILL);
        setCornerRadiusPx(cornerRadiusPx);
    }

    void setCornerRadiusPx(float cornerRadiusPx) {
        float clamped = Math.max(0f, cornerRadiusPx);
        if (this.cornerRadiusPx != clamped) {
            this.cornerRadiusPx = clamped;
            invalidateSelf();
        }
    }

    void syncFromBackgroundState(int actualHeightPx, int clipTopAmountPx, int clipBottomAmountPx,
            int tintColor, int drawableAlpha, boolean bottomAmountClips,
            boolean expandAnimationRunning, int[] drawableState) {
        boolean changed = false;

        int normalizedHeight = actualHeightPx > 0 ? actualHeightPx : -1;
        if (this.actualHeightPx != normalizedHeight) {
            this.actualHeightPx = normalizedHeight;
            changed = true;
        }
        int normalizedClipTop = Math.max(0, clipTopAmountPx);
        if (this.clipTopAmountPx != normalizedClipTop) {
            this.clipTopAmountPx = normalizedClipTop;
            changed = true;
        }
        int normalizedClipBottom = Math.max(0, clipBottomAmountPx);
        if (this.clipBottomAmountPx != normalizedClipBottom) {
            this.clipBottomAmountPx = normalizedClipBottom;
            changed = true;
        }
        if (this.tintColor != tintColor) {
            this.tintColor = tintColor;
            changed = true;
        }
        int normalizedAlpha = clamp(drawableAlpha, 0, 255);
        if (this.alpha != normalizedAlpha) {
            this.alpha = normalizedAlpha;
            changed = true;
        }
        if (this.bottomAmountClips != bottomAmountClips) {
            this.bottomAmountClips = bottomAmountClips;
            changed = true;
        }
        if (this.expandAnimationRunning != expandAnimationRunning) {
            this.expandAnimationRunning = expandAnimationRunning;
            changed = true;
        }
        if (drawableState != null && setState(drawableState)) {
            changed = true;
        }
        if (changed) {
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || alpha <= 0) {
            return;
        }

        boundsRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);

        float visibleTop = boundsRect.top + Math.max(0, clipTopAmountPx);
        float visibleBottom = boundsRect.bottom - Math.max(0, clipBottomAmountPx);
        if (actualHeightPx > 0) {
            visibleBottom = Math.min(visibleBottom, boundsRect.top + actualHeightPx);
        }
        if (visibleBottom <= visibleTop + 1f) {
            return;
        }

        renderRect.set(boundsRect.left, visibleTop, boundsRect.right, visibleBottom);
        float width = renderRect.width();
        float height = renderRect.height();
        if (width <= 1f || height <= 1f) {
            return;
        }

        float radius = Math.min(Math.max(cornerRadiusPx, 18f), height * 0.5f);
        float heightRatio = clamp(height / Math.max(1f, boundsRect.height()), 0.42f, 1f);
        float interactionBoost = pressed ? 1f : 0f;
        interactionBoost = Math.max(interactionBoost, activated ? 0.72f : 0f);
        interactionBoost = Math.max(interactionBoost, focused ? 0.52f : 0f);
        interactionBoost = Math.max(interactionBoost, hovered ? 0.34f : 0f);
        if (!enabled) {
            interactionBoost *= 0.55f;
        }
        float animationBoost = expandAnimationRunning ? 0.28f : 0f;
        float energy = clamp(interactionBoost + animationBoost, 0f, 1.35f);
        float tintMix = tintColor != 0 ? 0.58f : 0.22f;
        float coolDepth = bottomAmountClips ? 1f : 0.78f;

        clipPath.reset();
        clipPath.addRoundRect(renderRect, radius, radius, Path.Direction.CW);

        basePaint.setShader(new LinearGradient(
                renderRect.left,
                renderRect.top,
                renderRect.left,
                renderRect.bottom,
                new int[]{
                        tintedColor(108 + (int) (18f * energy), 255, 255, 255, tintMix * 0.18f),
                        tintedColor(78 + (int) (16f * energy), 244, 248, 255, tintMix * 0.34f),
                        tintedColor(68 + (int) (22f * energy), 225, 235, 248, tintMix * 0.54f)
                },
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP));

        glazePaint.setShader(new LinearGradient(
                renderRect.left,
                renderRect.top,
                renderRect.right,
                renderRect.bottom,
                new int[]{
                        tintedColor(72 + (int) (14f * energy), 255, 255, 255, tintMix * 0.10f),
                        tintedColor(22 + (int) (8f * energy), 250, 252, 255, tintMix * 0.24f),
                        tintedColor(56 + (int) (16f * energy), 186, 208, 238, tintMix * 0.60f),
                        tintedColor(0, 186, 208, 238, tintMix * 0.72f)
                },
                new float[]{0f, 0.28f, 0.82f, 1f},
                Shader.TileMode.CLAMP));

        topGlowPaint.setShader(new RadialGradient(
                renderRect.left + width * (0.26f - 0.03f * energy),
                renderRect.top + height * 0.02f,
                Math.max(width, height) * (0.80f + 0.06f * energy),
                new int[]{
                        tintedColor(162 + (int) (20f * energy), 255, 255, 255, tintMix * 0.08f),
                        tintedColor(84 + (int) (16f * energy), 245, 250, 255, tintMix * 0.18f),
                        tintedColor(0, 245, 250, 255, tintMix * 0.22f)
                },
                new float[]{0f, 0.32f, 1f},
                Shader.TileMode.CLAMP));

        lensPaint.setShader(new RadialGradient(
                renderRect.left + width * (0.76f + 0.03f * energy),
                renderRect.top + height * (0.70f - 0.04f * energy),
                Math.max(width, height) * (0.62f + 0.04f * heightRatio),
                new int[]{
                        tintedColor(58 + (int) (10f * energy), 214, 236, 255, tintMix * 0.62f),
                        tintedColor(34 + (int) (8f * energy), 176, 205, 242, tintMix * 0.82f),
                        tintedColor(0, 176, 205, 242, tintMix)
                },
                new float[]{0f, 0.42f, 1f},
                Shader.TileMode.CLAMP));

        shadowPaint.setShader(new LinearGradient(
                renderRect.left,
                renderRect.top + height * 0.42f,
                renderRect.left,
                renderRect.bottom,
                new int[]{
                        tintedColor(0, 110, 152, 210, tintMix * 0.58f),
                        tintedColor((int) (28f * coolDepth), 110, 152, 210, tintMix * 0.74f),
                        tintedColor(56 + (int) (12f * energy), 78, 116, 170, tintMix)
                },
                new float[]{0f, 0.72f, 1f},
                Shader.TileMode.CLAMP));

        edgePaint.setStrokeWidth(1.15f);
        edgePaint.setShader(new LinearGradient(
                renderRect.left,
                renderRect.top,
                renderRect.left,
                renderRect.bottom,
                new int[]{
                        tintedColor(170 + (int) (14f * energy), 255, 255, 255, tintMix * 0.06f),
                        tintedColor(118 + (int) (10f * energy), 224, 238, 255, tintMix * 0.24f),
                        tintedColor(142 + (int) (8f * energy), 255, 255, 255, tintMix * 0.10f)
                },
                new float[]{0f, 0.42f, 1f},
                Shader.TileMode.CLAMP));

        innerEdgePaint.setStrokeWidth(0.85f);
        innerEdgePaint.setShader(new LinearGradient(
                renderRect.left,
                renderRect.top,
                renderRect.right,
                renderRect.bottom,
                new int[]{
                        tintedColor(102 + (int) (12f * energy), 255, 255, 255, tintMix * 0.08f),
                        tintedColor(44 + (int) (8f * energy), 214, 234, 255, tintMix * 0.48f),
                        tintedColor(72 + (int) (10f * energy), 255, 255, 255, tintMix * 0.12f)
                },
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP));

        int saveCount = canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRoundRect(renderRect, radius, radius, basePaint);
        canvas.drawRoundRect(renderRect, radius, radius, glazePaint);
        canvas.drawRoundRect(renderRect, radius, radius, topGlowPaint);
        canvas.drawRoundRect(renderRect, radius, radius, lensPaint);
        canvas.drawRoundRect(renderRect, radius, radius, shadowPaint);

        bandRect.set(
                renderRect.left - width * 0.08f,
                renderRect.top + height * (0.08f + 0.02f * (1f - heightRatio)),
                renderRect.left + width * (0.78f + 0.04f * energy),
                renderRect.top + height * (0.28f + 0.03f * energy));
        bandPaint.setShader(new LinearGradient(
                bandRect.left,
                bandRect.centerY(),
                bandRect.right,
                bandRect.centerY(),
                new int[]{
                        tintedColor(0, 255, 255, 255, tintMix * 0.04f),
                        tintedColor(74 + (int) (18f * energy), 255, 255, 255, tintMix * 0.02f),
                        tintedColor(126 + (int) (22f * energy), 255, 255, 255, tintMix * 0.06f),
                        tintedColor(82 + (int) (14f * energy), 220, 236, 255, tintMix * 0.38f),
                        tintedColor(0, 220, 236, 255, tintMix * 0.42f)
                },
                new float[]{0f, 0.20f, 0.52f, 0.76f, 1f},
                Shader.TileMode.CLAMP));
        canvas.save();
        canvas.rotate(-10f - 4f * energy, bandRect.centerX(), bandRect.centerY());
        canvas.drawRoundRect(bandRect, bandRect.height() * 0.5f, bandRect.height() * 0.5f, bandPaint);
        canvas.restore();

        accentRect.set(
                renderRect.left + width * (0.54f - 0.03f * energy),
                renderRect.top + height * 0.02f,
                renderRect.right + width * 0.06f,
                renderRect.top + height * (0.22f + 0.03f * energy));
        accentPaint.setShader(new LinearGradient(
                accentRect.left,
                accentRect.centerY(),
                accentRect.right,
                accentRect.centerY(),
                new int[]{
                        tintedColor(0, 255, 255, 255, tintMix * 0.06f),
                        tintedColor(70 + (int) (14f * energy), 232, 242, 255, tintMix * 0.24f),
                        tintedColor(110 + (int) (20f * energy), 255, 255, 255, tintMix * 0.02f),
                        tintedColor(0, 255, 255, 255, tintMix * 0.06f)
                },
                new float[]{0f, 0.28f, 0.64f, 1f},
                Shader.TileMode.CLAMP));
        canvas.save();
        canvas.rotate(16f + 4f * energy, accentRect.centerX(), accentRect.centerY());
        canvas.drawRoundRect(accentRect, accentRect.height() * 0.5f, accentRect.height() * 0.5f, accentPaint);
        canvas.restore();

        innerRect.set(
                renderRect.left + width * 0.14f,
                renderRect.top + height * 0.18f,
                renderRect.right - width * 0.14f,
                renderRect.top + height * (0.46f + 0.04f * energy));
        corePaint.setShader(new LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.left,
                innerRect.bottom,
                new int[]{
                        tintedColor(18 + (int) (6f * energy), 255, 255, 255, tintMix * 0.04f),
                        tintedColor(42 + (int) (10f * energy), 240, 247, 255, tintMix * 0.22f),
                        tintedColor(0, 240, 247, 255, tintMix * 0.18f)
                },
                new float[]{0f, 0.62f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(innerRect, innerRect.height() * 0.52f, innerRect.height() * 0.52f, corePaint);
        canvas.restoreToCount(saveCount);

        float inset = 1.2f;
        innerRect.set(
                renderRect.left + inset,
                renderRect.top + inset,
                renderRect.right - inset,
                renderRect.bottom - inset);
        float innerRadius = Math.max(0f, radius - inset);
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerEdgePaint);
        canvas.drawRoundRect(renderRect, radius, radius, edgePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        int clamped = clamp(alpha, 0, 255);
        if (this.alpha != clamped) {
            this.alpha = clamped;
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        basePaint.setColorFilter(colorFilter);
        glazePaint.setColorFilter(colorFilter);
        topGlowPaint.setColorFilter(colorFilter);
        lensPaint.setColorFilter(colorFilter);
        shadowPaint.setColorFilter(colorFilter);
        bandPaint.setColorFilter(colorFilter);
        accentPaint.setColorFilter(colorFilter);
        edgePaint.setColorFilter(colorFilter);
        innerEdgePaint.setColorFilter(colorFilter);
        corePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean newPressed = hasState(stateSet, android.R.attr.state_pressed);
        boolean newActivated = hasState(stateSet, android.R.attr.state_activated);
        boolean newFocused = hasState(stateSet, android.R.attr.state_focused);
        boolean newHovered = hasState(stateSet, android.R.attr.state_hovered);
        boolean newEnabled = !hasState(stateSet, -android.R.attr.state_enabled);
        if (pressed == newPressed
                && activated == newActivated
                && focused == newFocused
                && hovered == newHovered
                && enabled == newEnabled) {
            return false;
        }
        pressed = newPressed;
        activated = newActivated;
        focused = newFocused;
        hovered = newHovered;
        enabled = newEnabled;
        invalidateSelf();
        return true;
    }

    private int tintedColor(int colorAlpha, int baseR, int baseG, int baseB, float tintMix) {
        int tint = tintColor != 0 ? tintColor : DEFAULT_TINT;
        int red = mixChannel(baseR, Color.red(tint), tintMix);
        int green = mixChannel(baseG, Color.green(tint), tintMix);
        int blue = mixChannel(baseB, Color.blue(tint), tintMix);
        return Color.argb(scaleAlpha(colorAlpha), red, green, blue);
    }

    private int scaleAlpha(int colorAlpha) {
        return clamp((colorAlpha * alpha) / 255, 0, 255);
    }

    private static int mixChannel(int from, int to, float amount) {
        float clamped = clamp(amount, 0f, 1f);
        return clamp(Math.round(from + ((to - from) * clamped)), 0, 255);
    }

    private static boolean hasState(int[] stateSet, int state) {
        if (stateSet == null) {
            return false;
        }
        for (int item : stateSet) {
            if (item == state) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
