package com.example.flymestatusbarsizer.feature.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

final class LauncherRecentsActivityOverlayView extends FrameLayout {
    private static final float STACK_DEPTH_CURVE_POWER = 0.82f;
    private static final float STACK_FRONT_VISIBLE_RATIO = 0.50f;
    private static final float STACK_FRONT_REVEAL_CURVE_POWER = 0.72f;
    private static final float STACK_BACK_SPREAD_RATIO = 0.14f;
    private static final float STACK_MIN_OVERLAP_RATIO = 0.20f;
    private static final float STACK_SCALE_STEP = 0.065f;
    private static final float STACK_MIN_SCALE = 0.80f;
    private static final float STACK_LEFT_INSET_RATIO = 0.05f;
    private static final float MAX_STACK_LAYERS = 3.0f;

    interface Callbacks {
        void onHomeRequested();

        void onTaskActivated(int taskId);

        void onTaskLaunchRequested(int taskId);

        void onTaskDismissRequested(int taskId);
    }

    static final class CardRecord {
        final int taskId;
        final int accentColor;
        final String badgeText;
        final String fallbackTitle;
        final String subtitle;
        final int preferredWidthPx;
        final int preferredHeightPx;
        int generation;
        Drawable icon;
        Bitmap thumbnail;
        String contentDescription;
        String title;

        CardRecord(
                int taskId,
                int accentColor,
                String title,
                String fallbackTitle,
                String subtitle,
                int preferredWidthPx,
                int preferredHeightPx,
                String badgeText,
                String contentDescription,
                Drawable icon,
                Bitmap thumbnail,
                int generation) {
            this.taskId = taskId;
            this.accentColor = accentColor;
            this.title = title;
            this.fallbackTitle = fallbackTitle;
            this.subtitle = subtitle;
            this.preferredWidthPx = preferredWidthPx;
            this.preferredHeightPx = preferredHeightPx;
            this.badgeText = badgeText;
            this.contentDescription = contentDescription;
            this.icon = icon;
            this.thumbnail = thumbnail;
            this.generation = generation;
        }

        String effectiveTitle() {
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
            return fallbackTitle;
        }
    }

