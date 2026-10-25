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

import com.example.kpkn.screens.sessioneditor.components.SessionExerciseDetailSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SessionEditorSheets
import com.example.kpkn.screens.sessioneditor.components.sheets.AssistantGlassOverlay
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBlockNotice
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBottomBar
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBottomBarHeight
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameHeader
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameTopBar
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameWeekSheet
import com.example.kpkn.screens.sessioneditor.frame.adjacentWeekDay
import com.example.kpkn.screens.sessioneditor.frame.frameDayIndex
import com.example.kpkn.screens.sessioneditor.frame.frameDayLabel
import com.example.kpkn.screens.sessioneditor.frame.frameDayWeekLabel
import com.example.kpkn.screens.sessioneditor.frame.frameWeekNumber
import com.example.kpkn.screens.sessioneditor.frame.isRedundantWhileSaveBlocked
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorLoadErrorState
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorLoadingState
import com.example.kpkn.screens.sessioneditor.frame.sessionFrameSummaryChips
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDropHintBanner
import com.example.kpkn.screens.sessioneditor.frame.frameVariantName
import com.example.kpkn.screens.sessioneditor.frame.frameWeekOrder
import com.example.kpkn.screens.sessioneditor.frame.neighbourSessionDay
import com.example.kpkn.screens.sessioneditor.frame.nextFreeVariant
import com.example.kpkn.screens.sessioneditor.frame.primarySessionOnDay
import com.example.kpkn.screens.sessioneditor.frame.sessionFrameSummary
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlin.math.roundToInt
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.screens.workout.RepairBenchmarkTrace


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
    // Aviso fijo del guardado automático cuando el cortafuegos o la validación lo bloquean (ER1a §5).
    val autoSaveBlock by viewModel.autoSaveBlock.collectAsStateWithLifecycle()
    val archivedUserTemplates = remember(userTemplates) { userTemplates.filter { it.isArchived } }
    val session = uiState.activeVariantSession ?: uiState.session
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // La hoja «Semana» (el navegador de días y sesiones) se abre desde el texto de la píldora de la fila superior.
    var showWeekSheet by rememberSaveable { mutableStateOf(false) }
    var pendingAutoExpandExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var catalogRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    // La ficha del catálogo de un ejercicio de la sesión se abre dentro del editor (ER1f): «Ficha» de las acciones rápidas.
    var editorDetailExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun openAssistantSheet() {
        // Dismiss the group-name BasicTextField before the overlay is composed;
        // otherwise Android can keep its yellow insertion handle visible above
        // the sheet even though the field is visually covered.
        clearSessionEditorAssistantFocus(focusManager, keyboardController)
        viewModel.openSheet(SessionEditorSheet.ASSISTANT)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Al pasar a segundo plano, lo pendiente va también al programa (ER1a §5).
                    viewModel.flushProgramAutoSaveInBackground()
                    viewModel.saveDraftForExit()
                }
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

    // Guardado automático (ER1a §5): cada cambio de la sesión o de sus reglas, y la sesión recién cargada con cambios
    // pendientes, programan el guardado en el programa al quedarse quieta la mano (1,2 s).
    LaunchedEffect(uiState.session, uiState.ruleDefaults, uiState.partRuleDefaults, uiState.ruleLimits, uiState.pendingTransferToDays) {
        if (viewModel.currentUiState.hasUnsavedChanges) viewModel.scheduleProgramAutoSave()
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
            // ED-A · A6: un solo aviso por estado. Si el aviso fijo de «guardado bloqueado» ya cuenta lo mismo, la snackbar no lo repite.
            if (isRedundantWhileSaveBlocked(msg, autoSaveBlock?.message)) {
                viewModel.clearSnackbarMessage()
                return@LaunchedEffect
            }
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
    // La lista deja sitio para la isla inferior y para la barra de gestos, para que no tape el último elemento.
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = navigationBarBottom + SessionEditorFrameBottomBarHeight + 24.dp
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
            catalogRequestInFlight = true
            val request = uiState.catalogLaunchRequest()
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
                val retry = uiState.catalogLaunchRequest()
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
        val expectedRequest = uiState.catalogLaunchRequest(catalogRequestId ?: result.requestId)
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
        // ED-A · A3: la cabecera de una superserie mueve el bloque entero; un ejercicio que no es de ninguna superserie se arrastra solo,
        // para poder soltarlo sobre una ya formada.
        val container = if (partId == SessionEditorDragController.LOOSE_PART_ID) {
            session?.exercises
        } else {
            session?.parts?.firstOrNull { it.id == partId }?.exercises
        }
        val groupId = container?.firstOrNull { it.id == exerciseId }?.supersetGroupRefOrLegacyId()
        val isSupersetBlock = groupId != null && (container?.count { it.supersetGroupRefOrLegacyId() == groupId } ?: 0) >= 2
        dragController.beginExerciseDrag(
            partId = partId,
            exerciseId = exerciseId,
            grabOffset = Offset(24f, 24f),
            liveBounds = currentListGeometry().takeIf { it.isNotEmpty() },
            pointerStartWindow = pointerWindow,
            session = session,
            collapsedPartIds = uiState.collapsedPartIds,
            dragScope = if (isSupersetBlock) ExerciseDragScope.BLOCK else ExerciseDragScope.INDIVIDUAL,
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
        // ED-A · A6: una sola franja por estado (cargando, error), sin otro aviso encima.
        val loadErrorMessage = uiState.loadErrorMessage
        if (loadErrorMessage != null) {
            SessionEditorLoadErrorState(
                message = loadErrorMessage,
                onBack = onBack,
                onRetry = viewModel::retryLoadSession,
            )
        } else {
            SessionEditorLoadingState()
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
        uiState.cardioSpaceCommitted,
        uiState.cardioSpacePlacement,
    ) {
        buildSessionListItems(
            session = session,
            collapsedPartIds = uiState.collapsedPartIds,
            showStrengthDivider = uiState.strengthSpaceCommitted,
            cardioSpaceCommitted = uiState.cardioSpaceCommitted,
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
    // ED-A · A3: qué pasaría si se soltara ahora el ejercicio que se arrastra; lo pinta el aviso de arriba y las señales de la lista.
    val dropPlan = if (draggingExerciseId != null) {
        planExerciseDrop(
            session = session,
            sourcePartId = draggingExercisePartId,
            exerciseId = draggingExerciseId,
            scope = dragController.draggingExerciseScope,
            targetPartId = exerciseDropTargetPartId,
            targetIndex = exerciseDropTargetIndex,
            targetGroupId = dragUi.exerciseDropTargetGroupId,
            outOfRange = exerciseDropOutOfRange,
        )
    } else {
        null
    }

    // Auto-scroll al ejercicio recién añadido (índice = Hero + offset en scrollable)
    LaunchedEffect(pendingAutoExpandExerciseId) {
        val expandId = pendingAutoExpandExerciseId ?: return@LaunchedEffect
        val targetIndex = lazyColumnIndexForExercise(scrollableListItems, expandId)
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

    // Salir (la flecha o el Atrás del sistema): guarda lo pendiente en el programa y, si lo bloquea, el borrador local. Sale igual:
    // lo escrito queda en el borrador y el aviso se ve al volver. Sin diálogo de «Salir del editor» (ER1a §5).
    fun exitEditor() {
        focusManager.clearFocus(force = true)
        scope.launch {
            viewModel.flushProgramAutoSaveAndAwait()
            if (viewModel.saveDraftForExitAndAwait()) onBack()
        }
    }
    BackHandler(enabled = uiState.sheet == SessionEditorSheet.NONE && !showWeekSheet) { exitEditor() }
    BackHandler(enabled = uiState.sheet == SessionEditorSheet.ASSISTANT) {
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
            .onGloballyPositioned { editorRootBounds = it.boundsInWindow() }
            .drawWithContent {
                drawContent()
                RepairBenchmarkTrace.editorReadyDrawn(
                    requestId = repairBenchmarkEditorRequestId,
                    ready = session != null && uiState.loadErrorMessage == null,
                )
            },
    ) {
        // ER1a: fila superior fija, cabecera sin portada, lista, y barra inferior Añadir · Asistente · Reglas (sin FAB arrastrable).
        val currentDay = uiState.dayOfWeek ?: session.dayOfWeek
        val orderedWeekDays = remember(uiState.weekStartDay) { frameWeekOrder(uiState.weekStartDay) }
        val sessionDays = remember(uiState.siblingSessions) { uiState.siblingSessions.mapNotNull { it.dayOfWeek }.toSet() }
        val previousDay = neighbourSessionDay(orderedWeekDays, currentDay, sessionDays, step = -1)
        val nextDay = neighbourSessionDay(orderedWeekDays, currentDay, sessionDays, step = 1)
        // La duración sale de la misma fuente que el FAB de tiempo de antes (SE9/SE12): sin ejercicios, no hay minutos.
        val timeMinutes = sessionTimeFabMinutes(
            hasExercises = allExercisesForUi.isNotEmpty(),
            breakdownMinutes = uiState.sessionTimeBreakdown?.totalMinutes,
            fallbackMinutes = uiState.estimatedDurationMinutes,
        )
        // Una sesión solo de cardio no cuenta series (ED-A · A2b): «2 ejercicios · ≈ 42 min».
        val summaryChips = sessionFrameSummaryChips(
            exercises = allExercisesForUi.size,
            sets = session.workingSetCount(),
            minutes = timeMinutes,
            showSets = !(allExercisesForUi.isNotEmpty() && allExercisesForUi.all { it.isCardio }),
        )

        // Cambio de día: el contenido nuevo entra deslizándose desde el lado del día (220 ms); con movimiento reducido, un fundido.
        val slideX = remember { Animatable(0f) }
        val slideAlpha = remember { Animatable(1f) }
        val reducedMotion = wizardReducedMotion()
        var listWidthPx by remember { mutableIntStateOf(0) }
        var shownSessionId by remember { mutableStateOf<String?>(null) }
        var shownDay by remember { mutableStateOf<Int?>(null) }
        var slideForward by remember { mutableStateOf(true) }
        LaunchedEffect(session.id) {
            val previousId = shownSessionId
            val previousDayValue = shownDay
            shownSessionId = session.id
            shownDay = currentDay
            if (previousId == null || previousId == session.id) return@LaunchedEffect
            val oldIndex = frameDayIndex(orderedWeekDays, previousDayValue)
            val newIndex = frameDayIndex(orderedWeekDays, currentDay)
            slideForward = oldIndex < 0 || newIndex < 0 || newIndex >= oldIndex
            if (reducedMotion) {
                slideAlpha.snapTo(0f)
                slideAlpha.animateTo(1f, tween(durationMillis = 120))
            } else {
                slideX.snapTo(if (slideForward) listWidthPx.toFloat() else -listWidthPx.toFloat())
                slideX.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
            }
        }

        // Las flechas y la píldora pasan por el mismo camino que el gesto del original (requestSessionSwitch).
        fun goToSessionDay(day: Int?) {
            val target = day?.let { primarySessionOnDay(uiState.siblingSessions, it) } ?: return
            viewModel.requestSessionSwitch(target.id)
        }
        // El gesto de la cabecera va al día contiguo, como el original (selectRoadmapDay).
        fun swipeToAdjacentDay(step: Int): Boolean {
            val target = adjacentWeekDay(orderedWeekDays, currentDay, step) ?: return false
            viewModel.selectRoadmapDay(target)
            return true
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SessionEditorFrameTopBar(
                dayLabel = frameDayWeekLabel(
                    day = currentDay,
                    // Solo se nombra la semana si el programa las distingue (varias semanas, o una simple con bucles).
                    weekNumber = if (uiState.roadmapOptions.size > 1 || (uiState.isSimpleProgram && uiState.hasActiveLoops)) {
                        frameWeekNumber(uiState.roadmapOptions, uiState.weekId)
                    } else {
                        null
                    },
                    sessionName = session.name,
                ),
                forward = slideForward,
                canGoPrevious = previousDay != null,
                canGoNext = nextDay != null,
                onBack = ::exitEditor,
                onPrevious = { goToSessionDay(previousDay) },
                onNext = { goToSessionDay(nextDay) },
                onOpenWeek = { showWeekSheet = true },
                onTransfer = { viewModel.openSheet(SessionEditorSheet.TRANSFER) },
                onHistory = { viewModel.openSheet(SessionEditorSheet.HISTORY) },
            )
            // Aviso fijo: el guardado automático lo ha bloqueado (cortafuegos o validación). Lo escrito sigue en pantalla.
            autoSaveBlock?.let { block ->
                SessionEditorFrameBlockNotice(
                    message = block.message,
                    onOpenExercise = block.exerciseId?.let { exerciseId -> { pendingAutoExpandExerciseId = exerciseId } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .onSizeChanged { listWidthPx = it.width }
                        .graphicsLayer {
                            translationX = slideX.value
                            alpha = slideAlpha.value
                        }
                        .hazeSource(state = hazeState)
                        .onGloballyPositioned { lazyColumnWindowBounds = it.boundsInWindow() },
                    contentPadding = PaddingValues(bottom = contentBottomPadding),
                ) {
                    // Índice 0: la cabecera. La lista va después, así que los índices de auto-scroll no cambian.
                    item {
                        SessionEditorFrameHeader(
                            sessionId = session.id,
                            name = session.name,
                            description = session.description.orEmpty(),
                            protocolLabel = uiState.protocolLabel,
                            summaryChips = summaryChips,
                            onNameChange = viewModel::updateSessionName,
                            onDescriptionChange = viewModel::updateSessionDescription,
                            onDaySwipe = ::swipeToAdjacentDay,
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
                    exerciseDropTargetGroupId = dragUi.exerciseDropTargetGroupId,
                    draggingExercisePartId = draggingExercisePartId,
                    dropPlan = dropPlan,
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
            }
        }

        // Barra inferior fija: Añadir · Asistente · Reglas (ER1a §2.3). Va sobre la lista, con el vidrio que lee la lista.
        SessionEditorFrameBottomBar(
            hazeState = hazeState,
            suggestionCount = uiState.assistantReport?.ajustes?.size ?: 0,
            estimatedMinutes = timeMinutes,
            limitMinutes = session.targetDurationMinutes,
            onAdd = { viewModel.openPickerForUncategorized() },
            onAssistant = ::openAssistantSheet,
            // ED-A · A1: el anillo del tiempo abre la pestaña de tiempo; «Reglas», la de reglas.
            onRules = { viewModel.openRulesSheet(initialTab = 0) },
            onTime = { viewModel.openRulesSheet(initialTab = 1) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .zIndex(260f),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = SessionEditorFrameBottomBarHeight + 24.dp),
        ) { KpknSnackbar(it) }

        if (showWeekSheet) {
            SessionEditorFrameWeekSheet(
                orderedDays = orderedWeekDays,
                sessions = uiState.siblingSessions,
                currentSessionId = session.id,
                currentDayOfWeek = currentDay,
                competitionKeyDaysInWeek = uiState.competitionKeyDaysInWeek,
                roadmapOptions = uiState.roadmapOptions,
                currentWeekId = uiState.weekId,
                isSimpleProgram = uiState.isSimpleProgram,
                hasActiveLoops = uiState.hasActiveLoops,
                activeVariant = uiState.activeVariant,
                availableVariants = uiState.availableVariants,
                // B/C/D names live on the base session even while the editor shows one derivative.
                variantName = { variant -> frameVariantName(uiState.session ?: session, variant) },
                nextVariant = nextFreeVariant(uiState.availableVariants),
                defaultVariantName = "${session.name.ifBlank { "Sesión" }} – Rápida",
                onSelectSession = { id -> viewModel.requestSessionSwitch(id) },
                onSetMainSession = { id ->
                    // Lo pendiente se guarda antes: cambiar la principal recarga la sesión.
                    scope.launch {
                        viewModel.flushProgramAutoSaveAndAwait()
                        viewModel.setMainSessionForDay(id)
                    }
                },
                onCreateSessionForDay = { day -> viewModel.createSessionForDay(day) },
                onSelectRoadmapOption = { option -> viewModel.selectRoadmapOption(option) },
                onSelectVariant = { variant ->
                    viewModel.commitActiveVariantChanges()
                    viewModel.switchVariant(variant)
                },
                onCreateVariant = { variant, name -> viewModel.createVariant(variant, name) },
                onDeleteActiveVariant = { viewModel.deleteVariant(uiState.activeVariant) },
                onDismiss = { showWeekSheet = false },
            )
        }

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
        SessionEditorDropHintBanner(
            plan = dropPlan?.takeIf { !exerciseDropOutOfRange },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 60.dp)
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

        if (uiState.sheet == SessionEditorSheet.ASSISTANT) {
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
            // «Se copia ahora»: al confirmar, la copia va al programa en el acto (no «al guardar»).
            val staged = viewModel.cloneCurrentSessionToTargets(targetKeys, selectedExerciseIds, applyMode)
            scope.launch {
                if (!staged.success) {
                    snackbarHostState.showKpknSnackbar(staged.message, SnackbarType.DANGER)
                    return@launch
                }
                val saved = viewModel.flushProgramAutoSaveAndAwait()
                snackbarHostState.showKpknSnackbar(
                    if (saved) "Copia hecha en ${targetKeys.size} día(s)." else "La copia no se guardó. Revisa el aviso de arriba.",
                    if (saved) SnackbarType.SUCCESS else SnackbarType.DANGER,
                )
            }
        },
        onImportFromSource = { sourceSessionId, selectedExerciseIds, applyMode ->
            val result = viewModel.importFromSourceSession(sourceSessionId, selectedExerciseIds, applyMode)
            scope.launch {
                if (result.success) viewModel.flushProgramAutoSaveAndAwait()
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
        onAddMobilityExercise = viewModel::addMobilityToTarget,
        onRemoveMobilityExercise = { info ->
            // D9: el catálogo puede apuntar a un ejercicio o al grupo de movilidad de la sesión.
            val partId = uiState.quickActionsPartId
            val exerciseId = uiState.quickActionsExerciseId
            val targetSeries = when {
                exerciseId != null -> session.allExercises()
                    .firstOrNull { it.id == exerciseId }
                    ?.mobilitySeries
                partId != null -> session.parts.firstOrNull { it.id == partId }?.mobilitySeries
                else -> null
            }.orEmpty()
            targetSeries.firstOrNull { it.catalogIdentityKey() == info.id }?.let { existing ->
                viewModel.removeMobilityTarget(partId, exerciseId, existing.id)
            }
        },
        onAddCardio = viewModel::addCardioToPart,
        onQuickActionDelete = viewModel::triggerQuickActionDelete,
        onQuickActionDuplicate = viewModel::duplicateQuickActionExercise,
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
        // ER1f: la ficha se abre aquí dentro; el parámetro externo `onOpenExerciseDetail` ya no recibe la acción.
        onOpenExerciseDetail = { id -> editorDetailExerciseId = id },
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

    // La ficha de un ejercicio de la sesión (ER1f): el mismo cuerpo que la del catálogo, sin «Añadir/Quitar».
    editorDetailExerciseId?.let { detailId ->
        val detailExercise = (uiState.activeVariantSession ?: uiState.session)
            ?.allExercises()
            ?.firstOrNull { it.id == detailId }
        if (detailExercise != null) {
            SessionExerciseDetailSheet(
                exercise = detailExercise,
                onDismiss = { editorDetailExerciseId = null },
            )
        }
    }
}
