// Adapted from OneStep4 (com.sangluo.onestep), licensed under AGPL-3.0.
// See licenses/OneStep4-AGPL-3.0.txt.
package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Rect;

import java.util.List;

/** Calculates main and side-window frames without owning or mutating any views. */
public final class OneStepWindowLayout {
    private OneStepWindowLayout() {
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

}
