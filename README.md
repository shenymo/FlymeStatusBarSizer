# FlymeStatusBarSizer AI Handoff README

This project is an Android/Xposed module that customizes Flyme/SystemUI status bar sizing and several iOS-style status bar visuals. This README is written primarily for AI/code agents that need to understand the project quickly and find the right edit points.

## What This App Does

FlymeStatusBarSizer has two sides:

1. A normal Android settings app.
2. An Xposed module loaded into `com.android.systemui`.

The settings app stores configuration in device-protected `SharedPreferences`. The Xposed side reads those settings through `SettingsProvider` using a `content://` URI and applies changes to SystemUI views by hooking constructors, factory methods, draw methods, and image update methods.

Main feature groups:

- Global status bar icon scaling.
- Per-icon scaling factors for mobile signal, Wi-Fi, battery, generic icons, arrows, and 5G/network type labels.
- iOS-style battery drawing.
- iOS-style mobile signal bars.
- iOS-style 5G label replacement.
- Current network speed text scaling and offset tuning.
- Config import/export as JSON.

## Project Layout

Important files:

- `app/src/main/java/com/example/flymestatusbarsizer/FlymeStatusBarSizer.java`
  - Main Xposed module.
  - Hooks SystemUI and applies all runtime modifications.
  - Most behavior bugs live here.

- `app/src/main/java/com/example/flymestatusbarsizer/SettingsStore.java`
  - Canonical setting keys and defaults.
  - Also contains `INT_KEYS`, `BOOLEAN_KEYS`, `defaultInt()`, and `defaultBoolean()` for full config import/export.
  - If a new setting is added, update this file first.

- `app/src/main/java/com/example/flymestatusbarsizer/SettingsProvider.java`
  - Exposes settings to the SystemUI/Xposed process through `content://com.example.flymestatusbarsizer.settings/settings`.
  - Must include any setting that `FlymeStatusBarSizer.Config.load()` needs.

- `app/src/main/java/com/example/flymestatusbarsizer/MainActivity.java`
  - Main settings screen.
  - Contains global toggles, global scale, text scale, navigation buttons, and JSON import/export.


- `app/src/main/java/com/example/flymestatusbarsizer/SignalNetworkSettingsActivity.java`
  - iOS mobile signal bars and 5G/network type label tuning.
  - Contains mobile signal scale, 5G/network type scale and offsets, plus iOS signal offsets.

- `app/src/main/java/com/example/flymestatusbarsizer/WifiSettingsActivity.java`
  - Wi-Fi icon settings.
  - Contains Wi-Fi signal scale.

- `app/src/main/java/com/example/flymestatusbarsizer/OtherIconSettingsActivity.java`
  - Other status bar icon settings.
  - Contains generic status icon scale, activity arrow scale, and current network speed offsets.

- `app/src/main/java/com/example/flymestatusbarsizer/BatterySettingsActivity.java`
  - iOS battery icon settings.
  - Contains battery scale, iOS battery dimensions, offsets, and inner text size.

- `app/src/main/java/com/example/flymestatusbarsizer/IosBatteryPainter.java`
  - Draws the iOS-style battery.

- `app/src/main/java/com/example/flymestatusbarsizer/IosSignalDrawable.java`
  - Draws iOS-style signal bars.

- `app/src/main/java/com/example/flymestatusbarsizer/NetworkTypeDrawable.java`
  - Draws iOS-style network type labels such as 5G/5GA/5G+.

- `app/src/main/AndroidManifest.xml`
  - Registers settings activities and exported settings provider.

## Settings Flow

Settings flow is:

```text
Settings Activity
  -> SettingsStore.prefs(...)
  -> device-protected SharedPreferences
  -> SettingsProvider query(...)
  -> FlymeStatusBarSizer.Config.load(...)
  -> runtime view/draw modifications in SystemUI
```

Important URI:

```java
content://com.example.flymestatusbarsizer.settings/settings
```

If a setting appears in the UI but does not affect SystemUI, check all of these places:

1. `SettingsStore.java`: key/default exists.
2. The relevant Activity writes the key.
3. `SettingsProvider.java`: provider exports the key.
4. `FlymeStatusBarSizer.Config`: field exists and `apply()` reads the key.
5. Some runtime method actually uses the field.
6. Config refresh or hook timing invalidates/reapplies the change.

## Current Setting Groups

Boolean keys:

- `enabled`: global module enabled flag. Hook code still loads, but most modifications are skipped if false.
- `ios_battery_style`: default true. Enables custom iOS-style battery drawing.
- `ios_signal_style`: default true. Enables custom iOS-style mobile signal bars.
- `ios_network_type_style`: default true. Enables custom iOS-style 5G/network type label drawing.

Integer keys:

