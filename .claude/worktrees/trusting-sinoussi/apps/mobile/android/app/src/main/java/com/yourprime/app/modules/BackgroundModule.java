package com.yourprime.app.modules;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
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

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.TimeUnit;

public class BackgroundModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "KPKNBackground";
    private static final String UNIQUE_PERIODIC_WORK = "kpkn_periodic_sync";
    private static final String UNIQUE_IMMEDIATE_WORK = "kpkn_immediate_sync";
    private static final String PREFS_NAME = "kpkn_background_state";

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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
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
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(KpknBackgroundWorker.class)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(getReactApplicationContext())
            .enqueueUniqueWork(UNIQUE_IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, request);

        WritableMap result = Arguments.createMap();
        result.putBoolean("started", true);
        promise.resolve(result);
    }

    @ReactMethod
    public void getStatus(Promise promise) {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        WritableMap result = Arguments.createMap();
        long lastDispatchAt = prefs.getLong("lastDispatchAt", 0L);
        long lastCompletionAt = prefs.getLong("lastCompletionAt", 0L);
        if (lastDispatchAt <= 0L) {
            result.putNull("lastDispatchAtMs");
        } else {
            result.putDouble("lastDispatchAtMs", lastDispatchAt);
        }
        if (lastCompletionAt <= 0L) {
            result.putNull("lastCompletionAtMs");
        } else {
            result.putDouble("lastCompletionAtMs", lastCompletionAt);
        }
        result.putString("lastResult", prefs.getString("lastResult", "idle"));
        String lastError = prefs.getString("lastError", null);
        if (lastError == null) {
            result.putNull("lastError");
        } else {
            result.putString("lastError", lastError);
        }
        result.putInt("runAttemptCount", prefs.getInt("runAttemptCount", 0));
        promise.resolve(result);
    }

    @ReactMethod
    public void reportTaskResult(com.facebook.react.bridge.ReadableMap params, Promise promise) {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean success = params.hasKey("success") && params.getBoolean("success");
        String error = params.hasKey("error") && !params.isNull("error") ? params.getString("error") : null;

        SharedPreferences.Editor editor = prefs.edit()
            .putLong("lastCompletionAt", System.currentTimeMillis())
            .putString("lastResult", success ? "success" : "failure");

        if (error == null || error.isEmpty()) {
            editor.remove("lastError");
        } else {
            editor.putString("lastError", error);
        }

        editor.apply();
        promise.resolve(null);
    }
}
