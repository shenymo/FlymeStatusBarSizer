package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

final class ImeToolbarController {
    private static final String TAG_IME_TOOLBAR_ROOT = "flyme_status_bar_sizer_ime_toolbar_root";
    private static final String TAG_IME_TOOLBAR_ORIGINAL = "flyme_status_bar_sizer_ime_toolbar_original";

    private ImeToolbarController() {
    }

    static void refreshToolbarNow(Object inputMethodService, View inputView) {
        if (inputMethodService == null || inputView == null) {
            return;
        }
        detachToolbarIfPresent(inputMethodService);
        attachToolbarIfNeeded(inputMethodService, inputView);
    }

    static void attachToolbarIfNeeded(Object inputMethodService, View inputView) {
        if (inputMethodService == null || inputView == null) {
            return;
        }
        Context context = inputView.getContext();
        if (context == null) {
            return;
        }
        FlymeStatusBarSizer.ImeConfigSnapshot config = FlymeStatusBarSizer.loadImeConfig(context);
        if (!config.enabled
                || !config.imeToolbarEnabled
                || ImeToolbarSpec.shouldEmbedInStockControlBar(config)) {
            detachToolbarIfPresent(inputMethodService);
            return;
        }
        ViewGroup inputFrame = asViewGroup(FlymeStatusBarSizer.getFieldCompat(inputMethodService, "mInputFrame"));
        if (inputFrame == null) {
            return;
        }
        View current = inputFrame.getChildCount() > 0 ? inputFrame.getChildAt(0) : null;
        if (current != null && TAG_IME_TOOLBAR_ROOT.equals(current.getTag())) {
            updateToolbarState(inputMethodService, current);
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
        container.addView(ImeToolbarViewFactory.createToolbarView(context, inputMethodService, current),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        inputFrame.addView(container, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        inputFrame.requestLayout();
        inputFrame.invalidate();
    }

    static void detachToolbarIfPresent(Object inputMethodService) {
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

    private static void updateToolbarState(Object inputMethodService, View toolbarRoot) {
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
        LinearLayout rebuiltBar =
                ImeToolbarViewFactory.createToolbarView(group.getContext(), inputMethodService, originalInputView);
        group.addView(rebuiltBar, new LinearLayout.LayoutParams(
                layoutParams != null ? layoutParams.width : ViewGroup.LayoutParams.MATCH_PARENT,
                layoutParams != null ? layoutParams.height : ViewGroup.LayoutParams.WRAP_CONTENT));
        group.requestLayout();
        group.invalidate();
    }

    private static ViewGroup asViewGroup(Object object) {
        return object instanceof ViewGroup ? (ViewGroup) object : null;
    }
}
