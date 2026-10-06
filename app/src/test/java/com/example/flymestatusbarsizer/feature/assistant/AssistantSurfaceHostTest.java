package com.example.flymestatusbarsizer.feature.assistant;

import android.app.Dialog;
import android.content.Context;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantSurfaceHostTest {
    @Test public void movingBetweenHostsRefreshesTheRemoteHostOnEveryRoundTrip() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Dialog desktop = new Dialog(context);
        FrameLayout desktopHost = new FrameLayout(context);
        desktop.setContentView(desktopHost);
        desktop.show();
        Dialog global = new Dialog(context);
        FrameLayout globalHost = new FrameLayout(context);
        global.setContentView(globalHost);
        global.show();
        shadowOf(Looper.getMainLooper()).idle();
        FrameLayout cards = new FrameLayout(context);
        NativeSurface surface = new NativeSurface(context);
        SurfaceView ordinarySurface = new SurfaceView(context);
        View ordinaryCard = new View(context);
        cards.addView(surface);
        cards.addView(ordinarySurface);
        cards.addView(ordinaryCard);
        desktopHost.addView(cards);
        AssistantSurfaceHost host = new AssistantSurfaceHost(NativeSurface.class);
        Object data = surface.cardData;
        try {
            Object desktopRoot = surface.getRootView();
            assertSame(desktopRoot, surface.remoteHost);

            // Reproduce the native cache bug: the package still looks valid after reparenting.
            desktopHost.removeView(cards);
            globalHost.addView(cards);
            assertSame(desktopRoot, surface.remoteHost);
            assertNotSame(surface.getRootView(), surface.remoteHost);
            globalHost.removeView(cards);
            desktopHost.addView(cards);

            int expectedRequests = 1;
            for (int i = 0; i < 3; i++) {
                host.prepareForHostChange(cards);
                // Invalidating is deferred until native detach, while the old root still exists.
                assertSame(desktopRoot, surface.remoteHost);
                desktopHost.removeView(cards);
                assertNull(surface.remoteHost);
                globalHost.addView(cards);
                assertSame(surface.getRootView(), surface.remoteHost);
                assertNotSame(desktopRoot, surface.remoteHost);
                assertEquals(++expectedRequests, surface.requests);

                host.prepareForHostChange(cards);
                globalHost.removeView(cards);
                desktopHost.addView(cards);
                assertSame(desktopRoot, surface.remoteHost);
                assertEquals(++expectedRequests, surface.requests);
                assertFalse(surface.startRelease);
                assertSame(data, surface.cardData);
                assertSame(cards, ordinarySurface.getParent());
                assertEquals(View.VISIBLE, ordinarySurface.getVisibility());
                assertEquals(View.VISIBLE, ordinaryCard.getVisibility());
            }
        } finally {
            desktop.dismiss();
            global.dismiss();
        }
    }

    @Test public void detachedCardsAreNotArmedForAnUnrelatedFutureDetach() throws Exception {
        NativeSurface surface = new NativeSurface(RuntimeEnvironment.getApplication());
        new AssistantSurfaceHost(NativeSurface.class).prepareForHostChange(surface);
        assertFalse(surface.startRelease);
        assertEquals(0, surface.requests);
    }

    @Test public void unavailableFlymeSurfaceClassLeavesOtherContentAlone() {
        FrameLayout cards = new FrameLayout(RuntimeEnvironment.getApplication());
        View card = new View(cards.getContext());
        cards.addView(card);
        AssistantSurfaceHost.create(getClass().getClassLoader()).prepareForHostChange(cards);
        assertSame(cards, card.getParent());
        assertEquals(View.VISIBLE, card.getVisibility());
    }

    /** Models the verified w6.c cache/release protocol, without pretending to render a Surface. */
    public static class NativeSurface extends SurfaceView {
        final Object cardData = new Object();
        Object remoteHost;
        boolean startRelease;
        int requests;

        NativeSurface(Context context) { super(context); }

        public void setStartRelease(boolean value) { startRelease = value; }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (remoteHost == null) {
                remoteHost = getRootView();
                requests++;
            }
        }

        @Override protected void onDetachedFromWindow() {
            if (startRelease) {
                remoteHost = null;
                startRelease = false;
            }
            super.onDetachedFromWindow();
        }
    }
}
