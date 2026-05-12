package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.R;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

final class ImeToolbarIcons {
    private static final float IME_TOOLBAR_ICON_VIEWPORT = 960f;

    private ImeToolbarIcons() {
    }

    static int resolveIconColor(Context context) {
        if (context == null) {
            return Color.WHITE;
        }
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
    }

    static int resolveStockControlBarIconColor(Context context) {
        return resolveIconColor(context);
    }

    static Drawable createIconDrawable(Context context, String iconType) {
        if ("undo".equals(iconType)) {
            return createModuleDrawable(context, R.drawable.undo_24px, "ime undo drawable");
        }
        String pathData = getIconPathData(iconType);
        if (TextUtils.isEmpty(pathData) || context == null) {
            return null;
        }
        try {
            return new PathDrawable(pathData, Math.max(1, FlymeStatusBarSizer.dp(context, 24)));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to create ime toolbar drawable: " + iconType, t);
            return null;
        }
    }

    static Drawable createKeyboardDismissDrawable(Context context) {
        return createModuleDrawable(context, R.drawable.keyboard_double_arrow_down_24px,
                "keyboard dismiss drawable");
    }

    private static Drawable createModuleDrawable(Context context, int drawableRes, String logLabel) {
        if (context == null) {
            return null;
        }
        try {
            Context resourceContext = resolveModuleResourceContext(context);
            if (resourceContext == null) {
                return null;
            }
            Drawable drawable = resourceContext.getDrawable(drawableRes);
            return cloneDrawable(drawable, context.getResources());
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to load " + logLabel, t);
            return null;
        }
    }

