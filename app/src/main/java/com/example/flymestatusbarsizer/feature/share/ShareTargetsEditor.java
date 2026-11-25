package com.example.flymestatusbarsizer.feature.share;

import android.content.Context;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.ui.ResolverTargetsUiApi;
import com.example.flymestatusbarsizer.ui.TargetsEditorDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ShareTargetsEditor {
    private static final TargetsEditorDialog.Config CONFIG = new TargetsEditorDialog.Config(
            "分享列表管理",
            "分享类型：",
            "搜索应用或分享入口",
            "搜索分享入口",
            "正在扫描分享入口…",
            "正在加载分享入口…",
            "暂时无法加载分享入口\n已保存的设置仍保留",
            "没有显示中的入口\n可在更多中重新扫描",
            "已保存，重新打开分享面板生效",
            "保存分享列表修改？",
            "分享列表选项",
            "排序仅在系统的常用、更多和设备各组内生效",
            "分享列表使用说明",
            "长按图标拖动，或点击入口调整位置、隐藏入口。已隐藏的入口可在“已隐藏”中恢复。\n\n"
                    + "默认跟随系统排序，仅隐藏入口不会固定顺序。实际调整顺序会自动启用自定义列表。"
                    + "排序只在 Flyme 的常用、更多和设备各组内生效。\n\n"
                    + "未单独设置的类型沿用默认，混合或未识别类型使用默认配置。"
                    + "各类型只列出支持该内容的入口，单文件和多文件共用配置。\n\n"
                    + "保存会一次应用所有类型的修改，重新打开分享面板生效。"
                    + "首次使用请在 LSPosed 中启用系统分享范围。"
                    + "Flyme 图库内分享还需勾选图库（com.meizu.media.gallery），并重启对应应用或手机。",
            "分享入口",
            "share-target",
            "搜索应用或分享入口",
            "分享开关"
    );

    private final MainActivity activity;
    private final Executor scanner;
    private final BiFunction<Context, ShareContentType, ShareTargetCatalog.Snapshot> catalog;
    private ShareTargetCatalog.Session scanSession;

    public ShareTargetsEditor(MainActivity activity) {
        this.activity = activity;
        this.scanner = command -> new Thread(command, "share-target-scan").start();
        this.catalog = null;
    }

    ShareTargetsEditor(MainActivity activity, Executor scanner,
            Function<Context, List<ShareTargetCatalog.Target>> catalog) {
        this(activity, scanner, (context, type) -> catalog.apply(context));
    }

    ShareTargetsEditor(MainActivity activity, Executor scanner,
            BiFunction<Context, ShareContentType, List<ShareTargetCatalog.Target>> catalog) {
        this.activity = activity;
        this.scanner = scanner;
        this.catalog = (context, type) -> new ShareTargetCatalog.Snapshot(catalog.apply(context, type), true);
    }

    public void show() {
        new TargetsEditorDialog(activity, scanner, new TargetsEditorDialog.DataSource() {
            @Override
            public List<ResolverTargetsUiApi.ContentType> getContentTypes(Context context) {
                List<ResolverTargetsUiApi.ContentType> result = new ArrayList<>();
                for (ShareContentType type : ShareContentType.values()) {
                    boolean isDefault = type == ShareContentType.DEFAULT;
                    result.add(new ResolverTargetsUiApi.ContentType(
                            type.name(), type.label, isDefault ? "默认" : type.label, isDefault));
                }
                return result;
            }

            @Override
            public void onResetScanSession() {
                scanSession = null;
            }

            @Override
            public ResolverTargetsUiApi.CatalogSnapshot loadTargets(
                    Context context, ResolverTargetsUiApi.ContentType type, BooleanSupplier cancelled) {
                return loadTargets(context, type, cancelled, null);
            }

            @Override
            public ResolverTargetsUiApi.CatalogSnapshot loadTargets(
                    Context context,
                    ResolverTargetsUiApi.ContentType type,
                    BooleanSupplier cancelled,
                    Consumer<ResolverTargetsUiApi.ScanProgress> progress) {
                ShareContentType shareType = ShareContentType.valueOf(type.id);
                if (catalog != null) {
                    if (cancelled != null && cancelled.getAsBoolean()) {
                        throw new CancellationException();
                    }
                    reportProgress(progress, CONFIG.loadingStatus, 0, -1);
                    ShareTargetCatalog.Snapshot found = catalog.apply(context, shareType);
                    if (cancelled != null && cancelled.getAsBoolean()) {
                        throw new CancellationException();
                    }
                    return toUiSnapshot(found);
                }
                if (scanSession == null) {
                    scanSession = new ShareTargetCatalog.Session();
                }
                ShareTargetCatalog.Snapshot found = scanSession.load(
                        context,
                        shareType,
                        cancelled,
                        (current, total, phase) -> reportProgress(progress, phase, current, total));
                return toUiSnapshot(found);
            }

            @Override
            public ResolverTargetsUiApi.SavedState loadState(Context context) {
                ShareTargetRules defaults = ShareTargetRules.parse(
                        SettingsStore.readString(activity.prefs(), SettingsStore.KEY_SHARE_TARGET_ORDER, ""),
                        SettingsStore.readString(activity.prefs(), SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
                ShareTargetProfiles profiles = ShareTargetProfiles.parse(
                        SettingsStore.readString(activity.prefs(), SettingsStore.KEY_SHARE_TARGET_PROFILES, ""));
                Map<String, ResolverTargetsUiApi.Rules> overrides = new LinkedHashMap<>();
                for (ShareContentType type : ShareContentType.values()) {
                    if (type != ShareContentType.DEFAULT && profiles.hasOverride(type)) {
                        overrides.put(type.name(), toUiRules(profiles.rulesFor(type, defaults)));
                    }
                }
                return new ResolverTargetsUiApi.SavedState(toUiRules(defaults), overrides);
            }

            @Override
            public void saveState(Context context, ResolverTargetsUiApi.SavedState state) {
                ShareTargetRules defaults = toShareRules(state.defaultRules);
                ShareTargetProfiles profiles = new ShareTargetProfiles();
                for (ShareContentType type : ShareContentType.values()) {
                    if (type == ShareContentType.DEFAULT) continue;
                    ResolverTargetsUiApi.Rules override = state.typeOverrides.get(type.name());
                    if (override != null) profiles.set(type, toShareRules(override));
                }
                activity.prefs().edit()
                        .putString(SettingsStore.KEY_SHARE_TARGET_PROFILES, profiles.encode())
                        .putString(SettingsStore.KEY_SHARE_TARGET_ORDER, defaults.encodeOrder())
                        .putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, defaults.encodeHidden())
                        .apply();
                SettingsStore.notifyChanged(activity);
            }
        }, CONFIG).show();
    }

    static String targetName(String component, ShareTargetCatalog.Target target) {
        if (target == null) return "不可用入口";
        String label = target.label;
        return label.equals(component) || label.equals(component.substring(component.indexOf('/') + 1))
                ? "分享入口" : label;
    }

    private static ResolverTargetsUiApi.CatalogSnapshot toUiSnapshot(ShareTargetCatalog.Snapshot found) {
        List<ResolverTargetsUiApi.Target> items = new ArrayList<>(found.targets.size());
        for (ShareTargetCatalog.Target target : found.targets) {
            items.add(new ResolverTargetsUiApi.Target(
                    target.component, target.label, target.appName, target.icon, target.appIcon));
        }
        return new ResolverTargetsUiApi.CatalogSnapshot(items, found.systemOrderAvailable);
    }

    private static void reportProgress(
            Consumer<ResolverTargetsUiApi.ScanProgress> progress, String stage, int processed, int total) {
        if (progress != null) progress.accept(new ResolverTargetsUiApi.ScanProgress(stage, processed, total));
    }

    private static ResolverTargetsUiApi.Rules toUiRules(ShareTargetRules rules) {
        return new ResolverTargetsUiApi.Rules(rules.order(), rules.hidden());
    }

    private static ShareTargetRules toShareRules(ResolverTargetsUiApi.Rules rules) {
        return new ShareTargetRules(rules.order, rules.hidden);
    }
}
