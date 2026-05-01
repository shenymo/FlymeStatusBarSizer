# FlymeStatusBarSizer

`FlymeStatusBarSizer` 是一个面向 Flyme / SystemUI 的 Android Xposed 模块，同时带有一个本地设置界面。

它的目标不是简单缩放单个图标，而是把右上角状态栏图标组重新整理成一套更容易调的结构：以电池区域为锚点，把 `Wi-Fi / 移动信号 / 电池` 作为一组视觉图标统一处理，并提供实时预览、导入导出和信号调试能力。

相关文档：

- [HOOK_CHAINS.md](HOOK_CHAINS.md)：Hook 注册点与运行链路说明
- [SYSTEMUI_STATUS_BAR_NOTES.md](SYSTEMUI_STATUS_BAR_NOTES.md)：状态栏相关笔记

## 功能概览

当前版本主要提供以下能力：

- 全局状态栏图标缩放
- 状态栏文字缩放与时钟字重控制
- 自绘 iOS 风格电池图标
- 自绘 iOS 风格 Wi-Fi 图标
- 自绘 iOS 风格移动信号图标
- 将 `Wi-Fi / 移动信号` 视觉上并入电池区域，统一为右上角图标组
- 统一调整图标组内部缩放、间距、起始外间距
- 分场景调整移动信号偏移
  - 桌面
  - 锁屏
  - 控制中心
- 隐藏系统原始网络制式标识
  - 例如 `2G / 3G / 4G / 5G`
- 隐藏系统原始上下行小箭头
  - `mobile_in / mobile_out / wifi_in / wifi_out / mobile_inout`
- 网速文字偏移调整
- 配置导入 / 导出
- 信号格调试页面
  - 通过欺骗 SystemUI 的信号等级读取链路，验证桌面 / 锁屏 / 控制中心的信号格显示

## 当前界面结构

应用侧目前只有两个页面：

### 1. 主页面 `MainActivity`

主页面包含：

- 实时预览区
  - 预览右上角图标组画布
  - 拖动参数后立即看到 `Wi-Fi / 信号 / 电池` 的组合效果
- 全局调整
  - 状态栏整体图标缩放
  - 文字缩放
- 右上角图标组设置
  - 电池页
  - Wi-Fi 页
  - 移动信号页
  - 组内排布页
- 信号调试入口
- 菜单
  - 导入配置
  - 导出配置
  - 恢复默认
  - 重启 SystemUI

### 2. 信号格调试页 `SignalDebugActivity`

这个页面用于验证模块是否正确跟随 SystemUI 的信号状态链路：

- 启用假信号等级
- 分别控制 SIM1 / SIM2 是否参与测试
- 分别设置 SIM1 / SIM2 的假信号格数
- 查看 SystemUI 进程回传的运行时 Hook 状态
- 一键恢复真实信号

## 项目结构

核心文件如下：

- [app/src/main/java/com/example/flymestatusbarsizer/FlymeStatusBarSizer.java](app/src/main/java/com/example/flymestatusbarsizer/FlymeStatusBarSizer.java)
  - Xposed 模块主入口
  - 负责 Hook `com.android.systemui`
  - 负责读取配置并作用到运行中的 SystemUI 视图

- [app/src/main/java/com/example/flymestatusbarsizer/MainActivity.java](app/src/main/java/com/example/flymestatusbarsizer/MainActivity.java)
  - 主设置页
  - 包含实时预览、图标组设置、导入导出、恢复默认等

- [app/src/main/java/com/example/flymestatusbarsizer/SignalDebugActivity.java](app/src/main/java/com/example/flymestatusbarsizer/SignalDebugActivity.java)
  - 信号格调试页

- [app/src/main/java/com/example/flymestatusbarsizer/SettingsStore.java](app/src/main/java/com/example/flymestatusbarsizer/SettingsStore.java)
  - 所有配置 key 与默认值的唯一来源
  - 同时维护 `INT_KEYS`、`BOOLEAN_KEYS`、`defaultInt()`、`defaultBoolean()`

- [app/src/main/java/com/example/flymestatusbarsizer/SettingsProvider.java](app/src/main/java/com/example/flymestatusbarsizer/SettingsProvider.java)
  - 通过 `content://` 向 SystemUI/Xposed 侧暴露配置

