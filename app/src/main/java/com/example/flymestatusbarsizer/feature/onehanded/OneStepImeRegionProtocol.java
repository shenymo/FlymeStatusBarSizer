package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.RectF;
import android.os.IBinder;
import android.os.Parcel;

import com.example.flymestatusbarsizer.BuildConfig;

final class OneStepImeRegionProtocol {
    static final String DESCRIPTOR = BuildConfig.APPLICATION_ID + ".workspace.ime.region";
    static final int CONNECT = 0x00534f4a;
    static final int STATE = IBinder.FIRST_CALL_TRANSACTION;
    static final int REGION = IBinder.FIRST_CALL_TRANSACTION;
    static final int CLOSE = REGION + 1;
    static final int EDITOR = 1;
    static final int CARET = 2;

    private OneStepImeRegionProtocol() {}

    static boolean valid(RectF region) {
        return region != null && Float.isFinite(region.left) && Float.isFinite(region.top)
                && Float.isFinite(region.right) && Float.isFinite(region.bottom)
                && region.left <= region.right && region.top < region.bottom;
    }

    static RectF readRegion(Parcel data) {
        return data.readInt() == 0 ? null : RectF.CREATOR.createFromParcel(data);
    }

    static void writeRegion(Parcel data, RectF region) {
        data.writeInt(region == null ? 0 : 1);
        if (region != null) region.writeToParcel(data, 0);
    }
}
