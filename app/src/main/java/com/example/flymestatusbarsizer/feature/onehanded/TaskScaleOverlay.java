package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.Log;
import android.hardware.display.DisplayManager;
import android.view.AttachedSurfaceControl;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.ViewTreeObserver;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Two non-focusable windows cover only the top/left margins once the animation settles. */
final class TaskScaleOverlay implements TaskScaleController.Overlay {
    private final Context context;
    private final WindowManager manager;
    private final Runnable onExit;
    private final RecentTaskCardsLoader cardsLoader;
    private final Consumer<RecentTaskCard> onSelect;
    private final Mask[] masks = new Mask[3];
    private boolean animating;
    private TaskScaleTarget cardsTarget;
    private final RecentTaskCardSlots slots = new RecentTaskCardSlots();
    private boolean swapping;
    private long coverGeneration;
    private Mask coverOwner;
    private ViewTreeObserver.OnPreDrawListener coverDraw;
    private TaskSwapAnimationView swapAnimation;
    private TaskScaleLayout layout;

    TaskScaleOverlay(Context source, Runnable onExit, RecentTaskCardsLoader cardsLoader,
            Consumer<RecentTaskCard> onSelect) {
        Display display = source.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        context = source.createDisplayContext(display).createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        manager = context.getSystemService(WindowManager.class);
        this.onExit = onExit;
        this.cardsLoader = cardsLoader;
        this.onSelect = onSelect;
    }

    @Override public void show(TaskScaleTarget target, TaskScaleLayout layout, boolean animating) {
        this.layout = layout;
        this.animating = animating || swapping;
        if (animating) cancelCards();
        Rect content = layout.content;
        Rect top = new Rect(target.bounds);
        Rect left = new Rect();
        if (!animating) {
            top.bottom = content.top;
            left.set(target.bounds.left, content.top, content.left, content.bottom);
        }
        // During the short enter/exit animation input is blocked, but the app remains visible.
        intersect(top, layout.available);
        intersect(left, layout.available);
        update(0, top, animating ? content : null);
        update(1, left, null);
        if (!animating) {
            showCards(target, false);
            positionCards(target);
        }
    }

    @Override public void beginSwap(TaskScaleTarget current, RecentTaskCard selected, Runnable covered) {
        List<RecentTaskCard> previousCards = new ArrayList<>(slots.cards());
        int selectedIndex = -1;
        for (int i = 0; i < previousCards.size(); i++) {
            if (selected.sameTask(previousCards.get(i))) selectedIndex = i;
        }
        if (selectedIndex < 0 || masks[1] == null) throw new IllegalStateException("Selected card unavailable");
        WindowManager.LayoutParams margin = (WindowManager.LayoutParams) masks[1].getLayoutParams();
        int gap = Math.max(1, Math.round(RecentTaskCardLayout.GAP_DP
                * context.getResources().getDisplayMetrics().density));
        Rect[] positions = layout.cards(new Rect(0, 0, margin.width, margin.height), previousCards.size(),
                current.bounds.width() / (float) current.bounds.height(), gap);
        if (positions.length != previousCards.size()) throw new IllegalStateException("Invalid card layout");
        RecentTaskCard oldMain = slots.begin(current, selected);
        swapping = true;
        animating = true;
        cancelCards();
        // Cover the complete usable display behind IME, including space exposed as IME hides.
        Rect frame = new Rect(layout.usable);
        if (frame.isEmpty()) throw new IllegalStateException("No space for swap cover");
        // This window is above both margins, so their opaque backgrounds cannot clip the motion.
        update(2, frame, null);
        Mask cover = masks[2];
        cover.removeAllViews();
        Rect content = new Rect(layout.content);
        content.offset(-frame.left, -frame.top);
        for (Rect position : positions) position.offset(margin.x - frame.left, margin.y - frame.top);
        TaskSwapAnimationView animation = new TaskSwapAnimationView(context, previousCards, positions,
                selectedIndex, oldMain, content);
        swapAnimation = animation;
        cover.addView(animation, new FrameLayout.LayoutParams(frame.width(), frame.height()));
        awaitCoverCommit(cover, frame.width(), frame.height(), () -> {
            // Prepare the final left slot under the cover while the previews are moving.
            renderCards(current);
            animation.start();
            covered.run();
        });
    }

