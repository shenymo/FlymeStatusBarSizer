package com.example.flymestatusbarsizer.feature.ime;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.util.ArrayList;

public final class VerificationCodeClient {
    private VerificationCodeClient() {
    }

    static String read(Context context) {
        Bundle result = call(context, VerificationCodeProvider.METHOD_READ, null);
        return result == null ? null : result.getString(VerificationCodeProvider.KEY_CODE);
    }

    static void clear(Context context) {
        call(context, VerificationCodeProvider.METHOD_CLEAR, null);
    }

    static void publish(Context context, String code) {
        call(context, VerificationCodeProvider.METHOD_PUBLISH, code);
    }

    public static ArrayList<Bundle> readDiagnostics(Context context) {
        Bundle result = call(context, VerificationCodeProvider.METHOD_READ_DIAGNOSTICS, null);
        return result == null ? null : result.getParcelableArrayList(
                VerificationCodeProvider.KEY_DIAGNOSTICS);
    }

    public static void clearDiagnostics(Context context) {
        call(context, VerificationCodeProvider.METHOD_CLEAR_DIAGNOSTICS, null);
    }

    static void recordDiagnostic(Context context, String stage, boolean success, String reason) {
        if (context == null || !FlymeStatusBarSizer.loadImeConfig(context)
                .imeVerificationCodeDiagnosticsEnabled) {
            return;
        }
        Bundle event = new Bundle();
        event.putBoolean(VerificationCodeProvider.KEY_SUCCESS, success);
        event.putString(VerificationCodeProvider.KEY_REASON, reason);
        call(context, VerificationCodeProvider.METHOD_RECORD_DIAGNOSTIC, stage, event);
    }

    private static Bundle call(Context context, String method, String arg) {
        return call(context, method, arg, null);
    }

    private static Bundle call(Context context, String method, String arg, Bundle extras) {
        if (context == null) {
            return null;
        }
        ContentResolver resolver = context.getContentResolver();
        try {
            return resolver.call(VerificationCodeProvider.URI, method, arg, extras);
        } catch (IllegalArgumentException unavailableProvider) {
            return null;
        }
    }
}
