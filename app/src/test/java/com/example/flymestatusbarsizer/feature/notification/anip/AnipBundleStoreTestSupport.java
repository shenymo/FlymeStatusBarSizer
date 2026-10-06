package com.example.flymestatusbarsizer.feature.notification.anip;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.File;

/** Helpers shared by the ANIP bundle tests. */
final class AnipBundleStoreTestSupport {
    private AnipBundleStoreTestSupport() {
    }

    /** A tiny real PNG, so decoding is exercised rather than stubbed. */
    static byte[] onePixelPng() {
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xFF112233);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
