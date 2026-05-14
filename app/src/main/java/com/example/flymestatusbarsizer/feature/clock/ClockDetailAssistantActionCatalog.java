package com.example.flymestatusbarsizer.feature.clock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;

public final class ClockDetailAssistantActionCatalog {
    public static final String ACTION_WECHAT_SCAN = "微信/扫一扫";
    public static final String ACTION_WECHAT_PAY = "微信/收付款";
    public static final String ACTION_ALIPAY_SCAN = "支付宝/扫一扫";
    public static final String ACTION_ALIPAY_PAY = "支付宝/付款码";

    private static final String ASSISTANT_PACKAGE = "com.meizu.assistant";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String WECHAT_SHORTCUT_ACTIVITY =
            "com.tencent.mm.ui.ShortCutDispatchActivity";
    private static final String WECHAT_SHORTCUT_ACTION =
            "com.tencent.mm.ui.ShortCutDispatchAction";
    private static final String WECHAT_SHORTCUT_EXTRA =
            "LauncherUI.Shortcut.LaunchType";
    private static final String WECHAT_SHORTCUT_SCAN = "launch_type_scan_qrcode";
    private static final String WECHAT_SHORTCUT_PAY = "launch_type_offline_wallet";

    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static final String ALIPAY_SHORTCUT_ACTIVITY =
            "com.alipay.android.phone.wallet.shortcuts.bridge.ShortcutsLauncherActivity";
    private static final String ALIPAY_SHORTCUT_APP_ID = "KEY_APP_ID";
    private static final String ALIPAY_SHORTCUT_SCHEME = "KEY_SCHEME";
    private static final int ALIPAY_SCAN_APP_ID = 10000007;
    private static final int ALIPAY_PAY_APP_ID = 20000056;
    private static final String ALIPAY_SCAN_SCHEME =
            "alipays://platformapi/startapp?appId=10000007&sourceId=scan3dtouch";
    private static final String ALIPAY_PAY_SCHEME =
            "alipays://platformapi/startapp?appId=20000056";

    private static final String[] ACTION_VALUES = new String[]{
            ACTION_WECHAT_SCAN,
            ACTION_WECHAT_PAY,
            ACTION_ALIPAY_SCAN,
            ACTION_ALIPAY_PAY
    };

    private static final String[] ACTION_TITLES = new String[]{
            "微信扫一扫",
            "微信收付款",
            "支付宝扫一扫",
            "支付宝付款码"
    };

    private static final String[] ACTION_ICON_NAMES = new String[]{
            "quick_weixin_scan",
            "quick_weixin_receipt",
            "quick_alipay_scan",
            "quick_alipay_pay"
    };

    private ClockDetailAssistantActionCatalog() {
    }

    public static String[] actionValues() {
        return ACTION_VALUES.clone();
    }

    public static String[] actionTitles() {
        return ACTION_TITLES.clone();
    }

    public static String normalizeAction(String action) {
        if (action == null) {
            return "";
        }
        String trimmed = action.trim();
        for (String value : ACTION_VALUES) {
            if (value.equals(trimmed)) {
                return value;
            }
        }
        return "";
    }

    public static String resolveDisplayLabel(String action) {
        return resolveActionTitle(action);
    }

    public static ClockDetailActionSpec[] createAssistantPresetGrid() {
        return createGridForActionOrder(ACTION_VALUES);
    }

    public static ClockDetailActionSpec[] normalizePresetGrid(ClockDetailActionSpec[] specs) {
        String[] orderedActions = new String[ClockDetailActionSpec.SLOT_COUNT];
        boolean[] seen = new boolean[ACTION_VALUES.length];
        int count = 0;
        if (specs != null) {
            for (ClockDetailActionSpec spec : specs) {
                if (count >= orderedActions.length || spec == null) {
                    continue;
                }
                String normalized = normalizeAction(spec.assistantAction);
                int index = indexOfAction(normalized);
                if (index < 0 || seen[index]) {
                    continue;
                }
                seen[index] = true;
                orderedActions[count++] = normalized;
            }
        }
        for (String value : ACTION_VALUES) {
            if (count >= orderedActions.length) {
                break;
            }
            int index = indexOfAction(value);
            if (index >= 0 && !seen[index]) {
                seen[index] = true;
                orderedActions[count++] = value;
            }
        }
        return createGridForActionOrder(orderedActions);
    }

