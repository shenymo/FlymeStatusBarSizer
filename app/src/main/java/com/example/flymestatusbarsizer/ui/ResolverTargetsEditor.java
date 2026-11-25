package com.example.flymestatusbarsizer.ui;

import android.content.Context;

import com.example.flymestatusbarsizer.MainActivity;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Editor entry for ordinary Resolver ("打开方式") candidate apps by content type. */
public final class ResolverTargetsEditor {
    private static final TargetsEditorDialog.Config CONFIG = new TargetsEditorDialog.Config(
            "打开方式管理",
            "打开类型：",
            "搜索应用或打开入口",
            "搜索打开方式入口",
            "正在扫描打开方式…",
            "正在加载打开方式…",
            "暂时无法加载打开方式\n已保存的设置仍保留",
            "没有可用的打开方式\n可在更多中重新扫描",
            "已保存，重新打开“打开方式”生效",
            "保存打开方式修改？",
            "打开方式选项",
            "排序将按当前顺序应用到“打开方式”弹窗",
            "打开方式使用说明",
            "长按图标拖动，或点击入口调整位置、隐藏入口。已隐藏的入口可在“已隐藏”中恢复。\n\n"
                    + "默认跟随系统排序，仅隐藏入口不会固定顺序。实际调整顺序会自动启用自定义排序。\n\n"
                    + "未单独设置的类型沿用默认配置；仅作用于普通“打开方式”弹窗，不影响“分享面板”。\n\n"
                    + "保存会一次应用所有类型的修改，重新打开“打开方式”弹窗生效。",
            "打开入口",
            "resolver-target",
            "搜索应用或打开入口",
            "打开方式开关"
    );

    private final MainActivity activity;
    private final ResolverTargetsUiApi.Provider provider;
    private final Executor scanner;

    public ResolverTargetsEditor(MainActivity activity) {
        this(activity, ResolverTargetsUiApi.getProvider());
    }

    public ResolverTargetsEditor(MainActivity activity, ResolverTargetsUiApi.Provider provider) {
        this.activity = activity;
        this.provider = provider != null ? provider : ResolverTargetsUiApi.getProvider();
        this.scanner = command -> new Thread(command, "resolver-target-scan").start();
    }

    public void show() {
        new TargetsEditorDialog(activity, scanner, new TargetsEditorDialog.DataSource() {
            @Override
            public List<ResolverTargetsUiApi.ContentType> getContentTypes(Context context) {
                return provider.getContentTypes(context);
            }

            @Override
            public ResolverTargetsUiApi.CatalogSnapshot loadTargets(
                    Context context, ResolverTargetsUiApi.ContentType type, BooleanSupplier cancelled) {
                return provider.loadTargets(context, type, cancelled);
            }

            @Override
            public ResolverTargetsUiApi.SavedState loadState(Context context) {
                return provider.loadRules(context);
            }

            @Override
            public void saveState(Context context, ResolverTargetsUiApi.SavedState state) {
                provider.saveRules(context, state);
            }
        }, CONFIG).show();
    }
}
