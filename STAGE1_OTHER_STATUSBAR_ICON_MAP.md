# 阶段一映射表：其他状态栏图标 PNG -> XML

这份表只针对：

- 电池、WiFi、移动信号之外的状态栏图标
- 当前运行时仍可能命中 `PNG` 的资源
- 需要先提取出来给你逐个重画成 `XML drawable`

## 判定原则

阶段一不是看“`res/drawable/` 里有没有 XML”，而是看：

1. 这个逻辑图标运行时对应的资源名是什么
2. 这个资源名在高密目录 `drawable-xhdpi/xxhdpi/xxxhdpi` 下是否还有同名 `PNG`
3. 如果有，高密设备通常就会优先吃这些 `PNG`

所以：

- “base 是 XML，但高密目录有同名 PNG” 也算本阶段要处理
- “只有 XML/Vector，没有同名 PNG” 本阶段先跳过
- “走 `@android:` / 自定义 Drawable / 隐私动态图标” 单独标记，后面再接

## 1. 需要你重画的 PNG 目标

下面这些是阶段一的核心目标。提取目录里已经给你准备好了高密参考图。

| 逻辑图标 | 运行时资源名 | 当前 base 入口 | 高密目录 PNG | 阶段一动作 |
| --- | --- | --- | --- | --- |
| 闹钟 | `stat_sys_alarm` | `drawable/stat_sys_alarm.xml` | 有 | 画成 XML |
| 闹钟 dim | `stat_sys_alarm_dim` | `drawable/stat_sys_alarm_dim.xml` | 有 | 画成 XML |
| NFC | `stat_sys_nfc` | 无 base XML | 有 | 画成 XML |
| 勿扰 | `stat_sys_dnd` | `drawable/stat_sys_dnd.xml` | 有 | 画成 XML |
| 静音 | `stat_sys_ringer_silent` | `drawable/stat_sys_ringer_silent.xml` | 有 | 画成 XML |
| 振动 | `stat_sys_ringer_vibrate` | `drawable/stat_sys_ringer_vibrate.xml` | 有 | 画成 XML |
| 投屏 | `stat_sys_cast` | `drawable/stat_sys_cast.xml` | 有 | 画成 XML |
| 外接显示 | `stat_sys_connected_display` | `drawable/stat_sys_connected_display.xml` | 有 | 画成 XML |
| 有线耳机带麦 | `stat_sys_headset_with_mic` | 无 base XML | 有 | 画成 XML |
| 有线耳机无麦 | `stat_sys_headset_without_mic` | 无 base XML | 有 | 画成 XML |
| 无 SIM | `stat_sys_no_sims` | 无 base XML | 有 | 画成 XML |
| 飞行模式 | `stat_sys_airplane_mode` | `drawable/stat_sys_airplane_mode.xml` | 有 | 画成 XML |
| VPN 普通 | `stat_sys_vpn_ic` | `drawable/stat_sys_vpn_ic.xml` | 有 | 画成 XML |
| 蓝牙已连接 | `stat_sys_data_bluetooth_connected` | `drawable/stat_sys_data_bluetooth_connected.xml` | 有 | 画成 XML |
| 蓝牙下载帧 1 | `stat_sys_bluetooth_data_down` | 无 base XML | 有 | 画成 XML |
| 蓝牙上传帧 1 | `stat_sys_bluetooth_data_up` | 无 base XML | 有 | 画成 XML |
| 蓝牙传输共享帧 | `stat_sys_data_bluetooth_up_down` | 无 base XML | 有 | 画成 XML |
| 蓝牙电量 0-9 | `stat_sys_data_bluetooth_battery_0` ~ `9` | 无 base XML | 有 | 画成 XML |
| 定位动画帧 1 | `mz_stat_sys_gps_acquire` | 无 base XML | 有 | 画成 XML |
| 定位动画帧 2 | `mz_stat_sys_gps_on` | 无 base XML | 有 | 画成 XML |

## 2. 这些图标为什么虽然有 XML 还要进阶段一

这类图标的关键问题是：

- `res/drawable/xxx.xml` 只是 base 入口
- 同名高密资源目录下还有 `PNG`
- 真机如果是高密屏，大概率实际会命中 `PNG`

典型例子：

- `stat_sys_alarm`
- `stat_sys_alarm_dim`
- `stat_sys_dnd`
- `stat_sys_ringer_silent`
- `stat_sys_ringer_vibrate`
- `stat_sys_cast`
- `stat_sys_connected_display`
- `stat_sys_airplane_mode`
- `stat_sys_vpn_ic`
- `stat_sys_data_bluetooth_connected`

