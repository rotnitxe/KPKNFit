    @Test fun inspectNativeIntegrity(): Unit = runBlocking(Dispatchers.IO) {
        val context = profile("A")
        val name = "restore_${java.util.UUID.randomUUID()}.db"
        val probe = context.getDatabasePath(name)
        java.util.zip.ZipFile(archive).use { zip ->
            zip.getInputStream(zip.getEntry("database.db")).use { input -> probe.outputStream().use { input.copyTo(it) } }
        }
        fun checkFile(file: File): String = android.database.sqlite.SQLiteDatabase.openDatabase(
            file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { sql ->
            val messages = mutableListOf<String>()
            sql.rawQuery("PRAGMA integrity_check", null).use { cursor -> while (cursor.moveToNext()) messages += cursor.getString(0) }
            messages.joinToString(" | ")
        }
        val before = checkFile(probe)
        val rewrite = PortableBackup::class.java.getDeclaredMethod("rewriteFiles", Context::class.java, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        rewrite.invoke(PortableBackup, context, name, false)
        val after = checkFile(probe)
        val rollback = checkFile(File(context.noBackupFilesDir, "maintenance/rollback.db"))
        val checkpoint = android.database.sqlite.SQLiteDatabase.openDatabase(probe.absolutePath, null, 0).use { sql ->
            sql.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor -> cursor.moveToFirst(); (0..2).joinToString(",") { cursor.getInt(it).toString() } }
        }
        val result = "before=$before; after=$after; rollback=$rollback; checkpoint=$checkpoint; afterCheckpoint=${checkFile(probe)}"
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("kpkn_integrity_probe", result) })
        File(control, "integrity.txt").writeText(result)
        context.deleteDatabase(name)
    }

