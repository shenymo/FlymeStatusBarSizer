package com.example.flymestatusbarsizer.feature.statusbar;

public enum BluetoothIconState {
    CONNECTED("connected", "连接"),
    BATTERY_0("battery_0", "电量 0–9%"),
    BATTERY_1("battery_1", "电量 10–19%"),
    BATTERY_2("battery_2", "电量 20–29%"),
    BATTERY_3("battery_3", "电量 30–39%"),
    BATTERY_4("battery_4", "电量 40–49%"),
    BATTERY_5("battery_5", "电量 50–59%"),
    BATTERY_6("battery_6", "电量 60–69%"),
    BATTERY_7("battery_7", "电量 70–79%"),
    BATTERY_8("battery_8", "电量 80–89%"),
    BATTERY_9("battery_9", "电量 90–100%"),
    DOWNLOAD("download", "下载"),
    UPLOAD("upload", "上传");

    public final String id;
    public final String title;

    BluetoothIconState(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public static BluetoothIconState fromResourceName(String name) {
        for (BluetoothIconState state : values()) {
            String resource = state.isTransfer() ? "bt_status_" + state.id
                    : state == CONNECTED ? "stat_sys_data_bluetooth_connected"
                    : "stat_sys_data_bluetooth_" + state.id;
            if (resource.equals(name)) return state;
        }
        return null;
    }

    public boolean isTransfer() { return this == UPLOAD || this == DOWNLOAD; }
}
