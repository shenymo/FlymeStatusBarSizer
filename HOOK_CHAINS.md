# FlymeStatusBarSizer Hook 链路总览

这份文档只总结模块真正依赖的 hook 链路。

结论先说：

- 这个 App 的设置界面本身不做 SystemUI 改造，主要负责写入 `SharedPreferences`。
- 真正生效的核心逻辑基本都在 `app/src/main/java/com/example/flymestatusbarsizer/FlymeStatusBarSizer.java`。
- 模块通过 Xposed 在 `com.android.systemui` 进程中注册 hook，然后把设置转换成运行时的 View、Drawable、文本、信号状态修改。

## 总体链路

总体数据流是：

```text
MainActivity / 其他设置页
  -> SettingsStore.prefs(...)
  -> device-protected SharedPreferences
  -> SettingsProvider.query()
  -> FlymeStatusBarSizer.Config.load()
  -> Xposed hook 拦截 SystemUI 构造、绑定、绘制、状态更新
  -> 修改 View / Drawable / Text / Telephony 状态
```

SystemUI hook 注册入口：

- `FlymeStatusBarSizer.onPackageLoaded(...)`
- 只在包名为 `com.android.systemui` 且 `isFirstPackage()` 时注册

## Hook 注册清单

`onPackageLoaded(...)` 当前会注册这些链路：

1. `hookConstructAndBind(...)`
2. `hookFlymeSlotIndexUpdates(...)`
3. `hookTelephonyDebugSignals(...)`
4. `hookSignalIconModelDebugStates(...)`
5. `hookFlymeWifiView(...)`
6. `hookConnectionRateView(...)`
7. `hookImageViewTintUpdates(...)`
8. `hookSignalDrawableLevels(...)`
9. `hookConstructors(..., "com.android.systemui.statusbar.StatusBarIconView", ...)`
10. `hookConstructors(..., "com.flyme.statusbar.battery.FlymeBatteryMeterView", ...)`
11. `hookFlymeBatteryMeterViewDraw(...)`
12. `hookFlymeBatteryMeterViewMeasure(...)`
13. `hookConstructors(..., "com.flyme.statusbar.battery.FlymeBatteryTextView", ...)`
14. `hookBatteryDrawable(...)`
15. `hookStatusTextView(..., Clock)`
16. `hookClockWeekday(...)`
17. `hookStatusTextView(..., OperatorNameView)`
18. `hookStatusTextView(..., CarrierText)`
19. `hookStatusTextView(..., AutoMarqueeTextView)`
20. `hookConstructors(..., KeyguardStatusBarView, ...)`
21. `hookConstructors(..., KeyguardBouncerStatusBarView, ...)`
22. `hookConstructors(..., ShadeCarrier, ...)`

## 1. 移动信号主链路

这是最核心的一条链路，负责信号格缩放、iOS 信号格替换、双卡合一、5G 标识联动。

### 1.1 View 创建/绑定入口

Hook 目标：

- `com.flyme.systemui.statusbar.net.mobile.ui.view.FlymeModernStatusBarMobileView.constructAndBind(...)`
- `com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView.constructAndBind(...)`
- `com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernShadeCarrierGroupMobileView.constructAndBind(...)`

Hook 后动作：

- 调用 `registerMobileViewSlot(view)`
- 调用 `applyStatusBarSizing(view)`

后续效果：

- 识别主卡/副卡槽位
- 记录 `mobile_signal` 当前对应的 `subId`
- 应用移动信号图标缩放
- 应用 iOS 信号格替换
- 应用网络类型标签缩放和位移
- 隐藏上下行箭头

### 1.2 `mobile_signal` 资源/Drawable 状态入口

Hook 目标：

- `ImageView.setImageResource(...)`
- `ImageView.setImageDrawable(...)`
- `SignalDrawable.onLevelChange(int)`

其中 `mobile_signal` 相关的链路是：

```text
SystemUI 给 mobile_signal 设置资源或 Drawable
  -> hookImageViewTintUpdates(...)
  -> applyMobileSignalResource(...) 或记录 Drawable owner
  -> 后续 SignalDrawable.onLevelChange(...)
  -> applyMobileSignalDrawableState(...)
  -> applyMobileSignalInfo(...)
  -> applyIosSignalImageView(...)
  -> 最终替换为 IosSignalDrawable
```

关键点：

