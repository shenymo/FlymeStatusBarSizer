package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Image preparation runs off the UI thread; only bounded, normalized JPEGs are shared. */
final class AssistantBackgroundImages {
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    static final int MAX_EDGE = 2048;
    private static String cachedKey;
    private static Bitmap cachedBitmap;

    private AssistantBackgroundImages() { }

    static File directory(Context context) {
        return new File(context.getFilesDir(), "assistant-backgrounds");
    }

    static boolean validId(String id) {
        return id != null && id.matches("[a-f0-9]{32}");
    }

    static File file(Context context, String id, boolean blur) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid background ID");
        return new File(directory(context), id + (blur ? "-blur.jpg" : ".jpg"));
    }

    static String importImage(Context context, Uri uri) throws IOException {
        if (!"content".equals(uri.getScheme())) throw new IOException("请选择图库或文件中的图片");
        Bitmap decoded;
        if (Build.VERSION.SDK_INT >= 28) {
            decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri),
                    (decoder, info, source) -> {
                        int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                        float scale = Math.min(1f, MAX_EDGE / (float) Math.max(width, height));
                        decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                                Math.max(1, Math.round(height * scale)));
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
        } else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("无法读取图片");
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > MAX_EDGE)
                options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(input, null, options);
            }
        }
        if (decoded == null) throw new IOException("无法读取图片");
        String id = UUID.randomUUID().toString().replace("-", "");
        File dir = directory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) { decoded.recycle(); throw new IOException("无法保存图片"); }
        Bitmap opaque = null, blurred = null;
        boolean complete = false;
        try {
            opaque = Bitmap.createBitmap(decoded.getWidth(), decoded.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(opaque);
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(decoded, 0, 0, null);
            write(opaque, file(context, id, false));
            blurred = blur(opaque);
            write(blurred, file(context, id, true));
            complete = true;
            return id;
        } finally {
            decoded.recycle();
            if (opaque != null) opaque.recycle();
            if (blurred != null) blurred.recycle();
            if (!complete) { file(context, id, false).delete(); file(context, id, true).delete(); }
        }
    }

    private static void write(Bitmap bitmap, File file) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) throw new IOException("无法保存图片");
            output.getFD().sync();
        }
    }

    /** Three separable box passes approximate a Gaussian blur at a bounded working resolution. */
    static Bitmap blur(Bitmap source) {
        float scale = Math.min(1f, 512f / Math.max(source.getWidth(), source.getHeight()));
        Bitmap small = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * scale)),
                Math.max(1, Math.round(source.getHeight() * scale)), true);
        int w = small.getWidth(), h = small.getHeight();
        int[] pixels = new int[w * h], scratch = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        if (small != source) small.recycle();
        int radius = Math.max(1, Math.round(Math.min(w, h) * 0.025f));
        for (int pass = 0; pass < 3; pass++) {
            boxPass(pixels, scratch, w, h, radius, true);
            boxPass(scratch, pixels, w, h, radius, false);
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
    }

    private static void boxPass(int[] source, int[] target, int w, int h, int radius, boolean horizontal) {
        int length = horizontal ? w : h, lines = horizontal ? h : w, count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = source[index(line, clamp(i, length), w, horizontal)];
                r += Color.red(p); g += Color.green(p); b += Color.blue(p);
            }
            for (int i = 0; i < length; i++) {
                target[index(line, i, w, horizontal)] = Color.rgb(r / count, g / count, b / count);
                int remove = source[index(line, clamp(i - radius, length), w, horizontal)];
                int add = source[index(line, clamp(i + radius + 1, length), w, horizontal)];
                r += Color.red(add) - Color.red(remove);
                g += Color.green(add) - Color.green(remove);
                b += Color.blue(add) - Color.blue(remove);
            }
        }
    }

    private static int clamp(int value, int length) { return Math.max(0, Math.min(length - 1, value)); }
    private static int index(int line, int offset, int width, boolean horizontal) {
        return horizontal ? line * width + offset : offset * width + line;
    }

    // Only call on WORKER. Keep one decoded variant; active sessions retain their own reference.
    static Bitmap load(Context context, String id, boolean blur) throws IOException {
        String key = id + ":" + blur;
        if (key.equals(cachedKey) && cachedBitmap != null) return cachedBitmap;
        cachedKey = null;
        cachedBitmap = null;
        try (InputStream input = context.getContentResolver().openInputStream(
                AssistantBackgroundProvider.uri(id, blur))) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null || bitmap.getWidth() > MAX_EDGE || bitmap.getHeight() > MAX_EDGE)
                throw new IOException("Invalid background image");
            cachedKey = key;
            cachedBitmap = bitmap;
            return bitmap;
        }
    }

    static void deleteUnused(Context context, String keepId) {
        File[] files = directory(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.matches("[a-f0-9]{32}(-blur)?\\.jpg") && !name.startsWith(keepId + ".")
                    && !name.equals(keepId + "-blur.jpg")) file.delete();
        }
    }
}