- `global_icon_scale`: default 115, range in main UI 80-160 percent.
- `mobile_signal_factor`: default 100, range 0-160 percent.
- `wifi_signal_factor`: default 100, range 0-160 percent.
- `battery_factor`: default 100, range 0-160 percent.
- `status_icon_factor`: default 55, range 0-160 percent.
- `network_type_factor`: default 65, range 0-160 percent.
- `activity_icon_factor`: default 75, range 0-160 percent.
- `text_scale`: default 100, range 80-130 percent.

Network type / 5G label offsets:

- Legacy fallback keys:
  - `network_type_offset_x`
  - `network_type_offset_y`
- Scene-specific keys:
  - `network_type_desktop_offset_x`
  - `network_type_desktop_offset_y`
  - `network_type_keyguard_offset_x`
  - `network_type_keyguard_offset_y`
  - `network_type_control_center_offset_x`
  - `network_type_control_center_offset_y`

IOS signal offsets:

- Legacy fallback keys:
  - `ios_signal_offset_x`
  - `ios_signal_offset_y`
- Scene-specific keys:
  - `ios_signal_desktop_offset_x`
  - `ios_signal_desktop_offset_y`
  - `ios_signal_keyguard_offset_x`
  - `ios_signal_keyguard_offset_y`
  - `ios_signal_control_center_offset_x`
  - `ios_signal_control_center_offset_y`

IOS battery keys:

- `ios_battery_width`: default 28 dp, UI range 16-44 dp.
- `ios_battery_height`: default 14 dp, UI range 8-24 dp.
- `ios_battery_offset_x`: default 0 dp, UI range -20 to 20 dp.
- `ios_battery_offset_y`: default 0 dp, UI range -20 to 20 dp.
- `ios_battery_text_size`: default 72 percent, UI range 40-100 percent.

Current network speed keys:

- `connection_rate_offset_x`: default 0 dp, UI range -80 to 80 dp.
- `connection_rate_offset_y`: default 0 dp, UI range -80 to 80 dp.

Removed/deprecated:

- `hide_mobile_type` was removed from active code and UI. Do not reintroduce it unless explicitly requested.

## Xposed Hook Entry Points

The module only runs for first load of `com.android.systemui`:

```java
onPackageLoaded(...)
```

Main hook groups in `FlymeStatusBarSizer.java`:

- `hookConstructAndBind(...)`
  - Hooks mobile and Wi-Fi status bar view factory methods.
  - Calls `applyStatusBarSizing()` or similar sizing logic after views are built.

- `hookFlymeWifiView(...)`
  - Flyme-specific Wi-Fi view updates.

- `hookConnectionRateView(...)`
  - Hooks `com.flyme.statusbar.connectionRateView.ConnectionRateView`.
  - Applies text scaling and manual offsets.
  - Be careful: vertical offset is partly draw-time canvas translation, not just layout movement.

- `hookImageViewTintUpdates(...)`
  - Watches `ImageView` drawable/resource/tint changes.
  - Re-applies iOS signal and network type drawables after SystemUI changes resources.

- `hookBatteryDrawable(...)`
  - Hooks Flyme battery drawable drawing.

- `hookFlymeBatteryMeterViewDraw(...)`
  - Replaces Flyme battery view draw with iOS battery painter when enabled.

- `hookFlymeBatteryMeterViewMeasure(...)`
  - Adjusts measured size for custom iOS battery.

- `hookStatusTextView(...)`
  - Tracks clock/operator/carrier text for text scaling.

## Key Runtime Methods To Know

In `FlymeStatusBarSizer.java`:

- `applyStatusBarSizing(View root)`
  - Main mobile signal / network type / arrows sizing path.

- `applyWifiSizing(View root)`
  - Wi-Fi sizing path.

- `applyIosSignalStyle(...)`
  - Applies iOS signal drawable when enabled.

- `applyKnownNetworkTypeStyle(...)`
  - Applies iOS-style 5G/network type labels.

- `applyReferenceSignalSizing(...)`
  - Handles lockscreen/control-center/reference-size contexts.

- `applyConnectionRateTextScale(View view)`
  - Adjusts network speed text size.

- `applyConnectionRateOffset(View view)`
  - Applies horizontal network speed movement.
  - Vertical network speed offset is intentionally added to draw-time offset via `getConnectionRateManualDrawOffsetY()`.

- `getConnectionRateAlignmentOffset(View view)`
  - Auto-aligns network speed drawing to battery/reference bottom.
  - This can fight layout-based vertical movement, which is why manual Y offset is draw-time.

- `offsetView(View child, int offsetXDp, int offsetYDp)`
  - Generic margin/translation offset helper for many views.

- `scaleView(View child, float widthScale, float heightScale)`
  - Generic layout-size scaling helper.

- `disableAncestorClipping(View view, int maxDepth)`
  - Used when offsets/drawables may extend beyond parent bounds.

## Config Import/Export

Implemented in `MainActivity.java`.

Export:

