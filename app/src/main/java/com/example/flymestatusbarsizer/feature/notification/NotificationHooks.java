package com.example.flymestatusbarsizer.feature.notification;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

public final class NotificationHooks {
    private NotificationHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        module.installNotificationHooks(loader);
    }
}
