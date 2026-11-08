package com.example.flymestatusbarsizer.feature.ime;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class VerificationCodeProvider extends ContentProvider {
    static final String METHOD_PUBLISH = "publish";
    static final String METHOD_READ = "read";
    static final String METHOD_CLEAR = "clear";
    static final String METHOD_RECORD_DIAGNOSTIC = "record_diagnostic";
    static final String METHOD_READ_DIAGNOSTICS = "read_diagnostics";
    static final String METHOD_CLEAR_DIAGNOSTICS = "clear_diagnostics";
    static final String KEY_CODE = "code";
    public static final String KEY_STAGE = "stage";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_REASON = "reason";
    public static final String KEY_TIME = "time";
    public static final String KEY_DIAGNOSTICS = "diagnostics";
    static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID
            + ".verificationcode/code");
    private static final long EXPIRY_MS = 3 * 60 * 1000L;
    private static final Pattern CODE_PATTERN = Pattern.compile("[0-9]{4,8}");

    private static String code;
    private static long expiresAt;
    private static Handler handler;
    private static Context providerContext;
    private static final Object DIAGNOSTIC_LOCK = new Object();
    private static final ArrayDeque<Bundle> DIAGNOSTICS = new ArrayDeque<>();
    private static int lastReportedReadState = -1;
    private static final Runnable EXPIRY = VerificationCodeProvider::expire;

    @Override
    public boolean onCreate() {
        handler = new Handler(Looper.getMainLooper());
        providerContext = getContext().getApplicationContext();
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_RECORD_DIAGNOSTIC.equals(method)) {
            if (isDiagnosticWriter() && extras != null) {
                appendDiagnostic(arg, extras.getBoolean(KEY_SUCCESS),
                        extras.getString(KEY_REASON));
            }
            return Bundle.EMPTY;
        }
        if (METHOD_READ_DIAGNOSTICS.equals(method)) {
            if (!isModuleCaller()) {
                return Bundle.EMPTY;
            }
            Bundle result = new Bundle();
            ArrayList<Bundle> events = new ArrayList<>();
            synchronized (DIAGNOSTIC_LOCK) {
                for (Bundle event : DIAGNOSTICS) {
                    events.add(new Bundle(event));
                }
            }
            result.putParcelableArrayList(KEY_DIAGNOSTICS, events);
            return result;
        }
        if (METHOD_CLEAR_DIAGNOSTICS.equals(method)) {
            if (isModuleCaller()) {
                synchronized (DIAGNOSTIC_LOCK) {
                    DIAGNOSTICS.clear();
                }
                lastReportedReadState = -1;
            }
            return Bundle.EMPTY;
        }
        if (METHOD_PUBLISH.equals(method)) {
            if (!isPhoneCaller()) {
                return Bundle.EMPTY;
            }
            if (arg == null || !CODE_PATTERN.matcher(arg).matches()) {
                appendDiagnostic("共享发布", false, "Provider 拒绝了无效验证码");
                return Bundle.EMPTY;
            }
            code = arg;
            expiresAt = SystemClock.elapsedRealtime() + EXPIRY_MS;
            lastReportedReadState = -1;
            handler.removeCallbacks(EXPIRY);
            handler.postDelayed(EXPIRY, EXPIRY_MS);
            appendDiagnostic("共享发布", true, "Provider 已接受验证码");
            notifyChanged();
            return Bundle.EMPTY;
        }
        if (!isEnabledImeCaller()) {
            return Bundle.EMPTY;
        }
        expireIfNeeded();
        Bundle result = new Bundle();
        if (METHOD_READ.equals(method)) {
            result.putString(KEY_CODE, code);
            int readState = code == null ? 0 : 1;
            if (readState != lastReportedReadState && appendDiagnostic(
                    "输入法读取", readState == 1,
                    readState == 1 ? "输入法已读取到可用验证码" : "没有可用验证码")) {
                lastReportedReadState = readState;
            }
        } else if (METHOD_CLEAR.equals(method)) {
            code = null;
            expiresAt = 0L;
            lastReportedReadState = -1;
            handler.removeCallbacks(EXPIRY);
            appendDiagnostic("验证码消费", true, "输入法已消费验证码");
            notifyChanged();
        }
        return result;
    }

    private boolean isPhoneCaller() {
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        return packages != null && Arrays.asList(packages).contains("com.android.phone");
    }

    private boolean isModuleCaller() {
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        return packages != null && Arrays.asList(packages).contains(BuildConfig.APPLICATION_ID);
    }

    private boolean isDiagnosticWriter() {
        return isPhoneCaller() || isEnabledImeCaller();
    }

    private static boolean appendDiagnostic(String stage, boolean success, String reason) {
        Context context = providerContext;
        if (TextUtils.isEmpty(stage) || context == null
                || !SettingsStore.readBoolean(
                        SettingsStore.prefs(context),
                        SettingsStore.KEY_IME_VERIFICATION_CODE_DIAGNOSTICS_ENABLED,
                        SettingsStore.DEFAULT_IME_VERIFICATION_CODE_DIAGNOSTICS_ENABLED)) {
            return false;
        }
        Bundle event = new Bundle();
        event.putString(KEY_STAGE, stage);
        event.putBoolean(KEY_SUCCESS, success);
        event.putString(KEY_REASON, TextUtils.isEmpty(reason) ? "无附加信息" : reason);
        event.putLong(KEY_TIME, System.currentTimeMillis());
        synchronized (DIAGNOSTIC_LOCK) {
            if (DIAGNOSTICS.size() >= 50) {
                DIAGNOSTICS.removeFirst();
            }
            DIAGNOSTICS.addLast(event);
        }
        return true;
    }

    private boolean isEnabledImeCaller() {
        Context context = getContext();
        int uid = Binder.getCallingUid();
        InputMethodManager manager = (InputMethodManager)
                context.getSystemService(InputMethodService.INPUT_METHOD_SERVICE);
        if (manager == null) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        List<InputMethodInfo> methods = manager.getEnabledInputMethodList();
        for (InputMethodInfo method : methods) {
            try {
                if (packageManager.getApplicationInfo(method.getPackageName(), 0).uid == uid) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return false;
    }

    private static void expire() {
        if (code != null && SystemClock.elapsedRealtime() >= expiresAt) {
            code = null;
            expiresAt = 0L;
            lastReportedReadState = -1;
            appendDiagnostic("验证码过期", false, "验证码已超过 3 分钟有效期");
            Handler current = handler;
            if (current != null) {
                current.removeCallbacks(EXPIRY);
            }
            notifyChanged();
        }
    }

    private static void expireIfNeeded() {
        if (code != null && SystemClock.elapsedRealtime() >= expiresAt) {
            code = null;
            expiresAt = 0L;
            lastReportedReadState = -1;
            appendDiagnostic("验证码过期", false, "验证码已超过 3 分钟有效期");
            handler.removeCallbacks(EXPIRY);
            notifyChanged();
        }
    }

    private static void notifyChanged() {
        // The provider process owns the runtime-only code shared by phone and IME processes.
        Context context = providerContext;
        if (context != null) {
            context.getContentResolver().notifyChange(URI, null);
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
