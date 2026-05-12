package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

final class ImeToolbarViewFactory {
    private ImeToolbarViewFactory() {
    }

    static LinearLayout createToolbarView(Context context, Object inputMethodService, View inputView) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = FlymeStatusBarSizer.dp(context, 18);
        int vertical = FlymeStatusBarSizer.dp(context, 6);
        bar.setPadding(horizontal, vertical, horizontal, vertical);
        applyToolbarBackground(bar, inputView);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0,
                FlymeStatusBarSizer.dp(context, 40),
                1f);
        buttonParams.leftMargin = FlymeStatusBarSizer.dp(context, 4);
        buttonParams.rightMargin = FlymeStatusBarSizer.dp(context, 4);
        for (String action : ImeToolbarSpec.resolveToolbarOrder(FlymeStatusBarSizer.loadImeConfig(context))) {
            ImageButton button = new ImageButton(context);
            configureToolbarButton(context, button, action, ImeToolbarSpec.getActionLabel(action));
            button.setTag(action);
            bar.addView(button, new LinearLayout.LayoutParams(buttonParams));
        }
        applyToolbarIconTint(bar, ImeToolbarIcons.resolveIconColor(context));
        ImeToolbarActions.bindActionButtons(inputMethodService, bar);
        ImeToolbarActions.refreshActionButtonStates(inputMethodService, bar);
        return bar;
    }

    private static void applyToolbarBackground(LinearLayout bar, View inputView) {
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

    private static void configureToolbarButton(
            Context context, ImageButton button, String iconType, String desc) {
        Drawable drawable = ImeToolbarIcons.createIconDrawable(context, iconType);
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

    private static void applyToolbarIconTint(LinearLayout bar, int color) {
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
}
