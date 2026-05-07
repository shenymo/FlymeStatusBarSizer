package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class BatteryBoltPainter {
    private static final String BOLT_PATH_DATA =
            "M520,840L560,560L319,560L640,120L680,120L640,400L881,400L560,840L520,840Z"
                    + "M120,720L120,640L468,640L456,720L120,720Z"
                    + "M80,520L80,440L308,440L250,520L80,520Z"
                    + "M160,320L160,240L454,240L396,320L160,320Z";
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path SOURCE_PATH = SimplePathDataParser.parse(BOLT_PATH_DATA);
    private static final Path PATH = new Path();
    private static final Matrix MATRIX = new Matrix();
    private static final RectF SOURCE_BOUNDS = new RectF();
    private static final float MAX_ICON_AREA_FILL_RATIO = 1f;
    private static final float TARGET_BOLT_HEIGHT_RATIO = 0.86f;

    static {
        PAINT.setStyle(Paint.Style.FILL);
        if (SOURCE_PATH != null) {
            SOURCE_PATH.computeBounds(SOURCE_BOUNDS, true);
        }
    }

    private BatteryBoltPainter() {
    }

    static void draw(Canvas canvas, RectF area, float bodyWidth, float bodyHeight,
            int color, float widthRatio, float contentScale) {
        drawInternal(canvas, area, bodyWidth, bodyHeight, widthRatio, contentScale, PAINT, color);
    }

    static void drawCutout(Canvas canvas, RectF area, float bodyWidth, float bodyHeight,
            float widthRatio, float contentScale, Paint paint) {
        drawInternal(canvas, area, bodyWidth, bodyHeight, widthRatio, contentScale, paint, 0);
    }

    private static void drawInternal(Canvas canvas, RectF area, float bodyWidth, float bodyHeight,
            float widthRatio, float contentScale, Paint paint, int color) {
        if (canvas == null || area == null) {
            return;
        }
        if (paint == null) {
            return;
        }
        if (paint == PAINT) {
            PAINT.setColor(color);
        }
        float resolvedScale = normalizeContentScale(contentScale);
        float desiredWidth = Math.max(0f, bodyWidth) * Math.max(0.1f, widthRatio) * resolvedScale;
        float desiredHeight = Math.max(0f, bodyHeight) * TARGET_BOLT_HEIGHT_RATIO * resolvedScale;
        float iconWidth = Math.min(area.width() * MAX_ICON_AREA_FILL_RATIO, desiredWidth);
        float iconHeight = Math.min(area.height() * MAX_ICON_AREA_FILL_RATIO, desiredHeight);
        if (iconWidth <= 0f || iconHeight <= 0f) {
            return;
        }
        if (SOURCE_PATH == null || SOURCE_BOUNDS.isEmpty()) {
            return;
        }
        float scale = Math.min(iconWidth / SOURCE_BOUNDS.width(), iconHeight / SOURCE_BOUNDS.height());
        if (scale <= 0f) {
            return;
        }
        float translatedWidth = SOURCE_BOUNDS.width() * scale;
        float translatedHeight = SOURCE_BOUNDS.height() * scale;
        float translateX = area.centerX() - translatedWidth / 2f - SOURCE_BOUNDS.left * scale;
        float translateY = area.centerY() - translatedHeight / 2f - SOURCE_BOUNDS.top * scale;
        MATRIX.reset();
        MATRIX.setScale(scale, scale);
        MATRIX.postTranslate(translateX, translateY);
        PATH.reset();
        SOURCE_PATH.transform(MATRIX, PATH);
        canvas.drawPath(PATH, paint);
    }

    private static float normalizeContentScale(float contentScale) {
        if (contentScale <= 0f) {
            return 1f;
        }
        return Math.max(0.5f, Math.min(2f, contentScale));
    }

    private static final class SimplePathDataParser {
        private final String data;
        private final int length;
        private int index;

        private SimplePathDataParser(String data) {
            this.data = data == null ? "" : data;
            this.length = this.data.length();
        }

        static Path parse(String data) {
            try {
                return new SimplePathDataParser(data).parsePath();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Path parsePath() {
            Path path = new Path();
            float currentX = 0f;
            float currentY = 0f;
            float startX = 0f;
            float startY = 0f;
            char command = ' ';
            while (hasMore()) {
                skipSeparators();
                if (!hasMore()) {
                    break;
                }
                char next = data.charAt(index);
                if (isCommand(next)) {
                    command = next;
                    index++;
                } else if (command == ' ') {
                    throw new IllegalArgumentException("Path data missing command at " + index);
                }
                boolean relative = Character.isLowerCase(command);
                switch (Character.toUpperCase(command)) {
                    case 'M': {
                        boolean firstPoint = true;
                        while (hasNumber()) {
                            float x = nextFloat();
                            float y = nextFloat();
                            if (relative) {
                                x += currentX;
                                y += currentY;
                            }
                            if (firstPoint) {
                                path.moveTo(x, y);
                                startX = x;
                                startY = y;
                                firstPoint = false;
                            } else {
                                path.lineTo(x, y);
                            }
                            currentX = x;
                            currentY = y;
                        }
                        break;
                    }
                    case 'L': {
                        while (hasNumber()) {
                            float x = nextFloat();
                            float y = nextFloat();
                            if (relative) {
                                x += currentX;
                                y += currentY;
                            }
                            path.lineTo(x, y);
                            currentX = x;
                            currentY = y;
                        }
                        break;
                    }
                    case 'H': {
                        while (hasNumber()) {
                            float x = nextFloat();
                            if (relative) {
                                x += currentX;
                            }
                            path.lineTo(x, currentY);
                            currentX = x;
                        }
                        break;
                    }
                    case 'V': {
                        while (hasNumber()) {
                            float y = nextFloat();
                            if (relative) {
                                y += currentY;
                            }
                            path.lineTo(currentX, y);
                            currentY = y;
                        }
                        break;
                    }
                    case 'Q': {
                        while (hasNumber()) {
                            float controlX = nextFloat();
                            float controlY = nextFloat();
                            float endX = nextFloat();
                            float endY = nextFloat();
                            if (relative) {
                                controlX += currentX;
                                controlY += currentY;
                                endX += currentX;
                                endY += currentY;
                            }
                            path.quadTo(controlX, controlY, endX, endY);
                            currentX = endX;
                            currentY = endY;
                        }
                        break;
                    }
                    case 'Z': {
                        path.close();
                        currentX = startX;
                        currentY = startY;
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Unsupported command: " + command);
                }
            }
            return path;
        }

        private boolean hasMore() {
            return index < length;
        }

        private boolean hasNumber() {
            skipSeparators();
            return hasMore() && !isCommand(data.charAt(index));
        }

        private void skipSeparators() {
            while (hasMore()) {
                char c = data.charAt(index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',') {
                    index++;
                    continue;
                }
                break;
            }
        }

        private float nextFloat() {
            skipSeparators();
            if (!hasMore()) {
                throw new IllegalArgumentException("Unexpected end of path data");
            }
            int start = index;
            boolean seenDot = false;
            boolean seenExp = false;
            while (hasMore()) {
                char c = data.charAt(index);
                if (c >= '0' && c <= '9') {
                    index++;
                    continue;
                }
                if (c == '.' && !seenDot) {
                    seenDot = true;
                    index++;
                    continue;
                }
                if ((c == 'e' || c == 'E') && !seenExp) {
                    seenExp = true;
                    seenDot = false;
                    index++;
                    if (hasMore()) {
                        char sign = data.charAt(index);
                        if (sign == '+' || sign == '-') {
                            index++;
                        }
                    }
                    continue;
                }
                if ((c == '-' || c == '+') && index == start) {
                    index++;
                    continue;
                }
                break;
            }
            if (start == index) {
                throw new IllegalArgumentException("Invalid number at " + index);
            }
            return Float.parseFloat(data.substring(start, index));
        }

        private boolean isCommand(char c) {
            switch (c) {
                case 'M':
                case 'm':
                case 'L':
                case 'l':
                case 'H':
                case 'h':
                case 'V':
                case 'v':
                case 'Q':
                case 'q':
                case 'Z':
                case 'z':
                    return true;
                default:
                    return false;
            }
        }
    }
}
