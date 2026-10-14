// Adapted from OneStep4 (com.sangluo.onestep), licensed under AGPL-3.0.
// See licenses/OneStep4-AGPL-3.0.txt.
package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Rect;

import java.util.List;

/** Calculates main and side-window frames without owning or mutating any views. */
public final class OneStepWindowLayout {
    private OneStepWindowLayout() {
    }

    public static Rect[] calculateLandscapeWorkspace(int width, int height, int divider,
                                                     int toolbarHeight, int viewportWidth,
                                                     int viewportHeight, int mainSlot,
                                                     List<Integer> sideOrder, boolean mainOnLeft,
                                                     int landscapeSlot, boolean showLandscape,
                                                     boolean showSideWindows,
                                                     int toolbarPosition,
                                                     boolean keepMainAspectRatio) {
        int reservedHeight = toolbarHeight + divider * 2;
        int landscapeHeight = showLandscape ? Math.max(1, Math.min(Math.round(width * 9f / 16f),
                Math.max(1, (height - reservedHeight) / 3))) : 0;
        int contentTop = Math.min(Math.max(0, height - 1), showLandscape
                ? landscapeHeight + reservedHeight : toolbarHeight + divider + Math.max(0, toolbarPosition));
        Rect[] result = new Rect[landscapeSlot + 1];
        // Keep the landscape pane large, then fill the lower area with fixed gaps.
        // The workspace derives a shared app viewport from the resulting main frame.
        if (!showLandscape && showSideWindows) {
            // Portrait panes must keep the app viewport's aspect ratio. The generic
            // lower-pane splitter fills the column height, then uniform TaskView
            // scaling leaves a large unused strip below every side window.
            Rect[] portrait = calculateWorkspace(width, height, divider, contentTop,
                    viewportWidth, viewportHeight, mainSlot, sideOrder, mainOnLeft);
            result = java.util.Arrays.copyOf(portrait, landscapeSlot + 1);
            result[landscapeSlot] = new Rect(0, 0, 1, 1);
            return result;
        } else if (showSideWindows) {
            calculateLowerPanes(result, width, height, contentTop,
                    Math.max(1, height - contentTop), divider, mainSlot, sideOrder, mainOnLeft);
        } else {
            int mainLeft = 0;
            int mainWidth = width;
            if (!showLandscape && keepMainAspectRatio) {
                int availableHeight = Math.max(1, height - contentTop);
                mainWidth = Math.max(1, Math.min(width,
                        Math.round(availableHeight * viewportWidth / (float) Math.max(1, viewportHeight))));
                mainLeft = Math.max(0, (width - mainWidth) / 2);
            }
            result[mainSlot] = new Rect(mainLeft, contentTop, mainLeft + mainWidth, height);
            for (int slot : sideOrder) {
                result[slot] = hiddenRect(width, height, divider);
            }
        }
        if (showLandscape) {
            int landscapeWidth = Math.max(1, Math.min(width, Math.round(landscapeHeight * 16f / 9f)));
            int left = (width - landscapeWidth) / 2;
            result[landscapeSlot] = new Rect(left, 0, left + landscapeWidth, landscapeHeight);
        } else result[landscapeSlot] = new Rect(0, 0, 1, 1);
        return result;
    }

