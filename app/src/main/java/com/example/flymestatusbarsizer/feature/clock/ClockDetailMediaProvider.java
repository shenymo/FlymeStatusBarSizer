package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

final class ClockDetailMediaProvider {
    private static final String LOG_TAG = "FlymeStatusBarSizer";
    private static final int INVALID_PLAYBACK_STATE = Integer.MIN_VALUE;
    private static final long LAST_ACTIVE_SNAPSHOT_TTL_MS = 15000L;
    private static final long EMPTY_SNAPSHOT_GRACE_MS = 320L;
    private static final Object LAST_ACTIVE_SNAPSHOT_LOCK = new Object();

    private static ClockDetailMediaSnapshot lastActiveSnapshot = ClockDetailMediaSnapshot.EMPTY;
    private static long lastActiveSnapshotUptimeMs;

    private final Context context;
    private final Handler mainHandler;
    private final MediaSessionManager mediaSessionManager;
    private final PackageManager packageManager;
    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            publishControllerSnapshot();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publishControllerSnapshot();
        }

        @Override
        public void onSessionDestroyed() {
            refreshActiveController();
        }
    };

    private Handler resultHandler;
    private MediaSnapshotCallback callback;
    private MediaSessionManager.OnActiveSessionsChangedListener activeSessionsChangedListener;
    private MediaController activeController;
    private Object notificationMediaManager;
    private Class<?> notificationMediaManagerClass;
    private Field notificationMediaControllerField;
    private Field notificationMediaMetadataField;
    private Method notificationMediaGetMediaIconMethod;
    private Method notificationMediaAddCallbackMethod;
    private Method notificationMediaRemoveCallbackMethod;
    private Object notificationMediaListenerProxy;
    private long emptySnapshotGraceDeadlineUptimeMs;
    private int pendingEmptySnapshotVersion;
    private ClockDetailMediaSnapshot lastDeliveredSnapshot = ClockDetailMediaSnapshot.EMPTY;
    private final Runnable pendingEmptySnapshotRunnable = this::flushPendingEmptySnapshot;

    interface MediaSnapshotCallback {
        void onMediaSnapshot(ClockDetailMediaSnapshot snapshot);
    }

    ClockDetailMediaProvider(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
        Handler handler = FlymeStatusBarSizer.getMainHandler();
        this.mainHandler = handler != null
                ? handler
                : new Handler(this.context != null
                        ? this.context.getMainLooper()
                        : Looper.getMainLooper());
        this.mediaSessionManager = this.context != null
                ? this.context.getSystemService(MediaSessionManager.class)
                : null;
        this.packageManager = this.context != null ? this.context.getPackageManager() : null;
    }

    ClockDetailMediaSnapshot peekStartupSnapshot() {
        SnapshotQueryResult notificationResult = queryNotificationMediaManagerSnapshot();
        if (notificationResult.snapshot.active) {
            cacheLastActiveSnapshot(notificationResult.snapshot);
            logMediaDebug("peekStartupSnapshot notification="
                    + describeSnapshot(notificationResult.snapshot));
            return notificationResult.snapshot;
        }

        SnapshotQueryResult mediaSessionResult = queryCurrentMediaSessionSnapshot();
        if (mediaSessionResult.snapshot.active) {
            cacheLastActiveSnapshot(mediaSessionResult.snapshot);
            logMediaDebug("peekStartupSnapshot mediaSession="
                    + describeSnapshot(mediaSessionResult.snapshot));
            return mediaSessionResult.snapshot;
        }

        if (notificationResult.sourceAvailable || mediaSessionResult.sourceAvailable) {
            clearLastActiveSnapshot();
            logMediaDebug("peekStartupSnapshot authoritativeEmpty notificationAvailable="
                    + notificationResult.sourceAvailable
                    + " mediaSessionAvailable=" + mediaSessionResult.sourceAvailable);
            return ClockDetailMediaSnapshot.EMPTY;
        }

        ClockDetailMediaSnapshot cachedSnapshot = peekLastActiveSnapshot();
        logMediaDebug("peekStartupSnapshot fallbackCache=" + describeSnapshot(cachedSnapshot));
        return cachedSnapshot;
    }

    void startListening(Handler resultHandler, MediaSnapshotCallback callback) {
        stopListening();
        this.resultHandler = resultHandler;
        this.callback = callback;
        this.emptySnapshotGraceDeadlineUptimeMs = 0L;
        this.pendingEmptySnapshotVersion = 0;
        this.lastDeliveredSnapshot = ClockDetailMediaSnapshot.EMPTY;
        if (callback == null || context == null) {
            return;
        }

        ClockDetailMediaSnapshot startupSnapshot = peekStartupSnapshot();
        if (startupSnapshot.active) {
            publishSnapshot("startup", startupSnapshot);
        }

        boolean notificationListening = startNotificationMediaManagerListening();
        boolean mediaSessionListening = startMediaSessionListening();
        logMediaDebug("startListening startup=" + describeSnapshot(startupSnapshot)
                + " notificationListening=" + notificationListening
                + " mediaSessionListening=" + mediaSessionListening);
        if (!notificationListening && !mediaSessionListening && !startupSnapshot.active) {
            deliverSnapshot(ClockDetailMediaSnapshot.EMPTY);
        }
    }

    void stopListening() {
        logMediaDebug("stopListening");
        cancelPendingEmptySnapshot();
        this.emptySnapshotGraceDeadlineUptimeMs = 0L;
        this.lastDeliveredSnapshot = ClockDetailMediaSnapshot.EMPTY;
        stopNotificationMediaManagerListening();
        stopMediaSessionListening();
        this.callback = null;
        this.resultHandler = null;
    }

    private SnapshotQueryResult queryNotificationMediaManagerSnapshot() {
        if (!ensureNotificationMediaManagerBridge()) {
            logMediaDebug("readNotificationMediaManagerSnapshot bridge unavailable");
            return SnapshotQueryResult.unavailable();
        }
        ClockDetailMediaSnapshot snapshot =
                buildSnapshotFromNotificationMediaManager(null, INVALID_PLAYBACK_STATE);
        logMediaDebug("readNotificationMediaManagerSnapshot " + describeSnapshot(snapshot));
        return SnapshotQueryResult.available(snapshot);
    }

    private SnapshotQueryResult queryCurrentMediaSessionSnapshot() {
        if (mediaSessionManager == null) {
            logMediaDebug("queryCurrentMediaSessionSnapshot manager unavailable");
            return SnapshotQueryResult.unavailable();
        }
        try {
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(null);
            if (controllers == null || controllers.isEmpty()) {
                logMediaDebug("queryCurrentMediaSessionSnapshot activeSessions empty");
                return SnapshotQueryResult.available(ClockDetailMediaSnapshot.EMPTY);
            }
            logMediaDebug("queryCurrentMediaSessionSnapshot activeSessions count=" + controllers.size());
            MediaController bestController = null;
            int bestScore = Integer.MIN_VALUE;
            for (MediaController controller : controllers) {
                int state = resolvePlaybackState(controller);
                int score = scorePlaybackState(state);
                logMediaDebug("queryCurrentMediaSessionSnapshot candidate="
                        + describeController(controller)
                        + " score=" + score);
                if (score > bestScore) {
                    bestScore = score;
                    bestController = controller;
                }
                if (score >= 4) {
                    break;
                }
            }
            ClockDetailMediaSnapshot snapshot = bestScore > 0
                    ? buildSnapshotFromController(bestController, null, INVALID_PLAYBACK_STATE, null)
                    : ClockDetailMediaSnapshot.EMPTY;
            logMediaDebug("queryCurrentMediaSessionSnapshot result="
                    + describeSnapshot(snapshot)
                    + " bestScore=" + bestScore);
            return SnapshotQueryResult.available(snapshot);
        } catch (Throwable t) {
            logMediaWarning("queryCurrentMediaSessionSnapshot failed", t);
            return SnapshotQueryResult.unavailable();
        }
    }

    private boolean startNotificationMediaManagerListening() {
        if (!ensureNotificationMediaManagerBridge()
                || notificationMediaManager == null
                || notificationMediaAddCallbackMethod == null) {
            return false;
        }
        try {
            Object listenerProxy = notificationMediaListenerProxy;
            if (listenerProxy == null) {
                Class<?> listenerClass = notificationMediaAddCallbackMethod.getParameterTypes()[0];
                listenerProxy = Proxy.newProxyInstance(
                        listenerClass.getClassLoader(),
                        new Class[]{listenerClass},
                        (proxy, method, args) -> handleNotificationMediaManagerCallback(proxy, method, args));
                notificationMediaListenerProxy = listenerProxy;
            }
            notificationMediaAddCallbackMethod.invoke(notificationMediaManager, listenerProxy);
            logMediaDebug("startNotificationMediaManagerListening success");
            return true;
        } catch (Throwable t) {
            notificationMediaListenerProxy = null;
            logMediaWarning("startNotificationMediaManagerListening failed", t);
            return false;
        }
    }

    private void stopNotificationMediaManagerListening() {
        if (notificationMediaManager == null
                || notificationMediaRemoveCallbackMethod == null
                || notificationMediaListenerProxy == null) {
            return;
        }
        try {
            notificationMediaRemoveCallbackMethod.invoke(
                    notificationMediaManager,
                    notificationMediaListenerProxy);
            logMediaDebug("stopNotificationMediaManagerListening success");
        } catch (Throwable t) {
            logMediaWarning("stopNotificationMediaManagerListening failed", t);
        } finally {
            notificationMediaListenerProxy = null;
        }
    }

    private Object handleNotificationMediaManagerCallback(
            Object proxy,
            Method method,
            Object[] args) {
        if (method == null) {
            return null;
        }
        String methodName = method.getName();
        if ("onPrimaryMetadataOrStateChanged".equals(methodName)) {
            MediaMetadata metadata = args != null
                    && args.length > 0
                    && args[0] instanceof MediaMetadata
                    ? (MediaMetadata) args[0]
                    : null;
            int playbackState = args != null
                    && args.length > 1
                    && args[1] instanceof Integer
                    ? (Integer) args[1]
                    : INVALID_PLAYBACK_STATE;
            logMediaDebug("notification callback state="
                    + describePlaybackStateForLog(playbackState)
                    + " metadataTitle=" + describeMetadataTitle(metadata));
            publishNotificationMediaManagerSnapshot(metadata, playbackState);
            return null;
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
            return proxy == (args != null && args.length > 0 ? args[0] : null);
        }
        if ("toString".equals(methodName)) {
            return "ClockDetailMediaProvider.NotificationMediaListener";
        }
        return null;
    }

    private void publishNotificationMediaManagerSnapshot(
            MediaMetadata metadata,
            int playbackState) {
        publishSnapshot(
                "notification-callback state=" + describePlaybackStateForLog(playbackState),
                buildSnapshotFromNotificationMediaManager(metadata, playbackState));
    }

    private ClockDetailMediaSnapshot buildSnapshotFromNotificationMediaManager(
            MediaMetadata metadata,
            int playbackState) {
        if (notificationMediaManager == null) {
            return ClockDetailMediaSnapshot.EMPTY;
        }
        MediaController controller = extractNotificationMediaController(
                notificationMediaManager,
                notificationMediaControllerField);
        MediaMetadata resolvedMetadata = metadata != null
                ? metadata
                : extractNotificationMediaMetadata(
                        notificationMediaManager,
                        notificationMediaMetadataField);
        Drawable fallbackDrawable = resolveNotificationMediaDrawable(
                notificationMediaManager,
                notificationMediaGetMediaIconMethod);
        return buildSnapshotFromController(
                controller,
                resolvedMetadata,
                playbackState,
                fallbackDrawable);
    }

    private boolean ensureNotificationMediaManagerBridge() {
        if (notificationMediaManager != null
                && notificationMediaManagerClass != null
                && notificationMediaControllerField != null
                && notificationMediaMetadataField != null
                && notificationMediaGetMediaIconMethod != null
                && notificationMediaAddCallbackMethod != null
                && notificationMediaRemoveCallbackMethod != null) {
            return true;
        }
        ClassLoader loader = context != null ? context.getClassLoader() : null;
        if (loader == null) {
            return false;
        }
        try {
            Class<?> dependencyClass = Class.forName(
                    "com.android.systemui.Dependency",
                    false,
                    loader);
            Class<?> managerClass = Class.forName(
                    "com.android.systemui.media.NotificationMediaManager",
                    false,
                    loader);
            Class<?> listenerClass = Class.forName(
                    "com.android.systemui.media.NotificationMediaManager$MediaListener",
                    false,
                    loader);
            Method dependencyGetMethod = dependencyClass.getDeclaredMethod("get", Class.class);
            Object manager = dependencyGetMethod.invoke(null, managerClass);
            if (manager == null) {
                return false;
            }
            Field mediaControllerField = managerClass.getDeclaredField("mMediaController");
            Field mediaMetadataField = managerClass.getDeclaredField("mMediaMetadata");
            Method getMediaIconMethod = managerClass.getDeclaredMethod("getMediaIcon");
            Method addCallbackMethod = managerClass.getDeclaredMethod("addCallback", listenerClass);
            Method removeCallbackMethod = managerClass.getDeclaredMethod("removeCallback", listenerClass);
            mediaControllerField.setAccessible(true);
            mediaMetadataField.setAccessible(true);
            getMediaIconMethod.setAccessible(true);
            addCallbackMethod.setAccessible(true);
            removeCallbackMethod.setAccessible(true);
            notificationMediaManager = manager;
            notificationMediaManagerClass = managerClass;
            notificationMediaControllerField = mediaControllerField;
            notificationMediaMetadataField = mediaMetadataField;
            notificationMediaGetMediaIconMethod = getMediaIconMethod;
            notificationMediaAddCallbackMethod = addCallbackMethod;
            notificationMediaRemoveCallbackMethod = removeCallbackMethod;
            return true;
        } catch (Throwable t) {
            logMediaWarning("ensureNotificationMediaManagerBridge failed", t);
            return false;
        }
    }

    private boolean startMediaSessionListening() {
        if (mediaSessionManager == null) {
            return false;
        }
        activeSessionsChangedListener = controllers -> mainHandler.post(this::refreshActiveController);
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                    activeSessionsChangedListener,
                    null,
                    mainHandler);
        } catch (Throwable primaryError) {
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(
                        activeSessionsChangedListener,
                        null);
            } catch (Throwable fallbackError) {
                activeSessionsChangedListener = null;
                logMediaWarning(
                        "startMediaSessionListening failed for both listener signatures",
                        fallbackError);
                return false;
            }
        }
        logMediaDebug("startMediaSessionListening success");
        refreshActiveController();
        return true;
    }

    private void stopMediaSessionListening() {
        if (mediaSessionManager != null && activeSessionsChangedListener != null) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener);
                logMediaDebug("stopMediaSessionListening success");
            } catch (Throwable t) {
                logMediaWarning("stopMediaSessionListening failed", t);
            }
        }
        activeSessionsChangedListener = null;
        swapActiveController(null);
    }

    private void refreshActiveController() {
        MediaController nextController = choosePrimaryActiveController();
        logMediaDebug("refreshActiveController next=" + describeController(nextController));
        swapActiveController(nextController);
        publishControllerSnapshot();
    }

    private void publishControllerSnapshot() {
        publishSnapshot("media-session controller=" + describeController(activeController),
                buildSnapshotFromController(
                activeController,
                null,
                INVALID_PLAYBACK_STATE,
                null));
    }

    private void publishSnapshot(String source, ClockDetailMediaSnapshot snapshot) {
        ClockDetailMediaSnapshot safeSnapshot = snapshot != null && snapshot.active
                ? snapshot
                : ClockDetailMediaSnapshot.EMPTY;
        logMediaDebug("publishSnapshot source=" + source
                + " snapshot=" + describeSnapshot(safeSnapshot));
        if (safeSnapshot.active) {
            cancelPendingEmptySnapshot();
            emptySnapshotGraceDeadlineUptimeMs =
                    SystemClock.uptimeMillis() + EMPTY_SNAPSHOT_GRACE_MS;
            deliverSnapshot(safeSnapshot);
            return;
        }
        if (shouldDeferEmptySnapshot()) {
            logMediaDebug("publishSnapshot defer-empty source=" + source
                    + " deadline=" + emptySnapshotGraceDeadlineUptimeMs);
            schedulePendingEmptySnapshot();
            return;
        }
        deliverSnapshot(ClockDetailMediaSnapshot.EMPTY);
    }

    private boolean shouldDeferEmptySnapshot() {
        if (!lastDeliveredSnapshot.active) {
            return false;
        }
        return SystemClock.uptimeMillis() < emptySnapshotGraceDeadlineUptimeMs;
    }

    private void schedulePendingEmptySnapshot() {
        long delayMs = Math.max(
                0L,
                emptySnapshotGraceDeadlineUptimeMs - SystemClock.uptimeMillis());
        pendingEmptySnapshotVersion++;
        mainHandler.removeCallbacks(pendingEmptySnapshotRunnable);
        mainHandler.postDelayed(pendingEmptySnapshotRunnable, delayMs);
        logMediaDebug("schedulePendingEmptySnapshot delayMs=" + delayMs
                + " version=" + pendingEmptySnapshotVersion);
    }

    private void cancelPendingEmptySnapshot() {
        pendingEmptySnapshotVersion++;
        mainHandler.removeCallbacks(pendingEmptySnapshotRunnable);
        logMediaDebug("cancelPendingEmptySnapshot version=" + pendingEmptySnapshotVersion);
    }

    private void flushPendingEmptySnapshot() {
        if (shouldDeferEmptySnapshot()) {
            logMediaDebug("flushPendingEmptySnapshot deferred again");
            schedulePendingEmptySnapshot();
            return;
        }
        logMediaDebug("flushPendingEmptySnapshot deliver-empty");
        deliverSnapshot(ClockDetailMediaSnapshot.EMPTY);
    }

    private MediaController choosePrimaryActiveController() {
        if (mediaSessionManager == null) {
            return null;
        }
        try {
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(null);
            if (controllers == null || controllers.isEmpty()) {
                logMediaDebug("choosePrimaryActiveController activeSessions empty");
                return null;
            }
            logMediaDebug("choosePrimaryActiveController activeSessions count=" + controllers.size());
            MediaController bestController = null;
            int bestScore = Integer.MIN_VALUE;
            for (MediaController controller : controllers) {
                int state = resolvePlaybackState(controller);
                int score = scorePlaybackState(state);
                logMediaDebug("choosePrimaryActiveController candidate="
                        + describeController(controller)
                        + " score=" + score);
                if (score > bestScore) {
                    bestScore = score;
                    bestController = controller;
                }
                if (score >= 4) {
                    break;
                }
            }
            logMediaDebug("choosePrimaryActiveController picked="
                    + describeController(bestController)
                    + " bestScore=" + bestScore);
            return bestScore > 0 ? bestController : null;
        } catch (Throwable t) {
            logMediaWarning("choosePrimaryActiveController failed", t);
            return null;
        }
    }

    private void swapActiveController(MediaController nextController) {
        if (sameSessions(activeController, nextController)) {
            logMediaDebug("swapActiveController unchanged controller="
                    + describeController(activeController));
            return;
        }
        if (activeController != null) {
            try {
                activeController.unregisterCallback(controllerCallback);
            } catch (Throwable t) {
                logMediaWarning("unregister activeController callback failed", t);
            }
        }
        activeController = nextController;
        logMediaDebug("swapActiveController active=" + describeController(activeController));
        if (activeController != null) {
            try {
                activeController.registerCallback(controllerCallback, mainHandler);
            } catch (Throwable t) {
                logMediaWarning("register activeController callback failed", t);
            }
        }
    }

    private MediaController extractNotificationMediaController(Object manager, Field mediaControllerField) {
        if (manager == null || mediaControllerField == null) {
            return null;
        }
        try {
            Object value = mediaControllerField.get(manager);
            return value instanceof MediaController ? (MediaController) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private MediaMetadata extractNotificationMediaMetadata(Object manager, Field mediaMetadataField) {
        if (manager == null || mediaMetadataField == null) {
            return null;
        }
        try {
            Object value = mediaMetadataField.get(manager);
            return value instanceof MediaMetadata ? (MediaMetadata) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Drawable resolveNotificationMediaDrawable(Object manager, Method getMediaIconMethod) {
        if (manager == null || getMediaIconMethod == null || context == null) {
            return null;
        }
        try {
            Object iconValue = getMediaIconMethod.invoke(manager);
            return iconValue instanceof Icon ? ((Icon) iconValue).loadDrawable(context) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ClockDetailMediaSnapshot buildSnapshotFromController(
            MediaController controller,
            MediaMetadata metadata,
            int playbackState,
            Drawable fallbackDrawable) {
        if (controller == null) {
            return ClockDetailMediaSnapshot.EMPTY;
        }
        int resolvedState = playbackState != INVALID_PLAYBACK_STATE
                ? playbackState
                : resolvePlaybackState(controller);
        if (!shouldDisplayPlaybackState(resolvedState)) {
            return ClockDetailMediaSnapshot.EMPTY;
        }
        MediaMetadata resolvedMetadata = metadata != null ? metadata : controller.getMetadata();
        String packageName = safePackageName(controller.getPackageName());
        CharSequence appLabel = resolveAppLabel(packageName);
        CharSequence title = resolveTitle(resolvedMetadata, appLabel);
        CharSequence subtitle = resolveSubtitle(resolvedMetadata, appLabel);
        CharSequence playbackLabel = describePlaybackState(resolvedState);
        Drawable artwork = resolveArtwork(resolvedMetadata, fallbackDrawable, packageName);
        PendingIntent launchIntent = resolveLaunchIntent(controller);
        return new ClockDetailMediaSnapshot(
                true,
                artwork,
                title,
                subtitle,
                playbackLabel,
                launchIntent,
                packageName);
    }

    private PendingIntent resolveLaunchIntent(MediaController controller) {
        if (controller == null) {
            return null;
        }
        try {
            return controller.getSessionActivity();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Drawable resolveArtwork(
            MediaMetadata metadata,
            Drawable fallbackDrawable,
            String packageName) {
        Drawable artwork = resolveArtworkFromMetadata(metadata);
        if (artwork != null) {
            return artwork;
        }
        if (fallbackDrawable != null) {
            return fallbackDrawable;
        }
        return resolveApplicationIcon(packageName);
    }

    private Drawable resolveArtworkFromMetadata(MediaMetadata metadata) {
        if (metadata == null || context == null) {
            return null;
        }
        try {
            MediaDescription description = metadata.getDescription();
            if (description != null) {
                Bitmap iconBitmap = description.getIconBitmap();
                if (iconBitmap != null) {
                    return new BitmapDrawable(context.getResources(), iconBitmap);
                }
            }
            Bitmap artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (artwork == null) {
                artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (artwork == null) {
                artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            return artwork != null ? new BitmapDrawable(context.getResources(), artwork) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Drawable resolveApplicationIcon(String packageName) {
        if (packageManager == null || packageName == null || packageName.isEmpty()) {
            return null;
        }
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private CharSequence resolveTitle(MediaMetadata metadata, CharSequence appLabel) {
        CharSequence title = resolveDescriptionTitle(metadata);
        if (!isEmpty(title)) {
            return title;
        }
        if (metadata != null) {
            title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (!isEmpty(title)) {
                return title;
            }
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (!isEmpty(title)) {
                return title;
            }
        }
        return appLabel;
    }

    private CharSequence resolveSubtitle(MediaMetadata metadata, CharSequence appLabel) {
        if (metadata != null) {
            CharSequence artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (!isEmpty(artist)) {
                return artist;
            }
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (!isEmpty(artist)) {
                return artist;
            }
        }
        CharSequence subtitle = resolveDescriptionSubtitle(metadata);
        return !isEmpty(subtitle) ? subtitle : appLabel;
    }

    private CharSequence resolveDescriptionTitle(MediaMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        try {
            MediaDescription description = metadata.getDescription();
            return description != null ? description.getTitle() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private CharSequence resolveDescriptionSubtitle(MediaMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        try {
            MediaDescription description = metadata.getDescription();
            return description != null ? description.getSubtitle() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private CharSequence resolveAppLabel(String packageName) {
        if (packageManager == null || packageName == null || packageName.isEmpty()) {
            return "";
        }
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = applicationInfo.loadLabel(packageManager);
            return !isEmpty(label) ? label : packageName;
        } catch (Throwable ignored) {
            return packageName;
        }
    }

    private void deliverSnapshot(ClockDetailMediaSnapshot snapshot) {
        MediaSnapshotCallback localCallback = callback;
        Handler localHandler = resultHandler;
        if (localCallback == null) {
            return;
        }
        ClockDetailMediaSnapshot safeSnapshot = snapshot != null && snapshot.active
                ? snapshot
                : ClockDetailMediaSnapshot.EMPTY;
        lastDeliveredSnapshot = safeSnapshot;
        if (safeSnapshot.active) {
            cacheLastActiveSnapshot(safeSnapshot);
        } else {
            clearLastActiveSnapshot();
        }
        logMediaDebug("deliverSnapshot " + describeSnapshot(safeSnapshot));
        if (localHandler == null) {
            localCallback.onMediaSnapshot(safeSnapshot);
            return;
        }
        localHandler.post(() -> {
            if (callback == localCallback) {
                localCallback.onMediaSnapshot(safeSnapshot);
            }
        });
    }

    private static void cacheLastActiveSnapshot(ClockDetailMediaSnapshot snapshot) {
        synchronized (LAST_ACTIVE_SNAPSHOT_LOCK) {
            lastActiveSnapshot = snapshot != null && snapshot.active
                    ? snapshot
                    : ClockDetailMediaSnapshot.EMPTY;
            lastActiveSnapshotUptimeMs = SystemClock.uptimeMillis();
        }
    }

    private static void clearLastActiveSnapshot() {
        synchronized (LAST_ACTIVE_SNAPSHOT_LOCK) {
            lastActiveSnapshot = ClockDetailMediaSnapshot.EMPTY;
            lastActiveSnapshotUptimeMs = 0L;
        }
    }

    private static ClockDetailMediaSnapshot peekLastActiveSnapshot() {
        synchronized (LAST_ACTIVE_SNAPSHOT_LOCK) {
            if (!lastActiveSnapshot.active) {
                return ClockDetailMediaSnapshot.EMPTY;
            }
            long ageMs = SystemClock.uptimeMillis() - lastActiveSnapshotUptimeMs;
            if (ageMs > LAST_ACTIVE_SNAPSHOT_TTL_MS) {
                return ClockDetailMediaSnapshot.EMPTY;
            }
            return lastActiveSnapshot;
        }
    }

    private static int resolvePlaybackState(MediaController controller) {
        if (controller == null) {
            return PlaybackState.STATE_NONE;
        }
        try {
            PlaybackState playbackState = controller.getPlaybackState();
            return playbackState != null ? playbackState.getState() : PlaybackState.STATE_NONE;
        } catch (Throwable ignored) {
            return PlaybackState.STATE_NONE;
        }
    }

    private static int scorePlaybackState(int playbackState) {
        switch (playbackState) {
            case PlaybackState.STATE_PLAYING:
                return 4;
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
                return 3;
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return 2;
            default:
                return shouldDisplayPlaybackState(playbackState) ? 1 : Integer.MIN_VALUE;
        }
    }

    private static boolean shouldDisplayPlaybackState(int playbackState) {
        switch (playbackState) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
    }

    private static CharSequence describePlaybackState(int playbackState) {
        switch (playbackState) {
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
                return "连接中";
            case PlaybackState.STATE_FAST_FORWARDING:
                return "快进中";
            case PlaybackState.STATE_REWINDING:
                return "快退中";
            default:
                return "播放中";
        }
    }

    private static boolean sameSessions(MediaController first, MediaController second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        MediaSession.Token firstToken = first.getSessionToken();
        MediaSession.Token secondToken = second.getSessionToken();
        return firstToken != null && firstToken.equals(secondToken);
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private static String safePackageName(String packageName) {
        if (packageName == null) {
            return "";
        }
        String trimmed = packageName.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static void logMediaDebug(String message) {
        Log.d(LOG_TAG, "[clock-media] " + message);
    }

    private static void logMediaWarning(String message, Throwable throwable) {
        Log.w(LOG_TAG, "[clock-media] " + message, throwable);
        FlymeStatusBarSizer.logClockWarning("[clock-media] " + message, throwable);
    }

    private static String describeSnapshot(ClockDetailMediaSnapshot snapshot) {
        if (snapshot == null) {
            return "null";
        }
        return "active=" + snapshot.active
                + ", title=" + sanitizeForLog(snapshot.title)
                + ", subtitle=" + sanitizeForLog(snapshot.subtitle)
                + ", stateLabel=" + sanitizeForLog(snapshot.playbackStateLabel)
                + ", pkg=" + sanitizeForLog(snapshot.packageName)
                + ", hasArtwork=" + (snapshot.artwork != null)
                + ", hasIntent=" + (snapshot.launchIntent != null);
    }

    private static String describeController(MediaController controller) {
        if (controller == null) {
            return "null";
        }
        return "pkg=" + safePackageName(controller.getPackageName())
                + ", state=" + describePlaybackStateForLog(resolvePlaybackState(controller));
    }

    private static String describePlaybackStateForLog(int playbackState) {
        if (playbackState == INVALID_PLAYBACK_STATE) {
            return "INVALID";
        }
        switch (playbackState) {
            case PlaybackState.STATE_NONE:
                return "NONE";
            case PlaybackState.STATE_STOPPED:
                return "STOPPED";
            case PlaybackState.STATE_PAUSED:
                return "PAUSED";
            case PlaybackState.STATE_PLAYING:
                return "PLAYING";
            case PlaybackState.STATE_FAST_FORWARDING:
                return "FAST_FORWARDING";
            case PlaybackState.STATE_REWINDING:
                return "REWINDING";
            case PlaybackState.STATE_BUFFERING:
                return "BUFFERING";
            case PlaybackState.STATE_ERROR:
                return "ERROR";
            case PlaybackState.STATE_CONNECTING:
                return "CONNECTING";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
                return "SKIPPING_TO_PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
                return "SKIPPING_TO_NEXT";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return "SKIPPING_TO_QUEUE_ITEM";
            default:
                return String.valueOf(playbackState);
        }
    }

    private static String describeMetadataTitle(MediaMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        CharSequence title = metadata.getDescription() != null
                ? metadata.getDescription().getTitle()
                : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        return sanitizeForLog(title);
    }

    private static String sanitizeForLog(CharSequence text) {
        if (text == null) {
            return "";
        }
        String value = text.toString().replace('\n', ' ').trim();
        return value.isEmpty() ? "" : value;
    }

    private static final class SnapshotQueryResult {
        final ClockDetailMediaSnapshot snapshot;
        final boolean sourceAvailable;

        SnapshotQueryResult(ClockDetailMediaSnapshot snapshot, boolean sourceAvailable) {
            this.snapshot = snapshot != null ? snapshot : ClockDetailMediaSnapshot.EMPTY;
            this.sourceAvailable = sourceAvailable;
        }

        static SnapshotQueryResult available(ClockDetailMediaSnapshot snapshot) {
            return new SnapshotQueryResult(snapshot, true);
        }

        static SnapshotQueryResult unavailable() {
            return new SnapshotQueryResult(ClockDetailMediaSnapshot.EMPTY, false);
        }
    }
}
