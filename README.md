# FlymeStatusBarSizer

`FlymeStatusBarSizer` 是一个面向 Flyme `SystemUI` 的 LSPosed 模块，适配 `API 101`，自带本地设置界面和右侧图标预览。

## 现有功能

- 统一调节右上角系统状态图标大小，并单独调节电池内部数字大小
- 用代码绘制电池图标，支持类 `iOS` 和类 `One UI` 两套样式
- 支持电池内数字开关、数字字体切换、镂空电池，以及“镂空填充随电量变化”
- 用代码绘制 `mobile_signal`，支持单卡与双卡合并显示，并联动网络类型/`5G` 标识判断
- 提供独立的 `Telephony` 调试页，可伪造插卡数量、默认上网卡、两张卡的网络制式和信号等级，方便调试信号图标与 `5G/5GA/5G+`
- 给实时网速增加显示阈值、隐藏阈值，以及连续确认次数
- 支持状态栏时间表达式拼装，当前可组合小时、分钟、秒、星期、`AM/PM`、时段词、十二时辰地支和传统别称
- 支持调整时间字重、时间字号，并同步影响锁屏运营商和网速文字大小
- 可把第三方应用的状态栏通知图标替换为应用自身图标，并单独调节图标尺寸和内边距
- 接管 `mBack` 长按动作，可改为启动自定义 `URL` 或 `Intent URI`，并支持直接测试启动
- 提供 `mBack` 导航栏透明、隐藏小白条、背景抬高（`inset`）和导航栏高度调节
- 可用一个总开关接管输入法控制栏：替换当前控制栏、去掉深灰背景并同步输入法背景
- 替换后的输入法控制栏支持把返回 / 粘贴 / 撤销 / 删除 / 全选 / 复制 / 切换输入法拖到固定 7 槽位里，直接决定显示位置；没拖进去就不显示，点应用按钮后生效
- 输入法控制栏图标支持日间黑色 / 夜间白色，并可调图标大小和透明度
- 支持配置导入、导出、恢复默认和重启 `SystemUI`

## 作用域

- 主要作用于 `com.android.systemui`
- 输入法控制栏相关 Hook 当前作用于 `android`、`com.android.inputmethod.latin`、`com.google.android.inputmethod.latin`、`com.tencent.wetype`、`flyme.inputmethod`

## 实现方式

- 设置界面在 `MainActivity`，配置写入设备保护存储中的 `SharedPreferences`
- `RemoteSettingsSync` 会把本地配置同步到 LSPosed 的 `Remote Preferences`
- `ModuleConfig` 在运行时读取远端配置，并保留最近一次成功配置作为兜底
- `FlymeStatusBarSizer.java` 是 Xposed 入口，当前按状态栏、信号、电池、通知、时间、`mBack`、输入法几组逻辑分别注册 Hook
- 状态栏部分当前主要接管电池绘制、`mobile_signal`、通知图标、时间文字、网速视图和 `mBack` 相关类
- 输入法部分当前直接重建 `NavigationBarInflaterView` 的按钮布局，并把背景同步到输入法内容视图
- 配置变化后通过 `Remote Preferences` 监听刷新运行中的 `SystemUI` 和输入法界面
