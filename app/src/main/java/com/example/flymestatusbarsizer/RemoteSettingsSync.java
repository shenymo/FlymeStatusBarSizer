package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

final class RemoteSettingsSync {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final String KEY_REMOTE_SYNC_SCHEMA_VERSION = "__remote_sync_schema_version";
    private static final int REMOTE_SYNC_SCHEMA_VERSION = 1;
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
            if (!listenerRegistered) {
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
                    syncIfNeeded(appContext);
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
        }
        syncIfNeeded(normalized);
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
            Map<String, ?> values = localPrefs.getAll();
            Map<String, ?> remoteValues = remote.getAll();
            SharedPreferences.Editor editor = null;
            if (readRemoteSchemaVersion(remoteValues) != REMOTE_SYNC_SCHEMA_VERSION) {
                editor = remote.edit();
                editor.putInt(KEY_REMOTE_SYNC_SCHEMA_VERSION, REMOTE_SYNC_SCHEMA_VERSION);
            }
            editor = syncKnownKeys(remote, editor, values, remoteValues, SettingsStore.BOOLEAN_KEYS);
            editor = syncKnownKeys(remote, editor, values, remoteValues, SettingsStore.INT_KEYS);
            editor = syncKnownKeys(remote, editor, values, remoteValues, SettingsStore.STRING_KEYS);
            if (editor != null) {
                editor.apply();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to sync local settings to remote preferences", t);
        }
    }

    private static void syncIfNeeded(Context context) {
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
            Map<String, ?> localValues = localPrefs.getAll();
            Map<String, ?> remoteValues = remote.getAll();
            if (readRemoteSchemaVersion(remoteValues) != REMOTE_SYNC_SCHEMA_VERSION
                    || hasKnownKeyDifferences(localValues, remoteValues, SettingsStore.BOOLEAN_KEYS)
                    || hasKnownKeyDifferences(localValues, remoteValues, SettingsStore.INT_KEYS)
                    || hasKnownKeyDifferences(localValues, remoteValues, SettingsStore.STRING_KEYS)) {
                syncFromLocal(normalized);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to determine whether remote preferences need sync", t);
        }
    }

    private static SharedPreferences.Editor syncKnownKeys(SharedPreferences remote,
            SharedPreferences.Editor editor,
            Map<String, ?> localValues,
            Map<String, ?> remoteValues,
            String[] keys) {
        if (remote == null || localValues == null || remoteValues == null || keys == null) {
            return editor;
        }
        for (String key : keys) {
            boolean hasLocal = localValues.containsKey(key);
            boolean hasRemote = remoteValues.containsKey(key);
            if (!hasLocal) {
                if (!hasRemote) {
                    continue;
                }
                if (editor == null) {
                    editor = remote.edit();
                }
                editor.remove(key);
                continue;
            }
            Object localValue = localValues.get(key);
            if (hasRemote && areEquivalentValues(localValue, remoteValues.get(key))) {
                continue;
            }
            if (editor == null) {
                editor = remote.edit();
            }
            putTypedValue(editor, key, localValue);
        }
        return editor;
    }

    private static void putTypedValue(SharedPreferences.Editor editor, String key, Object value) {
        if (editor == null || key == null) {
            return;
        }
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
        } else {
            editor.remove(key);
        }
    }

    private static boolean areEquivalentValues(Object localValue, Object remoteValue) {
        if (localValue == remoteValue) {
            return true;
        }
        if (localValue == null || remoteValue == null) {
            return false;
        }
        if (localValue instanceof Number && remoteValue instanceof Number) {
            Number localNumber = (Number) localValue;
            Number remoteNumber = (Number) remoteValue;
            if (localValue instanceof Float || localValue instanceof Double
                    || remoteValue instanceof Float || remoteValue instanceof Double) {
                return Double.compare(localNumber.doubleValue(), remoteNumber.doubleValue()) == 0;
            }
            return localNumber.longValue() == remoteNumber.longValue();
        }
        return localValue.equals(remoteValue);
    }

    private static boolean hasKnownKeyDifferences(Map<String, ?> localValues,
            Map<String, ?> remoteValues,
            String[] keys) {
        if (localValues == null || remoteValues == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            boolean hasLocal = localValues.containsKey(key);
            boolean hasRemote = remoteValues.containsKey(key);
            if (hasLocal != hasRemote) {
                return true;
            }
            if (!hasLocal) {
                continue;
            }
            if (!areEquivalentValues(localValues.get(key), remoteValues.get(key))) {
                return true;
            }
        }
        return false;
    }

    private static int readRemoteSchemaVersion(Map<String, ?> remoteValues) {
        if (remoteValues == null) {
            return -1;
        }
        Object value = remoteValues.get(KEY_REMOTE_SYNC_SCHEMA_VERSION);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static Context normalizeContext(Context context) {
        if (context == null) {
            return null;
        }
        return context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }
}
