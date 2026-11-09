package com.example.kpkn.screens.programs.importer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportReadingProgress
import com.example.kpkn.domain.training.importer.ImportSourceKind
import com.example.kpkn.data.importer.ImportSourceMediaStore
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.domain.training.importer.ImportWizardSheet
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.entreno.EntrenoPlanNotice
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.entitlements.LocalPlan
import kotlinx.coroutines.launch

@Composable
fun ImportProgramScreen(
    onBack: () -> Unit,
    onSaved: (programId: String) -> Unit,
    onUseCatalogProgram: (planId: String) -> Unit,
    viewModel: ImportProgramViewModel = viewModel(),
    onOpenPro: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    val level: PlanLevel = LocalPlan.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sourceMediaStore = remember(context.filesDir) { ImportSourceMediaStore(context.filesDir) }
    var pastedText by rememberSaveable { mutableStateOf("") }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var urlMode by rememberSaveable { mutableStateOf(false) }
    var catalogRawName by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogApplyToAll by rememberSaveable { mutableStateOf(false) }
    var catalogFullScreen by rememberSaveable { mutableStateOf(false) }
    var sessionMenuDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionMenuWarningId by rememberSaveable { mutableStateOf<String?>(null) }
    var addExerciseContextDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddExercise by rememberSaveable { mutableStateOf(false) }
    var scheduleWeekIndex by rememberSaveable { mutableIntStateOf(1) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var extractedText by rememberSaveable(state.sourceEvidence.sourceText) {
        mutableStateOf(state.sourceEvidence.sourceText.orEmpty())
    }
    val pickFile = rememberImportFilePicker { uri -> viewModel.importFromPicked(uri) }
    val photoActions = rememberImportPhotoActions { uri, takenFile ->
        viewModel.importFromPicked(uri, photo = true, takenFile = takenFile)
    }
    var handoff by remember { mutableStateOf(ImportHandoff.take()) }
    LaunchedEffect(handoff) {
        when (val request = handoff) {
            is ImportRequest.File -> viewModel.importFromPicked(request.uri)
            is ImportRequest.Photo -> viewModel.importFromPicked(request.uri, photo = true, takenFile = request.takenFile)
            ImportRequest.PasteText -> {
                urlMode = false
                viewModel.navigateTo(ImportWizardPage.PASTE)
            }
            ImportRequest.Url -> {
                urlMode = true
                viewModel.navigateTo(ImportWizardPage.PASTE)
            }
            null -> Unit
        }
        handoff = null
    }
    LaunchedEffect(state.savedProgramId, state.page, state.imported) {
        val savedProgramId = state.savedProgramId
        if (savedProgramId != null) {
            onSaved(savedProgramId)
        } else if (state.imported != null && state.page == ImportWizardPage.SUMMARY) {
            // Compatibility for drafts created before the combined review replaced the extra summary page.
            viewModel.navigateTo(ImportWizardPage.EXERCISES)
        } else if (state.imported != null && state.page in setOf(
                ImportWizardPage.SESSIONS,
                ImportWizardPage.SESSION,
                ImportWizardPage.REVIEW,
                ImportWizardPage.SUCCESS,
            )
        ) {
            // Drafts created by older versions resume at the combined exercise and weekly review.
            viewModel.navigateTo(ImportWizardPage.DAYS)
        }
    }

    val imported = state.imported
    val issues = remember(imported, state.resolutions, state.editorStarted, state.editorProgram, state.editorSourceMap) {
        imported?.let { draft ->
            if (state.editorStarted) state.editorProgram?.let {
                canonicalImportIssues(draft, it, state.editorSourceMap, state.resolutions)
            }.orEmpty()
            else ImportDraftValidation.checkOccurrences(draft) {
                it.catalogSelectionId != null || state.resolutions.containsKey(it.rawName)
            }
        }.orEmpty()
    }
    val weekdayAssignments = imported?.let { effectiveImportedWeekdays(it, state.weekdays) }.orEmpty()
    val weekdaysMissing = imported != null && (
        state.weekdays.isEmpty() || imported.weeks.any { week ->
            week.days.filterNot { it.isRestDay }.any { day -> weekdayAssignments[day.id] !in 1..7 }
        }
    )
    val pendingWarnings = imported?.warnings?.count { it.id !in imported.reviewedWarnings } ?: 0
    val canSave = issues.isEmpty() && pendingWarnings == 0 && !weekdaysMissing && !state.busy && state.notice != IMPORT_CATALOG_UNAVAILABLE &&
        !state.draftPersistencePending && state.draftPersistenceError == null
    val planRejection = viewModel.planRejection(level)
    val requiresPro = planRejection != null

    fun backPage() {
        if (state.sheet != null) {
            viewModel.showSheet(null)
            return
        }
        when (state.page) {
            ImportWizardPage.ENTRY -> onBack()
            ImportWizardPage.PASTE -> {
                urlMode = false
                viewModel.navigateTo(ImportWizardPage.ENTRY)
            }
            ImportWizardPage.READING -> viewModel.cancelImport()
            ImportWizardPage.SUMMARY -> viewModel.navigateTo(ImportWizardPage.EXERCISES)
            ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> viewModel.navigateTo(ImportWizardPage.ENTRY)
            ImportWizardPage.SESSIONS, ImportWizardPage.SESSION, ImportWizardPage.REVIEW -> viewModel.navigateTo(ImportWizardPage.EXERCISES)
            ImportWizardPage.SUCCESS -> state.savedProgramId?.let(onSaved) ?: onBack()
            ImportWizardPage.EMPTY, ImportWizardPage.ERROR -> viewModel.navigateTo(ImportWizardPage.ENTRY)
        }
    }

    fun openAddExercise(dayId: String? = null) {
        viewModel.showSheet(null)
        addExerciseContextDayId = dayId
        showAddExercise = true
    }

    fun openWarningTarget(dayId: String?) {
        val structural = imported?.warnings?.firstOrNull {
            it.kind == com.example.kpkn.domain.training.importer.ImportWarningKind.STRUCTURE_REVIEW &&
                it.dayId == dayId && it.id !in imported.reviewedWarnings
        }
        if (dayId != null && structural != null) {
            sessionMenuDayId = dayId
            sessionMenuWarningId = structural.id
            viewModel.showSheet(ImportWizardSheet.SESSION_MENU)
        } else {
            sessionMenuWarningId = null
            openAddExercise(dayId)
        }
    }

    fun closeImportCatalog() {
        catalogRawName = null
        catalogExerciseId = null
        catalogApplyToAll = false
        catalogFullScreen = false
        viewModel.showSheet(null)
    }

    fun requestSave() {
        if (!canSave) return
        if (requiresPro) {
            viewModel.showSheet(ImportWizardSheet.PRO)
            return
        }
        scope.launch {
            viewModel.save(level)
        }
    }

    BackHandler(enabled = state.sheet != null || (state.page != ImportWizardPage.ENTRY && state.page != ImportWizardPage.SUCCESS)) {
        backPage()
    }

    val showDock = state.page in setOf(ImportWizardPage.EXERCISES, ImportWizardPage.DAYS)
    val showChrome = state.page in setOf(
        ImportWizardPage.PASTE,
        ImportWizardPage.EXERCISES,
        ImportWizardPage.DAYS,
        ImportWizardPage.EMPTY,
        ImportWizardPage.ERROR,
    )
    val chromeTitle = when (state.page) {
        ImportWizardPage.PASTE -> if (urlMode) "Enlace público" else "Pegar texto"
        ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> "Revisar programa"
        ImportWizardPage.EMPTY, ImportWizardPage.ERROR -> "Importar"
        else -> "Importar programa"
    }
    val chromeSubtitle = when (state.page) {
        ImportWizardPage.PASTE -> if (urlMode) "Página o archivo sin iniciar sesión" else "Se analiza en tu teléfono"
        ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> "Ejercicios y distribución semanal"
        ImportWizardPage.EMPTY -> "Texto leído · sin ejercicios"
        ImportWizardPage.ERROR -> when (state.sourceEvidence.kind) {
            ImportSourceKind.PUBLIC_URL -> "No pudimos leer el enlace"
            ImportSourceKind.PASTED_TEXT -> "No pudimos leer el texto"
            ImportSourceKind.PHOTO -> "No pudimos leer la foto"
            else -> "No pudimos leer el archivo"
        }
        else -> null
    }
    val scrollTopPadding = when {
        showChrome -> 52.dp
        state.page == ImportWizardPage.READING -> 12.dp
        else -> 20.dp
    }
    val pageScrollState = remember(state.page) { androidx.compose.foundation.ScrollState(0) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(pageScrollState)
                .testTag("import-page-scroll")
                .padding(horizontal = 16.dp)
                .padding(top = scrollTopPadding, bottom = if (showDock) 136.dp else 128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                if (imported != null && (state.sourceEvidence.sourceMediaId != null || state.sourceEvidence.sourcePreviewUnavailable)) {
                    ImportTextAction(
                        label = "Ver original importado",
                        onClick = { showOriginal = true },
                        modifier = Modifier.testTag("import-source-open-original"),
                        color = Color(0xFF81C784),
                    )
                }
                if (imported != null && (!state.editorStarted || state.canUndoWeekdayChange)) {
                    state.undoLabel?.let { ImportUndoLine(label = it, onUndo = viewModel::undo) }
                }
                when (state.page) {
                    ImportWizardPage.ENTRY -> ImportEntryPage(
                        imported = imported,
                        evidence = state.sourceEvidence,
                        pendingDecisions = imported?.weeks.orEmpty().sumOf { week ->
                            week.days.sumOf { day -> day.exercises.count { it.catalogSelectionId == null && !state.resolutions.containsKey(it.rawName) } }
                        },
                        onBack = onBack,
                        onPickFile = pickFile,
                        onPaste = { urlMode = false; viewModel.navigateTo(ImportWizardPage.PASTE) },
                        onUrl = { urlMode = true; viewModel.navigateTo(ImportWizardPage.PASTE) },
                        onCamera = photoActions.openCamera,
                        onGallery = photoActions.openGallery,
                        onResume = { viewModel.showSheet(ImportWizardSheet.RESUME) },
                        onStartEmpty = { urlMode = false; viewModel.importFromText("") },
                        onPlanLimits = { viewModel.showSheet(ImportWizardSheet.PRO) },
                    )
                    ImportWizardPage.PASTE -> if (urlMode) {
                        ImportUrlField(
                            url = sourceUrl,
                            onUrlChange = { sourceUrl = it },
                            onRead = { viewModel.importFromUrl(sourceUrl) },
                        )
                        ImportPlanLimitNote()
                    } else {
                        ImportPastePage(
                            text = pastedText,
                            onTextChange = { pastedText = it },
                            onExample = { pastedText = IMPORT_TEXT_EXAMPLE_FOR_WIZARD },
                            onRead = { viewModel.importFromText(pastedText) },
                        )
                    }
                    ImportWizardPage.READING -> ImportReadingPage(
                        readingProgress = state.readingProgress ?: ImportReadingProgress(),
                        detail = state.progress,
                        onCancel = viewModel::cancelImport,
                        evidence = state.sourceEvidence,
                    )
                    ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> imported?.let { source ->
                        ImportTitleField(state.title, viewModel::setTitle)
                        state.importKnownPlanId?.takeUnless { state.importKnownDismissed }?.let { planId ->
                            ImportKnownProgramNotice(planId, onUse = { onUseCatalogProgram(planId) }, onDismiss = viewModel::dismissImportKnown)
                        }
                        ImportProgramMatchesPage(
                            imported = source,
                            resolutions = state.resolutions,
                            pendingWarnings = source.warnings.count { it.id !in source.reviewedWarnings },
                            catalogUnavailable = state.notice == IMPORT_CATALOG_UNAVAILABLE,
                            catalogRetrying = state.busy && state.notice == IMPORT_MATCHING,
                            suggest = viewModel::suggestions,
                            search = viewModel::search,
                            namesOf = viewModel::namesOf,
                            onEdit = viewModel::edit,
                            onOpenCatalog = { exerciseId, rawName, perOccurrence ->
                                catalogExerciseId = exerciseId
                                catalogRawName = rawName
                                catalogApplyToAll = !perOccurrence
                                catalogFullScreen = false
                                viewModel.showSheet(ImportWizardSheet.CATALOG)
                            },
                            onOpenWarnings = { viewModel.showSheet(ImportWizardSheet.WARNINGS) },
                            onRetryCatalog = viewModel::retryCatalogMatching,
                            onAddExercise = { openAddExercise() },
                        )
                        ImportDaysIntro(
                            summary = importWeekDaySummary(source, state.weekdays, scheduleWeekIndex),
                        )
                        ImportWeeklyPlanner(
                            imported = source,
                            weekdays = state.weekdays,
                            selectedWeekIndex = scheduleWeekIndex,
                            onWeekSelected = { scheduleWeekIndex = it },
                            onAssign = viewModel::assignImportedDay,
                            onSuggest = viewModel::suggestWeekdaysForWeek,
                            onApplyToAll = viewModel::applyWeekdayPatternToAllWeeks,
                            onApplyPatternToWeek = viewModel::applyWeekdayPatternToWeek,
                            onOpenMenu = { _, dayId ->
                                sessionMenuDayId = dayId
                                sessionMenuWarningId = null
                                viewModel.showSheet(ImportWizardSheet.SESSION_MENU)
                            },
                        )
                    }
                    ImportWizardPage.EMPTY -> ImportEmptyPage(
                        evidence = state.sourceEvidence,
                        imported = imported,
                        onEditText = { extractedText = state.sourceEvidence.sourceText.orEmpty(); viewModel.showSheet(ImportWizardSheet.TEXT) },
                        onChooseSource = { viewModel.navigateTo(ImportWizardPage.ENTRY) },
                        onStartOver = { viewModel.clear() },
                        message = state.error,
                    )
                    ImportWizardPage.ERROR -> ImportErrorPage(
                        message = state.error ?: "No pudimos leer el programa.",
                        evidence = state.sourceEvidence,
                        readingProgress = state.readingProgress,
                        onShowText = { extractedText = state.sourceEvidence.sourceText.orEmpty(); viewModel.showSheet(ImportWizardSheet.TEXT) },
                        onRetryText = { viewModel.reanalyzeSourceText(extractedText) },
                        onPasteText = { pastedText = state.sourceEvidence.sourceText.orEmpty(); urlMode = false; viewModel.navigateTo(ImportWizardPage.PASTE) },
                        onChooseSource = {
                            when (state.sourceEvidence.kind) {
                                ImportSourceKind.PUBLIC_URL -> {
                                    urlMode = true
                                    viewModel.navigateTo(ImportWizardPage.PASTE)
                                }
                                ImportSourceKind.PASTED_TEXT -> {
                                    pastedText = state.sourceEvidence.sourceText.orEmpty()
                                    urlMode = false
                                    viewModel.navigateTo(ImportWizardPage.PASTE)
                                }
                                ImportSourceKind.PHOTO -> photoActions.openGallery()
                                ImportSourceKind.FILE -> pickFile()
                                ImportSourceKind.NONE -> viewModel.navigateTo(ImportWizardPage.ENTRY)
                            }
                        },
                    )
                    else -> Unit
                }
                state.notice?.takeIf {
                    it != IMPORT_PRO_NOTICE && state.page != ImportWizardPage.ERROR &&
                        !(it in setOf(IMPORT_CATALOG_UNAVAILABLE, IMPORT_MATCHING) && state.page == ImportWizardPage.EXERCISES)
                }?.let {
                    EntrenoPlanNotice(it, error = false)
                }
                state.error?.takeIf { state.page !in setOf(ImportWizardPage.ERROR, ImportWizardPage.EMPTY) }?.let { EntrenoPlanNotice(it) }
                state.draftPersistenceError?.takeIf { it != state.error }?.let { EntrenoPlanNotice(it) }
            }
            if (showDock) {
                val pendingDecisions = imported?.weeks.orEmpty().sumOf { week ->
                    week.days.sumOf { day -> day.exercises.count { it.catalogSelectionId == null && !state.resolutions.containsKey(it.rawName) } }
                }
                val dockLabel = when (state.page) {
                    ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> if (requiresPro) "Ver límite Pro" else "Guardar y abrir programa"
                    else -> "Continuar"
                }
                val dockAction: () -> Unit = when (state.page) {
                    ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> ::requestSave
                    else -> ({ Unit })
                }
                val dockSecondaryLabel = when (state.page) {
                    ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> if (pendingWarnings > 0) "Avisos" else null
                    else -> null
                }
                val dockSecondaryAction: (() -> Unit)? = when (state.page) {
                    ImportWizardPage.EXERCISES, ImportWizardPage.DAYS ->
                        if (pendingWarnings > 0) ({ viewModel.showSheet(ImportWizardSheet.WARNINGS) }) else null
                    else -> null
                }
                val dockNote = when (state.page) {
                    ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> {
                        val week = imported?.weeks?.firstOrNull { it.index == scheduleWeekIndex }
                        val sessions = week?.days.orEmpty().filterNot { it.isRestDay }
                        val assigned = sessions.mapNotNull { day -> imported?.let { effectiveImportedWeekdays(it, state.weekdays)[day.id] } }
                        val restDays = (7 - assigned.distinct().size - (sessions.size - assigned.size)).coerceAtLeast(0)
                        val weeklySummary = "${importCountLabel(sessions.size, "sesión", "sesiones")} · $restDays descansos"
                        when {
                            requiresPro -> "$weeklySummary · Requiere KPKN Pro."
                            pendingDecisions > 0 -> "$weeklySummary · Resuelve los $pendingDecisions ejercicios pendientes."
                            pendingWarnings > 0 -> "$weeklySummary · Revisa o descarta $pendingWarnings avisos."
                            !canSave -> "$weeklySummary · Completa la distribución semanal."
                            else -> "$weeklySummary · Se abrirá Program Detail para ajustar las semanas."
                        }
                    }
                    else -> null
                }
                val dockEnabled = when (state.page) {
                    ImportWizardPage.EXERCISES, ImportWizardPage.DAYS -> canSave
                    else -> !state.busy && !state.draftPersistencePending
                }
                ImportWizardDock(
                    label = dockLabel,
                    enabled = dockEnabled,
                    onNext = dockAction,
                    secondaryLabel = dockSecondaryLabel,
                    onSecondary = dockSecondaryAction,
                    secondaryTestTag = null,
                    note = dockNote,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }

        if (showChrome) {
            ImportWizardHeader(
                title = chromeTitle,
                subtitle = chromeSubtitle,
                onBack = ::backPage,
                action = null,
                onAction = null,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp).padding(top = 36.dp),
            )
        }

        when (state.sheet) {
            ImportWizardSheet.RESUME -> imported?.let { source ->
                KpknSheet(onDismissRequest = { viewModel.showSheet(null) }) {
                    ImportResumeSheetContent(
                        imported = source,
                        resumePage = state.resumePage,
                        onResume = viewModel::resumeDraft,
                        onDiscard = { viewModel.clear() },
                        onDiscardDraft = { viewModel.clear(onBack) },
                    )
                }
            }
            ImportWizardSheet.PRO -> {
                KpknSheet(onDismissRequest = { viewModel.showSheet(null) }) {
                    ImportProSheetContent(
                        weeks = imported?.weeks.orEmpty(),
                        onUpgrade = onOpenPro?.let { openPro -> { viewModel.showSheet(null); openPro() } },
                        onKeepWeek = if (state.editorStarted) null else { weekIndex ->
                            viewModel.edit(ImportEditOp.KeepOnlyWeek(weekIndex))
                            scheduleWeekIndex = 1
                            viewModel.showSheet(null)
                        },
                        onClose = { viewModel.showSheet(null) },
                    )
                }
            }
            ImportWizardSheet.TEXT -> KpknSheet(onDismissRequest = { viewModel.showSheet(null) }, maxHeightFraction = 0.94f) {
                ImportExtractedTextSheetContent(
                    evidence = state.sourceEvidence,
                    text = extractedText,
                    onTextChange = { extractedText = it },
                    onReanalyze = { viewModel.showSheet(null); viewModel.reanalyzeSourceText(extractedText) },
                    onClose = { viewModel.showSheet(null) },
                )
            }
            ImportWizardSheet.WARNINGS -> imported?.takeIf { it.warnings.isNotEmpty() }?.let { source ->
                KpknSheet(onDismissRequest = { viewModel.showSheet(null) }, maxHeightFraction = 0.94f) {
                    ImportWarningsSheetContent(
                        imported = source,
                        onOpenExercise = { exerciseId ->
                            viewModel.showSheet(null)
                            viewModel.open(exerciseId)
                            viewModel.navigateTo(ImportWizardPage.EXERCISES)
                        },
                        onEdit = viewModel::edit,
                        onOpenSessionEditor = ::openWarningTarget,
                        onClose = { viewModel.showSheet(null) },
                        hasOriginalSource = state.sourceEvidence.sourceMediaId != null || state.sourceEvidence.sourcePreviewUnavailable,
                        onOpenOriginalSource = { showOriginal = true },
                    )
                }
            }
            ImportWizardSheet.STRUCTURE -> Unit // Los borradores antiguos se reanudan en la revisión conjunta.
            ImportWizardSheet.SESSION_MENU -> {
                val selected = sessionMenuDayId?.let { id -> imported?.weeks?.firstNotNullOfOrNull { week -> week.days.firstOrNull { it.id == id }?.let { week to it } } }
                if (selected != null) {
                    val (selectedWeek, selectedDay) = selected
                    val dayNumber = selectedWeek.days.takeWhile { it.id != selectedDay.id }.count { !it.isRestDay } + 1
                    val displayName = importSessionDisplayName(dayNumber, selectedDay.name)
                    KpknSheet(
                        onDismissRequest = { sessionMenuWarningId = null; viewModel.showSheet(null) },
                        maxHeightFraction = 0.92f,
                    ) {
                    ImportSessionMenuContent(
                        name = displayName.substringAfter(" · ", displayName),
                        dayLabel = displayName.substringBefore(" · "),
                        weekIndex = selectedWeek.index,
                        exerciseCount = selectedDay.exercises.size,
                        exercises = selectedDay.exercises,
                        defaultSplitName = "Día ${dayNumber + 1}",
                        selectedDayOfWeek = selectedDay.dayOfWeek,
                        allowStructureChanges = !state.editorStarted,
                        onClose = { sessionMenuWarningId = null; viewModel.showSheet(null) },
                        onMove = { day ->
                            viewModel.assignImportedDay(selectedDay.id, day)
                            if (selectedDay.name.equals("Día sin asignar", ignoreCase = true)) {
                                viewModel.edit(ImportEditOp.RenameDay(selectedDay.id, importWeekdayName(day)))
                            }
                            sessionMenuWarningId?.let { warningId -> viewModel.edit(ImportEditOp.IgnoreWarning(warningId)) }
                            sessionMenuWarningId = null
                            viewModel.showSheet(null)
                        },
                        onSplitAtExercise = { exerciseId, newName ->
                            viewModel.edit(ImportEditOp.SplitDayAtExercise(selectedDay.id, exerciseId, newName))
                            sessionMenuWarningId = null
                            viewModel.showSheet(null)
                        },
                        onDuplicate = { viewModel.edit(ImportEditOp.DuplicateDay(selectedDay.id)); viewModel.showSheet(null) },
                        onRemove = { viewModel.edit(ImportEditOp.RemoveDay(selectedDay.id)); viewModel.showSheet(null) },
                    )
                    }
                }
            }
            ImportWizardSheet.CATALOG -> {
                val rawName = catalogRawName
                if (!catalogFullScreen && rawName != null && catalogExerciseId != null) {
                    KpknSheet(
                        onDismissRequest = ::closeImportCatalog,
                        maxHeightFraction = 0.78f,
                    ) {
                        ImportCatalogSheetContent(
                            rawName = rawName,
                            suggest = viewModel::suggestions,
                            search = viewModel::search,
                            onSelect = { candidate ->
                                val exerciseId = catalogExerciseId
                                if (catalogApplyToAll) {
                                    viewModel.edit(ImportEditOp.Decide(rawName, candidate.configurationId, candidate.displayName))
                                } else if (exerciseId != null) {
                                    viewModel.edit(ImportEditOp.DecideOccurrence(exerciseId, candidate.configurationId, candidate.displayName))
                                }
                                closeImportCatalog()
                            },
                            onOpenFullCatalog = { catalogFullScreen = true },
                            onClose = ::closeImportCatalog,
                        )
                    }
                }
            }
            null -> Unit
        }

        if (state.sheet == ImportWizardSheet.CATALOG && catalogFullScreen && catalogRawName != null && catalogExerciseId != null) {
            ImportSharedCatalog(
                rawName = catalogRawName!!,
                onSelect = { id ->
                    if (catalogApplyToAll) viewModel.edit(ImportEditOp.Decide(catalogRawName!!, id, catalogRawName!!))
                    else viewModel.edit(ImportEditOp.DecideOccurrence(catalogExerciseId!!, id, catalogRawName!!))
                    catalogRawName = null
                    catalogExerciseId = null
                    viewModel.showSheet(null)
                },
                onClose = ::closeImportCatalog,
            )
        }

        if (showAddExercise && imported != null) {
            ImportAddExerciseSheet(
                imported = imported,
                contextDayId = addExerciseContextDayId,
                search = viewModel::search,
                namesOf = viewModel::namesOf,
                onEdit = viewModel::edit,
                onClose = { showAddExercise = false },
            )
        }
        if (showOriginal) {
            ImportSourcePreviewDialog(
                file = sourceMediaStore.fileFor(state.sourceEvidence.sourceMediaId, state.sourceEvidence.documentType),
                type = state.sourceEvidence.documentType,
                unavailable = state.sourceEvidence.sourcePreviewUnavailable,
                onDismiss = { showOriginal = false },
            )
        }
    }
}

private const val IMPORT_TEXT_EXAMPLE_FOR_WIZARD = "Día 1 — Pierna\nSentadilla 4×6 @120 kg\nPrensa 3×10\nPeso muerto rumano 3×8 @100 kg"