- Uses `Intent.ACTION_CREATE_DOCUMENT`.
- Writes JSON with this structure:

```json
{
  "schema": "flyme_status_bar_sizer",
  "version": 1,
  "settings": {
    "enabled": true,
    "global_icon_scale": 115
  }
}
```

The actual exported file includes every key from `SettingsStore.BOOLEAN_KEYS` and `SettingsStore.INT_KEYS`, even if the user never changed the value.

Import:

- Uses `Intent.ACTION_OPEN_DOCUMENT`.
- Accepts the structured JSON above.
- Also accepts a plain object where settings are at top level.
- Clears old prefs first, then writes every known key with imported value or default fallback.

When adding a new setting, update:

1. `SettingsStore.KEY_*` and `DEFAULT_*`.
2. `SettingsStore.INT_KEYS` or `BOOLEAN_KEYS`.
3. `SettingsStore.defaultInt()` or `defaultBoolean()`.
4. `SettingsProvider.query()` if SystemUI needs it.
5. `FlymeStatusBarSizer.Config` if Xposed side needs it.
6. The relevant Activity UI.

## UI Pages

Current page split:

- Main page: global switches, global scale, navigation, import/export, text scale.
- Battery page: battery scale plus iOS battery dimensions/offset/text size.
- Signal/network page: mobile signal scale plus iOS signal bars and 5G/network type label scale/offsets.
- Wi-Fi page: Wi-Fi signal scale.
- Other icons page: generic status icon scale, activity arrow scale, and current network speed offset.

Most UI is created programmatically in Java. There are no XML layout files for these settings pages.

## Known Gotchas

- Many source files contain Chinese UI labels. To avoid encoding issues when editing from PowerShell or shell scripts, prefer Java Unicode escapes (`\uXXXX`) or use an editor that preserves UTF-8 correctly.

- Do not assume a setting works just because it exists in an Activity. It must also be exposed by `SettingsProvider` and read/used by `FlymeStatusBarSizer.Config`.

- SystemUI/Xposed changes often require restarting SystemUI or rebooting the phone.

- Status bar parent layouts frequently clip children. Use `disableAncestorClipping(...)` when offsets appear to stop moving or get cut off.

- Network speed vertical positioning is delicate. It has auto-alignment to the battery/reference view. Manual Y offset is currently applied at draw time to avoid auto-alignment cancelling it.

- Some offsets have legacy fallback keys for compatibility. Do not remove these lightly:
  - `network_type_offset_x/y`
  - `ios_signal_offset_x/y`

- `WeakHashMap` is used to remember original sizes/margins/translations for live SystemUI views. Avoid replacing these with normal maps unless there is a strong reason.

## Build Notes

This is a Gradle Android project:

```powershell
.\gradlew.bat assembleDebug
```

Network may be required if Gradle wrapper/dependencies are not cached. The Xposed API dependency is compile-only:

```gradle
compileOnly "io.github.libxposed:api:101.0.1"
```

The module targets:

- `compileSdk 35`
- `minSdk 26`
- `targetSdk 35`
- Java 17 source/target compatibility

## Quick Task Map

- Add a new setting:
  - Start in `SettingsStore.java`, then `SettingsProvider.java`, then `FlymeStatusBarSizer.Config`, then UI Activity.

- Change iOS battery drawing:
  - `IosBatteryPainter.java`
  - battery hooks in `FlymeStatusBarSizer.java`
  - `BatterySettingsActivity.java` for UI.

- Change iOS signal bars:
  - `IosSignalDrawable.java`
  - `applyIosSignalStyle(...)` and related methods in `FlymeStatusBarSizer.java`
  - `SignalNetworkSettingsActivity.java` for UI.

- Change 5G/network type labels:
  - `NetworkTypeDrawable.java`
  - `applyKnownNetworkTypeStyle(...)` and `applyNetworkTypeResource(...)` in `FlymeStatusBarSizer.java`
  - `SignalNetworkSettingsActivity.java` for UI.

- Change Wi-Fi icon sizing:
  - `applyWifiSizing(...)` and Flyme Wi-Fi hooks in `FlymeStatusBarSizer.java`
  - `WifiSettingsActivity.java` for UI.

- Change current network speed behavior:
  - `hookConnectionRateView(...)`
  - `applyConnectionRateTextScale(...)`
  - `applyConnectionRateOffset(...)`
  - `getConnectionRateAlignmentOffset(...)`
  - `OtherIconSettingsActivity.java` for UI.

- Change import/export:
  - `MainActivity.java`
  - `SettingsStore.INT_KEYS` / `BOOLEAN_KEYS`

- Change settings provider output:
  - `SettingsProvider.java`

## Current Intentional Defaults

- Module enabled: true.
- iOS battery style: true.
- iOS mobile signal style: true.
- iOS 5G/network type style: true.
- Hide 4G/5G switch: removed.


