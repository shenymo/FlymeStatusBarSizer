package com.example.flymestatusbarsizer;

import android.content.Context;
import android.graphics.Typeface;

import java.util.ArrayList;

final class BatteryTextFontHelper {
    private static final String MI_SANS_LATIN_VF_NUMBER_ASSET_PATH = "fonts/MiSansLatinVFNumber.ttf";
    private static final int[] CANDIDATE_OPTIONS = new int[]{
            SettingsStore.BATTERY_TEXT_FONT_SYSTEM_DEFAULT,
            SettingsStore.BATTERY_TEXT_FONT_SERIF,
            SettingsStore.BATTERY_TEXT_FONT_MONOSPACE,
            SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF,
            SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF_MEDIUM,
            SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF_CONDENSED,
            SettingsStore.BATTERY_TEXT_FONT_MI_SANS_LATIN_VF_NUMBER
    };
    private static volatile Typeface miSansLatinVfNumberTypeface;
    private static volatile boolean miSansLatinVfNumberLoaded;

    private BatteryTextFontHelper() {
    }

    static int[] getAvailableFontOptions(Context context) {
        ArrayList<Integer> options = new ArrayList<>();
        for (int option : CANDIDATE_OPTIONS) {
            if (isOptionAvailable(context, option)) {
                options.add(option);
            }
        }
        int[] result = new int[options.size()];
        for (int i = 0; i < options.size(); i++) {
            result[i] = options.get(i);
        }
        return result;
    }

    static String[] getFontLabels(int[] options) {
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = getFontLabel(options[i]);
        }
        return labels;
    }

    static String getFontLabel(int option) {
        switch (SettingsStore.normalizeBatteryTextFont(option)) {
            case SettingsStore.BATTERY_TEXT_FONT_SERIF:
                return "serif";
            case SettingsStore.BATTERY_TEXT_FONT_MONOSPACE:
                return "monospace";
            case SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF:
                return "sans-serif";
            case SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF_MEDIUM:
                return "sans-serif-medium";
            case SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF_CONDENSED:
                return "sans-serif-condensed";
            case SettingsStore.BATTERY_TEXT_FONT_MI_SANS_LATIN_VF_NUMBER:
                return "MiSansLatinVFNumber";
            default:
                return "系统默认";
        }
    }

    static Typeface resolveTypeface(Context context, int option) {
        switch (SettingsStore.normalizeBatteryTextFont(option)) {
            case SettingsStore.BATTERY_TEXT_FONT_SERIF:
                return Typeface.SERIF;
            case SettingsStore.BATTERY_TEXT_FONT_MONOSPACE:
                return Typeface.MONOSPACE;
            case SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF:
                return resolveNamedTypeface("sans-serif");
            case SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF_MEDIUM:
                return resolveNamedTypeface("sans-serif-medium");
            case SettingsStore.BATTERY_TEXT_FONT_SANS_SERIF_CONDENSED:
                return resolveNamedTypeface("sans-serif-condensed");
            case SettingsStore.BATTERY_TEXT_FONT_MI_SANS_LATIN_VF_NUMBER:
                return resolveMiSansLatinVfNumberTypeface(context);
            default:
                return null;
        }
    }

    private static boolean isOptionAvailable(Context context, int option) {
        switch (SettingsStore.normalizeBatteryTextFont(option)) {
            case SettingsStore.BATTERY_TEXT_FONT_SYSTEM_DEFAULT:
            case SettingsStore.BATTERY_TEXT_FONT_SERIF:
            case SettingsStore.BATTERY_TEXT_FONT_MONOSPACE:
                return true;
            default:
                return resolveTypeface(context, option) != null;
        }
    }

    private static Typeface resolveMiSansLatinVfNumberTypeface(Context context) {
        Typeface cached = miSansLatinVfNumberTypeface;
        if (cached != null) {
            return cached;
        }
        if (miSansLatinVfNumberLoaded) {
            return null;
        }
        synchronized (BatteryTextFontHelper.class) {
            if (miSansLatinVfNumberTypeface != null) {
                return miSansLatinVfNumberTypeface;
            }
            if (miSansLatinVfNumberLoaded) {
                return null;
            }
            miSansLatinVfNumberLoaded = true;
            Context fontContext = resolveFontContext(context);
            if (fontContext == null) {
                return null;
            }
            try {
                miSansLatinVfNumberTypeface = Typeface.createFromAsset(
                        fontContext.getAssets(), MI_SANS_LATIN_VF_NUMBER_ASSET_PATH);
            } catch (Throwable ignored) {
                miSansLatinVfNumberTypeface = null;
            }
            return miSansLatinVfNumberTypeface;
        }
    }

    private static Context resolveFontContext(Context context) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (BuildConfig.APPLICATION_ID.equals(appContext.getPackageName())) {
            return appContext;
        }
        try {
            return appContext.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Typeface resolveNamedTypeface(String familyName) {
        Typeface typeface = Typeface.create(familyName, Typeface.NORMAL);
        if (typeface == null) {
            return null;
        }
        if ("sans-serif".equals(familyName)) {
            return Typeface.SANS_SERIF;
        }
        if (sameTypeface(typeface, Typeface.DEFAULT) || sameTypeface(typeface, Typeface.SANS_SERIF)) {
            return null;
        }
        return typeface;
    }

    private static boolean sameTypeface(Typeface first, Typeface second) {
        return first == second || (first != null && first.equals(second));
    }
}
