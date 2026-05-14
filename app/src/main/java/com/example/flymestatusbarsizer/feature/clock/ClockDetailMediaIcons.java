package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

final class ClockDetailMediaIcons {
    private static final float VIEWPORT_SIZE = 960f;
    private static final String PREVIOUS_PATH =
            "M220,720L220,240L300,240L300,720L220,720Z"
                    + "M740,720L380,480L740,240L740,720Z"
                    + "M660,480L660,480L660,480Z"
                    + "M660,570L660,390L524,480L660,570Z";
    private static final String NEXT_PATH =
            "M660,720L660,240L740,240L740,720L660,720Z"
                    + "M220,720L220,240L580,480L220,720Z"
                    + "M300,480L300,480L300,480Z"
                    + "M300,570L436,480L300,390L300,570Z";
    private static final String PLAY_PATH =
            "M320,760L320,200L760,480L320,760Z";
    private static final String PAUSE_PATH =
            "M312,720L312,240L432,240L432,720L312,720Z"
                    + "M528,720L528,240L648,240L648,720L528,720Z";

    private ClockDetailMediaIcons() {
    }

    static Drawable createPreviousDrawable(int sizePx) {
        return createPathDrawable(PREVIOUS_PATH, sizePx);
    }

    static Drawable createNextDrawable(int sizePx) {
        return createPathDrawable(NEXT_PATH, sizePx);
    }

    static Drawable createPlayDrawable(int sizePx) {
        return createPathDrawable(PLAY_PATH, sizePx);
    }

    static Drawable createPauseDrawable(int sizePx) {
        return createPathDrawable(PAUSE_PATH, sizePx);
    }

    private static Drawable createPathDrawable(String pathData, int sizePx) {
        try {
            return new PathDrawable(pathData, Math.max(1, sizePx));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to create clock media icon", t);
            return null;
        }
    }

    private static final class PathDrawable extends Drawable {
        private final Path path;
        private final Paint paint;
        private final int intrinsicSize;
        private int alpha = 255;

        PathDrawable(String pathData, int intrinsicSize) {
            this.path = PathDataParser.parse(pathData);
            this.intrinsicSize = intrinsicSize;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas canvas) {
            if (path == null) {
                return;
            }
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            int saveCount = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / VIEWPORT_SIZE, bounds.height() / VIEWPORT_SIZE);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(saveCount);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return alpha < 255 ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicSize;
        }
    }

    private static final class PathDataParser {
        private final String data;
        private final int length;
        private int index;

        private PathDataParser(String data) {
            this.data = data == null ? "" : data;
            this.length = this.data.length();
        }

        static Path parse(String data) {
            try {
                return new PathDataParser(data).parsePath();
            } catch (Throwable t) {
                FlymeStatusBarSizer.logClockWarning("Failed to parse clock media path data", t);
                return null;
            }
        }

        private Path parsePath() {
            Path path = new Path();
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
                switch (command) {
                    case 'M': {
                        float moveX = nextFloat();
                        float moveY = nextFloat();
                        path.moveTo(moveX, moveY);
                        while (hasNumber()) {
                            path.lineTo(nextFloat(), nextFloat());
                        }
                        break;
                    }
                    case 'L': {
                        while (hasNumber()) {
                            path.lineTo(nextFloat(), nextFloat());
                        }
                        break;
                    }
                    case 'Z': {
                        path.close();
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
                if (c == ' ' || c == ',' || c == '\n' || c == '\r' || c == '\t') {
                    index++;
                    continue;
                }
                break;
            }
        }

        private float nextFloat() {
            skipSeparators();
            int start = index;
            boolean seenDot = false;
            while (hasMore()) {
                char c = data.charAt(index);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
                    index++;
                    continue;
                }
                if (c == '.' && !seenDot) {
                    seenDot = true;
                    index++;
                    continue;
                }
                break;
            }
            if (start == index) {
                throw new IllegalArgumentException("Expected number at " + index);
            }
            return Float.parseFloat(data.substring(start, index));
        }

        private static boolean isCommand(char c) {
            return c == 'M' || c == 'L' || c == 'Z';
        }
    }
}
