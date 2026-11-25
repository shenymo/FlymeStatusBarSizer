package com.example.flymestatusbarsizer.feature.openwith;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.util.Locale;

public enum OpenWithContentType {
    DEFAULT("默认 / 其他", "*/*"), WEB("网页", "text/html"), IMAGE("图片", "image/jpeg"),
    VIDEO("视频", "video/mp4"), TEXT("文本", "text/plain"),
    PDF("PDF", "application/pdf"), AUDIO("音频", "audio/mpeg"),
    APK("安装包", "application/vnd.android.package-archive"),
    ARCHIVE("压缩包", "application/zip"), OTHER("其他文件", "application/octet-stream");

    public final String label;
    public final String mime;

    OpenWithContentType(String label, String mime) {
        this.label = label;
        this.mime = mime;
    }

    public static OpenWithContentType resolve(Context context, Intent intent) {
        if (intent == null) return DEFAULT;
        Uri data = intent.getData();
        if (data != null && ("http".equals(data.getScheme()) || "https".equals(data.getScheme()))) {
            return WEB;
        }
        String mime = intent.getType();
        if (isGenericMime(mime) && data != null && "content".equals(data.getScheme())) {
            try {
                mime = context.getContentResolver().getType(data);
            } catch (RuntimeException ignored) {
                mime = null;
            }
        }
        OpenWithContentType type = fromMime(mime);
        if (type != DEFAULT) return type;
        if (data != null) {
            String path = data.getLastPathSegment();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && dot < path.length() - 1) {
                    return fromMime(MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                            path.substring(dot + 1).toLowerCase(Locale.ROOT)));
                }
            }
        }
        return DEFAULT;
    }

    public static OpenWithContentType fromMime(String mime) {
        if (isGenericMime(mime)) return DEFAULT;
        String value = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (value.equals("text/html") || value.equals("application/xhtml+xml")) return WEB;
        if (value.startsWith("image/")) return IMAGE;
        if (value.startsWith("video/")) return VIDEO;
        if (value.startsWith("text/")) return TEXT;
        if (value.equals("application/pdf")) return PDF;
        if (value.startsWith("audio/")) return AUDIO;
        if (value.equals("application/vnd.android.package-archive")) return APK;
        if (value.equals("application/zip") || value.equals("application/x-7z-compressed")
                || value.equals("application/x-rar-compressed")) return ARCHIVE;
        return OTHER;
    }

    private static boolean isGenericMime(String mime) {
        if (mime == null) return true;
        String value = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() || value.equals("*/*") || value.equals("application/*")
                || value.equals("application/octet-stream");
    }
}
