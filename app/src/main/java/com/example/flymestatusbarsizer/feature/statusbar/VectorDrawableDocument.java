package com.example.flymestatusbarsizer.feature.statusbar;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Editable representation of the static Vector XML subset rendered by RingerVectorXml. */
public final class VectorDrawableDocument {
    public static final String ANDROID = "http://schemas.android.com/apk/res/android";

    public enum Kind { TEXT, DIMENSION, NUMBER, COLOR, BOOLEAN, ENUM }

    public record AttributeSpec(String name, String label, Kind kind, String defaultValue,
                                String[] choices) {
        public AttributeSpec(String name, String label, Kind kind, String defaultValue) {
            this(name, label, kind, defaultValue, new String[0]);
        }
    }

    private static final Map<String, List<AttributeSpec>> SPECS = specs();
    private static final String[] TAGS = {"vector", "group", "path", "clip-path"};
    private final Node root;

    private VectorDrawableDocument(Node root) {
        this.root = root;
    }

    public Node root() {
        return root;
    }

    public static List<AttributeSpec> specsFor(String tag) {
        return SPECS.getOrDefault(tag, List.of());
    }

    public static VectorDrawableDocument parse(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入 Android Vector XML");
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(new StringReader(xml));
            ArrayDeque<Node> stack = new ArrayDeque<>();
            Node root = null;
            int paths = 0;
            for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT;
                    event = parser.next()) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (!isSupportedTag(tag)) {
                        throw new IllegalArgumentException("不支持标签：" + tag);
                    }
                    if (parser.getDepth() == 1 && root != null) {
                        throw new IllegalArgumentException("XML 只能有一个根标签");
                    }
                    if (parser.getDepth() > 1 && root == null) {
                        throw new IllegalArgumentException("根标签必须是 vector");
                    }
                    if (root == null && !"vector".equals(tag)) {
                        throw new IllegalArgumentException("根标签必须是 vector");
                    }
                    Node node = new Node(tag);
                    List<AttributeSpec> allowed = specsFor(tag);
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String name = parser.getAttributeName(i);
                        if (!ANDROID.equals(parser.getAttributeNamespace(i))
                                || allowed.stream().noneMatch(spec -> spec.name().equals(name))) {
                            throw new IllegalArgumentException("不支持属性：" + name);
                        }
                        String value = parser.getAttributeValue(i).trim();
                        if (value.startsWith("@") || value.startsWith("?")) {
                            throw new IllegalArgumentException(name + " 不能引用外部资源，请直接填写数值");
                        }
                        node.attributes.put(name, value);
                    }
                    if (root == null) {
                        root = node;
                    } else {
                        if (stack.isEmpty() || "path".equals(stack.peek().tag)
                                || "clip-path".equals(stack.peek().tag)) {
                            throw new IllegalArgumentException("path 不能包含子标签");
                        }
                        stack.peek().children.add(node);
                    }
                    stack.push(node);
                    if ("path".equals(tag) || "clip-path".equals(tag)) paths++;
                } else if (event == XmlPullParser.END_TAG) {
                    if (stack.isEmpty() || !stack.pop().tag.equals(parser.getName())) {
                        throw new IllegalArgumentException("标签嵌套有误：" + parser.getName());
                    }
                } else if (event == XmlPullParser.TEXT && !parser.isWhitespace()) {
                    throw new IllegalArgumentException("矢量标签中不能包含文本");
                }
            }
            if (root == null || !stack.isEmpty() || paths == 0) {
                throw new IllegalArgumentException("请提供包含 path 的完整 vector XML");
            }
            if (!"vector".equals(root.tag)) {
                throw new IllegalArgumentException("根标签必须是 vector");
            }
            VectorDrawableDocument document = new VectorDrawableDocument(root);
            RingerVectorXml.parse(document.toXml());
            return document;
        } catch (XmlPullParserException | IOException error) {
            throw new IllegalArgumentException("XML 格式错误：" + error.getMessage(), error);
        }
    }

    public String toXml() {
        StringBuilder result = new StringBuilder();
        appendNode(result, root, 0, true);
        return result.toString();
    }

    private static void appendNode(StringBuilder out, Node node, int depth, boolean root) {
        indent(out, depth).append('<').append(node.tag);
        if (root) out.append(" xmlns:android=\"").append(ANDROID).append('"');
        for (Map.Entry<String, String> attribute : node.attributes.entrySet()) {
            out.append(" android:").append(attribute.getKey()).append("=\"")
                    .append(escape(attribute.getValue())).append('"');
        }
        if (node.children.isEmpty()) {
            out.append(" />\n");
            return;
        }
        out.append(">\n");
        for (Node child : node.children) appendNode(out, child, depth + 1, false);
        indent(out, depth).append("</").append(node.tag).append(">\n");
    }

    private static StringBuilder indent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) out.append("    ");
        return out;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean isSupportedTag(String tag) {
        for (String item : TAGS) if (item.equals(tag)) return true;
        return false;
    }

    private static Map<String, List<AttributeSpec>> specs() {
        LinkedHashMap<String, List<AttributeSpec>> result = new LinkedHashMap<>();
        result.put("vector", List.of(
                new AttributeSpec("name", "名称", Kind.TEXT, "自定义图标"),
                new AttributeSpec("width", "显示宽度", Kind.DIMENSION, "24dp"),
                new AttributeSpec("height", "显示高度", Kind.DIMENSION, "24dp"),
                new AttributeSpec("viewportWidth", "坐标宽度", Kind.NUMBER, "24"),
                new AttributeSpec("viewportHeight", "坐标高度", Kind.NUMBER, "24"),
                new AttributeSpec("alpha", "整体透明度", Kind.NUMBER, "1"),
                new AttributeSpec("tint", "整体颜色", Kind.COLOR, "#FFFFFFFF"),
                new AttributeSpec("tintMode", "颜色混合模式", Kind.ENUM, "src_in",
                        new String[]{"src_in", "src_over", "src_atop", "multiply", "screen", "add"}),
                new AttributeSpec("autoMirrored", "自动镜像", Kind.BOOLEAN, "false")));
        result.put("group", List.of(
                new AttributeSpec("name", "名称", Kind.TEXT, "图层组"),
                new AttributeSpec("rotation", "旋转角度", Kind.NUMBER, "0"),
                new AttributeSpec("pivotX", "旋转中心 X", Kind.NUMBER, "0"),
                new AttributeSpec("pivotY", "旋转中心 Y", Kind.NUMBER, "0"),
                new AttributeSpec("scaleX", "水平缩放", Kind.NUMBER, "1"),
                new AttributeSpec("scaleY", "垂直缩放", Kind.NUMBER, "1"),
                new AttributeSpec("translateX", "水平位移", Kind.NUMBER, "0"),
                new AttributeSpec("translateY", "垂直位移", Kind.NUMBER, "0")));
        result.put("path", pathSpecs());
        result.put("clip-path", List.of(
                new AttributeSpec("name", "名称", Kind.TEXT, "裁剪区域"),
                new AttributeSpec("pathData", "路径数据", Kind.TEXT, "M0,0L1,0L1,1Z"),
                new AttributeSpec("fillType", "填充规则", Kind.ENUM, "nonZero",
                        new String[]{"nonZero", "evenOdd"})));
        return Collections.unmodifiableMap(result);
    }

    private static List<AttributeSpec> pathSpecs() {
        return List.of(
                new AttributeSpec("name", "名称", Kind.TEXT, "形状"),
                new AttributeSpec("pathData", "路径数据", Kind.TEXT, "M0,0L1,0L1,1Z"),
                new AttributeSpec("fillColor", "填充颜色", Kind.COLOR, "#FFFFFFFF"),
                new AttributeSpec("fillAlpha", "填充透明度", Kind.NUMBER, "1"),
                new AttributeSpec("fillType", "填充规则", Kind.ENUM, "nonZero",
                        new String[]{"nonZero", "evenOdd"}),
                new AttributeSpec("strokeColor", "描边颜色", Kind.COLOR, "#00000000"),
                new AttributeSpec("strokeAlpha", "描边透明度", Kind.NUMBER, "1"),
                new AttributeSpec("strokeWidth", "描边宽度", Kind.NUMBER, "0"),
                new AttributeSpec("strokeLineCap", "线头样式", Kind.ENUM, "butt",
                        new String[]{"butt", "round", "square"}),
                new AttributeSpec("strokeLineJoin", "拐角样式", Kind.ENUM, "miter",
                        new String[]{"miter", "round", "bevel"}),
                new AttributeSpec("strokeMiterLimit", "斜接限制", Kind.NUMBER, "4"),
                new AttributeSpec("trimPathStart", "裁剪起点", Kind.NUMBER, "0"),
                new AttributeSpec("trimPathEnd", "裁剪终点", Kind.NUMBER, "1"),
                new AttributeSpec("trimPathOffset", "裁剪偏移", Kind.NUMBER, "0"));
    }

    public static final class Node {
        private final String tag;
        private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        private final ArrayList<Node> children = new ArrayList<>();

        private Node(String tag) {
            this.tag = tag;
        }

        public String tag() { return tag; }
        public List<Node> children() { return Collections.unmodifiableList(children); }
        public String get(String name) { return attributes.get(name); }
        public boolean has(String name) { return attributes.containsKey(name); }
        public void set(String name, String value) { attributes.put(name, value == null ? "" : value); }
        public void remove(String name) { attributes.remove(name); }
        public List<String> attributeNames() { return List.copyOf(attributes.keySet()); }
    }
}
