package com.example.kpkn.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.screens.onboarding.design.KpknModule
import com.example.kpkn.screens.onboarding.design.LocalWizardPageScroll
import com.example.kpkn.screens.onboarding.design.ModuleCompleteOverlay
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardDarkSystemBars
import com.example.kpkn.screens.onboarding.design.WizardDock
import com.example.kpkn.screens.onboarding.design.WizardDockClearance
import com.example.kpkn.screens.onboarding.design.WizardHeaderBlockHeight
import com.example.kpkn.screens.onboarding.design.WizardMotion
import com.example.kpkn.screens.onboarding.design.WizardPageHeader
import com.example.kpkn.screens.onboarding.design.WizardPageItem
import com.example.kpkn.screens.onboarding.design.WizardPageMetrics
import com.example.kpkn.screens.onboarding.design.WizardPageMode
import com.example.kpkn.screens.onboarding.design.WizardPageScroll
import com.example.kpkn.screens.onboarding.design.WizardProgressSegment
import com.example.kpkn.screens.onboarding.design.WizardScrollLock
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTopScrim
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Host del wizard tradicional que sustituye a WizChat.
 *
 * **Una sola página larga** con secciones de cristal, no una pantalla por paso: lo ya
 * confirmado se pliega en una fila-resumen (tocarla edita ese paso), el paso actual está
 * desplegado y el siguiente solo asoma, inerte. El check confirma, y es lo único que genera el
 * paso siguiente: la página se desliza hasta él (no hay página nueva) y el scroll del usuario no
 * puede adelantarse a lo que el check no ha generado ([WizardScrollLock]).
 *
 * Conserva intactas las piezas que el plan manda preservar: inicialización del borrador,
 * **atrás sin pérdida** (nunca descarta), diálogos de salida diferenciados y activación conjunta
 * al final.
 *
 * El paso actual es autoridad del estado ([SetupWizardState.currentStep]); la flecha atrás de la
 * cabecera y el botón de sistema vuelven un paso cuando hay historial y abren la salida explícita
 * en el primer paso. La navegación por salida/descarte solo avanza cuando la operación de
 * persistencia devuelve true.
 */
@Composable
fun SetupWizardScreen(
    mode: SetupWizardMode,
    draftId: String? = null,
    /** Plan de la biblioteca que la persona eligió («Configurar este plan», E-18); null = ninguno. */
    preselectedPlanId: String? = null,
    /** Abre un concepto de «Conceptos clave» desde las hojas «Cómo funciona»; null = sin enlace (sin navegación). */
    onOpenConcept: ((String) -> Unit)? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SetupWizardViewModel = viewModel(),
    /** La pantalla de arranque (sin nada completado) antes de la primera pregunta de un borrador nuevo. */
    showIntro: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var navigatedAfterCommit by remember { mutableStateOf(false) }

    // La clave incluye el ViewModel: si las tests recrean el VM, la
    // inicialización se vuelve a ejecutar en lugar de quedar en un no-op.
    LaunchedEffect(mode, draftId, viewModel) {
        viewModel.initialize(mode, draftId = draftId, preselectedPlanId = preselectedPlanId)
    }

    // Atrás nunca descarta ni borra respuestas: retrocede un paso cuando existe
    // historial y abre la salida explícita solo en el primer paso.
    fun leaveNow() {
        scope.launch {
            viewModel.leaveImmediately()
            onCancel()
        }
    }

    BackHandler {
        when {
            state.isSavingAndExiting -> Unit
            state.dialog == SetupWizardDialog.DISCARD -> viewModel.keepConfiguring()
            viewModel.canGoBack() -> viewModel.goBack()
            else -> leaveNow()
        }
    }

    if (state.dialog == SetupWizardDialog.DISCARD) {
        SetupWizardExitDialogs(state = state, viewModel = viewModel, onLeftWizard = onCancel)
    }

    CompositionLocalProvider(LocalOpenConcept provides onOpenConcept) {
        when (state.machineState) {
            WizChatMachineState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(WizardColors.background),
            )

            WizChatMachineState.UnsupportedDraft -> WizardStatusScreen(
                title = "No pude abrir tu configuración",
                body = state.errors["draft"] ?: "Esta configuración no se puede continuar. Puedes volver y empezar de nuevo.",
                secondaryLabel = "Volver",
                onSecondary = onCancel,
            )

            WizChatMachineState.RecoverableError -> WizardStatusScreen(
                title = "No pude abrir tu configuración",
                // Mensaje honesto del fallo real, no un texto genérico.
                body = state.lastFailure
                    ?: state.errors["initialize"]
                    ?: "Lo que llevas sigue guardado. Puedes reintentarlo.",
                secondaryLabel = "Reintentar",
                onSecondary = { viewModel.retryFailedOperation(SetupRetryOperation.LOAD) },
                tertiaryLabel = "Volver",
                onTertiary = onCancel,
            )

            else -> WizardLongPage(
                state = state,
                viewModel = viewModel,
                showIntro = showIntro,
                onLeave = { leaveNow() },
                onActivate = {
                    scope.launch {
                        if (viewModel.commit() != null && !navigatedAfterCommit) {
                            navigatedAfterCommit = true
                            onDone()
                        }
                    }
                },
            )
        }
    }
}

