package com.example.flymestatusbarsizer.feature.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.widget.FrameLayout;

import com.android.systemui.statusbar.StatusBarIconView;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.feature.notification.anip.AnipIconLibrary;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowBitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public final class NotificationIconTintTest {
    private static final String PACKAGE = "test.anip.app";
    private static final String MISSING_PACKAGE = "test.anip.missing";
    private Context context;
    private ActivityController<Activity> activityController;
    private FrameLayout iconContainer;
    private ModuleConfig config;
    private File bundleDirectory;
    private int nextNotificationId;

    @Before public void setup() throws Exception {
        activityController = Robolectric.buildActivity(Activity.class).setup().visible();
        context = activityController.get();
        iconContainer = new FrameLayout(context);
        activityController.get().setContentView(iconContainer);
        // Legacy graphics otherwise accepts arbitrary bytes as a bitmap, hiding decode failures.
        ShadowBitmapFactory.setAllowInvalidImageData(false);
        config = new ModuleConfig();
        config.enabled = true;
        config.notificationAppIconEnabled = true;
        config.anipIconEnabled = true;
        setStaticField(ModuleConfig.class, "activeConfig", config);
        installPackage(PACKAGE);
        installPackage(MISSING_PACKAGE);
        NotificationHooks.clearRenderedNotificationAppIconCache();
        AnipIconLibrary.get().invalidate();
        setStaticField(NotificationHooks.class, "flymeGetApplicationIconMethod",
                getClass().getMethod("applicationIcon", Context.class, String.class, int.class));

        bundleDirectory = new File(context.getCacheDir(), "tint-test-anip");
        File resourceDirectory = new File(bundleDirectory, "res");
        assertTrue(resourceDirectory.mkdirs() || resourceDirectory.isDirectory());
        Files.write(new File(bundleDirectory, "manifest.json").toPath(),
                ("{\"" + PACKAGE + "\":{\"label\":\"Test\",\"format\":\"png\"}}")
                        .getBytes(StandardCharsets.UTF_8));
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        try (FileOutputStream output = new FileOutputStream(artworkFile())) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        setStaticField(NotificationHooks.class, "ANIP_BUNDLE_DIRECTORY", bundleDirectory);
        setStaticField(NotificationHooks.class, "ANIP_BUNDLE_RESOLVED", true);
    }

    @After public void cleanup() throws Exception {
        config.enabled = false;
        for (int i = 0; i < iconContainer.getChildCount(); i++) {
            NotificationHooks.applyNotificationStatusBarIconDrawable(iconContainer.getChildAt(i));
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        activityController.pause().stop().destroy();
        NotificationHooks.clearRenderedNotificationAppIconCache();
        AnipIconLibrary.get().invalidate();
        setStaticField(ModuleConfig.class, "activeConfig", null);
        setStaticField(NotificationHooks.class, "flymeGetApplicationIconMethod", null);
    }

    @Test public void cachedAnipKeepsSystemTintOnAnotherViewAndOnReuse() {
        StatusBarIconView first = newView(PACKAGE);
        StatusBarIconView second = newView(PACKAGE);
        NotificationHooks.applyNotificationStatusBarIconDrawable(first);
        NotificationHooks.applyNotificationStatusBarIconDrawable(second);

        assertSystemTint(first, Color.BLACK);
        assertSystemTint(second, Color.BLACK);
        assertNotSame(first.getDrawable(), second.getDrawable());
        assertSame(bitmap(first), bitmap(second));

        Drawable reused = second.getDrawable();
        for (int color : new int[]{Color.WHITE, Color.BLACK}) {
            second.setSystemTint(color);
            NotificationHooks.applyNotificationStatusBarIconDrawable(second);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertSame(reused, second.getDrawable());
            assertSystemTint(second, color);
        }
    }

    @Test public void missingRuleCachesApplicationSourceDespiteAnipPreference() {
        assertCachedFallback(MISSING_PACKAGE);
    }

    @Test public void undecodableAnipCachesApplicationSourceDespiteAnipPreference() throws Exception {
        Files.write(artworkFile().toPath(), "not a png".getBytes(StandardCharsets.UTF_8));
        assertCachedFallback(PACKAGE);
    }

    @Test public void switchingSourcesUpdatesMarkerAndRestoresCurrentSystemColor() {
        StatusBarIconView view = newView(PACKAGE);
        NotificationHooks.applyNotificationStatusBarIconDrawable(view);
        Bitmap anipBitmap = bitmap(view);
        assertSystemTint(view, Color.BLACK);

        config.anipIconEnabled = false;
        NotificationHooks.applyNotificationStatusBarIconDrawable(view);
        assertOriginalColors(view);
        view.setSystemTint(Color.WHITE);
        assertOriginalColors(view);

        // Simulate the current SystemUI colour changing after its last cleared filter was saved.
        view.currentIconColor = Color.BLACK;
        config.anipIconEnabled = true;
        NotificationHooks.applyNotificationStatusBarIconDrawable(view);
        assertSame(anipBitmap, bitmap(view));
        assertNotNull(view.getImageTintList());
        assertSame(view.lastSystemColorFilter, view.getColorFilter());
        // A desktop-path clear runnable may still be queued when the view becomes ANIP again.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(view.lastSystemColorFilter, view.getColorFilter());

        view.setSystemTint(Color.BLACK);
        assertSystemTint(view, Color.BLACK);
        config.anipIconEnabled = false;
        NotificationHooks.applyNotificationStatusBarIconDrawable(view);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertOriginalColors(view);
    }

    @Test public void cachedAnipViewsParticipateInTrackedRefresh() {
        StatusBarIconView first = newView(PACKAGE);
        StatusBarIconView second = newView(PACKAGE);
        NotificationHooks.applyNotificationStatusBarIconDrawable(first);
        NotificationHooks.applyNotificationStatusBarIconDrawable(second);
        assertSystemTint(first, Color.BLACK);
        assertSystemTint(second, Color.BLACK);

        // Force both tracked views to fall back; refresh also invalidates their rendered caches.
        config.anipIconEnabled = false;
        NotificationHooks.forceRefreshTrackedNotificationIcons();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertOriginalColors(first);
        assertOriginalColors(second);
    }

    private void assertCachedFallback(String packageName) {
        StatusBarIconView first = newView(packageName);
        StatusBarIconView second = newView(packageName);
        NotificationHooks.applyNotificationStatusBarIconDrawable(first);
        NotificationHooks.applyNotificationStatusBarIconDrawable(second);
        assertSame(bitmap(first), bitmap(second));
        for (StatusBarIconView view : new StatusBarIconView[]{first, second}) {
            view.setSystemTint(Color.WHITE);
            NotificationHooks.applyNotificationStatusBarIconDrawable(view);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertOriginalColors(view);
        }
    }

    private void installPackage(String packageName) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        Shadows.shadowOf(context.getPackageManager()).installPackage(info);
    }

    private StatusBarIconView newView(String packageName) {
        StatusBarNotification notification = new StatusBarNotification(packageName, packageName,
                ++nextNotificationId, null, 10000, 0, 0, new Notification(),
                UserHandle.getUserHandleForUid(10000), 0L);
        StatusBarIconView view = new StatusBarIconView(context, notification);
        iconContainer.addView(view, new FrameLayout.LayoutParams(20, 20));
        return view;
    }

    private File artworkFile() {
        return new File(bundleDirectory, "res/" + PACKAGE + ".png");
    }

    private static Bitmap bitmap(StatusBarIconView view) {
        assertTrue(view.getDrawable() instanceof BitmapDrawable);
        return ((BitmapDrawable) view.getDrawable()).getBitmap();
    }

    private static void assertSystemTint(StatusBarIconView view, int color) {
        assertNotNull(view.getImageTintList());
        assertEquals(color, view.getImageTintList().getDefaultColor());
        assertNotNull(view.lastSystemColorFilter);
        assertSame(view.lastSystemColorFilter, view.getColorFilter());
    }

    private static void assertOriginalColors(StatusBarIconView view) {
        assertNotNull(view.getDrawable());
        assertNull(view.getImageTintList());
        assertNull(view.getColorFilter());
        assertNull(view.getDrawable().getColorFilter());
    }

    public static Drawable applicationIcon(Context context, String packageName, int userId) {
        return new ColorDrawable(Color.RED);
    }

    private static void setStaticField(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
