package com.example.flymestatusbarsizer.feature.mback;

import android.graphics.drawable.Drawable;

final class MBackStarApp {
    static final MBackStarApp[] EMPTY_ARRAY = new MBackStarApp[0];

    final int taskId;
    final Drawable icon;
    final CharSequence label;

    MBackStarApp(int taskId, Drawable icon, CharSequence label) {
        this.taskId = taskId;
        this.icon = icon;
        this.label = label == null ? "" : label;
    }
}
