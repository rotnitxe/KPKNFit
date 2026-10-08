package com.example.kpkn.data.account

import android.content.Context
import com.example.kpkn.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Account operation failed"))
        }
    }
}

class FirebaseSession(val app: FirebaseApp, val local: Boolean) {
    val auth: FirebaseAuth = FirebaseAuth.getInstance(app)
    val storage: FirebaseStorage = FirebaseStorage.getInstance(app)
    init {
        if (local) {
            auth.useEmulator("10.0.2.2", 19099)
            storage.useEmulator("10.0.2.2", 19199)
        } else FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
    val functionsBase: String = if (local) "http://10.0.2.2:15001/demo-kpkn-stability/${BuildConfig.FUNCTIONS_REGION}/"
        else "https://${BuildConfig.FUNCTIONS_REGION}-${BuildConfig.FIREBASE_PROJECT_ID}.cloudfunctions.net/"
}

object FirebaseRuntime {
    @Volatile private var session: FirebaseSession? = null
    fun get(context: Context): FirebaseSession? {
        session?.let { return it }
        return synchronized(this) {
            session ?: run {
                val local = BuildConfig.FIREBASE_EMULATORS
                if (!local && (BuildConfig.FIREBASE_APP_ID.isBlank() || BuildConfig.FIREBASE_API_KEY.isBlank())) return@synchronized null
                val options = FirebaseOptions.Builder()
                    .setProjectId(if (local) "demo-kpkn-stability" else BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(if (local) "1:123456789012:android:1234567890abcdef" else BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(if (local) "AIza" + "D".repeat(35) else BuildConfig.FIREBASE_API_KEY)
                    .setStorageBucket(if (local) "demo-kpkn-stability.appspot.com" else BuildConfig.FIREBASE_STORAGE_BUCKET)
                    .build()
                val name = if (local) "KPKN_LOCAL" else FirebaseApp.DEFAULT_APP_NAME
                val app = FirebaseApp.getApps(context).firstOrNull { it.name == name }
                    ?: FirebaseApp.initializeApp(ProfileStorage.platformContext(), options, name)
                FirebaseSession(app, local).also { session = it; com.example.kpkn.data.diagnostics.CrashReportingController.initialize(app) }
            }
        }
    }
}
