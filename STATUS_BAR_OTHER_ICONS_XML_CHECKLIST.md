# 状态栏其他图标 XML 重绘清单

这份清单只覆盖“电池、WiFi、移动信号”之外的状态栏图标。

不在本清单范围内：

- 电池相关
- WiFi 相关
- 移动信号 / 网络类型 / VoLTE / 5G 相关

目标是给 `FlymeStatusBarSizer` 后续补齐“其他状态图标”的 XML 重绘提供拆分依据。

## 1. 第一批：适合直接 XML 重绘

这些图标形状简单、状态少，优先做，收益最高。

### 1.1 单状态或双状态小图标

| 逻辑图标 | 现有资源 | 建议 |
| --- | --- | --- |
| 闹钟 | `stat_sys_alarm`, `stat_sys_alarm_dim` | 用 `vector` 重绘，两态即可 |
| NFC | `stat_sys_nfc` | 直接 `vector` |
| 勿扰 | `stat_sys_dnd` | 直接 `vector` |
| 静音 | `stat_sys_ringer_silent` | 直接 `vector` |
| 振动 | `stat_sys_ringer_vibrate` | 直接 `vector` |
| 投屏 | `stat_sys_cast` | 直接 `vector` |
| 外接显示 | `stat_sys_connected_display` | 直接 `vector` |
| 数据节省 | `stat_sys_data_saver` | 直接 `vector` |
| 传感器关闭 | `stat_sys_sensors_off` | 直接 `vector` |
| TTY | `stat_sys_tty_mode` | 直接 `vector` |
| 无 SIM | `stat_sys_no_sims` | 直接 `vector` |
| 飞行模式 | `stat_sys_airplane_mode` | 直接 `vector` |

### 1.2 有少量变体但仍然简单

| 逻辑图标 | 现有资源 | 建议 |
| --- | --- | --- |
| 耳机 | `stat_sys_headset_with_mic`, `stat_sys_headset_without_mic` | 拆成 2 个 `vector` |
| 旋转锁定 | `stat_sys_rotate_portrait`, `stat_sys_rotate_landscape` | 拆成 2 个 `vector` |
| 热点基础图标 | `stat_sys_hotspot` | 先单独做基础版 |

## 2. 第二批：可做 XML，但要保留多状态包装

这些不是单个图标文件就能结束，通常要保留原来的 `layer-list` / `animation-list` / 多资源组合逻辑。

### 2.1 屏幕录制

相关资源：

- `stat_sys_screen_record`
- `stat_sys_screen_record_1`
- `stat_sys_screen_record_2`
- `stat_sys_screen_record_3`

建议：

- 先确认 `stat_sys_screen_record.xml` 是否是对 1/2/3 的包装
- 如果是动画或分帧切换，不要只替换一个图标
- 正确做法是把底层图形改成 XML，同时保留外层状态切换逻辑

### 2.2 定位

相关资源：

- `stat_sys_location`
- `mz_stat_sys_gps_on`
- `mz_stat_sys_gps_acquire`

建议：

- `stat_sys_location` 本身带动画切换
- 需要把 `gps_on` / `gps_acquire` 都重绘掉
- 不建议只改静态结果图

### 2.3 蓝牙

相关资源：

- `stat_sys_data_bluetooth_connected`
- `bt_status_download`
- `bt_status_upload`
- `stat_sys_data_bluetooth_battery_0` 到 `stat_sys_data_bluetooth_battery_9`

来源：

- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:2228)

建议：

- 至少拆成 4 组：
  - 蓝牙连接
  - 蓝牙下载
  - 蓝牙上传
  - 蓝牙耳机电量分段
- 电量分段如果继续保持 0-9 档，可以用统一轮廓 + 数值/填充逻辑重绘
- 这是第二批里最值得单独建子任务的一项

### 2.4 热点 WiFi 代际图标

相关资源：

