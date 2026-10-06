package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

final class AssistantProtocol {
    static final String PACKAGE = "com.meizu.assistant";
    static final String SERVICE = PACKAGE + ".function.AssistantService";
    static final String DESCRIPTOR = "com.fiyme.statusbarsizer.assistant.control.v1";
    static final String ACTION = DESCRIPTOR + ".BIND";
    static final String CALLBACK = DESCRIPTOR + ".callback";
    static final int REGISTER = IBinder.FIRST_CALL_TRANSACTION;
    static final int SHOW = REGISTER + 1;
    static final int HIDE = REGISTER + 2;
    static final int TITLE_TINT = REGISTER + 3;
    static final int STATE = IBinder.FIRST_CALL_TRANSACTION;
    static final int RESULT = STATE + 1;

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

    static boolean isSystemUi(Context context) {
        String[] packages = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages != null) for (String name : packages) {
            if ("com.android.systemui".equals(name)) return true;
        }
        return false;
    }
}
