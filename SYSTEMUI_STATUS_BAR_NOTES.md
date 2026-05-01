# SystemUI 状态栏结构与图标速查

这份文档用于快速接手当前工作区里的 `systemui_apktool` 反编译结果，重点回答：

- `battery` 和 `statusIcons` 的层级关系
- `statusIcons` 里大致有哪些图标
- 这些图标的来源分布在哪里
- 已经提取出的本地状态栏图标目录在哪里

## 关键文件

- [systemui_apktool/res/layout/status_bar.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar.xml:24)
- [systemui_apktool/res/layout/system_icons.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/system_icons.xml:2)
- [systemui_apktool/res/layout/status_bar_mobile_signal_group_inner.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar_mobile_signal_group_inner.xml:4)
- [systemui_apktool/res/layout/status_bar_wifi_group_inner.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar_wifi_group_inner.xml:4)
- [systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:105)
- [systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/StatusBarSignalPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/StatusBarSignalPolicy.smali:54)
- [systemui_apktool/smali_classes5/com/flyme/systemui/statusbar/net/wifi/FlymeWifiSignalPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes5/com/flyme/systemui/statusbar/net/wifi/FlymeWifiSignalPolicy.smali:21)

## 1. 外层结构结论

右侧状态栏通过 [status_bar.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar.xml:25) 引入 [system_icons.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/system_icons.xml:2)。

`system_icons` 这一层的固定顺序可以直接确认：

1. `connection_rate`
2. `statusIcons`
3. `battery`
4. `battery_percent`

对应位置：

- `connection_rate` 见 [system_icons.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/system_icons.xml:4)
- `statusIcons` 见 [system_icons.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/system_icons.xml:5)
- `battery` 见 [system_icons.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/system_icons.xml:6)
- `battery_percent` 见 [system_icons.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/system_icons.xml:7)

直接结论：

- `battery` 在 `statusIcons` 外面。
- `battery` 不是包了一层额外容器的布局块，而是 `system_icons` 下的独立兄弟 View。
- `battery_percent` 也不在 `statusIcons` 里。

## 2. `battery` 是什么

`battery` 本体是：

- `com.flyme.statusbar.battery.FlymeBatteryMeterView`

也就是说它是一个独立的自定义 View，不是 `StatusIconContainer` 内部的普通 slot 图标。

电池相关图标资源由 `FlymeBatteryMeterView` 自己管理和切换，见：

- [FlymeBatteryMeterView.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes5/com/flyme/statusbar/battery/FlymeBatteryMeterView.smali:331)

已确认会用到的本地电池资源包括：

- `stat_sys_battery_normal`
- `stat_sys_battery_normal_level_inside`
- `stat_sys_battery_normal_level_inside_whitebg`
- `stat_sys_battery_charge`
- `stat_sys_battery_super_charge`
- `stat_sys_battery_low`
- `stat_sys_battery_low_power_mode`
- `stat_sys_battery_low_power_mode_level_inside`
- `stat_sys_battery_unknown`
- `stat_sys_battery_bypass`
- `stat_sys_battery_bypass_level_inside`
- `stat_sys_battery_bypass_level_inside_whitebg`
- `stat_sys_battery_bypass_low_power_mode`
- `stat_sys_battery_plugged`
- `stat_sys_battery_plugged_level_inside`

## 3. `statusIcons` 里大致包含什么

`statusIcons` 本身是 `StatusIconContainer`。它不是只放 Wi‑Fi 和移动信号，而是承载右侧大多数“状态类 slot 图标”。

### 3.1 从 policy 能确认的 slot

`PhoneStatusBarPolicy` 管理的典型 slot：

- `alarm_clock`
- `bluetooth`
- `camera`
- `cast`
- `connected_display`
- `data_saver`
- `headset`
- `hotspot`
- `location`
- `managed_profile`
- `microphone`
- `mute`
- `nfc`
- `rotate`
- `screen_record`
- `sensors_off`
- `tty`
- `vibrate`
- `zen`

来源见 [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:105)。

`StatusBarSignalPolicy` 管理的典型网络 slot：

- `airplane`
- `ethernet`
- `mobile`
- `vpn`

来源见 [StatusBarSignalPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/StatusBarSignalPolicy.smali:54)。

`FlymeWifiSignalPolicy` 额外管理：

- `wifi`
- `dual_wifi`

来源见 [FlymeWifiSignalPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes5/com/flyme/systemui/statusbar/net/wifi/FlymeWifiSignalPolicy.smali:21)。

### 3.2 粗略顺序怎么理解

需要区分两层：

- `system_icons` 这一层的 XML 顺序是固定的。
- `statusIcons` 内部具体左右顺序不是完全写死在这几个 XML 里，而是由 `StatusBarIconList` 初始化 slot 后，再由各个 policy 动态更新。

`StatusBarIconList` 来自系统字符串数组初始化，见：

- [CentralSurfacesDependenciesModule.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/dagger/CentralSurfacesDependenciesModule.smali:123)
- [StatusBarIconList.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/ui/StatusBarIconList.smali:13)

所以现在能安全下结论的是：

- 固定外层顺序：`connection_rate -> statusIcons -> battery -> battery_percent`
- `statusIcons` 里通常会出现：`vpn / ethernet / airplane / wifi / dual_wifi / mobile / no_sims / volte_or_vowifi / bluetooth / alarm / nfc / dnd / mute / vibrate / tty / hotspot / location / cast / connected_display / data_saver / sensors_off / screen_record / camera / microphone / managed_profile`
- 但这些 slot 在 `statusIcons` 容器内的精确最终顺序，仍然要看初始化字符串数组和运行时控制逻辑，不能只靠当前几个布局 XML 百分百锁死

