package com.example.flymestatusbarsizer.feature.statusbar;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.feature.network.ConnectionRateFontWeightTest;
import com.flyme.statusbar.battery.FlymeBatteryTextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, shadows = {
        ConnectionRateFontWeightTest.FontRequests.class,
        ConnectionRateFontWeightTest.BoldPaint.class})
@ConscryptMode(ConscryptMode.Mode.OFF)
public class NativeStatusBarTextWeightTest {
    @After public void clearConfig() {
        ReflectionHelpers.setStaticField(ModuleConfig.class, "activeConfig", null);
    }

    @Test public void scopeIncludesOnlyLockscreenCarrierAndNativeBatteryText() {
        assertTrue(NativeStatusBarTextWeight.isSupported(
                "com.android.keyguard.CarrierText", "keyguard_carrier_text"));
        assertTrue(NativeStatusBarTextWeight.isSupported(
                "com.android.keyguard.CarrierText", "carrier_text"));
        assertTrue(NativeStatusBarTextWeight.isSupported(
                "com.flyme.statusbar.battery.FlymeBatteryTextView", "battery_percentage_view"));
        assertFalse(NativeStatusBarTextWeight.isSupported(
                "com.android.keyguard.CarrierText", "qs_carrier_text"));
        assertFalse(NativeStatusBarTextWeight.isSupported(
                "android.widget.TextView", "carrier_text"));
        assertFalse(NativeStatusBarTextWeight.isSupported(
                "com.android.systemui.statusbar.policy.Clock", "clock"));
        assertFalse(NativeStatusBarTextWeight.isSupported(
                "com.flyme.statusbar.battery.FlymeBatteryMeterView", "battery"));
    }

    @Test public void weightsChangeWithoutReplacingFamilyContentSizeOrNativeVisibility() {
        TextView view = text();
        view.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        view.setText("中国移动 | 中国联通");
        view.setVisibility(View.GONE);
        view.setTextColor(0xffaabbcc);
        float size = view.getTextSize();
        NativeStatusBarTextWeight.FontState state = new NativeStatusBarTextWeight.FontState(view);
        ModuleConfig config = config();
        for (int weight : new int[]{100, 900, 400, 600, 300}) {
            config.clockFontWeight = weight;
            state.apply(view, config);
            assertWeight(weight, view);
            assertTrue(view.getTypeface().isItalic());
            assertEquals("serif", shadowOf(view.getTypeface()).getFontDescription().getFamilyName());
            assertEquals(weight >= 600, view.getPaint().isFakeBoldText());
            assertEquals("中国移动 | 中国联通", view.getText().toString());
            assertEquals(size, view.getTextSize(), 0f);
            assertEquals(0xffaabbcc, view.getCurrentTextColor());
            assertEquals(View.GONE, view.getVisibility());
            Typeface applied = view.getTypeface();
            state.apply(view, config);
            assertSame(applied, view.getTypeface());
        }
    }

    @Test public void disablingEitherSwitchRestoresTheExactOriginalFontAndFakeBold() {
        TextView view = text();
        Typeface original = Typeface.create("monospace", Typeface.BOLD_ITALIC);
        view.setTypeface(original);
        view.getPaint().setFakeBoldText(true);
        NativeStatusBarTextWeight.FontState state = new NativeStatusBarTextWeight.FontState(view);
        for (boolean disableModule : new boolean[]{false, true, false}) {
            ModuleConfig config = config();
            config.clockFontWeight = 100;
            state.apply(view, config);
            assertFalse(view.getPaint().isFakeBoldText());
            if (disableModule) config.enabled = false;
            else config.clockBoldEnabled = false;
            state.apply(view, config);
            assertSame(original, view.getTypeface());
            assertTrue(view.getPaint().isFakeBoldText());
        }
    }

    @Test public void themeFontResetBecomesTheNewRestoreTargetWithoutCapturingOurBoldFlag() {
        TextView view = text();
        view.getPaint().setFakeBoldText(false);
        NativeStatusBarTextWeight.FontState state = new NativeStatusBarTextWeight.FontState(view);
        ModuleConfig config = config();
        state.apply(view, config);
        assertTrue(view.getPaint().isFakeBoldText());
        Typeface themeFont = Typeface.create("monospace", Typeface.ITALIC);
        view.setTypeface(themeFont);
        state.apply(view, config);
        assertWeight(900, view);
        assertTrue(view.getTypeface().isItalic());
        assertEquals("monospace", shadowOf(view.getTypeface()).getFontDescription().getFamilyName());
        config.clockBoldEnabled = false;
        state.apply(view, config);
        assertSame(themeFont, view.getTypeface());
        assertFalse(view.getPaint().isFakeBoldText());

        // A system change immediately before disabling must also survive restoration.
        config.clockBoldEnabled = true;
        state.apply(view, config);
        Typeface finalFont = Typeface.create("serif", Typeface.NORMAL);
        view.setTypeface(finalFont);
        config.enabled = false;
        state.apply(view, config);
        assertSame(finalFont, view.getTypeface());
    }

    @Test public void nativeBatteryLifecycleReappliesAfterInflationAndReattachment() {
        ModuleConfig config = config();
        ReflectionHelpers.setStaticField(ModuleConfig.class, "activeConfig", config);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        FlymeBatteryTextView battery = new FlymeBatteryTextView(activity);
        battery.setText("85%");
        NativeStatusBarTextWeight.track(battery);
        NativeStatusBarTextWeight.track(battery);
        root.addView(battery);
        battery.getViewTreeObserver().dispatchOnPreDraw();
        assertWeight(900, battery);

        Typeface poleStar = Typeface.create("PoleStar", Typeface.NORMAL);
        battery.setTypeface(poleStar);
        battery.getViewTreeObserver().dispatchOnPreDraw();
        assertWeight(900, battery);
        assertEquals("PoleStar", shadowOf(battery.getTypeface()).getFontDescription().getFamilyName());
        config.clockFontWeight = 200;
        NativeStatusBarTextWeight.refreshTrackedViews();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertWeight(200, battery);
        root.removeView(battery);
        battery.setTypeface(poleStar);
        root.addView(battery);
        battery.getViewTreeObserver().dispatchOnPreDraw();
        assertWeight(200, battery);
        config.clockBoldEnabled = false;
        battery.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(poleStar, battery.getTypeface());
        assertEquals("85%", battery.getText().toString());
        activity.finish();
    }

    private static TextView text() {
        return new TextView(RuntimeEnvironment.getApplication());
    }

    private static ModuleConfig config() {
        ModuleConfig config = new ModuleConfig();
        config.enabled = true;
        config.clockBoldEnabled = true;
        config.clockFontWeight = 900;
        return config;
    }

    private static void assertWeight(int expected, TextView view) {
        // Legacy Typeface shadow records the requested weight in the font descriptor.
        assertEquals(expected, shadowOf(view.getTypeface()).getFontDescription().getStyle());
    }
}
