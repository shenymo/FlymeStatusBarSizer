package com.example.flymestatusbarsizer.feature.battery;

import android.graphics.RectF;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class BatteryIconPack {
    public enum Direction { LEFT_TO_RIGHT, RIGHT_TO_LEFT, BOTTOM_TO_TOP, TOP_TO_BOTTOM }

    public final String name, json;
    public final float width, height, textSize, textOutline, textAlpha;
    public final RectF visualBounds, bodyBounds, fillBounds, textBounds;
    public final Direction direction;
    public final RingerVectorXml shell, fill, background, charging, quickCharging;

    private BatteryIconPack(JSONObject object) throws JSONException {
        if (object.getInt("version") != 1) throw new IllegalArgumentException("不支持的图标包版本");
        name = object.getString("name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("请输入图标包名称");
        width = positive(object, "width");
        height = positive(object, "height");
        visualBounds = rect(object, "visualBounds");
        fillBounds = rect(object, "fillBounds");
        textBounds = rect(object, "textBounds");
        if (!visualBounds.contains(fillBounds) || !visualBounds.contains(textBounds)) {
            throw new IllegalArgumentException("电芯和文字区域必须位于有效显示区域内");
        }
        direction = switch (object.getString("fillDirection")) {
            case "left_to_right" -> Direction.LEFT_TO_RIGHT;
            case "right_to_left" -> Direction.RIGHT_TO_LEFT;
            case "bottom_to_top" -> Direction.BOTTOM_TO_TOP;
            case "top_to_bottom" -> Direction.TOP_TO_BOTTOM;
            default -> throw new IllegalArgumentException("填充方向无效");
        };
        textSize = positive(object, "textSize");
        textOutline = (float) object.optDouble("textOutline", 0);
        textAlpha = (float) object.optDouble("textAlpha", 1);
        if (!Float.isFinite(textOutline) || textOutline < 0
                || !Float.isFinite(textAlpha) || textAlpha < 0 || textAlpha > 1) {
            throw new IllegalArgumentException("文字描边或透明度无效");
        }
        shell = vector(object, "shellXml", true);
        fill = vector(object, "fillXml", true);
        background = vector(object, "backgroundXml", false);
        charging = vector(object, "chargingXml", false);
        RingerVectorXml quick = vector(object, "quickChargingXml", false);
        quickCharging = quick == null ? charging : quick;
        RectF content = new RectF();
        fill.unionDrawingBounds(content);
        if (background != null) background.unionDrawingBounds(content);
        if (!content.intersect(fillBounds)) content.setEmpty();
        content.union(textBounds);
        shell.unionDrawingBounds(content);
        bodyBounds = new RectF(Math.max(visualBounds.left, content.left), visualBounds.top,
                Math.min(visualBounds.right, content.right), visualBounds.bottom);
        json = object.toString();
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
        if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException(key + " 必须大于 0");
        return value;
    }

    private RectF rect(JSONObject object, String key) throws JSONException {
        JSONArray values = object.getJSONArray(key);
        if (values.length() != 4) throw new IllegalArgumentException(key + " 需要左、上、右、下四个坐标");
        float[] bounds = new float[4];
        for (int i = 0; i < 4; i++) {
            bounds[i] = (float) values.getDouble(i);
            if (!Float.isFinite(bounds[i])) throw new IllegalArgumentException(key + " 坐标无效");
        }
        RectF rect = new RectF(bounds[0], bounds[1], bounds[2], bounds[3]);
        if (rect.isEmpty() || rect.left < 0 || rect.top < 0 || rect.right > width || rect.bottom > height) {
            throw new IllegalArgumentException(key + " 必须位于画布内，且宽高大于 0");
        }
        return rect;
    }

    private RingerVectorXml vector(JSONObject object, String key, boolean required) throws JSONException {
        String xml = required ? object.getString(key) : object.optString(key, "");
        if (!required && xml.isBlank()) return null;
        RingerVectorXml vector = RingerVectorXml.parse(xml);
        if (vector.getViewportWidth() != width || vector.getViewportHeight() != height) {
            throw new IllegalArgumentException(key + " 的 viewport 必须与图标包画布一致");
        }
        return vector;
    }
}
