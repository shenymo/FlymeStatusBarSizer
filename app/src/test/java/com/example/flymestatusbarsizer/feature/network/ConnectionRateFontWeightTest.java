package com.example.flymestatusbarsizer.feature.network;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.flymestatusbarsizer.config.ModuleConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLegacyTypeface;
import org.robolectric.shadows.ShadowPaint;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, shadows = {
        ConnectionRateFontWeightTest.FontRequests.class,
        ConnectionRateFontWeightTest.BoldPaint.class})
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ConnectionRateFontWeightTest {
    @Test public void lazyDigitsAndUnitFollowLiveWeightChangesWithoutChangingSizeOrColor() {
        RateView view = new RateView();
        ModuleConfig config = enabledConfig();
        float unitSize = view.mUnitView.getTextSize();
        int unitColor = view.mUnitView.getCurrentTextColor();
        assertNull(view.mPaint);

        for (int weight : new int[]{100, 400, 600, 900, 300}) {
            config.clockFontWeight = weight;
            ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
            assertRequestedWeight(weight, view.mPaint.getTypeface());
            assertRequestedWeight(weight, view.mUnitView.getTypeface());
            assertEquals(weight >= 600, view.mPaint.isFakeBoldText());
            assertEquals(weight >= 600, view.mUnitView.getPaint().isFakeBoldText());
            assertTrue(view.mPaint.getTypeface().isItalic());
            assertEquals(17f, view.mPaint.getTextSize(), 0f);
            assertEquals(0xff123456, view.mPaint.getColor());
            assertEquals(unitSize, view.mUnitView.getTextSize(), 0f);
            assertEquals(unitColor, view.mUnitView.getCurrentTextColor());

            Typeface numberFont = view.mPaint.getTypeface();
            Typeface unitFont = view.mUnitView.getTypeface();
            ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
            assertSame(numberFont, view.mPaint.getTypeface());
            assertSame(unitFont, view.mUnitView.getTypeface());
        }
    }

    @Test public void eitherSwitchRestoresNativeTypefaceAndSyntheticBoldAcrossRepeatedToggles() {
        RateView view = new RateView();
        Typeface nativeNumberFont = view.getPaint().getTypeface();
        Typeface nativeUnitFont = view.mUnitView.getTypeface();
        ModuleConfig config = enabledConfig();

        for (boolean disableModule : new boolean[]{false, true, false}) {
            config.enabled = true;
            config.clockBoldEnabled = true;
            config.clockFontWeight = 100;
            ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
            assertFalse(view.mPaint.isFakeBoldText());
            if (disableModule) {
                config.enabled = false;
            } else {
                config.clockBoldEnabled = false;
            }
            ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
            assertSame(nativeNumberFont, view.mPaint.getTypeface());
            assertSame(nativeUnitFont, view.mUnitView.getTypeface());
            assertTrue(view.mPaint.isFakeBoldText());
            assertFalse(view.mUnitView.getPaint().isFakeBoldText());
        }
    }

    @Test public void fontRefreshAndReattachmentKeepTheNewSystemFontAsRestoreTarget() {
        RateView view = new RateView();
        ModuleConfig config = enabledConfig();
        ConnectionRateHooks.applyConnectionRateFontWeight(view, config);

        // Same boundary as the hook: restore before Flyme resets fonts, apply after.
        ConnectionRateHooks.restoreConnectionRateFontWeight(view);
        Typeface themeNumberFont = Typeface.create("serif", Typeface.NORMAL);
        Typeface themeUnitFont = Typeface.create("monospace", Typeface.BOLD_ITALIC);
        view.mPaint.setTypeface(themeNumberFont);
        view.mUnitView.setTypeface(themeUnitFont);
        ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
        assertRequestedWeight(900, view.mPaint.getTypeface());
        assertRequestedWeight(900, view.mUnitView.getTypeface());
        assertTrue(view.mUnitView.getTypeface().isItalic());

        // Configuration/reattachment with no new font must not capture our weight.
        ConnectionRateHooks.restoreConnectionRateFontWeight(view);
        ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
        config.clockBoldEnabled = false;
        ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
        assertSame(themeNumberFont, view.mPaint.getTypeface());
        assertSame(themeUnitFont, view.mUnitView.getTypeface());
        assertTrue(view.mPaint.isFakeBoldText());
        assertFalse(view.mUnitView.getPaint().isFakeBoldText());
    }

    @Test public void lateUnitAndReplacementPaintReceiveWeightAndRestoreTheirOwnFonts() {
        RateView view = new RateView();
        view.mUnitView = null;
        ModuleConfig config = enabledConfig();
        ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
        view.mPaint = new Paint();
        Typeface replacementNumberFont = Typeface.create("serif", Typeface.ITALIC);
        view.mPaint.setTypeface(replacementNumberFont);
        view.mUnitView = new TextView(view.getContext());
        Typeface replacementUnitFont = view.mUnitView.getTypeface();

        ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
        assertRequestedWeight(900, view.mPaint.getTypeface());
        assertRequestedWeight(900, view.mUnitView.getTypeface());
        config.enabled = false;
        ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
        assertSame(replacementNumberFont, view.mPaint.getTypeface());
        assertSame(replacementUnitFont, view.mUnitView.getTypeface());
        assertFalse(view.mPaint.isFakeBoldText());
    }

    @Test public void disabledSettingLeavesUninitializedAndUnsupportedViewsAlone() {
        RateView view = new RateView();
        ModuleConfig config = enabledConfig();
        config.clockBoldEnabled = false;
        Typeface unitFont = view.mUnitView.getTypeface();
        ConnectionRateHooks.applyConnectionRateFontWeight(view, config);
        assertNull(view.mPaint);
        assertSame(unitFont, view.mUnitView.getTypeface());

        config.clockBoldEnabled = true;
        ConnectionRateHooks.applyConnectionRateFontWeight(
                new LinearLayout(view.getContext()), config);
    }

    private static ModuleConfig enabledConfig() {
        ModuleConfig config = new ModuleConfig();
        config.enabled = true;
        config.clockBoldEnabled = true;
        config.clockFontWeight = 900;
        return config;
    }

    private static void assertRequestedWeight(int expected, Typeface typeface) {
        FontRequests font = Shadow.extract(typeface);
        assertEquals(expected, font.requestedWeight);
    }

    // Legacy Robolectric does not implement getWeight/isItalic or fake-bold storage.
    // Record Android API arguments here; glyph rendering still requires a Flyme device.
    @Implements(Typeface.class)
    public static class FontRequests extends ShadowLegacyTypeface {
        int requestedWeight = -1;
        Boolean requestedItalic;

        @Implementation protected static Typeface create(Typeface base, int weight, boolean italic) {
            Typeface result = ShadowLegacyTypeface.create(base, weight, italic);
            FontRequests font = Shadow.extract(result);
            font.requestedWeight = weight;
            font.requestedItalic = italic;
            return result;
        }

        @Implementation protected boolean isItalic() {
            return requestedItalic != null ? requestedItalic : (getStyle() & Typeface.ITALIC) != 0;
        }
    }

    @Implements(Paint.class)
    public static class BoldPaint extends ShadowPaint {
        boolean fakeBold;

        @Implementation protected void setFakeBoldText(boolean value) {
            fakeBold = value;
        }

        @Implementation protected boolean isFakeBoldText() {
            return fakeBold;
        }
    }

    // Mirrors Flyme's distinct Canvas digit Paint and TextView unit, including lazy Paint.
    public static final class RateView extends LinearLayout {
        public Paint mPaint;
        public TextView mUnitView;

        RateView() {
            super(RuntimeEnvironment.getApplication());
            Context context = getContext();
            mUnitView = new TextView(context);
            mUnitView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            mUnitView.setText("KB/S");
            addView(mUnitView);
        }

        public Paint getPaint() {
            if (mPaint == null) {
                mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mPaint.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mPaint.setFakeBoldText(true);
                mPaint.setTextSize(17f);
                mPaint.setColor(0xff123456);
            }
            return mPaint;
        }
    }
}
