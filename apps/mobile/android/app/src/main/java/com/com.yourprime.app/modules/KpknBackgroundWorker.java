package com.yourprime.app.modules;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class KpknBackgroundWorker extends Worker {
    private static final String PREFS_NAME = "kpkn_background_state";

    public KpknBackgroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong("lastRunAt", System.currentTimeMillis()).apply();
        return Result.success();
    }
}
