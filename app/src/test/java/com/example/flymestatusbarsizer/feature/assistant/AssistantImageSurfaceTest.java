package com.example.flymestatusbarsizer.feature.assistant;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.view.SurfaceControl;
import android.widget.FrameLayout;

import com.example.flymestatusbarsizer.config.ModuleConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, manifest = Config.NONE, shadows = {
        AssistantBlurSurfaceTest.BuilderShadow.class, AssistantBlurSurfaceTest.TransactionShadow.class})
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantImageSurfaceTest {
    @Test public void centerCropFillsPortraitAndLandscapeWithoutStretching() {
        assertEquals(new RectF(-100, 0, 300, 400), AssistantImageSurface.centerCrop(100, 100, 200, 400));
        assertEquals(new RectF(0, -100, 400, 300), AssistantImageSurface.centerCrop(100, 100, 400, 200));
        assertEquals(new RectF(0, 0, 200, 400), AssistantImageSurface.centerCrop(100, 200, 200, 400));
    }

    @Test public void imageStaysBelowCardsAndSlidingClipsTheImageInsteadOfRescalingIt() throws Exception {
        Dialog dialog = new Dialog(RuntimeEnvironment.getApplication());
        FrameLayout content = new FrameLayout(dialog.getContext());
        dialog.setContentView(content);
        dialog.show();
        shadowOf(Looper.getMainLooper()).idle();
        content.layout(0, 0, 200, 400);
        SurfaceControl window = AssistantBlurSurfaceTest.windowSurface();
        AssistantReflection.set(AssistantReflection.call(content, "getViewRootImpl"), "mSurfaceControl", window);
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        AssistantImageSurface image = AssistantImageSurface.create(content, bitmap);
        assertNotNull(image);
        AssistantBlurSurfaceTest.State state = AssistantBlurSurfaceTest.state(
                (SurfaceControl) AssistantReflection.get(image, "surface"));
        try {
            assertSame(window, state.parent);
            assertSame(window, state.relativeTo);
            assertTrue(state.layer < -2);
            assertEquals(0, state.radius);
            AssistantBlurSurfaceTest.RecordingRoot root = new AssistantBlurSurfaceTest.RecordingRoot();
            // Left exit: keep the right half of the same original image buffer.
            image.update(new Rect(0, 0, 100, 400), -100, 0, 0.5f, root);
            assertEquals(-100, state.x, 0);
            assertEquals(new Rect(100, 0, 200, 400), state.crop);
            assertEquals(0.5f, state.alpha, 0);
            assertTrue(state.visible);
            image.update(new Rect(0, 0, 100, 400), -100, 0, 0.5f, root);
            assertEquals(1, root.queued);
            // Right exit: keep the left half, without changing image scale.
            image.update(new Rect(100, 0, 200, 400), 100, 0, 1f, root);
            assertEquals(new Rect(0, 0, 100, 400), state.crop);
            assertEquals(100, state.x, 0);
            image.update(new Rect(), 200, 0, 0f, root);
            assertFalse(state.visible);
            image.release();
            assertNull(state.parent);
            assertFalse(image.isValid(content));
            image.release();
        } finally { image.release(); bitmap.recycle(); dialog.dismiss(); window.release(); }
    }

    @Test public void loadedImageReplacesBlurAndIsReleasedBeforeDesktopReattachment() throws Exception {
        Dialog dialog = new Dialog(RuntimeEnvironment.getApplication());
        FrameLayout content = new FrameLayout(dialog.getContext());
        ColorDrawable original = new ColorDrawable(Color.TRANSPARENT);
        content.setBackground(original);
        dialog.setContentView(content);
        dialog.show();
        shadowOf(Looper.getMainLooper()).idle();
        content.layout(0, 0, 200, 400);
        SurfaceControl window = AssistantBlurSurfaceTest.windowSurface();
        AssistantReflection.set(AssistantReflection.call(content, "getViewRootImpl"), "mSurfaceControl", window);
        AssistantWindowBackground background = new AssistantWindowBackground(content, 3);
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        try {
            background.apply();
            content.getViewTreeObserver().dispatchOnPreDraw();
            AssistantBlurSurface blur = (AssistantBlurSurface) AssistantReflection.get(background, "nativeBlur");
            assertNotNull(blur);
            AssistantBlurSurfaceTest.State previous = AssistantBlurSurfaceTest.state(blur);
            AssistantReflection.set(background, "image", bitmap);
            content.getViewTreeObserver().dispatchOnPreDraw();
            assertNull(AssistantReflection.get(background, "nativeBlur"));
            assertNull(previous.parent);
            AssistantImageSurface image = (AssistantImageSurface) AssistantReflection.get(background, "imageSurface");
            assertNotNull(image);
            AssistantBlurSurfaceTest.State state = AssistantBlurSurfaceTest.state(
                    (SurfaceControl) AssistantReflection.get(image, "surface"));
            background.restore();
            assertNull(state.parent);
            assertFalse(state.visible);
            assertSame(original, content.getBackground());
            assertNull(AssistantReflection.get(background, "image"));
            content.getViewTreeObserver().dispatchOnPreDraw();
            assertNull(AssistantReflection.get(background, "imageSurface"));
            assertNull(AssistantReflection.get(background, "nativeBlur"));
        } finally { background.restore(); bitmap.recycle(); dialog.dismiss(); window.release(); }
    }

    @Test public void disablingBlurCreatesNoEffectAndRestoresOriginalBackground() throws Exception {
        Dialog dialog = new Dialog(RuntimeEnvironment.getApplication());
        FrameLayout content = new FrameLayout(dialog.getContext());
        ColorDrawable original = new ColorDrawable(Color.BLUE);
        content.setBackground(original);
        dialog.setContentView(content);
        dialog.show();
        shadowOf(Looper.getMainLooper()).idle();
        ModuleConfig config = new ModuleConfig();
        config.assistantBackgroundBlur = false;
        AssistantWindowBackground background = new AssistantWindowBackground(content, 3, config);
        try {
            background.apply();
            content.getViewTreeObserver().dispatchOnPreDraw();
            assertNull(AssistantReflection.get(background, "nativeBlur"));
            assertNull(AssistantReflection.get(background, "imageSurface"));
            assertEquals(Color.TRANSPARENT, ((ColorDrawable) content.getBackground()).getColor());
            background.restore();
            assertSame(original, content.getBackground());
        } finally { background.restore(); dialog.dismiss(); }
    }
}
