package com.example.kpkn.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.domain.training.importer.ImportReadingProgress
import com.example.kpkn.domain.training.importer.ImportReadingStage
import com.example.kpkn.domain.training.importer.ImportSourceEvidence
import com.example.kpkn.domain.training.importer.ImportSourceKind
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.screens.programs.importer.ImportCatalogSheetContent
import com.example.kpkn.screens.programs.importer.ImportDaysIntro
import com.example.kpkn.screens.programs.importer.ImportEmptyPage
import com.example.kpkn.screens.programs.importer.ImportEntryPage
import com.example.kpkn.screens.programs.importer.ImportErrorPage
import com.example.kpkn.screens.programs.importer.ImportExtractedTextSheetContent
import com.example.kpkn.screens.programs.importer.ImportProSheetContent
import com.example.kpkn.screens.programs.importer.ImportPastePage
import com.example.kpkn.screens.programs.importer.ImportProgramStructureOverview
import com.example.kpkn.screens.programs.importer.ImportProgramMatchesPage
import com.example.kpkn.screens.programs.importer.ImportReadingPage
import com.example.kpkn.screens.programs.importer.ImportResumeSheetContent
import com.example.kpkn.screens.programs.importer.ImportReviewIntro
import com.example.kpkn.screens.programs.importer.ImportSessionMenuContent
import com.example.kpkn.screens.programs.importer.ImportSessionEditorPortal
import com.example.kpkn.screens.programs.importer.ImportSessionsIntro
import com.example.kpkn.screens.programs.importer.ImportSuccessPage
import com.example.kpkn.screens.programs.importer.ImportSummaryPage
import com.example.kpkn.screens.programs.importer.ImportWeeklyPlanner
import com.example.kpkn.screens.programs.importer.ImportWizardDock
import com.example.kpkn.screens.programs.importer.ImportWizardHeader
import com.example.kpkn.screens.programs.importer.ImportWarningsSheetContent
import com.example.kpkn.screens.sessioneditor.ImportedProgramEditorStore
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.theme.KPKNTheme

/** SOLO DEPURACIÓN: capturas de páginas del importador con datos locales efímeros, sin leer ni escribir Room. */
class ImportProgramVisualHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "reading"
        setContent {
            KPKNTheme {
                if (scenario == "session-editor") {
                    ImportProgramVisualEditorHarness(onClose = ::finish)
                } else {
                    ImportProgramVisualHarness(scenario = scenario, onClose = ::finish)
                }
            }
        }
    }
}

private data class VisualEditorExercise(
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val loadKg: Double,
    val rir: Int,
    val restSeconds: Int,
    val equipment: String,
    val muscle: String,
)

