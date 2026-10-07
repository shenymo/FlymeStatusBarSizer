package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;

/** A preview-only button. A drag or multiple fingers must not select a task or exit the mode. */
final class RecentTaskCardView extends ImageView {
    private final int slop;
    private float downX, downY;
    private boolean tap;

    RecentTaskCardView(Context context, RecentTaskCard card, Runnable onSelect) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setScaleType(ScaleType.FIT_CENTER);
        setImageBitmap(card.preview);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(32, 34, 38));
        background.setCornerRadius(4 * getResources().getDisplayMetrics().density);
        setBackground(background);
        setClipToOutline(true);
        setContentDescription("切换到 " + card.description);
        setOnClickListener(v -> onSelect.run());
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tap = true;
                downX = event.getX(); downY = event.getY();
                setAlpha(.8f);
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) {
                    tap = false;
                    setAlpha(1f);
                }
                break;
            case MotionEvent.ACTION_UP:
                setAlpha(1f);
                if (tap && event.getX() >= 0 && event.getX() < getWidth()
                        && event.getY() >= 0 && event.getY() < getHeight()) performClick();
                tap = false;
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                tap = false;
                setAlpha(1f);
                break;
        }
        return true;
    }
}
