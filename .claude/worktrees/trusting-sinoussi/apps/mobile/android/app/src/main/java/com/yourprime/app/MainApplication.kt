package com.yourprime.app

import android.app.Application
import android.preference.PreferenceManager
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.yourprime.app.BuildConfig
import com.yourprime.app.modules.KPKNMobilePackage

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(KPKNMobilePackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      PreferenceManager.getDefaultSharedPreferences(this)
          .edit()
          .putString("debug_http_host", "localhost:8081")
          .apply()
    }
    loadReactNative(this)
  }
}
