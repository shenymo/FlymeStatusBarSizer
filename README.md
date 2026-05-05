# FlymeStatusBarSizer

`FlymeStatusBarSizer` 是一个给 Flyme `SystemUI` 用的 LSPosed 模块，带本地设置界面。

## 功能

- 统一调整状态栏图标大小
- 替换电池图标为代码绘制样式，当前有类 iOS 和类 One UI 两种，并可控制电池内数字与字体
- 替换移动信号图标为代码绘制样式，双卡时合并显示，只保留一个信号位
- 给实时网速加显隐阈值和连续确认次数
- 支持用按钮组合自定义状态栏时间表达式，并可调整时间字重、时间和锁屏运营商字体大小
- 接管 `mBack` 长按动作，可改为启动自定义 `URL` 或 `Intent URI`
- 提供 `mBack` 导航栏透明、隐藏小白条、底部 `inset`、导航栏高度这些试验项
- 可把通知卡片背景改成透明或液态玻璃效果
- 可在输入法内容区下方加入工具栏，包含粘贴、删除、全选、复制、切换输入法，并支持调整按钮顺序
- 支持配置导入、导出、重置和重启 `SystemUI`

## 实现方式

- 设置界面在 `MainActivity`，配置写入设备保护存储里的 `SharedPreferences`
- `SettingsProvider` 把配置以 `content://com.fiyme.statusbarsizer.settings/settings` 提供给 `SystemUI`
- `ModuleConfig` 在运行时读取并缓存配置
- `FlymeStatusBarSizer.java` 是 Xposed 入口，主要 Hook `com.android.systemui`，少量功能还会 Hook `InputMethodService`
- 状态栏部分会接管电池绘制、移动信号图标、时间文字、网速视图和部分 `mBack` 相关类
- 通知卡片的背景层会按系统版本切成透明或液态玻璃视图
- 输入法工具栏是在输入法输入视图下方补一层自定义按钮栏
- 配置变化后通过 `ContentObserver` 通知运行中的 `SystemUI` 和输入法刷新