    @Override public boolean swapAnimationFinished() {
        return swapAnimation != null && swapAnimation.isFinished();
    }

    @Override public void moveSwap(TaskScaleTarget current, TaskScaleLayout layout) {
        if (!swapping || swapAnimation == null) return;
        show(current, layout, false);
        if (masks[1] == null || masks[2] == null) return;
        WindowManager.LayoutParams margin = (WindowManager.LayoutParams) masks[1].getLayoutParams();
        WindowManager.LayoutParams cover = (WindowManager.LayoutParams) masks[2].getLayoutParams();
        Rect[] positions = cardPositions(current, swapAnimation.cardCount());
        for (Rect position : positions) position.offset(margin.x - cover.x, margin.y - cover.y);
        Rect main = new Rect(layout.content);
        main.offset(-cover.x, -cover.y);
        swapAnimation.setLayout(positions, main);
    }

    private void awaitCoverCommit(Mask cover, int width, int height, Runnable covered) {
        cancelCoverCallback();
        long epoch = coverGeneration;
        coverOwner = cover;
        coverDraw = () -> {
            if (cover.getWidth() != width || cover.getHeight() != height) return true;
            cover.getViewTreeObserver().removeOnPreDrawListener(coverDraw);
            coverDraw = null;
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            boolean queued = false;
            try {
                AttachedSurfaceControl root = cover.getRootSurfaceControl();
                transaction.addTransactionCommittedListener(command -> cover.post(command), () -> {
                    if (swapping && coverGeneration == epoch) covered.run();
                });
                queued = root != null && root.applyTransactionOnDraw(transaction);
                if (!queued) Log.w(OneHandedTaskHooks.TAG, "Swap cover could not be committed");
            } catch (RuntimeException e) {
                Log.w(OneHandedTaskHooks.TAG, "Cannot commit swap cover", e);
            } finally { if (!queued) transaction.close(); }
            return true;
        };
        cover.getViewTreeObserver().addOnPreDrawListener(coverDraw);
        cover.invalidate();
    }

    private void cancelCoverCallback() {
        coverGeneration++;
        if (coverOwner != null && coverDraw != null && coverOwner.getViewTreeObserver().isAlive()) {
            coverOwner.getViewTreeObserver().removeOnPreDrawListener(coverDraw);
        }
        coverDraw = null;
        coverOwner = null;
    }

    @Override public void endSwap(TaskScaleTarget current, TaskScaleLayout layout) {
        cancelCoverCallback();
        swapping = false;
        slots.end(current);
        show(current, layout, false);
        cancelSwapAnimation();
        remove(2);
    }

    private void renderCards(TaskScaleTarget target) {
        Mask margin = masks[1];
        if (margin == null) return;
        margin.removeAllViews();
        Rect[] positions = cardPositions(target, slots.cards().size());
        for (int i = 0; i < positions.length; i++) {
            RecentTaskCard card = slots.cards().get(i);
            RecentTaskCardView view = new RecentTaskCardView(context, card, () -> {
                if (!swapping) onSelect.accept(card);
            });
            Rect rect = positions[i];
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(rect.width(), rect.height());
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.leftMargin = rect.left;
            params.topMargin = rect.top;
            margin.addView(view, params);
        }
    }

    private Rect[] cardPositions(TaskScaleTarget target, int count) {
        if (masks[1] == null) return new Rect[0];
        WindowManager.LayoutParams frame = (WindowManager.LayoutParams) masks[1].getLayoutParams();
        int gap = Math.max(1, Math.round(RecentTaskCardLayout.GAP_DP
                * context.getResources().getDisplayMetrics().density));
        return layout.cards(new Rect(0, 0, frame.width, frame.height), count,
                target.bounds.width() / (float) target.bounds.height(), gap);
    }

