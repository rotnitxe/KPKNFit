package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.entitlements.ProgramAccessPolicy
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportedProgramBuilder
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Aviso del límite Free al guardar un programa importado de más de una semana o de más de cinco sesiones. */
internal const val IMPORT_PRO_NOTICE = "Los programas de más de una semana o de más de 5 sesiones son de KPKN Pro."

/** Lo que muestra la pantalla «Importar programa» (Programas → Nuevo programa → Importar). */
data class ImportProgramState(
    val imported: ImportedProgramDraft? = null,
    val title: String = "",
    /** Decisión por nombre importado: configuración del catálogo, o null = ejercicio propio. Sin clave = con dudas. */
    val resolutions: Map<String, String?> = emptyMap(),
    /** Ejercicio cuyo panel «Elegir» está abierto. */
    val openedName: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Estado y acciones de la pantalla de importación de Programas. Lee y empareja como el paso del alta, pero guarda el
 * programa con el repositorio de siempre, sujeto a los límites del nivel (`ProgramAccessPolicy`).
 */
class ImportProgramViewModel(application: Application) : AndroidViewModel(application) {
    private val catalogRepository: ExerciseCatalogRepositoryV2 =
        ApprovedAssetExerciseCatalogRepositoryV2(application.applicationContext)
    private val catalogMutex = Mutex()
    @Volatile private var catalog: ExerciseCatalogV2? = null
    private var importJob: Job? = null

    /** Guarda el programa; las pruebas lo sustituyen. Producción: el repositorio de programas. */
    internal var saveProgram: suspend (Program) -> Result<Unit> = { ProgramRepository.getInstance().addProgramNow(it) }

    private val _state = MutableStateFlow(ImportProgramState())
    val state: StateFlow<ImportProgramState> = _state.asStateFlow()

    fun importFromSheets(sheets: List<ImportSheet>, title: String) =
        startImport(title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE }) {
            ProgramImportParser.parseSheets(sheets, title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE })
        }

    fun importFromText(text: String) =
        startImport(ImportedProgramBuilder.DEFAULT_TITLE) {
            ProgramImportParser.parseText(text, ImportedProgramBuilder.DEFAULT_TITLE)
        }

    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message, busy = false)
    }

    fun setTitle(title: String) {
        _state.value = _state.value.copy(title = title)
    }

    fun resolve(rawName: String, configId: String?) {
        _state.value = _state.value.copy(resolutions = _state.value.resolutions + (rawName to configId))
    }

    fun open(rawName: String?) {
        _state.value = _state.value.copy(openedName = rawName)
    }

    fun clear() {
        importJob?.cancel()
        _state.value = ImportProgramState()
    }

    suspend fun suggestions(rawName: String): List<ExerciseNameCandidate> =
        withContext(Dispatchers.Default) { ImportCatalogLookup.suggestions(loadedCatalog(), rawName) }

    suspend fun search(query: String): List<ExerciseNameCandidate> =
        withContext(Dispatchers.Default) { ImportCatalogLookup.search(loadedCatalog(), query) }

    suspend fun namesOf(configIds: Set<String>): Map<String, String> =
        withContext(Dispatchers.Default) { ImportCatalogLookup.namesOf(loadedCatalog(), configIds) }

    /**
     * Guarda el programa importado si el nivel lo permite. Devuelve el id guardado, o null con el aviso que toca
     * ([IMPORT_PRO_NOTICE] si el límite Free lo rechaza). Un programa con dudas sin decidir tampoco se guarda.
     */
    suspend fun save(level: PlanLevel): String? {
        val current = _state.value
        val imported = current.imported ?: return null
        if (imported.weeks.none { week -> week.days.any { it.exercises.isNotEmpty() } }) {
            reportError("No encontramos ejercicios en lo que importaste.")
            return null
        }
        if (imported.weeks.flatMap { week -> week.days.flatMap { it.exercises } }
                .any { !current.resolutions.containsKey(it.rawName) }
        ) {
            reportError("Elige o deja como está los ejercicios con dudas.")
            return null
        }
        val program = ImportedProgramBuilder.build(
            draft = imported.copy(title = current.title),
            resolved = current.resolutions,
            weekdays = emptyList(),
            programId = "import-${UUID.randomUUID()}",
            catalog = loadedCatalog(),
        )
        if (ProgramAccessPolicy.rejection(level, program) != null) {
            _state.value = current.copy(notice = IMPORT_PRO_NOTICE, error = null)
            return null
        }
        return saveProgram(program).fold(
            onSuccess = {
                _state.value = current.copy(notice = null, error = null)
                program.id
            },
            onFailure = {
                _state.value = current.copy(error = "No pudimos guardar el programa.", notice = null)
                null
            },
        )
    }

    private fun startImport(defaultTitle: String, parse: () -> ImportedProgramDraft) {
        importJob?.cancel()
        _state.value = ImportProgramState(busy = true)
        importJob = viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) { parse() }
                val names = parsed.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
                    .map { it.rawName }
                    .distinct()
                val confident = withContext(Dispatchers.Default) {
                    com.example.kpkn.domain.training.importer.ExerciseNameMatcher(loadedCatalog()).matchAll(names)
                        .filterValues { it.isConfident }
                        .mapValues { (_, match) -> match.best?.configurationId }
                }
                _state.value = ImportProgramState(
                    imported = parsed,
                    title = parsed.title.ifBlank { defaultTitle },
                    resolutions = confident,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                _state.value = ImportProgramState(error = error.message ?: "No pudimos leer el programa.")
            }
        }
    }

    /** El catálogo de ejercicios, cargado una sola vez; sin él no hay emparejado ni nombres. */
    private suspend fun loadedCatalog(): ExerciseCatalogV2 = catalog ?: catalogMutex.withLock {
        catalog ?: run {
            withContext(Dispatchers.IO) { catalogRepository.load() }
            val ready = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog
                ?: error("El catálogo de ejercicios todavía no está disponible")
            ready.also { catalog = it }
        }
    }
}
