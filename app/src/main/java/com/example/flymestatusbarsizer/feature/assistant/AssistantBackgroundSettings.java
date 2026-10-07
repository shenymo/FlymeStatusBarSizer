package com.example.flymestatusbarsizer.feature.assistant;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.LinearLayout;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;

public final class AssistantBackgroundSettings {
    private static final int REQUEST_IMAGE = 4701;
    private AssistantBackgroundSettings() { }

    public static void addRows(MainActivity activity, LinearLayout page) {
        activity.addProfileSectionHeader(page, "全局负一屏背景", "仅影响全局打开的负一屏，下次打开时生效。图片居中裁剪铺满背景。");
        activity.addSwitchRow(page, "使用自选背景", "关闭后显示后方应用；开启前请先选择图片。",
                SettingsStore.KEY_ASSISTANT_BACKGROUND_CUSTOM, false);
        String id = activity.prefs().getString(SettingsStore.KEY_ASSISTANT_BACKGROUND_IMAGE, "");
        boolean available = AssistantBackgroundImages.validId(id)
                && AssistantBackgroundImages.file(activity, id, false).isFile();
        activity.addActionButtonRow(page, "背景图片", available ? "已保存自选图片，可随时更换。"
                        : "尚未选择图片。图片保存在模块内，导出配置不包含图片。",
                available ? "更换图片" : "选择图片", () -> pickImage(activity));
        activity.addSwitchRow(page, "背景模糊", "使用自选图片时仅模糊图片；使用应用背景时模糊后方界面，卡片和文字保持清晰。",
                SettingsStore.KEY_ASSISTANT_BACKGROUND_BLUR, SettingsStore.DEFAULT_ASSISTANT_BACKGROUND_BLUR);
        activity.addChoiceRow(page, "自选背景顶部颜色",
                "自选背景开启时，统一设置 Aicy 纵览、加号、搜索文字与图标、搜索框的颜色；保留搜索框透明度与圆角。",
                SettingsStore.KEY_ASSISTANT_BACKGROUND_FOREGROUND_MODE,
                SettingsStore.ASSISTANT_FOREGROUND_FOLLOW_STATUS_BAR,
                new int[]{SettingsStore.ASSISTANT_FOREGROUND_FOLLOW_STATUS_BAR,
                        SettingsStore.ASSISTANT_FOREGROUND_BLACK, SettingsStore.ASSISTANT_FOREGROUND_WHITE},
                new String[]{"跟随状态栏", "黑色", "白色"});
        activity.addActionButtonRow(page, "恢复默认背景", "清除自选图片，恢复应用背景、背景模糊和顶部颜色跟随状态栏。",
                "恢复默认", () -> {
                    Context context = activity.getApplicationContext();
                    AssistantBackgroundImages.WORKER.execute(() -> {
                        boolean saved = SettingsStore.prefs(context).edit().remove(SettingsStore.KEY_ASSISTANT_BACKGROUND_IMAGE)
                                .putBoolean(SettingsStore.KEY_ASSISTANT_BACKGROUND_CUSTOM, false)
                                .putBoolean(SettingsStore.KEY_ASSISTANT_BACKGROUND_BLUR, true)
                                .putInt(SettingsStore.KEY_ASSISTANT_BACKGROUND_FOREGROUND_MODE,
                                        SettingsStore.ASSISTANT_FOREGROUND_FOLLOW_STATUS_BAR).commit();
                        if (!saved) { finish(activity, "恢复失败，请重试", false); return; }
                        SettingsStore.notifyChanged(context);
                        AssistantBackgroundImages.deleteUnused(context, "");
                        finish(activity, "已恢复默认背景", true);
                    });
                });
    }

    private static void pickImage(MainActivity activity) {
        // Try the system photo picker directly, including devices with a backported picker.
        // Its single-selection result grants access to just the selected image.
        Intent picker = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivityForResult(picker, REQUEST_IMAGE);
        } catch (ActivityNotFoundException unavailable) {
            Intent gallery = new Intent(Intent.ACTION_PICK)
                    .setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { activity.startActivityForResult(gallery, REQUEST_IMAGE); }
            catch (ActivityNotFoundException error) { activity.showToast("未找到图片选择器"); }
        }
    }

    public static boolean onResult(MainActivity activity, int requestCode, Uri uri) {
        if (requestCode != REQUEST_IMAGE) return false;
        activity.showToast("正在处理背景图片…");
        Context context = activity.getApplicationContext();
        AssistantBackgroundImages.WORKER.execute(() -> {
            try {
                String id = AssistantBackgroundImages.importImage(context, uri);
                if (!SettingsStore.prefs(context).edit().putString(SettingsStore.KEY_ASSISTANT_BACKGROUND_IMAGE, id)
                        .putBoolean(SettingsStore.KEY_ASSISTANT_BACKGROUND_CUSTOM, true).commit())
                    throw new java.io.IOException("无法保存背景设置");
                SettingsStore.notifyChanged(context);
                AssistantBackgroundImages.deleteUnused(context, id);
                finish(activity, "背景图片已保存，下次打开全局负一屏时生效", true);
            } catch (Exception | OutOfMemoryError error) {
                android.util.Log.w("FlymeAssistantGesture", "Cannot import background image", error);
                finish(activity, "图片处理失败，请选择其他图片", false);
            }
        });
        return true;
    }

    private static void finish(MainActivity activity, String message, boolean refresh) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            activity.showToast(message);
            if (refresh) activity.recreate();
        });
    }
}
