package com.example.flymestatusbarsizer.feature.onehanded;

import android.net.Uri;

import com.example.flymestatusbarsizer.BuildConfig;

public final class OneStepWorkspaceProtocol {
    public static final String URI = "flymestatusbarsizer://workspace/open";
    public static final String ACTION_OPEN = BuildConfig.APPLICATION_ID + ".action.OPEN_WORKSPACE";

    private OneStepWorkspaceProtocol() { }

    public static boolean matches(Uri uri) {
        return uri != null && "flymestatusbarsizer".equals(uri.getScheme())
                && "workspace".equals(uri.getHost()) && "/open".equals(uri.getPath());
    }
}