@androidx.compose.runtime.Composable
private fun ImportProgramVisualEditorHarness(onClose: () -> Unit) {
    val program = remember {
        val prescriptions = listOf(
            VisualEditorExercise("Press de banca con barra", 4, 6, 8, 100.0, 2, 180, "Barra", "Pecho"),
            VisualEditorExercise("Press inclinado con mancuernas", 3, 8, 10, 32.0, 2, 120, "Mancuerna", "Pecho"),
            VisualEditorExercise("Aperturas en polea", 3, 12, 15, 15.0, 1, 75, "Polea", "Pecho"),
            VisualEditorExercise("Elevaciones laterales", 3, 12, 15, 8.0, 2, 75, "Mancuerna", "Hombro"),
            VisualEditorExercise("Extensión de tríceps en polea", 3, 10, 12, 22.5, 2, 90, "Polea", "Tríceps"),
            VisualEditorExercise("Press francés con barra Z", 4, 10, 12, 20.0, 2, 90, "Barra Z", "Tríceps"),
        )
        val exercises = prescriptions.mapIndexed { exerciseIndex, prescription ->
            Exercise(
                id = "visual-editor-exercise-${exerciseIndex + 1}",
                name = prescription.name,
                restTime = prescription.restSeconds,
                setupDetails = ExerciseSetupDetails(equipmentNotes = prescription.equipment),
                effectiveMuscles = listOf(InvolvedMuscle(muscle = prescription.muscle)),
                sets = List(prescription.sets) { setIndex ->
                    ExerciseSet(
                        id = "visual-editor-set-${exerciseIndex + 1}-${setIndex + 1}",
                        targetReps = prescription.repsMax,
                        targetRepsRange = RepRange(prescription.repsMin, prescription.repsMax),
                        targetRIR = prescription.rir,
                        weight = prescription.loadKg,
                        restAfterSeconds = prescription.restSeconds,
                    )
                },
            )
        }
        val session = Session(
            id = "visual-editor-session",
            name = "Día 1 · Empuje",
            description = "Pecho y hombro",
            dayOfWeek = 1,
            exercises = exercises,
        )
        Program(
            id = "visual-editor-program",
            name = "Bloque de fuerza · Julio",
            macrocycles = listOf(
                Macrocycle(
                    id = "visual-editor-macrocycle",
                    name = "Bloque 1",
                    blocks = listOf(
                        Block(
                            id = "visual-editor-block",
                            name = "Bloque 1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "visual-editor-mesocycle",
                                    name = "Bloque de fuerza",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "visual-editor-week",
                                            name = "Semana 1",
                                            sessions = listOf(session),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
    val store = remember(program) { ImportedProgramEditorStore(program) { true } }
    ImportSessionEditorPortal(
        store = store,
        programId = program.id,
        sessionId = "visual-editor-session",
        weekId = "visual-editor-week",
        macroIndex = 0,
        mesoIndex = 0,
        dayOfWeek = 1,
        importedOriginTitle = "Programa de ejemplo",
        importedOriginText = "Día 1 — Empuje\nPress de banca con barra 4×6–8 @100 kg, RIR 2, descanso 180 s\nPress inclinado con mancuernas 3×8–10 @32 kg, RIR 2, descanso 120 s\nAperturas en polea 3×12–15 @15 kg, RIR 1, descanso 75 s",
        onClose = onClose,
    )
}

@androidx.compose.runtime.Composable
private fun ImportProgramVisualHarness(scenario: String, onClose: () -> Unit) {
    val sampleText = "Día 1 — Pierna\nSentadilla 4x6 120\nPrensa 3x10\nPeso muerto rumano 3x8 100\nDía 2 — Tirón\nRemo 3x8 60\nJalón 3x10"
    val pasteText = "Día 1 — Pierna\nSentadilla 4×6 @120 kg\nPrensa 3×10\nPeso muerto rumano 3×8 @100 kg"
    val evidence = remember {
        ImportSourceEvidence(
            kind = ImportSourceKind.FILE,
            displayName = "rutina-semanal.pdf",
            mediaType = "application/pdf",
            sourceText = sampleText,
            sheetCount = 2,
            rowCount = 48,
        )
    }
    val encryptedPdfEvidence = remember(evidence) {
        evidence.copy(
            displayName = "archivo.pdf",
            sourceText = null,
            sheetCount = null,
            rowCount = null,
            pageCount = 12,
        )
    }
    val visualProgram = remember {
        var exerciseIndex = 0
        val sessionNames = listOf("Empuje", "Tirón", "Pierna", "Superior")
        val sourceDays = listOf(1, 2, 4, 5)
        val exerciseCounts = listOf(6, 5, 6, 5)
        val firstSessionExercises = listOf(
            ImportedExercise("visual-ex-1", "Press de banca con barra", 4, 6, 8, loadKg = 100.0, rir = 2, restSeconds = 180, catalogSelectionId = "visual-catalog-0"),
            ImportedExercise("visual-ex-2", "Press inclinado con mancuernas", 3, 8, 10, loadKg = 32.0, rir = 2, restSeconds = 120, catalogSelectionId = "visual-catalog-1"),
            ImportedExercise("visual-ex-3", "Aperturas en polea", 3, 12, 15, loadKg = 15.0, rir = 1, restSeconds = 75, catalogSelectionId = "visual-catalog-2"),
            ImportedExercise("visual-ex-4", "Elevaciones laterales", 3, 12, 15, loadKg = 8.0, rir = 2, restSeconds = 75, catalogSelectionId = "visual-catalog-3"),
            ImportedExercise("visual-ex-5", "Extensión de tríceps en polea", 3, 10, 12, loadKg = 22.5, rir = 2, restSeconds = 90, catalogSelectionId = "visual-catalog-4"),
            ImportedExercise("visual-ex-6", "Press francés con barra Z", 3, 10, 12, loadKg = 20.0, rir = 2, restSeconds = 90, catalogSelectionId = "visual-catalog-5"),
        )
        val exerciseNames = listOf("Jalón al pecho", "Remo con barra", "Prensa de piernas", "Peso muerto rumano", "Press militar", "Extensión de cuádriceps")
        ImportedProgramDraft(
            title = "Bloque de fuerza · Julio",
            weeks = (1..3).map { weekIndex ->
                ImportedWeek(
                    index = weekIndex,
                    days = sessionNames.mapIndexed { dayIndex, sessionName ->
                        val dayId = "visual-day-$weekIndex-${dayIndex + 1}"
                        val exerciseCount = exerciseCounts[dayIndex]
                        ImportedDay(
                            id = dayId,
                            name = "Día ${sourceDays[dayIndex]} — $sessionName",
                            dayOfWeek = sourceDays[dayIndex],
                            exercises = List(exerciseCount) { exercisePosition ->
                                val index = exerciseIndex++
                                val firstSession = if (weekIndex == 1 && dayIndex == 0) firstSessionExercises.getOrNull(exercisePosition) else null
                                val rawName = firstSession?.rawName ?: when {
                                    weekIndex == 1 && dayIndex == 1 && exercisePosition == 0 -> "Remo"
                                    weekIndex == 1 && dayIndex == 1 && exercisePosition == 1 -> "Extensión de cuádriceps"
                                    weekIndex == 1 && dayIndex == 2 && exercisePosition == 0 -> "Sentadilla con barra"
                                    weekIndex == 2 && dayIndex == 0 && exercisePosition == 1 -> "Press inclinado con mancuernas"
                                    else -> exerciseNames[(dayIndex + exercisePosition) % exerciseNames.size]
                                }
                                firstSession ?: ImportedExercise(
                                    id = "visual-ex-${index + 1}",
                                    rawName = rawName,
                                    sets = when (rawName) {
                                        "Sentadilla con barra" -> 4
                                        else -> 3
                                    },
                                    repsMin = when (rawName) {
                                        "Remo" -> 8
                                        "Sentadilla con barra" -> 6
                                        else -> 8
                                    },
                                    repsMax = when (rawName) {
                                        "Sentadilla con barra" -> 6
                                        "Extensión de cuádriceps" -> 12
                                        else -> 10
                                    },
                                    loadKg = when (rawName) {
                                        "Remo" -> 60.0
                                        "Sentadilla con barra" -> 120.0
                                        "Extensión de cuádriceps" -> 40.0
                                        else -> null
                                    },
                                    catalogSelectionId = if (
                                        (weekIndex == 1 && dayIndex == 1 && exercisePosition == 0) ||
                                        (weekIndex == 1 && dayIndex == 2 && exercisePosition == 0)
                                    ) null else "visual-catalog-$index",
                                )
                            },
                        )
                    },
                )
            },
        )
    }
    val visualWarnings = remember(visualProgram) {
        visualProgram.copy(
            warnings = listOf(
                ImportWarning("visual-warning-1", ImportWarningKind.IGNORED_VALUE, "Semana 2 · Día 1", "Press inclinado", "La carga no se pudo leer con seguridad.", 1, "visual-day-2-1", "visual-ex-24"),
                ImportWarning("visual-warning-2", ImportWarningKind.SHEET_NOTE, "Hoja Notas", "Subir 2,5 kg cada semana", "Nota del documento conservada para revisar.", 1, null, null),
                ImportWarning("visual-warning-3", ImportWarningKind.SHEET_NOTE, "Hoja Rutina", "%", "La columna «%» no se usó para calcular cargas.", 1, null, null),
                ImportWarning("visual-warning-4", ImportWarningKind.IGNORED_VALUE, "40 kg", null, "40 kg: Carga parcial", 1, "visual-day-1-2", "visual-ex-8"),
            ),
            reviewedWarnings = emptySet(),
        )
    }

    val selectedNames = remember(visualProgram) {
        visualProgram.weeks.flatMap { it.days }.flatMap { it.exercises }
            .mapNotNull { exercise -> exercise.catalogSelectionId?.let { it to exercise.rawName } }
            .toMap()
    }

    val visualCandidates = remember {
        mapOf(
            "sentadilla" to listOf(
                ExerciseNameCandidate("visual-squat-high", "Sentadilla trasera alta con barra", 0.94, "barbell"),
                ExerciseNameCandidate("visual-squat-front", "Sentadilla frontal con barra", 0.81, "barbell"),
                ExerciseNameCandidate("visual-squat-bulgarian", "Sentadilla búlgara con mancuernas", 0.62, "dumbbells"),
            ),
            "remo" to listOf(
                ExerciseNameCandidate("visual-row-barbell", "Remo con barra", 0.89, "barbell"),
                ExerciseNameCandidate("visual-row-dumbbell", "Remo con mancuerna", 0.78, "dumbbells"),
                ExerciseNameCandidate("visual-row-cable", "Remo sentado en polea", 0.68, "cable"),
            ),
        )
    }
    fun candidatesFor(query: String): List<ExerciseNameCandidate> =
        visualCandidates.entries.firstOrNull { (term, _) -> query.contains(term, ignoreCase = true) }?.value.orEmpty()

    var extracted by remember { mutableStateOf(sampleText) }
    val baseScenario = when (scenario) {
        "resume" -> "entry"
        "catalog", "warnings" -> "matches"
        "structure", "pro" -> "summary"
        "session-menu" -> "days"
        "text", "extracted" -> "empty"
        else -> scenario
    }
    val isChromePage = baseScenario in setOf("paste", "summary", "matches", "days", "sessions", "review", "empty", "file-error")
    val hasDock = baseScenario in setOf("summary", "matches", "days", "sessions", "review")
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(
                    top = when {
                        isChromePage -> 52.dp
                        baseScenario == "reading" -> 12.dp
                        else -> 20.dp
                    },
                    bottom = if (hasDock) 136.dp else 128.dp,
                ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            when (baseScenario) {
                "entry" -> ImportEntryPage(
                    imported = visualProgram,
                    evidence = evidence,
                    pendingDecisions = 2,
                    onBack = onClose,
                    onPickFile = {},
                    onPaste = {},
                    onUrl = {},
                    onCamera = {},
                    onGallery = {},
                    onResume = {},
                    onStartEmpty = {},
                    onPlanLimits = {},
                )
                "paste" -> ImportPastePage(
                    text = pasteText,
                    onTextChange = {},
                    onExample = {},
                    onRead = {},
                )
                "summary" -> ImportSummaryPage(
                    imported = visualProgram,
                    title = visualProgram.title,
                    onTitleChange = {},
                    resolutions = emptyMap(),
                    weekdays = setOf(1, 2, 4, 5),
                    freeLimitReached = true,
                    draftPersistencePending = false,
                    draftPersistenceError = null,
                    pendingDecisions = 2,
                    pendingWarnings = visualWarnings.warnings.size,
                    onStructure = {},
                    onReviewMatches = {},
                    onReviewWarnings = {},
                    onPlanLimits = {},
                )
                "matches" -> ImportProgramMatchesPage(
                    imported = visualWarnings,
                    resolutions = emptyMap(),
                    pendingWarnings = visualWarnings.warnings.size,
                    catalogUnavailable = false,
                    catalogRetrying = false,
                    suggest = ::candidatesFor,
                    search = ::candidatesFor,
                    namesOf = { ids -> selectedNames.filterKeys(ids::contains) },
                    onEdit = {},
                    onOpenCatalog = { _, _, _ -> },
                    onOpenWarnings = {},
                    onRetryCatalog = {},
                    onAddExercise = {},
                    initialActiveRawName = "Sentadilla con barra",
                )
                "days" -> {
                    ImportDaysIntro(summary = "4 sesiones · 3 descansos")
                    ImportWeeklyPlanner(
                        imported = visualProgram,
                        weekdays = setOf(1, 2, 4, 5),
                        selectedWeekIndex = 1,
                        onWeekSelected = {},
                        onAssign = { _, _ -> },
                        onSuggest = {},
                        onApplyToAll = {},
                        onApplyPatternToWeek = { _, _ -> },
                        onOpenMenu = { _, _ -> },
                    )
                }
                "sessions" -> ImportSessionsIntro(
                    imported = visualProgram,
                    reviewedDayIds = emptySet(),
                    onEditSession = { _, _ -> },
                    onOpenMenu = { _, _ -> },
                )
                "review" -> ImportReviewIntro(
                    imported = visualWarnings,
                    muscleDistribution = null,
                    requiresPro = false,
                    issueCount = 2,
                    pendingDecisions = 2,
                    weekdays = setOf(1, 2, 4, 5),
                    reviewedDayIds = emptySet(),
                    pendingWarnings = visualWarnings.warnings.size,
                    catalogUnavailable = false,
                    catalogRetrying = false,
                    onReviewMatches = {},
                    onReviewDays = {},
                    onReviewSessions = {},
                    onWarnings = {},
                    onPlanLimits = {},
                    onBackToSessions = {},
                    onRetryCatalog = {},
                )
                "empty" -> ImportEmptyPage(
                    evidence = evidence,
                    imported = null,
                    onEditText = {},
                    onChooseSource = {},
                    onStartOver = {},
                )
                "file-error" -> ImportErrorPage(
                    message = "El PDF está protegido con contraseña.",
                    evidence = encryptedPdfEvidence,
                    readingProgress = ImportReadingProgress(
                        stage = ImportReadingStage.DOCUMENT,
                        currentPage = 2,
                        totalPages = 12,
                    ),
                    onShowText = {},
                    onRetryText = {},
                    onPasteText = {},
                    onChooseSource = onClose,
                )
                "success" -> ImportSuccessPage(
                    imported = visualProgram,
                    weekdays = setOf(1, 2, 4, 5),
                    firstSessionId = "visual-session-1",
                    onViewProgram = onClose,
                    onStartFirstSession = {},
                )
                else -> ImportReadingPage(
                    readingProgress = ImportReadingProgress(
                        stage = ImportReadingStage.DAYS,
                        currentPage = 3,
                        totalPages = 3,
                        weeks = 3,
                        sessions = 12,
                        exercises = 66,
                        matchedNames = 64,
                    ),
                    detail = "Listo. Abriendo el resumen…",
                    onCancel = onClose,
                    evidence = evidence.copy(sheetCount = 3, rowCount = 48),
                    completed = true,
                )
            }
        }

        if (isChromePage) {
            val title = when (baseScenario) {
                "paste" -> "Pegar texto"
                "summary" -> "Resumen"
                "matches" -> "Coincidencias"
                "days" -> "Distribución"
                "sessions" -> "Sesiones"
                "review" -> "Revisión"
                "empty", "file-error" -> "Importar"
                else -> "Importar programa"
            }
            val subtitle = when (baseScenario) {
                "paste" -> "Se analiza en tu teléfono"
                "summary" -> "3 semanas · 12 sesiones"
                "matches" -> "Conservamos el texto original"
                "days" -> "4 sesiones · 3 descansos"
                "sessions" -> "0 de 12 revisadas"
                "review" -> "Programa · Borrador"
                "empty" -> "Texto leído · sin ejercicios"
                "file-error" -> "No pudimos leer el archivo"
                else -> null
            }
            ImportWizardHeader(
                title = title,
                subtitle = subtitle,
                onBack = onClose,
                action = when (baseScenario) {
                    "summary", "days" -> "⋯"
                    "matches" -> "Guardar y salir"
                    "sessions", "review" -> "?"
                    else -> null
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp).padding(top = 36.dp),
            )
        }

        if (hasDock) {
            val label = when (baseScenario) {
                "summary" -> "Revisar coincidencias"
                "matches" -> "Continuar a distribución"
                "days" -> "Continuar a sesiones"
                "sessions" -> "Abrir la siguiente"
                else -> "Guardar como borrador"
            }
            val secondary = when (baseScenario) {
                "summary" -> "Cambiar archivo"
                "days" -> "Estructura"
                "sessions" -> "Ir a revisión"
                else -> null
            }
            val note = when (baseScenario) {
                "summary" -> "2 decisiones pendientes"
                "matches" -> "Te faltan 2 por decidir."
                "days" -> "4 sesiones · 3 descansos"
                "sessions" -> "Siguiente: Semana 1 · Día 1 — Empuje"
                else -> "Resuelve los puntos pendientes para guardar."
            }
            ImportWizardDock(
                label = label,
                enabled = baseScenario !in setOf("matches", "review"),
                onNext = {},
                secondaryLabel = secondary,
                onSecondary = if (secondary == null) null else ({}),
                note = note,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }

        if (scenario == "resume") {
            KpknSheet(onDismissRequest = onClose) {
                ImportResumeSheetContent(
                    imported = visualProgram,
                    resumePage = ImportWizardPage.DAYS,
                    onResume = onClose,
                    onDiscard = onClose,
                    onDiscardDraft = onClose,
                )
            }
        }

        if (scenario == "catalog") {
            KpknSheet(onDismissRequest = onClose, maxHeightFraction = 0.92f) {
                ImportCatalogSheetContent(
                    rawName = "Sentadilla con barra",
                    suggest = ::candidatesFor,
                    search = ::candidatesFor,
                    onSelect = {},
                    onOpenFullCatalog = {},
                    onClose = onClose,
                )
            }
        }

        if (scenario == "structure") {
            KpknSheet(onDismissRequest = onClose, maxHeightFraction = 0.92f) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Text(
                        "Estructura del programa",
                        color = Color(0xFFF2EEE6),
                    )
                    androidx.compose.material3.Text(
                        "3 semanas · 12 sesiones. Los cambios se guardan en el borrador.",
                        color = Color(0xFFC4BFB6),
                    )
                    ImportProgramStructureOverview(visualProgram, onOpenWeek = {}, onOpenDay = {}, onEdit = {})
                }
            }
        }

        if (scenario == "pro") {
            KpknSheet(onDismissRequest = onClose) {
                ImportProSheetContent(
                    weeks = visualProgram.weeks,
                    onUpgrade = {},
                    onKeepWeek = {},
                    onClose = onClose,
                )
            }
        }

        if (scenario == "session-menu") {
            KpknSheet(
                onDismissRequest = onClose,
                maxHeightFraction = 0.92f,
            ) {
                ImportSessionMenuContent(
                    name = "Empuje",
                    dayLabel = "Día 1",
                    weekIndex = 1,
                    exerciseCount = 6,
                    exercises = visualProgram.weeks.first().days.first().exercises,
                    defaultSplitName = "Día 2",
                    selectedDayOfWeek = 1,
                    allowStructureChanges = true,
                    onClose = onClose,
                    onMove = {},
                    onSplitAtExercise = { _, _ -> },
                    onDuplicate = {},
                    onRemove = {},
                )
            }
        }

        if (scenario == "warnings") {
            KpknSheet(onDismissRequest = onClose, maxHeightFraction = 0.94f) {
                ImportWarningsSheetContent(
                    imported = visualWarnings,
                    onOpenExercise = {},
                    onEdit = {},
                    onOpenSessionEditor = {},
                    onClose = onClose,
                )
            }
        }

        if (scenario in setOf("text", "extracted")) {
            KpknSheet(onDismissRequest = onClose, maxHeightFraction = 0.94f) {
                ImportExtractedTextSheetContent(
                    evidence = evidence,
                    text = extracted,
                    onTextChange = { extracted = it },
                    onReanalyze = onClose,
                    onClose = onClose,
                )
            }
        }
    }
}