- `applyMobileSignalResource(...)` 会从资源名解析信号等级
- `applyMobileSignalDrawableState(...)` 会从 `SignalDrawable` 的 level state 解析真实等级
- `applyMobileSignalInfo(...)` 会统一处理主副卡、双卡合一、视图隐藏/显示、缓存和重绘
- 主卡视图可能会带上副卡四个小点，最终由 `updatePrimarySignalDrawables()` 汇总更新

### 1.3 SIM 槽位纠偏入口

Hook 目标：

- `MobileIconsViewModel.handleLatestVmFlymeSlotIndexChanged(int subId, int slotIndex)`

链路：

```text
Flyme VM 更新 subId -> slotIndex
  -> recordFlymeSlotIndex(...)
  -> refreshTrackedSignalViews(true)
```

作用：

- 修正 `subId` 到主卡/副卡槽位的映射
- 解决仅靠 view 名称或物理槽位判断不稳定的问题

## 2. Telephony 调试信号链路

这组 hook 的目标不是单纯改 View，而是“劫持 SystemUI 读取信号等级的过程”，让调试页能注入假信号。

### 2.1 TelephonyManager 链路

Hook 目标：

- `TelephonyManager.createForSubscriptionId(int)`
- `TelephonyManager.getSignalStrength()`

作用：

- 记录新建的 `TelephonyManager` 对应哪个 `subId`
- 记录 `SignalStrength` 对象对应哪个 `subId`

### 2.2 SignalStrength 链路

Hook 目标：

- `SignalStrength.getLevel()`
- `SignalStrength.getCellSignalStrengths()`

作用：

- 在调试模式下把 `getLevel()` 的返回值替换成假等级
- 遍历 cell 列表时，把 `CellSignalStrength` 也绑到同一个 `subId`

### 2.3 CellSignalStrength 子类链路

Hook 目标：

- `CellSignalStrengthCdma.getLevel()`
- `CellSignalStrengthGsm.getLevel()`
- `CellSignalStrengthWcdma.getLevel()`
- `CellSignalStrengthTdscdma.getLevel()`
- `CellSignalStrengthLte.getLevel()`
- `CellSignalStrengthNr.getLevel()`

作用：

- 在调试模式下继续兜底替换各制式单独返回的等级

### 2.4 SignalIconModel 链路

Hook 目标：

- `SignalIconModel$Cellular.toSignalDrawableState()`

链路：

```text
SystemUI 生成 SignalDrawable state
  -> hookSignalIconModelDebugStates(...)
  -> buildSignalDrawableState(...)
  -> 返回伪造后的 state
```

作用：

- 当 SystemUI 直接走 domain model 生成 drawable state 时，也能注入假信号

### 2.5 调试信号回写链路

运行中会配合这些非 hook 逻辑一起生效：

- `applyDebugSignalDrawableStates(...)`
- `reportSignalDebug(...)`
- `reportCurrentTrackedSignalState(...)`
- `SettingsProvider.update(...)`

结果：

- 调试页可以看到当前 hook 是否命中
- 可以看到来源、slot、subId、state、level 和错误信息

## 3. Wi-Fi 链路

### 3.1 Flyme Wi-Fi View 生命周期入口

Hook 目标：

- `com.flyme.systemui.statusbar.net.wifi.FlymeStatusBarWifiView.fromContext(...)`
- `initViewState(...)`
- `updateState(...)`
- `applyWifiState(...)`

链路：

```text
Flyme Wi-Fi View 创建/更新状态
  -> hookFlymeWifiView(...)
  -> applyWifiSizing(view)
  -> applyFlymeWifiStateResource(view, state)
  -> applyIosWifiImageView(...)
```

作用：

- 给 Wi-Fi 图标做缩放
- 从 Flyme 的 state 对象里拿 `resId`
- 解析 Wi-Fi 资源名中的强度等级
- 用 `IosWifiDrawable` 替换原图标

### 3.2 通用 ImageView Wi-Fi 资源入口

Hook 目标：

- `ImageView.setImageResource(...)`
- `ImageView.setImageDrawable(...)`

当 `idName == "wifi_signal"` 时：

- `applyWifiSignalResource(...)`
- `applyIosWifiImageView(...)`
- `applyIosWifiLayout(...)`

作用：

- 即使没有命中 Flyme 专有 view，也能从通用 `ImageView` 链路改掉 Wi-Fi 图标

## 4. 网络类型 / 5G 标识链路

### 4.1 `mobile_type` 资源入口

Hook 目标：