- [app/src/main/java/com/example/flymestatusbarsizer/IosBatteryPainter.java](app/src/main/java/com/example/flymestatusbarsizer/IosBatteryPainter.java)
  - 自绘电池图标

- [app/src/main/java/com/example/flymestatusbarsizer/IosWifiDrawable.java](app/src/main/java/com/example/flymestatusbarsizer/IosWifiDrawable.java)
  - 自绘 Wi-Fi 图标

- [app/src/main/java/com/example/flymestatusbarsizer/IosSignalDrawable.java](app/src/main/java/com/example/flymestatusbarsizer/IosSignalDrawable.java)
  - 自绘移动信号图标

- [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)
  - 注册应用入口、调试页与 `SettingsProvider`

## 配置流转

配置流转路径如下：

```text
MainActivity / SignalDebugActivity
  -> SettingsStore.prefs(...)
  -> 设备保护存储 SharedPreferences
  -> SettingsProvider
  -> FlymeStatusBarSizer.Config.load(...)
  -> SystemUI 运行时 Hook 逻辑
```

配置读取使用的 URI：

```text
content://com.fiyme.statusbarsizer.settings/settings
```

如果新增一个配置项，至少要同步更新这些位置：

1. `SettingsStore.java`
2. 对应设置页面写入逻辑
3. `SettingsProvider.java`
4. `FlymeStatusBarSizer.Config`
5. 实际使用这个配置的运行时逻辑

## 当前设计方向

当前版本已经不再沿用早期那种“电池 / Wi-Fi / 信号 / 网络制式分别独立调”的方式，而是收敛到了“右上角图标组”思路：

- 视觉上把 `Wi-Fi / 移动信号` 并入电池区域
- 外部原始 `wifi_signal` / `mobile_signal` 视图在需要时会被隐藏
- 系统原始 `mobile_type` 会被折叠隐藏
- 组内统一缩放、统一高度、统一排布
- 锁屏、控制中心、桌面等场景继续保留必要的移动信号偏移控制

## 运行时行为说明

模块只在 `com.android.systemui` 首次加载时生效。

主要运行时处理包括：

- Hook 状态栏 Wi-Fi / 移动信号视图构建过程
- Hook 电池绘制与测量
- Hook `ImageView` 的资源更新与 tint 更新
- 持续跟踪 `mobile_signal`、`wifi_signal`、电池视图和状态栏文字
- 配置变化后通过 `ContentObserver` 触发刷新
- 在信号调试模式下，拦截 `SignalStrength / CellSignalStrength` 等等级读取链路

## 已收敛 / 已移除的旧设计

当前代码与文档都不再保留以下旧体系：

- 旧的 `network_type` / `mobile_type` 独立配置链
- `2G / 3G / 4G / 5G` 文案或样式独立设置页
- `iosBatteryStyle / iosSignalStyle / iosWifiStyle` 三个旧开关
- `*_OFF` 双配置档体系
- 旧的多 Activity 设置页拆分

导入配置也已经按现有结构收敛，不再兼容旧配置格式中的这些旧 key。

## 维护建议

- 修改默认值时，优先改 [SettingsStore.java](app/src/main/java/com/example/flymestatusbarsizer/SettingsStore.java)
- 修改预览区时，主入口在 [MainActivity.java](app/src/main/java/com/example/flymestatusbarsizer/MainActivity.java) 的 `RightIconGroupPreviewView`
- 修改右上角图标组合并逻辑时，主入口在 [FlymeStatusBarSizer.java](app/src/main/java/com/example/flymestatusbarsizer/FlymeStatusBarSizer.java)
- 如果发现某个设置项 UI 改了但 SystemUI 不生效，优先检查 `SettingsProvider -> Config.load -> 实际使用点`
- 如果发现锁屏 / 控制中心 / 桌面表现不一致，优先检查场景识别与信号偏移逻辑，而不是先怀疑预览区

## 说明

这是一个偏运行时重绘与 Hook 的项目，很多问题不是单纯的布局问题，而是：

- SystemUI 视图创建时机
- 资源更新时机
- Flyme 与 AOSP 不同视图实现
- 锁屏 / 控制中心 / 桌面三类上下文差异

因此改动时应尽量先确认：

1. 目标视图是谁
2. 它在哪个场景下创建
3. 它是通过资源更新、Drawable 更新还是 draw/measure 被影响
4. 配置改动后是否有刷新路径
