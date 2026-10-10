package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.example.flymestatusbarsizer.BuildConfig;

/** A separate Binder capability is issued for each workspace launch. */
final class OneStepActivityProtocol {
    static final String ACTIVITY = "com.example.flymestatusbarsizer.feature.onehanded.OneStepActivity";
    static final String CONTROL = BuildConfig.APPLICATION_ID + ".workspace.control";
    static final String CALLBACK = CONTROL + ".callback";
    static final String EXTRA_CONTROL = "workspace_control";
    static final String EXTRA_UID = "workspace_uid";
    static final int ATTACH = IBinder.FIRST_CALL_TRANSACTION;
    static final int MOUNTED = ATTACH + 1;
    static final int INSETS = ATTACH + 2;
    static final int CLOSE = ATTACH + 3;
    static final int BAR_COLORS = ATTACH + 4;
    static final int VISIBILITY = ATTACH + 5;
    static final int REMOUNT = ATTACH + 6;
    static final int DETACH = ATTACH + 7;
    static final int SURFACE = IBinder.FIRST_CALL_TRANSACTION;
    static final int FINISH = SURFACE + 1;

    private OneStepActivityProtocol() {}

    static boolean isActivity(ComponentName component) {
        return component != null && BuildConfig.APPLICATION_ID.equals(component.getPackageName())
                && ACTIVITY.equals(component.getClassName());
    }

    interface Writer { void write(Parcel data); }

    static boolean send(IBinder binder, String descriptor, int code, Writer writer) throws RemoteException {
        if (binder == null) return false;
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            writer.write(data);
            return binder.transact(code, data, null, IBinder.FLAG_ONEWAY);
        } finally { data.recycle(); }
    }
}