    private void positionCards(TaskScaleTarget target) {
        Mask margin = masks[1];
        if (margin == null) return;
        Rect[] positions = cardPositions(target, slots.cards().size());
        if (margin.getChildCount() != positions.length) { renderCards(target); return; }
        for (int i = 0; i < positions.length; i++) {
            Rect rect = positions[i];
            android.view.View child = margin.getChildAt(i);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
            if (params.width == rect.width() && params.height == rect.height()
                    && params.leftMargin == rect.left && params.topMargin == rect.top) continue;
            params.width = rect.width();
            params.height = rect.height();
            params.leftMargin = rect.left;
            params.topMargin = rect.top;
            child.setLayoutParams(params);
        }
    }

    private void showCards(TaskScaleTarget target, boolean force) {
        Mask margin = masks[1];
        if (cardsLoader == null || margin == null || animating) return;
        if (!force && target.sameTask(cardsTarget)) return;
        cardsTarget = target;
        renderCards(target);
        Rect[] positions = cardPositions(target, 3);
        if (positions.length == 0) return;
        cardsLoader.request(target, positions[0].width(), positions[0].height(), cards -> {
            if (cardsTarget != target || masks[1] != margin || animating) return;
            slots.update(cards);
            renderCards(target);
        }, slots::rememberMain);
    }

    @Override public void tasksChanged() {
        if (!swapping && cardsTarget != null) showCards(cardsTarget, true);
    }

    private void cancelCards() {
        if (cardsTarget == null) return;
        cardsTarget = null;
        if (cardsLoader != null) cardsLoader.cancel();
    }

    static void intersect(Rect rect, Rect usable) {
        if (!rect.intersect(usable)) rect.setEmpty();
    }

    private void update(int index, Rect bounds, Rect hole) {
        if (bounds.isEmpty()) { remove(index); return; }
        Mask view = masks[index];
        if (view == null) {
            view = new Mask(context);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    bounds.width(), bounds.height(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.setFitInsetsTypes(0);
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            params.setTitle("FlymeBarSizer TaskScale margin " + index);
            params.packageName = context.getPackageName();
            params.x = bounds.left;
            params.y = bounds.top;
            view.setMask(bounds, hole);
            manager.addView(view, params);
            masks[index] = view;
        } else {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
            if (params.x != bounds.left || params.y != bounds.top
                    || params.width != bounds.width() || params.height != bounds.height()) {
                params.x = bounds.left;
                params.y = bounds.top;
                params.width = bounds.width();
                params.height = bounds.height();
                manager.updateViewLayout(view, params);
            }
            view.setMask(bounds, hole);
        }
    }

    private void cancelSwapAnimation() {
        if (swapAnimation != null) swapAnimation.cancel();
        swapAnimation = null;
    }

    @Override public void pause() {
        cancelCards();
        cancelCoverCallback();
        cancelSwapAnimation();
        remove(2);
        remove(0);
        remove(1);
    }

    @Override public void hide() {
        pause();
        swapping = false;
        slots.clear();
    }

    private void remove(int index) {
        Mask view = masks[index];
        if (view == null) return;
        try { manager.removeViewImmediate(view); }
        catch (RuntimeException e) { android.util.Log.w(OneHandedTaskHooks.TAG, "Remove margin window", e); }
        masks[index] = null;
    }

    private final class Mask extends FrameLayout {
        final Paint paint = new Paint();
        final Rect hole = new Rect();
        final int slop;
        float downX, downY;
        boolean tap;

        Mask(Context context) {
            super(context);
            setWillNotDraw(false);
            paint.setColor(Color.rgb(20, 22, 26));
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
            setContentDescription("退出应用单手缩放");
            setOnClickListener(v -> { if (!animating) onExit.run(); });
        }

        void setMask(Rect frame, Rect opening) {
            hole.setEmpty();
            if (opening != null) { hole.set(opening); hole.offset(-frame.left, -frame.top); }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            int save = canvas.save();
            if (!hole.isEmpty()) canvas.clipOutRect(hole);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            canvas.restoreToCount(save);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tap = !animating;
                    downX = event.getX(); downY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) tap = false;
                    break;
                case MotionEvent.ACTION_UP:
                    if (tap && !animating) performClick();
                    tap = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_POINTER_DOWN:
                    tap = false;
                    break;
            }
            return true;
        }
    }
}
