package com.example.kpkn.data.media

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object WorkoutMediaZipExporter {
    fun writeZip(filesDir: File, output: OutputStream): Int {
        val root = WorkoutMediaStore(filesDir).root()
        if (!root.isDirectory) {
            ZipOutputStream(output).use { /* empty archive */ }
            return 0
        }
        var count = 0
        ZipOutputStream(output).use { zip ->
            root.walkTopDown().filter { it.isFile && it.length() > 0L }.forEach { file ->
                val relative = file.relativeTo(root).path.replace('\\', '/')
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
                count += 1
            }
        }
        return count
    }
}
