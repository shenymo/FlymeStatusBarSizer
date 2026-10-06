package com.example.flymestatusbarsizer.feature.battery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.statusbar.RightIconGroupPreviewView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class OneUiBatteryWidthTest {
    @Test
    public void bodyStaysInsideMeasuredWidthAcrossAppearanceAndChargingStates() {
        for (int size : new int[]{11, 22, 44, 66, 132}) {
            for (int widthPercent : new int[]{50, 75, 100, 101, 125, 150}) {
                for (int powerState = 0; powerState < 3; powerState++) {
                    for (boolean hollow : new boolean[]{false, true}) {
                        for (boolean followsLevel : new boolean[]{false, true}) {
                            int width = OneUiBatteryPainter.getRequiredWidth(size,
                                    powerState != 0, widthPercent);
                            Rect bounds = new Rect(7, 13, 7 + width, 13 + size);
                            RecordingCanvas canvas = new RecordingCanvas();
                            OneUiBatteryPainter.draw(canvas, bounds, 82, powerState != 0,
                                    powerState == 2, powerState == 2,
                                    Color.BLACK, Color.WHITE, true, 1f, Typeface.DEFAULT,
                                    0f, 0f, 0f, hollow, followsLevel, widthPercent, 100, 100);
                            assertFalse(canvas.batteries.isEmpty());
                            RectF body = canvas.batteries.get(0);
                            String scenario = "size=" + size + ", width=" + widthPercent
                                    + ", power=" + powerState + ", hollow=" + hollow;
                            assertTrue(scenario + ": body overlaps preceding icon",
                                    body.left >= bounds.left);
                            assertTrue(scenario + ": body exceeds measured width",
                                    body.right <= bounds.right);
                            assertEquals(bounds.exactCenterY(), body.centerY(), 0.001f);
                            assertEquals(body.centerX(), canvas.levelTextX, 0.001f);
                            if (widthPercent >= 100) {
                                assertEquals(scenario, bounds.left, body.left, 0.001f);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void previewMovesSignalLeftAsBatteryWidensAndPreservesGap() {
        RightIconGroupPreviewView preview = new RightIconGroupPreviewView(
                RuntimeEnvironment.getApplication());
        preview.setBatteryStyle(SettingsStore.BATTERY_STYLE_ONEUI);
        preview.layout(0, 0, 720, 360);
        RecordingCanvas normal = new RecordingCanvas();
        preview.draw(normal);
        preview.setBatteryBodyWidthPercent(150);
        RecordingCanvas wide = new RecordingCanvas();
        preview.draw(wide);

        assertEquals(2, normal.batteries.size());
        assertEquals(2, wide.batteries.size());
        assertEquals(2, wide.badgeRightEdges.size());
        for (int row = 0; row < 2; row++) {
            RectF normalBody = normal.batteries.get(row);
            RectF wideBody = wide.batteries.get(row);
            assertTrue(wideBody.width() > normalBody.width());
            assertTrue("wider battery must move adjacent signal left",
                    wide.badgeRightEdges.get(row) < normal.badgeRightEdges.get(row));
            assertEquals("signal-to-battery gap must remain stable",
                    normalBody.left - normal.badgeRightEdges.get(row),
                    wideBody.left - wide.badgeRightEdges.get(row), 0.001f);
            assertTrue("wider battery must fit before the original right edge",
                    wideBody.right <= normalBody.right + 0.001f);
        }
    }

    private static final class RecordingCanvas extends Canvas {
        final List<RectF> batteries = new ArrayList<>();
        final List<Float> badgeRightEdges = new ArrayList<>();
        RectF lastRoundRect;
        float levelTextX;

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            lastRoundRect = new RectF(rect);
        }

        @Override
        public void drawText(String text, float x, float y, Paint paint) {
            if ("82".equals(text)) {
                batteries.add(new RectF(lastRoundRect));
                levelTextX = x;
            } else if ("5G".equals(text)) {
                badgeRightEdges.add(x + paint.measureText(text));
            }
        }
    }
}
