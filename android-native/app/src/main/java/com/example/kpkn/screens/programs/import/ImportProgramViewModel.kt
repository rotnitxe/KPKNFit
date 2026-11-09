package com.example.kpkn.screens.programs.importer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.importer.ImportedEditorDocument
import com.example.kpkn.data.importer.ImportedEditorDraftPersistence
import com.example.kpkn.data.importer.ImportSourceMediaStore
import com.example.kpkn.data.importer.ProgramUrlDocument
import com.example.kpkn.data.importer.ProgramUrlReader
import com.example.kpkn.data.importer.programFileNameOf
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.entitlements.CapabilityCatalog
import com.example.kpkn.domain.entitlements.ProgramAccessPolicy
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.resolvePrimaryMuscleLabel
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportDraftEditor
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportDocumentType
import com.example.kpkn.domain.training.importer.ImportedProgramBuilder
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedProgramSourceMap
import com.example.kpkn.domain.training.importer.ImportSourceEvidence
import com.example.kpkn.domain.training.importer.ImportSourceKind
import com.example.kpkn.domain.training.importer.ImportReadingProgress
import com.example.kpkn.domain.training.importer.ImportReadingStage
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.domain.training.importer.ImportWizardSheet
import com.example.kpkn.domain.training.importer.KnownProgramMatch
import com.example.kpkn.domain.training.importer.KnownProgramRecognizer
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.File
import java.net.URI
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

internal data class ImportMuscleSeries(val label: String, val sets: Int)
internal data class ImportMuscleDistribution(
    val weekIndex: Int,
    val groups: List<ImportMuscleSeries>,
    val unassignedExercises: Int,
)

private const val MAX_SOURCE_EVIDENCE_CHARS = 500_000

/** Persiste la cita de origen sin la ruta pública, que también puede contener identificadores privados. */
private fun publicSourceOrigin(value: String): String? = runCatching {
    val source = URI(value)
    val host = source.host?.removeSurrounding("[", "]")?.lowercase()?.takeIf { it.isNotBlank() }
        ?: return@runCatching null
    if (!source.scheme.equals("https", ignoreCase = true)) return@runCatching null
    val authorityHost = if (':' in host) "[$host]" else host
    val port = source.port.takeIf { it != -1 && it != 443 }?.let { ":$it" }.orEmpty()
    "https://$authorityHost$port"
}.getOrNull()

internal fun effectiveImportedWeekdays(
    imported: ImportedProgramDraft,
    weekdays: Set<Int>,
): Map<String, Int?> = buildMap {
    val structuralDaysPendingReview = imported.warnings
        .asSequence()
        .filter { it.kind == ImportWarningKind.STRUCTURE_REVIEW && it.id !in imported.reviewedWarnings }
        .mapNotNull { it.dayId }
        .toSet()
    imported.weeks.forEach { week ->
        val trainingDays = week.days.filterNot { it.isRestDay }
        val fallback = ImportedProgramBuilder.assignWeekdays(trainingDays.size, weekdays.filter { it in 1..7 }.sorted())
        var trainingIndex = 0
        week.days.forEach { day ->
            val fallbackDay = if (day.isRestDay) null else fallback.getOrNull(trainingIndex++)
            val resolved = if (day.isRestDay) {
                day.dayOfWeek?.takeIf { it in 1..7 }
            } else if (day.id in structuralDaysPendingReview) {
                null
            } else {
                day.dayOfWeek?.takeIf { it in 1..7 } ?: fallbackDay
            }
            put(day.id, resolved)
        }
    }
}

internal fun suggestedImportedWeekdays(sessionCount: Int): List<Int?> {
    val pattern = if (sessionCount <= 5) listOf(1, 2, 3, 4, 5) else (1..7).toList()
    return ImportedProgramBuilder.assignWeekdays(sessionCount, pattern)
}

private data class ImportReadResult(
    val draft: ImportedProgramDraft,
    val knownProgram: KnownProgramMatch?,
    val sourceEvidence: ImportSourceEvidence,
)

