package com.example.kpkn.data.sync

import android.content.Context
import android.net.Uri
import com.example.kpkn.data.account.FirebaseSession
import com.example.kpkn.data.account.awaitResult
import com.example.kpkn.data.backup.BoundedInput
import com.example.kpkn.data.backup.PortableBackup
import com.google.android.gms.tasks.Task
import com.google.firebase.storage.OnProgressListener
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable data class MediaFileReference(val role: String, val sha256: String, val bytes: Long, val contentType: String)

/** Private file identities never contain paths, download tokens or upload-session credentials. */
object MediaFiles {
    const val MAX_BYTES = 256L * 1024 * 1024
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) {
            val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun stage(context: Context, path: String, role: String, video: Boolean): MediaFileReference? {
        require(role in setOf("main", "thumbnail", "pose"))
        val original = File(path).canonicalFile
        if (!original.isFile) return null
        val root = context.filesDir.canonicalFile
        require(original.toPath().startsWith(root.toPath()) && original.relativeTo(root).invariantSeparatorsPath.substringBefore('/') in PortableBackup.portableRoots)
        require(original.length() in 1..MAX_BYTES)
        val header = original.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
        val type = when {
            role == "pose" -> "application/json"
            video -> "video/mp4"
            header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "image/png"
            header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
            original.extension.lowercase() == "png" -> "image/png"
            original.extension.lowercase() == "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val reference = MediaFileReference(role, hash(original), original.length(), type)
        val staged = File(context.noBackupFilesDir, "sync-media/${reference.sha256}")
        if (!staged.isFile || staged.length() != reference.bytes || hash(staged) != reference.sha256) {
            staged.parentFile!!.mkdirs()
            val temporary = File(staged.parentFile, "${reference.sha256}.part")
            original.inputStream().use { input -> java.io.FileOutputStream(temporary).use { output -> BoundedInput.copy(input, output, reference.bytes); output.fd.sync() } }
            require(temporary.length() == reference.bytes && hash(temporary) == reference.sha256)
            Files.move(temporary.toPath(), staged.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        return reference
    }
    fun references(payload: JsonObject?): List<MediaFileReference> = payload?.get("mediaFiles")?.takeIf { it != JsonNull }?.jsonArray.orEmpty().map {
        json.decodeFromJsonElement(MediaFileReference.serializer(), it).also { reference ->
            require(reference.sha256.matches(Regex("[a-f0-9]{64}")) && reference.bytes in 1..MAX_BYTES && reference.role in setOf("main", "thumbnail", "pose"))
            require(reference.contentType in setOf("image/jpeg", "image/png", "image/webp", "video/mp4", "application/json") &&
                (reference.contentType != "application/json" || reference.role == "pose"))
        }
    }.also { require(it.size <= 3 && it.map(MediaFileReference::role).distinct().size == it.size) }
    fun local(context: Context, reference: MediaFileReference): File = File(context.filesDir, "workout_media/cloud/${reference.sha256}")
}

class MediaTransfer(private val context: Context, private val uid: String, private val session: FirebaseSession) {
    private val preferences = context.getSharedPreferences("sync_media_transfer", 0)
    private fun owned() = check(session.auth.currentUser?.uid == uid) { "Media account changed" }
    suspend fun upload(generation: Long, payload: JsonObject?) = withContext(Dispatchers.IO) {
        for (reference in MediaFiles.references(payload)) {
            owned()
            val local = File(context.noBackupFilesDir, "sync-media/${reference.sha256}")
            require(local.isFile && local.length() == reference.bytes && MediaFiles.hash(local) == reference.sha256)
            val remote = session.storage.reference.child("accounts/$uid/$generation/media/${reference.sha256}")
            val existing = try { remote.metadata.awaitResult() } catch (error: StorageException) {
                if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error else null
            }
            if (existing != null) {
                require(existing.sizeBytes == reference.bytes && existing.contentType == reference.contentType && existing.getCustomMetadata("ownerUid") == uid)
                continue
            }
            val metadata = StorageMetadata.Builder().setContentType(reference.contentType).setCustomMetadata("ownerUid", uid)
                .setCustomMetadata("fileId", reference.sha256).setCustomMetadata("role", reference.role).build()
            val key = "$generation-${reference.sha256}"
            val resume = preferences.getString(key, null)?.let(Uri::parse)
            val completed = AtomicBoolean(false)
            val progress = OnProgressListener<UploadTask.TaskSnapshot> { snapshot ->
                if (!completed.get()) snapshot.uploadSessionUri?.let { uri ->
                    check(preferences.edit().putString(key, uri.toString()).commit())
                }
            }
            val executor = java.util.concurrent.Executor { runnable -> com.example.kpkn.data.repository.MediaPersistenceOwner.scope.launch { runnable.run() } }
            var task: UploadTask? = null
            try {
                OwnedStorageTasks.run {
                    val upload = if (resume == null) remote.putFile(Uri.fromFile(local), metadata) else remote.putFile(Uri.fromFile(local), metadata, resume)
                    task = upload
                    upload.addOnProgressListener(executor, progress)
                    upload to { upload.cancel(); Unit }
                }
                owned()
            }
            catch (error: Exception) {
                if (error !is CancellationException) preferences.edit().remove(key).commit()
                throw error
            } finally { completed.set(true); task?.removeOnProgressListener(progress) }
            check(preferences.edit().remove(key).commit())
        }
    }
    suspend fun download(generation: Long, payload: JsonObject?) = withContext(Dispatchers.IO) {
        for (reference in MediaFiles.references(payload)) {
            owned()
            val destination = MediaFiles.local(context, reference)
            if (destination.isFile && destination.length() == reference.bytes && MediaFiles.hash(destination) == reference.sha256) continue
            destination.parentFile!!.mkdirs()
            val staged = File(destination.parentFile, "${reference.sha256}.part")
            val remote = session.storage.reference.child("accounts/$uid/$generation/media/${reference.sha256}")
            val metadata = remote.metadata.awaitResult()
            require(metadata.sizeBytes == reference.bytes && metadata.contentType == reference.contentType && metadata.getCustomMetadata("ownerUid") == uid)
            FirebasePrivateDownloads(session, uid).download(remote.path, staged, reference.bytes, reference.sha256)
            owned()
            require(staged.length() == reference.bytes && MediaFiles.hash(staged) == reference.sha256)
            java.io.FileInputStream(staged).use { it.fd.sync() }
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
