package com.example.flymestatusbarsizer.feature.clock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.feature.statusbar.StatusBarPageLayout;
import com.example.flymestatusbarsizer.feature.statusbar.StatusBarPageLayout.Item;
import com.example.flymestatusbarsizer.feature.statusbar.StatusBarPageView;
import com.example.flymestatusbarsizer.util.ReflectUtils;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class StatusBarPageHooks {
    private static State current;
    private static boolean selectedExtra;
    private static Method nativeTouch;

    private StatusBarPageHooks() { }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> phone = Class.forName("com.android.systemui.statusbar.phone.PhoneStatusBarView", false, loader);
            Class<?> fragment = Class.forName("com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment", false, loader);
            nativeTouch = phone.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            nativeTouch.setAccessible(true);
            module.intercept(fragment.getDeclaredMethod("onViewCreated", View.class, Bundle.class), chain -> {
                Object result = chain.proceed();
                ViewGroup root = (ViewGroup) chain.getArg(0);
                if (((Number) ReflectUtils.invokeNoArg(root.getContext(), "getDisplayId")).intValue() == 0) {
                    if (current != null) current.close();
                    current = new State(root, chain.getThisObject());
                    current.configure();
                }
                return result;
            });
            module.intercept(fragment.getDeclaredMethod("onDestroyView"), chain -> {
                if (current != null && current.fragment == chain.getThisObject()) {
                    current.close();
                    current = null;
                }
                return chain.proceed();
            });
            module.intercept(phone.getDeclaredMethod("onInterceptTouchEvent", MotionEvent.class), chain -> {
                Object result = chain.proceed();
                State state = current;
                if (state == null || state.root != chain.getThisObject() || !state.enabled || state.injecting) return result;
                return state.intercept((MotionEvent) chain.getArg(0)) || (Boolean) result;
            });
            module.deoptimize(nativeTouch);
            module.intercept(nativeTouch, chain -> {
                State state = current;
                if (state == null || state.root != chain.getThisObject() || !state.enabled || state.injecting) return chain.proceed();
                MotionEvent event = (MotionEvent) chain.getArg(0);
                // ViewGroup skips onInterceptTouchEvent after a blank-area DOWN.
                if (state.route == 1 && event.getActionMasked() == MotionEvent.ACTION_MOVE) state.intercept(event);
                if (state.route == 2) {
                    state.drag(event);
                    return true;
                }
                if (state.route == 3 && state.fromExtra && !state.nativeDown) {
                    state.nativeDown = true;
                    chain.proceed(new Object[]{state.down});
                }
                if ((state.fromExtra || selectedExtra && state.available) && state.route != 3) {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) state.finishTouch();
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) state.nativeDown = true;
                Object result = chain.proceed();
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) state.finishTouch();
                return result;
            });
            module.intercept(phone.getDeclaredMethod("scrollBack", MotionEvent.class), chain ->
                    current != null && current.root == chain.getThisObject() && current.fromExtra
                            ? null : chain.proceed());
            Class<?> live = Class.forName("com.android.systemui.statusbar.phone.PhoneStatusBarViewController$LiveNotificationTouchHandler", false, loader);
            module.intercept(live.getDeclaredMethod("onTouchEvent", MotionEvent.class), chain ->
                    current != null && current.fromExtra ? false : chain.proceed());
        } catch (ReflectiveOperationException error) {
            FlymeStatusBarSizer.logClockWarning("Failed to hook status bar pages", error);
        }
    }

    public static void refresh() {
        FlymeStatusBarSizer.postToMainHandler(() -> { if (current != null) current.configure(); });
    }

    public static void onWindowStateChanged() {
        if (current != null) current.sync();
    }

    private static final class State implements ViewTreeObserver.OnPreDrawListener {
        final ViewGroup root;
        final Object fragment;
        final Object keyguard;
        final Object shade;
        final Object center;
        final Object windowState;
        final Handler handler;
        final PowerManager power;
        final View start;
        final View end;
        final TextView clock;
        final Rect leftBounds = new Rect();
        final Rect rightBounds = new Rect();
        final int slop;
        final int minFling;
        FrameLayout original;
        StatusBarPageView extra;
        ClockDetailSystemStatusProvider status;
        ClockDetailMediaProvider media;
        ValueAnimator animator;
        boolean enabled;
        boolean available;
        boolean refreshing;
        boolean mediaListening;
        boolean injecting;
        boolean fromExtra;
        boolean nativeDown;
        int route;
        MotionEvent down;
        VelocityTracker velocity;
        float distance;
        float offset;
        long nextBattery;
        long nextMemory;
        int refreshGeneration;
        int visibleItems;
        final Runnable tick = this::updateData;
        final BroadcastReceiver screen = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { sync(); }
        };

        State(ViewGroup root, Object fragment) {
            this.root = root;
            this.fragment = fragment;
            keyguard = ReflectUtils.getField(fragment, "mKeyguardStateController");
            shade = ReflectUtils.getField(fragment, "mShadeExpansionStateManager");
            center = ReflectUtils.getField(fragment, "mCenterExpansionStateManager");
            windowState = ReflectUtils.getField(fragment, "mStatusBarWindowStateController");
            start = find("status_bar_start_side_container");
            end = find("status_bar_end_side_container");
            clock = (TextView) find("clock");
            handler = new Handler(root.getContext().getMainLooper());
            power = root.getContext().getSystemService(PowerManager.class);
            ViewConfiguration config = ViewConfiguration.get(root.getContext());
            slop = config.getScaledTouchSlop();
            minFling = config.getScaledMinimumFlingVelocity();
            root.getViewTreeObserver().addOnPreDrawListener(this);
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            root.getContext().registerReceiver(screen, filter);
        }

        View find(String name) {
            return root.findViewById(root.getResources().getIdentifier(name, "id", "com.android.systemui"));
        }

        void configure() {
            ModuleConfig config = ModuleConfig.load(root.getContext());
            enabled = config.enabled && config.statusBarPagesEnabled;
            if (!enabled) {
                selectedExtra = false;
                cancelAnimation();
                finishTouch();
                stopData();
                unwrap();
                return;
            }
            if (original == null) {
                original = new FrameLayout(root.getContext());
                while (root.getChildCount() > 0) {
                    View child = root.getChildAt(0);
                    root.removeViewAt(0);
                    original.addView(child);
                }
                root.addView(original, new FrameLayout.LayoutParams(-1, -1));
                extra = new StatusBarPageView(root.getContext(), item -> {
                    if (item == Item.PREVIOUS) media.skipToPrevious();
                    else if (item == Item.NEXT) media.skipToNext();
                    else media.togglePlayPause();
                });
                root.addView(extra, new FrameLayout.LayoutParams(-1, -1));
                status = new ClockDetailSystemStatusProvider(root.getContext());
                media = new ClockDetailMediaProvider(root.getContext(), 0);
            }
            stopData();
            extra.setItems(config.statusBarPageLayout);
            nextBattery = nextMemory = 0;
            sync();
        }

        @Override public boolean onPreDraw() { sync(); return true; }

        void sync() {
            if (!enabled) return;
            available = root.isShown() && power.isInteractive()
                    && Boolean.TRUE.equals(ReflectUtils.invokeNoArg(windowState, "windowIsShowing"))
                    && !Boolean.TRUE.equals(ReflectUtils.invokeNoArg(keyguard, "isShowing"))
                    && Boolean.TRUE.equals(ReflectUtils.invokeNoArg(shade, "isClosed"))
                    && Boolean.TRUE.equals(ReflectUtils.invokeNoArg(center, "isClosed"));
            if (!available && (route == 2 || animator != null)) {
                cancelAnimation();
                finishTouch();
            }
            bounds(start, leftBounds);
            bounds(end, rightBounds);
            extra.setSideBounds(leftBounds, rightBounds);
            extra.setStyle(clock.getCurrentTextColor(), clock.getTextSize(), clock.getTypeface());
            if (animator == null && route != 2) {
                original.setTranslationX(0);
                extra.setTranslationX(0);
                original.setVisibility(selectedExtra && available ? View.INVISIBLE : View.VISIBLE);
                extra.setVisibility(selectedExtra && available ? View.VISIBLE : View.INVISIBLE);
            }
            boolean show = available && extra.getVisibility() == View.VISIBLE;
            if (show) {
                int visible = 0;
                for (Item item : Item.values()) if (extra.hasVisible(item)) visible |= 1 << item.ordinal();
                if (!refreshing || visible != visibleItems) {
                    visibleItems = visible;
                    refreshing = true;
                    nextBattery = nextMemory = 0;
                    handler.removeCallbacks(tick);
                    handler.post(tick);
                }
            } else if (refreshing) stopData();
        }

        void bounds(View anchor, Rect rect) {
            int x = 0, y = 0;
            View node = anchor;
            while (node != root) {
                x += node.getLeft();
                y += node.getTop();
                node = (View) node.getParent();
            }
            rect.set(x, y, x + anchor.getWidth(), y + anchor.getHeight());
        }

        boolean intercept(MotionEvent event) throws ReflectiveOperationException {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                cancelAnimation();
                finishTouch();
                sync();
                if (!available) return false;
                route = 1;
                fromExtra = selectedExtra;
                down = MotionEvent.obtain(event);
                velocity = VelocityTracker.obtain();
                velocity.addMovement(event);
                if (fromExtra) ReflectUtils.setBooleanField(root, "mIsTracking", true);
            } else if (route != 0) {
                velocity.addMovement(event);
                if (action == MotionEvent.ACTION_MOVE && route == 1) {
                    float dx = event.getX() - down.getX();
                    float dy = event.getY() - down.getY();
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        if (nativeDown) {
                            MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            injecting = true;
                            try { nativeTouch.invoke(root, cancel); }
                            finally { injecting = false; cancel.recycle(); }
                        }
                        ReflectUtils.setBooleanField(root, "mScrollTriggered", false);
                        ReflectUtils.setBooleanField(root, "mIsTracking", true);
                        route = 2;
                        original.setVisibility(View.VISIBLE);
                        extra.setVisibility(View.VISIBLE);
                    } else if (Math.abs(dy) > slop) route = 3;
                }
                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    // End paging with the same cancellation semantics as the native views.
                    if (route == 2) { settle(false); finishTouch(); return true; }
                    route = 3;
                }
                if (route == 2 || (route == 3 && fromExtra)) return true;
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    // A child (clock or media button) may own this tap and never call onTouchEvent.
                    if (!nativeDown) finishTouch();
                }
            }
            return false;
        }

        void drag(MotionEvent event) {
            velocity.addMovement(event);
            distance = event.getX() - down.getX();
            float width = root.getWidth();
            offset = fromExtra ? Math.max(0, Math.min(width, distance))
                    : Math.min(0, Math.max(-width, distance));
            translate(offset);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                velocity.computeCurrentVelocity(1000);
                float speed = velocity.getXVelocity();
                boolean forward = fromExtra ? distance > slop : distance < -slop;
                boolean commit = action == MotionEvent.ACTION_UP && forward
                        && (Math.abs(distance) >= Math.min(dp(48), width * 0.18f)
                                || Math.abs(speed) > minFling && speed * distance > 0);
                settle(commit);
                finishTouch();
            }
        }

        void translate(float value) {
            original.setTranslationX(fromExtra ? value - root.getWidth() : value);
            extra.setTranslationX(fromExtra ? value : value + root.getWidth());
        }

        void settle(boolean commit) {
            boolean origin = fromExtra;
            selectedExtra = commit ? !origin : origin;
            float target = commit ? (origin ? root.getWidth() : -root.getWidth()) : 0;
            animator = ValueAnimator.ofFloat(offset, target);
            animator.setDuration(180);
            animator.addUpdateListener(a -> {
                float value = (Float) a.getAnimatedValue();
                original.setTranslationX(origin ? value - root.getWidth() : value);
                extra.setTranslationX(origin ? value : value + root.getWidth());
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { animator = null; sync(); }
            });
            animator.start();
        }

        void cancelAnimation() {
            if (animator != null) animator.cancel();
        }

        void finishTouch() {
            route = 0;
            fromExtra = false;
            nativeDown = false;
            offset = 0;
            if (down != null) {
                MotionEvent cancel = MotionEvent.obtain(down);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                ReflectUtils.invokeMethod(ReflectUtils.getField(root, "mTouchEventHandler"),
                        "onInterceptTouchEvent", new Class<?>[]{MotionEvent.class}, cancel);
                cancel.recycle();
                down.recycle();
                down = null;
            }
            if (velocity != null) { velocity.recycle(); velocity = null; }
            ReflectUtils.setBooleanField(root, "mIsTracking", false);
        }

        void updateData() {
            sync();
            if (!refreshing) return;
            if (extra.hasVisible(Item.DATE)) extra.setText(Item.DATE,
                    new SimpleDateFormat("MM/dd E", Locale.getDefault()).format(new Date()));
            boolean battery = extra.hasVisible(Item.TEMPERATURE) || extra.hasVisible(Item.POWER);
            boolean memory = extra.hasVisible(Item.MEMORY);
            long now = SystemClock.uptimeMillis();
            boolean readBattery = battery && now >= nextBattery;
            boolean readMemory = memory && now >= nextMemory;
            if (readBattery || readMemory) {
                int generation = refreshGeneration;
                status.requestStatusBarValues(handler, readBattery, readMemory, (temperature, watts, ram) -> {
                    if (!refreshing || generation != refreshGeneration) return;
                    if (temperature != null) extra.setText(Item.TEMPERATURE, temperature);
                    if (watts != null) extra.setText(Item.POWER, watts);
                    if (ram != null) extra.setText(Item.MEMORY, ram);
                });
                if (readBattery) nextBattery = now + 2000;
                if (readMemory) nextMemory = now + 10000;
            }
            if (extra.hasMedia() && !mediaListening) {
                mediaListening = true;
                media.startListening(handler, media.peekStartupSnapshot(), value -> {
                    if (!refreshing) return;
                    extra.setMedia(value.title.toString(), value.playbackState == PlaybackState.STATE_PLAYING,
                            value.availableActions, value.active);
                });
            }
            handler.postDelayed(tick, 2000);
        }

        void stopData() {
            refreshing = false;
            refreshGeneration++;
            handler.removeCallbacks(tick);
            if (mediaListening) { media.stopListening(); mediaListening = false; }
        }

        int dp(int value) { return Math.round(root.getResources().getDisplayMetrics().density * value); }

        void unwrap() {
            if (original == null) return;
            root.removeView(extra);
            root.removeView(original);
            while (original.getChildCount() > 0) {
                View child = original.getChildAt(0);
                original.removeViewAt(0);
                root.addView(child);
            }
            original = null;
            extra = null;
        }

        void close() {
            enabled = false;
            cancelAnimation();
            finishTouch();
            stopData();
            unwrap();
            root.getViewTreeObserver().removeOnPreDrawListener(this);
            root.getContext().unregisterReceiver(screen);
        }
    }
}
