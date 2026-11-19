package com.example.flymestatusbarsizer.feature.share;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.function.Function;

/** Component-level rules. Never adds a target that cannot handle the current share. */
public final class ShareTargetRules {
    public static final ShareTargetRules EMPTY = parse("", "");
    private final List<String> order;
    private final Set<String> hidden;
    private final Map<String, Integer> positions;

    public ShareTargetRules(Collection<String> order, Collection<String> hidden) {
        this.order = Collections.unmodifiableList(new ArrayList<>(normalize(order)));
        this.hidden = Collections.unmodifiableSet(normalize(hidden));
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (int i = 0; i < this.order.size(); i++) {
            ranks.put(this.order.get(i), i);
        }
        this.positions = Collections.unmodifiableMap(ranks);
    }

    public static ShareTargetRules parse(String order, String hidden) {
        return new ShareTargetRules(lines(order), lines(hidden));
    }

    private static List<String> lines(String value) {
        return value == null || value.isEmpty()
                ? Collections.emptyList() : java.util.Arrays.asList(value.split("\\r?\\n"));
    }

    private static LinkedHashSet<String> normalize(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String component = normalizeComponent(value);
                if (!component.isEmpty()) result.add(component);
            }
        }
        return result;
    }

    public static String normalizeComponent(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        int slash = trimmed.indexOf('/');
        if (slash <= 0 || slash != trimmed.lastIndexOf('/') || slash == trimmed.length() - 1) {
            return "";
        }
        String pkg = trimmed.substring(0, slash);
        String cls = trimmed.substring(slash + 1);
        if (!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")
                || !cls.matches("[A-Za-z0-9_.$]+")) return "";
        if (cls.startsWith(".")) cls = pkg + cls;
        return pkg + "/" + cls;
    }

    public List<String> order() { return order; }
    public Set<String> hidden() { return hidden; }
    public String encodeOrder() { return String.join("\n", order); }
    public String encodeHidden() { return String.join("\n", hidden); }
    public boolean isEmpty() { return order.isEmpty() && hidden.isEmpty(); }

    public <T> List<T> filterHidden(List<T> source, Function<T, String> componentOf) {
        if (hidden.isEmpty()) return source;
        List<T> result = new ArrayList<>(source);
        result.removeIf(item -> hidden.contains(componentOf.apply(item)));
        return result;
    }

    public <T> List<T> apply(List<T> source, Function<T, String> componentOf) {
        List<T> result = filterHidden(source, componentOf);
        if (order.isEmpty()) return result;
        Map<T, Integer> ranks = new IdentityHashMap<>();
        for (T item : result) {
            ranks.put(item, positions.getOrDefault(componentOf.apply(item), Integer.MAX_VALUE));
        }
        // List.sort is stable: new/unconfigured targets retain the system's relative order.
        result.sort((left, right) -> Integer.compare(ranks.get(left), ranks.get(right)));
        return result;
    }
}
