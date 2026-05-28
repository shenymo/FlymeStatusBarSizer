package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.List;

final class ImeToolbarActions {
    private static final Object CAPTCHA_LOCK = new Object();
    private static final long DEFAULT_CAPTCHA_TTL_MS = 60_000L;
    private static final long MAX_CAPTCHA_TTL_MS = 300_000L;
    private static final int[] CAPTCHA_QUERY_POSITIONS = new int[]{16, -1, 0xFFFF};
    private static String captchaLabel;
    private static String captchaInput;
    private static int captchaDisplayId = -1;
    private static long captchaExpiryUptimeMs;

    private ImeToolbarActions() {
    }

    static void bindActionButtons(Object inputMethodService, View root) {
        if (root == null) {
            return;
        }
        if (root.getTag() instanceof String) {
            bindActionButton(inputMethodService, root, (String) root.getTag());
        }
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            bindActionButtons(inputMethodService, group.getChildAt(i));
        }
    }

    static void bindActionButtonView(Object inputMethodService, View button) {
        if (button == null || !(button.getTag() instanceof String)) {
            return;
        }
        bindActionButton(inputMethodService, button, (String) button.getTag());
    }

    static void refreshActionButtonStates(Object inputMethodService, View root) {
        if (root == null) {
            return;
        }
        refreshActionButtonState(inputMethodService, root);
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            refreshActionButtonStates(inputMethodService, group.getChildAt(i));
        }
    }

    static void refreshActionButtonState(Object inputMethodService, View button) {
        if (button == null || !(button.getTag() instanceof String)) {
            return;
        }
        String action = (String) button.getTag();
        if ("paste".equals(action)) {
            updatePasteButtonEnabled(inputMethodService, button);
        } else if (ImeToolbarSpec.isCaptchaButton(action)) {
            updateCaptchaButtonState(inputMethodService, button);
        } else if (ImeToolbarSpec.isValidActionName(action)) {
            button.setEnabled(true);
            button.setAlpha(1f);
        }
    }

    static void updatePasteButtonEnabled(Object inputMethodService, View pasteButton) {
        if (pasteButton == null) {
            return;
        }
        boolean enabled = getCurrentInputConnectionCompat(inputMethodService) != null;
        pasteButton.setEnabled(enabled);
        pasteButton.setAlpha(enabled ? 1f : 0.55f);
    }

    private static void bindActionButton(Object inputMethodService, View button, String action) {
        if (!ImeToolbarSpec.isValidActionName(action) || button == null) {
            return;
        }
        if ("paste".equals(action)) {
            button.setOnClickListener(v -> {
                performActionHapticFeedback(v);
                performPasteAction(inputMethodService, v.getContext());
            });
        } else if ("undo".equals(action)) {
            button.setOnClickListener(v -> {
                performActionHapticFeedback(v);
                performUndoAction(inputMethodService);
            });
        } else if ("delete".equals(action)) {
            button.setOnClickListener(v -> {
                performActionHapticFeedback(v);
                performDeleteAction(inputMethodService);
            });
        } else if ("select_all".equals(action)) {
            button.setOnClickListener(v -> {
                performActionHapticFeedback(v);
                performEditorAction(inputMethodService, android.R.id.selectAll);
            });
        } else if ("copy".equals(action)) {
            button.setOnClickListener(v -> {
                performActionHapticFeedback(v);
                performEditorAction(inputMethodService, android.R.id.copy);
            });
        } else if ("switch_ime".equals(action)) {
            button.setOnClickListener(v -> {
                performActionHapticFeedback(v);
                showInputMethodPicker(v.getContext());
            });
        } else if (ImeToolbarSpec.isCaptchaButton(action)) {
            button.setOnClickListener(v -> {
                performActionHapticFeedback(v);
                performCaptchaAction(inputMethodService, v);
            });
        }
    }

    static boolean updateFlymeCaptchaCandidate(Object item) {
        if (item == null) {
            return clearCaptchaCandidate();
        }
        int position = asInt(FlymeStatusBarSizer.invokeNoArgCompat(item, "getPosition"), -1);
        if (position != 16) {
            return false;
        }
        int state = asInt(FlymeStatusBarSizer.invokeNoArgCompat(item, "getState"), 0);
        if (state != 0) {
            return clearCaptchaCandidate();
        }
        CharSequence label = asCharSequence(FlymeStatusBarSizer.invokeNoArgCompat(item, "getLabel"));
        CharSequence input = asCharSequence(FlymeStatusBarSizer.invokeNoArgCompat(item, "getInput"));
        long expiryTime = asLong(FlymeStatusBarSizer.invokeNoArgCompat(item, "getExpiryTime"), 0L);
        int showSeconds = asInt(FlymeStatusBarSizer.invokeNoArgCompat(item, "getShowSeconds"), 0);
        int displayId = asInt(FlymeStatusBarSizer.invokeNoArgCompat(item, "getDisplayId"), -1);
        return updateCaptchaCandidate(label, input, expiryTime, showSeconds, displayId);
    }

    static boolean updateFlymeCaptchaCandidateFromList(Object result) {
        if (!(result instanceof List)) {
            return false;
        }
        List<?> items = (List<?>) result;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            int position = asInt(FlymeStatusBarSizer.invokeNoArgCompat(item, "getPosition"), -1);
            if (position == 16) {
                return updateFlymeCaptchaCandidate(item);
            }
        }
        return false;
    }

    static boolean refreshFlymeCaptchaCandidate(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Class<?> managerClass = findFlymeClass(context, "flyme.inputmethod.QsActionManager");
            if (managerClass == null) {
                return false;
            }
            Method getInstance = managerClass.getDeclaredMethod("getInstance", Context.class);
            getInstance.setAccessible(true);
            Object manager = getInstance.invoke(null, context);
            if (manager == null) {
                return false;
            }
            Method getActionItemList = managerClass.getDeclaredMethod("getActionItemList", int.class);
            getActionItemList.setAccessible(true);
            for (int i = 0; i < CAPTCHA_QUERY_POSITIONS.length; i++) {
                Object result = getActionItemList.invoke(manager, CAPTCHA_QUERY_POSITIONS[i]);
                if (updateFlymeCaptchaCandidateFromList(result)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static long getCaptchaRefreshDelayMs() {
        synchronized (CAPTCHA_LOCK) {
            if (TextUtils.isEmpty(captchaInput) || captchaExpiryUptimeMs <= 0L) {
                return -1L;
            }
            long delay = captchaExpiryUptimeMs - SystemClock.uptimeMillis();
            return delay > 0L ? delay : 0L;
        }
    }

    private static void performActionHapticFeedback(View button) {
        if (button == null) {
            return;
        }
        try {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignored) {
        }
    }

    private static void showInputMethodPicker(Context context) {
        if (context == null) {
            return;
        }
        try {
            InputMethodManager imm =
                    (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to show input method picker", t);
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

    private static void performUndoAction(Object inputMethodService) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null) {
            return;
        }
        if (performContextMenuAction(connection, android.R.id.undo)) {
            return;
        }
        sendCtrlZUndoFallback(connection);
    }

    private static boolean performEditorAction(Object inputMethodService, int actionId) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        return performContextMenuAction(connection, actionId);
    }

    private static boolean performContextMenuAction(InputConnection connection, int actionId) {
        if (connection == null) {
            return false;
        }
        try {
            return connection.performContextMenuAction(actionId);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to perform editor action: " + actionId, t);
            return false;
        }
    }

    private static void sendCtrlZUndoFallback(InputConnection connection) {
        if (connection == null) {
            return;
        }
        long downTime = SystemClock.uptimeMillis();
        try {
            connection.sendKeyEvent(new KeyEvent(
                    downTime,
                    downTime,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_Z,
                    0,
                    KeyEvent.META_CTRL_ON));
            connection.sendKeyEvent(new KeyEvent(
                    downTime,
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_Z,
                    0,
                    KeyEvent.META_CTRL_ON));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to send Ctrl+Z fallback", t);
        }
    }

    private static void performPasteAction(Object inputMethodService, Context context) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null || context == null) {
            return;
        }
        if (performContextMenuAction(connection, android.R.id.paste)) {
            return;
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

    private static boolean updateCaptchaCandidate(
            CharSequence label, CharSequence input, long expiryTime, int showSeconds, int displayId) {
        String inputText = sanitizeCaptchaText(input);
        if (TextUtils.isEmpty(inputText)) {
            inputText = sanitizeCaptchaText(label);
        }
        if (TextUtils.isEmpty(inputText)) {
            return clearCaptchaCandidate();
        }
        String labelText = sanitizeCaptchaText(label);
        if (TextUtils.isEmpty(labelText)) {
            labelText = inputText;
        }
        long nowWall = System.currentTimeMillis();
        long ttlMs = expiryTime > nowWall
                ? expiryTime - nowWall
                : (showSeconds > 0 ? showSeconds * 1000L : DEFAULT_CAPTCHA_TTL_MS);
        ttlMs = Math.max(1000L, Math.min(MAX_CAPTCHA_TTL_MS, ttlMs));
        long expiryUptime = SystemClock.uptimeMillis() + ttlMs;
        synchronized (CAPTCHA_LOCK) {
            boolean changed = !TextUtils.equals(captchaLabel, labelText)
                    || !TextUtils.equals(captchaInput, inputText)
                    || captchaDisplayId != displayId;
            captchaLabel = labelText;
            captchaInput = inputText;
            captchaDisplayId = displayId;
            captchaExpiryUptimeMs = expiryUptime;
            return changed;
        }
    }

    private static boolean clearCaptchaCandidate() {
        synchronized (CAPTCHA_LOCK) {
            boolean changed = !TextUtils.isEmpty(captchaLabel) || !TextUtils.isEmpty(captchaInput);
            captchaLabel = null;
            captchaInput = null;
            captchaDisplayId = -1;
            captchaExpiryUptimeMs = 0L;
            return changed;
        }
    }

    private static void updateCaptchaButtonState(Object inputMethodService, View button) {
        if (button == null) {
            return;
        }
        int displayId = resolveDisplayId(button.getContext());
        String label = getActiveCaptchaLabel(displayId);
        if (button instanceof TextView) {
            TextView textButton = (TextView) button;
            textButton.setGravity(Gravity.CENTER);
            textButton.setText(TextUtils.isEmpty(label) ? "" : label);
        }
        boolean enabled = !TextUtils.isEmpty(label)
                && !TextUtils.isEmpty(getActiveCaptchaInput(displayId))
                && getCurrentInputConnectionCompat(inputMethodService) != null;
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.55f);
    }

    private static void performCaptchaAction(Object inputMethodService, View button) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null) {
            return;
        }
        String input = getActiveCaptchaInput(resolveDisplayId(button == null ? null : button.getContext()));
        if (TextUtils.isEmpty(input)) {
            updateCaptchaButtonState(inputMethodService, button);
            return;
        }
        try {
            if (connection.commitText(input, 1)) {
                clearCaptchaCandidate();
                ImeHooks.refreshTrackedInputMethodViews();
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to input captcha text", t);
        }
    }

    private static String getActiveCaptchaLabel(int displayId) {
        synchronized (CAPTCHA_LOCK) {
            if (!isCaptchaActiveLocked(displayId)) {
                return null;
            }
            return captchaLabel;
        }
    }

    private static String getActiveCaptchaInput(int displayId) {
        synchronized (CAPTCHA_LOCK) {
            if (!isCaptchaActiveLocked(displayId)) {
                return null;
            }
            return captchaInput;
        }
    }

    private static boolean isCaptchaActiveLocked(int displayId) {
        if (TextUtils.isEmpty(captchaInput)
                || captchaExpiryUptimeMs <= SystemClock.uptimeMillis()) {
            captchaLabel = null;
            captchaInput = null;
            captchaDisplayId = -1;
            captchaExpiryUptimeMs = 0L;
            return false;
        }
        return captchaDisplayId == -1 || displayId == -1 || captchaDisplayId == displayId;
    }

    private static String sanitizeCaptchaText(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    private static CharSequence asCharSequence(Object value) {
        return value instanceof CharSequence ? (CharSequence) value : null;
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long asLong(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static Class<?> findFlymeClass(Context context, String className) {
        ClassLoader[] loaders = new ClassLoader[]{
                context.getClassLoader(),
                context.getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader()
        };
        for (int i = 0; i < loaders.length; i++) {
            ClassLoader loader = loaders[i];
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(className, false, loader);
            } catch (Throwable ignored) {
            }
        }
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int resolveDisplayId(Context context) {
        if (context == null) {
            return -1;
        }
        return asInt(FlymeStatusBarSizer.invokeNoArgCompat(context, "getDisplayId"), -1);
    }

    private static InputConnection getCurrentInputConnectionCompat(Object inputMethodService) {
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(inputMethodService, "getCurrentInputConnection");
        return value instanceof InputConnection ? (InputConnection) value : null;
    }
}
