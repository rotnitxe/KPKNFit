package com.example.kpkn.data.sync

import com.example.kpkn.data.account.FirebaseSession
import com.example.kpkn.data.account.awaitResult
import com.google.firebase.appcheck.FirebaseAppCheck
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal fun privateStorageObjectPath(uid: String, path: String, sha256: String): String {
    require(uid.matches(Regex("[A-Za-z0-9_.:-]{1,128}")) && sha256.matches(Regex("[a-f0-9]{64}")))
    val objectPath = path.removePrefix("/") // StorageReference.getPath delegates to Uri.getPath.
    require(objectPath.matches(Regex("accounts/${Regex.escape(uid)}/[1-9][0-9]*/(?:data|media)/$sha256")))
    return objectPath
}

/** Authenticated Storage REST reads are resumable across WorkManager and process restarts. */
internal class FirebasePrivateDownloads(private val session: FirebaseSession, private val uid: String) {
    suspend fun download(path: String, partial: File, bytes: Long, sha256: String) {
        val objectPath = privateStorageObjectPath(uid, path, sha256)
        ResumablePrivateDownload {
            val user = requireNotNull(session.auth.currentUser).also { check(it.uid == uid) }
            val token = requireNotNull(user.getIdToken(false).awaitResult().token)
            val appCheck = if (session.local) null else FirebaseAppCheck.getInstance(session.app).getAppCheckToken(false).awaitResult().token
            check(session.auth.currentUser?.uid == uid)
            fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
            val endpoint = if (session.local) "http://10.0.2.2:19199" else "https://firebasestorage.googleapis.com"
            val bucket = session.storage.reference.bucket
            (URL("$endpoint/v0/b/${encode(bucket)}/o/${encode(objectPath)}?alt=media").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Firebase $token")
                setRequestProperty("X-Firebase-GMPID", session.app.options.applicationId)
                appCheck?.let { setRequestProperty("X-Firebase-AppCheck", it) }
            }
        }.download(partial, bytes, sha256)
        check(session.auth.currentUser?.uid == uid) { "Media account changed" }
    }
}
