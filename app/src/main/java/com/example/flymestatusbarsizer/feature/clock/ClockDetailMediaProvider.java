package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
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
    private final int artworkSizePx;
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
    private ClockDetailMediaSnapshot lastDeliveredSnapshot = ClockDetailMediaSnapshot.EMPTY;
    private final Runnable pendingEmptySnapshotRunnable = this::flushPendingEmptySnapshot;

    interface MediaSnapshotCallback {
        void onMediaSnapshot(ClockDetailMediaSnapshot snapshot);
    }

    ClockDetailMediaProvider(Context context, int artworkSizePx) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
        this.artworkSizePx = Math.max(1, artworkSizePx);
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
            return notificationResult.snapshot;
        }

        SnapshotQueryResult mediaSessionResult = queryCurrentMediaSessionSnapshot();
        if (mediaSessionResult.snapshot.active) {
            cacheLastActiveSnapshot(mediaSessionResult.snapshot);
            return mediaSessionResult.snapshot;
        }

        if (notificationResult.sourceAvailable || mediaSessionResult.sourceAvailable) {
            clearLastActiveSnapshot();
            return ClockDetailMediaSnapshot.EMPTY;
        }

        return peekLastActiveSnapshot();
    }

    void startListening(
            Handler resultHandler,
            ClockDetailMediaSnapshot startupSnapshot,
            MediaSnapshotCallback callback) {
        stopListening();
        this.resultHandler = resultHandler;
        this.callback = callback;
        this.emptySnapshotGraceDeadlineUptimeMs = 0L;
        this.lastDeliveredSnapshot = ClockDetailMediaSnapshot.EMPTY;
        if (callback == null || context == null) {
            return;
        }

        ClockDetailMediaSnapshot initialSnapshot = startupSnapshot != null
                ? startupSnapshot
                : peekStartupSnapshot();
        if (initialSnapshot.active) {
            publishSnapshot(initialSnapshot);
        }

        boolean notificationListening = startNotificationMediaManagerListening();
        boolean mediaSessionListening = startMediaSessionListening();
        if (!notificationListening && !mediaSessionListening && !initialSnapshot.active) {
            deliverSnapshot(ClockDetailMediaSnapshot.EMPTY);
        }
    }

    void stopListening() {
        cancelPendingEmptySnapshot();
        this.emptySnapshotGraceDeadlineUptimeMs = 0L;
        this.lastDeliveredSnapshot = ClockDetailMediaSnapshot.EMPTY;
        stopNotificationMediaManagerListening();
        stopMediaSessionListening();
        this.callback = null;
        this.resultHandler = null;
    }

    boolean skipToPrevious() {
        return dispatchTransportCommand(
                PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                controls -> controls.skipToPrevious(),
                "skipToPrevious");
    }

    boolean skipToNext() {
        return dispatchTransportCommand(
                PlaybackState.ACTION_SKIP_TO_NEXT,
                controls -> controls.skipToNext(),
                "skipToNext");
    }

    boolean togglePlayPause() {
        MediaController controller = resolveControllableController();
        if (controller == null) {
            return false;
        }
        MediaController.TransportControls controls = controller.getTransportControls();
        if (controls == null) {
            return false;
        }
        PlaybackState playbackStateObject = resolvePlaybackStateObject(controller);
        int playbackState = playbackStateObject != null
                ? playbackStateObject.getState()
                : PlaybackState.STATE_NONE;
        long actions = playbackStateObject != null ? playbackStateObject.getActions() : 0L;
        boolean shouldResume = isPausedPlaybackState(playbackState);
        long primaryAction = shouldResume ? PlaybackState.ACTION_PLAY : PlaybackState.ACTION_PAUSE;
        if (!supportsAnyAction(actions, primaryAction, PlaybackState.ACTION_PLAY_PAUSE)) {
            return false;
        }
        try {
            if (shouldResume) {
                controls.play();
            } else {
                controls.pause();
            }
            return true;
        } catch (Throwable t) {
            logMediaWarning("togglePlayPause failed", t);
            return false;
        }
    }

    private SnapshotQueryResult queryNotificationMediaManagerSnapshot() {
        if (!ensureNotificationMediaManagerBridge()) {
            return SnapshotQueryResult.unavailable();
        }
        ClockDetailMediaSnapshot snapshot =
                buildSnapshotFromNotificationMediaManager(null, INVALID_PLAYBACK_STATE);
        return SnapshotQueryResult.available(snapshot);
    }

    private SnapshotQueryResult queryCurrentMediaSessionSnapshot() {
        if (mediaSessionManager == null) {
            return SnapshotQueryResult.unavailable();
        }
        try {
            ControllerSelection selection =
                    selectPrimaryController(mediaSessionManager.getActiveSessions(null));
            ClockDetailMediaSnapshot snapshot = selection.controller != null
                    ? buildSnapshotFromController(selection.controller, null, INVALID_PLAYBACK_STATE, null)
                    : ClockDetailMediaSnapshot.EMPTY;
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
        publishSnapshot(buildSnapshotFromNotificationMediaManager(metadata, playbackState));
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
        refreshActiveController();
        return true;
    }

    private void stopMediaSessionListening() {
        if (mediaSessionManager != null && activeSessionsChangedListener != null) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener);
            } catch (Throwable t) {
                logMediaWarning("stopMediaSessionListening failed", t);
            }
        }
        activeSessionsChangedListener = null;
        swapActiveController(null);
    }

    private void refreshActiveController() {
        MediaController nextController = choosePrimaryActiveController();
        swapActiveController(nextController);
        publishControllerSnapshot();
    }

    private void publishControllerSnapshot() {
        publishSnapshot(buildSnapshotFromController(
                activeController,
                null,
                INVALID_PLAYBACK_STATE,
                null));
    }

    private void publishSnapshot(ClockDetailMediaSnapshot snapshot) {
        ClockDetailMediaSnapshot safeSnapshot = snapshot != null && snapshot.active
                ? snapshot
                : ClockDetailMediaSnapshot.EMPTY;
        if (safeSnapshot.active) {
            cancelPendingEmptySnapshot();
            emptySnapshotGraceDeadlineUptimeMs =
                    SystemClock.uptimeMillis() + EMPTY_SNAPSHOT_GRACE_MS;
            deliverSnapshot(safeSnapshot);
            return;
        }
        if (shouldDeferEmptySnapshot()) {
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
        mainHandler.removeCallbacks(pendingEmptySnapshotRunnable);
        mainHandler.postDelayed(pendingEmptySnapshotRunnable, delayMs);
    }

    private void cancelPendingEmptySnapshot() {
        mainHandler.removeCallbacks(pendingEmptySnapshotRunnable);
    }

    private void flushPendingEmptySnapshot() {
        if (shouldDeferEmptySnapshot()) {
            schedulePendingEmptySnapshot();
            return;
        }
        deliverSnapshot(ClockDetailMediaSnapshot.EMPTY);
    }

    private MediaController choosePrimaryActiveController() {
        if (mediaSessionManager == null) {
            return null;
        }
        try {
            return selectPrimaryController(mediaSessionManager.getActiveSessions(null)).controller;
        } catch (Throwable t) {
            logMediaWarning("choosePrimaryActiveController failed", t);
            return null;
        }
    }

    private void swapActiveController(MediaController nextController) {
        if (sameSessions(activeController, nextController)) {
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
        if (activeController != null) {
            try {
                activeController.registerCallback(controllerCallback, mainHandler);
            } catch (Throwable t) {
                logMediaWarning("register activeController callback failed", t);
            }
        }
    }

    private boolean dispatchTransportCommand(
            long requiredAction,
            TransportControlsCommand command,
            String operationName) {
        MediaController controller = resolveControllableController();
        if (controller == null || command == null) {
            return false;
        }
        MediaController.TransportControls controls = controller.getTransportControls();
        if (controls == null) {
            return false;
        }
        long actions = resolveAvailableActions(controller);
        if (!supportsAction(actions, requiredAction)) {
            return false;
        }
        try {
            command.run(controls);
            return true;
        } catch (Throwable t) {
            logMediaWarning(operationName + " failed", t);
            return false;
        }
    }

    private MediaController resolveControllableController() {
        if (activeController != null) {
            return activeController;
        }
        MediaController controller = choosePrimaryActiveController();
        if (controller != null) {
            return controller;
        }
        if (!ensureNotificationMediaManagerBridge()) {
            return null;
        }
        return extractNotificationMediaController(
                notificationMediaManager,
                notificationMediaControllerField);
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
        PlaybackState playbackStateObject = resolvePlaybackStateObject(controller);
        int resolvedState = playbackState != INVALID_PLAYBACK_STATE
                ? playbackState
                : (playbackStateObject != null
                        ? playbackStateObject.getState()
                        : PlaybackState.STATE_NONE);
        if (!shouldDisplayPlaybackState(resolvedState)) {
            return ClockDetailMediaSnapshot.EMPTY;
        }
        long availableActions = playbackStateObject != null
                ? playbackStateObject.getActions()
                : 0L;
        MediaMetadata resolvedMetadata = metadata != null ? metadata : controller.getMetadata();
        String packageName = safePackageName(controller.getPackageName());
        CharSequence title = resolveTitle(resolvedMetadata);
        CharSequence subtitle = resolveSubtitle(resolvedMetadata);
        CharSequence playbackLabel = describePlaybackState(resolvedState);
        ResolvedArtwork artwork = resolveArtwork(resolvedMetadata, fallbackDrawable, packageName);
        PendingIntent launchIntent = resolveLaunchIntent(controller);
        return new ClockDetailMediaSnapshot(
                true,
                artwork != null ? artwork.drawable : null,
                artwork != null ? artwork.key : "",
                title,
                subtitle,
                playbackLabel,
                resolvedState,
                availableActions,
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

    private ResolvedArtwork resolveArtwork(
            MediaMetadata metadata,
            Drawable fallbackDrawable,
            String packageName) {
        ResolvedArtwork artwork = resolveArtworkFromMetadata(metadata);
        if (artwork != null) {
            return artwork;
        }
        if (fallbackDrawable != null) {
            return buildArtworkFromDrawable(fallbackDrawable, "fallback:" + packageName);
        }
        return buildArtworkFromDrawable(resolveApplicationIcon(packageName), "app:" + packageName);
    }

    private ResolvedArtwork resolveArtworkFromMetadata(MediaMetadata metadata) {
        if (metadata == null || context == null) {
            return null;
        }
        try {
            MediaDescription description = metadata.getDescription();
            if (description != null) {
                Bitmap iconBitmap = description.getIconBitmap();
                if (iconBitmap != null) {
                    return buildArtworkFromBitmap(
                            iconBitmap,
                            "desc:" + stringify(description.getIconUri()));
                }
            }
            Bitmap artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (artwork == null) {
                artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (artwork == null) {
                artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            if (artwork == null) {
                return null;
            }
            String artworkKey = firstNonEmpty(
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                    metadata.getString(MediaMetadata.METADATA_KEY_ART_URI),
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI));
            return buildArtworkFromBitmap(artwork, artworkKey);
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

    private CharSequence resolveTitle(MediaMetadata metadata) {
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
        return "";
    }

    private CharSequence resolveSubtitle(MediaMetadata metadata) {
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
        return !isEmpty(subtitle) ? subtitle : "";
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

    private void deliverSnapshot(ClockDetailMediaSnapshot snapshot) {
        MediaSnapshotCallback localCallback = callback;
        Handler localHandler = resultHandler;
        if (localCallback == null) {
            return;
        }
        ClockDetailMediaSnapshot safeSnapshot = snapshot != null && snapshot.active
                ? snapshot
                : ClockDetailMediaSnapshot.EMPTY;
        if (safeSnapshot.isEquivalentTo(lastDeliveredSnapshot)) {
            lastDeliveredSnapshot = safeSnapshot;
            if (safeSnapshot.active) {
                cacheLastActiveSnapshot(safeSnapshot);
            } else {
                clearLastActiveSnapshot();
            }
            return;
        }
        lastDeliveredSnapshot = safeSnapshot;
        if (safeSnapshot.active) {
            cacheLastActiveSnapshot(safeSnapshot);
        } else {
            clearLastActiveSnapshot();
        }
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
        PlaybackState playbackState = resolvePlaybackStateObject(controller);
        return playbackState != null ? playbackState.getState() : PlaybackState.STATE_NONE;
    }

    private static PlaybackState resolvePlaybackStateObject(MediaController controller) {
        if (controller == null) {
            return null;
        }
        try {
            return controller.getPlaybackState();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int scorePlaybackState(int playbackState) {
        switch (playbackState) {
            case PlaybackState.STATE_PLAYING:
                return 4;
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
                return 3;
            case PlaybackState.STATE_PAUSED:
                return 2;
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

    private static ControllerSelection selectPrimaryController(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) {
            return ControllerSelection.EMPTY;
        }
        MediaController bestController = null;
        int bestScore = Integer.MIN_VALUE;
        for (MediaController controller : controllers) {
            int score = scorePlaybackState(resolvePlaybackState(controller));
            if (score > bestScore) {
                bestScore = score;
                bestController = controller;
            }
            if (score >= 4) {
                break;
            }
        }
        return bestScore > 0
                ? new ControllerSelection(bestController)
                : ControllerSelection.EMPTY;
    }

    private static boolean shouldDisplayPlaybackState(int playbackState) {
        switch (playbackState) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_PAUSED:
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
            case PlaybackState.STATE_PAUSED:
                return "已暂停";
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

    private ResolvedArtwork buildArtworkFromBitmap(Bitmap source, String keyHint) {
        if (source == null || source.isRecycled() || context == null) {
            return null;
        }
        try {
            Bitmap scaledBitmap = Bitmap.createBitmap(
                    artworkSizePx,
                    artworkSizePx,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(scaledBitmap);
            canvas.drawBitmap(source, null, buildArtworkRect(source.getWidth(), source.getHeight()), null);
            return new ResolvedArtwork(
                    new BitmapDrawable(context.getResources(), scaledBitmap),
                    buildArtworkKey(scaledBitmap, keyHint));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ResolvedArtwork buildArtworkFromDrawable(Drawable source, String keyHint) {
        if (source == null || context == null) {
            return null;
        }
        try {
            Bitmap scaledBitmap = Bitmap.createBitmap(
                    artworkSizePx,
                    artworkSizePx,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(scaledBitmap);
            Rect previousBounds = new Rect(source.getBounds());
            source.setBounds(buildArtworkRect(source.getIntrinsicWidth(), source.getIntrinsicHeight()));
            source.draw(canvas);
            source.setBounds(previousBounds);
            return new ResolvedArtwork(
                    new BitmapDrawable(context.getResources(), scaledBitmap),
                    buildArtworkKey(scaledBitmap, keyHint));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Rect buildArtworkRect(int sourceWidth, int sourceHeight) {
        int safeSourceWidth = Math.max(1, sourceWidth);
        int safeSourceHeight = Math.max(1, sourceHeight);
        float scale = Math.min(
                (float) artworkSizePx / safeSourceWidth,
                (float) artworkSizePx / safeSourceHeight);
        int drawWidth = Math.max(1, Math.round(safeSourceWidth * scale));
        int drawHeight = Math.max(1, Math.round(safeSourceHeight * scale));
        int left = (artworkSizePx - drawWidth) / 2;
        int top = (artworkSizePx - drawHeight) / 2;
        return new Rect(left, top, left + drawWidth, top + drawHeight);
    }

    private static String buildArtworkKey(Bitmap bitmap, String keyHint) {
        if (bitmap == null) {
            return "";
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[Math.max(1, width * height)];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        long hash = 1125899906842597L;
        for (int pixel : pixels) {
            hash = (31L * hash) + pixel;
        }
        String prefix = firstNonEmpty(keyHint, "artwork");
        return prefix + ":" + width + "x" + height + ":" + Long.toHexString(hash);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String safeValue = safePackageName(value);
            if (!safeValue.isEmpty()) {
                return safeValue;
            }
        }
        return "";
    }

    private static String stringify(Object value) {
        return value != null ? value.toString() : "";
    }

    private static long resolveAvailableActions(MediaController controller) {
        PlaybackState playbackState = resolvePlaybackStateObject(controller);
        return playbackState != null ? playbackState.getActions() : 0L;
    }

    private static boolean supportsAction(long actions, long targetAction) {
        return actions == 0L || (actions & targetAction) != 0L;
    }

    private static boolean supportsAnyAction(long actions, long firstAction, long secondAction) {
        return supportsAction(actions, firstAction) || supportsAction(actions, secondAction);
    }

    private static boolean isPausedPlaybackState(int playbackState) {
        switch (playbackState) {
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED:
            case PlaybackState.STATE_NONE:
            case PlaybackState.STATE_ERROR:
                return true;
            default:
                return false;
        }
    }

    private static void logMediaWarning(String message, Throwable throwable) {
        Log.w(LOG_TAG, "[clock-media] " + message, throwable);
        FlymeStatusBarSizer.logClockWarning("[clock-media] " + message, throwable);
    }

    @FunctionalInterface
    private interface TransportControlsCommand {
        void run(MediaController.TransportControls controls);
    }

    private static final class ControllerSelection {
        static final ControllerSelection EMPTY = new ControllerSelection(null);

        final MediaController controller;

        ControllerSelection(MediaController controller) {
            this.controller = controller;
        }
    }

    private static final class ResolvedArtwork {
        final Drawable drawable;
        final String key;

        ResolvedArtwork(Drawable drawable, String key) {
            this.drawable = drawable;
            this.key = safePackageName(key);
        }
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