- `ImageView.setImageResource(...)`

当 `idName == "mobile_type"` 时：

```text
SystemUI 设置 mobile_type 资源
  -> applyNetworkTypeResource(...)
  -> getNetworkTypeLabel(...)
  -> rememberNetworkTypeLabelForSubId(...)
  -> applyNetworkTypeLabel(...)
  -> 替换为 NetworkTypeDrawable
```

当前策略：

- 只识别并统一输出 `5G`
- 例如各种 `5g`、`5g_plus`、`5g_a` 最终都归一成 `5G`
- 非活动数据卡或双卡合一副卡会被隐藏

### 4.2 监听网络类型变化

这条链路不直接来自 View hook，而是来自 Telephony 监听：

- `PhoneStateListener.onDisplayInfoChanged(...)`
- `PhoneStateListener.onDataConnectionStateChanged(...)`

触发后：

- `invalidateNetworkTypeCache()`
- `scheduleNetworkTypeRefresh()`
- `refreshTrackedNetworkTypeViews()`

作用：

- 5G/非 5G 切换时，已跟踪的 `mobile_type` 视图会被刷新

## 5. 电池链路

### 5.1 电池 View 构造入口

Hook 目标：

- `FlymeBatteryMeterView` 所有构造函数

作用：

- 标准化电池间距
- 关闭父布局裁剪
- 调整 iOS 电池尺寸
- 按配置缩放整个电池视图

### 5.2 BatteryMeterDrawable 入口

Hook 目标：

- `BatteryMeterDrawable` 所有构造函数
- `BatteryMeterDrawable.draw(Canvas)`

链路：

```text
BatteryMeterDrawable 构造
  -> applyIosBatteryStyleIfNeeded(...)

BatteryMeterDrawable.draw(...)
  -> drawIosBatteryIfNeeded(...)
  -> 若启用 iOS 电池则直接改为自绘
```

作用：

- 改黑底、白字、白色闪电等配色
- 完全接管原有 drawable 绘制

### 5.3 FlymeBatteryMeterView 绘制/测量入口

Hook 目标：

- `FlymeBatteryMeterView.onDraw(Canvas)`
- `FlymeBatteryMeterView.onMeasure(int, int)`

作用：

- `onDraw` 时直接调用 `drawIosBatteryViewIfNeeded(...)`
- `onMeasure` 时调用 `measureIosBatteryViewIfNeeded(...)`
- 保证自绘 iOS 电池的宽高和充电态额外空间正确

### 5.4 电池百分比文字入口

Hook 目标：

- `FlymeBatteryTextView` 所有构造函数

作用：

- 应用文字缩放
- iOS 电池风格启用时把文字颜色强制设为白色

## 6. 文字链路

### 6.1 状态栏文字构造入口

Hook 目标：

- `Clock` 构造函数
- `OperatorNameView` 构造函数
- `CarrierText` 构造函数
- `AutoMarqueeTextView` 构造函数

链路：

```text
TextView 构造完成
  -> hookStatusTextView(...)
  -> trackStatusTextView(...)
  -> ensureConfigRefreshObserver(...)
  -> applyTextScale(...)
```

作用：

- 跟踪时钟、运营商、锁屏运营商、电池百分比等状态栏文字
- 统一应用 `text_scale`
- 配置变更后可批量重刷

### 6.2 自定义时间表达式入口

Hook 目标：

- `Clock.getSmallTime()`

链路：

```text
SystemUI 生成时间文本
  -> hookClockWeekday(...)
  -> buildCustomClockText(...)
  -> 返回“自定义表达式结果”或系统原始时间
```

说明：

- 当前实现优先渲染模块自定义时间表达式
- 只对 `idName == "clock"` 的状态栏主时钟生效

### 6.3 时间加粗链路

这不是单独的 method hook，而是复用文字构造链路：

- `hookStatusTextView(...)` 命中 `Clock`
- `applyTextScale(...)`
- `applyClockFontWeight(...)`

作用：

- 只对 `clock` 文本应用字重
- 关闭开关时恢复原始 `Typeface`
- 因为星期/日期是追加到同一个 `TextView` 上，所以会一起变粗

## 7. 网络速度链路

Hook 目标：

- `ConnectionRateView.dispatchDraw(Canvas)`
- `ConnectionRateView.onAttachedToWindow()`
- `ConnectionRateView.onConfigurationChanged(...)`

链路：

