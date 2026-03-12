package com.yourprime.app.modules;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

import java.util.concurrent.TimeUnit;

public class BackgroundModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "KPKNBackground";
    private static final String UNIQUE_PERIODIC_WORK = "kpkn_periodic_sync";
    private static final String UNIQUE_IMMEDIATE_WORK = "kpkn_immediate_sync";

    public BackgroundModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void schedulePeriodicSync(Promise promise) {
        PeriodicWorkRequest request =
            new PeriodicWorkRequest.Builder(KpknBackgroundWorker.class, 6, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(getReactApplicationContext())
            .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);

        WritableMap result = Arguments.createMap();
        result.putBoolean("scheduled", true);
        promise.resolve(result);
    }

    @ReactMethod
    public void cancelPeriodicSync(Promise promise) {
        WorkManager.getInstance(getReactApplicationContext()).cancelUniqueWork(UNIQUE_PERIODIC_WORK);
        WritableMap result = Arguments.createMap();
        result.putBoolean("cancelled", true);
        promise.resolve(result);
    }

    @ReactMethod
    public void runImmediateSync(Promise promise) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(KpknBackgroundWorker.class).build();
        WorkManager.getInstance(getReactApplicationContext())
            .enqueueUniqueWork(UNIQUE_IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, request);

        WritableMap result = Arguments.createMap();
        result.putBoolean("started", true);
        promise.resolve(result);
    }
}
