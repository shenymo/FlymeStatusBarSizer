package com.example.flymestatusbarsizer.feature.clock;

import android.content.Intent;
import android.graphics.drawable.Drawable;

public final class ClockDetailActionEntry {
    public static final ClockDetailActionEntry[] EMPTY_ARRAY = new ClockDetailActionEntry[0];

    public final int slot;
    public final String type;
    public final String resolvedLabel;
    public final Drawable icon;
    public final Intent launchIntent;
    public final boolean valid;

    public ClockDetailActionEntry(
            int slot,
            String type,
            String resolvedLabel,
            Intent launchIntent,
            boolean valid) {
        this(slot, type, resolvedLabel, null, launchIntent, valid);
    }

    public ClockDetailActionEntry(
            int slot,
            String type,
            String resolvedLabel,
            Drawable icon,
            Intent launchIntent,
            boolean valid) {
        this.slot = ClockDetailActionSpec.normalizeSlot(slot);
        this.type = ClockDetailActionSpec.normalizeType(type);
        this.resolvedLabel = resolvedLabel == null ? "" : resolvedLabel.trim();
        this.icon = cloneDrawable(icon);
        this.launchIntent = launchIntent == null ? null : new Intent(launchIntent);
        this.valid = valid && this.launchIntent != null;
    }

    public static ClockDetailActionEntry empty(int slot) {
        return new ClockDetailActionEntry(slot, ClockDetailActionSpec.TYPE_EMPTY, "", null, false);
    }

    public boolean hasDisplayLabel() {
        return !resolvedLabel.isEmpty();
    }

    public boolean hasIcon() {
        return icon != null;
    }

    private static Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        Drawable.ConstantState constantState = drawable.getConstantState();
        if (constantState != null) {
            Drawable copy = constantState.newDrawable();
            return copy == null ? null : copy.mutate();
        }
        return drawable;
    }
}
