package com.example.kpkn.data.export

import android.content.Context
import android.net.Uri
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

interface CsvExportPort {
    suspend fun prepare(domains: Set<CsvExportDomain>): CsvExportBundle
    suspend fun write(destination: Uri, bundle: CsvExportBundle)
}

/** El selector del sistema concede el destino; no se crean archivos de exportación en caché. */
class CsvExportService(private val context: Context, private val program: ProgramRepository, private val database: () -> KpknDatabase) : CsvExportPort {
    override suspend fun prepare(domains: Set<CsvExportDomain>): CsvExportBundle = withContext(Dispatchers.IO) {
        check(withTimeoutOrNull(30_000L) { program.isReady.first { it } } == true) { "Los datos todavía no están disponibles. Inténtalo de nuevo." }
        val snapshot = CsvSnapshotLoader(database(), program.settings.value).load(domains)
        CsvDomainRows.build(snapshot, domains, LocalDate.now())
    }
    override suspend fun write(destination: Uri, bundle: CsvExportBundle) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(destination, "wt") ?: error("No se pudo abrir el destino elegido.")
        output.use(bundle::writeTo)
    }
}
