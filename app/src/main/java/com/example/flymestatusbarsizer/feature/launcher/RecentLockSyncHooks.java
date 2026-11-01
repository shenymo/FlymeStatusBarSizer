package com.example.flymestatusbarsizer.feature.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Keeps Flyme's process-local recents lock cache aligned with its shared system setting. */
public final class RecentLockSyncHooks {
    private static final String SETTING = "recent_lock_apps";
    private static final String TAG = "FlymeStatusBarSizer";
    private static boolean installed;
    private static boolean observerRegistered;

    private RecentLockSyncHooks() { }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null || installed) return;
        try {
            Class<?> manager = Class.forName("com.meizu.systemui.shared.model.LockTaskManager", false, loader);
            Method create = manager.getDeclaredMethod("getAndCreateManager", Context.class);
            create.setAccessible(true);
            module.intercept(create, chain -> {
                Object result = chain.proceed();
                Context context = chain.getArg(0) instanceof Context ? (Context) chain.getArg(0) : null;
                if (context != null) {
                    Context app = context.getApplicationContext();
                    sync(result, app);
                    registerObserver(module, result, app);
                }
                return result;
            });
            Class<?> taskView = Class.forName("com.android.quickstep.views.TaskView", false, loader);
            Method isLocked = taskView.getDeclaredMethod("isTaskLocked");
            isLocked.setAccessible(true);
            module.intercept(isLocked, chain -> {
                refreshTaskFlags(chain.getThisObject(), manager);
                return chain.proceed();
            });
            installed = true;
        } catch (Throwable error) {
            module.log(android.util.Log.WARN, TAG, "Recent lock synchronization unavailable", error);
        }
    }

    private static void registerObserver(FlymeStatusBarSizer module, Object manager, Context context) {
        if (observerRegistered) return;
        Uri uri = Settings.System.getUriFor(SETTING);
        context.getContentResolver().registerContentObserver(uri, false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override public void onChange(boolean selfChange, Uri changedUri) {
                        sync(manager, context);
                    }
                });
        observerRegistered = true;
    }

    private static void sync(Object manager, Context context) {
        try {
            Set<String> locked = new HashSet<>();
            String stored = Settings.System.getString(context.getContentResolver(), SETTING);
            if (stored != null) {
                for (String key : stored.split(";")) if (!key.isEmpty()) locked.add(key);
            }
            @SuppressWarnings("unchecked")
            Set<String> cached = (Set<String>) field(manager.getClass(), "mLockedPackage").get(manager);
            cached.clear();
            cached.addAll(locked);
            SharedPreferences preferences = (SharedPreferences) field(manager.getClass(), "mSharedPreferences").get(manager);
            preferences.edit().putStringSet("packageList", locked).apply();
        } catch (Exception error) {
            FlymeStatusBarSizer.logLauncherWarning("Failed to synchronize Flyme recent locks", error);
        }
    }

    private static void refreshTaskFlags(Object taskView, Class<?> managerClass) throws Exception {
        Object manager = managerClass.getDeclaredMethod("getManager").invoke(null);
        if (manager == null) return;
        @SuppressWarnings("unchecked")
        Set<String> locked = (Set<String>) field(managerClass, "mLockedPackage").get(manager);
        Object containers = taskView.getClass().getMethod("getTaskContainers").invoke(taskView);
        if (!(containers instanceof Iterable)) return;
        for (Object container : (Iterable<?>) containers) {
            Object task = container.getClass().getMethod("getTask").invoke(container);
            String key = (String) field(task.getClass(), "keyName").get(task);
            field(task.getClass(), "isMzLocked").setBoolean(task, locked.contains(key));
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field result = current.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
