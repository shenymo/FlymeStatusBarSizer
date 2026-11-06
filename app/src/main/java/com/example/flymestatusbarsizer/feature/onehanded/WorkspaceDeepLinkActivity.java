package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.example.flymestatusbarsizer.feature.ime.ImeWindowModeBridge;

public final class WorkspaceDeepLinkActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Uri data = getIntent().getData();
        if (OneStepWorkspaceProtocol.matches(data)) {
            sendBroadcast(new Intent(OneStepWorkspaceProtocol.ACTION_OPEN)
                    .setPackage("com.android.systemui")
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        }
        if (ImeWeltWindowProtocol.matches(data)) {
            String targetPackage = data.getQueryParameter("package");
            ImeWindowModeBridge.request(this, targetPackage);
        }
        finishAndRemoveTask();
    }
}
