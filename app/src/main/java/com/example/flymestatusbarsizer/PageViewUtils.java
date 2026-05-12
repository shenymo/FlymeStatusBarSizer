package com.example.flymestatusbarsizer;

import android.content.Context;
import android.widget.LinearLayout;

final class PageViewUtils {
    private PageViewUtils() {
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams matchWrapWithTop(Context context, int topDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(context, topDp);
        return lp;
    }

    static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams wrapWrapWithTop(Context context, int topDp) {
        LinearLayout.LayoutParams lp = wrapWrap();
        lp.topMargin = dp(context, topDp);
        return lp;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
