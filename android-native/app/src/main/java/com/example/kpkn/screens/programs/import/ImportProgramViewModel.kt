package com.example.kpkn.screens.programs.importer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.importer.ImportedEditorDocument
import com.example.kpkn.data.importer.ImportedEditorDraftPersistence
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.entitlements.ProgramAccessPolicy
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportDraftEditor
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportedProgramBuilder
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedProgramSourceMap
import com.example.kpkn.domain.training.importer.KnownProgramMatch
import com.example.kpkn.domain.training.importer.KnownProgramRecognizer
import com.example.kpkn.domain.training.importer.ProgramImportParser
import com.example.kpkn.screens.sessioneditor.ImportedProgramEditorStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Aviso del límite Free al guardar un programa importado de más de una semana o de más de cinco sesiones. */
internal const val IMPORT_PRO_NOTICE = "Los programas de más de una semana o de más de 5 sesiones son de KPKN Pro."

/** Lo que dice la revisión, y el aviso al guardar, cuando no hay ningún día de entrenamiento elegido. */
internal const val IMPORT_WEEKDAYS_MISSING = "Elige al menos un día de entrenamiento."
internal const val IMPORT_CATALOG_UNAVAILABLE = "Leímos el programa, pero no pudimos abrir el catálogo de ejercicios. Vuelve a intentar elegirlos antes de guardar."
internal const val IMPORT_MATCHING = "Buscando coincidencias en el catálogo…"
internal fun importedExerciseCatalogChoiceMessage(name: String): String =
    "Elige «$name» en el catálogo o créalo allí antes de continuar."

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
    /** Programa de KPKN que el archivo parece ser (H3f); null = ninguno. Cada lectura nueva lo reinicia. */
    val importKnownPlanId: String? = null,
    /** «Seguir con mi archivo»: el aviso del programa de KPKN no vuelve a salir para esta lectura. */
    val importKnownDismissed: Boolean = false,
    /** Días de entrenamiento con los que se reparten las sesiones al guardar (ISO: 1 = lunes). Al leer, la selección inicial. */
    val weekdays: Set<Int> = emptySet(),
    /** Full Program is the canonical model after the real SessionEditor has been opened. */
    val editorStarted: Boolean = false,
    val editorProgram: Program? = null,
    val editorSourceMap: ImportedProgramSourceMap = ImportedProgramSourceMap(),
    /** Active session editor target; held in the ViewModel so rotation reattaches the same ViewModelStore. */
    val editorActiveSessionId: String? = null,
    val draftPersistencePending: Boolean = false,
    val draftPersistenceError: String? = null,
    val savedProgramId: String? = null,
    val busy: Boolean = false,
    /** Lo que se muestra mientras se lee: «Leyendo la página N de M…», «Leyendo la foto…» o «Leyendo el programa…». */
    val progress: String? = null,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Estado y acciones de la pantalla de importación de Programas. Lee y empareja como el paso del alta, edita el programa
 * leído con deshacer y lo guarda con el repositorio de siempre, sujeto a los límites del nivel (`ProgramAccessPolicy`).
 */
