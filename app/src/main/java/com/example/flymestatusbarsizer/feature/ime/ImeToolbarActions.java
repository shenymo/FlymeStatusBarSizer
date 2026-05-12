package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

final class ImeToolbarActions {
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

    private static InputConnection getCurrentInputConnectionCompat(Object inputMethodService) {
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(inputMethodService, "getCurrentInputConnection");
        return value instanceof InputConnection ? (InputConnection) value : null;
    }
}
