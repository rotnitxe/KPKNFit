package com.example.kpkn.screens.sessioneditor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.components.rememberSessionEditorSpacing
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

import com.example.kpkn.screens.sessioneditor.sessionBackgroundPresets
import com.example.kpkn.screens.sessioneditor.sessionGradients
import com.example.kpkn.screens.sessioneditor.design.SessionEditorBottomIsland
import com.example.kpkn.screens.sessioneditor.design.SessionEditorCoverStrip
import com.example.kpkn.screens.sessioneditor.design.SessionEditorHeader
import com.example.kpkn.screens.sessioneditor.design.SessionEditorLoadError
import com.example.kpkn.screens.sessioneditor.design.SessionEditorLoadingSkeleton
import com.example.kpkn.screens.sessioneditor.design.SessionEditorMoreMenu
import com.example.kpkn.screens.sessioneditor.design.SessionEditorTopActions
import com.example.kpkn.screens.sessioneditor.design.SessionNavigatorSheet
import com.example.kpkn.screens.sessioneditor.design.SessionTemplateSaveDialog
import com.example.kpkn.screens.sessioneditor.components.sheets.SessionEditorSheets
import com.example.kpkn.screens.sessioneditor.components.sheets.AssistantGlassOverlay
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.KpknTopIsland
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.rememberIslandVisible
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlin.math.roundToInt
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.screens.workout.RepairBenchmarkTrace


/** Índice de la cabecera en la lista del editor: el 0 es la fila superior y el 1 la franja de portada. */
private const val EditorHeaderIndex = 2

/** Ítems que van delante del contenido de la sesión: fila superior, franja de portada y cabecera. */
private const val EditorLeadingItems = 3

