package com.example.flymestatusbarsizer.feature.onehanded;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class OneStepTouchMode {
    private static final String TAG = "FlymeOneStep";
    private static final String NODE = "/sys/class/meizu/main_tp/game_mode_node";
    private final Executor worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FlymeOneStepTouch");
        thread.setDaemon(true);
        return thread;
    });

    void enable() {
        worker.execute(() -> {
            try {
                writeEnabled();
                Log.i(TAG, "Touch game mode write completed: requested=1");
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Touch game mode write failed: " + NODE, e);
            }
        });
    }

    private static void writeEnabled() throws IOException {
        try (FileOutputStream stream = new FileOutputStream(NODE)) {
            stream.write("1\n".getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            rootCommand("echo 1 > '" + NODE + "'");
        }
    }

    private static void rootCommand(String command) throws IOException {
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        try {
            process.getOutputStream().close();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IOException("Touch game mode root command timed out");
            }
            String output = readOutput(process.getInputStream());
            if (process.exitValue() != 0) {
                throw new IOException("Touch game mode root command exited "
                        + process.exitValue() + ": " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Touch game mode root command interrupted", e);
        } finally {
            process.destroyForcibly();
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    private static String readOutput(InputStream stream) throws IOException {
        byte[] bytes = new byte[1024];
        int count = 0;
        while (count < bytes.length) {
            int read = stream.read(bytes, count, bytes.length - count);
            if (read < 0) break;
            count += read;
        }
        return new String(bytes, 0, count, StandardCharsets.UTF_8).trim();
    }
}
