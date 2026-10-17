package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.UserHandle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.example.flymestatusbarsizer.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Owns recent-app toolbar views and the ordering of its task entries. */
final class OneStepRecentAppsBar {
    interface Actions {
        int iconSizePx();
        int iconPaddingPx();
        int exitPaddingPx();
        int dp(float value);
        boolean active();
        boolean dragging();
        boolean isHosted(RecentTaskCard card);
        ImageView dragSource();
        void select(RecentTaskCard card);
        boolean beginDrag(ImageView icon, RecentTaskCard card);
        void toggleSideWindows();
        void close();
    }

    private final Context context;
    private final android.os.Handler handler;
    private final Executor iconLoader;
    private final Actions actions;
    private final ArrayList<ImageView> icons = new ArrayList<>();
    private final ArrayList<RecentTaskCard> recentCards = new ArrayList<>();
    private final ArrayList<RecentTaskCard> pinnedCards = new ArrayList<>();
    private LinearLayout row;
    private HorizontalScrollView scroll;
    private ImageView exitButton;
    private ImageView sideWindowsButton;
    private boolean orderDirty;
    private int generation;

    OneStepRecentAppsBar(Context context, android.os.Handler handler, Executor iconLoader, Actions actions) {
        this.context = context;
        this.handler = handler;
        this.iconLoader = iconLoader;
        this.actions = actions;
    }