    private final Callbacks callbacks;
    private final FrameLayout stackLayer;
    private final TextView titleView;
    private final TextView detailView;
    private final TextView emptyView;
    private final HashMap<Integer, TaskCardView> cardViews = new HashMap<>();
    private final ArrayList<CardRecord> cards = new ArrayList<>();
    private final int touchSlop;
    private final int minimumFlingVelocity;
    private final int maximumFlingVelocity;
    private float downX;
    private float downY;
    private float dragStartVisualTaskIndex;
    private float visualTaskIndex;
    private float inertialVelocityTaskIndexPerSecond;
    private long lastInertialFrameTimeMs;
    private boolean swiping;
    private boolean touchMoved;
    private int activeTaskId = -1;
    private VelocityTracker velocityTracker;
    private final Runnable inertialScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (cards.isEmpty()) {
                stopInertialScroll();
                return;
            }
            long nowMs = AnimationUtils.currentAnimationTimeMillis();
            float deltaSeconds = Math.max(0.016f, (nowMs - lastInertialFrameTimeMs) / 1000f);
            lastInertialFrameTimeMs = nowMs;
            float previousIndex = visualTaskIndex;
            updateVisualTaskIndex(visualTaskIndex + (inertialVelocityTaskIndexPerSecond * deltaSeconds));
            boolean hitBoundary =
                    (visualTaskIndex <= 0f && inertialVelocityTaskIndexPerSecond < 0f)
                            || (visualTaskIndex >= Math.max(0, cards.size() - 1)
                            && inertialVelocityTaskIndexPerSecond > 0f);
            float damping = (float) Math.pow(0.92f, (deltaSeconds * 1000f) / 16f);
            inertialVelocityTaskIndexPerSecond *= damping;
            if (hitBoundary
                    || Math.abs(visualTaskIndex - previousIndex) < 0.0001f
                    || Math.abs(inertialVelocityTaskIndexPerSecond) < 0.02f) {
                stopInertialScroll();
                return;
            }
            postOnAnimation(this);
        }
    };

    LauncherRecentsActivityOverlayView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        this.touchSlop = viewConfiguration.getScaledTouchSlop();
        this.minimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        this.maximumFlingVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(true);
        setFocusable(true);
        setBackground(null);
        setOnClickListener(v -> this.callbacks.onHomeRequested());

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(24), dp(28), dp(24), 0);

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setText("最近任务");
        header.addView(titleView, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));

        detailView = new TextView(context);
        detailView.setTextColor(0xCCFFFFFF);
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        detailView.setPadding(0, dp(6), 0, 0);
        detailView.setText("左右滑动切换，轻点当前卡片打开");
        header.addView(detailView, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));

        addView(header, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        stackLayer = new FrameLayout(context);
        stackLayer.setClipChildren(false);
        stackLayer.setClipToPadding(false);
        stackLayer.setPadding(dp(12), dp(92), dp(12), dp(36));
        addView(stackLayer, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextColor(0xE6FFFFFF);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setText("没有最近任务，点空白返回桌面");
        emptyView.setVisibility(GONE);
        addView(emptyView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        trackVelocity(event);
        boolean handled = super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            recycleVelocityTracker();
        }
        return handled;
    }

    void setCards(List<CardRecord> newCards, int requestedActiveTaskId) {
        stopInertialScroll();
        cards.clear();
        if (newCards != null) {
            cards.addAll(newCards);
        }
        HashSet<Integer> validTaskIds = new HashSet<>();
        for (CardRecord card : cards) {
            validTaskIds.add(card.taskId);
            TaskCardView cardView = cardViews.get(card.taskId);
            if (cardView == null) {
                cardView = new TaskCardView(getContext());
                cardViews.put(card.taskId, cardView);
                stackLayer.addView(cardView, new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT));
            }
        }
        ArrayList<Integer> removedTaskIds = new ArrayList<>();
        for (Integer taskId : cardViews.keySet()) {
            if (!validTaskIds.contains(taskId)) {
                removedTaskIds.add(taskId);
            }
        }
        for (Integer taskId : removedTaskIds) {
            TaskCardView removed = cardViews.remove(taskId);
            if (removed != null) {
                stackLayer.removeView(removed);
            }
        }
        if (!containsTaskId(requestedActiveTaskId) && !cards.isEmpty()) {
            activeTaskId = cards.get(0).taskId;
        } else {
            activeTaskId = requestedActiveTaskId;
        }
        if (cards.isEmpty()) {
            activeTaskId = -1;
        }
        visualTaskIndex = Math.max(0, indexOfTask(activeTaskId));
        rebindViews();
        updateHeader();
        applyStackLayout(false);
    }

    void updateTaskIcon(
            int taskId,
            int generation,
            Drawable icon,
            String title,
            String contentDescription) {
        CardRecord card = findCard(taskId);
        if (card == null || card.generation != generation) {
            return;
        }
        if (icon != null) {
            card.icon = icon;
        }
        if (!TextUtils.isEmpty(title)) {
            card.title = title;
        }
        if (!TextUtils.isEmpty(contentDescription)) {
            card.contentDescription = contentDescription;
        }
        TaskCardView cardView = cardViews.get(taskId);
        if (cardView != null) {
            cardView.bind(card, taskId == activeTaskId);
        }
        if (taskId == activeTaskId) {
            updateHeader();
        }
    }

    void updateTaskThumbnail(int taskId, int generation, Bitmap thumbnail) {
        CardRecord card = findCard(taskId);
        if (card == null || card.generation != generation || thumbnail == null) {
            return;
        }
        card.thumbnail = thumbnail;
        TaskCardView cardView = cardViews.get(taskId);
        if (cardView != null) {
            cardView.bind(card, taskId == activeTaskId);
        }
    }

    void setActiveTask(int taskId, boolean animate) {
        if (!containsTaskId(taskId)) {
            return;
        }
        stopInertialScroll();
        activeTaskId = taskId;
        visualTaskIndex = indexOfTask(taskId);
        updateHeader();
        rebindViews();
        applyStackLayout(animate);
    }

    void removeTask(int taskId) {
        stopInertialScroll();
        int removedIndex = indexOfTask(taskId);
        if (removedIndex < 0) {
            return;
        }
        cards.remove(removedIndex);
        TaskCardView removed = cardViews.remove(taskId);
        if (removed != null) {
            stackLayer.removeView(removed);
        }
        if (taskId == activeTaskId) {
            if (cards.isEmpty()) {
                activeTaskId = -1;
            } else if (removedIndex < cards.size()) {
                activeTaskId = cards.get(removedIndex).taskId;
            } else {
                activeTaskId = cards.get(cards.size() - 1).taskId;
            }
        }
        rebindViews();
        updateHeader();
        applyStackLayout(true);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragStartVisualTaskIndex = visualTaskIndex;
                stopInertialScroll();
                swiping = false;
                touchMoved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!swiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    swiping = true;
                    touchMoved = true;
                    return true;
                }
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    touchMoved = true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                swiping = false;
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragStartVisualTaskIndex = visualTaskIndex;
                stopInertialScroll();
                swiping = false;
                touchMoved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!swiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    swiping = true;
                }
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    touchMoved = true;
                }
                if (swiping) {
                    updateVisualTaskIndex(
                            dragStartVisualTaskIndex + (dx / resolveSwipeStepPx()));
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                stopInertialScroll();
                swiping = false;
                touchMoved = false;
                break;
            case MotionEvent.ACTION_UP:
                if (touchMoved) {
                    startInertialScrollIfNeeded();
                    swiping = false;
                    touchMoved = false;
                    return true;
                }
                swiping = false;
                touchMoved = false;
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyStackLayout(false);
    }

    private void moveActiveBy(int delta) {
        int currentIndex = indexOfTask(activeTaskId);
        if (currentIndex < 0) {
            return;
        }
        int targetIndex = Math.max(0, Math.min(cards.size() - 1, currentIndex + delta));
        if (targetIndex == currentIndex) {
            return;
        }
        CardRecord target = cards.get(targetIndex);
        activeTaskId = target.taskId;
        callbacks.onTaskActivated(target.taskId);
        updateHeader();
        rebindViews();
        applyStackLayout(true);
    }

    private void updateVisualTaskIndex(float taskIndex) {
        if (cards.isEmpty()) {
            visualTaskIndex = 0f;
            return;
        }
        float clampedIndex = clamp(taskIndex, 0f, Math.max(0, cards.size() - 1));
        visualTaskIndex = clampedIndex;
        updateHeader();
        applyStackLayout(false);
    }

    private void rebindViews() {
        for (CardRecord card : cards) {
            TaskCardView cardView = cardViews.get(card.taskId);
            if (cardView != null) {
                cardView.bind(card, card.taskId == activeTaskId);
            }
        }
    }

    private void updateHeader() {
        boolean hasCards = !cards.isEmpty();
        emptyView.setVisibility(hasCards ? GONE : VISIBLE);
        if (!hasCards) {
            titleView.setText("最近任务");
            detailView.setText("轻点空白返回桌面");
            return;
        }
        int displayedIndex = indexOfTask(activeTaskId);
        if (displayedIndex < 0) {
            displayedIndex = 0;
        }
        CardRecord active = cards.get(displayedIndex);
        int position = displayedIndex + 1;
        titleView.setText(active.effectiveTitle());
        detailView.setText("左右滑动切换  " + active.subtitle + "  " + position + " / " + cards.size());
    }

    private void applyStackLayout(boolean animate) {
        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> applyStackLayout(animate));
            return;
        }
        if (cards.isEmpty()) {
            return;
        }
        float activeIndex = clamp(visualTaskIndex, 0f, Math.max(0, cards.size() - 1));
        int width = getWidth();
        int height = getHeight();
        int cardWidth = resolvePreferredCardWidth(width);
        int cardHeight = resolvePreferredCardHeight(height);
        float frontY = Math.max(dp(110), ((height - cardHeight) * 0.5f) - dp(8));
        float stackBaseLeft = cardWidth * STACK_LEFT_INSET_RATIO;
        float frontLeft = Math.min(
                width - (cardWidth * STACK_FRONT_VISIBLE_RATIO),
                stackBaseLeft + (cardWidth * (1.0f - STACK_MIN_OVERLAP_RATIO)));
        float backSpreadPx = Math.min(
                cardWidth * STACK_BACK_SPREAD_RATIO,
                dp(96));
        float maxTranslationZ = dp(24);
        float zStepPx = dp(8);
        float frontZ = maxTranslationZ + zStepPx + maxTranslationZ;
        for (int i = 0; i < cards.size(); i++) {
            CardRecord card = cards.get(i);
            TaskCardView cardView = cardViews.get(card.taskId);
            if (cardView == null) {
                continue;
            }
            LayoutParams lp = (LayoutParams) cardView.getLayoutParams();
            if (lp.width != cardWidth || lp.height != cardHeight) {
                lp.width = cardWidth;
                lp.height = cardHeight;
                cardView.setLayoutParams(lp);
            }
            float targetX;
            float targetY;
            float targetScale;
            float targetAlpha;
            float targetZ;
            float relative = i - activeIndex;
            if (relative < 0f) {
                float distance = clamp(-relative, 0f, 2f);
                float outgoingProgress = smoothStep((float) Math.pow(
                        clamp(distance, 0f, 1f),
                        STACK_FRONT_REVEAL_CURVE_POWER));
                float rightBaseX = width - (cardWidth * 0.18f);
                float rightFarX = rightBaseX + (Math.max(0f, distance - 1f) * dp(18));
                float hiddenAlpha = distance > 1f
                        ? Math.max(0f, 0.58f - ((distance - 1f) * 0.58f))
                        : lerp(1f, 0.58f, distance);
                targetX = lerp(frontLeft, rightFarX, outgoingProgress);
                targetY = frontY + (distance * dp(12));
                targetScale = Math.max(0.84f, 1.0f - (distance * 0.08f));
                targetAlpha = hiddenAlpha;
                targetZ = lerp(
                        frontZ,
                        maxTranslationZ + (zStepPx * 0.5f) - distance,
                        outgoingProgress);
            } else {
                float distance = Math.min(relative, 4f);
                float handoffProgress = smoothStep((float) Math.pow(
                        clamp(distance, 0f, 1f),
                        STACK_FRONT_REVEAL_CURVE_POWER));
                float stackDepth = clamp(distance, 0f, MAX_STACK_LAYERS);
                float revealCurve = (float) Math.pow(
                        clamp(stackDepth / MAX_STACK_LAYERS, 0f, 1f),
                        STACK_DEPTH_CURVE_POWER);
                float visualStackDepth = revealCurve * MAX_STACK_LAYERS;
                float backgroundSpreadProgress = clamp(
                        (stackDepth - 1.0f) / Math.max(1.0f, MAX_STACK_LAYERS - 1.0f),
                        0f,
                        1f);
                float backgroundSpreadCurve = (float) Math.pow(
                        backgroundSpreadProgress,
                        STACK_DEPTH_CURVE_POWER);
                float backgroundLeft = stackBaseLeft
                        - (backSpreadPx * backgroundSpreadCurve);
                float backgroundScale = Math.max(
                        STACK_MIN_SCALE,
                        1.0f - (STACK_SCALE_STEP * visualStackDepth));
                float backgroundAlpha = distance >= 4f
                        ? 0f
                        : Math.max(0.42f, 0.94f - (distance * 0.15f));
                float backgroundZ = Math.max(0f, maxTranslationZ - (revealCurve * maxTranslationZ));
                targetX = lerp(frontLeft, backgroundLeft, handoffProgress);
                targetY = frontY + (distance * dp(11));
                targetScale = lerp(1f, backgroundScale, handoffProgress);
                targetAlpha = lerp(1f, backgroundAlpha, handoffProgress);
                targetZ = lerp(frontZ, backgroundZ, handoffProgress);
            }
            cardView.setTranslationZ(targetZ);
            if (animate) {
                cardView.animate()
                        .x(targetX)
                        .y(targetY)
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .alpha(targetAlpha)
                        .setDuration(220L)
                        .start();
            } else {
                cardView.animate().cancel();
                cardView.setX(targetX);
                cardView.setY(targetY);
                cardView.setScaleX(targetScale);
                cardView.setScaleY(targetScale);
                cardView.setAlpha(targetAlpha);
            }
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float lerp(float start, float end, float progress) {
        return start + ((end - start) * clamp(progress, 0f, 1f));
    }

    private float smoothStep(float value) {
        float clamped = clamp(value, 0f, 1f);
        return clamped * clamped * (3.0f - (2.0f * clamped));
    }

    private void trackVelocity(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            recycleVelocityTracker();
            velocityTracker = VelocityTracker.obtain();
        } else if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void startInertialScrollIfNeeded() {
        if (velocityTracker == null || cards.isEmpty()) {
            return;
        }
        velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
        float xVelocity = velocityTracker.getXVelocity();
        float yVelocity = velocityTracker.getYVelocity();
        if (Math.abs(xVelocity) < minimumFlingVelocity || Math.abs(xVelocity) <= Math.abs(yVelocity)) {
            return;
        }
        float taskVelocityPerSecond = xVelocity / resolveSwipeStepPx();
        if (Math.abs(taskVelocityPerSecond) < 0.02f) {
            return;
        }
        inertialVelocityTaskIndexPerSecond = taskVelocityPerSecond;
        lastInertialFrameTimeMs = AnimationUtils.currentAnimationTimeMillis();
        removeCallbacks(inertialScrollRunnable);
        postOnAnimation(inertialScrollRunnable);
    }

    private void stopInertialScroll() {
        inertialVelocityTaskIndexPerSecond = 0f;
        removeCallbacks(inertialScrollRunnable);
    }

    private float resolveSwipeStepPx() {
        int width = getWidth();
        if (width <= 0) {
            return dp(120);
        }
        int cardWidth = Math.min(width - dp(48), Math.round(width * 0.84f));
        return Math.max(dp(96), cardWidth * 0.52f);
    }

    private boolean containsTaskId(int taskId) {
        return indexOfTask(taskId) >= 0;
    }

    private int indexOfTask(int taskId) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).taskId == taskId) {
                return i;
            }
        }
        return -1;
    }

    private CardRecord findCard(int taskId) {
        int index = indexOfTask(taskId);
        return index >= 0 ? cards.get(index) : null;
    }

    private int dp(int value) {
        return FlymeStatusBarSizer.dp(getContext(), value);
    }

    private int resolvePreferredCardWidth(int containerWidth) {
        int preferredWidth = 0;
        for (CardRecord card : cards) {
            preferredWidth = Math.max(preferredWidth, card.preferredWidthPx);
        }
        int fallbackWidth = Math.min(containerWidth - dp(32), Math.round(containerWidth * 0.82f));
        int resolvedWidth = preferredWidth > 0 ? preferredWidth : fallbackWidth;
        return Math.max(dp(220), Math.min(containerWidth - dp(16), resolvedWidth));
    }

    private int resolvePreferredCardHeight(int containerHeight) {
        int preferredHeight = 0;
        for (CardRecord card : cards) {
            preferredHeight = Math.max(preferredHeight, card.preferredHeightPx);
        }
        int fallbackHeight = Math.min(containerHeight - dp(132), Math.round(containerHeight * 0.76f));
        int resolvedHeight = preferredHeight > 0 ? preferredHeight : fallbackHeight;
        return Math.max(dp(280), Math.min(containerHeight - dp(96), resolvedHeight));
    }

    private final class TaskCardView extends FrameLayout {
        private final ImageView thumbnailView;
        private final View placeholderView;
        private final View headerBackgroundView;
        private final ImageView iconView;
        private final TextView titleText;
        private final TextView closeButton;
        private int boundTaskId = -1;
        private int generation = -1;
        private boolean active;

        TaskCardView(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setClipToOutline(true);
            setBackground(createCardBackground());

            thumbnailView = new ImageView(context);
            thumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LayoutParams thumbnailLp = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            thumbnailLp.topMargin = dp(37);
            addView(thumbnailView, thumbnailLp);

            placeholderView = new View(context);
            LayoutParams placeholderLp = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            placeholderLp.topMargin = dp(37);
            addView(placeholderView, placeholderLp);

            headerBackgroundView = new View(context);
            headerBackgroundView.setBackground(createHeaderBackground());
            addView(headerBackgroundView, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    dp(30)));

            LinearLayout topBar = new LinearLayout(context);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setPadding(dp(18), 0, dp(18), 0);

            iconView = new ImageView(context);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            topBar.addView(iconView, iconLp);

            titleText = new TextView(context);
            titleText.setTextColor(0xFF1B1B1F);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            titleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            titleText.setMaxLines(1);
            titleText.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f);
            titleLp.leftMargin = dp(9);
            titleLp.rightMargin = dp(9);
            topBar.addView(titleText, titleLp);

            closeButton = new TextView(context);
            closeButton.setText("×");
            closeButton.setGravity(Gravity.CENTER);
            closeButton.setTextColor(0xFF77777C);
            closeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            closeButton.setBackground(null);
            topBar.addView(closeButton, new LinearLayout.LayoutParams(dp(18), dp(18)));

            LayoutParams topBarLp = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    dp(30));
            addView(topBar, topBarLp);

            closeButton.setOnClickListener(v -> {
                if (boundTaskId != -1) {
                    callbacks.onTaskDismissRequested(boundTaskId);
                }
            });
        }

        void bind(CardRecord record, boolean active) {
            this.boundTaskId = record.taskId;
            this.generation = record.generation;
            this.active = active;
            setContentDescription(record.contentDescription);
            titleText.setText(resolveHeaderTitle(record));
            if (record.icon != null) {
                iconView.setImageDrawable(record.icon);
            } else {
                iconView.setImageDrawable(null);
            }
            closeButton.setVisibility(active ? VISIBLE : INVISIBLE);
            closeButton.setAlpha(active ? 1f : 0.55f);
            if (record.thumbnail != null && !record.thumbnail.isRecycled()) {
                thumbnailView.setImageBitmap(record.thumbnail);
                placeholderView.setBackgroundColor(Color.TRANSPARENT);
            } else {
                thumbnailView.setImageDrawable(null);
                placeholderView.setBackground(createPlaceholderBackground(record.accentColor));
            }
            setOnClickListener(v -> {
                if (this.active) {
                    callbacks.onTaskLaunchRequested(boundTaskId);
                } else {
                    callbacks.onTaskActivated(boundTaskId);
                }
            });
        }

        private String resolveHeaderTitle(CardRecord record) {
            if (TextUtils.isEmpty(record.badgeText)) {
                return record.effectiveTitle();
            }
            return record.effectiveTitle() + " · " + record.badgeText;
        }

        private GradientDrawable createCardBackground() {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.TRANSPARENT);
            drawable.setCornerRadius(dp(24));
            return drawable;
        }

        private GradientDrawable createHeaderBackground() {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(0xFFF6F6F6);
            drawable.setCornerRadii(new float[]{
                    dp(16), dp(16),
                    dp(16), dp(16),
                    0f, 0f,
                    0f, 0f
            });
            return drawable;
        }

        private GradientDrawable createPlaceholderBackground(int accentColor) {
            int safeAccent = accentColor == 0 ? 0xFF2C3A5A : accentColor;
            return new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{adjustColor(safeAccent, 0.92f), 0xFF141A27});
        }

        private int adjustColor(int color, float mix) {
            int r = (int) (((Color.red(color) * mix) + (24f * (1f - mix))));
            int g = (int) (((Color.green(color) * mix) + (28f * (1f - mix))));
            int b = (int) (((Color.blue(color) * mix) + (38f * (1f - mix))));
            return Color.argb(255, clampColor(r), clampColor(g), clampColor(b));
        }

        private int clampColor(int value) {
            return Math.max(0, Math.min(255, value));
        }
    }
}
