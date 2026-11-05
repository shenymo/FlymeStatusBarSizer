package com.example.flymestatusbarsizer.feature.onehanded;

import android.net.Uri;

public final class ImeWeltWindowProtocol {
    public static final String URI = "flymestatusbarsizer://window/welt";

    private ImeWeltWindowProtocol() {
    }

    public static boolean matches(Uri uri) {
        return uri != null && "flymestatusbarsizer".equals(uri.getScheme())
                && "window".equals(uri.getHost()) && "/welt".equals(uri.getPath());
    }
}