    public static ClockDetailActionSpec[] createGridForActionOrder(String[] actions) {
        ClockDetailActionSpec[] normalized = new ClockDetailActionSpec[ClockDetailActionSpec.SLOT_COUNT];
        boolean[] seen = new boolean[ACTION_VALUES.length];
        int count = 0;
        if (actions != null) {
            for (String action : actions) {
                if (count >= normalized.length) {
                    break;
                }
                String normalizedAction = normalizeAction(action);
                int index = indexOfAction(normalizedAction);
                if (index < 0 || seen[index]) {
                    continue;
                }
                seen[index] = true;
                normalized[count] = assistantSpec(count, normalizedAction, resolveActionTitle(normalizedAction));
                count++;
            }
        }
        for (String value : ACTION_VALUES) {
            if (count >= normalized.length) {
                break;
            }
            int index = indexOfAction(value);
            if (index >= 0 && !seen[index]) {
                seen[index] = true;
                normalized[count] = assistantSpec(count, value, resolveActionTitle(value));
                count++;
            }
        }
        for (int slot = 0; slot < normalized.length; slot++) {
            if (normalized[slot] == null) {
                normalized[slot] = assistantSpec(slot, ACTION_VALUES[slot], resolveActionTitle(ACTION_VALUES[slot]));
            }
        }
        return normalized;
    }

    public static ClockDetailActionSpec assistantSpec(int slot, String action, String title) {
        return new ClockDetailActionSpec(
                slot,
                ClockDetailActionSpec.TYPE_ASSISTANT_ACTION,
                title,
                normalizeAction(action),
                "",
                "",
                "");
    }

    public static String resolveActionTitle(String action) {
        String normalized = normalizeAction(action);
        int index = indexOfAction(normalized);
        return index < 0 ? "" : ACTION_TITLES[index];
    }

    public static Drawable resolveActionIcon(Context context, String action) {
        String normalized = normalizeAction(action);
        int index = indexOfAction(normalized);
        if (context == null || index < 0) {
            return null;
        }
        Drawable assistantDrawable = loadAssistantActionDrawable(context, ACTION_ICON_NAMES[index]);
        if (assistantDrawable != null) {
            return assistantDrawable;
        }
        return loadApplicationIcon(context, index < 2 ? WECHAT_PACKAGE : ALIPAY_PACKAGE);
    }

    public static Intent buildLaunchIntent(Context context, String action) {
        String normalized = normalizeAction(action);
        if (normalized.isEmpty()) {
            return null;
        }
        switch (normalized) {
            case ACTION_WECHAT_SCAN:
                return buildWeChatShortcutIntent(context, WECHAT_SHORTCUT_SCAN);
            case ACTION_WECHAT_PAY:
                return buildWeChatShortcutIntent(context, WECHAT_SHORTCUT_PAY);
            case ACTION_ALIPAY_SCAN:
                return buildAlipayShortcutIntent(context, ALIPAY_SCAN_APP_ID, ALIPAY_SCAN_SCHEME);
            case ACTION_ALIPAY_PAY:
                return buildAlipayShortcutIntent(context, ALIPAY_PAY_APP_ID, ALIPAY_PAY_SCHEME);
            default:
                return null;
        }
    }

    private static Intent buildWeChatShortcutIntent(Context context, String launchType) {
        ComponentName componentName = new ComponentName(WECHAT_PACKAGE, WECHAT_SHORTCUT_ACTIVITY);
        if (!isExportedActivity(context, componentName)) {
            return null;
        }
        Intent intent = new Intent(WECHAT_SHORTCUT_ACTION);
        intent.setComponent(componentName);
        intent.setPackage(WECHAT_PACKAGE);
        intent.putExtra(WECHAT_SHORTCUT_EXTRA, launchType);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static Intent buildAlipayShortcutIntent(Context context, int appId, String scheme) {
        ComponentName componentName = new ComponentName(ALIPAY_PACKAGE, ALIPAY_SHORTCUT_ACTIVITY);
        if (isExportedActivity(context, componentName)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(componentName);
            intent.setPackage(ALIPAY_PACKAGE);
            intent.putExtra(ALIPAY_SHORTCUT_APP_ID, appId);
            intent.putExtra(ALIPAY_SHORTCUT_SCHEME, scheme);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(scheme));
        intent.setPackage(ALIPAY_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static Drawable loadAssistantActionDrawable(Context context, String resourceName) {
        if (context == null || resourceName == null || resourceName.trim().isEmpty()) {
            return null;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            android.content.res.Resources resources =
                    packageManager.getResourcesForApplication(ASSISTANT_PACKAGE);
            int resourceId = resources.getIdentifier(resourceName, "drawable", ASSISTANT_PACKAGE);
            if (resourceId == 0) {
                return null;
            }
            Drawable drawable = resources.getDrawable(resourceId, context.getTheme());
            return drawable == null ? null : drawable.mutate();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable loadApplicationIcon(Context context, String packageName) {
        if (context == null || packageName == null || packageName.trim().isEmpty()) {
            return null;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            Drawable drawable = packageManager.getApplicationIcon(packageName.trim());
            return drawable == null ? null : drawable.mutate();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isExportedActivity(Context context, ComponentName componentName) {
        if (context == null || componentName == null) {
            return false;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            ActivityInfo activityInfo = packageManager.getActivityInfo(componentName, 0);
            return activityInfo != null && activityInfo.exported;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int indexOfAction(String action) {
        if (action == null || action.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < ACTION_VALUES.length; index++) {
            if (ACTION_VALUES[index].equals(action)) {
                return index;
            }
        }
        return -1;
    }
}