    View create(List<RecentTaskCard> recent, int generation) {
        this.generation = generation;
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOnClickListener(v -> { });
        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setContentDescription("应用选择栏，点击切换主窗口，长按拖入任意窗口");
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x80171c25);
        background.setCornerRadius(actions.dp(16));
        background.setStroke(Math.max(1, actions.dp(0.7f)), 0x30ffffff);
        toolbar.setBackground(background);
        toolbar.setClipToOutline(true);
        toolbar.setElevation(actions.dp(4));
        row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(actions.dp(8), 0, actions.dp(8), 0);
        row.setOnClickListener(v -> { });
        recentCards.clear();
        if (recent != null) recentCards.addAll(recent);
        orderDirty = true;
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        toolbar.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));

        sideWindowsButton = new ImageView(context);
        sideWindowsButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sideWindowsButton.setPadding(actions.exitPaddingPx(), actions.exitPaddingPx(),
                actions.exitPaddingPx(), actions.exitPaddingPx());
        setModuleIcon(sideWindowsButton, R.drawable.ic_workspace_side_windows,
                android.R.drawable.ic_menu_view);
        styleButton(sideWindowsButton, "显示侧边窗口", actions::toggleSideWindows);
        toolbar.addView(sideWindowsButton, new LinearLayout.LayoutParams(
                actions.iconSizePx(), actions.iconSizePx()));

        View divider = new View(context);
        divider.setBackgroundColor(0x30ffffff);
        toolbar.addView(divider, new LinearLayout.LayoutParams(Math.max(1, actions.dp(0.7f)), actions.dp(28)));

        exitButton = new ImageView(context);
        exitButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        exitButton.setPadding(actions.exitPaddingPx(), actions.exitPaddingPx(),
                actions.exitPaddingPx(), actions.exitPaddingPx());
        setModuleIcon(exitButton, R.drawable.ic_workspace_exit,
                android.R.drawable.ic_menu_close_clear_cancel);
        styleButton(exitButton, "退出工作台", actions::close);
        toolbar.addView(exitButton, new LinearLayout.LayoutParams(
                actions.iconSizePx(), actions.iconSizePx()));
        refresh();
        return toolbar;
    }

    private void setModuleIcon(ImageView view, int resource, int fallback) {
        try {
            Context module = context.createPackageContext(com.example.flymestatusbarsizer.BuildConfig.APPLICATION_ID, 0);
            view.setImageDrawable(module.getDrawable(resource));
        } catch (Exception error) {
            view.setImageResource(fallback);
            view.setColorFilter(android.graphics.Color.WHITE);
        }
    }

    private void styleButton(ImageView view, String description, Runnable click) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff), null, null));
        view.setContentDescription(description);
        view.setTooltipText(description);
        view.setFocusable(true);
        view.setOnClickListener(v -> click.run());
    }

    void updateToolbarControls(int iconSize, int exitPadding, boolean sideWindowsCollapsed) {
        if (sideWindowsButton != null) {
            sideWindowsButton.setPadding(exitPadding, exitPadding, exitPadding, exitPadding);
            sideWindowsButton.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            String description = sideWindowsCollapsed ? "显示侧边窗口" : "隐藏侧边窗口";
            sideWindowsButton.setContentDescription(description);
            sideWindowsButton.setTooltipText(description);
        }
        if (exitButton != null) {
            exitButton.setPadding(exitPadding, exitPadding, exitPadding, exitPadding);
            exitButton.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        }
        for (ImageView icon : icons) {
            icon.setPadding(actions.iconPaddingPx(), actions.iconPaddingPx(),
                    actions.iconPaddingPx(), actions.iconPaddingPx());
            icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        }
    }

    void setRecentRowPadding(int padding) {
        if (row != null) row.setPadding(padding, 0, padding, 0);
    }

    void setSideWindowsEnabled(boolean enabled, boolean collapsed) {
        if (sideWindowsButton == null) return;
        sideWindowsButton.setEnabled(enabled);
        sideWindowsButton.setAlpha(enabled ? 1f : 0.45f);
        String description = collapsed ? "显示侧边窗口" : "隐藏侧边窗口";
        sideWindowsButton.setContentDescription(description);
        sideWindowsButton.setTooltipText(description);
    }

    void setExitEnabled(boolean enabled) {
        if (exitButton != null) exitButton.setEnabled(enabled);
    }

    void pin(RecentTaskCard card) {
        if (!eligible(card)) return;
        pinnedCards.removeIf(item -> sameEntry(item, card));
        pinnedCards.add(0, card);
        orderDirty = true;
        refresh();
    }

    void remember(RecentTaskCard card) {
        if (!eligible(card)) return;
        for (RecentTaskCard item : recentCards) if (sameEntry(item, card)) return;
        recentCards.add(0, card);
        orderDirty = true;
    }

    void remove(RecentTaskCard card) {
        if (card == null) return;
        pinnedCards.removeIf(item -> item.sameTask(card));
        recentCards.removeIf(item -> item.sameTask(card));
        orderDirty = true;
        refresh();
    }

    void markOrderDirty() {
        orderDirty = true;
    }

    void clearPins() {
        pinnedCards.clear();
    }

    void refresh() {
        if (orderDirty && !actions.dragging() && row != null) {
            orderDirty = false;
            row.removeAllViews();
            icons.clear();
            ArrayList<RecentTaskCard> ordered = new ArrayList<>(pinnedCards);
            for (RecentTaskCard card : recentCards) {
                boolean duplicate = false;
                for (RecentTaskCard item : ordered) if (sameEntry(item, card)) { duplicate = true; break; }
                if (!duplicate && !card.home && !card.temporary) ordered.add(card);
            }
            final int request = generation;
            for (RecentTaskCard card : ordered) {
                if (card.component == null) continue;
                ImageView icon = new ImageView(context);
                icon.setTag(card);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setPadding(actions.iconPaddingPx(), actions.iconPaddingPx(),
                        actions.iconPaddingPx(), actions.iconPaddingPx());
                icon.setImageDrawable(context.getPackageManager().getDefaultActivityIcon());
                icon.setOnClickListener(v -> actions.select(card));
                icon.setHapticFeedbackEnabled(false);
                icon.setOnLongClickListener(v -> actions.beginDrag(icon, card));
                row.addView(icon, new LinearLayout.LayoutParams(actions.iconSizePx(), actions.iconSizePx()));
                icons.add(icon);
                iconLoader.execute(() -> {
                    Drawable drawable;
                    try {
                        android.content.pm.PackageManager packageManager = context.getPackageManager();
                        drawable = packageManager.getActivityIcon(card.component);
                        if (card.userId == OneStepRecentTasks.CLONE_USER_ID) {
                            drawable = packageManager.getUserBadgedIcon(drawable,
                                    UserHandle.getUserHandleForUid(card.userId * 100000));
                        }
                    }
                    catch (Exception error) { drawable = context.getPackageManager().getDefaultActivityIcon(); }
                    Drawable result = drawable;
                    handler.post(() -> {
                        if (actions.active() && generation == request && icon.getParent() == row)
                            icon.setImageDrawable(result);
                    });
                });
            }
            if (!pinnedCards.isEmpty() && scroll != null) scroll.scrollTo(0, 0);
        }
        for (ImageView icon : icons) {
            RecentTaskCard card = (RecentTaskCard) icon.getTag();
            boolean hosted = actions.isHosted(card);
            icon.setAlpha(icon == actions.dragSource() ? 0.4f : hosted ? 0.65f : 1f);
            icon.setLongClickable(true);
            icon.setContentDescription(card.description + (hosted ? "，已在工作台中" : "")
                    + "，点击切换主窗口，长按拖入任意窗口");
        }
    }

    void clear() {
        scroll = null;
        sideWindowsButton = null;
        exitButton = null;
        icons.clear();
        recentCards.clear();
        pinnedCards.clear();
        row = null;
        orderDirty = false;
    }

    private static boolean eligible(RecentTaskCard card) {
        return card != null && !card.home && card.component != null && !card.temporary;
    }

    private static boolean sameEntry(RecentTaskCard a, RecentTaskCard b) {
        return a.sameTask(b) && a.component != null && b.component != null
                && a.component.getPackageName().equals(b.component.getPackageName());
    }
}
