package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class ImeHooks {
    private static final String TAG_IME_TOOLBAR_ROOT = "flyme_status_bar_sizer_ime_toolbar_root";
    private static final String TAG_IME_TOOLBAR_ORIGINAL = "flyme_status_bar_sizer_ime_toolbar_original";
    private static final float IME_TOOLBAR_ICON_VIEWPORT = 960f;
    private static final String[] IME_TOOLBAR_ACTIONS = {
            "paste", "delete", "select_all", "copy", "switch_ime"
    };
    private static final WeakHashMap<Object, View> TRACKED_INPUT_METHOD_VIEWS = new WeakHashMap<>();

    private ImeHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookInputMethodService(module, loader);
    }

    public static void refreshTrackedInputMethodViews() {
        FlymeStatusBarSizer.postToMainHandler(() -> {
            ArrayList<Object> services = new ArrayList<>(TRACKED_INPUT_METHOD_VIEWS.keySet());
            for (Object inputMethodService : services) {
                if (inputMethodService == null) {
                    continue;
                }
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(inputMethodService);
                if (inputView == null) {
                    continue;
                }
                refreshImeToolbarNow(inputMethodService, inputView);
            }
        });
    }

    private static void hookInputMethodService(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("android.inputmethodservice.InputMethodService", false, loader);
            Method setInputView = clazz.getDeclaredMethod("setInputView", View.class);
            setInputView.setAccessible(true);
            module.intercept(setInputView, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                Object arg = chain.getArg(0);
                if (!(thisObject instanceof Context) || !(arg instanceof View)) {
                    return result;
                }
                Context context = (Context) thisObject;
                View inputView = (View) arg;
                FlymeStatusBarSizer.rememberSystemUiContext(context);
                FlymeStatusBarSizer.ensureConfigRefreshObserver(context);
                TRACKED_INPUT_METHOD_VIEWS.put(thisObject, inputView);
                inputView.post(() -> attachImeToolbarIfNeeded(inputMethodService(thisObject), inputView));
                return result;
            });

            Class<?> editorInfoClass = Class.forName("android.view.inputmethod.EditorInfo", false, loader);
            Method onStartInputView = clazz.getDeclaredMethod("onStartInputView", editorInfoClass, boolean.class);
            onStartInputView.setAccessible(true);
            module.intercept(onStartInputView, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(thisObject);
                if (inputView != null) {
                    inputView.post(() -> attachImeToolbarIfNeeded(inputMethodService(thisObject), inputView));
                }
                return result;
            });

            Method showWindow = clazz.getDeclaredMethod("showWindow", boolean.class);
            showWindow.setAccessible(true);
            module.intercept(showWindow, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(thisObject);
                if (inputView != null) {
                    inputView.post(() -> attachImeToolbarIfNeeded(inputMethodService(thisObject), inputView));
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to hook InputMethodService.setInputView", t);
        }
    }

    private static Object inputMethodService(Object value) {
        return value;
    }

    private static void attachImeToolbarIfNeeded(Object inputMethodService, View inputView) {
        if (inputMethodService == null || inputView == null) {
            return;
        }
        Context context = inputView.getContext();
        if (context == null) {
            return;
        }
        FlymeStatusBarSizer.ImeConfigSnapshot config = FlymeStatusBarSizer.loadImeConfig(context);
        if (!config.enabled || !config.imeToolbarEnabled) {
            detachImeToolbarIfPresent(inputMethodService);
            return;
        }
        ViewGroup inputFrame = asViewGroup(FlymeStatusBarSizer.getFieldCompat(inputMethodService, "mInputFrame"));
        if (inputFrame == null) {
            return;
        }
        View current = inputFrame.getChildCount() > 0 ? inputFrame.getChildAt(0) : null;
        if (current != null && TAG_IME_TOOLBAR_ROOT.equals(current.getTag())) {
            updateImeToolbarState(inputMethodService, current);
            return;
        }
        if (current != inputView || current == null || current.getParent() != inputFrame) {
            return;
        }
        inputFrame.removeAllViews();
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setTag(TAG_IME_TOOLBAR_ROOT);
        FlymeStatusBarSizer.disableAncestorClipping(container, 2);

        ViewGroup currentParent = asViewGroup(current.getParent());
        if (currentParent != null) {
            currentParent.removeView(current);
        }
        current.setTag(TAG_IME_TOOLBAR_ORIGINAL);
        container.addView(current, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(createImeToolbarView(context, inputMethodService, current),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        inputFrame.addView(container, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        inputFrame.requestLayout();
        inputFrame.invalidate();
    }

    private static void detachImeToolbarIfPresent(Object inputMethodService) {
        ViewGroup inputFrame = asViewGroup(FlymeStatusBarSizer.getFieldCompat(inputMethodService, "mInputFrame"));
        if (inputFrame == null || inputFrame.getChildCount() == 0) {
            return;
        }
        View current = inputFrame.getChildAt(0);
        if (current == null || !TAG_IME_TOOLBAR_ROOT.equals(current.getTag()) || !(current instanceof ViewGroup)) {
            return;
        }
        ViewGroup container = (ViewGroup) current;
        View original = null;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (TAG_IME_TOOLBAR_ORIGINAL.equals(child.getTag())) {
                original = child;
                break;
            }
        }
        if (original == null) {
            return;
        }
        container.removeView(original);
        original.setTag(null);
        inputFrame.removeAllViews();
        inputFrame.addView(original, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        inputFrame.requestLayout();
        inputFrame.invalidate();
    }

    private static void updateImeToolbarState(Object inputMethodService, View toolbarRoot) {
        if (!(toolbarRoot instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) toolbarRoot;
        if (group.getChildCount() < 2) {
            return;
        }
        View toolbar = group.getChildAt(group.getChildCount() - 1);
        if (!(toolbar instanceof LinearLayout)) {
            return;
        }
        View originalInputView = group.getChildAt(0);
        ViewGroup.LayoutParams layoutParams = toolbar.getLayoutParams();
        group.removeView(toolbar);
        LinearLayout rebuiltBar = createImeToolbarView(group.getContext(), inputMethodService, originalInputView);
        group.addView(rebuiltBar, new LinearLayout.LayoutParams(
                layoutParams != null ? layoutParams.width : ViewGroup.LayoutParams.MATCH_PARENT,
                layoutParams != null ? layoutParams.height : ViewGroup.LayoutParams.WRAP_CONTENT));
        group.requestLayout();
        group.invalidate();
    }

    private static LinearLayout createImeToolbarView(Context context, Object inputMethodService, View inputView) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = FlymeStatusBarSizer.dp(context, 18);
        int vertical = FlymeStatusBarSizer.dp(context, 6);
        bar.setPadding(horizontal, vertical, horizontal, vertical);
        applyImeToolbarBackground(bar, inputView);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0,
                FlymeStatusBarSizer.dp(context, 40),
                1f);
        buttonParams.leftMargin = FlymeStatusBarSizer.dp(context, 4);
        buttonParams.rightMargin = FlymeStatusBarSizer.dp(context, 4);
        ArrayList<String> orderedActions = resolveImeToolbarOrder(FlymeStatusBarSizer.loadImeConfig(context));
        for (String action : orderedActions) {
            ImageButton button = new ImageButton(context);
            configureImeToolbarButton(context, button, action, getImeToolbarActionLabel(action));
            button.setTag(action);
            bar.addView(button, new LinearLayout.LayoutParams(buttonParams));
        }
        applyImeToolbarIconTint(bar, resolveImeToolbarIconColor(context));
        bindImeToolbarButtonActions(inputMethodService, bar);
        updateImeToolbarStateForButtons(inputMethodService, bar);
        return bar;
    }

    private static void updateImeToolbarStateForButtons(Object inputMethodService, LinearLayout bar) {
        updatePasteButtonEnabled(inputMethodService, findImeToolbarButton(bar, "paste"));
    }

    private static View findImeToolbarButton(LinearLayout bar, String action) {
        if (bar == null || TextUtils.isEmpty(action)) {
            return null;
        }
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child != null && action.equals(child.getTag())) {
                return child;
            }
        }
        return null;
    }

    private static ArrayList<String> resolveImeToolbarOrder(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        ArrayList<String> result = new ArrayList<>();
        if (config != null && !TextUtils.isEmpty(config.imeToolbarOrder)) {
            String[] parts = config.imeToolbarOrder.split(",");
            for (String part : parts) {
                String action = part == null ? "" : part.trim();
                if (isValidImeToolbarAction(action) && !result.contains(action)) {
                    result.add(action);
                }
            }
        }
        for (String action : IME_TOOLBAR_ACTIONS) {
            if (!result.contains(action)) {
                result.add(action);
            }
        }
        return result;
    }

    private static boolean isValidImeToolbarAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return false;
        }
        for (String candidate : IME_TOOLBAR_ACTIONS) {
            if (candidate.equals(action)) {
                return true;
            }
        }
        return false;
    }

    private static String getImeToolbarActionLabel(String action) {
        if ("paste".equals(action)) {
            return "粘贴";
        }
        if ("delete".equals(action)) {
            return "删除";
        }
        if ("select_all".equals(action)) {
            return "全选";
        }
        if ("copy".equals(action)) {
            return "复制";
        }
        if ("switch_ime".equals(action)) {
            return "切换输入法";
        }
        return action;
    }

    private static void applyImeToolbarBackground(LinearLayout bar, View inputView) {
        if (bar == null) {
            return;
        }
        Drawable background = inputView == null ? null : inputView.getBackground();
        if (background != null) {
            Drawable copied = background.getConstantState() != null
                    ? background.getConstantState().newDrawable().mutate()
                    : background.mutate();
            bar.setBackground(copied);
        } else {
            bar.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static void configureImeToolbarButton(Context context, ImageButton button, String iconType, String desc) {
        Drawable drawable = createImeToolbarIconDrawable(context, iconType);
        button.setImageDrawable(drawable);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(desc);
        button.setBackground(new ColorDrawable(Color.TRANSPARENT));
        button.setPadding(
                FlymeStatusBarSizer.dp(context, 8),
                FlymeStatusBarSizer.dp(context, 8),
                FlymeStatusBarSizer.dp(context, 8),
                FlymeStatusBarSizer.dp(context, 8));
    }

    private static void applyImeToolbarIconTint(LinearLayout bar, int color) {
        if (bar == null) {
            return;
        }
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof ImageButton) {
                ((ImageButton) child).setColorFilter(color);
            }
        }
    }

    private static int resolveImeToolbarIconColor(Context context) {
        if (context == null) {
            return Color.WHITE;
        }
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
    }

    private static Drawable createImeToolbarIconDrawable(Context context, String iconType) {
        String pathData = getImeToolbarIconPathData(iconType);
        if (TextUtils.isEmpty(pathData) || context == null) {
            return null;
        }
        try {
            return new ImeToolbarPathDrawable(pathData, Math.max(1, FlymeStatusBarSizer.dp(context, 24)));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to create ime toolbar drawable: " + iconType, t);
            return null;
        }
    }

    private static String getImeToolbarIconPathData(String iconType) {
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

    private static void bindImeToolbarButtonActions(Object inputMethodService, LinearLayout bar) {
        if (bar == null) {
            return;
        }
        for (int i = 0; i < bar.getChildCount(); i++) {
            View button = bar.getChildAt(i);
            if (button == null || !(button.getTag() instanceof String)) {
                continue;
            }
            String action = (String) button.getTag();
            if ("paste".equals(action)) {
                button.setOnClickListener(v -> performPasteAction(inputMethodService, v.getContext()));
            } else if ("delete".equals(action)) {
                button.setOnClickListener(v -> performDeleteAction(inputMethodService));
            } else if ("select_all".equals(action)) {
                button.setOnClickListener(v ->
                        performEditorAction(inputMethodService, android.R.id.selectAll));
            } else if ("copy".equals(action)) {
                button.setOnClickListener(v ->
                        performEditorAction(inputMethodService, android.R.id.copy));
            } else if ("switch_ime".equals(action)) {
                button.setOnClickListener(v -> {
                    Context context = v.getContext();
                    try {
                        InputMethodManager imm =
                                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showInputMethodPicker();
                        }
                    } catch (Throwable t) {
                        FlymeStatusBarSizer.logImeWarning("Failed to show input method picker", t);
                    }
                });
            }
        }
    }

    private static void performDeleteAction(Object inputMethodService) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null) {
            return;
        }
        try {
            CharSequence selectedText = connection.getSelectedText(0);
            if (!TextUtils.isEmpty(selectedText)) {
                connection.commitText("", 1);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            connection.deleteSurroundingText(1, 0);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to delete surrounding text", t);
        }
    }

    private static void performEditorAction(Object inputMethodService, int actionId) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null) {
            return;
        }
        try {
            connection.performContextMenuAction(actionId);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to perform editor action: " + actionId, t);
        }
    }

    private static void performPasteAction(Object inputMethodService, Context context) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null || context == null) {
            return;
        }
        try {
            if (connection.performContextMenuAction(android.R.id.paste)) {
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                return;
            }
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() <= 0) {
                return;
            }
            CharSequence text = clipData.getItemAt(0).coerceToText(context);
            if (!TextUtils.isEmpty(text)) {
                connection.commitText(text, 1);
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to paste clipboard text", t);
        }
    }

    private static void updatePasteButtonEnabled(Object inputMethodService, View pasteButton) {
        if (pasteButton == null) {
            return;
        }
        boolean enabled = getCurrentInputConnectionCompat(inputMethodService) != null;
        pasteButton.setEnabled(enabled);
        pasteButton.setAlpha(enabled ? 1f : 0.55f);
    }

    private static InputConnection getCurrentInputConnectionCompat(Object inputMethodService) {
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(inputMethodService, "getCurrentInputConnection");
        return value instanceof InputConnection ? (InputConnection) value : null;
    }

    private static ViewGroup asViewGroup(Object object) {
        return object instanceof ViewGroup ? (ViewGroup) object : null;
    }

    private static void refreshImeToolbarNow(Object inputMethodService, View inputView) {
        if (inputMethodService == null || inputView == null) {
            return;
        }
        detachImeToolbarIfPresent(inputMethodService);
        attachImeToolbarIfNeeded(inputMethodService, inputView);
    }

    private static final class ImeToolbarPathDrawable extends Drawable {
        private final Path path;
        private final Paint paint;
        private final int intrinsicSize;
        private int alpha = 255;

        ImeToolbarPathDrawable(String pathData, int intrinsicSize) {
            this.path = SimplePathDataParser.parse(pathData);
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

    private static final class SimplePathDataParser {
        private final String data;
        private int index;
        private final int length;

        private SimplePathDataParser(String data) {
            this.data = data == null ? "" : data;
            this.length = this.data.length();
        }

        static Path parse(String data) {
            try {
                return new SimplePathDataParser(data).parsePath();
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
