package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.example.flymestatusbarsizer.feature.ime.ImeWindowModeBridge;

public final class WorkspaceDeepLinkActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (OneStepWorkspaceProtocol.matches(getIntent().getData())) {
            sendBroadcast(new Intent(OneStepWorkspaceProtocol.ACTION_OPEN)
                    .setPackage("com.android.systemui")
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        }
        if (ImeWeltWindowProtocol.matches(getIntent().getData())) {
            ImeWindowModeBridge.request(this);
        }
        finishAndRemoveTask();
    }
}
