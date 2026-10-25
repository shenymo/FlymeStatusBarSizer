package com.example.flymestatusbarsizer.ui;

/** Restart entries for the packages declared in META-INF/xposed/scope.list. */
public enum RestartTarget {
    SYSTEM_UI("com.android.systemui", "系统界面（SystemUI）",
            "修改状态栏或通知背景后，需重启系统界面（SystemUI）才能生效。"),
    SHARE_RESOLVER("com.android.intentresolver", "系统分享",
            "修改分享面板后，需重启系统分享；下次打开分享面板时生效。"),
    GALLERY("com.meizu.media.gallery", "Flyme 图库",
            "修改图库分享功能后，需重启 Flyme 图库才能生效。"),
    LAUNCHER("com.meizu.flyme.launcher", "系统桌面",
            "修改桌面文件夹或最近任务布局后，需重启系统桌面才能生效。"),
    ASSISTANT("com.meizu.assistant", "Aicy 纵览",
            "修改全局负一屏功能后，需重启 Aicy 纵览才能生效。"),
    SYSTEM_UI_TOOLS("com.flyme.systemuitools", "系统小窗工具（SystemUITools）",
            "重启后小窗相关修改重新加载。"),
    PPS("com.meizu.pps", "OneMind（PPS）",
            "修改 OneMind 设置后，需重启 OneMind 才能生效。"),
    PHONE_MANAGER("com.meizu.safe", "手机管家",
            "修改后台优化设置后，需重启手机管家才能生效。"),
    CARLINK("com.upuphone.carlink", "CarLink",
            "修改应用流转和车机设置后，需重启 CarLink 才能生效。"),
    AOSP_IME("com.android.inputmethod.latin", "AOSP 输入法",
            "修改输入法控制栏后，需重启输入法才能生效。"),
    GBOARD("com.google.android.inputmethod.latin", "Gboard",
            "修改输入法控制栏后，需重启输入法才能生效。"),
    WECHAT_IME("com.tencent.wetype", "微信输入法",
            "修改输入法控制栏后，需重启输入法才能生效。"),
    FLYME_IME("flyme.inputmethod", "Flyme 输入法",
            "修改输入法控制栏后，需重启输入法才能生效。"),
    FRAMEWORK("android", "系统框架",
            "系统框架相关功能需重启手机才能生效。操作前请保存未完成的内容，并确认设备已授权 Root。");

    public final String packageName;
    public final String label;
    public final String summary;

    RestartTarget(String packageName, String label, String summary) {
        this.packageName = packageName;
        this.label = label;
        this.summary = summary;
    }

    public String[] restartCommands() {
        return switch (this) {
            case SYSTEM_UI -> new String[]{
                    "killall " + packageName, "pkill -f " + packageName, "am crash " + packageName};
            case SHARE_RESOLVER, GALLERY, LAUNCHER -> new String[]{
                    "am force-stop " + packageName, "pkill -f " + packageName, "killall " + packageName};
            // Keep bound services eligible for reconnection; force-stop removes their bindings.
            case ASSISTANT -> new String[]{"killall " + packageName, "pkill -f " + packageName};
            case SYSTEM_UI_TOOLS -> new String[]{
                    "pkill -f " + packageName, "killall " + packageName, "am crash " + packageName};
            case PPS -> new String[]{
                    "cmd package set-stopped-state " + packageName + " false",
                    "pkill -f " + packageName, "killall " + packageName};
            case PHONE_MANAGER, CARLINK, AOSP_IME, GBOARD, WECHAT_IME, FLYME_IME -> new String[]{
                    "pkill -f '^" + packageName.replace(".", "[.]") + "(:[^[:space:]]+)?$'",
                    "killall " + packageName};
            case FRAMEWORK -> throw new IllegalStateException("Framework requires a separate device restart");
        };
    }

    public boolean stopOnFirstSuccess() {
        return this != PPS;
    }
}