也就是说，这些不是“已经做完了”，而是“逻辑资源入口有 XML，但实际显示链路仍然可能落到 PNG”。

## 3. 本阶段先不用画的图标

这些资源当前已经是 XML/Vector/自定义 Drawable，阶段一不需要先画 PNG。

| 逻辑图标 | 运行时资源名 | 当前类型 | 本阶段处理 |
| --- | --- | --- | --- |
| 数据节省 | `stat_sys_data_saver` | XML + Vector | 跳过 |
| 热点基础版 | `stat_sys_hotspot` | XML + Vector | 跳过 |
| 热点 WiFi 4 | `stat_sys_wifi_4_hotspot` | XML + Vector | 跳过 |
| 热点 WiFi 5 | `stat_sys_wifi_5_hotspot` | XML + Vector | 跳过 |
| 热点 WiFi 6 | `stat_sys_wifi_6_hotspot` | XML + Vector | 跳过 |
| 传感器关闭 | `stat_sys_sensors_off` | Vector | 跳过 |
| TTY | `stat_sys_tty_mode` | Vector | 跳过 |
| 以太网 | `stat_sys_ethernet` | Vector | 跳过 |
| 以太网 fully | `stat_sys_ethernet_fully` | Vector | 跳过 |
| VPN 无网普通版 | `stat_sys_no_internet_vpn_ic` | Vector | 跳过 |
| VPN 品牌版 | `stat_sys_branded_vpn` | Vector | 跳过 |
| VPN 无网品牌版 | `stat_sys_no_internet_branded_vpn` | Vector | 跳过 |
| 旋转锁竖屏 | `stat_sys_rotate_portrait` | XML + Vector | 跳过 |
| 旋转锁横屏 | `stat_sys_rotate_landscape` | XML + Vector | 跳过 |

## 4. 本阶段暂不按 PNG 处理的动态图标

这些不是简单“找一张 PNG 重画”能解决的，后面单独处理。

| 逻辑图标 | 运行时资源名/入口 | 当前类型 | 备注 |
| --- | --- | --- | --- |
| 定位图标外层 | `stat_sys_location` | `animation-list` | 真正要画的是内部两帧 PNG |
| 蓝牙下载外层 | `bt_status_download` | `animation-list` | 真正要画的是内部 PNG |
| 蓝牙上传外层 | `bt_status_upload` | `animation-list` | 真正要画的是内部 PNG |
| 屏幕录制 | `stat_sys_screen_record` | `ScreenRecordDrawable` | 先不按 PNG 处理 |
| 相机隐私 | `PrivacyType.TYPE_CAMERA` | 动态 `getIconId()` | 不是本地固定 PNG |
| 麦克风隐私 | `PrivacyType.TYPE_MICROPHONE` | 动态 `getIconId()` | 不是本地固定 PNG |
| 隐私圆点类 | `privacy_item_circle_*` | `layer-list` + `@android:` | 不是你现在要画的 PNG 主体 |

## 5. 提取目录

阶段一提取目录：

- [stage1_other_statusbar_icon_extract](/mnt/c/Users/22/Downloads/312/stage1_other_statusbar_icon_extract)

目录结构：

- `png_refs/`
  - 每个需要重画的目标只保留一份高密参考图
  - 优先 `xxxhdpi`，没有就退到 `xxhdpi`，再退到 `xhdpi`
- `entry_drawables/`
  - 保留相关入口 XML，方便你对照当前状态机/包装关系
- `MANIFEST.md`
  - 列出提取来源和选取规则

## 6. 建议你画图的顺序

建议按这组顺序一个个画：

1. `alarm`
2. `alarm_dim`
3. `nfc`
4. `dnd`
5. `silent`
6. `vibrate`
7. `cast`
8. `connected_display`
9. `headset_with_mic`
10. `headset_without_mic`
11. `no_sims`
12. `airplane_mode`
13. `vpn_ic`
14. `gps_acquire`
15. `gps_on`
16. `bluetooth_connected`
17. `bluetooth_data_down`
18. `bluetooth_data_up`
19. `bluetooth_up_down`
20. `bluetooth_battery_0` 到 `9`

## 7. 下一步

等你把这些图一个个画成 XML 之后，下一阶段再做：

1. 给每个新 XML 确定模块内资源名
2. 确定替换策略：
   - 同名资源替换
   - 运行时按 slot / drawable id 接管
3. 再接入 `FlymeStatusBarSizer`
