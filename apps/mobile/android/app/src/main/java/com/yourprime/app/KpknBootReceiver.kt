package com.yourprime.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.yourprime.app.modules.KpknBackgroundWorker
import java.util.concurrent.TimeUnit

class KpknBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val request = PeriodicWorkRequest.Builder(KpknBackgroundWorker::class.java, 6, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("kpkn_periodic_sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
