package com.yourprime.app.modules;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.ReactHost;
import com.facebook.react.ReactInstanceEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

public class KpknBackgroundSyncService extends HeadlessJsTaskService {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        HeadlessJsTaskConfig taskConfig = getTaskConfig(intent);
        if (taskConfig == null) {
            return START_NOT_STICKY;
        }

        ReactContext currentContext = getReactContext();
        if (currentContext != null && !currentContext.hasActiveReactInstance()) {
            ReactHost host = getReactHost();
            if (host != null) {
                acquireWakeLockNow(this);
                ReactInstanceEventListener listener = new ReactInstanceEventListener() {
                    @Override
                    public void onReactContextInitialized(ReactContext context) {
                        startTask(taskConfig);
                        host.removeReactInstanceEventListener(this);
                    }
                };
                host.addReactInstanceEventListener(listener);
                host.start();
                return START_REDELIVER_INTENT;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected @Nullable HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        Bundle extras = intent != null && intent.getExtras() != null ? intent.getExtras() : new Bundle();
        return new HeadlessJsTaskConfig(
            "KPKNBackgroundSync",
            Arguments.fromBundle(extras),
            30000,
            false
        );
    }
}
