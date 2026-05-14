package com.example.flymestatusbarsizer.feature.clock;

public final class ClockDetailActionSpec {
    public static final int SLOT_COUNT = 5;
    public static final String TYPE_EMPTY = "empty";
    public static final String TYPE_ASSISTANT_ACTION = "assistant_action";
    public static final String TYPE_APP = "app";
    public static final String TYPE_INTENT = "intent";
    public static final String TYPE_ACTIVITY = "activity";

    public final int slot;
    public final String type;
    public final String title;
    public final String assistantAction;
    public final String packageName;
    public final String componentName;
    public final String intentUri;

    public ClockDetailActionSpec(
            int slot,
            String type,
            String title,
            String packageName,
            String componentName,
            String intentUri) {
        this(slot, type, title, "", packageName, componentName, intentUri);
    }

    public ClockDetailActionSpec(
            int slot,
            String type,
            String title,
            String assistantAction,
            String packageName,
            String componentName,
            String intentUri) {
        this.slot = normalizeSlot(slot);
        this.type = normalizeType(type);
        this.title = sanitize(title);
        this.assistantAction = sanitize(assistantAction);
        this.packageName = sanitize(packageName);
        this.componentName = sanitize(componentName);
        this.intentUri = sanitize(intentUri);
    }

    public static ClockDetailActionSpec empty(int slot) {
        return new ClockDetailActionSpec(slot, TYPE_EMPTY, "", "", "", "", "");
    }

    public boolean isConfigured() {
        switch (type) {
            case TYPE_ASSISTANT_ACTION:
                return !assistantAction.isEmpty();
            case TYPE_APP:
                return !packageName.isEmpty();
            case TYPE_INTENT:
                return !intentUri.isEmpty();
            case TYPE_ACTIVITY:
                return !componentName.isEmpty();
            default:
                return false;
        }
    }

    public String getTargetValue() {
        switch (type) {
            case TYPE_ASSISTANT_ACTION:
                return assistantAction;
            case TYPE_APP:
                return packageName;
            case TYPE_INTENT:
                return intentUri;
            case TYPE_ACTIVITY:
                return componentName;
            default:
                return "";
        }
    }

    public static int normalizeSlot(int slot) {
        return Math.max(0, Math.min(SLOT_COUNT - 1, slot));
    }

    public static String normalizeType(String type) {
        if (type == null) {
            return TYPE_EMPTY;
        }
        String normalized = type.trim().toLowerCase();
        switch (normalized) {
            case TYPE_ASSISTANT_ACTION:
            case TYPE_APP:
            case TYPE_INTENT:
            case TYPE_ACTIVITY:
                return normalized;
            default:
                return TYPE_EMPTY;
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
