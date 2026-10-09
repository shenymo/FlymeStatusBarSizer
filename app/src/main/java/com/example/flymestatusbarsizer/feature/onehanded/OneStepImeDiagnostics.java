package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowInsets;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only diagnostics; no input text, Intent extras or window titles are recorded. */
public final class OneStepImeDiagnostics {
    static final String TAG = "FlymeOneStepIme";
    private static final WeakHashMap<Object, String> SERVER_STATES = new WeakHashMap<>();
    private static final AtomicBoolean INSTALL_STARTED = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_SERVER_CALLBACK = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_ERROR = new AtomicBoolean();

    private OneStepImeDiagnostics() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (!INSTALL_STARTED.compareAndSet(false, true)) return;
        int installed = 0;
        try {
            Class<?> display = Class.forName("com.android.server.wm.DisplayContent", false, loader);
            // The layout callback also covers showing the IME without changing its target.
            for (String name : new String[]{"updateImeParent", "adjustForImeIfNeeded", "updateImeControlTarget"}) {
                try {
                    Method method = "updateImeControlTarget".equals(name)
                            ? display.getDeclaredMethod(name, boolean.class)
                            : display.getDeclaredMethod(name);
                    method.setAccessible(true);
                    module.intercept(method, chain -> {
                        Object result = chain.proceed();
                        recordServerState(chain.getThisObject(), name);
                        return result;
                    });
                    installed++;
                    Log.i(TAG, "server hook installed=" + name);
                } catch (Throwable error) {
                    Log.w(TAG, "server hook unavailable=" + name, error);
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "server IME diagnostics unavailable", error);
        } finally {
            Log.i(TAG, "server hooks ready=" + installed + "/3");
        }
    }

    static String taskInfo(Object info) {
        if (info == null) return "taskInfo=unavailable";
        Object window = field(field(info, "configuration"), "windowConfiguration");
        return "task=" + field(info, "taskId") + " user=" + field(info, "userId")
                + " display=" + field(info, "displayId")
                + " top=" + component(field(info, "topActivity"))
                + " actualMode=" + call(info, "getWindowingMode")
                + " supportsMultiWindow=" + field(info, "supportsMultiWindow")
                + " resizeMode=" + field(info, "resizeMode")
                + " isResizeable=" + field(info, "isResizeable")
                + " focused=" + field(info, "isFocused")
                + " parentTask=" + field(info, "parentTaskId")
                + " bounds=" + call(window, "getBounds")
                + " appBounds=" + call(window, "getAppBounds")
                + appInfo(field(info, "topActivityInfo"));
    }