class ImportProgramViewModel @JvmOverloads constructor(
    application: Application,
    draftFile: File = File(application.filesDir, "imported-program-editor-v1.json"),
) : AndroidViewModel(application) {
    private val catalogRepository: ExerciseCatalogRepositoryV2 =
        ApprovedAssetExerciseCatalogRepositoryV2(application.applicationContext)
    private val catalogMutex = Mutex()
    private val reviewPersistenceMutex = Mutex()
    private val draftPersistence = ImportedEditorDraftPersistence(draftFile)
    private val restored = CompletableDeferred<Unit>()
    @Volatile private var reviewMutationGeneration = 0L
    @Volatile private var catalog: ExerciseCatalogV2? = null
    private var importJob: Job? = null
    @Volatile private var editorDraftSession: ImportedEditorDraftSession? = null
    private var editorDocumentJob: Job? = null

    /** Lo que había antes de empezar a leer: «Cancelar» vuelve a ello tal cual. Null si no hay lectura en curso. */
    private var stateBeforeRead: ImportProgramState? = null

    /** Pila de deshacer de las ediciones: en memoria; se vacía al leer otro archivo o al empezar de nuevo. */
    private val undoStack = ImportUndoStack()

    /** Nombre que se muestra por configuración del catálogo, para los textos de deshacer. */
    private val displayNames = mutableMapOf<String, String>()

    /** Guarda el programa; las pruebas lo sustituyen. Producción: el repositorio de programas. */
    internal var saveProgram: suspend (Program) -> Result<Unit> = { ProgramRepository.getInstance().addProgramNow(it) }

    /** Consulta idempotente antes de guardar; se inyecta para que la prueba no dependa del singleton global. */
    internal var findExistingProgram: (String) -> Program? = { id -> ProgramRepository.getInstance().getProgramById(id) }

    /**
     * Lee un archivo elegido o una foto con sus hojas y su título, y avisa de cada página de un PDF. Las pruebas lo sustituyen
     * por uno falso con progreso.
     */
    internal var readFile: suspend (uri: Uri, onProgress: (page: Int, pages: Int) -> Unit) -> Result<Pair<String, List<ImportSheet>>> =
        { uri, onProgress -> readPickedFile(application.contentResolver, uri, application.cacheDir, onProgress) }

    internal var matchNames: suspend (List<String>) -> Map<String, String?> = { names ->
        withContext(Dispatchers.Default) {
            val matcher = com.example.kpkn.domain.training.importer.ExerciseNameMatcher(loadedCatalog())
            matcher.matchAll(names)
                .filterValues(matcher::canResolveAutomatically)
                .mapValues { (_, match) -> match.best?.configurationId }
        }
    }

    private val _state = MutableStateFlow(ImportProgramState())
    val state: StateFlow<ImportProgramState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val restoredSession = ImportedEditorDraftSession.restore(draftPersistence)
            restoredSession.fold(
                onSuccess = { session ->
                    editorDraftSession = session
                    session?.let(::observeEditorDocument)
                    if (session != null) _state.value = stateFrom(session.document.value)
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message ?: "No pudimos abrir el borrador guardado.") }
                },
            )
            restored.complete(Unit)
        }
    }

    fun importFromSheets(sheets: List<ImportSheet>, title: String) =
        startImport(title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE }, IMPORT_READING_TEXT) {
            withContext(Dispatchers.Default) {
                ProgramImportParser.parseSheets(sheets, title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE }) to
                    KnownProgramRecognizer.recognizeSheets(title, sheets)
            }
        }

    fun importFromText(text: String) =
        startImport(ImportedProgramBuilder.DEFAULT_TITLE, IMPORT_READING_TEXT) {
            withContext(Dispatchers.Default) {
                ProgramImportParser.parseText(text, ImportedProgramBuilder.DEFAULT_TITLE) to
                    KnownProgramRecognizer.recognizeText(text)
            }
        }

    /**
     * Lee un archivo elegido o una foto en segundo plano, con su progreso y «Cancelar», y lo importa. Una foto de la cámara
     * ([takenFile]) se borra al terminar de leerla; una de la galería no se toca.
     */
    fun importFromPicked(uri: Uri, photo: Boolean = false, takenFile: File? = null) =
        startImport(ImportedProgramBuilder.DEFAULT_TITLE, if (photo) IMPORT_READING_PHOTO_TEXT else IMPORT_READING_TEXT) {
            try {
                val (fileTitle, sheets) = readFile(uri, ::reportPage).getOrThrow()
                // Una foto de la cámara no tiene un nombre útil: su programa lleva el título por defecto.
                val title = if (takenFile != null) "" else fileTitle
                withContext(Dispatchers.Default) {
                    ProgramImportParser.parseSheets(sheets, title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE }) to
                        KnownProgramRecognizer.recognizeSheets(title, sheets)
                }
            } finally {
                takenFile?.delete()
            }
        }

    /** «Cancelar» de la lectura: corta el trabajo y deja la pantalla como estaba antes de empezar, sin error. */
    fun cancelImport() {
        val before = stateBeforeRead ?: return
        importJob?.cancel()
        importJob = null
        stateBeforeRead = null
        _state.value = before
    }

    /** Avisa de la página de un PDF que se lee; solo mientras hay una lectura (un aviso tardío tras cancelar no cambia nada). */
    private fun reportPage(page: Int, pages: Int) {
        _state.update { if (it.busy) it.copy(progress = importReadingPageText(page, pages)) else it }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message, busy = false)
    }

    fun setTitle(title: String) {
        val current = _state.value
        if (current.editorStarted) {
            val session = editorDraftSession ?: return
            viewModelScope.launch {
                val saved = session.programStore.mutate { it.copy(name = title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE }) }
                if (!saved) reportDraftPersistenceFailure()
            }
        } else {
            _state.value = current.copy(title = title)
            scheduleReviewPersistence()
        }
    }

    /** Decide un nombre importado: [configId] del catálogo, o null = ejercicio propio. Pasa por la pila de deshacer. */
    fun resolve(rawName: String, configId: String?) =
        edit(ImportEditOp.Decide(rawName, configId, name = configId?.let { displayNames[it] } ?: rawName))

    /** Aplica una edición de la vista previa y la deja en la pila de deshacer. Sin cambios, no hace nada. */
    fun edit(op: ImportEditOp) {
        val current = _state.value
        if (current.editorStarted) {
            val session = editorDraftSession ?: return
            viewModelScope.launch {
                val ids = when (op) {
                    is ImportEditOp.IgnoreWarning -> listOf(op.warningId)
                    ImportEditOp.IgnoreAllWarnings -> session.document.value.source.warnings
                        .filter { it.id !in session.document.value.source.reviewedWarnings }.map { it.id }
                    is ImportEditOp.Batch -> op.ops.filterIsInstance<ImportEditOp.IgnoreWarning>().map { it.warningId }
                    else -> emptyList()
                }
                if (ids.isEmpty() && op !is ImportEditOp.IgnoreAllWarnings) return@launch
                for (id in ids) if (!session.reviewWarning(id)) {
                    reportDraftPersistenceFailure()
                    return@launch
                }
            }
            return
        }
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
        scheduleReviewPersistence()
    }

    /** Deshace el último cambio de la vista previa. */
    fun undo() {
        val current = _state.value
        if (current.editorStarted) return
        val previous = undoStack.pop() ?: return
        _state.value = current.copy(
            imported = previous.imported,
            resolutions = previous.resolutions,
            undoLabel = undoStack.label,
        )
        scheduleReviewPersistence()
    }

    /** Abre el panel de ajuste de un ejercicio (por su id); null lo cierra. */
    fun open(exerciseId: String?) {
        _state.value = _state.value.copy(openedExerciseId = exerciseId)
    }

    fun clear() {
        cancelImport()
        importJob?.cancel()
        importJob = null
        stateBeforeRead = null
        undoStack.clear()
        val session = editorDraftSession
        if (session == null) {
            _state.value = ImportProgramState()
            return
        }
        _state.update { it.copy(draftPersistencePending = true, draftPersistenceError = null, error = null) }
        viewModelScope.launch {
            restored.await()
            val result = session.discard()
            if (result.getOrNull() == true) {
                editorDocumentJob?.cancel()
                editorDocumentJob = null
                editorDraftSession = null
                _state.value = ImportProgramState()
            } else {
                _state.update {
                    it.copy(
                        draftPersistencePending = false,
                        draftPersistenceError = result.exceptionOrNull()?.message ?: "No se pudo quitar el borrador. No se borró.",
                        error = result.exceptionOrNull()?.message ?: "No se pudo quitar el borrador. No se borró.",
                    )
                }
            }
        }
    }

    /** «Seguir con mi archivo» (H3f): el aviso del programa de KPKN se descarta para esta lectura. */
    fun dismissImportKnown() {
        _state.value = _state.value.copy(importKnownDismissed = true)
    }

    /** Marca o desmarca un día de entrenamiento (ISO: 1 = lunes). Es una elección, no una edición: no pasa por deshacer. */
    fun toggleWeekday(day: Int) {
        if (day !in 1..7) return
        val current = _state.value
        val weekdays = if (day in current.weekdays) current.weekdays - day else current.weekdays + day
        if (current.editorStarted) {
            val session = editorDraftSession ?: return
            viewModelScope.launch {
                val saved = session.programStore.mutate { it.withImportedWeekdays(weekdays) }
                if (!saved) reportDraftPersistenceFailure()
            }
        } else {
            _state.update { it.copy(weekdays = weekdays) }
            scheduleReviewPersistence()
        }
    }

    /** Opens the existing full editor for one imported day after its complete Program is durably staged. */
    internal suspend fun openSessionEditor(
        weekIndex: Int,
        dayId: String,
    ): Pair<ImportedProgramEditorStore, ImportedSessionEditorTarget>? {
        restored.await()
        awaitCatalogMatching()
        if (!persistReviewNow()) return null
        val session = editorDraftSession ?: return null
        val current = _state.value
        val source = current.imported ?: return null
        val document = session.document.value
        if (!document.editingStarted) {
            val readyCatalog = try {
                loadedCatalog()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                reportError(IMPORT_CATALOG_UNAVAILABLE)
                return null
            }
            val customExercises = com.example.kpkn.data.repository.CustomExerciseRepository.customExercises.value
            val unavailable = ImportedProgramBuilder.unavailableNames(source, current.resolutions, readyCatalog, customExercises)
            if (unavailable.isNotEmpty()) {
                reportError(importedExerciseCatalogChoiceMessage(unavailable.first()))
                return null
            }
            val program = ImportedProgramBuilder.build(
                draft = source.copy(title = current.title),
                resolved = current.resolutions,
                weekdays = current.weekdays.sorted(),
                programId = document.program.id,
                catalog = readyCatalog,
                customExercises = customExercises,
                catalogConfigurationInfo = readyCatalog.toLegacyConfigurationLookup(
                    ImportedProgramBuilder.catalogConfigurationIds(source, current.resolutions),
                ),
            )
            val sourceMap = ImportedProgramBuilder.sourceMap(source, current.resolutions, program.id)
            if (!session.beginEditing(program, sourceMap)) {
                reportDraftPersistenceFailure()
                return null
            }
        }
        val latest = session.document.value
        val exists = latest.source.weeks.any { week -> week.index == weekIndex && week.days.any { it.id == dayId } }
        if (!exists) {
            reportError("No pudimos localizar esa sesión del borrador.")
            return null
        }
        val sessionId = latest.sourceMap.sessionIdsByDayId[dayId]
        val target = sessionId?.let(latest.program::editorTarget)
        if (target == null) {
            reportError("No pudimos localizar la sesión completa del borrador.")
            return null
        }
        _state.update {
            it.copy(
                editorStarted = true,
                editorProgram = latest.program,
                editorSourceMap = latest.sourceMap,
                editorActiveSessionId = target.sessionId,
                undoLabel = null,
                draftPersistenceError = null,
                error = null,
            )
        }
        undoStack.clear()
        return session.programStore to target
    }

    /** Opens a canonical session after returning from the editor or restoring the screen after rotation. */
    internal suspend fun openCanonicalSessionEditor(
        sessionId: String,
    ): Pair<ImportedProgramEditorStore, ImportedSessionEditorTarget>? {
        restored.await()
        val session = editorDraftSession ?: return null
        val document = session.document.value
        if (!document.editingStarted) return null
        val target = document.program.editorTarget(sessionId) ?: return null
        _state.update { it.copy(editorActiveSessionId = sessionId, editorProgram = document.program) }
        return session.programStore to target
    }

    /** Gives an empty import an empty session shell, then opens SessionEditor; it never creates sample sets. */
    internal suspend fun addEmptySessionAndOpen(targetWeekId: String? = null): Pair<ImportedProgramEditorStore, ImportedSessionEditorTarget>? {
        restored.await()
        awaitCatalogMatching()
        val current = _state.value
        val source = current.imported ?: return null
        if (current.editorStarted) {
            if (targetWeekId == null) {
                val firstSession = current.editorProgram?.macrocycles.orEmpty().flatMap { it.blocks }
                    .flatMap { it.mesocycles }.flatMap { it.weeks }.firstNotNullOfOrNull { it.sessions.firstOrNull() }
                if (firstSession != null) return openCanonicalSessionEditor(firstSession.id)
            }
            val session = editorDraftSession ?: return null
            var createdSessionId: String? = null
            val saved = session.programStore.mutate { program ->
                val (updated, sessionId) = program.withEmptyImportedSession(targetWeekId) ?: return@mutate null
                createdSessionId = sessionId
                updated
            }
            if (!saved) {
                reportDraftPersistenceFailure()
                return null
            }
            return createdSessionId?.let { openCanonicalSessionEditor(it) }
        }
        val day = source.weeks.firstNotNullOfOrNull { week -> week.days.firstOrNull()?.let { week.index to it } }
            ?: run {
                edit(ImportEditOp.AddEmptyFirstDay)
                if (!persistReviewNow()) return null
                val updated = _state.value.imported ?: return null
                updated.weeks.firstNotNullOfOrNull { week -> week.days.firstOrNull()?.let { week.index to it } }
            }
            ?: return null
        return openSessionEditor(day.first, day.second.id)
    }

    internal fun activeEditorStore(): ImportedProgramEditorStore? =
        editorDraftSession?.programStore?.takeIf { _state.value.editorActiveSessionId != null }

    internal fun closeEditorPortal(sessionId: String) {
        _state.update { if (it.editorActiveSessionId == sessionId) it.copy(editorActiveSessionId = null) else it }
    }

    private fun stateFrom(document: ImportedEditorDocument): ImportProgramState = ImportProgramState(
        imported = document.source,
        title = document.title,
        resolutions = document.resolutions,
        weekdays = document.weekdays,
        editorStarted = document.editingStarted,
        editorProgram = document.program,
        editorSourceMap = document.sourceMap,
        savedProgramId = document.savedProgramId,
    )

    private fun observeEditorDocument(session: ImportedEditorDraftSession) {
        editorDocumentJob?.cancel()
        editorDocumentJob = viewModelScope.launch {
            // Callers hydrate _state from session.document before this observer is installed. Ignore the initial
            // StateFlow replay so a queued review edit cannot be overwritten by that already-applied snapshot.
            var isInitialSnapshot = true
            session.document.collect { document ->
                if (isInitialSnapshot) {
                    isInitialSnapshot = false
                    return@collect
                }
                if (editorDraftSession !== session) return@collect
                val previous = _state.value
                // Review edits already live in this ViewModel. A durable write can lag behind a newer tap, so
                // publishing its older document here would roll back the UI (and can lose rapid decisions).
                // The document becomes authoritative for the screen only after the complete editor is staged.
                if (!document.editingStarted && !previous.editorStarted) return@collect
                _state.value = stateFrom(document).copy(
                    openedExerciseId = previous.openedExerciseId,
                    undoLabel = previous.undoLabel,
                    importKnownPlanId = previous.importKnownPlanId,
                    importKnownDismissed = previous.importKnownDismissed,
                    editorActiveSessionId = previous.editorActiveSessionId,
                    busy = previous.busy,
                    progress = previous.progress,
                    error = previous.error,
                    notice = previous.notice,
                    draftPersistencePending = false,
                    draftPersistenceError = null,
                )
            }
        }
    }

    private fun scheduleReviewPersistence() {
        if (_state.value.editorStarted || _state.value.imported == null) return
        synchronized(this) { reviewMutationGeneration += 1 }
        _state.update { it.copy(draftPersistencePending = true, draftPersistenceError = null) }
        viewModelScope.launch { persistReviewNow() }
    }

    /** Serializes review edits and writes the latest snapshot before an editor handoff or library commit. */
    private suspend fun persistReviewNow(): Boolean {
        restored.await()
        return reviewPersistenceMutex.withLock { persistReviewLocked() }
    }

    private suspend fun persistReviewLocked(): Boolean {
        while (true) {
            val generation = synchronized(this) { reviewMutationGeneration }
            val current = _state.value
            val imported = current.imported ?: return true
            val session = editorDraftSession ?: return false
            if (session.document.value.editingStarted) return true
            _state.update { it.copy(draftPersistencePending = true, draftPersistenceError = null) }
            val saved = session.updateReview(
                source = imported,
                resolutions = current.resolutions,
                title = current.title,
                weekdays = current.weekdays,
            )
            if (generation != synchronized(this) { reviewMutationGeneration }) continue
            var isLatest = false
            withContext(Dispatchers.Main.immediate) {
                if (generation == synchronized(this@ImportProgramViewModel) { reviewMutationGeneration }) {
                    if (saved) {
                        _state.update { it.copy(draftPersistencePending = false, draftPersistenceError = null) }
                    } else {
                        val message = "No se pudo guardar el borrador. No se guardó el programa."
                        _state.update { it.copy(draftPersistencePending = false, draftPersistenceError = message, error = message) }
                    }
                    isLatest = true
                }
            }
            if (isLatest) return saved
        }
    }

    private fun reportDraftPersistenceFailure() {
        val message = "No se pudo guardar el borrador del editor. No se guardó el programa."
        _state.update { it.copy(draftPersistencePending = false, draftPersistenceError = message, error = message) }
    }

    private suspend fun awaitCatalogMatching() {
        if (_state.value.notice == IMPORT_MATCHING) importJob?.join()
    }

    suspend fun suggestions(rawName: String): List<ExerciseNameCandidate> =
        withContext(Dispatchers.Default) { ImportCatalogLookup.suggestions(loadedCatalog(), rawName) }.also(::rememberNames)

    suspend fun search(query: String): List<ExerciseNameCandidate> =
        withContext(Dispatchers.Default) { ImportCatalogLookup.search(loadedCatalog(), query) }.also(::rememberNames)

    suspend fun namesOf(configIds: Set<String>): Map<String, String> {
        if (configIds.isEmpty()) return emptyMap()
        val custom = com.example.kpkn.data.repository.CustomExerciseRepository.customExercises.value
            .filter { it.id in configIds && it.isCustom }.associate { it.id to it.name }
        val remaining = configIds - custom.keys
        val names = if (remaining.isEmpty()) custom else custom + withContext(Dispatchers.Default) {
            ImportCatalogLookup.namesOf(loadedCatalog(), remaining)
        }
        return names.also { displayNames.putAll(it) }
    }

    /**
     * Guarda el programa importado si la revisión final está limpia, hay días de entrenamiento y el nivel lo permite. Devuelve el
     * id guardado, o null con el aviso que toca: el primer problema de la revisión, [IMPORT_WEEKDAYS_MISSING] o
     * [IMPORT_PRO_NOTICE] si el límite Free lo rechaza.
     */
    suspend fun save(level: PlanLevel): String? {
        restored.await()
        awaitCatalogMatching()
        if (!persistReviewNow()) return null
        val current = _state.value
        val imported = current.imported ?: return null
        val session = editorDraftSession ?: run {
            reportError("No pudimos abrir el borrador durable. No se guardó el programa.")
            return null
        }
        val document = session.document.value
        document.savedProgramId?.let { alreadySaved ->
            _state.update { it.copy(savedProgramId = alreadySaved, notice = null, error = null) }
            return alreadySaved
        }
        val program = if (document.editingStarted) {
            document.program
        } else {
            val readyCatalog = try {
                loadedCatalog()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.update { it.copy(error = IMPORT_CATALOG_UNAVAILABLE) }
                return null
            }
            val customExercises = com.example.kpkn.data.repository.CustomExerciseRepository.customExercises.value
            val unavailable = ImportedProgramBuilder.unavailableNames(imported, current.resolutions, readyCatalog, customExercises)
            if (unavailable.isNotEmpty()) {
                reportError(importedExerciseCatalogChoiceMessage(unavailable.first()))
                return null
            }
            ImportedProgramBuilder.build(
                draft = imported.copy(title = current.title),
                resolved = current.resolutions,
                weekdays = current.weekdays.sorted(),
                programId = document.program.id,
                catalog = readyCatalog,
                customExercises = customExercises,
                catalogConfigurationInfo = readyCatalog.toLegacyConfigurationLookup(
                    ImportedProgramBuilder.catalogConfigurationIds(imported, current.resolutions),
                ),
            )
        }
        val issues = if (document.editingStarted) {
            canonicalImportIssues(imported, program, document.sourceMap, document.resolutions)
        } else {
            ImportDraftValidation.checkOccurrences(imported) {
                it.catalogSelectionId != null || current.resolutions.containsKey(it.rawName)
            }
        }
        issues.firstOrNull()?.let { first ->
            reportError(importIssueLine(first, imported))
            return null
        }
        if (document.weekdays.isEmpty()) {
            reportError(IMPORT_WEEKDAYS_MISSING)
            return null
        }
        val exercises = program.macrocycles.flatMap { macro ->
            macro.blocks.flatMap { block -> block.mesocycles.flatMap { meso ->
                meso.weeks.flatMap { week -> week.sessions.flatMap { it.allExercises() } }
            } }
        }
        if (exercises.isEmpty()) {
            reportError("No encontramos ejercicios en lo que importaste.")
            return null
        }
        if (exercises.any { it.sets.isEmpty() }) {
            reportError("Completa las series de cada ejercicio en el editor antes de guardar.")
            return null
        }
        val readyCatalog = try {
            loadedCatalog()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            _state.update { it.copy(error = IMPORT_CATALOG_UNAVAILABLE) }
            return null
        }
        val customExercises = com.example.kpkn.data.repository.CustomExerciseRepository.customExercises.value
        val unavailable = if (document.editingStarted) {
            ImportedProgramBuilder.unavailableProgramExercises(program, readyCatalog, customExercises)
        } else {
            emptyList()
        }
        if (unavailable.isNotEmpty()) {
            reportError(importedExerciseCatalogChoiceMessage(unavailable.first()))
            return null
        }
        if (ProgramAccessPolicy.rejection(level, program) != null) {
            _state.value = current.copy(notice = IMPORT_PRO_NOTICE, error = null)
            return null
        }
        val committed = if (findExistingProgram(program.id) != null) {
            Result.success(Unit)
        } else {
            saveProgram(program)
        }
        return committed.fold(
            onSuccess = {
                val marked = session.markSaved(program.id)
                if (marked) {
                    val removed = session.discard()
                    if (removed.getOrNull() == true) {
                        editorDraftSession = null
                        editorDocumentJob?.cancel()
                        editorDocumentJob = null
                    }
                }
                _state.update {
                    it.copy(
                        savedProgramId = program.id,
                        notice = if (marked && session.document.value.savedProgramId == program.id) null
                        else "El programa quedó guardado; el borrador se conservará hasta poder verificar su cierre.",
                        error = null,
                        draftPersistencePending = false,
                    )
                }
                program.id
            },
            onFailure = {
                _state.update { it.copy(error = "No pudimos guardar el programa.", notice = null) }
                null
            },
        )
    }

    /**
     * Lee e importa en un trabajo cancelable, con [progress] mientras tanto. Una lectura nueva cancela la anterior, y el estado
     * de antes de la primera se guarda para «Cancelar». Al terminar bien, la pila de deshacer se vacía (el programa es otro); si
     * falla, sale el error.
     */
    private fun startImport(
        defaultTitle: String,
        progress: String,
        read: suspend () -> Pair<ImportedProgramDraft, KnownProgramMatch?>,
    ) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            try {
                restored.await()
                if (stateBeforeRead == null) {
                    stateBeforeRead = _state.value.let { previous ->
                        if (previous.notice == IMPORT_MATCHING) previous.copy(notice = null) else previous
                    }
                }
                _state.value = ImportProgramState(busy = true, progress = progress)
                val (parsed, known) = read()
                val source = ImportDraftEditor.withIds(parsed)
                val title = source.title.ifBlank { defaultTitle }
                val normalizedSource = source.copy(title = title)
                val names = normalizedSource.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
                    .map { it.rawName }
                    .distinct()
                ensureActive()

                val previousSession = editorDraftSession
                val previousDocument = previousSession?.document?.value
                val draftId = UUID.randomUUID().toString()
                val programId = "import-$draftId"
                val weekdays = importInitialWeekdays(importSessionsPerWeek(normalizedSource))
                val initialProgram = ImportedProgramBuilder.build(
                    draft = normalizedSource,
                    resolved = emptyMap(),
                    weekdays = weekdays.sorted(),
                    programId = programId,
                )
                val initialDocument = ImportedEditorDocument(
                    id = draftId,
                    revision = 0L,
                    source = normalizedSource,
                    resolutions = emptyMap(),
                    title = title,
                    weekdays = weekdays,
                    program = initialProgram,
                    sourceMap = ImportedProgramBuilder.sourceMap(normalizedSource, emptyMap(), programId),
                )
                val nextSession = withContext(NonCancellable) {
                    val replacement = if (previousDocument == null) {
                        ImportedEditorDraftSession.create(draftPersistence, initialDocument)
                    } else {
                        ImportedEditorDraftSession.replace(
                            draftPersistence,
                            initialDocument,
                            previousDocument.id,
                            previousDocument.revision,
                        )
                    }
                    val next = replacement.getOrElse { throw it }
                    editorDraftSession = next
                    observeEditorDocument(next)
                    if (previousSession != null && previousSession !== next) previousSession.retire()
                    stateBeforeRead = null
                    undoStack.clear()
                    _state.value = stateFrom(next.document.value).copy(
                        importKnownPlanId = known?.planId,
                        notice = if (names.isNotEmpty()) IMPORT_MATCHING else null,
                    )
                    next
                }
                if (names.isEmpty()) return@launch
                var catalogUnavailable = false
                val confident = try {
                    matchNames(names)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    catalogUnavailable = true
                    emptyMap()
                }
                ensureActive()
                _state.update { current ->
                    // Una edición o decisión posterior a la lectura prevalece sobre una búsqueda que llegó tarde.
                    val untouched = current.imported === normalizedSource && current.resolutions.isEmpty() && !current.editorStarted
                    current.copy(
                        resolutions = if (untouched) confident else current.resolutions,
                        notice = if (current.notice == IMPORT_MATCHING) {
                            if (catalogUnavailable) IMPORT_CATALOG_UNAVAILABLE else null
                        } else current.notice,
                    )
                }
                persistReviewNow()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                val before = stateBeforeRead ?: _state.value
                stateBeforeRead = null
                _state.value = before.copy(busy = false, progress = null, error = error.message ?: "No pudimos leer el programa.")
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
