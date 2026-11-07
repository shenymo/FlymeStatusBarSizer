package com.example.flymestatusbarsizer.feature.ime;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

final class VerificationCodeClient {
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

    private static Bundle call(Context context, String method, String arg) {
        if (context == null) {
            return null;
        }
        ContentResolver resolver = context.getContentResolver();
        try {
            return resolver.call(VerificationCodeProvider.URI, method, arg, null);
        } catch (IllegalArgumentException unavailableProvider) {
            return null;
        }
    }
}
