package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

final class ImeToolbarActions {
    private static final String TAG = "FlymeImeWelt";

    private ImeToolbarActions() {
    }

    static void bindActionButtonView(Object inputMethodService, View button) {
        if (button == null || !(button.getTag() instanceof String)) {
            return;
        }
        String action = (String) button.getTag();
        if (!ImeToolbarSpec.isValidActionName(action)) {
            return;
        }
        button.setOnClickListener(v -> {
            performActionHapticFeedback(v);
            if ("paste".equals(action)) {
                performPasteAction(inputMethodService, v.getContext());
            } else if ("undo".equals(action)) {
                performUndoAction(inputMethodService);
            } else if ("delete".equals(action)) {
                performDeleteAction(inputMethodService);
            } else if ("select_all".equals(action)) {
                performEditorAction(inputMethodService, android.R.id.selectAll);
            } else if ("copy".equals(action)) {
                performEditorAction(inputMethodService, android.R.id.copy);
            } else if ("switch_ime".equals(action)) {
                showInputMethodPicker(v.getContext());
            } else if ("welt_window".equals(action)) {
                String packageName = editorPackageName(inputMethodService);
                Log.i(TAG, "button_click editorPackage=" + packageName
                        + " service=" + inputMethodService.getClass().getName());
                ImeWindowModeBridge.request(v.getContext(), packageName);
            }
        });
    }

    private static String editorPackageName(Object service) {
        if (service instanceof InputMethodService) {
            android.view.inputmethod.EditorInfo editorInfo =
                    ((InputMethodService) service).getCurrentInputEditorInfo();
            return editorInfo == null ? null : editorInfo.packageName;
        }
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(service, "getCurrentInputEditorInfo");
        return value instanceof android.view.inputmethod.EditorInfo
                ? ((android.view.inputmethod.EditorInfo) value).packageName : null;
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

    private static void refreshActionButtonState(Object inputMethodService, View button) {
        if (button == null || !(button.getTag() instanceof String)) {
            return;
        }
        String action = (String) button.getTag();
        if ("paste".equals(action)) {
            boolean enabled = getCurrentInputConnectionCompat(inputMethodService) != null;
            button.setEnabled(enabled);
            button.setAlpha(enabled ? 1f : 0.55f);
        } else if (ImeToolbarSpec.isValidActionName(action)) {
            button.setEnabled(true);
            button.setAlpha(1f);
        }
    }

    private static void performActionHapticFeedback(View button) {
        if (button != null) {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private static void showInputMethodPicker(Context context) {
        InputMethodManager imm = context == null
                ? null
                : (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private static void performDeleteAction(Object service) {
        InputConnection connection = getCurrentInputConnectionCompat(service);
        if (connection == null) {
            return;
        }
        CharSequence selectedText = connection.getSelectedText(0);
        if (!TextUtils.isEmpty(selectedText)) {
            connection.commitText("", 1);
            return;
        }
        connection.deleteSurroundingText(1, 0);
    }

    private static void performUndoAction(Object service) {
        InputConnection connection = getCurrentInputConnectionCompat(service);
        if (connection == null || performContextMenuAction(connection, android.R.id.undo)) {
            return;
        }
        long downTime = SystemClock.uptimeMillis();
        connection.sendKeyEvent(new KeyEvent(
                downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON));
        connection.sendKeyEvent(new KeyEvent(
                downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON));
    }

    private static void performEditorAction(Object service, int actionId) {
        performContextMenuAction(getCurrentInputConnectionCompat(service), actionId);
    }

    private static boolean performContextMenuAction(InputConnection connection, int actionId) {
        return connection != null && connection.performContextMenuAction(actionId);
    }

    private static void performPasteAction(Object service, Context context) {
        InputConnection connection = getCurrentInputConnectionCompat(service);
        if (connection == null || context == null) {
            return;
        }
        if (performContextMenuAction(connection, android.R.id.paste)) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData != null && clipData.getItemCount() > 0) {
            CharSequence text = clipData.getItemAt(0).coerceToText(context);
            if (!TextUtils.isEmpty(text)) {
                connection.commitText(text, 1);
            }
        }
    }

    private static InputConnection getCurrentInputConnectionCompat(Object service) {
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(service, "getCurrentInputConnection");
        return value instanceof InputConnection ? (InputConnection) value : null;
    }
}