private val EMPTY_SUMMARY = SetupStepSummary(label = "", value = "")

/** Lo que se puede haber movido la página desde donde la dejó el anfitrión y aún contar como «no tocada» (px). */
private const val PIN_TOLERANCE_PX = 3

/** Lo que tarda la página en subir lo justo para que el final del paso quede sobre el botón de confirmar. */
private const val OPEN_EXTRA_MILLIS = 260

/**
 * La página larga: fondo ambiental, secciones que se deslizan bajo una cabecera y un botón de
 * cristal fijos, y el aviso flotante de errores.
 *
 * El destino del deslizado sale de una fórmula cerrada ([WizardPageMetrics]) porque todo lo que
 * hay antes del paso activo son filas-resumen de alto fijo: no se mide ninguna posición.
 */
@Composable
private fun WizardLongPage(
    state: SetupWizardState,
    viewModel: SetupWizardViewModel,
    showIntro: Boolean,
    onLeave: () -> Unit,
    onActivate: () -> Unit,
) {
    WizardDarkSystemBars()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = wizardReducedMotion()

    val step = state.currentStep
    val route = SetupStepGraph.stepIds(state.draft.stepContext())
    val pages = wizardPresentationSteps(route)
    val currentPage = wizardCurrentPage(step, route)
    val currentIndex = pages.indexOf(currentPage).coerceAtLeast(0)
    // Un hito es el overlay de «bloque completado» sobre la última página del bloque, ya confirmada.
    val milestoneModule = if (SetupStepGraph.isMilestone(step)) SetupStepGraph.blockOf(step).toKpknModule() else null

    val ageYears = state.draft.ageYears
    val aliasReady = currentPage != SetupStepId.NAME ||
        (state.draft.name.isNotBlank() && ageYears != null && ageYears in 13..100)
    val measuresReady = step != SetupStepId.HEIGHT ||
        (state.draft.heightCm != null && state.draft.weightKg != null)
    // El plan de alimentación con calorías peligrosamente bajas o una pérdida extrema no se confirma: el aviso del
    // panel ya dice por qué y el check se apaga hasta que el plan sea razonable (el ViewModel lo vuelve a exigir).
    val nutritionPlanBlocked = nutritionResultGate(state, step).isNotEmpty()
    val checkEnabled = state.canConfirmStep && aliasReady && measuresReady && !state.isSubmittingAnswer && !nutritionPlanBlocked
    val ctaLabel = if (step == SetupStepId.REVIEW_ACTIVATE) "Activar y entrar a KPKN" else "Continuar"

    // ── Geometría ────────────────────────────────────────────────────────────
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val headerBottomPx = statusTopPx + with(density) { WizardHeaderBlockHeight.roundToPx() }
    // Las filas-resumen van seguidas, sin hueco entre ellas: la página es una sola superficie.
    val gapPx = 0
    val chipPx = with(density) { WizardSpacing.summaryRowHeightFor(density.fontScale).roundToPx() }
    val peekPx = with(density) { WizardSpacing.peekHeight.roundToPx() }
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val clearancePx = with(density) { WizardDockClearance.roundToPx() } + navBottomPx

    val scroll = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val heights = remember { mutableStateMapOf<SetupStepId, Int>() }
    val summaries = remember(state.draft.draftId) { HashMap<SetupStepId, SetupStepSummary>() }
    val staleSummaries = remember(state.draft.draftId) { HashSet<SetupStepId>() }
    val targetPx = WizardPageMetrics.target(currentIndex, chipPx, gapPx)
    val lockMaxPx = WizardPageMetrics.lockMax(
        index = currentIndex,
        headerBottomPx = headerBottomPx,
        chipPx = chipPx,
        gapPx = gapPx,
        // En la última página no hay nada que asome: no se reserva hueco para el siguiente.
        peekPx = if (currentIndex + 1 < pages.size) peekPx else 0,
        clearancePx = clearancePx,
        viewportPx = viewportPx,
        activeHeightPx = heights[currentPage] ?: 0,
    )
    // El paso siguiente asoma hasta el borde inferior de la pantalla, no a media altura.
    val peekWindowPx = WizardPageMetrics.peekWindow(
        viewportPx = viewportPx,
        focusLinePx = WizardPageMetrics.focusLine(currentIndex, headerBottomPx, chipPx, gapPx),
        activeHeightPx = heights[currentPage] ?: 0,
        minPeekPx = peekPx,
    )
    val lockMax by rememberUpdatedState(lockMaxPx)
    val lock = remember(scroll) { WizardScrollLock(scroll) { lockMax } }
    // Quien arrastra algo hasta el borde visible (el tablero de la semana) puede pedirle a la página que se desplace, con el mismo
    // límite que la persona y dentro de la franja que dejan la cabecera y el botón de confirmar.
    val bandTop by rememberUpdatedState(headerBottomPx)
    val bandBottom by rememberUpdatedState(viewportPx - clearancePx)
    val pageScroll = remember(scroll) {
        WizardPageScroll(scroll = scroll, limit = { lockMax }, top = { bandTop.toFloat() }, bottom = { bandBottom.toFloat() })
    }

    // Cuánto se sube la página al abrir un paso cuyo final quedaría bajo el botón de confirmar (con la letra grande, p. ej. «Ver
    // detalles» en PLAN): lo que falta para que el final del paso quede por encima del botón y de su velo, y nunca más de una
    // fila-resumen. Con el teclado abierto, o sin medidas todavía, no se toca.
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val openExtraPx = if (imeBottomPx > 0 || viewportPx <= 0 || (heights[currentPage] ?: 0) <= 0) {
        0
    } else {
        WizardPageMetrics.openExtra(
            focusLinePx = WizardPageMetrics.focusLine(currentIndex, headerBottomPx, chipPx, gapPx),
            activeHeightPx = heights[currentPage] ?: 0,
            clearancePx = clearancePx,
            viewportPx = viewportPx,
            chipPx = chipPx,
        )
    }
    val openExtra by rememberUpdatedState(openExtraPx)

    // Primera vez: colocar la página en el paso del borrador sin animar (reabrir a mitad no
    // arranca arriba). Después: cada cambio de paso desliza hasta el nuevo, sea hacia delante
    // (check) o hacia atrás (atrás o tocar una fila-resumen), con la misma duración que el plegado.
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex) {
        // Dónde dejó la página este efecto la última vez: si la persona la movió, ya no se toca.
        var pinnedAt: Int
        if (!restored) {
            withTimeoutOrNull(1_500) { snapshotFlow { scroll.maxValue }.first { it >= targetPx } }
            scroll.scrollTo(targetPx.coerceAtMost(scroll.maxValue))
            pinnedAt = scroll.value
            restored = true
        } else if (reducedMotion) {
            scroll.scrollTo(targetPx.coerceAtMost(scroll.maxValue))
            pinnedAt = scroll.value
        } else {
            scroll.animateScrollTo(
                targetPx,
                tween(durationMillis = WizardMotion.SlideMillis, easing = FastOutSlowInEasing),
            )
            pinnedAt = scroll.value
        }
        // El final del paso, siempre por encima del botón: cuando el paso se compone del todo o crece (el control del paso
        // que asoma llega tras la animación; PLAN se revela al terminar el barrido) la página sube lo justo, mientras la persona no
        // la haya movido.
        snapshotFlow { openExtra }.collect { extra ->
            val wanted = (targetPx + extra).coerceAtMost(scroll.maxValue)
            if (wanted != pinnedAt && abs(scroll.value - pinnedAt) <= PIN_TOLERANCE_PX && !scroll.isScrollInProgress) {
                if (reducedMotion) {
                    scroll.scrollTo(wanted)
                } else {
                    scroll.animateScrollTo(wanted, tween(durationMillis = OPEN_EXTRA_MILLIS, easing = FastOutSlowInEasing))
                }
                pinnedAt = scroll.value
            }
        }
    }
    // Si el paso activo se encoge (o se cierra el teclado) y el scroll queda más allá del límite, volver.
    LaunchedEffect(Unit) {
        snapshotFlow { lockMax }.collect { limit ->
            if (scroll.value > limit && !scroll.isScrollInProgress) scroll.animateScrollTo(limit)
        }
    }

    val hazeState = remember { HazeState() }
    val statusTopDp = with(density) { statusTopPx.toDp() }
    val viewportDp = with(density) { viewportPx.toDp() }

    Box(modifier = Modifier.fillMaxSize().background(WizardColors.background)) {
        // Fuente del desenfoque de la cabecera y del botón: el fondo y la página que se desliza.
        // El fondo va DENTRO de la fuente: así el desenfoque que ven la cabecera y el botón es opaco y
        // lo que pasa por debajo no se transparenta nítido a través del cristal.
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(WizardColors.background)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .nestedScroll(lock)
                    .onSizeChanged { viewportPx = it.height },
            ) {
                CompositionLocalProvider(LocalWizardPageScroll provides pageScroll) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll),
                    ) {
                        Spacer(Modifier.height(statusTopDp + WizardHeaderBlockHeight))
                        pages.take(currentIndex + 2).forEachIndexed { index, page ->
                            key(page) {
                                val pageMode = when {
                                    index < currentIndex -> WizardPageMode.Completed
                                    index == currentIndex -> WizardPageMode.Active
                                    else -> WizardPageMode.Peek
                                }
                                val copy = wizardPageCopy(page, state.draft.goalProfile)
                                // El resumen se calcula UNA vez, cuando la página queda confirmada, y se guarda: recalcular
                                // todas las filas con cada pulsación sería caro (alguna consulta el catálogo de planes).
                                // Una página activa lo marca como caduco para que se recalcule al volver a confirmarse,
                                // pero conserva el texto anterior mientras la fila se despliega en sección.
                                val summary = when (pageMode) {
                                    WizardPageMode.Completed -> {
                                        if (page in staleSummaries || page !in summaries) {
                                            summaries[page] = setupStepSummary(page, state)
                                            staleSummaries.remove(page)
                                        }
                                        summaries.getValue(page)
                                    }
                                    WizardPageMode.Active -> {
                                        staleSummaries.add(page)
                                        summaries[page] ?: EMPTY_SUMMARY
                                    }
                                    WizardPageMode.Peek -> summaries[page] ?: EMPTY_SUMMARY
                                }
                                WizardPageItem(
                                    mode = pageMode,
                                    eyebrow = wizardEyebrow(page, pages),
                                    title = copy.title,
                                    subtitle = copy.subtitle,
                                    summaryLabel = summary.label,
                                    summaryValue = summary.value,
                                    stepTag = "setup-step-${page.name}",
                                    summaryTag = "setup-summary-${page.name}",
                                    onEdit = if (pageMode == WizardPageMode.Completed) {
                                        { viewModel.editStep(page) }
                                    } else {
                                        null
                                    },
                                    onNaturalHeight = { heights[page] = it },
                                    reducedMotion = reducedMotion,
                                    peekWindowPx = peekWindowPx,
                                    // La última confirmada sigue compuesta (oculta): atrás despliega justo esa.
                                    keepCard = index == currentIndex - 1,
                                ) {
                                    SetupStepContent(step = page, state = state, vm = viewModel)
                                }
                            }
                        }
                        // Hueco final: permite anclar arriba incluso un paso corto; el bloqueo de
                        // scroll impide que el usuario llegue a él arrastrando.
                        Spacer(Modifier.height(viewportDp))
                    }
                }
            }
        }

        WizardTopScrim(
            height = statusTopDp + WizardHeaderBlockHeight + 20.dp,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        WizardPageHeader(
            haze = hazeState,
            label = wizardHeaderLabel(currentPage, pages).uppercase(),
            segments = wizardBlockProgress(pages, confirmedCount = currentIndex + if (milestoneModule != null) 1 else 0).map { (_, fraction) ->
                WizardProgressSegment(fill = fraction)
            },
            onBack = { if (viewModel.canGoBack()) viewModel.goBack() else onLeave() },
            onExit = onLeave,
            exitLabel = "Salir",
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )
        WizardDock(
            enabled = checkEnabled,
            label = ctaLabel,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                when {
                    step == SetupStepId.REVIEW_ACTIVATE -> onActivate()
                    currentPage == SetupStepId.NAME -> viewModel.submitAliasAgePair()
                    step == SetupStepId.HEIGHT -> viewModel.submitAnthropometryPair()
                    else -> viewModel.submitCurrentStep(step)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // El aviso flota sobre el paso: no empuja la cabecera, la pregunta
        // ni el botón. Sigue siendo descartable y reintenta la misma operación.
        // H6: lo que el paso ya pinta por sí mismo (la lista de planes y el preview) no se repite aquí.
        val floatingErrors = state.errors.filterKeys { key -> !stepRendersError(step, key) }
        if (floatingErrors.isNotEmpty()) {
            WizardInlineErrors(
                errors = floatingErrors,
                onDismiss = viewModel::clearError,
                retryFor = viewModel::retryOperationForError,
                onRetry = { operation -> viewModel.retryFailedOperation(operation) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 92.dp),
            )
        }

        // El hito del bloque: la animación del módulo y la fila de etapas con la guía. Continuar confirma el
        // hito (el cursor pasa al bloque siguiente y la página se desliza hasta él); atrás vuelve a la última
        // pregunta del bloque.
        if (milestoneModule != null) {
            key(step) {
                ModuleCompleteOverlay(
                    module = milestoneModule,
                    onContinue = { viewModel.submitCurrentStep(step) },
                    onDismiss = { viewModel.goBack() },
                    stages = milestoneStages(step, route, state.completedBlocks),
                    rootTag = "setup-step-${step.name}",
                    ctaTag = "setup-milestone-continue",
                )
            }
        }

        // La pantalla de arranque: lo mismo que un hito pero sin nada completado ni color de «completado».
        // Solo en el alta completa y mientras el borrador sigue sin empezar: el cursor nunca salió de la primera
        // pregunta (los valores que traen los ajustes, como el alias o la edad, no cuentan como empezar).
        var introSeen by rememberSaveable(state.draft.draftId) { mutableStateOf(false) }
        val untouchedDraft = state.draft.stepProgress.visited.size <= 1 && step == route.firstOrNull()
        if (showIntro && !introSeen && milestoneModule == null && state.mode == SetupWizardMode.FULL &&
            untouchedDraft && introStages(route).size > 1
        ) {
            ModuleCompleteOverlay(
                module = KpknModule.INTRO,
                onContinue = { introSeen = true },
                onDismiss = onLeave,
                cta = "Empezar",
                stages = introStages(route),
                rootTag = "setup-intro",
                ctaTag = "setup-intro-start",
            )
        }
    }
}

/**
 * H6: ¿el paso [step] ya pinta por sí mismo el error de clave [key] de `SetupWizardState.errors`? Si sí, el aviso
 * flotante ([WizardInlineErrors]) no lo repite: antes pintaba TODO `errors` encima del paso y el mismo texto salía dos
 * veces (en el paso y en el aviso, con su propio «Reintentar»).
 *
 *  - PLAN pinta `candidates` (la búsqueda que falló o «ningún plan viable») y `preview` (el error del programa de la
 *    selección, encima de las tarjetas, con su «Reintentar»).
 *  - La semana armada (WEEK_LAYOUT) y la revisión final (REVIEW_ACTIVATE) pintan `preview` (sin programa preparado).
 *
 * Todo lo demás (guardado, activación, la clave `plan` de «Continuar»…) no lo pinta ningún paso y sigue en el aviso.
 */
internal fun stepRendersError(step: SetupStepId, key: String): Boolean = when (step) {
    SetupStepId.PLAN -> key == "candidates" || key == "preview"
    SetupStepId.WEEK_LAYOUT, SetupStepId.REVIEW_ACTIVATE -> key == "preview"
    else -> false
}

/**
 * Banner inline de errores, colocado en el flujo (por encima del paso, nunca
 * sobre la cabecera). Cada error se puede cerrar y, si pertenece a una
 * operación fallida, reintentar exactamente esa operación.
 */
@Composable
private fun WizardInlineErrors(
    errors: Map<String, String>,
    onDismiss: (String) -> Unit,
    retryFor: (String) -> SetupRetryOperation?,
    onRetry: (SetupRetryOperation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WizardSpacing.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        errors.forEach { (key, message) ->
            Surface(
                color = WizardColors.cardFill,
                shape = WizardShapes.card,
                border = BorderStroke(1.dp, WizardColors.danger),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        style = WizardTypography.bodySmall,
                        color = WizardColors.text,
                        modifier = Modifier.weight(1f),
                    )
                    retryFor(key)?.let { operation ->
                        TextButton(onClick = { onRetry(operation) }) {
                            Text("Reintentar", color = WizardColors.text)
                        }
                    }
                    TextButton(onClick = { onDismiss(key) }) { Text("Cerrar", color = WizardColors.danger) }
                }
            }
        }
    }
}