## 4. Wi‑Fi 和移动信号组内部顺序

### 4.1 移动信号组

[status_bar_mobile_signal_group_inner.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar_mobile_signal_group_inner.xml:4) 的内部顺序大致是：

1. `mobile_volte`
2. `mobile_in`
3. `mobile_out`
4. `mobile_type_container` / `mobile_type`
5. `mobile_roaming_space`
6. `mobile_signal`
7. `mobile_roaming`

也就是说 5G/4G 这类网络类型标识和主信号格确实不是同一个 View。

### 4.2 Wi‑Fi 组

[status_bar_wifi_group_inner.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar_wifi_group_inner.xml:4) 的内部顺序大致是：

1. `wifi_in`
2. `wifi_out`
3. `wifi_signal`
4. `wifi_signal_spacer`
5. `connected_device_signals_stub`
6. `wifi_airplane_spacer`

## 5. 已确认的本地状态栏图标来源

### 5.1 直接在布局里引用

- `ic_sysbar_lights_out_dot_small`
- `ic_activity_down`
- `ic_activity_up`
- `stat_sys_roaming`

来源：

- [status_bar.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar.xml:4)
- [status_bar_mobile_signal_group_inner.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar_mobile_signal_group_inner.xml:7)
- [status_bar_wifi_group_inner.xml](/mnt/c/Users/22/Downloads/312/systemui_apktool/res/layout/status_bar_wifi_group_inner.xml:6)

### 5.2 `PhoneStatusBarPolicy` 明确设置的本地资源

已直接确认的资源名包括：

- `stat_sys_nfc`
- `stat_sys_alarm`
- `stat_sys_alarm_dim`
- `stat_sys_dnd`
- `stat_sys_ringer_vibrate`
- `stat_sys_ringer_silent`
- `stat_sys_cast`
- `stat_sys_connected_display`
- `stat_sys_location`
- `stat_sys_sensors_off`
- `stat_sys_screen_record`
- `stat_sys_data_bluetooth_connected`
- `bt_status_download`
- `bt_status_upload`
- `stat_sys_headset_with_mic`
- `stat_sys_headset_without_mic`
- `stat_sys_wifi_6_hotspot`
- `stat_sys_wifi_5_hotspot`
- `stat_sys_wifi_4_hotspot`
- `stat_sys_hotspot`
- `stat_sys_tty_mode`

关键位置：

- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:1005)
- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:2228)
- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:2501)
- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:2551)
- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:3052)

### 5.3 `StatusBarSignalPolicy` 和 `FlymeWifiSignalPolicy`

这两类更多是拿“动态图标 ID”或状态对象：

- `vpn` 用 `currentVpnIconId(...)` 动态决定
- `ethernet` 直接吃 `IconState.icon` 或 `Icon.Resource`
- `airplane` 用 `TelephonyIcons.FLIGHT_MODE_ICON`
- `wifi` 和 `dual_wifi` 多数情况下直接吃 `WifiIndicators.statusIcon.icon`

也就是说：

- 一部分资源就在 `SystemUI` 的 `res/drawable*`
- 一部分来自 `settingslib` / pipeline / framework 内部常量
- 这类“只拿到整型 ID，且当前 APK 里无法直接反查资源名”的图标，不一定能仅靠本地文件 100% 还原

### 5.4 隐私图标说明

`camera` 和 `microphone` slot 是通过 `PrivacyType.getIconId()` 传给 `StatusBarIconController` 的，见：

- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:1261)
- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:1303)

这类图标不一定直接以 `stat_sys_*` 命名存在于当前 APK 里，所以提取目录里可能没有与它们一一对应的本地文件。

## 6. 图标提取目录

已提取的本地状态栏图标目录：

- [systemui_statusbar_icon_dump](/mnt/c/Users/22/Downloads/312/systemui_statusbar_icon_dump)

提取规则：

- 保留原始密度目录结构，例如 `drawable-xhdpi`、`drawable-xxhdpi`
- 主要提取所有本地 `stat_sys_*` 资源
- 对少量命中同样前缀规则的动画依赖，也一并保留了 `anim` / `interpolator` 文件
- 额外补充状态栏直接依赖的辅助资源：
  - `ic_activity_up`
  - `ic_activity_down`
  - `ic_sysbar_lights_out_dot_small`
  - `ic_qs_no_internet_available`
  - `ic_qs_no_internet_unavailable`
  - `ic_hotspot*`
  - `ic_headset*`
  - `ic_data_saver*`
  - `ic_bluetooth_connected`
  - `ic_legacy_*`
  - `mz_stat_sys_gps_*`
  - `bt_status_download`
  - `bt_status_upload`

清单文件：

- [systemui_statusbar_icon_dump/MANIFEST.txt](/mnt/c/Users/22/Downloads/312/systemui_statusbar_icon_dump/MANIFEST.txt)

## 7. 接手建议

如果后面要继续做状态栏改造，优先顺序建议是：

1. 先看 `system_icons.xml`，确认外层层级和哪些东西根本不在 `statusIcons` 里。
2. 再看 `status_bar_mobile_signal_group_inner.xml` 和 `status_bar_wifi_group_inner.xml`，确认移动信号和 Wi‑Fi 组内部控件拆分。
3. 再看 `PhoneStatusBarPolicy`、`StatusBarSignalPolicy`、`FlymeWifiSignalPolicy`，确认某个 slot 是谁在控制。
4. 如果要继续深挖 `statusIcons` 的最终左右顺序，再去追 `StatusBarIconList` 初始化字符串数组和 `StatusBarIconControllerImpl`。
