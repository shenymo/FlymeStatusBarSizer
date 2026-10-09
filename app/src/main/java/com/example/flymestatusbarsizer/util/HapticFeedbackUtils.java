package com.example.flymestatusbarsizer.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Haptics for SystemUI hooks, including views hosted in windowless surfaces. */
public final class HapticFeedbackUtils {
    private HapticFeedbackUtils() {}

    public static void perform(Context context, View view, int feedback) {
        if (view != null && view.isAttachedToWindow()) {
            try {
                if (view.performHapticFeedback(feedback, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)) return;
            } catch (RuntimeException error) {
                Log.w("FlymeHaptics", "View haptic feedback unavailable", error);
            }
        }
        // WindowlessWindowManager cannot always dispatch view haptics. These callers
        // run in SystemUI, which already holds the vibration permission.
        try {
            if (Settings.System.getInt(context.getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return;
            Vibrator vibrator = context.getSystemService(Vibrator.class);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            boolean tick = feedback == HapticFeedbackConstants.CLOCK_TICK;
            boolean longPress = feedback == HapticFeedbackConstants.LONG_PRESS;
            VibrationEffect effect;
            if (Build.VERSION.SDK_INT >= 29) {
                effect = VibrationEffect.createPredefined(tick ? VibrationEffect.EFFECT_TICK
                        : longPress ? VibrationEffect.EFFECT_HEAVY_CLICK : VibrationEffect.EFFECT_CLICK);
            } else {
                effect = VibrationEffect.createOneShot(tick ? 8 : longPress ? 20 : 12,
                        VibrationEffect.DEFAULT_AMPLITUDE);
            }
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH).build());
            } else {
                vibrator.vibrate(effect, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            }
        } catch (RuntimeException error) {
            Log.w("FlymeHaptics", "System haptic feedback unavailable", error);
        }
    }
}
