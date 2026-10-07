package com.example.flymestatusbarsizer.feature.statusbar;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Shared weight for native carrier and battery text; preserves their system font family. */
public final class NativeStatusBarTextWeight {
    private static final WeakHashMap<TextView, FontState> VIEWS = new WeakHashMap<>();

    private NativeStatusBarTextWeight() {
    }

    public static void track(TextView view) {
        if (view == null || VIEWS.containsKey(view)) {
            return;
        }
        FontState state = new FontState(view);
        VIEWS.put(view, state);
        view.addOnAttachStateChangeListener(state);
        FlymeStatusBarSizer.ensureConfigRefreshObserver(view.getContext());
        if (view.isAttachedToWindow()) {
            state.onViewAttachedToWindow(view);
        }
    }

    public static void refreshTrackedViews() {
        FlymeStatusBarSizer.postToMainHandler(() -> {
            for (TextView view : new ArrayList<>(VIEWS.keySet())) {
                FontState state = VIEWS.get(view);
                if (state != null) {
                    state.refresh();
                }
            }
        });
    }

    static boolean isSupported(String className, String idName) {
        return "com.flyme.statusbar.battery.FlymeBatteryTextView".equals(className)
                || ("com.android.keyguard.CarrierText".equals(className)
                && ("keyguard_carrier_text".equals(idName) || "carrier_text".equals(idName)));
    }

    static final class FontState implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<TextView> viewRef;
        private WeakReference<ViewTreeObserver> observerRef = new WeakReference<>(null);
        private Typeface originalTypeface;
        private boolean originalFakeBold;
        private Typeface appliedTypeface;
        private int appliedWeight = -1;
        private boolean overridden;

        FontState(TextView view) {
            viewRef = new WeakReference<>(view);
        }

        void refresh() {
            TextView view = viewRef.get();
            if (view == null) {
                return;
            }
            boolean supported = isSupported(view.getClass().getName(),
                    FlymeStatusBarSizer.getSystemUiIdNameCompat(view));
            apply(view, supported ? ModuleConfig.load(view.getContext()) : null);
        }

        void apply(TextView view, ModuleConfig config) {
            Typeface current = view.getTypeface();
            boolean fakeBold = view.getPaint().isFakeBoldText();
            boolean nativeFontChanged = overridden && current != appliedTypeface;
            if (!overridden || nativeFontChanged) {
                originalTypeface = current;
            }
            // A native setTypeface may leave our synthetic-bold flag untouched. Preserve
            // the original flag unless the system actually supplied a different value.
            if (!overridden || fakeBold != (appliedWeight >= 600)) {
                originalFakeBold = fakeBold;
            }
            if (config == null || !config.enabled || !config.clockBoldEnabled) {
                if (overridden) {
                    setFont(view, originalTypeface, originalFakeBold);
                    overridden = false;
                    appliedWeight = -1;
                    appliedTypeface = null;
                }
                return;
            }
            int weight = Math.max(100, Math.min(900, config.clockFontWeight));
            if (!overridden || nativeFontChanged || appliedWeight != weight) {
                boolean italic = originalTypeface != null && originalTypeface.isItalic();
                try {
                    appliedTypeface = Typeface.create(originalTypeface, weight, italic);
                } catch (Throwable ignored) {
                    appliedTypeface = Typeface.create(originalTypeface,
                            (weight >= 600 ? Typeface.BOLD : Typeface.NORMAL)
                                    | (italic ? Typeface.ITALIC : Typeface.NORMAL));
                }
                appliedWeight = weight;
            }
            overridden = true;
            setFont(view, appliedTypeface, weight >= 600);
        }

        private static void setFont(TextView view, Typeface typeface, boolean fakeBold) {
            if (view.getTypeface() == typeface && view.getPaint().isFakeBoldText() == fakeBold) {
                return;
            }
            if (view.getTypeface() != typeface) {
                view.setTypeface(typeface);
            }
            view.getPaint().setFakeBoldText(fakeBold);
            view.requestLayout();
            view.invalidate();
        }

        @Override public void onViewAttachedToWindow(View view) {
            removeObserver();
            ViewTreeObserver observer = view.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
            observerRef = new WeakReference<>(observer);
            refresh();
        }

        @Override public void onViewDetachedFromWindow(View view) {
            removeObserver();
        }

        private void removeObserver() {
            ViewTreeObserver observer = observerRef.get();
            if (observer != null && observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
            }
            observerRef.clear();
        }

        @Override public boolean onPreDraw() {
            // Carrier controllers and battery inflation can reset typefaces after the
            // constructor/attach callback, including during a theme change.
            refresh();
            return true;
        }
    }
}
