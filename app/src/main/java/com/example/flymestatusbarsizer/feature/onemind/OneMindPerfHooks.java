package com.example.flymestatusbarsizer.feature.onemind;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class OneMindPerfHooks {
    private OneMindPerfHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        FlymeStatusBarSizer.logOneMindStatus("install start");
        int hooks = 0;
        hooks += hookPerfCoreWrap(module, loader);
        hooks += hookBoostFramework(module, loader);
        hooks += hookHardCoderManager(module, loader);
        FlymeStatusBarSizer.logOneMindStatus("install finished, hooks=" + hooks);
        FlymeStatusBarSizer.recordOneMindHooksInstalled();
    }

    private static int hookPerfCoreWrap(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.meizu.perf.PerfCoreWrap", false, loader);
            int hooks = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!intReturn(method)) {
                    continue;
                }
                if ("c".equals(name) || "d".equals(name) || "e".equals(name) || "f".equals(name)) {
                    hooks += intercept(module, method, -1);
                }
            }
            FlymeStatusBarSizer.logOneMindStatus("PerfCoreWrap hooks=" + hooks);
            return hooks;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logOneMindWarning("Failed to hook PerfCoreWrap", t);
            return 0;
        }
    }

    private static int hookBoostFramework(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.meizu.perf.c", false, loader);
            int hooks = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!intReturn(method)) {
                    continue;
                }
                if ("a".equals(name) || "b".equals(name) || "c".equals(name) || "d".equals(name)) {
                    hooks += intercept(module, method, -1);
                }
            }
            FlymeStatusBarSizer.logOneMindStatus("BoostFramework hooks=" + hooks);
            return hooks;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logOneMindWarning("Failed to hook BoostFramework", t);
            return 0;
        }
    }

    private static int hookHardCoderManager(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.meizu.perf.HardCoderManager", false, loader);
            int hooks = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (intReturn(method)
                        && ("requestCpuHighFreq".equals(name)
                        || "requestCpuCoreForThread".equals(name)
                        || "requestUnifyCpuIOThreadCore".equals(name))) {
                    hooks += intercept(module, method, 0);
                }
            }
            FlymeStatusBarSizer.logOneMindStatus("HardCoderManager hooks=" + hooks);
            return hooks;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logOneMindWarning("Failed to hook HardCoderManager", t);
            return 0;
        }
    }

    private static boolean intReturn(Method method) {
        return method != null && method.getReturnType() == int.class;
    }

    private static int intercept(FlymeStatusBarSizer module, Method method, int result) {
        method.setAccessible(true);
        String hookPoint = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        module.intercept(method, chain -> {
            Object[] args = chain.getArgs().toArray();
            boolean disableEnabled = FlymeStatusBarSizer.isOneMindPerfDisableEnabled();
            FlymeStatusBarSizer.logOneMindStatus("before " + hookPoint
                    + ", disableEnabled=" + disableEnabled
                    + ", args=" + argsToString(args));
            if (!disableEnabled) {
                Object originalResult = chain.proceed();
                FlymeStatusBarSizer.logOneMindStatus("after " + hookPoint
                        + ", originalResult=" + valueToString(originalResult));
                return originalResult;
            }
            FlymeStatusBarSizer.recordOneMindPerfIntercept(hookPoint);
            FlymeStatusBarSizer.logOneMindStatus("after " + hookPoint
                    + ", originalSkipped=true, returnOverride=" + result);
            return result;
        });
        FlymeStatusBarSizer.logOneMindStatus("hook installed " + hookPoint
                + ", params=" + method.getParameterTypes().length
                + ", result=" + result);
        return 1;
    }

    private static String argsToString(Object[] args) {
        try {
            return Arrays.deepToString(args == null ? new Object[0] : args);
        } catch (Throwable t) {
            return "[args-format-error]";
        }
    }

    private static String valueToString(Object value) {
        if (value == null) {
            return "null";
        }
        return String.valueOf(value);
    }
}
