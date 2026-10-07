package com.example.flymestatusbarsizer.feature.assistant;

import android.content.res.ColorStateList;

import com.example.flymestatusbarsizer.config.SettingsStore;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantTitleColorTest {
    @Test public void customBackgroundFixedColorsIgnoreStatusBarChangesAndRestoreNativeControls() {
        for (int mode : new int[]{SettingsStore.ASSISTANT_FOREGROUND_BLACK, SettingsStore.ASSISTANT_FOREGROUND_WHITE}) {
            Header header = new Header();
            ColorStateList originalTitle = header.title.getTextColors();
            ColorStateList originalHint = header.text.getHintTextColors();
            android.graphics.drawable.Drawable originalBackground = header.search.getBackground();
            int alpha = originalBackground.getAlpha();
            AssistantTitleColor color = header.colors();
            color.setBackgroundColorMode(true, mode);
            // Fixed colors work before SystemUI has supplied a tint.
            color.apply();
            int expected = mode == SettingsStore.ASSISTANT_FOREGROUND_BLACK ? Color.BLACK : Color.WHITE;
            header.assertTint(expected);
            for (Integer status : new Integer[]{Color.WHITE, Color.BLACK, Color.RED, null}) {
                color.setTint(status);
                header.draw();
                header.assertTint(expected);
                assertSame(originalBackground, header.search.getBackground());
                assertEquals(alpha, originalBackground.getAlpha());
            }
            color.restore();
            assertSame(originalTitle, header.title.getTextColors());
            assertSame(originalHint, header.text.getHintTextColors());
            assertNull(header.add.getImageTintList());
            assertNull(header.icon.getImageTintList());
            assertNull(header.search.getBackgroundTintList());
        }
    }

    @Test public void applicationBackgroundAlwaysFollowsAndSwitchingBackUsesLatestStatusBarTint() {
        Header header = new Header();
        AssistantTitleColor color = header.colors();
        color.setBackgroundColorMode(true, SettingsStore.ASSISTANT_FOREGROUND_BLACK);
        color.setTint(Color.WHITE);
        color.apply();
        header.assertTint(Color.BLACK);
        color.setBackgroundColorMode(false, SettingsStore.ASSISTANT_FOREGROUND_BLACK);
        header.assertTint(Color.WHITE);
        color.setTint(Color.RED);
        header.assertTint(Color.RED);
        color.setBackgroundColorMode(true, SettingsStore.ASSISTANT_FOREGROUND_FOLLOW_STATUS_BAR);
        color.setTint(Color.WHITE);
        header.assertTint(Color.WHITE);
        color.setBackgroundColorMode(true, 99);
        color.setTint(Color.BLACK);
        header.assertTint(Color.BLACK);
        color.restore();
    }

    @Test public void returningFromFixedModeWithoutStatusBarTintRestoresNativeColors() {
        Header header = new Header();
        ColorStateList originalTitle = header.title.getTextColors();
        AssistantTitleColor color = header.colors();
        color.setBackgroundColorMode(true, SettingsStore.ASSISTANT_FOREGROUND_WHITE);
        color.apply();
        header.assertTint(Color.WHITE);
        color.setBackgroundColorMode(true, SettingsStore.ASSISTANT_FOREGROUND_FOLLOW_STATUS_BAR);
        assertSame(originalTitle, header.title.getTextColors());
        assertNull(header.search.getBackgroundTintList());
        color.restore();
    }

    @Test public void globalTintFollowsTransitionsAndRestoresStatefulDesktopColors() {
        TextView title = new TextView(RuntimeEnvironment.getApplication());
        ColorStateList nativeColors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{Color.RED, Color.BLACK});
        title.setTextColor(nativeColors);
        AssistantTitleColor color = new AssistantTitleColor(title);
        color.setTint(Color.WHITE);
        assertSame(nativeColors, title.getTextColors());
        color.apply();
        assertEquals(Color.WHITE, title.getCurrentTextColor());
        color.setTint(0xff777777);
        assertEquals(0xff777777, title.getCurrentTextColor());
        color.setTint(Color.BLACK);
        assertEquals(Color.BLACK, title.getCurrentTextColor());
        color.restore();
        assertSame(nativeColors, title.getTextColors());
        color.setTint(Color.WHITE);
        title.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(nativeColors, title.getTextColors());
    }

    @Test public void wallpaperChangesAreSuppressedGloballyButPreservedForDesktop() {
        TextView title = new TextView(RuntimeEnvironment.getApplication());
        title.setTextColor(Color.WHITE);
        AssistantTitleColor color = new AssistantTitleColor(title);
        color.setTint(Color.BLACK);
        color.apply();
        ColorStateList newWallpaperColors = ColorStateList.valueOf(0xffeeeeee);
        title.setTextColor(newWallpaperColors);
        title.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(Color.BLACK, title.getCurrentTextColor());
        color.restore();
        assertSame(newWallpaperColors, title.getTextColors());
        title.setTextColor(Color.WHITE);
        title.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(Color.WHITE, title.getCurrentTextColor());
        color.apply();
        assertEquals(Color.BLACK, title.getCurrentTextColor());
        color.restore();
        assertEquals(Color.WHITE, title.getCurrentTextColor());
    }

    @Test public void missingTintOrCancelledAttachmentDoesNotChangeNativeTitle() {
        TextView title = new TextView(RuntimeEnvironment.getApplication());
        title.setTextColor(Color.RED);
        AssistantTitleColor color = new AssistantTitleColor(title);
        color.setTint(Color.BLACK);
        color.restore();
        assertEquals(Color.RED, title.getCurrentTextColor());
        color.setTint(null);
        color.apply();
        color.onPreDraw();
        assertEquals(Color.RED, title.getCurrentTextColor());
        color.restore();
        new AssistantTitleColor((TextView) null).apply();
    }

    @Test public void allHeaderControlsFollowTintAndRestoreTheirOwnNativeColors() {
        Header header = new Header();
        ColorStateList imageColors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{Color.RED, Color.GREEN});
        ColorStateList hints = ColorStateList.valueOf(Color.BLUE);
        header.add.setImageTintList(imageColors);
        header.add.setImageTintMode(PorterDuff.Mode.SRC_IN);
        header.text.setTextColor(Color.RED);
        header.text.setHintTextColor(hints);
        header.icon.setAlpha(0.6f);
        TextView card = new TextView(RuntimeEnvironment.getApplication());
        card.setTextColor(Color.MAGENTA);
        header.root.addView(card);
        AssistantTitleColor color = header.colors();
        color.setTint(Color.WHITE);
        assertSame(imageColors, header.add.getImageTintList());
        assertNull(header.search.getBackgroundTintList());
        color.apply();
        for (int tint : new int[]{Color.WHITE, 0xff777777, Color.BLACK}) {
            color.setTint(tint);
            header.assertTint(tint);
        }
        assertEquals(0.6f, header.icon.getAlpha(), 0f);
        assertEquals(Color.MAGENTA, card.getCurrentTextColor());
        color.restore();
        assertSame(imageColors, header.add.getImageTintList());
        assertEquals(PorterDuff.Mode.SRC_IN, header.add.getImageTintMode());
        assertNull(header.add.getBackgroundTintList());
        assertNull(header.search.getBackgroundTintList());
        assertNull(header.icon.getImageTintList());
        assertNull(header.icon.getBackgroundTintList());
        assertEquals(Color.RED, header.text.getCurrentTextColor());
        assertSame(hints, header.text.getHintTextColors());
        color.setTint(Color.WHITE);
        header.draw();
        assertSame(imageColors, header.add.getImageTintList());
        assertSame(hints, header.text.getHintTextColors());
    }

    @Test public void wallpaperUpdatesAndLastMomentNativeChangesSurviveClosing() {
        Header header = new Header();
        AssistantTitleColor color = header.colors();
        color.setTint(Color.BLACK);
        color.apply();
        ColorStateList wallpaper = ColorStateList.valueOf(0xffeeeeee);
        header.add.setImageTintList(wallpaper);
        header.add.setBackgroundTintList(wallpaper);
        header.text.setTextColor(wallpaper);
        header.text.setHintTextColor(wallpaper);
        header.icon.setImageTintList(wallpaper);
        header.search.setBackgroundTintList(wallpaper);
        header.draw();
        header.assertTint(Color.BLACK);
        // A native change can also arrive immediately before restore, without a pre-draw.
        header.icon.setImageTintList(null);
        color.restore();
        assertSame(wallpaper, header.add.getImageTintList());
        assertSame(wallpaper, header.add.getBackgroundTintList());
        assertSame(wallpaper, header.text.getTextColors());
        assertSame(wallpaper, header.text.getHintTextColors());
        assertSame(wallpaper, header.search.getBackgroundTintList());
        assertNull(header.icon.getImageTintList());
        color.apply();
        header.assertTint(Color.BLACK);
        color.restore();
        assertSame(wallpaper, header.text.getHintTextColors());
        assertNull(header.icon.getImageTintList());
    }

    @Test public void lateAndRecycledSearchViewsFollowTintEvenWithoutTitle() {
        Header header = new Header();
        header.root.removeView(header.title);
        header.root.removeView(header.search);
        AssistantTitleColor color = header.colors();
        color.setTint(Color.BLACK);
        color.apply();
        assertEquals(Color.BLACK, header.add.getImageTintList().getDefaultColor());
        ColorStateList hints = header.text.getHintTextColors();
        header.root.addView(header.search);
        header.draw();
        assertEquals(Color.BLACK, header.text.getCurrentHintTextColor());
        Header replacement = new Header();
        replacement.root.removeView(replacement.search);
        ColorStateList replacementHints = replacement.text.getHintTextColors();
        header.root.removeView(header.search);
        header.root.addView(replacement.search);
        header.draw();
        assertSame(hints, header.text.getHintTextColors());
        assertNull(header.search.getBackgroundTintList());
        assertNull(header.icon.getImageTintList());
        assertEquals(Color.BLACK, replacement.text.getCurrentHintTextColor());
        assertEquals(Color.BLACK, replacement.icon.getImageTintList().getDefaultColor());
        color.restore();
        assertSame(replacementHints, replacement.text.getHintTextColors());
        assertNull(replacement.search.getBackgroundTintList());
        assertNull(replacement.icon.getImageTintList());
    }

    @Test public void noTintOrCancelledAttachmentLeavesAllNativeControlsAlone() {
        Header header = new Header();
        ColorStateList hints = header.text.getHintTextColors();
        AssistantTitleColor color = header.colors();
        color.setTint(Color.BLACK);
        color.restore();
        assertNull(header.add.getImageTintList());
        assertNull(header.search.getBackgroundTintList());
        color.setTint(null);
        color.apply();
        header.draw();
        color.restore();
        assertSame(hints, header.text.getHintTextColors());
        assertNull(header.add.getImageTintList());
        assertNull(header.search.getBackgroundTintList());
        assertNull(header.icon.getImageTintList());
    }

    @Test public void searchBackgroundDrawsWithStatusBarTintAndNativeTranslucency() {
        Header header = new Header();
        AssistantTitleColor color = header.colors();
        color.setTint(Color.BLACK);
        color.apply();
        for (int nativeColor : new int[]{0x40ffffff, 0x59000000}) {
            // SearchViewHolder can rebind the native background while the panel is open.
            header.search.setBackgroundColor(nativeColor);
            ColorDrawable background = (ColorDrawable) header.search.getBackground();
            for (int tint : new int[]{Color.BLACK, Color.WHITE}) {
                color.setTint(tint);
                header.draw();
                background.setBounds(0, 0, 2, 2);
                int[] draws = {0};
                // Inspect the actual Drawable draw call; native pixel rendering is unavailable
                // in Robolectric on Linux/aarch64, so final composition still needs a device.
                background.draw(new Canvas() {
                    @Override public void drawRect(Rect bounds, Paint paint) {
                        draws[0]++;
                        assertEquals(nativeColor, paint.getColor());
                        assertTrue(paint.getColorFilter() instanceof PorterDuffColorFilter);
                        PorterDuffColorFilter filter = (PorterDuffColorFilter) paint.getColorFilter();
                        assertEquals(tint, shadowOf(filter).getColor());
                        assertEquals(PorterDuff.Mode.SRC_IN, shadowOf(filter).getMode());
                    }
                });
                assertEquals(1, draws[0]);
                assertEquals(nativeColor, background.getColor());
            }
        }
        color.restore();
        assertNull(header.search.getBackgroundTintList());
        assertEquals(0x59000000, ((ColorDrawable) header.search.getBackground()).getColor());
    }

    private static final class Header {
        final FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        final TextView title = new TextView(root.getContext());
        final ImageView add = new ImageView(root.getContext());
        final FrameLayout search = new FrameLayout(root.getContext());
        final TextView text = new TextView(root.getContext());
        final ImageView icon = new ImageView(root.getContext());

        Header() {
            View[] views = {title, add, search, text, icon};
            for (int i = 0; i < views.length; i++) views[i].setId(i + 1);
            root.addView(title);
            root.addView(add);
            root.addView(search);
            search.addView(text);
            search.addView(icon);
            search.setBackgroundColor(0x40ffffff);
            text.setEnabled(false);
            text.setHint("搜索");
            text.setHintTextColor(Color.WHITE);
            add.setImageDrawable(new ColorDrawable(Color.WHITE));
            icon.setImageDrawable(new ColorDrawable(Color.WHITE));
        }

        AssistantTitleColor colors() { return new AssistantTitleColor(root, 1, 2, 3, 4, 5); }
        void draw() { root.getViewTreeObserver().dispatchOnPreDraw(); }

        void assertTint(int tint) {
            assertEquals(tint, title.getCurrentTextColor());
            assertEquals(tint, add.getImageTintList().getDefaultColor());
            assertEquals(tint, add.getBackgroundTintList().getDefaultColor());
            assertEquals(tint, search.getBackgroundTintList().getDefaultColor());
            assertEquals(tint, text.getCurrentTextColor());
            assertEquals(tint, text.getCurrentHintTextColor());
            assertEquals(tint, icon.getImageTintList().getDefaultColor());
            assertEquals(tint, icon.getBackgroundTintList().getDefaultColor());
        }
    }
}
