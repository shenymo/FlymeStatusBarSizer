package com.example.flymestatusbarsizer.feature.onehanded;

import java.util.ArrayList;
import java.util.List;

/** Slot order belongs to the current one-handed session, not the changing system MRU order. */
final class RecentTaskCardSlots {
    private List<RecentTaskCard> cards = new ArrayList<>();
    private List<RecentTaskCard> beforeSwap;
    private TaskScaleTarget previousMain;
    private RecentTaskCard main;
    private RecentTaskCard previousMainPreview;

    List<RecentTaskCard> cards() { return cards; }

    void update(List<RecentTaskCard> recent) {
        List<RecentTaskCard> merged = new ArrayList<>();
        for (RecentTaskCard slot : cards) {
            for (RecentTaskCard fresh : recent) {
                if (slot.sameTask(fresh)) { merged.add(fresh); break; }
            }
        }
        for (RecentTaskCard fresh : recent) {
            boolean present = false;
            for (RecentTaskCard slot : merged) if (slot.sameTask(fresh)) present = true;
            if (!present && merged.size() < RecentTaskCardLayout.MAX_CARDS) merged.add(fresh);
        }
        cards = merged;
    }

    void rememberMain(RecentTaskCard card) { main = card; }

    RecentTaskCard begin(TaskScaleTarget current, RecentTaskCard selected) {
        int index = -1;
        for (int i = 0; i < cards.size(); i++) if (selected.sameTask(cards.get(i))) index = i;
        if (index < 0) throw new IllegalStateException("Selected card is no longer in this layout");
        previousMain = current;
        previousMainPreview = main;
        beforeSwap = new ArrayList<>(cards);
        RecentTaskCard oldMain = main != null && main.matches(current) ? main
                : new RecentTaskCard(current.taskId, selected.userId, current.token, "之前的应用", null);
        cards = new ArrayList<>(cards);
        cards.set(index, oldMain);
        main = selected;
        return oldMain;
    }

    void end(TaskScaleTarget current) {
        if (beforeSwap != null && previousMain.sameTask(current)) {
            cards = beforeSwap;
            main = previousMainPreview;
        }
        beforeSwap = null;
        previousMain = null;
        previousMainPreview = null;
    }

    void clear() {
        cards = new ArrayList<>();
        beforeSwap = null;
        previousMain = null;
        previousMainPreview = null;
        main = null;
    }
}
