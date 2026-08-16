package com.example.kpkn.screens.sessioneditor

import android.content.Intent
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.Session
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
import com.example.kpkn.screens.sessioneditor.components.SessionHero
import com.example.kpkn.screens.sessioneditor.sessionBackgroundPresets
import com.example.kpkn.screens.sessioneditor.sessionGradients
import com.example.kpkn.screens.sessioneditor.components.SessionHeroCompactOverlay
import com.example.kpkn.screens.sessioneditor.components.SessionContextNavigator
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import com.example.kpkn.screens.sessioneditor.components.sheets.SessionEditorSheets
import com.example.kpkn.screens.sessioneditor.components.sheets.AssistantGlassOverlay
import com.example.kpkn.screens.sessioneditor.components.HeroGlassFab
import com.example.kpkn.screens.sessioneditor.components.HeroTimeFab
import com.example.kpkn.screens.sessioneditor.components.DraggableHeroFabGroup
import com.example.kpkn.screens.sessioneditor.components.CompetitionConfigSheet
import com.example.kpkn.screens.sessioneditor.components.CompetitionSessionEditor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.IconButton
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import com.example.kpkn.ui.components.KpknAlertDialog


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
    openCompetitionConfig: Boolean = false,
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
    val session = uiState.activeVariantSession ?: uiState.session
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAutoExpandExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCompetitionConfigSheet by rememberSaveable { mutableStateOf(openCompetitionConfig) }
    var catalogRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var catalogRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

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
            snackbarHostState.showKpknSnackbar(msg, SnackbarType.SUCCESS)
            viewModel.clearSnackbarMessage()
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
                val retry = CatalogLaunchRequest(
                    origin = if (uiState.pickerTargetExerciseId == null) CatalogLaunchOrigin.SESSION_EDITOR else CatalogLaunchOrigin.REPLACEMENT,
                    selectionMode = if (uiState.pickerTargetExerciseId == null) CatalogSelectionMode.MULTIPLE else CatalogSelectionMode.REPLACEMENT,
                    targetExerciseId = uiState.pickerTargetExerciseId,
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
        val expectedRequest = CatalogLaunchRequest(
            requestId = catalogRequestId ?: result.requestId,
            origin = if (targetExerciseId == null) CatalogLaunchOrigin.SESSION_EDITOR else CatalogLaunchOrigin.REPLACEMENT,
            selectionMode = if (targetExerciseId == null) CatalogSelectionMode.MULTIPLE else CatalogSelectionMode.REPLACEMENT,
            targetExerciseId = targetExerciseId,
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
    val dragController = remember(session?.id) { SessionEditorDragController() }
    val partBounds = dragController.partBounds
    val partContentBounds = dragController.partContentBounds
    val exerciseBounds = dragController.exerciseBounds
    var looseContentBounds by dragController::looseContentBounds
    LaunchedEffect(session?.id) {
        // Cambio de sesión: los bounds pertenecen al layout anterior.
        dragController.clearBounds()
    }
    LaunchedEffect(session?.parts, session?.exercises, uiState.collapsedPartIds) {
        // Tras ediciones, solo se descartan bounds de ítems que ya no existen
        // (sin vaciar todo: onGloballyPositioned no se re-dispara si nada se movió).
        val active = session ?: return@LaunchedEffect
        dragController.pruneBounds(active, uiState.collapsedPartIds)
    }
    var draggingPartId by dragController::draggingPartId
    var draggingPartOffsetY by dragController::draggingPartOffsetY
    var partDropTargetId by dragController::partDropTargetId
    var partDropTargetIndex by dragController::partDropTargetIndex
    var draggingExerciseId by dragController::draggingExerciseId
    var draggingExercisePartId by dragController::draggingExercisePartId
    var draggingExerciseOffset by dragController::draggingExerciseOffset
    var exerciseDropTargetKey by dragController::exerciseDropTargetKey
    var exerciseDropTargetPartId by dragController::exerciseDropTargetPartId
    var exerciseDropTargetIndex by dragController::exerciseDropTargetIndex

    fun beginExerciseDrag(partId: String, exerciseId: String, grab: Offset) =
        dragController.beginExerciseDrag(partId, exerciseId, grab)

    fun endExerciseDrag() {
        val activeSession = session ?: return
        dragController.endExerciseDrag(activeSession) { fromPartId, exerciseId, toPartId, toIndex ->
            viewModel.moveExerciseToPart(fromPartId, exerciseId, toPartId, toIndex)
        }
    }

    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val loadErrorMessage = uiState.loadErrorMessage
            if (loadErrorMessage != null) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        loadErrorMessage,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Puedes reintentar la carga o volver al programa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack) { Text("Volver") }
                        Button(onClick = viewModel::retryLoadSession) { Text("Reintentar") }
                    }
                }
            } else {
                LinearProgressIndicator(modifier = Modifier.width(180.dp))
            }
        }
        return
    }
    val sessionListItems = remember(session.parts, session.exercises, session.supersetGroups, uiState.collapsedPartIds) {
        buildSessionListItems(session, uiState.collapsedPartIds)
    }
    val scrollableListItems = remember(sessionListItems) {
        sessionListItems.drop(1).let { tail ->
            if (tail.lastOrNull() is SessionListItem.AddActions) tail.dropLast(1) else tail
        }
    }
    val allExercisesForUi = remember(session.exercises, session.parts) { session.allExercises() }
    // Sticky compact header ONLY when the expanded hero (item 0) has fully left the viewport.
    // Es un overlay translúcido: NO empuja el contenido (empujarlo/animarlo durante
    // el scroll hacía que el contenido se moviera más rápido que el dedo).
    val showCompactHero by remember(listState, scrollableListItems) {
        derivedStateOf {
            scrollableListItems.isNotEmpty() && listState.firstVisibleItemIndex > 0
        }
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
    val draggedExerciseIds = remember(session, draggingExerciseId, draggingExercisePartId) {
        val activeId = draggingExerciseId ?: return@remember emptySet<String>()
        val sourcePartId = draggingExercisePartId
        val sourceList = when (sourcePartId) {
            "__loose__" -> session.exercises
            null -> emptyList()
            else -> session.parts.firstOrNull { it.id == sourcePartId }?.exercises.orEmpty()
        }
        val groupId = sourceList.firstOrNull { it.id == activeId }?.supersetGroupRefOrLegacyId()
        if (groupId != null) sourceList.filter { it.supersetGroupRefOrLegacyId() == groupId }.map { it.id }.toSet() else setOf(activeId)
    }

    fun projectedShiftFor(
        partId: String,
        index: Int,
        exerciseId: String,
        itemHeight: Float = (dragController.frozenExerciseBounds["$partId|$exerciseId"]
            ?: exerciseBounds["$partId|$exerciseId"])?.height ?: 88f,
    ): Float {
        val activeId = draggingExerciseId ?: return 0f
        val sourcePartId = draggingExercisePartId ?: return 0f
        val keyTargetPart = exerciseDropTargetKey?.substringBefore("|")
        val keyTargetExercise = exerciseDropTargetKey?.substringAfter("|")
        val targetPartId = exerciseDropTargetPartId ?: keyTargetPart ?: return 0f
        val targetList = if (targetPartId == "__loose__") session.exercises
        else session.parts.firstOrNull { it.id == targetPartId }?.exercises.orEmpty()
        val targetIndex = exerciseDropTargetIndex ?: keyTargetExercise?.let { targetExerciseId ->
            targetList.indexOfFirst { it.id == targetExerciseId }.takeIf { it >= 0 }
        } ?: return 0f
        if (exerciseId in draggedExerciseIds) return 0f
        if (partId != targetPartId) return 0f
        val movingCount = draggedExerciseIds.size.coerceAtLeast(1)
        val gap = (itemHeight + 10f) * movingCount
        if (partId != sourcePartId) {
            if (targetIndex < targetList.size) return if (index >= targetIndex) gap else 0f
            return if (index == targetList.size - 1) gap else 0f
        }
        val sourceList = if (partId == "__loose__") session.exercises else session.parts.firstOrNull { it.id == partId }?.exercises.orEmpty()
        val sourceIndex = sourceList.indexOfFirst { it.id == activeId }
        if (sourceIndex < 0 || targetIndex == sourceIndex) return 0f
        return when {
            targetIndex < sourceIndex && index >= targetIndex && index < sourceIndex -> gap
            targetIndex > sourceIndex && index > sourceIndex && index < targetIndex -> -gap
            targetIndex >= sourceList.size && index > sourceIndex -> -gap
            else -> 0f
        }
    }

    LaunchedEffect(openCompetitionConfig, session.id) {
        if (openCompetitionConfig && session.isMeetDay) {
            showCompetitionConfigSheet = true
        }
    }

    LaunchedEffect(session.exercises.isEmpty()) {
        if (session.exercises.isEmpty()) {
            looseContentBounds = null
        }
    }

    BackHandler(enabled = !showDiscardDialog && uiState.sheet == SessionEditorSheet.NONE) {
        viewModel.saveDraftForExit()
        onBack()
    }
    BackHandler(enabled = uiState.sheet == SessionEditorSheet.AUGE) {
        viewModel.closeSheet()
    }

    var editorRootBounds by remember { mutableStateOf<Rect?>(null) }
    var lazyColumnWindowBounds by remember { mutableStateOf<Rect?>(null) }

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
                draggingExerciseId != null -> {
                    val start = dragController.dragStartExerciseRect
                    if (start != null) start.top + dragController.dragStartGrabOffset.y + draggingExerciseOffset.y else null
                }
                draggingPartId != null -> {
                    val start = dragController.dragStartPartRect
                    if (start != null) start.center.y + draggingPartOffsetY else null
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
                    dragController.applyScrollDelta(consumed)
                }
            }
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { editorRootBounds = it.boundsInWindow() },
    ) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // Glass FAB must live OUTSIDE hazeSource (sibling overlay) — same pattern as the roadmap dock.
        ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .onGloballyPositioned { lazyColumnWindowBounds = it.boundsInWindow() },
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + contentBottomPadding),
        ) {
            item {
                SessionHero(
                    session = session,
                    latestBodyMeasurement = uiState.latestBodyMeasurement,
                    onNameChange = viewModel::updateSessionName,
                    onDescriptionChange = viewModel::updateSessionDescription,
                    onMeetBodyweightChange = viewModel::updateSessionMeetBodyweight,
                    onSyncMeetBodyweight = {
                        val result = viewModel.syncMeetBodyweightFromLatestMeasurement()
                        scope.launch {
                            snackbarHostState.showKpknSnackbar(
                                result.message,
                                if (result.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
                            )
                        }
                    },
                    onOpenCoverSheet = { viewModel.openSheet(SessionEditorSheet.BACKGROUND) },
                    onOpenTransfer = { viewModel.openSheet(SessionEditorSheet.TRANSFER) },
                    onOpenHistory = { viewModel.openSheet(SessionEditorSheet.HISTORY) },
                    onOpenRules = { viewModel.openRulesSheet(initialTab = 0) },
                    activeDayOfWeek = uiState.dayOfWeek,
                    weekStartDay = uiState.weekStartDay,
                    onSelectDay = { day -> viewModel.selectRoadmapDay(day) },
                    roadmapContent = {
                        val heroAccent = remember(session.background?.value) {
                            val preset = sessionBackgroundPresets.firstOrNull { it.id == session.background?.value } ?: sessionGradients.first()
                            val a = preset.colors.firstOrNull() ?: Color.White
                            val b = preset.colors.getOrNull(preset.colors.lastIndex / 2) ?: a
                            val c = preset.colors.lastOrNull() ?: b
                            Color((a.red + b.red + c.red)/3f, (a.green + b.green + c.green)/3f, (a.blue + b.blue + c.blue)/3f, 1f)
                        }
                        SessionContextNavigator(
                            sessions = uiState.siblingSessions,
                            selectedSessionId = uiState.selectedSiblingSessionId ?: session.id,
                            onSelectSession = viewModel::requestSessionSwitch,
                            weekStartDay = uiState.weekStartDay,
                            activeDayOfWeek = uiState.dayOfWeek,
                            onSelectDay = { day ->
                                viewModel.selectRoadmapDay(day)
                            },
                            roadmapOptions = uiState.roadmapOptions,
                            onSelectRoadmapOption = viewModel::selectRoadmapOption,
                            competitionKeyDaysInWeek = uiState.competitionKeyDaysInWeek,
                            onCreateSessionForDay = { day ->
                                viewModel.createSessionForDay(day)
                            },
                            onCreateCompetitionSessionForDay = { day ->
                                viewModel.createCompetitionSessionForDay(day)
                            },
                            isSimpleProgram = uiState.isSimpleProgram,
                            hasActiveLoops = uiState.hasActiveLoops,
                            hazeState = hazeState,
                            onSetMainSessionForDay = viewModel::setMainSessionForDay,
                            currentSessionId = session.id,
                            currentDayOfWeek = uiState.dayOfWeek,
                            currentSession = session,
                            activeVariant = uiState.activeVariant,
                            availableVariants = uiState.availableVariants,
                            onCreateVariant = { variant, name -> viewModel.createVariant(variant, name) },
                            onDeleteVariant = { viewModel.deleteVariant(it) },
                            onSwitchVariant = {
                                viewModel.commitActiveVariantChanges()
                                viewModel.switchVariant(it)
                            },
                            embedded = true,
                            accentColor = heroAccent,
                        )
                    },
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
                    draggingExerciseOffset = draggingExerciseOffset,
                    exerciseDropTargetKey = exerciseDropTargetKey,
                    exerciseDropTargetPartId = exerciseDropTargetPartId,
                    exerciseDropTargetIndex = exerciseDropTargetIndex,
                    draggingPartId = draggingPartId,
                    draggingPartOffsetY = draggingPartOffsetY,
                    partDropTargetId = partDropTargetId,
                    pendingAutoExpandExerciseId = pendingAutoExpandExerciseId,
                    onPendingAutoExpandHandled = { exerciseId ->
                        if (pendingAutoExpandExerciseId == exerciseId) pendingAutoExpandExerciseId = null
                    },
                    onOpenCompetitionConfig = { showCompetitionConfigSheet = true },
                    onLooseBoundsReport = { rect ->
                        if (!dragController.isExerciseDragging) {
                            looseContentBounds = mergeBounds(looseContentBounds, rect)
                        } else {
                            val currentFrozen = dragController.frozenLooseContentBounds
                            val shouldExpand = currentFrozen == null ||
                                rect.top < currentFrozen.top - 1f ||
                                rect.bottom > currentFrozen.bottom + 1f ||
                                rect.left < currentFrozen.left - 1f ||
                                rect.right > currentFrozen.right + 1f
                            if (shouldExpand) {
                                looseContentBounds = mergeBounds(looseContentBounds, rect)
                                dragController.frozenLooseContentBounds = when (currentFrozen) {
                                    null -> rect
                                    else -> mergeBounds(currentFrozen, rect)
                                }
                            }
                        }
                    },
                    onPartContentBoundsReport = { partId, rect ->
                        if (!dragController.isExerciseDragging) {
                            partContentBounds[partId] = mergeBounds(partContentBounds[partId], rect)
                        } else {
                            if (partId !in dragController.frozenPartContentBounds) {
                                partContentBounds[partId] = rect
                                dragController.frozenPartContentBounds =
                                    dragController.frozenPartContentBounds + (partId to rect)
                            } else {
                                val existing = dragController.frozenPartContentBounds[partId]
                                if (existing != null &&
                                    (rect.top < existing.top - 1f || rect.bottom > existing.bottom + 1f)
                                ) {
                                    val expanded = mergeBounds(existing, rect)
                                    dragController.frozenPartContentBounds =
                                        dragController.frozenPartContentBounds + (partId to expanded)
                                    partContentBounds[partId] = mergeBounds(partContentBounds[partId], rect)
                                }
                            }
                        }
                    },
                    beginExerciseDrag = ::beginExerciseDrag,
                    updateExerciseDrag = ::updateExerciseDrag,
                    endExerciseDrag = ::endExerciseDrag,
                    projectedShiftFor = ::projectedShiftFor,
                    viewModel = viewModel,
                )
            }

            if (!session.isMeetDay) item {
                val isEmptySession = session.exercises.isEmpty() &&
                    session.parts.none { !it.isUncategorizedPart() }
                Column(
                    modifier = Modifier
                        .padding(horizontal = editorSpacing.screenPadding, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isEmptySession) {
                        SessionEditorEmptyState(
                            onAddExercise = viewModel::openPickerForUncategorized,
                            onAddGroup = viewModel::addPart,
                            onAddCardio = { viewModel.openCardioPicker(null) },
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = viewModel::openPickerForUncategorized,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Text("Añadir ejercicio", fontWeight = FontWeight.Bold)
                                }
                                FilledTonalButton(
                                    onClick = viewModel::addPart,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Text("Nuevo grupo", fontWeight = FontWeight.Bold)
                                }
                            }
                            FilledTonalButton(
                                onClick = { viewModel.openCardioPicker(null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text("Añadir cardio", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        }
        } // hazeSource — content behind glass overlays (dock + assistant FAB)

        AnimatedVisibility(
            visible = showCompactHero,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(270f),
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
        ) {
            SessionHeroCompactOverlay(
                session = session,
                hazeState = hazeState,
                onOpenCoverSheet = { viewModel.openSheet(SessionEditorSheet.BACKGROUND) },
                onOpenTransfer = { viewModel.openSheet(SessionEditorSheet.TRANSFER) },
                onOpenHistory = { viewModel.openSheet(SessionEditorSheet.HISTORY) },
                onOpenRules = { viewModel.openRulesSheet(initialTab = 0) },
            )
        }

        // Assistant FAB + Time FAB: sibling OVER hazeSource (never nested inside
        // Scaffold FAB slot). Draggable group, moves together across the screen.
        val showTimeFab =
            session.targetDurationMinutes != null ||
                allExercisesForUi.isNotEmpty() ||
                uiState.estimatedDurationMinutes > 0
        val estimated = uiState.sessionTimeBreakdown?.totalMinutes
            ?: uiState.estimatedDurationMinutes
        val navBarBottomPx = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val screenWidthPx = editorRootBounds?.width?.roundToInt() ?: 0
        val screenHeightPx = editorRootBounds?.height?.roundToInt() ?: 0
        DraggableHeroFabGroup(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            navBarBottomPx = with(density) { navBarBottomPx.toPx() }.roundToInt(),
            fabBottomPadding = fabBottomPadding,
            modifier = Modifier.zIndex(260f),
            assistantFab = { fabModifier ->
                HeroGlassFab(
                    summary = uiState.augeSummary,
                    modifier = fabModifier,
                    hazeState = hazeState,
                    onClick = { viewModel.openSheet(SessionEditorSheet.AUGE) },
                )
            },
            timeFab = if (showTimeFab) {
                { fabModifier ->
                    HeroTimeFab(
                        estimatedMinutes = estimated,
                        limitMinutes = session.targetDurationMinutes,
                        hasSuggestions = session.targetDurationMinutes != null &&
                            estimated > (session.targetDurationMinutes ?: Int.MAX_VALUE),
                        modifier = fabModifier,
                        hazeState = hazeState,
                        onClick = { viewModel.openRulesSheet(initialTab = 1) },
                    )
                }
            } else {
                null
            },
        )

        val previewExercise = draggingExerciseId?.let { activeId -> allExercisesForUi.firstOrNull { it.id == activeId } }
        val previewPartId = draggingExercisePartId
        val previewRect = if (previewPartId != null && draggingExerciseId != null) {
            dragController.dragStartExerciseRect ?: exerciseBounds["$previewPartId|$draggingExerciseId"]
        } else {
            null
        }
        if (previewExercise != null && previewRect != null) {
            DragLiftPreview(
                exercise = previewExercise,
                rect = previewRect,
                offset = draggingExerciseOffset,
                rootBounds = editorRootBounds,
                modifier = Modifier.zIndex(500f),
            )
        }
        val draggingPart = draggingPartId?.let { id -> groupedParts.firstOrNull { it.id == id } }
        val partPreviewRect = draggingPartId?.let { dragController.dragStartPartRect ?: partBounds[it] }
        if (draggingPart != null && partPreviewRect != null) {
            DragPartLiftPreview(
                partName = draggingPart.name,
                rect = partPreviewRect,
                offsetY = draggingPartOffsetY,
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
                            "Defaults aplicados a $target (${outcome.exercisesChanged} $noun)",
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
            val hasPendingSwitch = uiState.pendingSessionSwitchId != null
            val saveResult = viewModel.saveSession(saveScope)
            if (saveResult.success && !hasPendingSwitch) {
                // Exit immediately after a successful save; waiting on snackbar blocks navigation.
                onSavedAndExit()
            } else {
                scope.launch {
                    snackbarHostState.showKpknSnackbar(
                        saveResult.message,
                        if (saveResult.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
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

    if (showDiscardDialog) {
        KpknAlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Salir del editor", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tienes cambios sin guardar.")
                    OutlinedButton(
                        onClick = {
                            showDiscardDialog = false
                            viewModel.discardDraftForCurrentSession()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cerrar sin guardar")
                    }
                    Button(
                        onClick = {
                            val result = viewModel.saveSession()
                            scope.launch {
                                if (result.success) {
                                    showDiscardDialog = false
                                    onSavedAndExit()
                                } else {
                                    snackbarHostState.showKpknSnackbar(result.message, SnackbarType.DANGER)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Guardar y salir")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Continuar editando") }
            },
        )
    }

    if (showCompetitionConfigSheet && session.isMeetDay) {
        CompetitionConfigSheet(
            session = session,
            onDismiss = { showCompetitionConfigSheet = false },
            onUpdateSession = { updater: (Session) -> Session -> viewModel.updateCurrentSession(updater) },
        )
    }
}
