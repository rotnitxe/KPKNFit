package com.yourprime.app.modules;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.facebook.react.HeadlessJsTaskService;

public class KpknBackgroundWorker extends Worker {
    private static final String PREFS_NAME = "kpkn_background_state";

    public KpknBackgroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        int runAttemptCount = getRunAttemptCount();

        try {
            Intent serviceIntent = new Intent();
            serviceIntent.setClassName(getApplicationContext(), "com.yourprime.app.modules.KpknBackgroundSyncService");
            serviceIntent.putExtra("trigger", "workmanager");
            serviceIntent.putExtra("requestedAtMs", now);
            HeadlessJsTaskService.acquireWakeLockNow(getApplicationContext());
            ComponentName componentName = getApplicationContext().startService(serviceIntent);
            if (componentName == null) {
                throw new IllegalStateException("No se pudo iniciar KpknBackgroundSyncService.");
            }

            prefs.edit()
                .putLong("lastDispatchAt", now)
                .putString("lastResult", "dispatching")
                .putInt("runAttemptCount", runAttemptCount)
                .remove("lastError")
                .apply();
            return Result.success();
        } catch (Exception error) {
            prefs.edit()
                .putLong("lastDispatchAt", now)
                .putString("lastResult", "failure")
                .putString("lastError", error.getMessage())
                .putInt("runAttemptCount", runAttemptCount)
                .apply();
            return runAttemptCount >= 4 ? Result.failure() : Result.retry();
        }
    }
}
