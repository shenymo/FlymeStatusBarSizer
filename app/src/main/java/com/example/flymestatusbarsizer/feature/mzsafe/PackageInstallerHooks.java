package com.example.flymestatusbarsizer.feature.mzsafe;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.graphics.Color;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class PackageInstallerHooks {
    private static final String ACTIVITY =
            "com.android.packageinstaller.FlymePackageInstallerActivity";

    private PackageInstallerHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (!FlymeStatusBarSizer.isPackageInstallerSafetyBypassEnabled()) {
            return;
        }
        try {
            Class<?> activityClass = Class.forName(ACTIVITY, false, loader);
            Method scanMethod = null;
            Method installMethod = null;
            for (Method method : activityClass.getDeclaredMethods()) {
                if ("startInstallScan".equals(method.getName())
                        && method.getParameterTypes().length == 0) {
                    scanMethod = method;
                } else if ("doInstallFlyme".equals(method.getName())
                        && method.getParameterTypes().length == 0) {
                    installMethod = method;
                }
            }
            if (scanMethod == null || installMethod == null) {
                return;
            }
            Method finalInstallMethod = installMethod;
            Method updateViewMethod = activityClass.getDeclaredMethod("updateViewForNewState", int.class);
            Method updateButtonMethod = activityClass.getDeclaredMethod("updatePrimaryButton", int.class);
            Field virusFinishedField = activityClass.getDeclaredField("mIsVirusCheckFinish");
            Field virusSafeField = activityClass.getDeclaredField("mIsVirusCheckResultSafe");
            Field receivedStoreInfoField = activityClass.getDeclaredField("receivedMzStoreInfo");
            Field disposalField = activityClass.getDeclaredField("isDisposaled");
            Field continueInstallField = activityClass.getDeclaredField("needContinueInstall");
            Field storeInfoField = activityClass.getDeclaredField("mzStoreAppInfo");
            Field warningLayoutField = activityClass.getDeclaredField("mWarningLayout");
            Field primaryButtonField = activityClass.getDeclaredField("primaryButton");
            scanMethod.setAccessible(true);
            finalInstallMethod.setAccessible(true);
            updateViewMethod.setAccessible(true);
            updateButtonMethod.setAccessible(true);
            virusFinishedField.setAccessible(true);
            virusSafeField.setAccessible(true);
            receivedStoreInfoField.setAccessible(true);
            disposalField.setAccessible(true);
            continueInstallField.setAccessible(true);
            storeInfoField.setAccessible(true);
            warningLayoutField.setAccessible(true);
            primaryButtonField.setAccessible(true);
            module.intercept(scanMethod, chain -> {
                try {
                    Object activity = chain.getThisObject();
                    virusFinishedField.setBoolean(activity, true);
                    virusSafeField.setBoolean(activity, true);
                    receivedStoreInfoField.setBoolean(activity, true);
                    disposalField.setInt(activity, 0);
                    continueInstallField.setBoolean(activity, true);

                    Object storeInfo = storeInfoField.get(activity);
                    Field icpStatusField = storeInfo.getClass().getDeclaredField("icpStatus");
                    icpStatusField.setAccessible(true);
                    icpStatusField.setBoolean(storeInfo, true);

                    updateViewMethod.invoke(activity, 3);
                    updateButtonMethod.invoke(activity, 1);

                    View warningLayout = (View) warningLayoutField.get(activity);
                    warningLayout.setVisibility(View.GONE);
                    View primaryButton = (View) primaryButtonField.get(activity);
                    if (primaryButton instanceof android.widget.TextView) {
                        android.widget.TextView textButton = (android.widget.TextView) primaryButton;
                        textButton.setText("安装");
                        textButton.setTextColor(Color.WHITE);
                    }
                    primaryButton.setOnClickListener(view -> {
                        try {
                            finalInstallMethod.invoke(activity);
                        } catch (Throwable error) {
                            module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                                    "Unable to start package installation", error);
                        }
                    });
                    return null;
                } catch (Throwable error) {
                    module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                            "Unable to prepare package installer confirmation", error);
                    return null;
                }
            });
        } catch (Throwable error) {
            module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                    "Unable to hook Flyme package installer", error);
        }
    }
}