/**
 * Estados sin paso: carga, error recuperable y borrador no convertible.
 *
 * Es una pantalla completa, NO un AlertDialog: una carga que no termina o un
 * error no puede quedarse tras un modal sin salida ("popup eterno"), y todas
 * las acciones siguen visibles para volver o reintentar.
 */
@Composable
private fun WizardStatusScreen(
    title: String,
    body: String,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardColors.background)
            .statusBarsPadding()
            .padding(horizontal = WizardSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = WizardTypography.question,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
            if (tertiaryLabel != null && onTertiary != null) {
                TextButton(onClick = onTertiary) { Text(tertiaryLabel, color = WizardColors.danger) }
            }
        }
    }
}

/**
 * Intenciones de salida, separadas a propósito:
 *  - Salir (atrás o la X de la cabecera) abre «Guardar y salir» / «Seguir
 *    configurando»; el wizard solo se abandona cuando el guardado terminó y
 *    devolvió verdadero.
 *  - Descartar es una acción aparte, detrás de su propia confirmación, y nunca
 *    es alcanzable desde el botón Atrás. Solo navega si el descarte tuvo éxito.
 */
@Composable
private fun SetupWizardExitDialogs(
    state: SetupWizardState,
    viewModel: SetupWizardViewModel,
    onLeftWizard: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (state.dialog) {
        SetupWizardDialog.EXIT -> Unit

        SetupWizardDialog.DISCARD -> AlertDialog(
            onDismissRequest = { viewModel.keepConfiguring() },
            title = { Text("¿Descartar este borrador?", style = WizardTypography.cardTitle, color = WizardColors.text) },
            text = {
                Text(
                    "Se perderán las respuestas guardadas de esta configuración. Esta acción no se puede deshacer.",
                    style = WizardTypography.bodySmall,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (viewModel.confirmDiscard()) onLeftWizard()
                    }
                }) {
                    Text("Descartar", color = WizardColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.keepConfiguring() }) { Text("Conservar") }
            },
        )

        SetupWizardDialog.NONE -> Unit
    }
}