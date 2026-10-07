package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Rect;

/** Keep full portrait previews in three consistent slots, with only a narrow gap between cards. */
final class RecentTaskCardLayout {
    static final int MAX_CARDS = 3;
    static final int GAP_DP = 2;

    static Rect[] arrange(int width, int height, int count, float aspect, int gap) {
        count = Math.min(MAX_CARDS, Math.max(0, count));
        if (width <= 0 || height <= 0 || count == 0 || !Float.isFinite(aspect) || aspect <= 0) {
            return new Rect[0];
        }
        gap = Math.max(0, gap);
        int maxHeight = (height - gap * (MAX_CARDS + 1)) / MAX_CARDS;
        int cardWidth = Math.min(width - gap * 2, (int) Math.floor(maxHeight * aspect));
        int cardHeight = Math.min(maxHeight, (int) Math.floor(cardWidth / aspect));
        if (cardWidth <= 0 || cardHeight <= 0) return new Rect[0];
        int x = (width - cardWidth) / 2;
        int y = (height - count * cardHeight - (count - 1) * gap) / 2;
        Rect[] result = new Rect[count];
        for (int i = 0; i < count; i++) {
            result[i] = new Rect(x, y, x + cardWidth, y + cardHeight);
            y += cardHeight + gap;
        }
        return result;
    }

    private RecentTaskCardLayout() {}
}
