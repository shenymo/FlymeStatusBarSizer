package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

public final class ClockHooks {
    private ClockHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        module.installClockHooks(loader);
    }
}
