package com.example.flymestatusbarsizer.feature.mback;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

public final class MBackHooks {
    private MBackHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        module.installMBackHooks(loader);
    }
}
