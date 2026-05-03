package com.example.flymestatusbarsizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class NotificationLiquidGlassView extends View {
    private static final String SHADER_SRC = """
            uniform shader content;
            uniform float2 size;
            uniform float2 offset;
            uniform float4 cornerRadii;
            uniform float refractionHeight;
            uniform float refractionAmount;
            uniform float depthEffect;
            uniform float chromaticAberration;
            uniform float contrast;
            uniform float whitePoint;
            uniform float chromaMultiplier;
            uniform float3 tintColor;
            uniform float tintAlpha;

            const half3 rgbToY = half3(0.2126, 0.7152, 0.0722);

            float radiusAt(float2 coord, float4 radii) {
                if (coord.x >= 0.0) {
                    if (coord.y <= 0.0) return radii.y;
                    return radii.z;
                }
                if (coord.y <= 0.0) return radii.x;
                return radii.w;
            }

            float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
                float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
                float outside = length(max(cornerCoord, 0.0)) - radius;
                float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
                return outside + inside;
            }

            float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
                float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
                if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                    return sign(coord) * normalize(max(cornerCoord, 0.0));
                } else {
                    float gradX = step(cornerCoord.y, cornerCoord.x);
                    return sign(coord) * float2(gradX, 1.0 - gradX);
                }
            }

            float circleMap(float x) {
                return 1.0 - sqrt(1.0 - x * x);
            }

            half4 saturateColor(half4 color, float amount) {
                half3 lin = toLinearSrgb(color.rgb);
                float y = dot(lin, rgbToY);
                half3 gray = half3(y);
                half3 sat = fromLinearSrgb(mix(gray, lin, amount));
                return half4(sat, color.a);
            }

            half4 main(float2 coord) {
                float2 halfSize = size * 0.5;
                float2 centeredCoord = (coord + offset) - halfSize;
                float radius = radiusAt(centeredCoord, cornerRadii);

                float sd = sdRoundedRect(centeredCoord, halfSize, radius);
                if (-sd >= refractionHeight) {
                    half4 baseColor = content.eval(coord);
                    baseColor = saturateColor(baseColor, chromaMultiplier);
                    float3 target = (whitePoint > 0.0) ? float3(1.0) : float3(0.0);
                    baseColor.rgb = mix(baseColor.rgb, target, abs(whitePoint));
                    baseColor.rgb = (baseColor.rgb - 0.5) * (1.0 + contrast) + 0.5;
                    half3 tintedRGB = mix(baseColor.rgb, tintColor, tintAlpha);
                    return half4(tintedRGB, baseColor.a);
                }

                sd = min(sd, 0.0);
                float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
                float smoothRadius = max(radius * 1.5, 30.0);
                float gradRadius = min(smoothRadius, min(halfSize.x, halfSize.y));

                float2 grad = normalize(
                        gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
                                + depthEffect * normalize(centeredCoord));

                float2 refractedCoord = coord + d * grad;
                float dispersionIntensity = chromaticAberration
                        * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
                float2 dispersedCoord = d * grad * dispersionIntensity;

                half4 color = half4(0.0);

                half4 red = content.eval(refractedCoord + dispersedCoord);
                color.r += red.r / 3.5;
                color.a += red.a / 7.0;

                half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
                color.r += orange.r / 3.5;
                color.g += orange.g / 7.0;
                color.a += orange.a / 7.0;

                half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
                color.r += yellow.r / 3.5;
                color.g += yellow.g / 3.5;
                color.a += yellow.a / 7.0;

                half4 green = content.eval(refractedCoord);
                color.g += green.g / 3.5;
                color.a += green.a / 7.0;

                half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
                color.g += cyan.g / 3.5;
                color.b += cyan.b / 3.0;
                color.a += cyan.a / 7.0;

                half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
                color.b += blue.b / 3.0;
                color.a += blue.a / 7.0;

                half4 purple = content.eval(refractedCoord - dispersedCoord);
                color.r += purple.r / 7.0;
                color.b += purple.b / 3.0;
                color.a += purple.a / 7.0;

                color = saturateColor(color, chromaMultiplier);
                float3 target = (whitePoint > 0.0) ? float3(1.0) : float3(0.0);
                color.rgb = mix(color.rgb, target, abs(whitePoint));
                color.rgb = (color.rgb - 0.5) * (1.0 + contrast) + 0.5;

                half3 tintedRGB = mix(color.rgb, tintColor, tintAlpha);
                return half4(tintedRGB, color.a);
            }
            """;

    private static final float DEFAULT_REFRACTION_HEIGHT_DP = 18f;
    private static final float DEFAULT_REFRACTION_OFFSET_DP = -56f;
    private static final float DEFAULT_BLUR_RADIUS = 14f;
    private static final float DEFAULT_DISPERSION = 0.32f;
    private static final float DEFAULT_DEPTH_EFFECT = 0.22f;
    private static final float DEFAULT_CHROMA_MULTIPLIER = 1.05f;
    private static final float DEFAULT_TINT_ALPHA = 0.12f;
    private static final boolean LIVE_SAMPLING_ENABLED = false;

    private final RectF clipRect = new RectF();
    private final Path clipPath = new Path();
    private final int[] sourcePos = new int[2];
    private final int[] hostPos = new int[2];
    private final int[] excludedPos = new int[2];
    private final Rect excludedRect = new Rect();
    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        updateRenderNode();
        return true;
    };
    private final Paint glassBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassInnerEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassTopHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassBottomTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassSpecularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ViewGroup source;
    private View excludedView;
    private float cornerRadiusPx;
    private boolean listenerAdded;
    private int excludedVisibilityDuringSnapshot = -1;

    private Object renderNode;
    private Object shader;
    private Object blurEffect;
    private float lastBlurRadius = Float.NaN;

    private static volatile Class<?> RENDER_NODE_CLASS;
    private static volatile Class<?> RUNTIME_SHADER_CLASS;
    private static volatile Class<?> RENDER_EFFECT_CLASS;
    private static volatile Class<?> SHADER_TILE_MODE_CLASS;
    private static volatile Object SHADER_TILE_MODE_CLAMP;
    private static volatile Constructor<?> RENDER_NODE_CONSTRUCTOR;
    private static volatile Constructor<?> RUNTIME_SHADER_CONSTRUCTOR;
    private static volatile Method RENDER_NODE_BEGIN_RECORDING;
    private static volatile Method RENDER_NODE_END_RECORDING;
    private static volatile Method RENDER_NODE_SET_POSITION;
    private static volatile Method RENDER_NODE_SET_RENDER_EFFECT;
    private static volatile Method CANVAS_DRAW_RENDER_NODE;
    private static volatile Method RENDER_EFFECT_CREATE_BLUR;
    private static volatile Method RENDER_EFFECT_CREATE_RUNTIME_SHADER;
    private static volatile Method RENDER_EFFECT_CREATE_CHAIN;
    private static volatile Method SHADER_SET_FLOAT_UNIFORM_ARRAY;
    private static volatile Method SHADER_SET_FLOAT_UNIFORM_SINGLE;

    NotificationLiquidGlassView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        initializeFallbackPaints();
        initializePlatformObjects();
    }

    void bind(ViewGroup source, View excludedView) {
        if (this.source == source && this.excludedView == excludedView) {
            return;
        }
        removePreDrawListener();
        this.source = source;
        this.excludedView = excludedView;
        addPreDrawListener();
        invalidate();
    }

    void setCornerRadiusPx(float cornerRadiusPx) {
        this.cornerRadiusPx = Math.max(0f, cornerRadiusPx);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        addPreDrawListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        removePreDrawListener();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invoke(renderNode, getRenderNodeSetPosition(), 0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        clipRect.set(0, 0, getWidth(), getHeight());
        clipPath.reset();
        clipPath.addRoundRect(clipRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        if (LIVE_SAMPLING_ENABLED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && renderNode != null
                && canvas.isHardwareAccelerated()) {
            invoke(canvas, getCanvasDrawRenderNode(), renderNode);
        }
        drawFallbackGlass(canvas);
        canvas.restoreToCount(save);
    }

    private void addPreDrawListener() {
        if (!LIVE_SAMPLING_ENABLED || source == null || listenerAdded) {
            return;
        }
        ViewTreeObserver observer = source.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnPreDrawListener(preDrawListener);
            listenerAdded = true;
        }
    }

    private void removePreDrawListener() {
        if (!LIVE_SAMPLING_ENABLED || source == null || !listenerAdded) {
            return;
        }
        ViewTreeObserver observer = source.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(preDrawListener);
        }
        listenerAdded = false;
    }

    private void updateRenderNode() {
        if (!LIVE_SAMPLING_ENABLED
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || renderNode == null
                || shader == null
                || source == null
                || getWidth() <= 0
                || getHeight() <= 0) {
            return;
        }

        Canvas recordingCanvas = (Canvas) invoke(renderNode, getRenderNodeBeginRecording(), getWidth(), getHeight());
        if (recordingCanvas == null) {
            return;
        }
        source.getLocationInWindow(sourcePos);
        getLocationInWindow(hostPos);
        recordingCanvas.translate(-(hostPos[0] - sourcePos[0]), -(hostPos[1] - sourcePos[1]));

        if (excludedView != null) {
            excludedView.getLocationInWindow(excludedPos);
            excludedRect.set(
                    excludedPos[0] - sourcePos[0],
                    excludedPos[1] - sourcePos[1],
                    excludedPos[0] - sourcePos[0] + excludedView.getWidth(),
                    excludedPos[1] - sourcePos[1] + excludedView.getHeight());
            recordingCanvas.clipOutRect(excludedRect);
            if (excludedRect.width() <= 0 || excludedRect.height() <= 0) {
                excludedView = null;
            }
        }
        try {
            hideExcludedViewForSnapshot();
            source.draw(recordingCanvas);
        } finally {
            restoreExcludedViewAfterSnapshot();
            invoke(renderNode, getRenderNodeEndRecording());
        }
        applyRenderEffect();
    }

    private void initializeFallbackPaints() {
        glassBasePaint.setStyle(Paint.Style.FILL);
        glassBasePaint.setColor(Color.argb(82, 255, 255, 255));

        glassEdgePaint.setStyle(Paint.Style.STROKE);
        glassEdgePaint.setStrokeWidth(dp(1.15f));
        glassEdgePaint.setColor(Color.argb(150, 255, 255, 255));

        glassInnerEdgePaint.setStyle(Paint.Style.STROKE);
        glassInnerEdgePaint.setStrokeWidth(dp(0.8f));
        glassInnerEdgePaint.setColor(Color.argb(70, 210, 235, 255));

        glassTopHighlightPaint.setStyle(Paint.Style.FILL);
        glassBottomTintPaint.setStyle(Paint.Style.FILL);
        glassSpecularPaint.setStyle(Paint.Style.FILL);
    }

    private void drawFallbackGlass(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        float radius = Math.max(cornerRadiusPx, dp(18f));
        float inset = dp(1.2f);

        glassTopHighlightPaint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                getHeight() * 0.58f,
                new int[]{
                        Color.argb(155, 255, 255, 255),
                        Color.argb(92, 250, 252, 255),
                        Color.argb(18, 255, 255, 255)
                },
                new float[]{0f, 0.34f, 1f},
                Shader.TileMode.CLAMP));

        glassBottomTintPaint.setShader(new LinearGradient(
                0f,
                getHeight() * 0.45f,
                0f,
                getHeight(),
                new int[]{
                        Color.argb(0, 120, 180, 255),
                        Color.argb(26, 120, 180, 255),
                        Color.argb(44, 82, 148, 255)
                },
                new float[]{0f, 0.72f, 1f},
                Shader.TileMode.CLAMP));

        glassSpecularPaint.setShader(new RadialGradient(
                getWidth() * 0.28f,
                getHeight() * 0.14f,
                Math.max(getWidth(), getHeight()) * 0.52f,
                new int[]{
                        Color.argb(135, 255, 255, 255),
                        Color.argb(52, 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                },
                new float[]{0f, 0.34f, 1f},
                Shader.TileMode.CLAMP));

        canvas.drawRoundRect(clipRect, radius, radius, glassBasePaint);
        canvas.drawRoundRect(clipRect, radius, radius, glassTopHighlightPaint);
        canvas.drawRoundRect(clipRect, radius, radius, glassBottomTintPaint);
        canvas.drawRoundRect(clipRect, radius, radius, glassSpecularPaint);

        RectF innerRect = new RectF(
                clipRect.left + inset,
                clipRect.top + inset,
                clipRect.right - inset,
                clipRect.bottom - inset);
        canvas.drawRoundRect(innerRect, Math.max(0f, radius - inset), Math.max(0f, radius - inset),
                glassInnerEdgePaint);
        canvas.drawRoundRect(clipRect, radius, radius, glassEdgePaint);
    }

    private void hideExcludedViewForSnapshot() {
        if (excludedView == null) {
            return;
        }
        excludedVisibilityDuringSnapshot = excludedView.getVisibility();
        if (excludedVisibilityDuringSnapshot == View.VISIBLE) {
            excludedView.setVisibility(View.INVISIBLE);
        }
    }

    private void restoreExcludedViewAfterSnapshot() {
        if (excludedView == null || excludedVisibilityDuringSnapshot == -1) {
            return;
        }
        if (excludedView.getVisibility() != excludedVisibilityDuringSnapshot) {
            excludedView.setVisibility(excludedVisibilityDuringSnapshot);
        }
        excludedVisibilityDuringSnapshot = -1;
    }

    private void applyRenderEffect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || renderNode == null
                || shader == null) {
            return;
        }
        float blurRadius = DEFAULT_BLUR_RADIUS;
        if (blurEffect == null || lastBlurRadius != blurRadius) {
            blurEffect = invokeStatic(getRenderEffectCreateBlur(), blurRadius, blurRadius, getShaderTileModeClamp());
            lastBlurRadius = blurRadius;
        }
        float[] size = new float[]{getWidth(), getHeight()};
        float[] offset = new float[]{0f, 0f};
        float[] cornerRadii = new float[]{
                cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx
        };
        setShaderFloatUniform("size", size);
        setShaderFloatUniform("offset", offset);
        setShaderFloatUniform("cornerRadii", cornerRadii);
        setShaderFloatUniform("refractionHeight", dp(DEFAULT_REFRACTION_HEIGHT_DP));
        setShaderFloatUniform("refractionAmount", dp(DEFAULT_REFRACTION_OFFSET_DP));
        setShaderFloatUniform("depthEffect", DEFAULT_DEPTH_EFFECT);
        setShaderFloatUniform("chromaticAberration", DEFAULT_DISPERSION);
        setShaderFloatUniform("contrast", 0.06f);
        setShaderFloatUniform("whitePoint", 0.08f);
        setShaderFloatUniform("chromaMultiplier", DEFAULT_CHROMA_MULTIPLIER);
        setShaderFloatUniform("tintColor", new float[]{1f, 1f, 1f});
        setShaderFloatUniform("tintAlpha", DEFAULT_TINT_ALPHA);

        Object shaderEffect = invokeStatic(getRenderEffectCreateRuntimeShader(), shader, "content");
        Object finalEffect = invokeStatic(getRenderEffectCreateChain(), shaderEffect, blurEffect);
        invoke(renderNode, getRenderNodeSetRenderEffect(), finalEffect);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void initializePlatformObjects() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        try {
            renderNode = getRenderNodeConstructor().newInstance("NotificationLiquidGlass");
            shader = getRuntimeShaderConstructor().newInstance(SHADER_SRC);
        } catch (Throwable ignored) {
            renderNode = null;
            shader = null;
        }
    }

    private void setShaderFloatUniform(String name, float value) {
        invoke(shader, getShaderSetFloatUniformSingle(), name, value);
    }

    private void setShaderFloatUniform(String name, float[] values) {
        invoke(shader, getShaderSetFloatUniformArray(), name, values);
    }

    private static Object invoke(Object target, Method method, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeStatic(Method method, Object... args) {
        return invoke(null, method, args);
    }

    private static Constructor<?> getRenderNodeConstructor() throws Exception {
        if (RENDER_NODE_CONSTRUCTOR == null) {
            RENDER_NODE_CONSTRUCTOR = getRenderNodeClass().getConstructor(String.class);
            RENDER_NODE_CONSTRUCTOR.setAccessible(true);
        }
        return RENDER_NODE_CONSTRUCTOR;
    }

    private static Constructor<?> getRuntimeShaderConstructor() throws Exception {
        if (RUNTIME_SHADER_CONSTRUCTOR == null) {
            RUNTIME_SHADER_CONSTRUCTOR = getRuntimeShaderClass().getConstructor(String.class);
            RUNTIME_SHADER_CONSTRUCTOR.setAccessible(true);
        }
        return RUNTIME_SHADER_CONSTRUCTOR;
    }

    private static Method getRenderNodeBeginRecording() {
        if (RENDER_NODE_BEGIN_RECORDING == null) {
            try {
                RENDER_NODE_BEGIN_RECORDING = getRenderNodeClass().getMethod("beginRecording", int.class, int.class);
                RENDER_NODE_BEGIN_RECORDING.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return RENDER_NODE_BEGIN_RECORDING;
    }

    private static Method getRenderNodeEndRecording() {
        if (RENDER_NODE_END_RECORDING == null) {
            try {
                RENDER_NODE_END_RECORDING = getRenderNodeClass().getMethod("endRecording");
                RENDER_NODE_END_RECORDING.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return RENDER_NODE_END_RECORDING;
    }

    private static Method getRenderNodeSetPosition() {
        if (RENDER_NODE_SET_POSITION == null) {
            try {
                RENDER_NODE_SET_POSITION = getRenderNodeClass().getMethod(
                        "setPosition", int.class, int.class, int.class, int.class);
                RENDER_NODE_SET_POSITION.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return RENDER_NODE_SET_POSITION;
    }

    private static Method getRenderNodeSetRenderEffect() {
        if (RENDER_NODE_SET_RENDER_EFFECT == null) {
            try {
                RENDER_NODE_SET_RENDER_EFFECT = getRenderNodeClass().getMethod(
                        "setRenderEffect", getRenderEffectClass());
                RENDER_NODE_SET_RENDER_EFFECT.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return RENDER_NODE_SET_RENDER_EFFECT;
    }

    private static Method getCanvasDrawRenderNode() {
        if (CANVAS_DRAW_RENDER_NODE == null) {
            try {
                CANVAS_DRAW_RENDER_NODE = Canvas.class.getMethod("drawRenderNode", getRenderNodeClass());
                CANVAS_DRAW_RENDER_NODE.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return CANVAS_DRAW_RENDER_NODE;
    }

    private static Method getRenderEffectCreateBlur() {
        if (RENDER_EFFECT_CREATE_BLUR == null) {
            try {
                RENDER_EFFECT_CREATE_BLUR = getRenderEffectClass().getMethod(
                        "createBlurEffect", float.class, float.class, getShaderTileModeClass());
                RENDER_EFFECT_CREATE_BLUR.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return RENDER_EFFECT_CREATE_BLUR;
    }

    private static Method getRenderEffectCreateRuntimeShader() {
        if (RENDER_EFFECT_CREATE_RUNTIME_SHADER == null) {
            try {
                RENDER_EFFECT_CREATE_RUNTIME_SHADER = getRenderEffectClass().getMethod(
                        "createRuntimeShaderEffect", getRuntimeShaderClass(), String.class);
                RENDER_EFFECT_CREATE_RUNTIME_SHADER.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return RENDER_EFFECT_CREATE_RUNTIME_SHADER;
    }

    private static Method getRenderEffectCreateChain() {
        if (RENDER_EFFECT_CREATE_CHAIN == null) {
            try {
                RENDER_EFFECT_CREATE_CHAIN = getRenderEffectClass().getMethod(
                        "createChainEffect", getRenderEffectClass(), getRenderEffectClass());
                RENDER_EFFECT_CREATE_CHAIN.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return RENDER_EFFECT_CREATE_CHAIN;
    }

    private static Method getShaderSetFloatUniformArray() {
        if (SHADER_SET_FLOAT_UNIFORM_ARRAY == null) {
            try {
                SHADER_SET_FLOAT_UNIFORM_ARRAY = getRuntimeShaderClass().getMethod(
                        "setFloatUniform", String.class, float[].class);
                SHADER_SET_FLOAT_UNIFORM_ARRAY.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return SHADER_SET_FLOAT_UNIFORM_ARRAY;
    }

    private static Method getShaderSetFloatUniformSingle() {
        if (SHADER_SET_FLOAT_UNIFORM_SINGLE == null) {
            try {
                SHADER_SET_FLOAT_UNIFORM_SINGLE = getRuntimeShaderClass().getMethod(
                        "setFloatUniform", String.class, float.class);
                SHADER_SET_FLOAT_UNIFORM_SINGLE.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        return SHADER_SET_FLOAT_UNIFORM_SINGLE;
    }

    private static Class<?> getRenderNodeClass() throws Exception {
        if (RENDER_NODE_CLASS == null) {
            RENDER_NODE_CLASS = Class.forName("android.graphics.RenderNode");
        }
        return RENDER_NODE_CLASS;
    }

    private static Class<?> getRuntimeShaderClass() throws Exception {
        if (RUNTIME_SHADER_CLASS == null) {
            RUNTIME_SHADER_CLASS = Class.forName("android.graphics.RuntimeShader");
        }
        return RUNTIME_SHADER_CLASS;
    }

    private static Class<?> getRenderEffectClass() throws Exception {
        if (RENDER_EFFECT_CLASS == null) {
            RENDER_EFFECT_CLASS = Class.forName("android.graphics.RenderEffect");
        }
        return RENDER_EFFECT_CLASS;
    }

    private static Class<?> getShaderTileModeClass() throws Exception {
        if (SHADER_TILE_MODE_CLASS == null) {
            SHADER_TILE_MODE_CLASS = Class.forName("android.graphics.Shader$TileMode");
        }
        return SHADER_TILE_MODE_CLASS;
    }

    private static Object getShaderTileModeClamp() {
        if (SHADER_TILE_MODE_CLAMP == null) {
            try {
                SHADER_TILE_MODE_CLAMP = Enum.valueOf((Class<Enum>) getShaderTileModeClass(), "CLAMP");
            } catch (Throwable ignored) {
                SHADER_TILE_MODE_CLAMP = null;
            }
        }
        return SHADER_TILE_MODE_CLAMP;
    }
}
