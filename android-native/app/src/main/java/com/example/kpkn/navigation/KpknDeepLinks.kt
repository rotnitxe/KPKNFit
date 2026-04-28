package com.example.kpkn.navigation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.kpkn.MainActivity

object KpknDeepLinks {
    private const val SCHEME = "kpkn"

    fun uri(path: String): Uri {
        val cleanPath = path.trim().trim('/').ifEmpty { "home" }
        return Uri.parse("$SCHEME://$cleanPath")
    }

    fun mainActivityIntent(context: Context, path: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri(path)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun pendingActivityIntent(
        context: Context,
        requestCode: Int,
        path: String,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        mainActivityIntent(context, path),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