/** Lo que muestra la pantalla «Importar programa» (Programas → Nuevo programa → Importar). */
data class ImportProgramState(
    val page: ImportWizardPage = ImportWizardPage.ENTRY,
    /** Last durable review page, retained when the user leaves the wizard or the process is restored. */
    val resumePage: ImportWizardPage = ImportWizardPage.EXERCISES,
    val resumeSheet: ImportWizardSheet? = null,
    val sheet: ImportWizardSheet? = null,
    val sourceEvidence: ImportSourceEvidence = ImportSourceEvidence(),
    val imported: ImportedProgramDraft? = null,
    val title: String = "",
    /** Decisión por nombre importado: configuración del catálogo, o null = ejercicio propio. Sin clave = con dudas. */
    val resolutions: Map<String, String?> = emptyMap(),
    /** Ejercicio cuyo panel de ajuste está abierto (su id). */
    val openedExerciseId: String? = null,
    /** Lo que deshace el siguiente toque; null si no hay nada que deshacer. */
    val undoLabel: String? = null,
    val canUndoWeekdayChange: Boolean = false,
    /** Programa de KPKN que el archivo parece ser (H3f); null = ninguno. Cada lectura nueva lo reinicia. */
    val importKnownPlanId: String? = null,
    /** «Seguir con mi archivo»: el aviso del programa de KPKN no vuelve a salir para esta lectura. */
    val importKnownDismissed: Boolean = false,
    /** Días de entrenamiento con los que se reparten las sesiones al guardar (ISO: 1 = lunes). Al leer, la selección inicial. */
    val weekdays: Set<Int> = emptySet(),
    /** Compatibilidad de borradores antiguos que ya contenían un Program completo; el flujo activo no inicia ese editor. */
    val editorStarted: Boolean = false,
    val editorProgram: Program? = null,
    val editorSourceMap: ImportedProgramSourceMap = ImportedProgramSourceMap(),
    val draftPersistencePending: Boolean = false,
    val draftPersistenceError: String? = null,
    val savedProgramId: String? = null,
    val busy: Boolean = false,
    /** Lo que se muestra mientras se lee: «Leyendo la página N de M…», «Leyendo la foto…» o «Leyendo el programa…». */
    val progress: String? = null,
    val readingProgress: ImportReadingProgress? = null,
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
    private data class WeekdayUndoState(val assignments: Map<String, Int?>, val weekdays: Set<Int>)
    private val catalogRepository: ExerciseCatalogRepositoryV2 =
        ApprovedAssetExerciseCatalogRepositoryV2(application.applicationContext)
    private val catalogMutex = Mutex()
    private val reviewPersistenceMutex = Mutex()
    private val sourceMediaStore = ImportSourceMediaStore(application.filesDir)
    private val draftPersistence = ImportedEditorDraftPersistence(draftFile, sourceMediaStore = sourceMediaStore)
    private val restored = CompletableDeferred<Unit>()
    @Volatile private var reviewMutationGeneration = 0L
    @Volatile private var catalog: ExerciseCatalogV2? = null
    private var importJob: Job? = null
    private var catalogRetryJob: Job? = null
    @Volatile private var editorDraftSession: ImportedEditorDraftSession? = null
    private var editorDocumentJob: Job? = null
    private var editorWeekdayUndo: WeekdayUndoState? = null

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

    /** Visible attachment metadata only; the provider URI and its permissions are deliberately not persisted. */
    internal var readSourceMetadata: suspend (Uri) -> Pair<String?, String?> = { uri ->
        withContext(Dispatchers.IO) {
            runCatching { programFileNameOf(application.contentResolver, uri).ifBlank { null } }.getOrNull() to
                runCatching { application.contentResolver.getType(uri) }.getOrNull()
        }
    }

    /** Lee una página o archivo web público; las pruebas sustituyen el transporte para no usar la red. */
    internal var readUrl: suspend (String) -> ProgramUrlDocument = { url ->
        ProgramUrlReader.read(url, application.cacheDir, ::reportPage)
    }

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
                    if (session != null) _state.value = stateFrom(session.document.value, restoredAtEntry = true)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            page = ImportWizardPage.ERROR,
                            error = error.message ?: "No pudimos abrir el borrador guardado.",
                        )
                    }
                },
            )
            restored.complete(Unit)
        }
    }

    fun importFromSheets(sheets: List<ImportSheet>, title: String) =
        startImport(
            title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE },
            IMPORT_READING_TEXT,
            sourceEvidenceOnFailure = {
                ImportSourceEvidence(
                    kind = ImportSourceKind.FILE,
                    displayName = title.ifBlank { null },
                    documentType = sheets.firstOrNull()?.documentType ?: ImportDocumentType.UNKNOWN,
                )
                    .withBoundedSourceText(sheets.asImportEvidenceText())
                    .withSourceStats(sheets)
            },
        ) { reportStage ->
            withContext(Dispatchers.Default) {
                reportStage(ImportReadingStage.STRUCTURE, null)
                val draft = ProgramImportParser.parseSheets(sheets, title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE })
                reportStage(ImportReadingStage.EXERCISES, draft)
                ImportReadResult(
                    draft = draft,
                    knownProgram = KnownProgramRecognizer.recognizeSheets(title, sheets),
                    sourceEvidence = ImportSourceEvidence(
                        kind = ImportSourceKind.FILE,
                        displayName = title,
                        documentType = sheets.firstOrNull()?.documentType ?: ImportDocumentType.UNKNOWN,
                    ).withBoundedSourceText(sheets.asImportEvidenceText()).withSourceStats(sheets),
                )
            }
        }

    fun importFromText(text: String) =
        startImport(
            ImportedProgramBuilder.DEFAULT_TITLE,
            IMPORT_READING_TEXT,
            sourceEvidenceOnFailure = {
                ImportSourceEvidence(
                    kind = ImportSourceKind.PASTED_TEXT,
                    displayName = "Texto pegado",
                    mediaType = "text/plain",
                    documentType = ImportDocumentType.PASTED_TEXT,
                ).withBoundedSourceText(text).withTextRowCount(text)
            },
        ) { reportStage ->
            withContext(Dispatchers.Default) {
                val prepared = com.example.kpkn.data.importer.TextProgramReader.read(text, "Texto pegado")
                reportStage(ImportReadingStage.STRUCTURE, null)
                val draft = ProgramImportParser.parsePreparedText(prepared, ImportedProgramBuilder.DEFAULT_TITLE)
                reportStage(ImportReadingStage.EXERCISES, draft)
                ImportReadResult(
                    draft = draft,
                    knownProgram = KnownProgramRecognizer.recognizeText(text),
                    sourceEvidence = ImportSourceEvidence(
                        kind = ImportSourceKind.PASTED_TEXT,
                        displayName = "Texto pegado",
                        mediaType = "text/plain",
                        documentType = ImportDocumentType.PASTED_TEXT,
                    ).withBoundedSourceText(text).withTextRowCount(text),
                )
            }
        }

    /** Lee una página pública o un archivo compartido por HTTPS y lo convierte al mismo borrador que los archivos locales. */
    fun importFromUrl(url: String) {
        var copiedMediaId: String? = null
        startImport(
            ImportedProgramBuilder.DEFAULT_TITLE,
            IMPORT_READING_URL_TEXT,
            sourceEvidenceOnFailure = {
                ImportSourceEvidence(kind = ImportSourceKind.PUBLIC_URL, displayName = "Enlace público")
            },
        ) { reportStage ->
            try {
                val document = readUrl(url)
                val documentType = document.sheets.firstOrNull()?.documentType ?: ImportDocumentType.UNKNOWN
                copiedMediaId = sourceMediaStore.retainBytes(document.sourceBytes, documentType)
                val sourceOrigin = publicSourceOrigin(document.sourceUrl)
                withContext(Dispatchers.Default) {
                    reportStage(ImportReadingStage.STRUCTURE, null)
                    val draft = ProgramImportParser.parseSheets(document.sheets, document.title).copy(
                        sourceTitle = document.title,
                        sourceUrl = sourceOrigin,
                    )
                    reportStage(ImportReadingStage.EXERCISES, draft)
                    ImportReadResult(
                        draft = draft,
                        knownProgram = KnownProgramRecognizer.recognizeSheets(document.title, document.sheets),
                        sourceEvidence = ImportSourceEvidence(
                            kind = ImportSourceKind.PUBLIC_URL,
                            displayName = document.displayName ?: document.title,
                            mediaType = document.mediaType,
                            documentType = documentType,
                            sourceMediaId = copiedMediaId,
                            sourcePreviewUnavailable = documentType in setOf(ImportDocumentType.URL_IMAGE, ImportDocumentType.URL_PDF) && copiedMediaId == null,
                            discardedText = document.discardedText,
                            discardedTextTruncated = document.discardedTextTruncated,
                            sourceUrl = sourceOrigin,
                        ).withBoundedSourceText(document.extractedText ?: document.sheets.asImportEvidenceText())
                            .withSourceStats(document.sheets),
                    )
                }
            } catch (cancel: CancellationException) {
                sourceMediaStore.delete(copiedMediaId)
                copiedMediaId = null
                throw cancel
            } catch (error: Throwable) {
                sourceMediaStore.delete(copiedMediaId)
                copiedMediaId = null
                throw error
            }
        }
    }

    /**
     * Lee un archivo elegido o una foto en segundo plano, con su progreso y «Cancelar», y lo importa. Una foto de la cámara
     * ([takenFile]) se borra al terminar de leerla; una de la galería no se toca.
     */
    fun importFromPicked(uri: Uri, photo: Boolean = false, takenFile: File? = null) {
        var sourceMetadata: Pair<String?, String?>? = null
        var copiedMediaId: String? = null
        startImport(
            ImportedProgramBuilder.DEFAULT_TITLE,
            if (photo) IMPORT_READING_PHOTO_TEXT else IMPORT_READING_TEXT,
            sourceEvidenceOnFailure = {
                val metadata = readSourceMetadata(uri).also { sourceMetadata = it }
                ImportSourceEvidence(
                    kind = if (photo) ImportSourceKind.PHOTO else ImportSourceKind.FILE,
                    displayName = metadata.first ?: if (photo) "Foto seleccionada" else "Archivo seleccionado",
                    mediaType = metadata.second,
                )
            },
        ) { reportStage ->
            try {
                val (fileTitle, sheets) = readFile(uri, ::reportPage).getOrThrow()
                val (displayName, mediaType) = sourceMetadata ?: readSourceMetadata(uri)
                val documentType = sheets.firstOrNull()?.documentType ?: ImportDocumentType.UNKNOWN
                copiedMediaId = sourceMediaStore.retain(getApplication<Application>().contentResolver, uri, documentType)
                // Una foto de la cámara no tiene un nombre útil: su programa lleva el título por defecto.
                val title = if (takenFile != null) "" else fileTitle
                withContext(Dispatchers.Default) {
                    reportStage(ImportReadingStage.STRUCTURE, null)
                    val draft = ProgramImportParser.parseSheets(sheets, title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE })
                    reportStage(ImportReadingStage.EXERCISES, draft)
                    ImportReadResult(
                        draft = draft,
                        knownProgram = KnownProgramRecognizer.recognizeSheets(title, sheets),
                        sourceEvidence = ImportSourceEvidence(
                            kind = if (photo) ImportSourceKind.PHOTO else ImportSourceKind.FILE,
                            displayName = displayName ?: fileTitle,
                            mediaType = mediaType,
                            documentType = documentType,
                            sourceMediaId = copiedMediaId,
                            sourcePreviewUnavailable = documentType in setOf(ImportDocumentType.IMAGE, ImportDocumentType.PDF) && copiedMediaId == null,
                        ).withBoundedSourceText(sheets.asImportEvidenceText()).withSourceStats(sheets),
                    )
                }
            } catch (cancel: CancellationException) {
                sourceMediaStore.delete(copiedMediaId)
                copiedMediaId = null
                throw cancel
            } catch (error: Throwable) {
                sourceMediaStore.delete(copiedMediaId)
                copiedMediaId = null
                throw error
            } finally {
                takenFile?.delete()
            }
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
        _state.update {
            if (!it.busy) it else it.copy(
                progress = importReadingPageText(page, pages),
                readingProgress = (it.readingProgress ?: ImportReadingProgress()).copy(
                    stage = ImportReadingStage.DOCUMENT,
                    currentPage = page,
                    totalPages = pages,
                ),
            )
        }
    }

    private fun reportReadingStage(
        stage: ImportReadingStage,
        draft: ImportedProgramDraft? = null,
        matchedNames: Int? = null,
    ) {
        _state.update { current ->
            if (!current.busy) current else {
                val previous = current.readingProgress ?: ImportReadingProgress()
                current.copy(
                    readingProgress = previous.copy(
                        stage = stage,
                        currentPage = null,
                        totalPages = null,
                        weeks = draft?.weeks?.size ?: previous.weeks,
                        sessions = draft?.weeks?.sumOf { week -> week.days.count { !it.isRestDay } } ?: previous.sessions,
                        exercises = draft?.weeks?.sumOf { week -> week.days.sumOf { it.exercises.size } } ?: previous.exercises,
                        matchedNames = matchedNames ?: previous.matchedNames,
                    ),
                )
            }
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message, busy = false)
    }

    /** Changes a native wizard page and durably records the last useful step. */
    fun navigateTo(page: ImportWizardPage) {
        val cancelCatalogRetry = page != ImportWizardPage.EXERCISES && _state.value.notice == IMPORT_MATCHING
        val leavingError = _state.value.page == ImportWizardPage.ERROR
        val evidenceBeforeError = if (leavingError) {
            stateBeforeRead?.sourceEvidence ?: editorDraftSession?.document?.value?.sourceEvidence
        } else {
            null
        }
        if (cancelCatalogRetry) {
            catalogRetryJob?.cancel()
            catalogRetryJob = null
        }
        _state.update { current ->
            val resumePage = when (page) {
                ImportWizardPage.ENTRY, ImportWizardPage.READING, ImportWizardPage.ERROR, ImportWizardPage.SUCCESS -> current.resumePage
                // PASTE is a transient source form. When a reviewed draft already exists, do not replace its
                // resumable route with a form whose unsubmitted contents are not part of that draft.
                ImportWizardPage.PASTE -> if (current.imported != null) current.resumePage else page
                ImportWizardPage.SESSION -> ImportWizardPage.SESSIONS
                else -> page
            }
            current.copy(
                page = page,
                resumePage = resumePage,
                sourceEvidence = if (current.page == ImportWizardPage.ERROR) {
                    evidenceBeforeError ?: current.sourceEvidence
                } else {
                    current.sourceEvidence
                },
                sheet = null,
                error = null,
                busy = if (cancelCatalogRetry) false else current.busy,
                progress = if (cancelCatalogRetry) null else current.progress,
                notice = if (cancelCatalogRetry) null else current.notice,
            )
        }
        if (leavingError) stateBeforeRead = null
        persistWorkflowLocation()
    }

    /** Opens or closes one of the seven design sheets without losing the underlying page. */
    fun showSheet(sheet: ImportWizardSheet?) {
        _state.update { it.copy(sheet = sheet, error = null) }
        persistWorkflowLocation()
    }

    /** Returns from the portfolio to the exact last page and sheet of a restored draft. */
    fun resumeDraft() {
        val current = _state.value
        val resumePage = normalizedReviewPage(current.resumePage)
        _state.value = current.copy(
            page = resumePage,
            resumePage = resumePage,
            sheet = current.resumeSheet?.takeUnless { it == ImportWizardSheet.RESUME },
            error = null,
        )
        persistWorkflowLocation()
    }

    /** Uses the same Free/Pro rule as the final save gate so the review can show the concrete paywall before the last tap. */
    fun planRejection(level: PlanLevel): String? {
        if (level == PlanLevel.PRO) return null
        val current = _state.value
        val program = current.editorProgram ?: editorDraftSession?.document?.value?.program
        if (program != null) return ProgramAccessPolicy.rejection(level, program)
        val imported = current.imported ?: return null
        val sessionsByWeek = imported.weeks.map { week -> week.days.count { !it.isRestDay } }
        return CapabilityCatalog.validateProgramStructure(level, maxOf(1, imported.weeks.size), sessionsByWeek.ifEmpty { listOf(0) })
    }

    /** Re-parses edited extraction text while keeping its original file or URL attribution. */
    fun reanalyzeSourceText(text: String) {
        val evidence = _state.value.sourceEvidence
        val previous = _state.value.imported
        startImport(
            ImportedProgramBuilder.DEFAULT_TITLE,
            IMPORT_READING_TEXT,
            sourceEvidenceOnFailure = { evidence.withBoundedSourceText(text).withTextRowCount(text) },
        ) { reportStage ->
            withContext(Dispatchers.Default) {
                var sheet = com.example.kpkn.data.importer.TextProgramReader.read(
                    text,
                    evidence.displayName ?: "Texto revisado",
                )
                previous?.warnings?.firstOrNull { it.message.startsWith("Origen web:") }?.let { sourceWarning ->
                    sheet = sheet.copy(notes = (sheet.notes + sourceWarning.message).distinct())
                }
                reportStage(ImportReadingStage.STRUCTURE, null)
                val parsed = ProgramImportParser.parsePreparedText(sheet, ImportedProgramBuilder.DEFAULT_TITLE).copy(
                    sourceTitle = previous?.sourceTitle,
                    sourceUrl = previous?.sourceUrl,
                )
                reportStage(ImportReadingStage.EXERCISES, parsed)
                ImportReadResult(
                    draft = parsed,
                    knownProgram = KnownProgramRecognizer.recognizeText(text),
                    sourceEvidence = evidence.withBoundedSourceText(text).withTextRowCount(text),
                )
            }
        }
    }

    private fun persistWorkflowLocation() {
        val current = _state.value
        if (current.imported == null || current.page == ImportWizardPage.SUCCESS) return
        if (!current.editorStarted) {
            scheduleReviewPersistence()
            return
        }
        val session = editorDraftSession ?: return
        viewModelScope.launch {
            val saved = session.updateWorkflow(
                wizardPage = current.resumePage,
                wizardSheet = current.sheet?.takeUnless { it == ImportWizardSheet.RESUME },
                sourceEvidence = current.sourceEvidence,
            )
            if (!saved) reportDraftPersistenceFailure()
        }
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
        if (op is ImportEditOp.SetDayOfWeek) {
            assignImportedDay(op.dayId, op.dayOfWeek)
            return
        }
        val current = _state.value
        if (current.editorStarted) {
            editorWeekdayUndo = null
            _state.update { it.copy(canUndoWeekdayChange = false, undoLabel = null) }
            val session = editorDraftSession ?: return
            viewModelScope.launch {
                val ids = when (op) {
                    is ImportEditOp.IgnoreWarning -> listOf(op.warningId)
                    ImportEditOp.IgnoreAllWarnings -> session.document.value.source.warnings
                        .filter { it.kind == ImportWarningKind.SHEET_NOTE && it.id !in session.document.value.source.reviewedWarnings }.map { it.id }
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
        val before = ImportSnapshot(imported, current.resolutions, current.weekdays)
        val (after, label) = ImportEdits.apply(before, op) { raw -> shownName(raw, current.resolutions) } ?: return
        if (op is ImportEditOp.Decide) op.configId?.let { displayNames[it] = op.name }
        undoStack.push(before, label)
        _state.value = current.copy(
            imported = after.imported,
            resolutions = after.resolutions,
            weekdays = after.weekdays,
            undoLabel = undoStack.label,
            error = null,
        )
        scheduleReviewPersistence()
    }

    /** Reintenta el emparejado automático sin volver a leer ni sustituir el borrador actual. */
    fun retryCatalogMatching() {
        val initial = _state.value
        val source = initial.imported ?: return
        if (initial.page !in setOf(ImportWizardPage.EXERCISES, ImportWizardPage.REVIEW) || initial.busy) return
        // Once the shared editor owns the canonical program, this is only a catalog availability check.
        // Applying source-name matches at that point could silently change the already edited program.
        val names = if (initial.editorStarted) emptyList() else source.weeks
            .flatMap { week -> week.days.flatMap { day -> day.exercises } }
            .filter { it.catalogSelectionId == null && !initial.resolutions.containsKey(it.rawName) }
            .map { it.rawName }
            .distinct()

        _state.update {
            it.copy(busy = true, progress = "Abriendo el catálogo de ejercicios…", notice = IMPORT_MATCHING, error = null)
        }
        catalogRetryJob?.cancel()
        catalogRetryJob = viewModelScope.launch {
            try {
                restored.await()
                val matched = matchNames(names)
                val latest = _state.value
                if (!latest.busy || latest.page !in setOf(ImportWizardPage.EXERCISES, ImportWizardPage.REVIEW) || latest.notice != IMPORT_MATCHING) return@launch
                val latestSource = latest.imported ?: return@launch
                if (!latest.editorStarted) {
                    val stillUnresolved = latestSource.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
                        .filter { it.catalogSelectionId == null && !latest.resolutions.containsKey(it.rawName) }
                        .map { it.rawName }
                        .toSet()
                    val decisions = names.filter { it in stillUnresolved }.mapNotNull { rawName ->
                        matched[rawName]?.let { configId -> ImportEditOp.Decide(rawName, configId, rawName) }
                    }
                    if (decisions.isNotEmpty()) edit(ImportEditOp.Batch(decisions))
                }
                _state.update { current ->
                    if (current.page in setOf(ImportWizardPage.EXERCISES, ImportWizardPage.REVIEW) && current.notice == IMPORT_MATCHING) {
                        current.copy(busy = false, progress = null, notice = null, error = null)
                    } else current
                }
            } catch (cancel: CancellationException) {
                _state.update { current ->
                    if (current.page in setOf(ImportWizardPage.EXERCISES, ImportWizardPage.REVIEW) && current.notice == IMPORT_MATCHING) {
                        current.copy(busy = false, progress = null, notice = null)
                    } else current
                }
                throw cancel
            } catch (_: Exception) {
                _state.update { current ->
                    if (current.page in setOf(ImportWizardPage.EXERCISES, ImportWizardPage.REVIEW) && current.notice == IMPORT_MATCHING) {
                        current.copy(busy = false, progress = null, notice = IMPORT_CATALOG_UNAVAILABLE)
                    } else current
                }
            } finally {
                catalogRetryJob = null
            }
        }
    }

    /** Coloca una sesión en el día elegido y materializa el reparto visible para conservarlo al guardar. */
    fun assignImportedDay(dayId: String, dayOfWeek: Int?) {
        if (dayOfWeek == null || dayOfWeek !in 1..7) return
        val current = _state.value
        val source = current.imported ?: return
        val day = source.weeks.asSequence().flatMap { it.days.asSequence() }
            .firstOrNull { it.id == dayId && !it.isRestDay } ?: return
        val assignments = effectiveImportedWeekdays(source, current.weekdays).toMutableMap()
        assignments[day.id] = dayOfWeek
        persistWeekdayAssignments(current, source, assignments)
    }

    /** Sugiere una distribución de lunes a viernes, con sábado y domingo si hay más de cinco sesiones. */
    fun suggestWeekdaysForWeek(weekIndex: Int) {
        val current = _state.value
        val source = current.imported ?: return
        val week = source.weeks.firstOrNull { it.index == weekIndex } ?: return
        val trainingDays = week.days.filterNot { it.isRestDay }
        val pendingStructureReviewDayIds = source.warnings.asSequence()
            .filter { it.kind == ImportWarningKind.STRUCTURE_REVIEW && it.id !in source.reviewedWarnings }
            .mapNotNull { it.dayId }
            .toSet()
        val missingSourceDays = trainingDays.filter {
            it.dayOfWeek == null && it.id !in pendingStructureReviewDayIds
        }
        val assignments = effectiveImportedWeekdays(source, current.weekdays).toMutableMap()
        val suggestion = suggestedImportedWeekdays(missingSourceDays.size)
        if (missingSourceDays.isEmpty() || suggestion.size != missingSourceDays.size) return
        missingSourceDays.forEachIndexed { index, day -> assignments[day.id] = suggestion[index] }
        persistWeekdayAssignments(current, source, assignments)
    }

    /** Copia el orden de días de la semana visible a las sesiones equivalentes de las demás semanas. */
    fun applyWeekdayPatternToAllWeeks(weekIndex: Int) {
        val current = _state.value
        val source = current.imported ?: return
        val selected = source.weeks.firstOrNull { it.index == weekIndex } ?: return
        val selectedDays = selected.days.filterNot { it.isRestDay }
        if (selectedDays.isEmpty()) return
        val assignments = effectiveImportedWeekdays(source, current.weekdays).toMutableMap()
        val pattern = selectedDays.map { assignments[it.id] }
        source.weeks.filterNot { it.index == weekIndex }.forEach { week ->
            week.days.filterNot { it.isRestDay }.forEachIndexed { index, day ->
                pattern.getOrNull(index)?.let { assignments[day.id] = it }
            }
        }
        persistWeekdayAssignments(current, source, assignments)
    }

    /** Copia el patrón de una semana únicamente a la semana destino, conservando las demás. */
    fun applyWeekdayPatternToWeek(sourceWeekIndex: Int, targetWeekIndex: Int) {
        if (sourceWeekIndex == targetWeekIndex) return
        val current = _state.value
        val source = current.imported ?: return
        val from = source.weeks.firstOrNull { it.index == sourceWeekIndex } ?: return
        val target = source.weeks.firstOrNull { it.index == targetWeekIndex } ?: return
        val pattern = from.days.filterNot { it.isRestDay }
            .map { it.dayOfWeek ?: effectiveImportedWeekdays(source, current.weekdays)[it.id] }
        val targetDays = target.days.filterNot { it.isRestDay }
        if (pattern.isEmpty() || targetDays.isEmpty()) return
        val assignments = effectiveImportedWeekdays(source, current.weekdays).toMutableMap()
        targetDays.forEachIndexed { index, day -> pattern.getOrNull(index)?.let { assignments[day.id] = it } }
        persistWeekdayAssignments(current, source, assignments)
    }

    private fun persistWeekdayAssignments(
        current: ImportProgramState,
        source: ImportedProgramDraft,
        assignments: Map<String, Int?>,
    ) {
        if (current.draftPersistencePending) return
        val previousAssignments = effectiveImportedWeekdays(source, current.weekdays)
        if (assignments == previousAssignments) return
        val selectedWeekdays = source.weeks.flatMap { it.days }
            .filterNot { it.isRestDay }
            .mapNotNull { assignments[it.id]?.takeIf { day -> day in 1..7 } }
            .toSet()
        if (current.editorStarted) {
            val session = editorDraftSession ?: return
            _state.update { it.copy(draftPersistencePending = true, draftPersistenceError = null, error = null) }
            viewModelScope.launch {
                if (!session.updateImportedWeekdays(assignments)) {
                    reportDraftPersistenceFailure()
                    return@launch
                }
                editorWeekdayUndo = WeekdayUndoState(previousAssignments, current.weekdays)
                val document = session.document.value
                _state.update {
                    it.copy(
                        imported = document.source,
                        editorProgram = document.program,
                        editorSourceMap = document.sourceMap,
                        weekdays = document.weekdays,
                        draftPersistencePending = false,
                        draftPersistenceError = null,
                        undoLabel = "Actualizaste el reparto semanal.",
                        canUndoWeekdayChange = true,
                    )
                }
            }
            return
        }

        val edits = source.weeks.flatMap { it.days }
            .filterNot { it.isRestDay }
            .mapNotNull { day ->
                assignments[day.id]?.takeIf { it in 1..7 && it != day.dayOfWeek }?.let { assigned ->
                    ImportEditOp.SetDayOfWeek(day.id, assigned)
                }
            }
        if (edits.isEmpty() && selectedWeekdays == current.weekdays) return
        val before = ImportSnapshot(source, current.resolutions, current.weekdays)
        val after = if (edits.isEmpty()) before else {
            ImportEdits.apply(before, ImportEditOp.Batch(edits)) { raw -> shownName(raw, current.resolutions) }?.first ?: before
        }
        if (after == before && selectedWeekdays == current.weekdays) return
        if (after != before) undoStack.push(before, "Actualizaste el reparto semanal.")
        _state.value = current.copy(
            imported = after.imported,
            weekdays = selectedWeekdays,
            undoLabel = undoStack.label,
            error = null,
        )
        scheduleReviewPersistence()
    }

    /** Deshace el último cambio de la vista previa. */
    fun undo() {
        val current = _state.value
        if (current.editorStarted) {
            val previous = editorWeekdayUndo ?: return
            val session = editorDraftSession ?: return
            if (current.draftPersistencePending) return
            _state.update { it.copy(draftPersistencePending = true, draftPersistenceError = null) }
            viewModelScope.launch {
                if (!session.updateImportedWeekdays(previous.assignments)) {
                    reportDraftPersistenceFailure()
                    return@launch
                }
                val document = session.document.value
                editorWeekdayUndo = null
                _state.update {
                    it.copy(
                        imported = document.source,
                        editorProgram = document.program,
                        editorSourceMap = document.sourceMap,
                        weekdays = document.weekdays,
                        draftPersistencePending = false,
                        draftPersistenceError = null,
                        undoLabel = null,
                        canUndoWeekdayChange = false,
                    )
                }
            }
            return
        }
        val previous = undoStack.pop() ?: return
        _state.value = current.copy(
            imported = previous.imported,
            resolutions = previous.resolutions,
            weekdays = previous.weekdays,
            undoLabel = undoStack.label,
            canUndoWeekdayChange = false,
        )
        scheduleReviewPersistence()
    }

    /** Abre el panel de ajuste de un ejercicio (por su id); null lo cierra. */
    fun open(exerciseId: String?) {
        _state.value = _state.value.copy(openedExerciseId = exerciseId)
    }

    fun clear(onComplete: (() -> Unit)? = null) {
        cancelImport()
        importJob?.cancel()
        importJob = null
        catalogRetryJob?.cancel()
        catalogRetryJob = null
        stateBeforeRead = null
        undoStack.clear()
        val session = editorDraftSession
        if (session == null) {
            _state.value = ImportProgramState()
            onComplete?.invoke()
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
                onComplete?.invoke()
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
                val saved = session.programStore.mutate {
                    val document = session.document.value
                    it.withImportedWeekdays(weekdays, explicitImportedSessionIds(document.source, document.sourceMap))
                }
                if (!saved) reportDraftPersistenceFailure()
            }
        } else {
            _state.update { it.copy(weekdays = weekdays) }
            scheduleReviewPersistence()
        }
    }

    private fun stateFrom(
        document: ImportedEditorDocument,
        restoredAtEntry: Boolean = false,
    ): ImportProgramState {
        val restoredLocation = if (restoredAtEntry) restoredWizardLocation(document) else null
        val resumeSheet = if (restoredLocation != null) restoredLocation.second else document.wizardSheet
        val savedPage = normalizedReviewPage(document.wizardPage)
        return ImportProgramState(
            page = when {
                document.savedProgramId != null -> ImportWizardPage.SUCCESS
                restoredAtEntry -> ImportWizardPage.ENTRY
                else -> savedPage
            },
            resumePage = restoredLocation?.first ?: savedPage,
            resumeSheet = resumeSheet,
            sheet = if (restoredAtEntry) null else document.wizardSheet,
            sourceEvidence = document.sourceEvidence,
            imported = document.source,
            title = document.title,
            resolutions = document.resolutions,
            weekdays = document.weekdays,
            editorStarted = document.editingStarted,
            editorProgram = document.program,
            editorSourceMap = document.sourceMap,
            savedProgramId = document.savedProgramId,
        )
    }

    /** Drops routes whose screen-local state cannot be restored from the durable document. */
    private fun restoredWizardLocation(document: ImportedEditorDocument): Pair<ImportWizardPage, ImportWizardSheet?> {
        val safePage = when (document.wizardPage) {
            ImportWizardPage.ENTRY,
            ImportWizardPage.PASTE,
            ImportWizardPage.READING,
            ImportWizardPage.ERROR,
            ImportWizardPage.SUCCESS,
            ImportWizardPage.SESSION -> if (document.editingStarted) ImportWizardPage.SESSIONS else ImportWizardPage.EXERCISES
            ImportWizardPage.SUMMARY -> ImportWizardPage.EXERCISES
            else -> document.wizardPage
        }
        val sheet = document.wizardSheet.takeIf {
            safePage == document.wizardPage && it != ImportWizardSheet.STRUCTURE
        }
        return safePage to sheet
    }

    private fun normalizedReviewPage(page: ImportWizardPage): ImportWizardPage =
        if (page == ImportWizardPage.SUMMARY) ImportWizardPage.EXERCISES else page

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
                    page = previous.page,
                    resumePage = document.wizardPage,
                    resumeSheet = document.wizardSheet,
                    sheet = previous.sheet,
                    sourceEvidence = document.sourceEvidence,
                    openedExerciseId = previous.openedExerciseId,
                    undoLabel = previous.undoLabel,
                    importKnownPlanId = previous.importKnownPlanId,
                    importKnownDismissed = previous.importKnownDismissed,
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

    /** Waits for the current durable review snapshot before a visible "Guardar y salir" action leaves the wizard. */
    suspend fun flushPendingReview(): Boolean = persistReviewNow()

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
                wizardPage = current.resumePage,
                wizardSheet = current.sheet,
                sourceEvidence = current.sourceEvidence,
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

    /** Primary-muscle set counts for one training week; exercises without catalog metadata remain unassigned. */
    internal suspend fun primaryMuscleSeriesForReview(): ImportMuscleDistribution? {
        val current = _state.value
        val imported = current.imported ?: return null
        val sourceWeek = imported.weeks.firstOrNull { week ->
            !week.executionKind.name.contains("REST", ignoreCase = true) &&
                week.days.any { day -> !day.isRestDay && day.exercises.isNotEmpty() }
        } ?: return null
        val totals = linkedMapOf<String, Int>()
        var unassigned = 0
        fun add(sets: Int, muscles: List<com.example.kpkn.data.models.InvolvedMuscle>) {
            val label = muscles.takeIf { it.isNotEmpty() }?.let { involved ->
                resolvePrimaryMuscleLabel(ExerciseMuscleInfo(id = "import-preview", name = "", involvedMuscles = involved))
            }
            if (label.isNullOrBlank() || label == "General") unassigned++
            else totals[label] = (totals[label] ?: 0) + sets
        }

        if (current.editorStarted) {
            val canonicalWeek = current.editorProgram?.macrocycles?.asSequence()
                ?.flatMap { it.blocks.asSequence() }
                ?.flatMap { it.mesocycles.asSequence() }
                ?.flatMap { it.weeks.asSequence() }
                ?.firstOrNull { it.name == "Semana ${sourceWeek.index}" }
                ?: return null
            canonicalWeek.sessions.flatMap { it.allExercises() }
                .forEach { exercise -> add(exercise.sets.size, exercise.effectiveMuscles.orEmpty()) }
        } else {
            val configurationIds = ImportedProgramBuilder.catalogConfigurationIds(imported, current.resolutions)
            val catalogInfo = if (configurationIds.isEmpty()) emptyMap() else {
                loadedCatalog().toLegacyConfigurationLookup(configurationIds)
            }
            val customInfo = com.example.kpkn.data.repository.CustomExerciseRepository.customExercises.value
                .filter { it.isCustom }
                .associateBy { it.id.lowercase() }
            sourceWeek.days.filterNot { it.isRestDay }.flatMap { it.exercises }.forEach { exercise ->
                val id = exercise.catalogSelectionId ?: current.resolutions[exercise.rawName]
                val info = id?.takeUnless { it.equals("own", ignoreCase = true) }?.let { selectedId ->
                    customInfo[selectedId.lowercase()] ?: catalogInfo[selectedId.lowercase()]
                }
                add(exercise.sets, info?.involvedMuscles.orEmpty())
            }
        }
        return ImportMuscleDistribution(
            weekIndex = sourceWeek.index,
            groups = totals.map { (label, sets) -> ImportMuscleSeries(label, sets) }.sortedByDescending { it.sets },
            unassignedExercises = unassigned,
        )
    }

    /** Guarda el importado cuando ejercicios, avisos y distribución semanal están resueltos. */
    suspend fun save(level: PlanLevel): String? {
        restored.await()
        awaitCatalogMatching()
        if (!persistReviewNow()) return null
        val current = _state.value
        val imported = current.imported ?: return null
        val pendingWarnings = imported.warnings.count { it.id !in imported.reviewedWarnings }
        if (pendingWarnings > 0) {
            _state.update { it.copy(notice = "Revisa o descarta los avisos del documento antes de continuar.", error = null) }
            return null
        }
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
            document.program ?: run {
                reportError("El borrador editado no contiene un programa recuperable.")
                return null
            }
        } else {
            val readyCatalog = try {
                loadedCatalog()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.update { it.copy(sheet = null, notice = IMPORT_CATALOG_UNAVAILABLE, error = null) }
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
                programId = document.program?.id ?: "import-${document.id}",
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
            _state.update { it.copy(sheet = null, notice = IMPORT_CATALOG_UNAVAILABLE, error = null) }
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
            _state.value = current.copy(notice = IMPORT_PRO_NOTICE, sheet = ImportWizardSheet.PRO, error = null)
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
        sourceEvidenceOnFailure: suspend () -> ImportSourceEvidence? = { null },
        read: suspend (reportStage: (ImportReadingStage, ImportedProgramDraft?) -> Unit) -> ImportReadResult,
    ) {
        catalogRetryJob?.cancel()
        catalogRetryJob = null
        if (_state.value.notice == IMPORT_MATCHING) {
            _state.update { it.copy(busy = false, progress = null, notice = null) }
        }
        importJob?.cancel()
        importJob = viewModelScope.launch {
            var attemptedEvidence: ImportSourceEvidence? = null
            var attemptedMediaId: String? = null
            var mediaCommitted = false
            try {
                restored.await()
                if (stateBeforeRead == null) {
                    stateBeforeRead = _state.value.let { previous ->
                        if (previous.notice == IMPORT_MATCHING) previous.copy(notice = null) else previous
                    }
                }
                _state.value = _state.value.copy(
                    page = ImportWizardPage.READING,
                    sheet = null,
                    busy = true,
                    progress = progress,
                    readingProgress = ImportReadingProgress(),
                    error = null,
                    notice = null,
                )
                attemptedEvidence = try {
                    sourceEvidenceOnFailure()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    null
                }
                val result = read { stage, draft -> reportReadingStage(stage, draft) }
                val parsed = result.draft
                val known = result.knownProgram
                val evidence = result.sourceEvidence
                attemptedMediaId = evidence.sourceMediaId
                val source = ImportDraftEditor.withIds(parsed)
                val title = source.title.ifBlank { defaultTitle }
                val normalizedSource = source.copy(title = title)
                val names = normalizedSource.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
                    .map { it.rawName }
                    .distinct()
                ensureActive()

                var catalogUnavailable = false
                val confident = if (names.isEmpty()) {
                    emptyMap()
                } else {
                    reportReadingStage(ImportReadingStage.CATALOG, normalizedSource)
                    try {
                        matchNames(names)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        catalogUnavailable = true
                        emptyMap()
                    }
                }
                ensureActive()

                val previousSession = editorDraftSession
                val previousDocument = previousSession?.document?.value
                val draftId = UUID.randomUUID().toString()
                val days = normalizedSource.weeks.flatMap { it.days }
                val trainingDays = days.filterNot { it.isRestDay }
                val explicitDays = trainingDays.mapNotNull { it.dayOfWeek?.takeIf { day -> day in 1..7 } }.toSet()
                val weekdays = if (explicitDays.isNotEmpty() && trainingDays.all { it.dayOfWeek != null }) explicitDays else
                    importInitialWeekdays(importSessionsPerWeek(normalizedSource)) + explicitDays
                if (names.isNotEmpty()) reportReadingStage(ImportReadingStage.DAYS, normalizedSource, confident.size)
                val initialDocument = ImportedEditorDocument(
                    id = draftId,
                    revision = 0L,
                    source = normalizedSource,
                    resolutions = confident,
                    title = title,
                    weekdays = weekdays,
                    wizardPage = if (names.isEmpty()) ImportWizardPage.EMPTY else ImportWizardPage.EXERCISES,
                    sourceEvidence = evidence,
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
                        notice = if (catalogUnavailable) IMPORT_CATALOG_UNAVAILABLE else null,
                    )
                    mediaCommitted = true
                    next
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                val failed = _state.value
                val before = stateBeforeRead ?: failed
                _state.value = before.copy(
                    page = ImportWizardPage.ERROR,
                    busy = false,
                    progress = null,
                    readingProgress = failed.readingProgress,
                    sheet = null,
                    sourceEvidence = attemptedEvidence ?: before.sourceEvidence,
                    error = error.message ?: "No pudimos leer el programa.",
                )
            } finally {
                if (!mediaCommitted) sourceMediaStore.delete(attemptedMediaId)
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

private fun ImportSourceEvidence.withBoundedSourceText(text: String?): ImportSourceEvidence {
    if (text == null) return this
    return copy(
        sourceText = text.take(MAX_SOURCE_EVIDENCE_CHARS),
        sourceTextTruncated = text.length > MAX_SOURCE_EVIDENCE_CHARS,
    )
}

private fun ImportSourceEvidence.withSourceStats(sheets: List<ImportSheet>): ImportSourceEvidence = copy(
    sheetCount = sheets.size,
    rowCount = sheets.sumOf { sheet -> sheet.rows.count { row -> row.any(String::isNotBlank) } },
    pageCount = sheets.sumOf { sheet -> maxOf(sheet.ocrPages.size, sheet.pageStarts.size) }.takeIf { it > 0 },
)

private fun ImportSourceEvidence.withTextRowCount(text: String): ImportSourceEvidence = copy(
    rowCount = text.lineSequence().count { it.isNotBlank() },
)

private fun List<ImportSheet>.asImportEvidenceText(): String = joinToString("\n\n") { sheet ->
    buildString {
        append("## ").append(sheet.name).append('\n')
        sheet.rows.forEach { row ->
            append(row.joinToString("\t")).append('\n')
        }
    }.trimEnd()
}
