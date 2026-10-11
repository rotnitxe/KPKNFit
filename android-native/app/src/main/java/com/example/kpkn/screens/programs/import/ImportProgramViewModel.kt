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
import com.example.kpkn.domain.training.importer.ImportDraftValidation
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
    /** Ejercicio cuyo panel de ajuste está abierto (su id). */
    val openedExerciseId: String? = null,
    /** Lo que deshace el siguiente toque; null si no hay nada que deshacer. */
    val undoLabel: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Estado y acciones de la pantalla de importación de Programas. Lee y empareja como el paso del alta, edita el programa
 * leído con deshacer y lo guarda con el repositorio de siempre, sujeto a los límites del nivel (`ProgramAccessPolicy`).
 */
class ImportProgramViewModel(application: Application) : AndroidViewModel(application) {
    private val catalogRepository: ExerciseCatalogRepositoryV2 =
        ApprovedAssetExerciseCatalogRepositoryV2(application.applicationContext)
    private val catalogMutex = Mutex()
    @Volatile private var catalog: ExerciseCatalogV2? = null
    private var importJob: Job? = null

    /** Pila de deshacer de las ediciones: en memoria; se vacía al leer otro archivo o al empezar de nuevo. */
    private val undoStack = ImportUndoStack()

    /** Nombre que se muestra por configuración del catálogo, para los textos de deshacer. */
    private val displayNames = mutableMapOf<String, String>()

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

    /** Decide un nombre importado: [configId] del catálogo, o null = ejercicio propio. Pasa por la pila de deshacer. */
    fun resolve(rawName: String, configId: String?) =
        edit(ImportEditOp.Decide(rawName, configId, name = configId?.let { displayNames[it] } ?: rawName))

    /** Aplica una edición de la vista previa y la deja en la pila de deshacer. Sin cambios, no hace nada. */
    fun edit(op: ImportEditOp) {
        val current = _state.value
        val imported = current.imported ?: return
        val before = ImportSnapshot(imported, current.resolutions)
        val (after, label) = ImportEdits.apply(before, op) { raw -> shownName(raw, current.resolutions) } ?: return
        if (op is ImportEditOp.Decide) op.configId?.let { displayNames[it] = op.name }
        undoStack.push(before, label)
        _state.value = current.copy(
            imported = after.imported,
            resolutions = after.resolutions,
            undoLabel = undoStack.label,
            error = null,
        )
    }

    /** Deshace el último cambio de la vista previa. */
    fun undo() {
        val current = _state.value
        val previous = undoStack.pop() ?: return
        _state.value = current.copy(
            imported = previous.imported,
            resolutions = previous.resolutions,
            undoLabel = undoStack.label,
        )
    }

    /** Abre el panel de ajuste de un ejercicio (por su id); null lo cierra. */
    fun open(exerciseId: String?) {
        _state.value = _state.value.copy(openedExerciseId = exerciseId)
    }

    fun clear() {
        importJob?.cancel()
        undoStack.clear()
        _state.value = ImportProgramState()
    }

    suspend fun suggestions(rawName: String): List<ExerciseNameCandidate> =
        withContext(Dispatchers.Default) { ImportCatalogLookup.suggestions(loadedCatalog(), rawName) }.also(::rememberNames)

    suspend fun search(query: String): List<ExerciseNameCandidate> =
        withContext(Dispatchers.Default) { ImportCatalogLookup.search(loadedCatalog(), query) }.also(::rememberNames)

    suspend fun namesOf(configIds: Set<String>): Map<String, String> =
        withContext(Dispatchers.Default) { ImportCatalogLookup.namesOf(loadedCatalog(), configIds) }
            .also { displayNames.putAll(it) }

    /**
     * Guarda el programa importado si la revisión final está limpia y el nivel lo permite. Devuelve el id guardado, o null
     * con el aviso que toca: el primer problema de la revisión, o [IMPORT_PRO_NOTICE] si el límite Free lo rechaza.
     */
    suspend fun save(level: PlanLevel): String? {
        val current = _state.value
        val imported = current.imported ?: return null
        if (imported.weeks.none { week -> week.days.any { it.exercises.isNotEmpty() } }) {
            reportError("No encontramos ejercicios en lo que importaste.")
            return null
        }
        ImportDraftValidation.check(imported) { current.resolutions.containsKey(it) }.firstOrNull()?.let { first ->
            reportError(importIssueLine(first, imported))
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
        undoStack.clear()
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

    /** Lo que se muestra de un nombre importado: el del catálogo si se eligió uno, o el del archivo. */
    private fun shownName(rawName: String, resolutions: Map<String, String?>): String =
        resolutions[rawName]?.let { displayNames[it] } ?: rawName

    private fun rememberNames(candidates: List<ExerciseNameCandidate>) {
        candidates.forEach { displayNames[it.configurationId] = it.displayName }
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
