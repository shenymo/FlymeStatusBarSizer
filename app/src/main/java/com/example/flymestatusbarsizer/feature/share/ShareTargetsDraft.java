package com.example.flymestatusbarsizer.feature.share;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Unsaved editor state, including rules for temporarily unavailable applications. */
public final class ShareTargetsDraft {
    private final List<String> defaults;
    private final List<String> order = new ArrayList<>();
    private final Set<String> hidden = new LinkedHashSet<>();
    private List<String> ruleOrder;

    public ShareTargetsDraft(Collection<String> available, ShareTargetRules saved) {
        defaults = new ArrayList<>(new ShareTargetRules(available, Collections.emptyList()).order());
        LinkedHashSet<String> all = new LinkedHashSet<>(saved.order());
        all.addAll(defaults);
        all.addAll(saved.hidden());
        order.addAll(all);
        hidden.addAll(saved.hidden());
        ruleOrder = saved.order();
    }

    public List<String> components() { return Collections.unmodifiableList(order); }
    public boolean isHidden(String component) { return hidden.contains(component); }
    public boolean hasFixedOrder() { return !ruleOrder.isEmpty(); }

    public void setHidden(String component, boolean value) {
        String key = ShareTargetRules.normalizeComponent(component);
        if (key.isEmpty()) return;
        if (value) hidden.add(key); else hidden.remove(key);
    }

    public void setHiddenAll(Collection<String> components, boolean value) {
        for (String component : components) setHidden(component, value);
    }

    public boolean move(String component, int destination) {
        int source = order.indexOf(component);
        if (source < 0 || destination < 0 || destination >= order.size() || source == destination) {
            return false;
        }
        order.add(destination, order.remove(source));
        ruleOrder = new ArrayList<>(order);
        return true;
    }

    public void reset() {
        hidden.clear();
        followSystemOrder();
    }

    public void followSystemOrder() {
        order.clear();
        order.addAll(defaults);
        // Keep hidden rules for applications that are temporarily unavailable.
        for (String component : hidden) {
            if (!order.contains(component)) order.add(component);
        }
        ruleOrder = Collections.emptyList();
    }

    public ShareTargetRules rules() {
        // Newly discovered entries belong in the preview, but are only pinned after an actual move.
        return new ShareTargetRules(ruleOrder, hidden);
    }
}
