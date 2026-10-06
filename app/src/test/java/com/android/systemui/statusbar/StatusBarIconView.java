package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.service.notification.StatusBarNotification;
import android.widget.ImageView;

import com.example.flymestatusbarsizer.feature.notification.NotificationHooks;

/** Minimal SystemUI stand-in; invokes the tint hook after system colour changes. */
public final class StatusBarIconView extends ImageView {
    private final StatusBarNotification notification;
    public int currentIconColor = Color.BLACK;
    public PorterDuffColorFilter lastSystemColorFilter;

    public StatusBarIconView(Context context, StatusBarNotification notification) {
        super(context);
        this.notification = notification;
        setSystemTint(currentIconColor);
    }

    public StatusBarNotification getNotification() {
        return notification;
    }

    public void setSystemTint(int color) {
        currentIconColor = color;
        setImageTintList(ColorStateList.valueOf(color));
        updateIconColor();
    }

    public void updateIconColor() {
        lastSystemColorFilter = new PorterDuffColorFilter(currentIconColor, PorterDuff.Mode.SRC_IN);
        setColorFilter(lastSystemColorFilter);
        NotificationHooks.clearNotificationAppIconTintIfNeeded(this);
    }
}
