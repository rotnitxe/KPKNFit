package com.example.kpkn

import android.app.Application
import android.content.Context
import android.os.StrictMode
import com.example.kpkn.telemetry.KpknTelemetry
import com.example.kpkn.ui.locale.LocaleManager

class KpknApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()

        // Enable StrictMode in debug builds to catch disk/network on main thread and leaked closeables.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build()
            )
        }

        val telemetry = KpknTelemetry.getInstance(this)
        telemetry.setUserProperty("app_version", BuildConfig.VERSION_NAME)
        telemetry.setUserProperty("app_version_code", BuildConfig.VERSION_CODE.toString())
    }
}