    private static String appInfo(Object info) {
        if (!(info instanceof ActivityInfo)) return " appInfo=unavailable";
        ActivityInfo activity = (ActivityInfo) info;
        ApplicationInfo app = activity.applicationInfo;
        return " activityResizeMode=" + field(activity, "resizeMode")
                + (app == null ? " applicationInfo=unavailable" : " uid=" + app.uid
                + " systemApp=" + ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                + " updatedSystemApp=" + ((app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                + " targetSdk=" + app.targetSdkVersion);
    }

    private static void recordServerState(Object display, String event) {
        try {
            if (REPORTED_SERVER_CALLBACK.compareAndSet(false, true)) {
                Log.i(TAG, "server observer-active event=" + event
                        + " display=" + call(display, "getDisplayId"));
            }
            Object input = field(display, "mImeInputTarget");
            Object inputWindow = call(input, "getWindowState");
            Object layering = field(display, "mImeLayeringTarget");
            // Hosted tasks exclude IME layout insets. Include the flag in the output;
            // task IDs correlate these candidates with the exact SystemUI workspace.
            if (!excludesIme(inputWindow) && !excludesIme(layering)) {
                synchronized (SERVER_STATES) {
                    if (SERVER_STATES.remove(display) != null)
                        Log.i(TAG, "server event=" + event + " display=" + call(display, "getDisplayId")
                                + " excludedImeTarget=false");
                }
                return;
            }
            Object control = field(display, "mImeControlTarget");
            Object container = field(display, "mImeWindowsContainer");
            Object parent = field(display, "mInputMethodSurfaceParent");
            Object appSurface = call(field(layering, "mActivityRecord"), "getSurfaceControl");
            Object displaySurface = call(call(container, "getParent"), "getSurfaceControl");
            String snapshot = "display=" + call(display, "getDisplayId")
                    + " visible=" + call(field(display, "mInputMethodWindow"), "isVisible")
                    + " input={" + window(inputWindow) + "} layering={" + window(layering) + "}"
                    + " control=" + (control == null ? "null" : control.getClass().getName())
                    + " controlIsInput=" + (control != null && control == inputWindow)
                    + " controlIsRemote=" + (control != null && control == field(display, "mRemoteInsetsControlTarget"))
                    + " remoteControlAvailable=" + (field(display, "mRemoteInsetsControlTarget") != null)
                    + " appControlsIme=" + call(display, "isImeControlledByApp")
                    + " shouldAttachToApp=" + call(display, "shouldImeAttachedToApp")
                    + " parentIsApp=" + sameSurface(parent, appSurface)
                    + " parentIsDisplay=" + sameSurface(parent, displaySurface)
                    + " parent={" + identity(field(display, "mInputMethodSurfaceParentWindow")) + "}"
                    + " imeContainerOrganized=" + call(container, "isOrganized");
            synchronized (SERVER_STATES) {
                if (snapshot.equals(SERVER_STATES.put(display, snapshot))) return;
            }
            Log.i(TAG, "server event=" + event + " " + snapshot);
        } catch (Throwable error) {
            unavailable(error);
        }
    }

    private static boolean excludesIme(Object window) {
        Object flags = field(window, "mMergedExcludeInsetsTypes");
        return flags instanceof Number && (((Number) flags).intValue() & WindowInsets.Type.ime()) != 0;
    }

    private static String window(Object window) {
        if (window == null) return "null";
        Object activity = field(window, "mActivityRecord");
        Object task = call(window, "getTask");
        Object attrs = field(window, "mAttrs");
        return identity(window) + " windowMode=" + call(window, "getWindowingMode")
                + " taskMode=" + call(task, "getWindowingMode")
                + " requestedTaskMode=" + call(task, "getRequestedOverrideWindowingMode")
                + " supportsMultiWindow=" + call(task, "supportsMultiWindow")
                + " shouldControlIme=" + call(window, "shouldControlIme")
                + " excludeInsets=" + field(window, "mMergedExcludeInsetsTypes")
                + " softInputMode=" + field(attrs, "softInputMode")
                + appInfo(field(activity, "info"));
    }

    private static String identity(Object container) {
        if (container == null) return "null";
        Object activity = field(container, "mActivityRecord");
        if (activity == null) activity = container;
        Object task = call(container, "getTask");
        return "kind=" + container.getClass().getSimpleName()
                + " task=" + field(task, "mTaskId")
                + " activity=" + component(field(activity, "mActivityComponent"));
    }

    private static Object sameSurface(Object first, Object second) {
        if (!(first instanceof SurfaceControl) || !(second instanceof SurfaceControl)) return "unavailable";
        try {
            return OneStepReflection.call(first, "isSameSurface", new Class<?>[]{SurfaceControl.class}, second);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return "unavailable";
        }
    }

    private static String component(Object value) {
        return value instanceof ComponentName ? ((ComponentName) value).flattenToShortString() : "unavailable";
    }

    private static Object field(Object target, String name) { return ReflectUtils.getField(target, name); }
    private static Object call(Object target, String name) { return ReflectUtils.invokeNoArg(target, name); }

    static void unavailable(Throwable error) {
        if (REPORTED_ERROR.compareAndSet(false, true)) Log.w(TAG, "Diagnostic snapshot unavailable", error);
    }
}
