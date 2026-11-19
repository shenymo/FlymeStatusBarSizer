package com.example.flymestatusbarsizer.feature.battery;

import android.graphics.RectF;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class BatteryIconPack {
    public enum Direction { LEFT_TO_RIGHT, RIGHT_TO_LEFT, BOTTOM_TO_TOP, TOP_TO_BOTTOM }

    public final String name, json;
    public final float width, height, textSize, textOutline, textAlpha, textOffsetX, textOffsetY;
    public final RectF visualBounds, bodyBounds, fillBounds;
    public final Direction direction;
    public final RingerVectorXml shell, fill, background, charging, quickCharging;

    private BatteryIconPack(JSONObject object) throws JSONException {
        int version = object.getInt("version");
        if (version != 1 && version != 2) {
            throw new IllegalArgumentException("无法识别此电池图标包版本，请重新导出或选择兼容的图标包。");
        }
        name = object.getString("name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("请输入图标包名称");
        shell = RingerVectorXml.parse(object.getString("shellXml"));
        width = shell.getViewportWidth();
        height = shell.getViewportHeight();
        fill = vector(object, "fillXml", true);
        background = vector(object, "backgroundXml", false);
        charging = vector(object, "chargingXml", false);
        RingerVectorXml quick = vector(object, "quickChargingXml", false);
        quickCharging = quick == null ? charging : quick;
        fillBounds = new RectF();
        fill.unionDrawingBounds(fillBounds);
        if (fillBounds.isEmpty()) {
            throw new IllegalArgumentException("电芯图层没有可见图形。请检查是否包含路径，并确认图形位于画布范围内。");
        }
        RectF content = new RectF(fillBounds);
        if (background != null) background.unionDrawingBounds(content);
        shell.unionDrawingBounds(content);
        visualBounds = new RectF(content);
        if (charging != null) charging.unionDrawingBounds(visualBounds);
        if (quick != null) quick.unionDrawingBounds(visualBounds);
        bodyBounds = new RectF(content.left, visualBounds.top, content.right, visualBounds.bottom);
        if (version == 1) {
            JSONArray values = object.getJSONArray("textBounds");
            if (values.length() != 4) {
                throw new IllegalArgumentException("旧版图标包的数字区域数据不完整，请重新导出该图标包。");
            }
            float[] bounds = new float[4];
            for (int i = 0; i < 4; i++) {
                bounds[i] = (float) values.getDouble(i);
                if (!Float.isFinite(bounds[i])) {
                    throw new IllegalArgumentException("旧版图标包的数字区域坐标无效，请重新导出该图标包。");
                }
            }
            RectF textBounds = new RectF(bounds[0], bounds[1], bounds[2], bounds[3]);
            if (textBounds.isEmpty() || textBounds.left < 0 || textBounds.top < 0
                    || textBounds.right > width || textBounds.bottom > height) {
                throw new IllegalArgumentException("旧版图标包的数字显示区域必须位于画布内，且宽度和高度都大于 0。");
            }
            textOffsetX = textBounds.centerX() - fillBounds.centerX();
            textOffsetY = textBounds.centerY() - fillBounds.centerY();
        } else {
            textOffsetX = object.has("textOffsetX") ? (float) object.getDouble("textOffsetX") : 0;
            textOffsetY = object.has("textOffsetY") ? (float) object.getDouble("textOffsetY") : 0;
            if (!Float.isFinite(textOffsetX) || !Float.isFinite(textOffsetY)) {
                throw new IllegalArgumentException("数字水平和垂直偏移必须填写有效数字。");
            }
        }
        direction = switch (object.getString("fillDirection")) {
            case "left_to_right" -> Direction.LEFT_TO_RIGHT;
            case "right_to_left" -> Direction.RIGHT_TO_LEFT;
            case "bottom_to_top" -> Direction.BOTTOM_TO_TOP;
            case "top_to_bottom" -> Direction.TOP_TO_BOTTOM;
            default -> throw new IllegalArgumentException("电量填充方向无法识别，请重新选择填充方向。");
        };
        textSize = positive(object, "textSize");
        textOutline = (float) object.optDouble("textOutline", 0);
        textAlpha = (float) object.optDouble("textAlpha", 1);
        if (!Float.isFinite(textOutline) || textOutline < 0
                || !Float.isFinite(textAlpha) || textAlpha < 0 || textAlpha > 1) {
            if (!Float.isFinite(textOutline) || textOutline < 0) {
                throw new IllegalArgumentException("数字描边宽度不能小于 0。");
            }
            throw new IllegalArgumentException("数字透明度需填写 0 到 1 之间的数值。");
        }
        object.remove("width");
        object.remove("height");
        object.remove("visualBounds");
        object.remove("fillBounds");
        object.remove("textBounds");
        json = object.put("version", 2).put("textOffsetX", textOffsetX).put("textOffsetY", textOffsetY).toString();
    }

    public RectF layoutBounds(boolean showBolt) {
        return showBolt && (charging != null || quickCharging != null) ? visualBounds : bodyBounds;
    }

    public static BatteryIconPack parse(String raw) {
        try {
            return new BatteryIconPack(new JSONObject(raw));
        } catch (JSONException invalid) {
            throw new IllegalArgumentException("图标包 JSON 格式错误：" + invalid.getMessage(), invalid);
        }
    }

    private static float positive(JSONObject object, String key) throws JSONException {
        float value = (float) object.getDouble(key);
        if (!Float.isFinite(value) || value <= 0) {
            String label = key.equals("textSize") ? "数字字号" : key;
            throw new IllegalArgumentException(label + "需填写大于 0 的数值。");
        }
        return value;
    }

    private RingerVectorXml vector(JSONObject object, String key, boolean required) throws JSONException {
        String xml = required ? object.getString(key) : object.optString(key, "");
        if (!required && xml.isBlank()) return null;
        RingerVectorXml vector = RingerVectorXml.parse(xml);
        if (vector.getViewportWidth() != width || vector.getViewportHeight() != height) {
            String layerName = switch (key) {
                case "fillXml" -> "电芯";
                case "backgroundXml" -> "空电量背景";
                case "chargingXml" -> "充电";
                case "quickChargingXml" -> "快充";
                default -> key;
            };
            throw new IllegalArgumentException(layerName + "图层的画布尺寸与外壳图层不一致。"
                    + "所有图层的 viewportWidth 和 viewportHeight 都必须与外壳图层相同。"
                    + "若要加宽图形，请保留画布数值并横向缩放图形；若要修改画布，请同步修改所有图层。");
        }
        return vector;
    }
}
