package com.example.kpkn.data.sync

import android.content.Context
import com.example.kpkn.data.account.AccountServiceException
import com.example.kpkn.data.account.FirebaseSession
import com.example.kpkn.data.account.awaitResult
import com.example.kpkn.data.backup.BoundedInput
import com.example.kpkn.domain.sync.PrivateDataReference
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Hash-addressed structured data is private and available in Free; it never shares media quotas. */
class PrivateDataTransport(private val context: Context, private val uid: String, private val session: FirebaseSession) {
    fun stage(payload: JsonObject): PrivateDataReference {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_BYTES)
        val hash = PersonalRecordCodec.sha256(bytes)
        val file = staged(hash)
        if (!file.isFile || file.length() != bytes.size.toLong() || MediaFiles.hash(file) != hash) {
            val temporary = File(file.parentFile, "$hash-${java.util.UUID.randomUUID()}.part")
            try {
                java.io.FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
                java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
        return PrivateDataReference(hash, bytes.size.toLong())
    }
    fun readStaged(reference: PrivateDataReference): JsonObject {
        require(reference.bytes in 1..MAX_BYTES.toLong())
        val file = staged(reference.sha256)
        require(file.isFile && file.length() == reference.bytes)
        val bytes = file.inputStream().use { BoundedInput.bytes(it, reference.bytes) }
        require(PersonalRecordCodec.sha256(bytes) == reference.sha256)
        return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
    }
    suspend fun upload(generation: Long, reference: PrivateDataReference) = withContext(Dispatchers.IO) {
        owned()
        val file = staged(reference.sha256)
        require(file.isFile && file.length() == reference.bytes)
        val hash = file.inputStream().use { PersonalRecordCodec.sha256(BoundedInput.bytes(it, MAX_BYTES.toLong())) }
        require(hash == reference.sha256)
        val metadata = StorageMetadata.Builder().setContentType("application/json")
            .setCustomMetadata("ownerUid", uid).setCustomMetadata("fileId", hash).build()
        val remote = session.storage.reference.child("accounts/$uid/$generation/data/$hash")
        val present = try { remote.metadata.awaitResult() } catch (error: com.google.firebase.storage.StorageException) {
            if (error.errorCode != com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND) throw error
            null
        }
        if (present == null) OwnedStorageTasks.run {
            val task = remote.putFile(android.net.Uri.fromFile(file), metadata)
            task to { task.cancel(); Unit }
        }
        else require(present.sizeBytes == reference.bytes && present.contentType == "application/json" && present.getCustomMetadata("ownerUid") == uid)
        owned()
        Unit
    }
    suspend fun download(generation: Long, reference: PrivateDataReference): JsonObject = withContext(Dispatchers.IO) {
        owned()
        require(reference.sha256.matches(Regex("[a-f0-9]{64}")) && reference.bytes in 1..MAX_BYTES.toLong())
        val destination = File(context.noBackupFilesDir, "sync-transfer/${reference.sha256}.download.part").also { it.parentFile?.mkdirs() }
        var verified = false
        try {
            val remote = session.storage.reference.child("accounts/$uid/$generation/data/${reference.sha256}")
            val metadata = remote.metadata.awaitResult()
            require(metadata.sizeBytes == reference.bytes && metadata.contentType == "application/json" && metadata.getCustomMetadata("ownerUid") == uid)
            FirebasePrivateDownloads(session, uid).download(remote.path, destination, reference.bytes, reference.sha256)
            owned()
            require(destination.length() == reference.bytes)
            val bytes = destination.inputStream().use { BoundedInput.bytes(it, reference.bytes) }
            require(PersonalRecordCodec.sha256(bytes) == reference.sha256)
            verified = true
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } finally { if (verified) destination.delete() }
    }
    private fun owned() { if (session.auth.currentUser?.uid != uid) throw AccountServiceException("UNAUTHENTICATED") }
    private fun staged(hash: String): File {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        return File(context.noBackupFilesDir, "sync-transfer/$hash.json").also { check(it.parentFile!!.isDirectory || it.parentFile!!.mkdirs()) }
    }
    companion object { const val MAX_BYTES = 64 * 1024 * 1024 }
}
