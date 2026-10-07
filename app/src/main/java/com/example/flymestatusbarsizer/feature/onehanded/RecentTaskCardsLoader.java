package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Snapshot IPC and bitmap conversion never run on the Shell/UI thread. No persistent cache. */
final class RecentTaskCardsLoader {
    interface Source {
        List<RecentTaskCard> recentTasks() throws Exception;
        // The caller owns the returned bitmap; it is not shared with Launcher or another view.
        Bitmap thumbnail(int taskId) throws Exception;
    }

    private final Source source;
    private final Executor worker;
    private final Handler resultHandler;
    private final AtomicLong generation = new AtomicLong();

    RecentTaskCardsLoader(Source source, Executor worker, Handler resultHandler) {
        this.source = source;
        this.worker = worker;
        this.resultHandler = resultHandler;
    }

    void cancel() { generation.incrementAndGet(); }

    void request(TaskScaleTarget foreground, int width, int height,
            Consumer<List<RecentTaskCard>> callback) {
        request(foreground, width, height, callback, null);
    }

    void request(TaskScaleTarget foreground, int width, int height,
            Consumer<List<RecentTaskCard>> callback, Consumer<RecentTaskCard> mainCallback) {
        long request = generation.incrementAndGet();
        worker.execute(() -> {
            if (generation.get() != request) return;
            List<RecentTaskCard> cards = new ArrayList<>();
            RecentTaskCard main = null;
            try {
                HashSet<Integer> seen = new HashSet<>();
                List<RecentTaskCard> recent = source.recentTasks();
                for (RecentTaskCard candidate : recent) {
                    if (generation.get() != request) break;
                    if (candidate.taskId == foreground.taskId || !seen.add(candidate.taskId)) continue;
                    Bitmap preview = null;
                    try { preview = downsample(source.thumbnail(candidate.taskId), width, height); }
                    catch (Exception e) { Log.w(OneHandedTaskHooks.TAG, "Cannot load task preview", e); }
                    cards.add(candidate.withPreview(preview));
                    if (cards.size() == RecentTaskCardLayout.MAX_CARDS) break;
                }
                if (mainCallback != null && generation.get() == request) {
                    for (RecentTaskCard candidate : recent) {
                        if (!candidate.matches(foreground)) continue;
                        main = candidate.withPreview(downsample(source.thumbnail(candidate.taskId), width, height));
                        break;
                    }
                }
            } catch (Exception e) { Log.w(OneHandedTaskHooks.TAG, "Cannot load recent tasks", e); }
            final RecentTaskCard mainPreview = main;
            if (generation.get() != request) { discard(cards, mainPreview); return; }
            if (!resultHandler.post(() -> {
                if (generation.get() == request) {
                    callback.accept(cards);
                    if (mainCallback != null && mainPreview != null) mainCallback.accept(mainPreview);
                } else discard(cards, mainPreview);
            })) discard(cards, mainPreview);
        });
    }

    private static Bitmap downsample(Bitmap source, int width, int height) {
        if (source == null) return null;
        Bitmap software = null;
        try {
            // Margin windows may use a software canvas. Never retain a full-size hardware buffer.
            software = source.copy(Bitmap.Config.ARGB_8888, false);
            if (software == null) return null;
            float scale = Math.min(1f, Math.min(Math.max(1, width) / (float) software.getWidth(),
                    Math.max(1, height) / (float) software.getHeight()));
            Bitmap result = Bitmap.createScaledBitmap(software,
                    Math.max(1, Math.round(software.getWidth() * scale)),
                    Math.max(1, Math.round(software.getHeight() * scale)), true);
            if (result == software) software = null;
            return result;
        } finally {
            source.recycle();
            if (software != null) software.recycle();
        }
    }

    private static void discard(List<RecentTaskCard> cards, RecentTaskCard main) {
        for (RecentTaskCard card : cards) if (card.preview != null) card.preview.recycle();
        if (main != null && main.preview != null) main.preview.recycle();
    }
}
