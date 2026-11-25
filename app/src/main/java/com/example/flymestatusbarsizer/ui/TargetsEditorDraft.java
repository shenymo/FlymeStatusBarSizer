package com.example.flymestatusbarsizer.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Unsaved editor draft state for target ordering and visibility rules. */
final class TargetsEditorDraft {
    private final List<String> defaults;
    private final List<String> order = new ArrayList<>();
    private final Set<String> hidden = new LinkedHashSet<>();
    private List<String> ruleOrder;

    TargetsEditorDraft(Collection<String> available, ResolverTargetsUiApi.Rules saved) {
        defaults = new ArrayList<>(new ResolverTargetsUiApi.Rules(available, Collections.emptyList()).order);
        LinkedHashSet<String> all = new LinkedHashSet<>(saved.order);
        all.addAll(defaults);
        all.addAll(saved.hidden);
        order.addAll(all);
        hidden.addAll(saved.hidden);
        ruleOrder = saved.order;
    }

    List<String> components() {
        return Collections.unmodifiableList(order);
    }

    boolean isHidden(String component) {
        return hidden.contains(component);
    }

    boolean hasFixedOrder() {
        return !ruleOrder.isEmpty();
    }

    void setHidden(String component, boolean value) {
        String key = ResolverTargetsUiApi.normalizeComponent(component);
        if (key.isEmpty()) return;
        if (value) hidden.add(key);
        else hidden.remove(key);
    }

    void setHiddenAll(Collection<String> components, boolean value) {
        for (String component : components) setHidden(component, value);
    }

    boolean move(String component, int destination) {
        int source = order.indexOf(component);
        if (source < 0 || destination < 0 || destination >= order.size() || source == destination) {
            return false;
        }
        order.add(destination, order.remove(source));
        ruleOrder = new ArrayList<>(order);
        return true;
    }

    void reset() {
        hidden.clear();
        followSystemOrder();
    }

    void followSystemOrder() {
        order.clear();
        order.addAll(defaults);
        for (String component : hidden) {
            if (!order.contains(component)) order.add(component);
        }
        ruleOrder = Collections.emptyList();
    }

    ResolverTargetsUiApi.Rules rules() {
        return new ResolverTargetsUiApi.Rules(ruleOrder, hidden);
    }
}
