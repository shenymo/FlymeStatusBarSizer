package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;

import dalvik.system.PathClassLoader;

final class OneMindHookPointDetector {
    private static final String PPS_PACKAGE = "com.meizu.pps";

    private OneMindHookPointDetector() {
    }

    static String detect(Context context) {
        if (context == null) {
            return "检测失败：无 Context";
        }
        ArrayList<String> missing = new ArrayList<>();
        try {
            ApplicationInfo appInfo = getPpsAppInfo(context);
            ClassLoader loader = new PathClassLoader(appInfo.sourceDir, context.getClassLoader());
            checkIntMethods(loader, "com.meizu.perf.PerfCoreWrap", "PerfCoreWrap",
                    new String[]{"c", "d", "e", "f"}, missing);
            checkIntMethods(loader, "com.meizu.perf.c", "BoostFramework",
                    new String[]{"a", "b", "c", "d"}, missing);
            checkIntMethods(loader, "com.meizu.perf.HardCoderManager", "HardCoderManager",
                    new String[]{
                            "requestCpuHighFreq",
                            "requestCpuCoreForThread",
                            "requestUnifyCpuIOThreadCore"
                    }, missing);
        } catch (PackageManager.NameNotFoundException e) {
            return "未安装 com.meizu.pps";
        } catch (Throwable t) {
            String message = t.getMessage();
            return "检测失败：" + (TextUtils.isEmpty(message)
                    ? t.getClass().getSimpleName()
                    : message);
        }
        if (missing.isEmpty()) {
            return "Hook 点有效：PerfCoreWrap / BoostFramework / HardCoderManager";
        }
        return "Hook 点缺失：" + TextUtils.join("、", missing);
    }

    @SuppressWarnings("deprecation")
    private static ApplicationInfo getPpsAppInfo(Context context)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return pm.getApplicationInfo(
                    PPS_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(0));
        }
        return pm.getApplicationInfo(PPS_PACKAGE, 0);
    }

    private static void checkIntMethods(ClassLoader loader, String className, String label,
            String[] methodNames, ArrayList<String> missing) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            for (String methodName : methodNames) {
                if (!hasIntMethod(clazz, methodName)) {
                    missing.add(label + "." + methodName);
                }
            }
        } catch (Throwable ignored) {
            missing.add(label);
        }
    }

    private static boolean hasIntMethod(Class<?> clazz, String name) {
        if (clazz == null || name == null) {
            return false;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (name.equals(method.getName()) && method.getReturnType() == int.class) {
                return true;
            }
        }
        return false;
    }
}