@Composable
fun SessionEditorScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
    onOpenExerciseDetail: (String) -> Unit = {},
    onOpenCatalog: ((CatalogLaunchRequest) -> Unit)? = null,
    catalogResult: CatalogResult? = null,
    onCatalogResultConsumed: () -> Unit = {},
    onSavedAndExit: () -> Unit = onBack,
    draftWeekId: String? = null,
    draftMacroIndex: Int? = null,
    draftMesoIndex: Int? = null,
    draftDayOfWeek: Int? = null,
    repairBenchmarkEditorRequestId: Long = 0L,
    viewModel: SessionEditorViewModel = viewModel(
        factory = SessionEditorViewModel.factory(
            programId = programId,
            sessionId = sessionId,
            draftWeekId = draftWeekId,
            draftMacroIndex = draftMacroIndex,
            draftMesoIndex = draftMesoIndex,
            draftDayOfWeek = draftDayOfWeek,
        )
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTemplates by viewModel.allTemplates.collectAsStateWithLifecycle()
    val userTemplates by viewModel.userTemplates.collectAsStateWithLifecycle()
    val templatesReady by viewModel.templatesReady.collectAsStateWithLifecycle()
    val corruptTemplateIds by viewModel.corruptTemplateIds.collectAsStateWithLifecycle()
    val archivedUserTemplates = remember(userTemplates) { userTemplates.filter { it.isArchived } }
    val session = uiState.activeVariantSession ?: uiState.session
    val listState = rememberLazyListState()
    // Isla superior: aparece cuando la cabecera (ítem 2 de la lista) sale de la vista (KPKN_UI_V2 §3).
    val headerScrolledAway = rememberIslandVisible(listState, afterItemIndex = EditorHeaderIndex)
    var showMoreMenu by remember { mutableStateOf(false) }
    var moreAnchor by remember { mutableStateOf<Rect?>(null) }
    var showNavigatorSheet by rememberSaveable { mutableStateOf(false) }
    var showSaveTemplateDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAutoExpandExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var catalogRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun openAssistantSheet() {
        // Dismiss the group-name BasicTextField before the overlay is composed;
        // otherwise Android can keep its yellow insertion handle visible above
        // the sheet even though the field is visually covered.
        clearSessionEditorAssistantFocus(focusManager, keyboardController)
        viewModel.openSheet(SessionEditorSheet.AUGE)
    }

    // Salida del editor: el botón «Atrás», la isla y el gesto del sistema hacen lo mismo. Con cambios sin guardar, pregunta antes.
    fun requestExit() {
        focusManager.clearFocus(force = true)
        if (uiState.hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            scope.launch {
                if (viewModel.saveDraftForExitAndAwait()) onBack()
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> viewModel.saveDraftForExit()
                Lifecycle.Event.ON_RESUME -> {
                    // The editor ViewModel already owns the current session and
                    // process-death recovery loads it in init. Reloading every
                    // resume races catalog results (and other unsaved edits),
                    // replacing them with the pre-navigation draft.
                    val current = viewModel.uiState.value
                    if (current.session == null || current.loadErrorMessage != null) {
                        viewModel.retryLoadSession()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.saveDraftForExit()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.updateBackgroundValue(uri.toString(), SessionBackgroundType.IMAGE)
        }
    }

    val hazeState = remember { HazeState() }

    // Snackbar for auto-save and navigation messages from ViewModel
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            val draftFailure = msg.startsWith("No se pudo guardar el borrador") ||
                msg.startsWith("No se pudo descartar el borrador")
            val action = snackbarHostState.showKpknSnackbar(
                msg,
                if (draftFailure) SnackbarType.DANGER else SnackbarType.SUCCESS,
                actionLabel = if (draftFailure) "Reintentar" else null,
            )
            viewModel.clearSnackbarMessage()
            if (action == SnackbarResult.ActionPerformed && draftFailure) {
                scope.launch {
                    if (msg.startsWith("No se pudo descartar")) {
                        val pendingSwitch = uiState.pendingSessionSwitchId != null
                        val discarded = if (pendingSwitch) {
                            viewModel.discardAndSwitchPendingSessionAndAwait()
                        } else {
                            viewModel.discardDraftForCurrentSessionAndAwait()
                        }
                        if (discarded && !pendingSwitch) {
                            showDiscardDialog = false
                            onBack()
                        }
                    } else {
                        viewModel.saveDraftForExitAndAwait()
                    }
                }
            }
        }
    }

    val editorSpacing = rememberSessionEditorSpacing()
    val density = LocalDensity.current
    val contentBottomPadding = editorSpacing.bottomContentPadding + 16.dp
    val fabBottomPadding = editorSpacing.fabBottomPadding
    val exerciseInfoById = catalogExerciseIndex()

    // The editor keeps its ViewModel state while the catalog is on the navigation
    // stack. The route returns only IDs, then the existing mutation methods apply
    // them and close the picker.
    LaunchedEffect(uiState.sheet, onOpenCatalog) {
        if (uiState.sheet != SessionEditorSheet.EXERCISE_PICKER || onOpenCatalog == null) {
            if (uiState.sheet != SessionEditorSheet.EXERCISE_PICKER) catalogRequestInFlight = false
            return@LaunchedEffect
        }
        if (!catalogRequestInFlight) {
            val targetExerciseId = uiState.pickerTargetExerciseId
            val targetGroupName = uiState.pickerTargetPartId?.let { pid ->
                uiState.session?.parts?.firstOrNull { it.id == pid }?.name
            }
            val targetExercise = targetExerciseId?.let { id ->
                uiState.session?.allExercises()?.firstOrNull { it.id == id }
            }
            catalogRequestInFlight = true
            val request = CatalogLaunchRequest(
                origin = if (targetExerciseId == null) {
                    CatalogLaunchOrigin.SESSION_EDITOR
                } else {
                    CatalogLaunchOrigin.REPLACEMENT
                },
                selectionMode = if (targetExerciseId == null) {
                    CatalogSelectionMode.MULTIPLE
                } else {
                    CatalogSelectionMode.REPLACEMENT
                },
                targetExerciseId = targetExerciseId,
                targetCatalogDefinitionId = targetExercise?.catalogDefinitionId,
                targetCatalogConfigurationId = targetExercise?.catalogConfigurationId
                    ?: targetExercise?.exerciseDbId,
                targetGroupName = targetGroupName,
                selectedExerciseIds = uiState.selectedExercisesIds.toList(),
                initialQuery = uiState.searchQuery,
            )
            catalogRequestId = request.requestId
            onOpenCatalog(request)
        }
    }
    LaunchedEffect(catalogResult?.requestId) {
        val result = catalogResult ?: return@LaunchedEffect
        if (catalogRequestId != null && result.requestId != catalogRequestId) {
            val expectedRequestId = catalogRequestId
            android.util.Log.e("SessionEditorScreen", "Rejecting catalog result for unknown requestId=${result.requestId}; expected=$expectedRequestId")
            onCatalogResultConsumed()
            catalogRequestInFlight = false
            catalogRequestId = null
            val action = snackbarHostState.showKpknSnackbar(
                message = "El resultado del catálogo ya no corresponde a esta solicitud",
                type = SnackbarType.DANGER,
                actionLabel = "Reintentar",
            )
            if (action == SnackbarResult.ActionPerformed && onOpenCatalog != null) {
                val targetGroupName = uiState.pickerTargetPartId?.let { pid ->
                    uiState.session?.parts?.firstOrNull { it.id == pid }?.name
                }
                val retry = CatalogLaunchRequest(
                    origin = if (uiState.pickerTargetExerciseId == null) CatalogLaunchOrigin.SESSION_EDITOR else CatalogLaunchOrigin.REPLACEMENT,
                    selectionMode = if (uiState.pickerTargetExerciseId == null) CatalogSelectionMode.MULTIPLE else CatalogSelectionMode.REPLACEMENT,
                    targetExerciseId = uiState.pickerTargetExerciseId,
                    targetGroupName = targetGroupName,
                    selectedExerciseIds = uiState.selectedExercisesIds.toList(),
                    initialQuery = uiState.searchQuery,
                )
                catalogRequestInFlight = true
                catalogRequestId = retry.requestId
                onOpenCatalog(retry)
            } else {
                viewModel.closeSheet()
            }
            return@LaunchedEffect
        }
        val targetPartId = uiState.pickerTargetPartId
        val targetExerciseId = uiState.pickerTargetExerciseId
        val targetGroupName = targetPartId?.let { pid ->
            uiState.session?.parts?.firstOrNull { it.id == pid }?.name
        }
        val expectedRequest = CatalogLaunchRequest(
            requestId = catalogRequestId ?: result.requestId,
            origin = if (targetExerciseId == null) CatalogLaunchOrigin.SESSION_EDITOR else CatalogLaunchOrigin.REPLACEMENT,
            selectionMode = if (targetExerciseId == null) CatalogSelectionMode.MULTIPLE else CatalogSelectionMode.REPLACEMENT,
            targetExerciseId = targetExerciseId,
            targetGroupName = targetGroupName,
            selectedExerciseIds = uiState.selectedExercisesIds.toList(),
            initialQuery = uiState.searchQuery,
        )
        if (catalogRequestId != null && !result.isValidFor(expectedRequest)) {
            android.util.Log.e("SessionEditorScreen", "Catalog result rejected: invalid request context/action/version")
            val action = snackbarHostState.showKpknSnackbar(
                message = "Resultado de catálogo inválido; no se aplicó ningún cambio",
                type = SnackbarType.DANGER,
                actionLabel = "Reintentar",
            )
            onCatalogResultConsumed()
            catalogRequestInFlight = false
            catalogRequestId = null
            if (action == SnackbarResult.ActionPerformed && onOpenCatalog != null) {
                val retry = expectedRequest.copy(requestId = UUID.randomUUID().toString())
                catalogRequestInFlight = true
                catalogRequestId = retry.requestId
                onOpenCatalog(retry)
            } else {
                viewModel.closeSheet()
            }
            return@LaunchedEffect
        }
        if (result.canceled) {
            viewModel.closeSheet()
        } else {
            val unresolved = result.unresolvedSelectionIds(exerciseInfoById)
            if (unresolved.isNotEmpty()) {
                android.util.Log.e("SessionEditorScreen", "Catalog result rejected atomically; unresolved=$unresolved")
                val action = snackbarHostState.showKpknSnackbar(
                    message = "No se pudieron resolver todos los ejercicios; vuelve a intentarlo",
                    type = SnackbarType.DANGER,
                    actionLabel = "Reintentar",
                )
                onCatalogResultConsumed()
                catalogRequestInFlight = false
                catalogRequestId = null
                if (action == SnackbarResult.ActionPerformed && onOpenCatalog != null) {
                    val retry = expectedRequest.copy(requestId = UUID.randomUUID().toString())
                    catalogRequestInFlight = true
                    catalogRequestId = retry.requestId
                    onOpenCatalog(retry)
                } else {
                    viewModel.closeSheet()
                }
                return@LaunchedEffect
            }
            val infos = result.resolveSelectedInfos(exerciseInfoById)
            if (result.commitAction == CatalogCommitAction.CREATE_SUPERSET && targetExerciseId == null) {
                viewModel.addExercisesAsSupersetToPart(
                    partId = targetPartId,
                    infos = infos,
                    config = result.supersetConfig ?: CatalogSupersetConfig(),
                )
            } else if (targetExerciseId != null) {
                infos.firstOrNull()?.let { info ->
                    viewModel.replaceExerciseInPart(targetPartId, targetExerciseId, info)
                } ?: viewModel.closeSheet()
            } else if (infos.isNotEmpty()) {
                viewModel.addExercisesToPart(targetPartId, infos)
            } else {
                viewModel.closeSheet()
            }
        }
        catalogRequestId = null
        onCatalogResultConsumed()
    }
    val dragController = viewModel.dragController
    val dragUi by viewModel.dragUiState.collectAsStateWithLifecycle()
    val partBounds = dragController.partBounds
    val partContentBounds = dragController.partContentBounds
    val exerciseBounds = dragController.exerciseBounds
    var looseContentBounds by dragController::looseContentBounds
    LaunchedEffect(session?.id) {
        // Cambio de sesión: los bounds pertenecen al layout anterior.
        viewModel.clearDragForSessionChange()
    }
    LaunchedEffect(session?.parts, session?.exercises, uiState.collapsedPartIds) {
        val active = session ?: return@LaunchedEffect
        dragController.invalidateLayoutCachesIfIdle()
        dragController.pruneBounds(active, uiState.collapsedPartIds)
    }
    val draggingPartId = dragUi.draggingPartId
    val partDropTargetId = dragUi.partDropTargetId
    val partDropTargetIndex = dragUi.partDropTargetIndex
    val draggingExerciseId = dragUi.draggingExerciseId
    val draggingExercisePartId = dragUi.draggingExercisePartId
    val exerciseDropTargetKey = dragUi.exerciseDropTargetKey
    val exerciseDropTargetPartId = dragUi.exerciseDropTargetPartId
    val exerciseDropTargetIndex = dragUi.exerciseDropTargetIndex
    val exerciseDropOutOfRange = dragUi.exerciseDropOutOfRange
    var lazyColumnWindowBounds by remember { mutableStateOf<Rect?>(null) }

    fun currentListGeometry(): Map<String, Rect> {
        val windowBounds = lazyColumnWindowBounds ?: return emptyMap()
        val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { itemInfo ->
            val key = itemInfo.key as? String ?: return@mapNotNull null
            SessionEditorVisibleItem(key = key, offset = itemInfo.offset, size = itemInfo.size)
        }
        return collectSessionEditorListGeometry(visible, windowBounds, session)
    }

    fun beginExerciseDrag(partId: String, exerciseId: String, pointerWindow: Offset) {
        dragController.beginExerciseDrag(
            partId = partId,
            exerciseId = exerciseId,
            grabOffset = Offset(24f, 24f),
            liveBounds = currentListGeometry().takeIf { it.isNotEmpty() },
            pointerStartWindow = pointerWindow,
            session = session,
            collapsedPartIds = uiState.collapsedPartIds,
        )
    }

    fun beginPartDrag(partId: String, grabRect: Rect?, pointerWindow: Offset?) {
        val geometry = currentListGeometry()
        val livePartBounds = geometry
            .filterKeys { it.startsWith("header|") }
            .mapKeys { it.key.removePrefix("header|") }
            .takeIf { it.isNotEmpty() }
        dragController.beginPartDrag(
            partId = partId,
            livePartBounds = livePartBounds,
            liveStartRect = grabRect,
            pointerStartWindow = pointerWindow,
            groupedParts = session?.parts?.filterNot { it.isUncategorizedPart() },
        )
    }

    fun endExerciseDrag() {
        val activeSession = session ?: return
        val result = dragController.endExerciseDrag(activeSession) { fromPartId, exerciseId, toPartId, toIndex ->
            viewModel.moveExerciseToPart(
                sourcePartId = fromPartId,
                exerciseId = exerciseId,
                targetPartId = toPartId,
                targetIndex = toIndex,
                // Read the final projection after endExerciseDrag() recomputes
                // the drop target. Capturing these before the final recompute
                // could lose the group the card is being dropped into.
                moveAsGroup = dragController.draggingExerciseScope == ExerciseDragScope.BLOCK,
                targetGroupId = dragController.exerciseDropTargetGroupId,
            )
        }
        if (result == ExerciseDragEndResult.OutOfRange) {
            scope.launch {
                snackbarHostState.showKpknSnackbar("Zona no válida", SnackbarType.DANGER)
            }
        }
    }

    if (session == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KpknSurface.canvas)
                .statusBarsPadding(),
        ) {
            KpknTopRow(onBack = onBack)
            val loadErrorMessage = uiState.loadErrorMessage
            if (loadErrorMessage != null) {
                SessionEditorLoadError(
                    message = loadErrorMessage,
                    onBack = onBack,
                    onRetry = viewModel::retryLoadSession,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                SessionEditorLoadingSkeleton(modifier = Modifier.padding(top = 12.dp))
            }
        }
        return
    }
    val sessionListItems = remember(
        session.parts,
        session.exercises,
        session.supersetGroups,
        session.cardioFirst,
        uiState.collapsedPartIds,
        uiState.strengthSpaceCommitted,
        uiState.cardioSpacePlacement,
    ) {
        buildSessionListItems(
            session = session,
            collapsedPartIds = uiState.collapsedPartIds,
            showStrengthDivider = uiState.strengthSpaceCommitted,
            cardioAtStart = when (uiState.cardioSpacePlacement) {
                CardioSpacePlacement.START -> true
                CardioSpacePlacement.END -> false
                null -> null
            },
        )
    }
    val scrollableListItems = remember(sessionListItems) {
        sessionListItems.drop(1)
    }
    val allExercisesForUi = remember(session.exercises, session.parts) { session.allExercises() }
    // Cabecera (SESSION_EDITOR_V1 §2): etiqueta del chip del navegador y duración de la línea de datos y de la isla.
    val navigatorLabel = listOfNotNull(
        uiState.dayOfWeek?.let { sessionEditorDayLabelShort(it) },
        session.name.ifBlank { "Sesión" },
    ).joinToString(" · ")
    val estimatedMinutes = uiState.sessionTimeBreakdown?.totalMinutes ?: uiState.estimatedDurationMinutes

    // Auto-scroll al ejercicio recién añadido (índice = Hero + offset en scrollable)
    LaunchedEffect(pendingAutoExpandExerciseId) {
        val expandId = pendingAutoExpandExerciseId ?: return@LaunchedEffect
        val targetIndex = lazyColumnIndexForExercise(scrollableListItems, expandId, leadingItems = EditorLeadingItems)
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    val groupedParts = remember(session.parts) { session.parts.filterNot { it.isUncategorizedPart() } }
    fun updateExerciseDrag(delta: Offset) {
        val activeSession = session ?: return
        dragController.updateExerciseDrag(delta, activeSession, groupedParts)
    }

    fun projectedShiftFor(
        partId: String,
        index: Int,
        exerciseId: String,
        itemHeight: Float = (dragController.frozenExerciseBounds["$partId|$exerciseId"]
            ?: exerciseBounds["$partId|$exerciseId"])?.height ?: 88f,
    ): Float {
        val activeSession = session ?: return 0f
        return dragController.calculateProjectedShift(activeSession, partId, index, exerciseId, itemHeight)
    }

    BackHandler(enabled = showMoreMenu) { showMoreMenu = false }
    BackHandler(enabled = !showDiscardDialog && uiState.sheet == SessionEditorSheet.NONE && !showNavigatorSheet && !showMoreMenu) {
        requestExit()
    }
    BackHandler(enabled = uiState.sheet == SessionEditorSheet.AUGE) {
        viewModel.closeSheet()
    }

    var editorRootBounds by remember { mutableStateOf<Rect?>(null) }

    // Auto-scroll durante drag: si el dedo se acerca al borde superior/inferior
    // del viewport, scrollea la lista y desplaza los Rect congelados en window
    // inverso al scroll para mantener hit-testing coherente.
    LaunchedEffect(draggingExerciseId, draggingPartId, lazyColumnWindowBounds) {
        if (draggingExerciseId == null && draggingPartId == null) return@LaunchedEffect
        while (isActive) {
            val viewport = lazyColumnWindowBounds
            if (viewport == null) {
                delay(16)
                continue
            }
            val pointerY: Float? = when {
                dragController.draggingExerciseId != null -> {
                    dragController.currentExercisePointer()?.y
                }
                dragController.draggingPartId != null -> {
                    val partPointer = dragController.dragPartPointerStartWindow
                    if (partPointer != null) {
                        partPointer.y + dragController.draggingPartOffsetY
                    } else {
                        val start = dragController.dragStartPartRect
                        if (start != null) start.center.y + dragController.draggingPartOffsetY else null
                    }
                }
                else -> null
            }
            if (pointerY == null) {
                delay(16)
                continue
            }
            val thresholdPx = with(density) { 96.dp.toPx() }
            val scrollSpeedPx = with(density) { 18.dp.toPx() }
            val canScroll = listState.layoutInfo.totalItemsCount > 0
            if (!canScroll) {
                delay(16)
                continue
            }
            val delta: Float = when {
                pointerY < viewport.top + thresholdPx -> -scrollSpeedPx
                pointerY > viewport.bottom - thresholdPx - with(density) { fabBottomPadding.toPx() } -> scrollSpeedPx
                else -> 0f
            }
            if (delta != 0f) {
                var consumed = 0f
                listState.scroll { consumed = scrollBy(delta) }
                if (consumed != 0f) {
                    dragController.applyScrollDelta(consumed, currentListGeometry())
                    if (dragController.draggingExerciseId != null) {
                        dragController.recomputeExerciseDropTarget()
                    } else if (dragController.draggingPartId != null) {
                        dragController.recomputePartDropTarget()
                    }
                }
            }
            delay(16)
        }
    }

    LaunchedEffect(draggingExerciseId, draggingPartId) {
        if (draggingExerciseId == null && draggingPartId == null) return@LaunchedEffect
        snapshotFlow {
            val window = lazyColumnWindowBounds
            val rows = listState.layoutInfo.visibleItemsInfo.map { item ->
                Triple(item.key as? String, item.offset, item.size)
            }
            window to rows
        }.collect { (window, rows) ->
            if (window == null) return@collect
            val visible = rows.mapNotNull { (key, offset, size) ->
                key?.let { SessionEditorVisibleItem(it, offset, size) }
            }
            val geometry = collectSessionEditorListGeometry(visible, window, session)
            dragController.refreshVisibleGeometryDuringDrag(geometry)
            if (dragController.draggingExerciseId != null) {
                dragController.recomputeExerciseDropTarget()
            } else if (dragController.draggingPartId != null) {
                dragController.recomputePartDropTarget()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KpknSurface.canvas)
            .onGloballyPositioned { editorRootBounds = it.boundsInWindow() }
            .drawWithContent {
                drawContent()
                RepairBenchmarkTrace.editorReadyDrawn(
                    requestId = repairBenchmarkEditorRequestId,
                    ready = session != null && uiState.loadErrorMessage == null,
                )
            },
    ) {
        // Fila superior, franja de portada, cabecera y lista. Sin Scaffold: la isla inferior ocupa el lugar del antiguo FAB.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .hazeSource(state = hazeState)
                .onGloballyPositioned { lazyColumnWindowBounds = it.boundsInWindow() },
            contentPadding = PaddingValues(bottom = contentBottomPadding),
        ) {
            // Ítems 0, 1 y 2 (EditorHeaderIndex): fila superior, franja de portada y cabecera de la sesión.
            item {
                KpknTopRow(
                    onBack = ::requestExit,
                    actions = {
                        SessionEditorTopActions(
                            saveStatus = if (uiState.hasUnsavedChanges) "Sin guardar" else "Guardado",
                            onMore = { showMoreMenu = true },
                            onMoreBoundsChanged = { moreAnchor = it },
                        )
                    },
                )
            }
            item {
                SessionEditorCoverStrip(background = session.background)
            }
            item {
                SessionEditorHeader(
                    sessionId = session.id,
                    name = session.name,
                    description = session.description,
                    navigatorLabel = navigatorLabel,
                    exerciseCount = allExercisesForUi.size,
                    setCount = allExercisesForUi.sumOf { it.sets.size },
                    durationMinutes = estimatedMinutes.takeIf { it > 0 },
                    onNameCommit = viewModel::updateSessionName,
                    onDescriptionCommit = viewModel::updateSessionDescription,
                    onOpenNavigator = { showNavigatorSheet = true },
                )
            }

            items(scrollableListItems, key = { it.stableKey }, contentType = { it::class }) { listItem ->
                SessionEditorListItem(
                    listItem = listItem,
                    session = session,
                    groupedParts = groupedParts,
                    uiState = uiState,
                    exerciseInfoById = exerciseInfoById,
                    dragController = dragController,
                    draggingExerciseId = draggingExerciseId,
                    exerciseDropTargetKey = exerciseDropTargetKey,
                    exerciseDropTargetPartId = exerciseDropTargetPartId,
                    exerciseDropTargetIndex = exerciseDropTargetIndex,
                    draggingPartId = draggingPartId,
                    partDropTargetId = partDropTargetId,
                    pendingAutoExpandExerciseId = pendingAutoExpandExerciseId,
                    onPendingAutoExpandHandled = { exerciseId ->
                        if (pendingAutoExpandExerciseId == exerciseId) pendingAutoExpandExerciseId = null
                    },
                    onLooseBoundsReport = { rect ->
                        dragController.setLooseContentBounds(rect, fromStrengthAddActions = false)
                    },
                    onStrengthAddActionsBoundsReport = { rect ->
                        dragController.setLooseContentBounds(rect, fromStrengthAddActions = true)
                    },
                    onPartContentBoundsReport = { partId, rect ->
                        dragController.setPartContentBounds(partId, rect)
                    },
                    beginExerciseDrag = ::beginExerciseDrag,
                    updateExerciseDrag = ::updateExerciseDrag,
                    endExerciseDrag = ::endExerciseDrag,
                    cancelExerciseDrag = dragController::cancelExerciseDrag,
                    beginPartDrag = ::beginPartDrag,
                    cancelPartDrag = dragController::cancelPartDrag,
                    projectedShiftFor = ::projectedShiftFor,
                    viewModel = viewModel,
                )
            }
        }

        // Isla superior: aparece cuando la cabecera sale de la vista (KPKN_UI_V2 §3). Lleva «Atrás» para que la salida no quede tapada.
        KpknTopIsland(
            visible = headerScrolledAway,
            title = session.name.ifBlank { "Nueva sesión" },
            subtitle = estimatedMinutes.takeIf { it > 0 }?.let { "≈ $it min" },
            leading = {
                KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = ::requestExit)
            },
            actions = {
                KpknIconButton(
                    icon = KpknLineIcons.Plus,
                    contentDescription = "Añadir ejercicio",
                    onClick = viewModel::openPickerForUncategorized,
                )
            },
        )

        // Isla inferior (vidrio v2): añadir ejercicio, asistente y tiempo. Sustituye al grupo de botones flotantes arrastrable.
        SessionEditorBottomIsland(
            visible = draggingExerciseId == null && draggingPartId == null,
            timeLabel = estimatedMinutes.takeIf { it > 0 }?.let { "≈ $it min" },
            hazeState = hazeState,
            onAddExercise = viewModel::openPickerForUncategorized,
            onAssistant = ::openAssistantSheet,
            onTime = { viewModel.openRulesSheet(initialTab = 1) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
                .zIndex(260f),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
                .zIndex(280f),
        ) { KpknSnackbar(it) }

        val previewExercise = draggingExerciseId?.let { activeId -> allExercisesForUi.firstOrNull { it.id == activeId } }
        val previewPartId = draggingExercisePartId
        val previewRect = if (previewPartId != null && draggingExerciseId != null) {
            dragController.dragStartExerciseRect ?: exerciseBounds["$previewPartId|$draggingExerciseId"]
        } else {
            null
        }
        val previewSupersetCount = if (previewExercise != null && previewPartId != null) {
            val list = if (previewPartId == "__loose__") session.exercises
            else session.parts.firstOrNull { it.id == previewPartId }?.exercises.orEmpty()
            val gid = previewExercise.supersetGroupRefOrLegacyId()
            if (gid != null) list.count { it.supersetGroupRefOrLegacyId() == gid } else 0
        } else {
            0
        }
        val previewIsGroup = dragController.draggingExerciseScope == ExerciseDragScope.BLOCK && previewSupersetCount >= 2
        if (previewExercise != null && previewRect != null) {
            DragLiftPreview(
                exercise = previewExercise,
                rect = previewRect,
                offsetProvider = { dragController.draggingExerciseOffset },
                rootBounds = editorRootBounds,
                modifier = Modifier.zIndex(500f),
                titleOverride = if (previewIsGroup) "Superserie" else null,
                subtitleOverride = if (previewIsGroup) {
                    "$previewSupersetCount ejercicios"
                } else {
                    null
                },
            )
        }
        SessionEditorInvalidDropBanner(
            visible = exerciseDropOutOfRange && draggingExerciseId != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
                .zIndex(510f),
        )
        val draggingPart = draggingPartId?.let { id -> groupedParts.firstOrNull { it.id == id } }
        val partPreviewRect = draggingPartId?.let { dragController.dragStartPartRect ?: partBounds[it] }
        if (draggingPart != null && partPreviewRect != null) {
            DragPartLiftPreview(
                partName = draggingPart.name,
                rect = partPreviewRect,
                offsetYProvider = { dragController.draggingPartOffsetY },
                rootBounds = editorRootBounds,
                modifier = Modifier.zIndex(500f),
            )
        }

        if (uiState.sheet == SessionEditorSheet.AUGE) {
            AssistantGlassOverlay(
                uiState = uiState,
                templates = allTemplates,
                hazeState = hazeState,
                onDismiss = viewModel::closeSheet,
                onApplyAssistantSuggestion = { suggestionId, detailIds ->
                    viewModel.applyAssistantSuggestion(suggestionId, detailIds)
                    scope.launch {
                        snackbarHostState.showKpknSnackbar("Ajuste aplicado", SnackbarType.SUCCESS)
                    }
                },
                onTemplateSearchChange = viewModel::setTemplateSearchQuery,
                onSelectTemplate = viewModel::selectTemplate,
                onConfirmApplyTemplate = viewModel::confirmTemplateApply,
                onCancelTemplateApply = viewModel::cancelTemplateApply,
                archivedUserTemplates = archivedUserTemplates,
                templatesReady = templatesReady,
                corruptTemplateIds = corruptTemplateIds,
                onArchiveUserTemplate = viewModel::archiveUserTemplate,
                onRestoreUserTemplate = viewModel::restoreUserTemplate,
                onDeleteUserTemplate = viewModel::deleteUserTemplate,
                onEditUserTemplate = { template, name, description, difficulty, focus, duration, splitIds, dayLabels, autoGeneration ->
                    viewModel.updateUserTemplateMetadataNow(
                        template = template,
                        name = name,
                        description = description,
                        difficulty = difficulty,
                        focusCategory = focus,
                        durationClass = duration,
                        splitIds = splitIds,
                        splitDayLabels = dayLabels,
                        autoGenerationEligible = autoGeneration,
                    )
                },
                onSaveCurrentTemplate = { name, description ->
                    viewModel.saveCurrentSessionAsTemplateNow(name, description)
                },
            )
        }
    }

    SessionEditorSheets(
        uiState = uiState,
        onDismiss = viewModel::closeSheet,
        onPickImage = { openDocument.launch(arrayOf("image/*")) },
        onSelectGradient = { viewModel.updateBackgroundValue(it, SessionBackgroundType.COLOR) },
        onBackgroundBlurChange = { viewModel.updateBackgroundStyle(blur = it) },
        onBackgroundBrightnessChange = { viewModel.updateBackgroundStyle(brightness = it) },
        onCoverBrightnessChange = viewModel::updateFilterBrightness,
        onCoverContrastChange = { viewModel.updateCoverFilters(contrast = it) },
        onCoverSaturationChange = { viewModel.updateCoverFilters(saturation = it) },
        onCoverGrayscaleChange = { viewModel.updateCoverFilters(grayscale = it) },
        onCoverVignetteChange = { viewModel.updateCoverFilters(vignette = it) },
        onCoverMotionChange = viewModel::updateCoverMotion,
        onLabelPositionChange = viewModel::updateLabelPosition,
        onExerciseSearch = viewModel::setSearchQuery,
        onSelectExercise = { info ->
            val targetPartId = uiState.pickerTargetPartId
            val targetExerciseId = uiState.pickerTargetExerciseId
            when {
                targetExerciseId != null -> {
                    viewModel.replaceExerciseInPart(targetPartId, targetExerciseId, info)
                    pendingAutoExpandExerciseId = targetExerciseId
                }
                else -> {
                    pendingAutoExpandExerciseId = viewModel.addExerciseToPart(targetPartId, info)
                }
            }
        },
        onMultiSelectExercises = { infos ->
            val targetPartId = uiState.pickerTargetPartId
            viewModel.addExercisesToPart(targetPartId, infos)
        },
        onToggleExerciseSelection = viewModel::toggleExerciseSelection,
        onClearExerciseSelection = viewModel::clearExerciseSelection,
        onApplyRules = { partId ->
            val outcome = viewModel.applyRuleDefaultsToSession(partId)
            scope.launch {
                when (outcome) {
                    is ApplyRulesOutcome.Applied -> {
                        val target = if (partId == null) "la sesión" else "el grupo"
                        val noun = if (outcome.exercisesChanged == 1) "ejercicio" else "ejercicios"
                        snackbarHostState.showKpknSnackbar(
                            "Ajustes por defecto aplicados a $target (${outcome.exercisesChanged} $noun)",
                            SnackbarType.SUCCESS,
                        )
                    }
                    ApplyRulesOutcome.NoChanges -> snackbarHostState.showKpknSnackbar(
                        "Sin cambios: las tarjetas ya tienen estos valores",
                        SnackbarType.SUGGESTION,
                    )
                    is ApplyRulesOutcome.ScopeNotFound -> snackbarHostState.showKpknSnackbar(
                        "Ese grupo no existe en esta sesión",
                        SnackbarType.DANGER,
                    )
                }
            }
            outcome
        },
        onCloneCurrentToTargets = { targetKeys, selectedExerciseIds, applyMode ->
            val result = viewModel.cloneCurrentSessionToTargets(targetKeys, selectedExerciseIds, applyMode)
            scope.launch {
                snackbarHostState.showKpknSnackbar(
                    result.message,
                    if (result.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
                )
            }
        },
        onImportFromSource = { sourceSessionId, selectedExerciseIds, applyMode ->
            val result = viewModel.importFromSourceSession(sourceSessionId, selectedExerciseIds, applyMode)
            scope.launch {
                snackbarHostState.showKpknSnackbar(
                    result.message,
                    if (result.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
                )
            }
        },
        onSave = { saveScope ->
            focusManager.clearFocus(force = true)
            val hasPendingSwitch = uiState.pendingSessionSwitchId != null
            scope.launch {
                val saveResult = viewModel.saveSession(saveScope)
                if (saveResult.success && !hasPendingSwitch) {
                    onSavedAndExit()
                } else if (!saveResult.success) {
                    val action = snackbarHostState.showKpknSnackbar(
                        saveResult.message,
                        SnackbarType.DANGER,
                        actionLabel = "Reintentar",
                    )
                    if (action == SnackbarResult.ActionPerformed) {
                        val retry = viewModel.saveSession(saveScope)
                        if (retry.success && !hasPendingSwitch) onSavedAndExit()
                        else if (!retry.success) snackbarHostState.showKpknSnackbar(retry.message, SnackbarType.DANGER)
                    }
                } else {
                    snackbarHostState.showKpknSnackbar(
                        saveResult.message,
                        SnackbarType.SUCCESS,
                    )
                }
            }
        },
        onApplyAssistantSuggestion = { suggestionId, detailIds ->
            viewModel.applyAssistantSuggestion(suggestionId, detailIds)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Ajuste aplicado", SnackbarType.SUCCESS)
            }
        },
        onDiscardSwitch = if (uiState.pendingSessionSwitchId != null) viewModel::discardAndSwitchPendingSession else null,
        onWarmupSave = { exerciseId, sets ->
            val partId = session.parts.firstOrNull { part -> part.exercises.any { it.id == exerciseId } }?.id
            viewModel.updateWarmupSets(partId, exerciseId, sets)
            viewModel.closeSheet()
        },
        onRestoreSnapshot = viewModel::restoreDraftSnapshot,
        onRuleDefaultsChange = { partId, setCount, reps, rpe, normalRest, sideRest, supersetBetween, supersetRound, applyToNew, intensityType ->
            viewModel.updateRuleDefaults(
                partId = partId,
                setCount = setCount,
                reps = reps,
                rpe = rpe,
                normalRestSeconds = normalRest,
                betweenSidesRestSeconds = sideRest,
                supersetBetweenRestSeconds = supersetBetween,
                supersetRoundRestSeconds = supersetRound,
                applyToNewItems = applyToNew,
                intensityType = intensityType,
            )
        },
        onRuleLimitsChange = { maxRPE, maxExercisesPerMuscle ->
            viewModel.updateRuleLimits(maxRPE = maxRPE, maxExercisesPerMuscle = maxExercisesPerMuscle)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Límites guardados", SnackbarType.SUCCESS)
            }
        },
        onAdvancedRuleLimitsChange = { maxSessionVolume, maxWeeklyVolume, maxPattern, rigid ->
            viewModel.updateAdvancedRuleLimits(
                maxVolumePerMuscleSession = maxSessionVolume,
                maxVolumePerMuscleWeekly = maxWeeklyVolume,
                maxSamePatternPerSession = maxPattern,
                rigidLimits = rigid,
            )
        },
        onApplyGlobalIntensityAdjustment = { mode, value, muscles ->
            viewModel.applyGlobalIntensityAdjustment(mode, value, muscles)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Modificación global aplicada", SnackbarType.SUCCESS)
            }
        },
        onQuickActionOpenPicker = viewModel::triggerQuickActionOpenPicker,
        onQuickActionOpenWarmup = viewModel::triggerQuickActionOpenWarmup,
        onQuickActionOpenMobility = viewModel::triggerQuickActionOpenMobility,
        onAddMobilityExercise = viewModel::addMobilityToQuickActionExercise,
        onRemoveMobilityExercise = { info ->
            val quickActionsExerciseId = uiState.quickActionsExerciseId
            val targetSeries = quickActionsExerciseId
                ?.let { id -> uiState.session?.allExercises()?.firstOrNull { it.id == id }?.mobilitySeries }
                .orEmpty()
            val existing = targetSeries.firstOrNull { it.catalogIdentityKey() == info.id }
            if (existing != null && quickActionsExerciseId != null) {
                viewModel.removeMobilitySeries(uiState.quickActionsPartId, quickActionsExerciseId, existing.id)
            }
        },
        onAddCardio = viewModel::addCardioToPart,
        onQuickActionDelete = viewModel::triggerQuickActionDelete,
        onQuickActionCreateSuperset = viewModel::triggerQuickActionCreateSuperset,
        onQuickActionManageSuperset = viewModel::triggerQuickActionManageSuperset,
        onLinkExerciseRelativeTo = viewModel::linkExerciseRelativeTo,
        onOpenSupersetManager = viewModel::openSupersetManager,
        onUpdateSupersetRestBetween = viewModel::updateSupersetRestBetween,
        onUpdateSupersetRestAfter = viewModel::updateSupersetRestAfter,
        onRemoveFromSuperset = viewModel::removeFromSuperset,
        onSupersetDraftUpdate = viewModel::updateSupersetDraft,
        onCreateSupersetGroup = viewModel::createSupersetGroupFromDraft,
        onOpenSupersetCreator = viewModel::openSupersetCreator,
        onOpenExerciseDetail = onOpenExerciseDetail,
        onOpenCatalog = viewModel::openPickerForUncategorized,
        useFullPageCatalog = onOpenCatalog != null,
        allTemplates = allTemplates,
        onSelectTemplate = viewModel::selectTemplate,
        onConfirmApplyTemplate = viewModel::confirmTemplateApply,
        onCancelTemplateApply = viewModel::cancelTemplateApply,
        onTemplateSearchChange = viewModel::setTemplateSearchQuery,
        archivedUserTemplates = archivedUserTemplates,
        templatesReady = templatesReady,
        corruptTemplateIds = corruptTemplateIds,
        onArchiveUserTemplate = viewModel::archiveUserTemplate,
        onRestoreUserTemplate = viewModel::restoreUserTemplate,
        onDeleteUserTemplate = viewModel::deleteUserTemplate,
        onEditUserTemplate = { template, name, description, difficulty, focus, duration, splitIds, dayLabels, autoGeneration ->
            viewModel.updateUserTemplateMetadataNow(
                template = template,
                name = name,
                description = description,
                difficulty = difficulty,
                focusCategory = focus,
                durationClass = duration,
                splitIds = splitIds,
                splitDayLabels = dayLabels,
                autoGenerationEligible = autoGeneration,
            )
        },
        onSaveCurrentTemplate = { name, description ->
            viewModel.saveCurrentSessionAsTemplateNow(name, description)
        },
        setTargetDuration = viewModel::setTargetDuration,
        setPartTargetDuration = viewModel::setPartTargetDuration,
        setExerciseTargetDuration = viewModel::setExerciseTargetDuration,
        onDistributeTargetAcrossParts = viewModel::distributeTargetDurationAcrossParts,
        onApplyRuleTemplate = { templateId, partId -> viewModel.applyRuleTemplate(templateId, partId) },
        onSaveRuleTemplate = viewModel::saveCurrentRulesAsTemplate,
        onRenameRuleTemplate = viewModel::renameRuleTemplate,
        onDeleteRuleTemplate = viewModel::deleteRuleTemplate,
        onPatchRuleDefaults = viewModel::patchRuleDefaults,
        onApplyTimeCoachSuggestion = viewModel::applyTimeCoachSuggestion,
        onDismissTimeCoachSuggestion = viewModel::dismissTimeCoachSuggestion,
        onRefreshTimeCoach = viewModel::refreshTimeCoachSuggestions,
        onRulesInitialTabConsumed = viewModel::clearRulesSheetInitialTab,
    )

    // Menú «⋯» de la sesión: anclado al botón, con las acciones que ya existían (SESSION_EDITOR_V1 §2, punto 1).
    val moreAnchorRect = moreAnchor
    if (showMoreMenu && moreAnchorRect != null) {
        SessionEditorMoreMenu(
            anchor = moreAnchorRect,
            onDismiss = { showMoreMenu = false },
            onCover = {
                showMoreMenu = false
                viewModel.openSheet(SessionEditorSheet.BACKGROUND)
            },
            onHistory = {
                showMoreMenu = false
                viewModel.openSheet(SessionEditorSheet.HISTORY)
            },
            onTransfer = {
                showMoreMenu = false
                viewModel.openSheet(SessionEditorSheet.TRANSFER)
            },
            onSaveTemplate = {
                showMoreMenu = false
                showSaveTemplateDialog = true
            },
            onRules = {
                showMoreMenu = false
                viewModel.openRulesSheet(initialTab = 0)
            },
        )
    }

    // Navegador de sesiones: la hoja que abre el chip de la cabecera (lo que antes enseñaba SessionContextNavigator).
    if (showNavigatorSheet) {
        val weekOffset = uiState.weekStartDay.coerceIn(1, 7) - 1
        val orderedDays = (1..7).toList().let { days -> days.drop(weekOffset) + days.take(weekOffset) }
        val nextVariant = listOf(WeekVariant.B, WeekVariant.C, WeekVariant.D)
            .firstOrNull { it !in uiState.availableVariants }
        val weekCaption = if (uiState.isSimpleProgram && uiState.hasActiveLoops) {
            uiState.roadmapOptions.firstOrNull { it.weekId.isNotBlank() }?.let { option ->
                "Semana ${option.weekIndex + 1} · ${option.weekName}"
            }
        } else {
            null
        }
        SessionNavigatorSheet(
            orderedDays = orderedDays,
            sessions = uiState.siblingSessions,
            currentSessionId = session.id,
            currentDayOfWeek = uiState.dayOfWeek,
            activeVariant = uiState.activeVariant,
            availableVariants = uiState.availableVariants,
            variantName = { variant ->
                when (variant) {
                    WeekVariant.A -> "Original"
                    WeekVariant.B -> uiState.session?.sessionB?.name ?: "Derivada"
                    WeekVariant.C -> uiState.session?.sessionC?.name ?: "Derivada"
                    WeekVariant.D -> uiState.session?.sessionD?.name ?: "Derivada"
                }
            },
            nextVariant = nextVariant,
            defaultVariantName = "${uiState.session?.name.orEmpty().ifBlank { "Sesión" }} – Rápida",
            roadmapOptions = uiState.roadmapOptions,
            currentWeekId = uiState.weekId,
            blockGrouping = !uiState.isSimpleProgram && uiState.roadmapOptions.map { it.blockIndex }.distinct().size > 1,
            weekCaption = weekCaption,
            onDismiss = { showNavigatorSheet = false },
            onSelectSession = viewModel::requestSessionSwitch,
            onMarkMain = viewModel::setMainSessionForDay,
            onCreateSessionForDay = { day -> viewModel.createSessionForDay(day) },
            onSelectVariant = { variant ->
                viewModel.commitActiveVariantChanges()
                viewModel.switchVariant(variant)
            },
            onCreateVariant = { variant, name -> viewModel.createVariant(variant, name) },
            onDeleteActiveVariant = { viewModel.deleteVariant(uiState.activeVariant) },
            onSelectRoadmapOption = viewModel::selectRoadmapOption,
        )
    }

    // Guardar la sesión como plantilla (menú «⋯»): la misma escritura que usa la pestaña de plantillas del asistente.
    if (showSaveTemplateDialog) {
        SessionTemplateSaveDialog(
            onDismiss = { showSaveTemplateDialog = false },
            onSaved = {
                showSaveTemplateDialog = false
                scope.launch {
                    snackbarHostState.showKpknSnackbar("Plantilla guardada", SnackbarType.SUCCESS)
                }
            },
            onSave = { name, description -> viewModel.saveCurrentSessionAsTemplateNow(name, description) },
        )
    }

    if (showDiscardDialog) {
        KpknAlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Salir del editor", style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tienes cambios sin guardar.", style = KpknType.body, color = WizardColors.textMuted)
                    KpknSecondaryPill(
                        text = "Cerrar sin guardar",
                        onClick = {
                            scope.launch {
                                if (viewModel.discardDraftForCurrentSessionAndAwait()) {
                                    showDiscardDialog = false
                                    onBack()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknPrimaryPill(
                        text = "Guardar y salir",
                        onClick = {
                            focusManager.clearFocus(force = true)
                            scope.launch {
                                val result = viewModel.saveSession()
                                if (result.success) {
                                    showDiscardDialog = false
                                    onSavedAndExit()
                                } else {
                                    val action = snackbarHostState.showKpknSnackbar(
                                        result.message,
                                        SnackbarType.DANGER,
                                        actionLabel = "Reintentar",
                                    )
                                    if (action == SnackbarResult.ActionPerformed) {
                                        val retry = viewModel.saveSession()
                                        if (retry.success) {
                                            showDiscardDialog = false
                                            onSavedAndExit()
                                        } else {
                                            snackbarHostState.showKpknSnackbar(retry.message, SnackbarType.DANGER)
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                KpknTextAction(text = "Continuar editando", onClick = { showDiscardDialog = false })
            },
        )
    }
}
