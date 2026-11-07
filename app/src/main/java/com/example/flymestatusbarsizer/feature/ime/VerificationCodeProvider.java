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
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.example.flymestatusbarsizer.BuildConfig;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class VerificationCodeProvider extends ContentProvider {
    static final String METHOD_PUBLISH = "publish";
    static final String METHOD_READ = "read";
    static final String METHOD_CLEAR = "clear";
    static final String KEY_CODE = "code";
    static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID
            + ".verificationcode/code");
    private static final long EXPIRY_MS = 3 * 60 * 1000L;
    private static final Pattern CODE_PATTERN = Pattern.compile("[0-9]{4,8}");

    private static String code;
    private static long expiresAt;
    private static Handler handler;
    private static Context providerContext;
    private static final Runnable EXPIRY = VerificationCodeProvider::expire;

    @Override
    public boolean onCreate() {
        handler = new Handler(Looper.getMainLooper());
        providerContext = getContext().getApplicationContext();
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_PUBLISH.equals(method)) {
            if (!isPhoneCaller() || arg == null || !CODE_PATTERN.matcher(arg).matches()) {
                return Bundle.EMPTY;
            }
            code = arg;
            expiresAt = SystemClock.elapsedRealtime() + EXPIRY_MS;
            handler.removeCallbacks(EXPIRY);
            handler.postDelayed(EXPIRY, EXPIRY_MS);
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
        } else if (METHOD_CLEAR.equals(method)) {
            code = null;
            expiresAt = 0L;
            handler.removeCallbacks(EXPIRY);
            notifyChanged();
        }
        return result;
    }

    private boolean isPhoneCaller() {
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        return packages != null && Arrays.asList(packages).contains("com.android.phone");
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
