package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

final class RemoteSettingsSync {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final Object LOCK = new Object();

    private static volatile boolean listenerRegistered;
    private static volatile Context appContext;
    private static volatile SharedPreferences remotePrefs;

    private RemoteSettingsSync() {
    }

    static void prepare(Context context) {
        Context normalized = normalizeContext(context);
        if (normalized == null) {
            return;
        }
        appContext = normalized;
        synchronized (LOCK) {
            if (listenerRegistered) {
                syncFromLocal(normalized);
                return;
            }
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService service) {
                    synchronized (LOCK) {
                        try {
                            remotePrefs = service.getRemotePreferences(SettingsStore.PREFS);
                        } catch (Throwable t) {
                            remotePrefs = null;
                            Log.w(TAG, "Failed to obtain remote preferences from Xposed service", t);
                        }
                    }
                    syncFromLocal(appContext);
                }

                @Override
                public void onServiceDied(XposedService service) {
                    synchronized (LOCK) {
                        remotePrefs = null;
                    }
                }
            });
            listenerRegistered = true;
        }
        syncFromLocal(normalized);
    }

    static void syncFromLocal(Context context) {
        Context normalized = normalizeContext(context);
        if (normalized == null) {
            return;
        }
        appContext = normalized;
        SharedPreferences localPrefs = SettingsStore.prefs(normalized);
        SharedPreferences remote = remotePrefs;
        if (localPrefs == null || remote == null) {
            return;
        }
        try {
            SharedPreferences.Editor editor = remote.edit().clear();
            Map<String, ?> values = localPrefs.getAll();
            writeTypedValues(editor, values, SettingsStore.BOOLEAN_KEYS);
            writeTypedValues(editor, values, SettingsStore.INT_KEYS);
            writeTypedValues(editor, values, SettingsStore.STRING_KEYS);
            editor.apply();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to sync local settings to remote preferences", t);
        }
    }

    private static void writeTypedValues(SharedPreferences.Editor editor, Map<String, ?> values, String[] keys) {
        if (editor == null || values == null || keys == null) {
            return;
        }
        for (String key : keys) {
            if (!values.containsKey(key)) {
                continue;
            }
            Object value = values.get(key);
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value != null) {
                editor.putString(key, String.valueOf(value));
            }
        }
    }

    private static Context normalizeContext(Context context) {
        if (context == null) {
            return null;
        }
        return context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }
}
