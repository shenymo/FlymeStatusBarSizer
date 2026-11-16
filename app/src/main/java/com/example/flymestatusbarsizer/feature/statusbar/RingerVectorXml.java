package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import android.util.LruCache;
import android.util.Xml;

import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import androidx.core.graphics.PathParser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RingerVectorXml {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final LruCache<String, RingerVectorXml> CACHE = new LruCache<>(32);
    private final Group root;
    private final float width, height, alpha;
    private final String xml;
    private RectF iconDrawingBounds;

    private RingerVectorXml(Group root, float width, float height, float alpha, String xml) {
        this.root = root;
        this.width = width;
        this.height = height;
        this.alpha = alpha;
        this.xml = xml;
    }

    public static RingerVectorXml parse(String xml) {
        return parse(xml, null);
    }

    public static RingerVectorXml parse(String xml, Context context) {
        String cacheKey = (context == null ? "" : context.getPackageName()) + "\n" + xml;
        RingerVectorXml cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        if (xml.contains("<animated-vector") || xml.contains("<aapt:attr")
                || xml.contains("=\"@") || xml.contains("=\"?")) {
            validateNativeXml(xml);
            RingerVectorXml nativeDocument = new RingerVectorXml(null, 24, 24, 1, xml);
            CACHE.put(cacheKey, nativeDocument);
            return nativeDocument;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(new StringReader(xml));
            Group root = null;
            float width = 0, height = 0, alpha = 1;
            ArrayDeque<Group> groups = new ArrayDeque<>();
            int paths = 0;
            for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (!"".equals(parser.getNamespace())) throw new IllegalArgumentException("标签不支持命名空间：" + tag);
                    Set<String> allowed = switch (tag) {
                        case "vector" -> Set.of("name", "width", "height", "viewportWidth", "viewportHeight", "alpha", "tint", "tintMode", "autoMirrored");
                        case "group" -> Set.of("name", "rotation", "pivotX", "pivotY", "scaleX", "scaleY", "translateX", "translateY");
                        case "path" -> Set.of("name", "pathData", "fillColor", "fillAlpha", "fillType", "strokeColor", "strokeAlpha", "strokeWidth", "strokeLineCap", "strokeLineJoin", "strokeMiterLimit", "trimPathStart", "trimPathEnd", "trimPathOffset");
                        case "clip-path" -> Set.of("name", "pathData", "fillType");
                        default -> throw new IllegalArgumentException("不支持标签：" + tag);
                    };
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String name = parser.getAttributeName(i);
                        if (!ANDROID.equals(parser.getAttributeNamespace(i)) || !allowed.contains(name)) {
                            throw new IllegalArgumentException("不支持属性：" + name);
                        }
                    }
                    if (root == null) {
                        if (!"vector".equals(tag) || parser.getDepth() != 1) throw new IllegalArgumentException("根标签必须是 vector");
                        width = number(parser, "viewportWidth", 0);
                        height = number(parser, "viewportHeight", 0);
                        if (width <= 0 || height <= 0) throw new IllegalArgumentException("viewportWidth 和 viewportHeight 必须大于 0");
                        for (String dimension : new String[]{"width", "height"}) {
                            String value = attr(parser, dimension, "");
                            if (value.startsWith("@") || value.startsWith("?")) continue;
                            if (!value.matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:dp|dip|px)")) throw new IllegalArgumentException(dimension + " 需填写正数及 dp 或 px 单位");
                            float size = Float.parseFloat(value.replaceAll("[a-z]", ""));
                            if (!Float.isFinite(size) || size <= 0) throw new IllegalArgumentException(dimension + " 必须大于 0");
                        }
                        alpha = unit(parser, "alpha", 1);
                        if (parser.getAttributeValue(ANDROID, "tint") != null) color(parser, "tint", Color.WHITE);
                        String mirrored = attr(parser, "autoMirrored", "false");
                        if (!mirrored.equals("true") && !mirrored.equals("false")) throw new IllegalArgumentException("autoMirrored 需为 true 或 false");
                        String mode = attr(parser, "tintMode", "src_in");
                        if (!Set.of("src_in", "src_over", "src_atop", "multiply", "screen", "add").contains(mode)) throw new IllegalArgumentException("不支持 tintMode：" + mode);
                        root = new Group(new Matrix());
                        groups.push(root);
                    } else if (groups.isEmpty() || "vector".equals(tag)
                            || parser.getDepth() != groups.size() + 1) {
                        throw new IllegalArgumentException("标签嵌套有误：" + tag);
                    } else if ("group".equals(tag)) {
                        float px = number(parser, "pivotX", 0), py = number(parser, "pivotY", 0);
                        Matrix matrix = new Matrix();
                        matrix.postTranslate(-px, -py);
                        matrix.postScale(number(parser, "scaleX", 1), number(parser, "scaleY", 1));
                        matrix.postRotate(number(parser, "rotation", 0));
                        matrix.postTranslate(px + number(parser, "translateX", 0), py + number(parser, "translateY", 0));
                        Group group = new Group(matrix);
                        groups.peek().children.add(group);
                        groups.push(group);
                    } else {
                        String data = attr(parser, "pathData", "");
                        if (data.isBlank() || !data.trim().matches("(?s)[Mm].*") || !data.matches("[MmZzLlHhVvCcSsQqTtAa0-9eE+.,\\s-]+")) throw new IllegalArgumentException("pathData 路径命令无效");
                        Path path = PathParser.createPathFromPathData(data);
                        String fillType = attr(parser, "fillType", "nonZero");
                        path.setFillType(switch (fillType) {
                            case "nonZero" -> Path.FillType.WINDING;
                            case "evenOdd" -> Path.FillType.EVEN_ODD;
                            default -> throw new IllegalArgumentException("不支持 fillType：" + fillType);
                        });
                        if ("clip-path".equals(tag)) {
                            groups.peek().children.add(new Clip(path));
                        } else {
                            float start = number(parser, "trimPathStart", 0), end = number(parser, "trimPathEnd", 1);
                            float offset = number(parser, "trimPathOffset", 0);
                            if (start != 0 || end != 1) {
                                PathMeasure measure = new PathMeasure(path, false);
                                float length = measure.getLength();
                                float a = ((start + offset) % 1 + 1) % 1 * length;
                                float b = ((end + offset) % 1 + 1) % 1 * length;
                                Path trimmed = new Path();
                                trimmed.setFillType(path.getFillType());
                                if (a > b) { measure.getSegment(a, length, trimmed, true); measure.getSegment(0, b, trimmed, true); }
                                else measure.getSegment(a, b, trimmed, true);
                                trimmed.rLineTo(0, 0);
                                path = trimmed;
                            }
                            int fill = color(parser, "fillColor", Color.TRANSPARENT);
                            int stroke = color(parser, "strokeColor", Color.TRANSPARENT);
                            float strokeWidth = number(parser, "strokeWidth", 0);
                            float miter = number(parser, "strokeMiterLimit", 4);
                            if (strokeWidth < 0 || miter < 0) throw new IllegalArgumentException("描边宽度及斜接限制不能小于 0");
                            Paint.Cap cap = switch (attr(parser, "strokeLineCap", "butt")) {
                                case "butt" -> Paint.Cap.BUTT;
                                case "round" -> Paint.Cap.ROUND;
                                case "square" -> Paint.Cap.SQUARE;
                                default -> throw new IllegalArgumentException("strokeLineCap 无效");
                            };
                            Paint.Join join = switch (attr(parser, "strokeLineJoin", "miter")) {
                                case "miter" -> Paint.Join.MITER;
                                case "round" -> Paint.Join.ROUND;
                                case "bevel" -> Paint.Join.BEVEL;
                                default -> throw new IllegalArgumentException("strokeLineJoin 无效");
                            };
                            groups.peek().children.add(new Shape(path,
                                    Color.alpha(fill) / 255f * unit(parser, "fillAlpha", 1),
                                    Color.alpha(stroke) / 255f * unit(parser, "strokeAlpha", 1), strokeWidth, cap, join, miter));
                            paths++;
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if ("group".equals(parser.getName()) || "vector".equals(parser.getName())) groups.pop();
                } else if (event == XmlPullParser.TEXT && !parser.isWhitespace()) {
                    throw new IllegalArgumentException("矢量标签中不能包含文本");
                }
            }
            if (root == null || paths == 0) throw new IllegalArgumentException("请提供包含 path 的完整 vector XML");
            RingerVectorXml result = new RingerVectorXml(root, width, height, alpha, xml);
            CACHE.put(cacheKey, result);
            return result;
        } catch (XmlPullParserException | IOException error) {
            throw new IllegalArgumentException("XML 格式错误：" + error.getMessage(), error);
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException) throw error;
            throw new IllegalArgumentException("路径或属性格式错误：" + error.getMessage(), error);
        }
    }

    private static String attr(XmlPullParser parser, String name, String fallback) {
        String value = parser.getAttributeValue(ANDROID, name);
        return value == null ? fallback : value.trim();
    }

    private static float number(XmlPullParser parser, String name, float fallback) {
        String raw = attr(parser, name, Float.toString(fallback));
        if (raw.startsWith("@") || raw.startsWith("?")) return fallback;
        float value = Float.parseFloat(raw);
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " 必须为有限数值");
        return value;
    }

    private static float unit(XmlPullParser parser, String name, float fallback) {
        float value = number(parser, name, fallback);
        if (value < 0 || value > 1) throw new IllegalArgumentException(name + " 需在 0 到 1 之间");
        return value;
    }

    private static int color(XmlPullParser parser, String name, int fallback) {
        String value = attr(parser, name, "");
        if (value.isEmpty()) return fallback;
        if (value.startsWith("@") || value.startsWith("?")) return fallback;
        if (!value.matches("#[0-9a-fA-F]{3,8}")) throw new IllegalArgumentException(name + " 需填写 #RGB、#ARGB、#RRGGBB 或 #AARRGGBB");
        if (value.length() == 4 || value.length() == 5) {
            StringBuilder expanded = new StringBuilder("#");
            for (int i = 1; i < value.length(); i++) expanded.append(value.charAt(i)).append(value.charAt(i));
            value = expanded.toString();
        }
        return Color.parseColor(value);
    }

    private static void validateNativeXml(String xml) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(new StringReader(xml));
            String root = null;
            for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG && root == null) root = parser.getName();
                if (event == XmlPullParser.TEXT && !parser.isWhitespace()) {
                    throw new IllegalArgumentException("矢量标签中不能包含文本");
                }
            }
            if (!"vector".equals(root) && !"animated-vector".equals(root)) {
                throw new IllegalArgumentException("根标签必须是 vector 或 animated-vector");
            }
        } catch (XmlPullParserException | IOException error) {
            throw new IllegalArgumentException("XML 格式错误：" + error.getMessage(), error);
        }
    }

    private interface Node {
        void draw(Canvas canvas, Paint paint, float alpha);
        Path outline();
    }
    private record Group(Matrix matrix, List<Node> children) implements Node {
        Group(Matrix matrix) { this(matrix, new ArrayList<>()); }
        public void draw(Canvas canvas, Paint paint, float alpha) {
            int save = canvas.save();
            canvas.concat(matrix);
            for (Node node : children) node.draw(canvas, paint, alpha);
            canvas.restoreToCount(save);
        }
        public Path outline() {
            Path result = new Path();
            Path clip = null;
            for (Node node : children) {
                Path path = node.outline();
                if (node instanceof Clip) {
                    if (clip == null) clip = path;
                    else clip.op(path, Path.Op.INTERSECT);
                } else {
                    if (clip != null) path.op(clip, Path.Op.INTERSECT);
                    result.op(path, Path.Op.UNION);
                }
            }
            result.transform(matrix);
            return result;
        }
    }
    private record Clip(Path path) implements Node {
        public void draw(Canvas canvas, Paint paint, float alpha) { canvas.clipPath(path); }
        public Path outline() { return new Path(path); }
    }
    private record Shape(Path path, float fill, float stroke, float strokeWidth,
                         Paint.Cap cap, Paint.Join join, float miter) implements Node {
        public void draw(Canvas canvas, Paint paint, float alpha) {
            if (fill > 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(Math.round(255 * alpha * fill));
                canvas.drawPath(path, paint);
            }
            if (stroke > 0 && strokeWidth > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setStrokeCap(cap);
                paint.setStrokeJoin(join);
                paint.setStrokeMiter(miter);
                paint.setAlpha(Math.round(255 * alpha * stroke));
                canvas.drawPath(path, paint);
            }
        }
        public Path outline() {
            Path result = fill > 0 ? new Path(path) : new Path();
            if (stroke > 0 && strokeWidth > 0) {
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setStrokeCap(cap);
                paint.setStrokeJoin(join);
                paint.setStrokeMiter(miter);
                Path border = new Path();
                paint.getFillPath(path, border);
                result.op(border, Path.Op.UNION);
            }
            return result;
        }
    }

    public float getViewportWidth() { return width; }

    public float getViewportHeight() { return height; }

    RectF iconDrawingBounds() {
        if (root == null) return null;
        if (iconDrawingBounds == null) {
            iconDrawingBounds = new RectF();
            unionDrawingBounds(iconDrawingBounds);
            if (!iconDrawingBounds.isEmpty()) {
                IconMetrics.VisualCanvas visual = IconMetrics.resolveCenteredVisualCanvas(
                        new RectF(0, 0, 1, 1), width / height, null);
                iconDrawingBounds.set(
                        visual.rect.left + iconDrawingBounds.left * visual.rect.width() / width,
                        visual.rect.top + iconDrawingBounds.top * visual.rect.height() / height,
                        visual.rect.left + iconDrawingBounds.right * visual.rect.width() / width,
                        visual.rect.top + iconDrawingBounds.bottom * visual.rect.height() / height);
            }
        }
        return iconDrawingBounds.isEmpty() ? null : iconDrawingBounds;
    }

    public void unionDrawingBounds(RectF bounds) {
        if (root == null) {
            bounds.union(0, 0, width, height);
            return;
        }
        if (alpha == 0) return;
        Path outline = root.outline();
        Path viewport = new Path();
        viewport.addRect(0, 0, width, height, Path.Direction.CW);
        outline.op(viewport, Path.Op.INTERSECT);
        RectF content = new RectF();
        outline.computeBounds(content, true);
        bounds.union(content);
    }

    public void drawRaw(Canvas canvas, Paint paint, int color) {
        if (root == null) return;
        paint.setColorFilter(null);
        paint.setColor(color);
        int save = canvas.save();
        canvas.clipRect(0, 0, width, height);
        root.draw(canvas, paint, alpha * Color.alpha(color) / 255f);
        canvas.restoreToCount(save);
    }

    public Drawable newDrawable(Context context) {
        if (root == null) {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                parser.setInput(new StringReader(resolveResourceIds(xml, context)));
                int event;
                do {
                    event = parser.next();
                } while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT);
                Drawable drawable = switch (parser.getName()) {
                    case "animated-vector" -> AnimatedVectorDrawableCompat.createFromXmlInner(
                            context, context.getResources(), parser, Xml.asAttributeSet(parser), null);
                    case "vector" -> VectorDrawableCompat.createFromXmlInner(
                            context.getResources(), parser, Xml.asAttributeSet(parser), null);
                    default -> throw new IllegalArgumentException("根标签必须是 vector 或 animated-vector");
                };
                return new NativeIcon(drawable, IconMetrics.resolveSignalBoxHeight(context, 1f));
            } catch (XmlPullParserException | IOException | RuntimeException error) {
                throw new IllegalArgumentException("系统无法解析矢量资源：" + error.getMessage(), error);
            }
        }
        return new VectorIcon(IconMetrics.resolveSignalBoxHeight(context, 1f));
    }

    private static String resolveResourceIds(String xml, Context context) throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader(xml));
        XmlSerializer serializer = Xml.newSerializer();
        StringWriter output = new StringWriter();
        serializer.setOutput(output);
        serializer.startDocument(null, null);
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            switch (event) {
                case XmlPullParser.START_TAG -> {
                    for (int i = 0; i < parser.getNamespaceCount(parser.getDepth()); i++) {
                        serializer.setPrefix(parser.getNamespacePrefix(i), parser.getNamespaceUri(i));
                    }
                    serializer.startTag(parser.getNamespace(), parser.getName());
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String value = parser.getAttributeValue(i);
                        if (value.startsWith("@") || value.startsWith("?")) {
                            String reference = value.substring(1);
                            String packageName = context.getPackageName();
                            int colon = reference.indexOf(':');
                            if (colon > 0) {
                                packageName = reference.substring(0, colon);
                                reference = reference.substring(colon + 1);
                            }
                            int slash = reference.indexOf('/');
                            if (slash > 0) {
                                int id = context.getResources().getIdentifier(reference.substring(slash + 1),
                                        reference.substring(0, slash), packageName);
                                if (id == 0) throw new IllegalArgumentException("找不到资源引用：" + value);
                                value = (value.startsWith("?") ? "?" : "@")
                                        + "0x" + Integer.toHexString(id);
                            }
                        }
                        serializer.attribute(parser.getAttributeNamespace(i), parser.getAttributeName(i), value);
                    }
                }
                case XmlPullParser.END_TAG -> serializer.endTag(parser.getNamespace(), parser.getName());
                case XmlPullParser.TEXT -> serializer.text(parser.getText());
                case XmlPullParser.CDSECT -> serializer.cdsect(parser.getText());
                case XmlPullParser.COMMENT -> serializer.comment(parser.getText());
                case XmlPullParser.PROCESSING_INSTRUCTION -> serializer.processingInstruction(parser.getText());
                case XmlPullParser.DOCDECL -> serializer.docdecl(parser.getText());
                default -> { }
            }
        }
        serializer.endDocument();
        return output.toString();
    }

    static final class NativeIcon extends Drawable implements Animatable, Drawable.Callback {
        private final Drawable delegate;
        private final int size;

        NativeIcon(Drawable delegate, int size) {
            this.delegate = delegate.mutate();
            this.size = size;
            this.delegate.setCallback(this);
        }

        @Override public void draw(Canvas canvas) {
            delegate.setBounds(getBounds());
            delegate.draw(canvas);
        }
        @Override protected void onBoundsChange(android.graphics.Rect bounds) { delegate.setBounds(bounds); }
        @Override public void setAlpha(int alpha) { delegate.setAlpha(alpha); invalidateSelf(); }
        @Override public int getAlpha() { return delegate.getAlpha(); }
        @Override public void setColorFilter(ColorFilter filter) { delegate.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return delegate.getOpacity(); }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public boolean isStateful() { return delegate.isStateful(); }
        @Override protected boolean onStateChange(int[] state) { boolean changed = delegate.setState(state); if (changed) invalidateSelf(); return changed; }
        @Override public void setTintList(ColorStateList tint) { delegate.setTintList(tint); invalidateSelf(); }
        @Override public void setTintMode(PorterDuff.Mode mode) { delegate.setTintMode(mode); invalidateSelf(); }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart) | delegate.setVisible(visible, restart);
            if (delegate instanceof Animatable animation) {
                if (visible && (restart || !animation.isRunning())) animation.start();
                else if (!visible) animation.stop();
            }
            return changed;
        }
        @Override public void start() { if (delegate instanceof Animatable animation) animation.start(); }
        @Override public void stop() { if (delegate instanceof Animatable animation) animation.stop(); }
        @Override public boolean isRunning() { return delegate instanceof Animatable animation && animation.isRunning(); }
        @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
    }

    final class VectorIcon extends Drawable {
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final IconMetrics.VisualCanvas visual = new IconMetrics.VisualCanvas();
        private int drawableAlpha = 255;
        private ColorFilter filter;
        private ColorStateList tint = ColorStateList.valueOf(Color.WHITE);
        private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
        VectorIcon(int size) { this.size = size; }
        @Override public void draw(Canvas canvas) {
            IconMetrics.resolveCenteredVisualCanvas(getBounds(), width / height, visual);
            if (visual.isEmpty()) return;
            paint.setColor(Color.WHITE);
            paint.setColorFilter(filter != null ? filter : new PorterDuffColorFilter(
                    tint.getColorForState(getState(), tint.getDefaultColor()), tintMode));
            int save = canvas.save();
            canvas.translate(visual.rect.left, visual.rect.top);
            canvas.scale(visual.rect.width() / width, visual.rect.height() / height);
            canvas.clipRect(0, 0, width, height);
            root.draw(canvas, paint, alpha * drawableAlpha / 255f);
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int value) { drawableAlpha = value; invalidateSelf(); }
        @Override public int getAlpha() { return drawableAlpha; }
        @Override public void setColorFilter(ColorFilter value) { filter = value; invalidateSelf(); }
        @Override public void setTintList(ColorStateList value) { tint = value == null ? ColorStateList.valueOf(Color.WHITE) : value; invalidateSelf(); }
        @Override public void setTintMode(PorterDuff.Mode value) { tintMode = value; invalidateSelf(); }
        @Override public boolean isStateful() { return tint.isStateful(); }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
    }
}
