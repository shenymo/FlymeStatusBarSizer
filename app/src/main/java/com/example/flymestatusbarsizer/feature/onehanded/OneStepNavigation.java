package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;

/** Owns only this workspace's Home/Overview disable request. */
final class OneStepNavigation {
    // NavigationBar publishes these as SYSUI_STATE_HOME_DISABLED / OVERVIEW_DISABLED.
    private static final int DISABLED = 0x00200000 | 0x01000000;
    private final IBinder token = new Binder();
    private final Object service;
    private final String packageName;
    private int lockedUser = -1;

    OneStepNavigation(Context context) throws Exception {
        packageName = context.getPackageName();
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "statusbar");
        service = Class.forName("com.android.internal.statusbar.IStatusBarService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        if (service == null) throw new IllegalStateException("Status bar service unavailable");
        OneStepReflection.method(service.getClass(), "disableForUser",
                int.class, IBinder.class, String.class, int.class);
    }

    void setLocked(boolean locked, int userId) throws Exception {
        int nextUser = locked ? userId : -1;
        if (nextUser == lockedUser) return;
        if (lockedUser >= 0) {
            disable(0, lockedUser);
            lockedUser = -1;
        }
        if (nextUser >= 0) {
            disable(DISABLED, nextUser);
            lockedUser = nextUser;
        }
    }

    private void disable(int flags, int userId) throws Exception {
        // A separate token lets Android combine this with keyguard/pinning/other clients.
        // Clearing our request never clears another client's disabled flags.
        OneStepReflection.call(service, "disableForUser",
                new Class<?>[]{int.class, IBinder.class, String.class, int.class},
                flags, token, packageName, userId);
    }
}
