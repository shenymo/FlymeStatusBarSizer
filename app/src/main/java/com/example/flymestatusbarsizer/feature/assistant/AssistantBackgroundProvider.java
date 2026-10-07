package com.example.flymestatusbarsizer.feature.assistant;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import com.example.flymestatusbarsizer.BuildConfig;

import java.io.FileNotFoundException;
import java.util.List;

/** Read-only access to prepared backgrounds for the injected Aicy process. */
public final class AssistantBackgroundProvider extends ContentProvider {
    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".assistant.background";

    static Uri uri(String id, boolean blur) {
        if (!AssistantBackgroundImages.validId(id)) throw new IllegalArgumentException("Invalid background ID");
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(id).appendPath(blur ? "blur" : "clear").build();
    }

    private void enforceReader() {
        if (Binder.getCallingUid() != Process.myUid() && !AssistantProtocol.PACKAGE.equals(getCallingPackage()))
            throw new SecurityException("Only the module and Aicy may read backgrounds");
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforceReader();
        List<String> path = uri.getPathSegments();
        if (!"r".equals(mode) || !AUTHORITY.equals(uri.getAuthority()) || path.size() != 2
                || !AssistantBackgroundImages.validId(path.get(0))
                || !("clear".equals(path.get(1)) || "blur".equals(path.get(1))))
            throw new FileNotFoundException("Unknown background");
        return ParcelFileDescriptor.open(AssistantBackgroundImages.file(getContext(), path.get(0),
                "blur".equals(path.get(1))), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { enforceReader(); return "image/jpeg"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
