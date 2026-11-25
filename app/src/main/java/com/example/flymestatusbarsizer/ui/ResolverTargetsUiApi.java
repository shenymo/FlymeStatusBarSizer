package com.example.flymestatusbarsizer.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.openwith.OpenWithContentType;
import com.example.flymestatusbarsizer.feature.openwith.OpenWithTargetCatalog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** UI data and persistence contract for managing ordinary Resolver ("打开方式") candidates. */
public final class ResolverTargetsUiApi {
    private static volatile Provider provider = new FallbackProvider();

    private ResolverTargetsUiApi() {}

    public static void setProvider(Provider customProvider) {
        provider = customProvider != null ? customProvider : new FallbackProvider();
    }

    public static Provider getProvider() {
        return provider;
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
                || !cls.matches("[A-Za-z0-9_.$]+")) {
            return "";
        }
        if (cls.startsWith(".")) cls = pkg + cls;
        return pkg + "/" + cls;
    }

    public interface Provider {
        boolean isEnabled(Context context);

        void setEnabled(Context context, boolean enabled);

        List<ContentType> getContentTypes(Context context);

        CatalogSnapshot loadTargets(Context context, ContentType type, BooleanSupplier cancelled);

        SavedState loadRules(Context context);

        void saveRules(Context context, SavedState state);
    }

    public static final class ContentType {
        public final String id;
        public final String label;
        public final String tabLabel;
        public final boolean isDefault;

        public ContentType(String id, String label, String tabLabel, boolean isDefault) {
            this.id = id;
            this.label = label;
            this.tabLabel = tabLabel;
            this.isDefault = isDefault;
        }
    }

    public static final class Target {
        public final String component;
        public final String label;
        public final String appName;
        public final Drawable icon;
        public final Drawable appIcon;

        public Target(String component, String label, String appName, Drawable icon, Drawable appIcon) {
            this.component = component;
            this.label = label;
            this.appName = appName;
            this.icon = icon;
            this.appIcon = appIcon;
        }
    }

    public static final class CatalogSnapshot {
        public final List<Target> targets;
        public final boolean systemOrderAvailable;

        public CatalogSnapshot(List<Target> targets, boolean systemOrderAvailable) {
            this.targets = targets == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(targets));
            this.systemOrderAvailable = systemOrderAvailable;
        }
    }

    public static final class Rules {
        public static final Rules EMPTY = new Rules(Collections.emptyList(), Collections.emptySet());

        public final List<String> order;
        public final Set<String> hidden;

        public Rules(Collection<String> order, Collection<String> hidden) {
            this.order = Collections.unmodifiableList(new ArrayList<>(normalize(order)));
            this.hidden = Collections.unmodifiableSet(normalize(hidden));
        }

        public static Rules parse(String order, String hidden) {
            return new Rules(lines(order), lines(hidden));
        }

        public String encodeOrder() {
            return String.join("\n", order);
        }

        public String encodeHidden() {
            return String.join("\n", hidden);
        }

        private static List<String> lines(String value) {
            return value == null || value.isEmpty()
                    ? Collections.emptyList() : Arrays.asList(value.split("\\r?\\n"));
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
    }

    public static final class SavedState {
        public final Rules defaultRules;
        public final Map<String, Rules> typeOverrides;

        public SavedState(Rules defaultRules, Map<String, Rules> typeOverrides) {
            this.defaultRules = defaultRules == null ? Rules.EMPTY : defaultRules;
            this.typeOverrides = typeOverrides == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(typeOverrides));
        }
    }

    private static final class FallbackProvider implements Provider {
        private static final List<ContentType> DEFAULT_TYPES = createContentTypes();

        private static List<ContentType> createContentTypes() {
            List<ContentType> result = new ArrayList<>();
            for (OpenWithContentType type : OpenWithContentType.values()) {
                result.add(new ContentType(type.name(), type.label, type.label,
                        type == OpenWithContentType.DEFAULT));
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public boolean isEnabled(Context context) {
            SharedPreferences prefs = SettingsStore.prefs(context);
            return prefs != null && SettingsStore.readBoolean(prefs,
                    SettingsStore.KEY_OPEN_WITH_TARGETS_ENABLED,
                    SettingsStore.DEFAULT_OPEN_WITH_TARGETS_ENABLED);
        }

        @Override
        public void setEnabled(Context context, boolean enabled) {
            SharedPreferences prefs = SettingsStore.prefs(context);
            if (prefs == null) return;
            prefs.edit().putBoolean(SettingsStore.KEY_OPEN_WITH_TARGETS_ENABLED, enabled).apply();
            SettingsStore.notifyChanged(context);
        }

        @Override
        public List<ContentType> getContentTypes(Context context) {
            return DEFAULT_TYPES;
        }

        @Override
        public CatalogSnapshot loadTargets(Context context, ContentType type, BooleanSupplier cancelled) {
            OpenWithContentType contentType = OpenWithContentType.valueOf(type.id);
            OpenWithTargetCatalog.Snapshot snapshot = OpenWithTargetCatalog.load(context, contentType);
            List<Target> targets = new ArrayList<>(snapshot.targets.size());
            for (OpenWithTargetCatalog.Target target : snapshot.targets) {
                if (cancelled.getAsBoolean()) break;
                targets.add(new Target(target.component, target.label, target.appName,
                        target.icon, target.appIcon));
            }
            return new CatalogSnapshot(targets, snapshot.available);
        }

        @Override
        public SavedState loadRules(Context context) {
            SharedPreferences prefs = SettingsStore.prefs(context);
            if (prefs == null) return new SavedState(Rules.EMPTY, Collections.emptyMap());
            Rules defaults = Rules.parse(
                    SettingsStore.readString(prefs, SettingsStore.KEY_OPEN_WITH_TARGET_ORDER, ""),
                    SettingsStore.readString(prefs, SettingsStore.KEY_OPEN_WITH_HIDDEN_TARGETS, ""));
            Map<String, Rules> overrides = new LinkedHashMap<>();
            String encoded = SettingsStore.readString(prefs,
                    SettingsStore.KEY_OPEN_WITH_TARGET_PROFILES, "");
            if (!encoded.isEmpty()) {
                try {
                    JSONObject root = new JSONObject(encoded);
                    for (ContentType type : DEFAULT_TYPES) {
                        if (type.isDefault) continue;
                        JSONObject item = root.optJSONObject(type.id);
                        if (item != null && item.opt("order") instanceof String
                                && item.opt("hidden") instanceof String) {
                            overrides.put(type.id, Rules.parse(
                                    item.getString("order"), item.getString("hidden")));
                        }
                    }
                } catch (JSONException ignored) {
                }
            }
            return new SavedState(defaults, overrides);
        }

        @Override
        public void saveRules(Context context, SavedState state) {
            SharedPreferences prefs = SettingsStore.prefs(context);
            if (prefs == null) return;
            JSONObject root = new JSONObject();
            try {
                for (Map.Entry<String, Rules> entry : state.typeOverrides.entrySet()) {
                    JSONObject item = new JSONObject();
                    item.put("order", entry.getValue().encodeOrder());
                    item.put("hidden", entry.getValue().encodeHidden());
                    root.put(entry.getKey(), item);
                }
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
            prefs.edit()
                    .putString(SettingsStore.KEY_OPEN_WITH_TARGET_PROFILES, root.toString())
                    .putString(SettingsStore.KEY_OPEN_WITH_TARGET_ORDER, state.defaultRules.encodeOrder())
                    .putString(SettingsStore.KEY_OPEN_WITH_HIDDEN_TARGETS, state.defaultRules.encodeHidden())
                    .apply();
            SettingsStore.notifyChanged(context);
        }
    }
}