```text
ConnectionRateView 附着/配置变化
  -> trackConnectionRateView(...)
  -> ensureConfigRefreshObserver(...)
  -> applyConnectionRateTextScale(...)
  -> applyConnectionRateOffset(...)

ConnectionRateView.dispatchDraw(...)
  -> 重新应用 text scale / offset
  -> 计算对齐偏移
  -> Canvas.translate(...)
  -> 再执行原始 draw
```

作用：

- 缩放网速数字和单位
- 处理横向偏移
- 处理“和电池或参考图标底部对齐”的纵向绘制偏移

## 8. 通用 ImageView 链路

Hook 目标：

- `ImageView.setImageTintList(...)`
- `ImageView.setColorFilter(...)`
- `ImageView.setImageTintMode(...)`
- `ImageView.setImageResource(...)`
- `ImageView.setImageDrawable(...)`

这条 hook 是全局兜底入口，很多功能都从这里分流：

- `mobile_signal`：信号资源解析、iOS 信号格替换、Drawable owner 记录、尺寸同步
- `mobile_type`：5G 标签替换、参考场景尺寸同步
- `wifi_signal`：Wi-Fi 等级解析、iOS Wi-Fi 替换
- `mobile_in/mobile_out/wifi_in/wifi_out/mobile_inout`：隐藏活动箭头
- 对 `IosSignalDrawable` / `NetworkTypeDrawable` / `IosWifiDrawable` 同步 tint

它相当于是本模块最重要的“统一图标入口”。

## 9. 其他状态栏图标链路

Hook 目标：

- `StatusBarIconView` 所有构造函数

作用：

- 统一对普通状态栏图标应用 `statusIconFactor`
- 这是除移动信号、Wi-Fi、电池以外的通用图标缩放入口

## 10. 参考场景容器链路

Hook 目标：

- `KeyguardStatusBarView` 构造函数
- `KeyguardBouncerStatusBarView` 构造函数
- `ShadeCarrier` 构造函数

作用：

- 关闭父布局和子布局裁剪
- 执行 `applyReferenceSignalSizing(...)`
- 让锁屏、Bouncer、控制中心等“参考尺寸场景”里的信号格和网络类型标签按单独偏移规则布局

## 11. 配置刷新链路

这条链路不是直接 hook 某个 SystemUI method，但它决定了 hook 生效后怎么“热更新”。

入口：

- `hookStatusTextView(...)`
- `hookConnectionRateView(...)`

这两个入口都会调用：

- `ensureConfigRefreshObserver(...)`

随后注册：

- `ContentObserver` 监听 `content://.../settings`
- `ACTION_USER_UNLOCKED` 广播
- SIM/默认数据卡/多卡配置变更广播

刷新动作：

- `refreshTrackedTextScaling()`
- `refreshTrackedSignalViews(...)`
- `refreshTrackedNetworkTypeViews()`
- 调试信号模式切换时的 `applyDebugSignalDrawableStates(...)`

可以理解为：

```text
设置页改配置
  -> SettingsProvider.notifyChange / query
  -> SystemUI ContentObserver 收到通知
  -> 失效 Config 缓存
  -> 批量重刷已跟踪文本 / 信号 / 网络类型视图
```

## 12. 当前未接入的备用 hook

代码里还有一个方法：

- `hookDrawableLevels()`

它会 hook：

- `Drawable.setLevel(int)`

作用本来是：

- 如果 `SignalDrawable.onLevelChange(...)` 没命中，还能从更底层的 `Drawable.setLevel(...)` 拿到信号 state

但截至当前版本，这个方法虽然实现了，`onPackageLoaded(...)` 里并没有调用它。

所以当前实际生效的是：

- `hookSignalDrawableLevels(...)`

而不是：

- `hookDrawableLevels()`

## 13. 一句话判断这个 App 的本质

可以把这个项目拆成两部分看：

- App 进程：设置页、导入导出、调试页、重启 SystemUI 按钮
- SystemUI 进程：Xposed hook、状态读取、View/Drawable 替换、绘制接管、调试注入

所以从实现本质上说，这个 App 的核心功能确实主要是 hook。

如果后续继续加功能，通常都要同时检查这几层：

1. 设置页有没有写入 key
2. `SettingsProvider` 有没有把 key 暴露给 SystemUI
3. `Config.load()` 有没有读到这个 key
4. 有没有现成 hook 链路可以复用
5. 如果没有，是否要新增新的构造、绘制、状态更新或 telephony 读取 hook