    public static Rect[] calculateWorkspace(int workspaceWidth, int workspaceHeight,
                                            int divider, int topChromeHeight,
                                            int viewportWidth, int viewportHeight,
                                            int activeMainSlot, List<Integer> sideSlotOrder,
                                            boolean mainOnLeft) {
        int sideCount = sideSlotOrder.size();
        Rect[] rects = new Rect[sideCount + 1];
        int contentHeight = Math.max(1, workspaceHeight - topChromeHeight);
        int verticalGaps = divider * Math.max(0, sideCount - 1);
        double aspectRatio = viewportWidth / (double) viewportHeight;
        // The main pane and the full side column share a height. Solve their combined
        // width using the app's actual aspect ratio before assigning any positions.
        double heightForWidth = (sideCount * (workspaceWidth - divider) / aspectRatio
                + verticalGaps) / (sideCount + 1);
        int mainHeight = Math.max(1, (int) Math.floor(Math.min(contentHeight, heightForWidth)));
        int sideHeight = Math.max(1, (mainHeight - verticalGaps) / Math.max(1, sideCount));
        int sideWidth = Math.max(1, (int) Math.round(sideHeight * aspectRatio));
        int mainWidth = Math.max(1, Math.min(workspaceWidth - divider - sideWidth,
                (int) Math.round(mainHeight * aspectRatio)));
        Rect main = fitViewport(mainWidth, mainHeight, viewportWidth, viewportHeight);
        Rect side = fitViewport(sideWidth, sideHeight, viewportWidth, viewportHeight);
        int sideColumnHeight = side.height() * sideCount + verticalGaps;
        int groupHeight = Math.max(main.height(), sideColumnHeight);
        int groupTop = workspaceHeight - groupHeight;
        int groupLeft = Math.max(0, (workspaceWidth - main.width() - divider - side.width()) / 2);
        int mainLeft = mainOnLeft ? groupLeft : groupLeft + side.width() + divider;
        int sideLeft = mainOnLeft ? groupLeft + main.width() + divider : groupLeft;
        main.offsetTo(mainLeft, groupTop + (groupHeight - main.height()) / 2);
        rects[activeMainSlot] = main;
        int sideTop = groupTop + (groupHeight - sideColumnHeight) / 2;
        for (int index = 0; index < sideCount; index++) {
            Rect frame = new Rect(side);
            frame.offsetTo(sideLeft, sideTop + index * (side.height() + divider));
            rects[sideSlotOrder.get(index)] = frame;
        }
        return rects;
    }

    private static Rect fitViewport(int width, int height, int viewportWidth, int viewportHeight) {
        double scale = Math.min(1d, Math.min(width / (double) viewportWidth,
                height / (double) viewportHeight));
        return new Rect(0, 0, Math.max(1, (int) Math.round(viewportWidth * scale)),
                Math.max(1, (int) Math.round(viewportHeight * scale)));
    }

    private static void calculateLowerPanes(Rect[] rects, int workspaceWidth,
                                             int workspaceHeight, int contentTop,
                                             int contentHeight, int divider,
                                             int activeMainSlot, List<Integer> sideSlotOrder,
                                             boolean mainOnLeft) {
        int visibleSideCount = sideSlotOrder.size();
        int availableWidth = Math.max(2, workspaceWidth - divider);
        int sideHeight = Math.max(1,
                (contentHeight - divider * Math.max(0, visibleSideCount - 1))
                        / Math.max(1, visibleSideCount));
        int sideWidth = Math.round(sideHeight * availableWidth
                / (float) Math.max(1, contentHeight + sideHeight));
        sideWidth = Math.max(1, Math.min(availableWidth - 1, sideWidth));
        int mainWidth = availableWidth - sideWidth;
        int sideLeft = mainOnLeft ? mainWidth + divider : 0;
        int mainLeft = mainOnLeft ? 0 : sideWidth + divider;

        rects[activeMainSlot] = new Rect(
                mainLeft, contentTop, mainLeft + mainWidth, workspaceHeight);
        for (int index = 0; index < visibleSideCount; index++) {
            int slot = sideSlotOrder.get(index);
            int top = contentTop + index * (sideHeight + divider);
            rects[slot] = new Rect(sideLeft, top, sideLeft + sideWidth, top + sideHeight);
        }
    }

    private static Rect hiddenRect(int workspaceWidth, int workspaceHeight, int hiddenMargin) {
        int left = workspaceWidth + hiddenMargin;
        int top = workspaceHeight + hiddenMargin;
        return new Rect(left, top, left + 1, top + 1);
    }
}
