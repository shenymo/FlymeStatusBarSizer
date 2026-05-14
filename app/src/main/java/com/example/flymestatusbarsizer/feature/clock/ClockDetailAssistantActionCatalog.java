package com.example.flymestatusbarsizer.feature.clock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClockDetailAssistantActionCatalog {
    public static final String ACTION_WECHAT_SCAN = "微信/扫一扫";
    public static final String ACTION_WECHAT_PAY = "微信/收付款";
    public static final String ACTION_ALIPAY_SCAN = "支付宝/扫一扫";
    public static final String ACTION_ALIPAY_PAY = "支付宝/付款码";
    public static final String ACTION_SCANNER_SCAN = "扫一扫/扫一扫";
    public static final String ACTION_WECHAT_BUS = "微信/乘车码";

    private static final String ASSISTANT_PACKAGE = "com.meizu.assistant";
    private static final String ASSISTANT_DISPATCH_ACTION =
            "com.meizu.assistant.action.QUICK_ACTIONS_DISPATCH";
    private static final String ASSISTANT_RAW_QUICK_ACTIONS = "quick_actions";
    private static final String LEGACY_CAMERA_SCAN = "相机/扫一扫";
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

    private static final String[] DEFAULT_ACTION_ORDER = new String[]{
            ACTION_WECHAT_SCAN,
            ACTION_SCANNER_SCAN,
            ACTION_ALIPAY_SCAN,
            ACTION_ALIPAY_PAY,
            ACTION_WECHAT_BUS,
            ACTION_WECHAT_PAY,
            "支付宝/乘车码",
            "时钟/新建闹钟"
    };

    private static final String[] DEFAULT_SELECTED_ACTIONS = new String[]{
            ACTION_WECHAT_SCAN,
            ACTION_SCANNER_SCAN,
            ACTION_ALIPAY_SCAN,
            ACTION_ALIPAY_PAY,
            ACTION_WECHAT_BUS
    };
    private static final int ACTION_CACHE_FORMAT_VERSION = 1;

    private static final Map<String, String> ACTION_ICON_NAMES = buildActionIconNames();
    private static final Object CACHE_LOCK = new Object();

    private static volatile AssistantActionRecord[] cachedRecords;
    private static volatile String[] cachedActionValues;

    private ClockDetailAssistantActionCatalog() {
    }

    public static String[] actionValues() {
        return DEFAULT_ACTION_ORDER.clone();
    }

    public static String[] availableActionValues(Context context) {
        ensureAssistantCache(context);
        String[] cachedValues = cachedActionValues;
        if (cachedValues == null || cachedValues.length == 0) {
            return DEFAULT_ACTION_ORDER.clone();
        }
        return cachedValues.clone();
    }

    public static String[] availableActionValues(Context context, String cacheJson) {
        restoreActionCache(context, cacheJson);
        String[] cachedValues = cachedActionValues;
        if (cachedValues == null || cachedValues.length == 0) {
            return DEFAULT_ACTION_ORDER.clone();
        }
        return cachedValues.clone();
    }

    public static void restoreActionCache(Context context, String cacheJson) {
        AssistantActionRecord[] persistedRecords = parseActionCacheJson(cacheJson);
        if (persistedRecords.length > 0) {
            setCachedRecords(persistedRecords);
            return;
        }
        setCachedRecords(loadAssistantActionRecords(context));
    }

    public static String buildAvailableActionCacheJson(Context context) {
        AssistantActionRecord[] scannedRecords = loadAssistantActionRecords(context);
        if (scannedRecords == null || scannedRecords.length == 0) {
            return "";
        }
        setCachedRecords(scannedRecords);
        return encodeActionCacheJson(scannedRecords);
    }

    public static String[] actionValuesFromCacheJson(String cacheJson) {
        AssistantActionRecord[] records = parseActionCacheJson(cacheJson);
        if (records.length == 0) {
            return new String[0];
        }
        return buildActionValuesArray(records);
    }

    public static String normalizeAction(String action) {
        if (action == null) {
            return "";
        }
        String trimmed = action.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (LEGACY_CAMERA_SCAN.equals(trimmed)) {
            return ACTION_SCANNER_SCAN;
        }
        return trimmed;
    }

    public static String resolveDisplayLabel(String action) {
        return resolveActionName(action);
    }

    public static String resolveActionTitle(String action) {
        String normalized = normalizeAction(action);
        if (normalized.isEmpty()) {
            return "";
        }
        String appName = resolveActionAppName(normalized);
        String actionName = resolveActionName(normalized);
        return buildDisplayLabel(appName, actionName, normalized);
    }

    public static String resolveActionAppName(String action) {
        String normalized = normalizeAction(action);
        if (normalized.isEmpty()) {
            return "";
        }
        AssistantActionRecord record = findCachedRecord(normalized);
        if (record != null && !record.appName.isEmpty()) {
            return record.appName;
        }
        int separatorIndex = normalized.indexOf('/');
        if (separatorIndex <= 0) {
            return normalized;
        }
        return normalized.substring(0, separatorIndex).trim();
    }

    public static String resolveActionName(String action) {
        String normalized = normalizeAction(action);
        if (normalized.isEmpty()) {
            return "";
        }
        AssistantActionRecord record = findCachedRecord(normalized);
        if (record != null && !record.actionName.isEmpty()) {
            return record.actionName;
        }
        int separatorIndex = normalized.indexOf('/');
        if (separatorIndex < 0 || separatorIndex >= normalized.length() - 1) {
            return normalized;
        }
        return normalized.substring(separatorIndex + 1).trim();
    }

    public static ClockDetailActionSpec[] createAssistantPresetGrid() {
        return createGridForActionOrder(DEFAULT_SELECTED_ACTIONS);
    }

    public static ClockDetailActionSpec[] normalizePresetGrid(ClockDetailActionSpec[] specs) {
        ArrayList<String> orderedActions = new ArrayList<>();
        if (specs != null) {
            for (ClockDetailActionSpec spec : specs) {
                if (spec == null) {
                    continue;
                }
                String normalizedAction = normalizeAction(spec.assistantAction);
                if (normalizedAction.isEmpty() || orderedActions.contains(normalizedAction)) {
                    continue;
                }
                orderedActions.add(normalizedAction);
                if (orderedActions.size() >= ClockDetailActionSpec.SLOT_COUNT) {
                    break;
                }
            }
        }
        if (orderedActions.isEmpty()) {
            Collections.addAll(orderedActions, DEFAULT_SELECTED_ACTIONS);
        }
        return createGridForActionOrder(orderedActions.toArray(new String[0]));
    }

    public static ClockDetailActionSpec[] createGridForActionOrder(String[] actions) {
        ClockDetailActionSpec[] normalized = emptyGrid();
        ArrayList<String> orderedActions = new ArrayList<>();
        if (actions != null) {
            for (String action : actions) {
                String normalizedAction = normalizeAction(action);
                if (normalizedAction.isEmpty() || orderedActions.contains(normalizedAction)) {
                    continue;
                }
                orderedActions.add(normalizedAction);
                if (orderedActions.size() >= ClockDetailActionSpec.SLOT_COUNT) {
                    break;
                }
            }
        }
        if (orderedActions.isEmpty()) {
            Collections.addAll(orderedActions, DEFAULT_SELECTED_ACTIONS);
        }
        for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
            if (slot < orderedActions.size()) {
                String action = orderedActions.get(slot);
                normalized[slot] = assistantSpec(slot, action, resolveActionTitle(action));
            } else {
                normalized[slot] = ClockDetailActionSpec.empty(slot);
            }
        }
        return normalized;
    }

    public static ClockDetailActionSpec assistantSpec(int slot, String action, String title) {
        String normalizedAction = normalizeAction(action);
        return new ClockDetailActionSpec(
                slot,
                ClockDetailActionSpec.TYPE_ASSISTANT_ACTION,
                title == null || title.trim().isEmpty()
                        ? resolveActionTitle(normalizedAction)
                        : title.trim(),
                normalizedAction,
                "",
                "",
                "");
    }

    public static Drawable resolveActionIcon(Context context, String action) {
        String normalized = normalizeAction(action);
        if (context == null || normalized.isEmpty()) {
            return null;
        }
        String resourceName = ACTION_ICON_NAMES.get(normalized);
        Drawable assistantDrawable = loadAssistantActionDrawable(context, resourceName);
        if (assistantDrawable != null) {
            return assistantDrawable;
        }
        String packageName = resolvePrimaryPackageName(context, normalized);
        return loadApplicationIcon(context, packageName);
    }

    public static Intent buildLaunchIntent(Context context, String action) {
        String normalized = normalizeAction(action);
        if (context == null || normalized.isEmpty()) {
            return null;
        }
        AssistantActionRecord record = findActionRecord(context, normalized);
        Intent launchIntent = buildLaunchIntent(context, record);
        if (launchIntent != null) {
            return launchIntent;
        }
        return buildFallbackLaunchIntent(context, normalized);
    }

    private static Intent buildLaunchIntent(Context context, AssistantActionRecord record) {
        Intent dynamicIntent = buildAssistantQuickActionIntent(context, record);
        if (dynamicIntent != null) {
            return dynamicIntent;
        }
        return record == null ? null : buildFallbackLaunchIntent(context, record.actionFlag);
    }

    private static Intent buildFallbackLaunchIntent(Context context, String normalizedAction) {
        switch (normalizedAction) {
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

    private static ClockDetailActionSpec[] emptyGrid() {
        ClockDetailActionSpec[] specs = new ClockDetailActionSpec[ClockDetailActionSpec.SLOT_COUNT];
        for (int slot = 0; slot < specs.length; slot++) {
            specs[slot] = ClockDetailActionSpec.empty(slot);
        }
        return specs;
    }

    private static Intent buildAssistantQuickActionIntent(
            Context context,
            AssistantActionRecord record) {
        if (context == null || record == null || record.packageName.isEmpty()) {
            return null;
        }
        if (!ASSISTANT_PACKAGE.equals(record.packageName) && !isPackageInstalled(context, record.packageName)) {
            return null;
        }
        Intent parsedIntent = null;
        try {
            if ((record.state & 8) != 0) {
                parsedIntent = context.getPackageManager().getLaunchIntentForPackage(record.packageName);
            } else if (!record.intentUri.isEmpty()) {
                parsedIntent = Intent.parseUri(record.intentUri, 0);
            }
        } catch (Throwable ignored) {
            parsedIntent = null;
        }
        if (parsedIntent == null) {
            return null;
        }
        parsedIntent.setPackage(record.packageName);
        parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ACTION_SCANNER_SCAN.equals(record.actionFlag)) {
            parsedIntent.putExtra("PACKAGE_NAME", ASSISTANT_PACKAGE);
        }
        if (ASSISTANT_PACKAGE.equals(record.packageName)) {
            return parsedIntent;
        }
        Intent dispatchIntent = new Intent(ASSISTANT_DISPATCH_ACTION);
        dispatchIntent.setPackage(ASSISTANT_PACKAGE);
        dispatchIntent.putExtra("pkg", record.packageName);
        dispatchIntent.putExtra("appName", record.appName);
        dispatchIntent.putExtra("parsedIntent", parsedIntent);
        dispatchIntent.putExtra("originIntent", record.intentUri);
        dispatchIntent.putExtra("isNeedLogin", (record.state & 8) != 0);
        dispatchIntent.putExtra("actionFlag", record.actionFlag);
        dispatchIntent.putExtra("appOriginId", record.activityName);
        dispatchIntent.putExtra("pagePath", record.pagePath);
        dispatchIntent.putExtra("fromWidget", false);
        dispatchIntent.putExtra("payUrl", record.payUrl);
        dispatchIntent.putExtra("launcher_cookie_key", record.actionFlag);
        dispatchIntent.putExtra("launcher_cookie_pkg", record.packageName);
        dispatchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return dispatchIntent;
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

    private static void ensureAssistantCache(Context context) {
        if (cachedRecords != null && cachedActionValues != null) {
            return;
        }
        synchronized (CACHE_LOCK) {
            if (cachedRecords != null && cachedActionValues != null) {
                return;
            }
            AssistantActionRecord[] loadedRecords = loadAssistantActionRecords(context);
            setCachedRecords(loadedRecords);
        }
    }

    private static void setCachedRecords(AssistantActionRecord[] records) {
        synchronized (CACHE_LOCK) {
            cachedRecords = records == null ? null : records.clone();
            cachedActionValues = buildActionValuesArray(cachedRecords);
        }
    }

    private static AssistantActionRecord findActionRecord(Context context, String action) {
        String normalized = normalizeAction(action);
        ensureAssistantCache(context);
        return findCachedRecord(normalized);
    }

    private static AssistantActionRecord findCachedRecord(String normalizedAction) {
        if (normalizedAction == null || normalizedAction.isEmpty()) {
            return null;
        }
        AssistantActionRecord[] records = cachedRecords;
        if (records == null) {
            return null;
        }
        for (AssistantActionRecord record : records) {
            if (record != null && normalizedAction.equals(record.actionFlag)) {
                return record;
            }
        }
        return null;
    }

    private static AssistantActionRecord[] loadAssistantActionRecords(Context context) {
        if (context == null) {
            return fallbackAssistantActionRecords();
        }
        LinkedHashMap<String, AssistantActionRecord> recordsByFlag = new LinkedHashMap<>();
        try {
            PackageManager packageManager = context.getPackageManager();
            Resources resources = packageManager.getResourcesForApplication(ASSISTANT_PACKAGE);
            int resourceId = resources.getIdentifier(
                    ASSISTANT_RAW_QUICK_ACTIONS,
                    "raw",
                    ASSISTANT_PACKAGE);
            if (resourceId != 0) {
                String rawText = readRawText(resources, resourceId);
                JSONObject root = new JSONObject(rawText);
                String value = root.optString("value", "");
                if (!value.isEmpty()) {
                    JSONArray items = new JSONArray(value);
                    for (int index = 0; index < items.length(); index++) {
                        JSONObject item = items.optJSONObject(index);
                        AssistantActionRecord record = AssistantActionRecord.fromJson(item, index);
                        record = normalizeRecord(record);
                        if (record == null || record.actionFlag.isEmpty()) {
                            continue;
                        }
                        if ("携程旅行".equals(record.appName)) {
                            continue;
                        }
                        if (!recordsByFlag.containsKey(record.actionFlag)) {
                            recordsByFlag.put(record.actionFlag, record);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            recordsByFlag.clear();
        }
        if (recordsByFlag.isEmpty()) {
            return fallbackAssistantActionRecords();
        }
        ArrayList<AssistantActionRecord> records = new ArrayList<>(recordsByFlag.values());
        Collections.sort(records, new Comparator<AssistantActionRecord>() {
            @Override
            public int compare(AssistantActionRecord first, AssistantActionRecord second) {
                int firstRank = defaultOrderIndex(first.actionFlag);
                int secondRank = defaultOrderIndex(second.actionFlag);
                if (firstRank != secondRank) {
                    return firstRank - secondRank;
                }
                if (first.order != second.order) {
                    return first.order - second.order;
                }
                return first.actionFlag.compareTo(second.actionFlag);
            }
        });
        return records.toArray(new AssistantActionRecord[0]);
    }

    private static AssistantActionRecord normalizeRecord(AssistantActionRecord record) {
        if (record == null) {
            return null;
        }
        if ("com.meizu.media.camera.CameraActivity".equals(record.activityName)) {
            record.packageName = "com.flyme.scanner";
            record.appName = "扫一扫";
            record.actionName = "扫一扫";
            record.intentUri = "#Intent;action=com.flyme.scanner.capture;end";
        }
        record.actionFlag = normalizeAction(record.appName + "/" + record.actionName);
        return record;
    }

    private static AssistantActionRecord[] fallbackAssistantActionRecords() {
        AssistantActionRecord[] records = new AssistantActionRecord[DEFAULT_ACTION_ORDER.length];
        for (int index = 0; index < DEFAULT_ACTION_ORDER.length; index++) {
            String action = DEFAULT_ACTION_ORDER[index];
            AssistantActionRecord record = new AssistantActionRecord();
            record.actionFlag = action;
            record.appName = resolveActionAppNameFromFlag(action);
            record.actionName = resolveActionNameFromFlag(action);
            record.packageName = resolveFallbackPackageName(action);
            record.order = index;
            records[index] = record;
        }
        return records;
    }

    private static String[] buildActionValuesArray(AssistantActionRecord[] records) {
        if (records == null || records.length == 0) {
            return DEFAULT_ACTION_ORDER.clone();
        }
        ArrayList<String> actions = new ArrayList<>();
        for (AssistantActionRecord record : records) {
            if (record == null || record.actionFlag.isEmpty() || actions.contains(record.actionFlag)) {
                continue;
            }
            actions.add(record.actionFlag);
        }
        return actions.isEmpty()
                ? DEFAULT_ACTION_ORDER.clone()
                : actions.toArray(new String[0]);
    }

    private static String encodeActionCacheJson(AssistantActionRecord[] records) {
        try {
            JSONObject root = new JSONObject();
            JSONArray items = new JSONArray();
            root.put("version", ACTION_CACHE_FORMAT_VERSION);
            if (records != null) {
                for (AssistantActionRecord record : records) {
                    if (record == null || record.actionFlag.isEmpty()) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("actionFlag", record.actionFlag);
                    item.put("activityName", record.activityName);
                    item.put("packageName", record.packageName);
                    item.put("appName", record.appName);
                    item.put("intent", record.intentUri);
                    item.put("actionName", record.actionName);
                    item.put("iconDefault", record.iconDefault);
                    item.put("iconDefaultDark", record.iconDefaultDark);
                    item.put("order", record.order);
                    item.put("state", record.state);
                    item.put("pagePath", record.pagePath);
                    item.put("payUrl", record.payUrl);
                    items.put(item);
                }
            }
            root.put("items", items);
            return root.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static AssistantActionRecord[] parseActionCacheJson(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return new AssistantActionRecord[0];
        }
        LinkedHashMap<String, AssistantActionRecord> recordsByFlag = new LinkedHashMap<>();
        try {
            JSONObject root = new JSONObject(rawJson);
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                return new AssistantActionRecord[0];
            }
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.optJSONObject(index);
                AssistantActionRecord record = AssistantActionRecord.fromJson(item, index);
                record = normalizeRecord(record);
                if (record == null || record.actionFlag.isEmpty()) {
                    continue;
                }
                if (!recordsByFlag.containsKey(record.actionFlag)) {
                    recordsByFlag.put(record.actionFlag, record);
                }
            }
        } catch (Throwable ignored) {
            return new AssistantActionRecord[0];
        }
        return recordsByFlag.values().toArray(new AssistantActionRecord[0]);
    }

    private static String readRawText(Resources resources, int resourceId) throws Exception {
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            inputStream = resources.openRawResource(resourceId);
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int readSize;
            while ((readSize = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readSize);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable ignored) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String buildDisplayLabel(String appName, String actionName, String fallback) {
        if (actionName.isEmpty()) {
            return fallback;
        }
        if (appName.isEmpty() || appName.equals(actionName)) {
            return actionName;
        }
        return appName + "/" + actionName;
    }

    private static String resolvePrimaryPackageName(Context context, String action) {
        AssistantActionRecord record = findActionRecord(context, action);
        if (record != null && !record.packageName.isEmpty()) {
            return record.packageName;
        }
        return resolveFallbackPackageName(action);
    }

    private static String resolveFallbackPackageName(String action) {
        switch (normalizeAction(action)) {
            case ACTION_WECHAT_SCAN:
            case ACTION_WECHAT_PAY:
            case ACTION_WECHAT_BUS:
                return WECHAT_PACKAGE;
            case ACTION_ALIPAY_SCAN:
            case ACTION_ALIPAY_PAY:
                return ALIPAY_PACKAGE;
            case ACTION_SCANNER_SCAN:
                return "com.flyme.scanner";
            case "时钟/新建闹钟":
                return "com.android.alarmclock";
            default:
                return "";
        }
    }

    private static String resolveActionAppNameFromFlag(String actionFlag) {
        int separatorIndex = actionFlag == null ? -1 : actionFlag.indexOf('/');
        if (separatorIndex <= 0) {
            return actionFlag == null ? "" : actionFlag;
        }
        return actionFlag.substring(0, separatorIndex).trim();
    }

    private static String resolveActionNameFromFlag(String actionFlag) {
        int separatorIndex = actionFlag == null ? -1 : actionFlag.indexOf('/');
        if (separatorIndex < 0 || separatorIndex >= actionFlag.length() - 1) {
            return actionFlag == null ? "" : actionFlag;
        }
        return actionFlag.substring(separatorIndex + 1).trim();
    }

    private static Drawable loadAssistantActionDrawable(Context context, String resourceName) {
        if (context == null || resourceName == null || resourceName.trim().isEmpty()) {
            return null;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            Resources resources = packageManager.getResourcesForApplication(ASSISTANT_PACKAGE);
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

    private static boolean isPackageInstalled(Context context, String packageName) {
        if (context == null || packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
            return applicationInfo != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int defaultOrderIndex(String action) {
        String normalized = normalizeAction(action);
        for (int index = 0; index < DEFAULT_ACTION_ORDER.length; index++) {
            if (DEFAULT_ACTION_ORDER[index].equals(normalized)) {
                return index;
            }
        }
        return 1000;
    }

    private static Map<String, String> buildActionIconNames() {
        LinkedHashMap<String, String> iconNames = new LinkedHashMap<>();
        iconNames.put("Flyme Pay/付款码", "quick_meizu_pay");
        iconNames.put("微信/二维码收款", "quick_weixin_code_receipt");
        iconNames.put("百度地图/打车", "quick_baidu_taxi");
        iconNames.put("携程旅行/火车票", "quick_ctrip_train");
        iconNames.put(ACTION_WECHAT_BUS, "quick_weixin_bus");
        iconNames.put(ACTION_WECHAT_SCAN, "quick_weixin_scan");
        iconNames.put(ACTION_WECHAT_PAY, "quick_weixin_receipt");
        iconNames.put("微信/群收款", "quick_weixin_aa");
        iconNames.put("支付宝/信用卡还款", "quick_alipay_credit");
        iconNames.put("滴滴出行/快车叫车", "quick_didi_taxi");
        iconNames.put(ACTION_SCANNER_SCAN, "quick_action_camera");
        iconNames.put("快递100/查快递", "quick_kd100");
        iconNames.put("咪咕快游/云游戏", "quick_action_migu_game");
        iconNames.put("QQ浏览器/扫一扫", "quick_qq_scan");
        iconNames.put("中国移动/套餐查询", "quick_action_cmcc");
        iconNames.put(ACTION_ALIPAY_PAY, "quick_alipay_pay");
        iconNames.put("支付宝/乘车码", "quick_alipay_bus");
        iconNames.put("支付宝/健康码", "quick_alipay_health");
        iconNames.put(ACTION_ALIPAY_SCAN, "quick_alipay_scan");
        iconNames.put("支付宝/转账", "quick_alipay_transfer");
        iconNames.put("支付宝/充值中心", "quick_alipay_recharge");
        iconNames.put("支付宝/生活缴费", "quick_alipay_life");
        iconNames.put("支付宝/车主服务", "quick_alipay_car");
        iconNames.put("时钟/新建闹钟", "quick_meizu_newalarm");
        return iconNames;
    }

    private static final class AssistantActionRecord {
        String actionFlag = "";
        String activityName = "";
        String packageName = "";
        String appName = "";
        String intentUri = "";
        String actionName = "";
        String iconDefault = "";
        String iconDefaultDark = "";
        int order;
        int state;
        String pagePath = "";
        String payUrl = "";

        static AssistantActionRecord fromJson(JSONObject item, int fallbackOrder) {
            if (item == null) {
                return null;
            }
            AssistantActionRecord record = new AssistantActionRecord();
            record.activityName = item.optString("activityName", "").trim();
            record.packageName = item.optString("packageName", "").trim();
            record.appName = item.optString("appName", "").trim();
            record.intentUri = item.optString("intent", "").trim();
            record.actionName = item.optString("actionName", "").trim();
            record.iconDefault = item.optString("iconDefault", "").trim();
            record.iconDefaultDark = item.optString("iconDefaultDark", "").trim();
            record.order = item.optInt("order", fallbackOrder);
            record.state = item.optInt("state", 1);
            record.pagePath = item.optString("pagePath", "").trim();
            record.payUrl = item.optString("payUrl", "").trim();
            record.actionFlag = normalizeAction(item.optString("actionFlag", "").trim());
            if (record.actionFlag.isEmpty() && !record.appName.isEmpty() && !record.actionName.isEmpty()) {
                record.actionFlag = normalizeAction(record.appName + "/" + record.actionName);
            }
            return record;
        }
    }
}
