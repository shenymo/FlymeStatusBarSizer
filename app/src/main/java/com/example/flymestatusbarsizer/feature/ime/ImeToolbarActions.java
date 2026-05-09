package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

final class ImeToolbarActions {
    private ImeToolbarActions() {
    }

    static void bindButtonActions(Object inputMethodService, LinearLayout bar) {
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
                button.setOnClickListener(v -> showInputMethodPicker(v.getContext()));
            }
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

    private static InputConnection getCurrentInputConnectionCompat(Object inputMethodService) {
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(inputMethodService, "getCurrentInputConnection");
        return value instanceof InputConnection ? (InputConnection) value : null;
    }
}
