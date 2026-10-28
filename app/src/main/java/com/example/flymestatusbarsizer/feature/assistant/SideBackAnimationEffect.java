package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

interface SideBackAnimationEffect {
    Session start(View view, MotionEvent downEvent);

    interface Session {
        boolean hasStarted();
        void move(View view, MotionEvent event);
        void draw(View view, Canvas canvas);
        void finish(View view, boolean committed, Runnable onFinished);
        void cancel();
    }
}
