package com.example.kpkn.data.sync

import android.content.Context
import com.example.kpkn.data.backup.BoundedInput
import com.example.kpkn.data.backup.PortableBackup
import com.example.kpkn.data.backup.PortableFile
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Frozen files are published before rows; retries verify every destination. */
internal object GuestSnapshotFiles {
    private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private const val MAX_FILE = 512L * 1024 * 1024
    fun freeze(source: Context, root: File): List<PortableFile> {
        var total = 0L
        val entries = mutableListOf<PortableFile>()
        PortableBackup.portableRoots.sorted().forEach { name ->
            val origin = PortableBackup.portableFile(source, name).canonicalFile
            val files = if (origin.isDirectory) origin.walkTopDown().onEnter { directory ->
                require(directory.canonicalFile.toPath().startsWith(origin.toPath()))
                true
            }.filter(File::isFile) else sequenceOf(origin).filter(File::isFile)
            files.forEach { file ->
                val canonical = file.canonicalFile
                require(canonical == origin || canonical.toPath().startsWith(origin.toPath()))
                val path = if (canonical == origin) name else "$name/${canonical.relativeTo(origin).invariantSeparatorsPath}"
                require(entries.size < 20_000 && canonical.length() in 0..MAX_FILE)
                total += canonical.length(); require(total <= MAX_TOTAL)
                val entry = PortableFile(path, canonical.length(), MediaFiles.hash(canonical))
                copyVerified(canonical, File(root, "frozen/$path"), entry)
                entries += entry
            }
        }
        return entries
    }
    fun publish(root: File, target: Context, id: String, entries: List<PortableFile>): Map<String, String> {
        require(id.matches(Regex("[A-Za-z0-9-]{1,128}")))
        return entries.associate { entry ->
            safe(entry.path)
            val destination = if (entry.path == "legacy-body-recovery.json")
                // Retain opaque legacy recovery evidence under a portable root.
                File(target.filesDir, "profile/guest-imported/$id/${entry.path}") else {
                val category = entry.path.substringBefore('/')
                File(PortableBackup.portableFile(target, category), "guest-imported/$id/${entry.path.substringAfter('/', "data")}")
            }
            copyVerified(File(root, "frozen/${entry.path}"), destination, entry)
            entry.path to destination.canonicalPath
        }
    }
    fun remap(value: JsonElement, paths: Map<String, String>, depth: Int = 0): JsonElement {
        require(depth <= 48)
        return when (value) {
            is JsonObject -> JsonObject(value.mapValues { remap(it.value, paths, depth + 1) })
            is JsonArray -> JsonArray(value.map { remap(it, paths, depth + 1) })
            is JsonPrimitive -> if (value.isString) paths[value.content]?.let(::JsonPrimitive) ?: value else value
            else -> value
        }
    }
    fun copyVerified(source: File, destination: File, entry: PortableFile) {
        require(source.isFile && source.length() == entry.bytes && MediaFiles.hash(source) == entry.sha256) { "Guest snapshot is incomplete" }
        if (destination.isFile && destination.length() == entry.bytes && MediaFiles.hash(destination) == entry.sha256) return
        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
        val part = File(destination.parentFile, destination.name + ".guest-part")
        try {
            source.inputStream().use { input -> FileOutputStream(part).use { output ->
                BoundedInput.copy(input, output, entry.bytes); output.fd.sync()
            } }
            require(part.length() == entry.bytes && MediaFiles.hash(part) == entry.sha256)
            Files.move(part.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { part.delete() }
    }
    private fun safe(path: String) {
        require(path.length <= 1024 && !path.contains('\\') && path.split('/').none { it.isBlank() || it == "." || it == ".." } &&
            path.substringBefore('/') in PortableBackup.portableRoots)
    }
}
