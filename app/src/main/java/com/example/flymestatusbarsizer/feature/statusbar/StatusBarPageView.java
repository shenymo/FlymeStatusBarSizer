package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.session.PlaybackState;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;
import com.example.flymestatusbarsizer.feature.statusbar.StatusBarPageLayout.Item;

public final class StatusBarPageView extends FrameLayout {
    private final EnumMap<Item, View> items = new EnumMap<>(Item.class);
    private final SideRow left;
    private final SideRow right;
    private final Rect leftBounds = new Rect();
    private final Rect rightBounds = new Rect();
    private final Consumer<Item> action;
    private int color = android.graphics.Color.WHITE;
    private float textSize;
    private Typeface typeface = Typeface.DEFAULT;

    public StatusBarPageView(Context context, Consumer<Item> action) {
        super(context);
        this.action = action;
        textSize = 12 * getResources().getDisplayMetrics().scaledDensity;
        left = new SideRow(context, false);
        right = new SideRow(context, true);
        addView(left);
        addView(right);
    }

    public void setItems(StatusBarPageLayout layout) {
        items.clear();
        left.removeAllViews();
        right.removeAllViews();
        addItems(left, layout.left);
        addItems(right, layout.right);
        setMedia("", false, 0, false);
    }

    private void addItems(SideRow row, List<Item> list) {
        for (Item item : list) {
            View child;
            if (item.isButton()) {
                ImageButton button = new ImageButton(getContext());
                button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                button.setImageResource(item == Item.PREVIOUS ? android.R.drawable.ic_media_previous
                        : item == Item.NEXT ? android.R.drawable.ic_media_next
                        : android.R.drawable.ic_media_play);
                button.setImageTintList(ColorStateList.valueOf(color));
                button.setPadding(dp(4), dp(2), dp(4), dp(2));
                button.setContentDescription(item.label);
                button.setOnClickListener(v -> action.accept(item));
                child = button;
            } else {
                TextView text = new TextView(getContext());
                text.setSingleLine(true);
                text.setEllipsize(TextUtils.TruncateAt.END);
                text.setGravity(Gravity.CENTER_VERTICAL);
                text.setIncludeFontPadding(false);
                text.setPadding(dp(3), 0, dp(3), 0);
                text.setTextColor(color);
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                text.setTypeface(typeface);
                text.setText("--");
                child = text;
            }
            child.setTag(item);
            row.addView(child);
            items.put(item, child);
        }
    }

    public void setText(Item item, String value) {
        TextView view = (TextView) items.get(item);
        if (view != null && !TextUtils.equals(view.getText(), value)) {
            view.setText(value);
            view.setContentDescription(item.label + " " + value);
        }
    }

    public boolean hasVisible(Item item) {
        View view = items.get(item);
        return view != null && view.getVisibility() == VISIBLE && view.getWidth() > 0;
    }

    public boolean hasMedia() {
        for (Item item : items.keySet()) if (item.isMedia()) return true;
        return false;
    }

    public void setMedia(String title, boolean playing, long actions, boolean active) {
        setText(Item.SONG, title);
        active = active && (actions & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SKIP_TO_NEXT)) != 0;
        for (Item item : items.keySet()) {
            if (!item.isMedia()) continue;
            long needed = item == Item.PREVIOUS ? PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    : item == Item.NEXT ? PlaybackState.ACTION_SKIP_TO_NEXT
                    : PlaybackState.ACTION_PLAY_PAUSE
                            | (playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY);
            boolean show = active && (item == Item.SONG || (actions & needed) != 0);
            View child = items.get(item);
            // INVISIBLE is reserved for layout overflow; only availability uses GONE.
            if (!show) child.setVisibility(GONE);
            else if (child.getVisibility() == GONE) child.setVisibility(VISIBLE);
            if (item == Item.PLAY_PAUSE) {
                ((ImageButton) child).setImageResource(playing
                        ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                child.setContentDescription(playing ? "暂停" : "播放");
            }
        }
    }

    public void setStyle(int color, float size, Typeface face) {
        if (this.color == color && textSize == size && typeface == face) return;
        this.color = color;
        textSize = size;
        typeface = face;
        for (View child : items.values()) {
            if (child instanceof TextView) {
                TextView text = (TextView) child;
                text.setTextColor(color);
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                text.setTypeface(face);
            } else ((ImageButton) child).setImageTintList(ColorStateList.valueOf(color));
        }
    }

    public void setSideBounds(Rect left, Rect right) {
        if (leftBounds.equals(left) && rightBounds.equals(right)) return;
        leftBounds.set(left);
        rightBounds.set(right);
        requestLayout();
    }

    @Override protected void onMeasure(int width, int height) {
        setMeasuredDimension(MeasureSpec.getSize(width), MeasureSpec.getSize(height));
        left.measure(MeasureSpec.makeMeasureSpec(Math.max(0, leftBounds.width()), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, leftBounds.height()), MeasureSpec.EXACTLY));
        right.measure(MeasureSpec.makeMeasureSpec(Math.max(0, rightBounds.width()), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, rightBounds.height()), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        left.layout(leftBounds.left, leftBounds.top, leftBounds.right, leftBounds.bottom);
        right.layout(rightBounds.left, rightBounds.top, rightBounds.right, rightBounds.bottom);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class SideRow extends ViewGroup {
        private final boolean alignRight;
        private int usedWidth;
        SideRow(Context context, boolean alignRight) { super(context); this.alignRight = alignRight; }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            usedWidth = 0;
            boolean full = false;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                Item item = (Item) child.getTag();
                int remaining = Math.max(0, width - usedWidth);
                int childWidthSpec = item.isButton()
                        ? MeasureSpec.makeMeasureSpec(dp(28), MeasureSpec.EXACTLY)
                        : item == Item.SONG
                                ? MeasureSpec.makeMeasureSpec(Math.min(dp(96), remaining), MeasureSpec.AT_MOST)
                                : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                child.measure(childWidthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                boolean fits = !full && remaining > 0 && child.getMeasuredWidth() <= remaining
                        && (item != Item.SONG || remaining >= dp(28));
                child.setVisibility(fits ? VISIBLE : INVISIBLE);
                if (fits) usedWidth += child.getMeasuredWidth();
                else full = true;
            }
            setMeasuredDimension(width, height);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int x = alignRight ? getWidth() - usedWidth : 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == VISIBLE) {
                    child.layout(x, 0, x + child.getMeasuredWidth(), getHeight());
                    x += child.getMeasuredWidth();
                } else child.layout(0, 0, 0, 0);
            }
        }

        @Override protected LayoutParams generateDefaultLayoutParams() {
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        }
    }
}