    private static String getIconPathData(String iconType) {
        if (TextUtils.isEmpty(iconType)) {
            return null;
        }
        switch (iconType) {
            case "paste":
                return "M720,840L664,783L727,720L480,720L480,640L727,640L664,576L720,520L880,680L720,840ZM840,440L760,440L760,200Q760,200 760,200Q760,200 760,200L680,200L680,320L280,320L280,200L200,200Q200,200 200,200Q200,200 200,200L200,760Q200,760 200,760Q200,760 200,760L400,760L400,840L200,840Q167,840 143.5,816.5Q120,793 120,760L120,200Q120,167 143.5,143.5Q167,120 200,120L367,120Q378,85 410,62.5Q442,40 480,40Q520,40 551.5,62.5Q583,85 594,120L760,120Q793,120 816.5,143.5Q840,167 840,200L840,440ZM508.5,188.5Q520,177 520,160Q520,143 508.5,131.5Q497,120 480,120Q463,120 451.5,131.5Q440,143 440,160Q440,177 451.5,188.5Q463,200 480,200Q497,200 508.5,188.5Z";
            case "delete":
                return "M280,840Q247,840 223.5,816.5Q200,793 200,760L200,240L160,240L160,160L360,160L360,120L600,120L600,160L800,160L800,240L760,240L760,760Q760,793 736.5,816.5Q713,840 680,840L280,840ZM680,240L280,240L280,760Q280,760 280,760Q280,760 280,760L680,760Q680,760 680,760Q680,760 680,760L680,240ZM360,680L440,680L440,320L360,320L360,680ZM520,680L600,680L600,320L520,320L520,680ZM280,240L280,240L280,760Q280,760 280,760Q280,760 280,760L280,760Q280,760 280,760Q280,760 280,760L280,240Z";
            case "select_all":
                return "M280,680L280,280L680,280L680,680L280,680ZM360,600L600,600L600,360L360,360L360,600ZM200,760L200,840Q167,840 143.5,816.5Q120,793 120,760L200,760ZM120,680L120,600L200,600L200,680L120,680ZM120,520L120,440L200,440L200,520L120,520ZM120,360L120,280L200,280L200,360L120,360ZM200,200L120,200Q120,167 143.5,143.5Q167,120 200,120L200,200ZM280,840L280,760L360,760L360,840L280,840ZM280,200L280,120L360,120L360,200L280,200ZM440,840L440,760L520,760L520,840L440,840ZM440,200L440,120L520,120L520,200L440,200ZM600,840L600,760L680,760L680,840L600,840ZM600,200L600,120L680,120L680,200L600,200ZM760,840L760,760L840,760Q840,793 816.5,816.5Q793,840 760,840ZM760,680L760,600L840,600L840,680L760,680ZM760,520L760,440L840,440L840,520L760,520ZM760,360L760,280L840,280L840,360L760,360ZM760,200L760,120Q793,120 816.5,143.5Q840,167 840,200L760,200Z";
            case "copy":
                return "M360,720Q327,720 303.5,696.5Q280,673 280,640L280,160Q280,127 303.5,103.5Q327,80 360,80L720,80Q753,80 776.5,103.5Q800,127 800,160L800,640Q800,673 776.5,696.5Q753,720 720,720L360,720ZM360,640L720,640Q720,640 720,640Q720,640 720,640L720,160Q720,160 720,160Q720,160 720,160L360,160Q360,160 360,160Q360,160 360,160L360,640Q360,640 360,640Q360,640 360,640ZM200,880Q167,880 143.5,856.5Q120,833 120,800L120,240L200,240L200,800Q200,800 200,800Q200,800 200,800L640,800L640,880L200,880ZM360,640Q360,640 360,640Q360,640 360,640L360,160Q360,160 360,160Q360,160 360,160L360,160Q360,160 360,160Q360,160 360,160L360,640Q360,640 360,640Q360,640 360,640Z";
            case "switch_ime":
                return "M320,680L640,680L640,600L320,600L320,680ZM200,560L280,560L280,480L200,480L200,560ZM320,560L400,560L400,480L320,480L320,560ZM440,560L520,560L520,480L440,480L440,560ZM560,560L640,560L640,480L560,480L560,560ZM680,560L760,560L760,480L680,480L680,560ZM160,800Q127,800 103.5,776.5Q80,753 80,720L80,240Q80,207 103.5,183.5Q127,160 160,160L800,160Q833,160 856.5,183.5Q880,207 880,240L880,720Q880,753 856.5,776.5Q833,800 800,800L160,800ZM160,360L800,360L800,240Q800,240 800,240Q800,240 800,240L160,240Q160,240 160,240Q160,240 160,240L160,360ZM160,720L800,720Q800,720 800,720Q800,720 800,720L800,440L160,440L160,720Q160,720 160,720Q160,720 160,720ZM160,720Q160,720 160,720Q160,720 160,720L160,440L160,440L160,720Q160,720 160,720Q160,720 160,720Z";
            default:
                return null;
        }
    }

    private static Context resolveModuleResourceContext(Context context) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (BuildConfig.APPLICATION_ID.equals(appContext.getPackageName())) {
            return appContext;
        }
        try {
            return appContext.createPackageContext(
                    BuildConfig.APPLICATION_ID,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable cloneDrawable(Drawable drawable, Resources resources) {
        if (drawable == null) {
            return null;
        }
        Drawable.ConstantState constantState = drawable.getConstantState();
        if (constantState != null) {
            Drawable cloned = resources != null
                    ? constantState.newDrawable(resources)
                    : constantState.newDrawable();
            if (cloned != null) {
                return cloned.mutate();
            }
        }
        return drawable.mutate();
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
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / IME_TOOLBAR_ICON_VIEWPORT,
                    bounds.height() / IME_TOOLBAR_ICON_VIEWPORT);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(save);
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
        private int index;
        private final int length;

        private PathDataParser(String data) {
            this.data = data == null ? "" : data;
            this.length = this.data.length();
        }

        static Path parse(String data) {
            try {
                return new PathDataParser(data).parsePath();
            } catch (Throwable t) {
                FlymeStatusBarSizer.logImeWarning("Failed to parse path data", t);
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
