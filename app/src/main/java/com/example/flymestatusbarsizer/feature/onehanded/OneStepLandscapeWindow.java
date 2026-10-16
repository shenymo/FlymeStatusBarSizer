package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Rect;

import java.util.List;

/**
 * Owns the landscape pane contract used by the task workspace.
 *
 * The pane is a workspace role, not a second display. This object keeps that
 * role's slot, viewport and routing rules together so the workspace can treat
 * it like a normal pane without duplicating landscape-specific conditions.
 */
final class OneStepLandscapeWindow {
    static final int SLOT = 4;
    static final int PANE_COUNT = SLOT + 1;

    private final Rect viewport = new Rect();

    void updateViewport(Rect logicalBounds) {
        viewport.set(viewportFor(logicalBounds));
    }

    static Rect viewportFor(Rect logicalBounds) {
        int longSide = Math.max(logicalBounds.width(), logicalBounds.height());
        return new Rect(0, 0, longSide, Math.max(1, Math.round(longSide * 9f / 16f)));
    }

    Rect viewport() {
        return viewport;
    }

    boolean isSlot(int slot) {
        return slot == SLOT;
    }

    boolean visible(boolean dragging, boolean hasContent) {
        return dragging || hasContent;
    }

    boolean directlyInteractive(int slot, int mainSlot) {
        return slot == mainSlot || isSlot(slot);
    }

    int destinationForOrientation(int slot, int mainSlot, int direction) {
        if (slot == mainSlot && direction > 0) return SLOT;
        if (slot == SLOT && direction < 0) return mainSlot;
        return -1;
    }

    Rect[] calculate(int width, int height, int divider, int toolbarHeight,
                     int viewportWidth, int viewportHeight, int mainSlot,
                     List<Integer> sideOrder, boolean mainOnLeft,
                     boolean showLandscape, boolean showSideWindows) {
        int reservedHeight = toolbarHeight + divider * 2;
        int landscapeHeight = showLandscape ? Math.max(1, Math.min(Math.round(width * 9f / 16f),
                Math.max(1, (height - reservedHeight) / 3))) : 0;
        int contentTop = Math.min(Math.max(0, height - 1), showLandscape
                ? landscapeHeight + reservedHeight
                : toolbarHeight + divider);
        Rect[] result = new Rect[SLOT + 1];
        if (showSideWindows) {
            calculateLowerPanes(result, width, contentTop,
                    Math.max(1, height - contentTop), divider, mainSlot, sideOrder, mainOnLeft,
                    viewportWidth, viewportHeight);
        } else {
            int mainLeft = 0;
            int mainWidth = width;
            if (!showLandscape) {
                int availableHeight = Math.max(1, height - contentTop);
                mainWidth = Math.max(1, Math.min(width,
                        Math.round(availableHeight * viewportWidth
                                / (float) Math.max(1, viewportHeight))));
                mainLeft = Math.max(0, (width - mainWidth) / 2);
            }
            result[mainSlot] = new Rect(mainLeft, contentTop, mainLeft + mainWidth, height);
            for (int slot : sideOrder) result[slot] = hiddenRect(width, height, divider);
        }
        if (showLandscape) {
            int landscapeWidth = Math.max(1, Math.min(width, Math.round(landscapeHeight * 16f / 9f)));
            int left = (width - landscapeWidth) / 2;
            result[SLOT] = new Rect(left, 0, left + landscapeWidth, landscapeHeight);
        } else result[SLOT] = new Rect(0, 0, 1, 1);
        return result;
    }

    private static Rect fitViewport(int width, int height, int viewportWidth, int viewportHeight) {
        double scale = Math.min(1d, Math.min(width / (double) viewportWidth,
                height / (double) viewportHeight));
        return new Rect(0, 0, Math.max(1, (int) Math.round(viewportWidth * scale)),
                Math.max(1, (int) Math.round(viewportHeight * scale)));
    }

    private static void calculateLowerPanes(Rect[] rects, int workspaceWidth,
                                             int contentTop, int contentHeight, int divider,
                                             int activeMainSlot, List<Integer> sideSlotOrder,
                                             boolean mainOnLeft, int viewportWidth, int viewportHeight) {
        int visibleSideCount = sideSlotOrder.size();
        int availableWidth = Math.max(2, workspaceWidth - divider);
        int sideHeight = Math.max(1,
                (contentHeight - divider * Math.max(0, visibleSideCount - 1))
                        / Math.max(1, visibleSideCount));
        int sideWidth = Math.round(sideHeight * availableWidth
                / (float) Math.max(1, contentHeight + sideHeight));
        sideWidth = Math.max(1, Math.min(availableWidth - 1, sideWidth));
        int mainWidth = availableWidth - sideWidth;
        int mainHeight = Math.max(1, Math.min(contentHeight,
                Math.round(mainWidth * viewportHeight
                        / (float) Math.max(1, viewportWidth))));
        int groupTop = contentTop + contentHeight - mainHeight;
        int sideContentHeight = Math.max(1,
                mainHeight - divider * Math.max(0, visibleSideCount - 1));
        sideHeight = Math.max(1, sideContentHeight / Math.max(1, visibleSideCount));
        int sideRemainder = Math.max(0, sideContentHeight - sideHeight * visibleSideCount);
        int sideLeft = mainOnLeft ? mainWidth + divider : 0;
        int mainLeft = mainOnLeft ? 0 : sideWidth + divider;

        rects[activeMainSlot] = new Rect(mainLeft, contentTop, mainLeft + mainWidth,
                groupTop + mainHeight);
        rects[activeMainSlot].top = groupTop;
        for (int index = 0; index < visibleSideCount; index++) {
            int slot = sideSlotOrder.get(index);
            int top = groupTop + index * (sideHeight + divider);
            int paneHeight = sideHeight + (index == visibleSideCount - 1 ? sideRemainder : 0);
            rects[slot] = new Rect(sideLeft, top, sideLeft + sideWidth, top + paneHeight);
        }
    }

    private static Rect hiddenRect(int workspaceWidth, int workspaceHeight, int hiddenMargin) {
        int left = workspaceWidth + hiddenMargin;
        int top = workspaceHeight + hiddenMargin;
        return new Rect(left, top, left + 1, top + 1);
    }

    String emptyText() {
        return "拖入应用 · 横屏运行";
    }

    String emptyDescription() {
        return "横屏窗口，长按应用图标拖入此处";
    }
}