- `stat_sys_wifi_4_hotspot`
- `stat_sys_wifi_5_hotspot`
- `stat_sys_wifi_6_hotspot`
- `stat_sys_hotspot`

来源：

- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:2551)

建议：

- 如果你只想先统一风格，可以先做 `stat_sys_hotspot`
- 如果要完整替换，就要把 4/5/6 三套也一起补上

### 2.5 VPN

相关资源：

- `stat_sys_branded_vpn`
- `stat_sys_no_internet_branded_vpn`
- `stat_sys_vpn_ic`
- `stat_sys_no_internet_vpn_ic`

来源：

- [StatusBarSignalPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/StatusBarSignalPolicy.smali:212)

建议：

- VPN 不是单态图标
- 至少有：
  - 品牌 VPN
  - 普通 VPN
  - 无网品牌 VPN
  - 无网普通 VPN
- 这 4 个都要一起设计，不然视觉会断层

### 2.6 以太网

相关资源：

- `stat_sys_ethernet`
- `stat_sys_ethernet_fully`

建议：

- 资源数量少，技术上不难
- 但触发频率低，可以排在 VPN 后面

## 3. 第三批：动态图标来源，需要特殊处理

这些图标在 `SystemUI` 里不是简单固定拿 `stat_sys_*`，要先确认你在 `FlymeStatusBarSizer` 里想接管到什么程度。

### 3.1 隐私图标

逻辑图标：

- 麦克风
- 相机

来源：

- `PrivacyType.TYPE_MICROPHONE.getIconId()`
- `PrivacyType.TYPE_CAMERA.getIconId()`

参考位置：

- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:1237)
- [PhoneStatusBarPolicy.smali](/mnt/c/Users/22/Downloads/312/systemui_apktool/smali_classes4/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.smali:1281)

当前 APK 里能看到的相关资源更多是：

- `privacy_item_circle_camera`
- `privacy_item_circle_microphone`

建议：

- 先不要按 `stat_sys_*` 思路处理
- 先确认状态栏 slot 实际最终吃到的是哪组 drawable
- 这组更适合在 hook 时按 slot 或 contentDescription 接管，而不是只替换文件

### 3.2 定位隐私样式

虽然普通定位图标可以按第二批处理，但如果 Flyme/Android 在某些场景下走隐私入口，还可能涉及：

- `privacy_item_circle_location`

建议：

- 普通状态栏定位和隐私类定位分开看

## 4. 优先级建议

如果按投入产出比排，建议顺序是：

1. `alarm / nfc / dnd / silent / vibrate / cast / connected_display / data_saver / sensors_off / tty / no_sims / airplane`
2. `headset / rotate / hotspot(base)`
3. `screen_record / location`
4. `vpn / ethernet`
5. `bluetooth`
6. `privacy(camera / microphone / privacy location)`

## 5. 建议的实现拆分

最稳妥的拆法是按下面 4 个子包处理：

### 5.1 普通静态图标包

包含：

- `alarm`
- `nfc`
- `dnd`
- `silent`
- `vibrate`
- `cast`
- `connected_display`
- `data_saver`
- `sensors_off`
- `tty`
- `no_sims`
- `airplane`

### 5.2 小量多状态图标包

包含：

- `headset`
- `rotate`
- `hotspot`
- `ethernet`
- `vpn`

### 5.3 动画/组合图标包

包含：

- `screen_record`
- `location`
- `bluetooth`

### 5.4 动态来源接管包

包含：

- `camera`
- `microphone`
- `privacy location`

## 6. 落地建议

对 `FlymeStatusBarSizer` 来说，推荐做法不是“改 systemui_apktool 资源本体”，而是：

1. 先在模块里建立一套你自己的 XML drawable 资源命名
2. 再通过 hook 把对应 slot / ImageView / resource id 替换到模块资源
3. 复杂图标继续保留原 `SystemUI` 的状态机，只替换底层外观

这样后面维护成本最低，也更符合你现在这个项目的工作方式。
