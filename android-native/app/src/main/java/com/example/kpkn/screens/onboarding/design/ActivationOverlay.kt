package com.example.kpkn.screens.onboarding.design

/*
 * Revisión y activación (K9): el overlay motivador que cierra el alta. Se pinta sobre la página vacía de REVIEW_ACTIVATE:
 * un pager a todo el ancho con un mensaje por bloque (Tú, Tu programa, Nutrición y RINGS), la tarjeta animada del programa
 * elegido y, debajo, los avisos, las confirmaciones y el botón «Activar y entrar a KPKN». Todo se lee del borrador y de las
 * vistas previas: nada se calcula de nuevo ni se rellena; lo que falta no sale.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.nutrition.nutritionGoalActive
import com.example.kpkn.domain.nutrition.nutritionTargetKgOf
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.CompetitionRules
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.SetupFormCaption
import com.example.kpkn.screens.onboarding.SetupPlanRevealDay
import com.example.kpkn.screens.onboarding.SetupPlanReveals
import com.example.kpkn.screens.onboarding.SetupProgramRoute
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.WEEKDAY_LABELS
import com.example.kpkn.screens.onboarding.activationBody
import com.example.kpkn.screens.onboarding.activeCompetition
import com.example.kpkn.screens.onboarding.activeMeetDate
import com.example.kpkn.screens.onboarding.activeMeetName
import com.example.kpkn.screens.onboarding.effectiveWeekStart
import com.example.kpkn.screens.onboarding.fixedRecipeDiffers
import com.example.kpkn.screens.onboarding.formatPercent
import com.example.kpkn.screens.onboarding.longestSessionMinutes
import com.example.kpkn.screens.onboarding.nutritionGoalNoteOf
import com.example.kpkn.screens.onboarding.nutritionGoalOf
import com.example.kpkn.screens.onboarding.nutritionPlanTuningOf
import com.example.kpkn.screens.onboarding.placeConflictLine
import com.example.kpkn.screens.onboarding.planReviewNotes
import com.example.kpkn.screens.onboarding.reviewBlockTargets
import com.example.kpkn.screens.onboarding.reviewBodyFatPercentOf
import com.example.kpkn.screens.onboarding.reviewCompetitionLine
import com.example.kpkn.screens.onboarding.reviewGenderMarkOf
import com.example.kpkn.screens.onboarding.reviewPlanCard
import com.example.kpkn.screens.onboarding.reviewSexLabelOf
import com.example.kpkn.screens.onboarding.ringLineText
import com.example.kpkn.screens.onboarding.rings.ringsTowerValues
import com.example.kpkn.screens.onboarding.stepContext
import com.example.kpkn.screens.onboarding.timeReviewNote
import com.example.kpkn.screens.onboarding.wizardMassUnitOf
import com.example.kpkn.screens.onboarding.design.entreno.SymbolCanvas
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.programSourceArtOf
import com.example.kpkn.screens.onboarding.design.entreno.rememberEntrenoForeground
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.entreno.plan.BlurOverlayDialog
import com.example.kpkn.screens.onboarding.design.entreno.plan.CoverLayout
import com.example.kpkn.screens.onboarding.design.entreno.plan.OverlayScrim
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCover
import com.example.kpkn.screens.onboarding.design.entreno.plan.goalAccent
import com.example.kpkn.ui.components.PagerDots
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.theme.MacroColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// ─── Tiempos, geometría y textos ─────────────────────────────────────────────

/** Etiqueta del botón de activar: también su descripción accesible (la de siempre del dock). */
private const val ACTIVATE_LABEL = "Activar y entrar a KPKN"

/** Entrada del overlay (fundido) y de su cabecera (sube 16 dp). */
private const val ENTER_MILLIS = 300

/** Entrada de la tarjeta del programa, el rodar de las cifras y el encendido de los días. */
private const val CARD_ENTER_MILLIS = 360
private const val ROLL_MILLIS = 600
private const val STRIP_STEP_MILLIS = 120

/** El alias: cada letra sube y aparece 40 ms después de la anterior, y tarda 320 ms en llegar. */
private const val LETTER_STEP_MILLIS = 40
private const val LETTER_RISE_MILLIS = 320

/**
 * La deriva de la portada: un bucle de 8 s. La portada suma 0,10 de su zona por unidad de paralaje, así que ±0,2 unidades
 * dan ±2 % del ancho de la zona.
 */
private const val DRIFT_PERIOD_SECONDS = 8f
private const val DRIFT_UNITS = 0.2f

/** La tarjeta del programa: radio de 28 dp, fondo casi negro y borde de cristal. */
private val ProgramCardShape = RoundedCornerShape(28.dp)
private val ProgramCardFill = Color(0xFF0E0E0F)

/** Un programa aún sin armar: la hoja con lápiz. */
private val SheetSymbolSize = 72.dp

/** Anillos de macros y torre de RINGS. */
private val MacroRingsSize = 200.dp
private val RingsTowerHeight = 170.dp

/** Lo que puede ocupar el bloque de avisos y confirmaciones antes de desplazarse por dentro. */
private val FooterNoticesMaxHeight = 200.dp

/** Letras de los días de la semana, de lunes a domingo (miércoles «X»). */
private val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

private const val PHRASE_YOU = "Con esto calculamos tu gasto y tus cargas."
private const val PHRASE_NUTRITION_PLAN = "Tu plan de comidas empieza hoy."
private const val PHRASE_NUTRITION_TRACKING = "Registra lo que comes y KPKN aprende contigo."
private const val PHRASE_RINGS = "Así llegas hoy. Tus RINGS se ajustan con cada entreno."

// ─── Overlay ─────────────────────────────────────────────────────────────────

/**
 * El overlay de la revisión, a pantalla completa como los hitos ([BlurOverlayDialog]). [state] trae el borrador y las vistas
 * previas; [activateEnabled] es la puerta del botón (la del anfitrión: confirmaciones, vista previa lista y sin escritura en
 * curso). «Editar» lleva a un bloque con [onEdit]; las confirmaciones se marcan con sus setters y [onActivate] es la misma
 * acción del dock. Atrás ([onBack]) vuelve un paso, como en los hitos.
 */
@Composable
fun ActivationOverlay(
    state: SetupWizardState,
    activateEnabled: Boolean,
    onEdit: (SetupStepId) -> Unit,
    onConfirmActivation: (Boolean) -> Unit,
    onConfirmFixedRecipe: (Boolean) -> Unit,
    onActivate: () -> Unit,
    onBack: () -> Unit,
) {
    val draft = state.draft
    val route = SetupStepGraph.stepIds(draft.stepContext())
    val targets = reviewBlockTargets(route)
    // «Editar» solo si el bloque tiene un paso en la ruta; la edición vuelve sola a la revisión.
    fun editOf(block: SetupWizardBlock): (() -> Unit)? =
        targets[block]?.let { target -> { onEdit(target) } }
    val planEntry = remember(draft.selectedCatalogId) {
        draft.selectedCatalogId?.let(PersonalizedPlanCatalog::find)
    }

    val moments = ArrayList<ActivationMoment>()
    if (SetupWizardBlock.BASICS in targets) {
        moments += ActivationMoment(SetupWizardBlock.BASICS, "Tú", editOf(SetupWizardBlock.BASICS)) { settled ->
            ActivationYouMoment(
                alias = draft.name.ifBlank { null },
                sexMark = reviewGenderMarkOf(draft),
                sexLabel = reviewSexLabelOf(draft),
                ageYears = draft.ageYears,
                heightCm = draft.heightCm,
                weightKg = draft.weightKg,
                weightUnit = wizardMassUnitOf(draft),
                bodyFatPercent = reviewBodyFatPercentOf(draft),
                settled = settled,
            )
        }
    }
    if (SetupWizardBlock.TRAINING in targets) {
        val program = state.programPreview
        val card = reviewPlanCard(state, planEntry)
        val week = program?.let { SetupPlanReveals.weekOf(it) }.orEmpty()
        val startDay = program?.startDay?.takeIf { it in 1..7 } ?: 1
        val minutes = draft.minutesPerSession ?: longestSessionMinutes(program)
        moments += ActivationMoment(SetupWizardBlock.TRAINING, "Tu programa", editOf(SetupWizardBlock.TRAINING)) { settled ->
            ActivationProgramMoment(
                draft = draft,
                later = draft.programRoute == SetupProgramRoute.LATER,
                card = card,
                week = week,
                startDay = startDay,
                minutes = minutes,
                settled = settled,
            )
        }
    }
    if (SetupWizardBlock.NUTRITION in targets) {
        val plan = state.nutritionPreparation?.plan ?: state.nutritionPlanPreview
        val tuning = nutritionPlanTuningOf(state)
        val goal = nutritionGoalOf(state)
        // Con meta de peso activa (Definir o Volumen) se dice el objetivo aunque no haya fecha; la fecha solo si la calcula el motor.
        val goalActive = draft.nutritionDraft?.let { nutritionGoalActive(it.direction, nutritionTargetKgOf(it)) } == true
        moments += ActivationMoment(SetupWizardBlock.NUTRITION, "Nutrición", editOf(SetupWizardBlock.NUTRITION)) { settled ->
            ActivationNutritionMoment(
                trackingOnly = draft.stepContext().nutritionStartChoice == "tracking_only",
                kcal = plan?.calorieTarget,
                proteinG = plan?.proteinGoal,
                carbsG = plan?.carbGoal,
                fatG = plan?.fatGoal,
                fractions = tuning?.let { macroRingFractions(it) },
                goalLine = if (goal != null && goalActive) {
                    "Objetivo: ${WizardWeightScale.formatWithUnit(goal.targetKg, goal.unit)}"
                } else {
                    null
                },
                goalDateLine = nutritionGoalNoteOf(state),
                settled = settled,
            )
        }
    }
    if (SetupWizardBlock.RINGS in targets) {
        val values = ringsTowerValues(state.ringsCoveragePreview)
        moments += ActivationMoment(SetupWizardBlock.RINGS, "RINGS", editOf(SetupWizardBlock.RINGS)) { settled ->
            ActivationRingsMoment(values = values, settled = settled)
        }
    }

    val reduced = wizardReducedMotion()
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    val header = remember { Animatable(if (reduced) 1f else 0f) }
    var leaving by remember { mutableStateOf(false) }
    // Atrás: se desvanece y, si la acción no avanza, vuelve a mostrarse en lugar de quedar invisible (como los hitos).
    val leave: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                shown.animateTo(0f, tween(200))
                onBack()
                delay(700)
                leaving = false
                shown.animateTo(1f, tween(200))
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!reduced) {
            launch { header.animateTo(1f, tween(ENTER_MILLIS)) }
            shown.animateTo(1f, tween(ENTER_MILLIS))
        }
    }

    // Los avisos del pie, en lenguaje llano: lo que falta antes de activar y lo que se ajusta solo con los entrenos.
    val notices = activationNoticesOf(
        state = state,
        nutritionPage = SetupWizardBlock.NUTRITION in targets,
        ringsPage = SetupWizardBlock.RINGS in targets,
    )
    val title = activationTitleOf(alias = draft.name.ifBlank { null }, ready = notices.none { it.blocking })
    BlurOverlayDialog(
        onDismissRequest = leave,
        dismissOnBack = true,
        shown = { shown.value },
    ) { blur ->
        ActivationOverlayBody(
            title = title,
            rise = { header.value },
            moments = moments,
            state = state,
            notices = notices,
            activateEnabled = activateEnabled,
            onConfirmActivation = onConfirmActivation,
            onConfirmFixedRecipe = onConfirmFixedRecipe,
            onActivate = onActivate,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                // Sin desenfoque del sistema el velo debe tapar casi todo, como en los hitos.
                .background(OverlayScrim.copy(alpha = if (blur) 0.52f else 0.96f)),
        )
    }
}

/**
 * El cuerpo del overlay sin ventana: cabecera, pager de momentos y pie fijo. [rise] (0 a 1, se lee al dibujar) sube la
 * cabecera al entrar; [modifier] pone el tamaño y el fondo que decide quien lo muestra (el diálogo, o una hoja de prueba).
 */
@Composable
internal fun ActivationOverlayBody(
    title: String,
    rise: () -> Float,
    moments: List<ActivationMoment>,
    state: SetupWizardState,
    notices: List<ActivationNotice>,
    activateEnabled: Boolean,
    onConfirmActivation: (Boolean) -> Unit,
    onConfirmFixedRecipe: (Boolean) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    Column(
        modifier = modifier
            .testTag("setup-activation-overlay")
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // Lo que sube: la cabecera y el carrusel, con cada momento entero. Si la pantalla es baja, se desplaza esta parte.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            ActivationHeader(title = title, rise = rise, ready = notices.none { it.blocking })
            if (moments.isNotEmpty()) {
                ActivationPager(moments = moments, initialPage = initialPage)
            }
        }
        // Fijo, siempre a la vista: avisos, confirmaciones, un error de la activación y el botón.
        ActivationFooter(
            state = state,
            notices = notices,
            activateEnabled = activateEnabled,
            onConfirmActivation = onConfirmActivation,
            onConfirmFixedRecipe = onConfirmFixedRecipe,
            onActivate = onActivate,
        )
    }
}

/**
 * La cabecera: «TODO LISTO» en verde de marca cuando no bloquea nada (si no, «REVISA ANTES DE ACTIVAR» en ámbar) y el título en
 * Syne (sin cortar: si el alias es largo, ocupa dos líneas).
 */
@Composable
internal fun ActivationHeader(title: String, rise: () -> Float, ready: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .graphicsLayer {
                val k = rise()
                alpha = k
                translationY = (1f - k) * 16.dp.toPx()
            },
    ) {
        Text(
            text = if (ready) "TODO LISTO" else "REVISA ANTES DE ACTIVAR",
            style = WizardTypography.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
            color = if (ready) SymbolPalette.ok else SymbolPalette.energia,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = title, style = WizardTypography.heroTitle, color = WizardColors.text)
    }
}

// ─── Momentos y pager ────────────────────────────────────────────────────────

/** Un momento del pager: su bloque (para «Editar»), su rótulo y su contenido, que recibe si la página ya está asentada. */
internal class ActivationMoment(
    val block: SetupWizardBlock,
    val label: String,
    val onEdit: (() -> Unit)?,
    val content: @Composable (settled: Boolean) -> Unit,
)

/**
 * Los momentos a todo el ancho, con su indicador debajo y «Siguiente» como acción de texto (fuera en el último). Cada página
 * anima su contenido solo cuando queda asentada. [initialPage] solo cambia dónde empieza (las pruebas y la hoja lo usan).
 */
@Composable
internal fun ActivationPager(
    moments: List<ActivationMoment>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, moments.lastIndex),
        pageCount = { moments.size },
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // El carrusel toma el alto de su página más alta: cada página se mide sin límite y ninguna queda cortada.
    val pageHeights = remember(moments.size) { mutableStateMapOf<Int, Int>() }
    val carouselHeight = with(density) { (pageHeights.values.maxOrNull() ?: 0).toDp() }
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            // Se componen todas las páginas para medirlas; las que no se ven no animan (solo la asentada lo hace).
            beyondViewportPageCount = moments.size,
            modifier = Modifier
                .fillMaxWidth()
                .height(carouselHeight)
                .testTag("review-carousel"),
            verticalAlignment = Alignment.Top,
        ) { index ->
            val moment = moments[index]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true)
                    .onSizeChanged { pageHeights[index] = it.height }
                    .testTag("setup-activation-page-$index"),
            ) {
                ActivationMomentHeader(label = moment.label, block = moment.block, onEdit = moment.onEdit)
                Spacer(Modifier.height(16.dp))
                moment.content(pagerState.settledPage == index)
                Spacer(Modifier.height(8.dp))
            }
        }
        // Los puntos van centrados en el hueco que deja «Siguiente»; no son un control (el deslizado y el botón mandan).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sin «onSelect» PagerDots ya dice su página («Página N de M», la actual): aquí no se duplica.
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                PagerDots(count = moments.size, current = pagerState.currentPage)
            }
            if (pagerState.currentPage < moments.lastIndex) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier
                        .heightIn(min = WizardSpacing.touchTarget)
                        .testTag("setup-activation-next"),
                ) {
                    Text(text = "Siguiente", style = WizardTypography.controlLabel, color = WizardColors.text)
                }
            } else {
                // Mismo hueco que «Siguiente», para que los puntos no se desplacen al llegar a la última página.
                Spacer(Modifier.width(112.dp))
            }
        }
    }
}

/** El rótulo del momento en mayúsculas y, a la derecha, «Editar» con 48 dp de área de toque. */
@Composable
private fun ActivationMomentHeader(label: String, block: SetupWizardBlock, onEdit: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WizardSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = WizardTypography.milestoneTitle,
            color = WizardColors.text,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onEdit != null) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier
                    .heightIn(min = WizardSpacing.touchTarget)
                    .testTag("setup-activation-edit-${block.name}")
                    .semantics { contentDescription = "Editar $label" },
            ) {
                Text(text = "Editar", style = WizardTypography.controlLabel, color = WizardColors.text)
            }
        }
    }
}

/** Una frase bajo el visual, en la tinta de marca. Sin dato, no se pinta ninguna. */
@Composable
private fun ActivationPhrase(text: String) {
    Text(text = text, style = WizardTypography.body, color = WizardColors.text)
}

// ─── Tú ──────────────────────────────────────────────────────────────────────

/**
 * Tú: el alias en grande, letra a letra, y el símbolo del sexo; debajo, edad, altura, peso y % de grasa como contadores que
 * ruedan hasta su valor. Lo que falte, fuera.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivationYouMoment(
    alias: String?,
    sexMark: WizardGenderMark?,
    sexLabel: String?,
    ageYears: Int?,
    heightCm: Double?,
    weightKg: Double?,
    weightUnit: WizardMassUnit,
    bodyFatPercent: Double?,
    settled: Boolean,
) {
    val hasData = alias != null || sexMark != null || ageYears != null || heightCm != null ||
        weightKg != null || bodyFatPercent != null
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (alias != null) {
            ActivationRisingAlias(text = alias, settled = settled)
        } else {
            Text(text = "Sin declarar", style = WizardTypography.heroTitle, color = WizardColors.textFaint)
        }
        if (sexMark != null) {
            WizardGenderGlyph(
                mark = sexMark,
                color = WizardColors.text,
                modifier = Modifier
                    .size(44.dp)
                    .then(if (sexLabel != null) Modifier.semantics { contentDescription = sexLabel } else Modifier),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ageYears?.let { age ->
                ActivationRollingStat(target = age.toDouble(), settled = settled) { "${it.roundToInt()} años" }
            }
            heightCm?.let { cm ->
                ActivationRollingStat(target = cm, settled = settled) { "${it.roundToInt()} cm" }
            }
            weightKg?.let { kg ->
                ActivationRollingStat(target = kg, settled = settled) { WizardWeightScale.formatWithUnit(it, weightUnit) }
            }
            bodyFatPercent?.let { pct ->
                ActivationRollingStat(target = pct, settled = settled) { "${formatPercent(it)} % de grasa" }
            }
        }
        if (hasData) ActivationPhrase(PHRASE_YOU)
    }
}

/** Una letra del alias con su posición: cada una aparece en su turno. */
private class IndexedLetter(val ch: Char, val index: Int)

/**
 * El alias en grande, letra a letra: cada una sube y aparece 40 ms después de la anterior. Ya asentada (o con movimiento
 * reducido), está en su sitio. Se lee como una sola frase para TalkBack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivationRisingAlias(text: String, settled: Boolean) {
    val reduced = wizardReducedMotion()
    val words = remember(text) {
        var index = 0
        text.split(' ').filter { it.isNotEmpty() }.map { word -> word.map { ch -> IndexedLetter(ch, index++) } }
    }
    val letterCount = words.sumOf { it.size }
    val totalMillis = LETTER_STEP_MILLIS * (letterCount - 1).coerceAtLeast(0) + LETTER_RISE_MILLIS
    val progress = remember(text) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(settled, text) {
        if (settled && !reduced && progress.value < 1f) {
            progress.animateTo(1f, tween(durationMillis = totalMillis, easing = LinearEasing))
        }
    }
    FlowRow(
        modifier = Modifier.clearAndSetSemantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        words.forEach { word ->
            Row {
                word.forEach { letter ->
                    Text(
                        text = letter.ch.toString(),
                        style = WizardTypography.heroTitle,
                        color = WizardColors.text,
                        modifier = Modifier.graphicsLayer {
                            val elapsed = progress.value * totalMillis
                            val k = eOutCubic(clamp01((elapsed - LETTER_STEP_MILLIS * letter.index) / LETTER_RISE_MILLIS))
                            alpha = k
                            translationY = (1f - k) * 12.dp.toPx()
                        },
                    )
                }
            }
        }
    }
}

/** Un contador: arranca en cero y rueda hasta [target] al asentarse la página. Su texto lo da [format]. */
@Composable
private fun ActivationRollingStat(target: Double, settled: Boolean, format: (Double) -> String) {
    val value = rememberActivationRolling(target = target, settled = settled)
    Text(text = format(value), style = WizardTypography.stepTitle, color = WizardColors.text, maxLines = 1)
}

/**
 * Una cifra que rueda desde cero hasta [target] en 600 ms en cuanto la página se asienta. Con movimiento reducido, de golpe.
 * Antes de asentarse, vale cero (no hay un número real que enseñar todavía).
 */
@Composable
private fun rememberActivationRolling(target: Double, settled: Boolean): Double {
    val reduced = wizardReducedMotion()
    val rolled = remember { Animatable(if (reduced) target.toFloat() else 0f) }
    LaunchedEffect(settled, target) {
        if (settled) {
            if (reduced) {
                rolled.snapTo(target.toFloat())
            } else {
                rolled.animateTo(target.toFloat(), tween(durationMillis = ROLL_MILLIS))
            }
        }
    }
    return rolled.value.toDouble()
}

// ─── Tu programa ─────────────────────────────────────────────────────────────

/**
 * Tu programa: la tarjeta animada del programa elegido (la portada, su kicker, nombre, días y minutos con las cifras que ruedan,
 * la semana con sus días de entreno, la sesión principal y la competición). Sin programa, la tarjeta dice que aún no hay
 * ninguno. Su frase dice el día de la primera sesión, sacado del programa.
 */
@Composable
internal fun ActivationProgramMoment(
    draft: SetupWizardDraft,
    later: Boolean,
    card: PlanCardModel?,
    week: List<SetupPlanRevealDay>,
    startDay: Int,
    minutes: Int?,
    settled: Boolean,
) {
    val reduced = wizardReducedMotion()
    // La tarjeta entra al asentarse (sube 24 dp, se funde y se endereza en 360 ms) y después la portada deriva despacio.
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    val drift = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(settled) {
        if (settled && !reduced) {
            enter.animateTo(1f, tween(durationMillis = CARD_ENTER_MILLIS))
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    val seconds = (now - start) / 1_000_000_000f
                    drift.floatValue = sin(2f * PI.toFloat() * seconds / DRIFT_PERIOD_SECONDS) * DRIFT_UNITS
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val k = enter.value
                    alpha = k
                    translationY = (1f - k) * 24.dp.toPx()
                    rotationX = (1f - k) * 8f
                    cameraDistance = 12f * density
                }
                .clip(ProgramCardShape)
                .background(ProgramCardFill)
                .border(1.dp, WizardColors.glassBorder, ProgramCardShape),
        ) {
            when {
                later -> ActivationLaterBody(settled = settled)
                card == null -> Text(
                    text = "Cuando lo elijas, lo verás aquí.",
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(20.dp),
                )
                else -> ActivationProgramBody(
                    draft = draft,
                    card = card,
                    week = week,
                    startDay = startDay,
                    minutes = minutes,
                    settled = settled,
                    drift = { drift.floatValue },
                )
            }
        }
        val firstDay = week.firstOrNull()?.day
        if (!later && firstDay != null) {
            ActivationPhrase("Tu primera sesión es el ${WEEKDAY_LABELS[firstDay].lowercase()}.")
        }
    }
}

/** Sin programa («Lo armaré yo más adelante»): la hoja con lápiz, «Aún sin programa» y dónde elegirlo después. */
@Composable
private fun ActivationLaterBody(settled: Boolean) {
    val reduced = wizardReducedMotion()
    val foreground = rememberEntrenoForeground()
    val clock = rememberSymbolClock(active = !reduced && foreground && settled)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SymbolCanvas(
            art = programSourceArtOf(ProgramSource.LATER, reduced),
            selected = true,
            clock = clock,
            reducedMotion = reduced,
            modifier = Modifier
                .size(SheetSymbolSize)
                .semantics { contentDescription = "Aún sin programa" },
        )
        Text(text = "Aún sin programa", style = WizardTypography.stepTitle, color = WizardColors.text)
        Text(text = "Lo eliges cuando quieras desde Programas.", style = WizardTypography.note, color = WizardColors.textMuted)
    }
}

/**
 * El cuerpo de la tarjeta con programa: la portada en su modo de arte (sin título ni datos repetidos encima), y debajo el
 * kicker, el nombre, las cifras, la semana, la sesión principal y la competición.
 */
@Composable
private fun ActivationProgramBody(
    draft: SetupWizardDraft,
    card: PlanCardModel,
    week: List<SetupPlanRevealDay>,
    startDay: Int,
    minutes: Int?,
    settled: Boolean,
    drift: () -> Float,
) {
    val reduced = wizardReducedMotion()
    val mainSession = week.firstOrNull { it.isMain }
    val today = remember { LocalDate.now() }
    Column {
        PlanCover(
            model = card.copy(title = "", kicker = "", blurb = "", daysLabel = "", minutesLabel = "", levelLabel = "", badge = null),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            animate = false,
            reduced = reduced,
            parallax = drift,
            cornerRadius = 0.dp,
            layout = CoverLayout.POSTER,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (card.kicker.isNotBlank()) {
                Text(text = card.kicker, style = WizardTypography.caption, color = WizardColors.textMuted)
            }
            Text(text = card.title, style = WizardTypography.stepTitle, color = WizardColors.text)
            ActivationRollingFacts(
                days = week.size.takeIf { it > 0 },
                minutes = minutes?.takeIf { it > 0 },
                level = card.levelLabel,
                settled = settled,
            )
            ActivationWeekStrip(
                startDay = startDay,
                trainingDays = week.map { it.day },
                accent = goalAccent(card.profile),
                settled = settled,
            )
            mainSession?.let {
                Text(
                    text = "Sesión principal: ${it.title} · ${WEEKDAY_LABELS[it.day].lowercase()}",
                    style = WizardTypography.note,
                    color = WizardColors.text,
                )
            }
            activationCompetitionLine(draft, today)?.let { line ->
                Text(text = line, style = WizardTypography.note, color = WizardColors.text)
            }
        }
    }
}

/** «4 días · 60 min · Intermedio»: las cifras ruedan hasta su valor; los datos que no hay, fuera. */
@Composable
private fun ActivationRollingFacts(days: Int?, minutes: Int?, level: String, settled: Boolean) {
    val shownDays = rememberActivationRolling(target = days?.toDouble() ?: 0.0, settled = settled)
    val shownMinutes = rememberActivationRolling(target = minutes?.toDouble() ?: 0.0, settled = settled)
    val text = listOfNotNull(
        days?.let { SpanishPlurals.days(shownDays.roundToInt()) },
        minutes?.let { "${shownMinutes.roundToInt()} min" },
        level.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    if (text.isNotEmpty()) {
        Text(text = text, style = WizardTypography.controlLabel, color = WizardColors.text)
    }
}

/**
 * La semana en el orden del programa (empieza en su día de inicio): los días de entreno se encienden uno a uno, de 120 ms en
 * 120 ms, en el acento del programa. Con movimiento reducido, todos encendidos desde el principio.
 */
@Composable
private fun ActivationWeekStrip(startDay: Int, trainingDays: List<Int>, accent: Color, settled: Boolean) {
    val reduced = wizardReducedMotion()
    val totalMillis = STRIP_STEP_MILLIS * trainingDays.size.coerceAtLeast(1)
    val lit = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(settled) {
        if (settled && !reduced && lit.value < 1f) {
            lit.animateTo(1f, tween(durationMillis = totalMillis, easing = LinearEasing))
        }
    }
    val order = (0 until 7).map { ((startDay - 1 + it) % 7) + 1 }
    val names = trainingDays.joinToString(", ") { WEEKDAY_LABELS[it].lowercase() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Días de entreno: $names" },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        order.forEach { day ->
            val k = trainingDays.indexOf(day)
            val on = k >= 0 && lit.value * totalMillis >= STRIP_STEP_MILLIS * k
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .then(
                        if (on) {
                            Modifier.background(accent)
                        } else {
                            Modifier.border(1.dp, WizardColors.glassBorder, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = DAY_LETTERS[day - 1],
                    style = WizardTypography.note,
                    color = when {
                        on -> WizardColors.ctaContent
                        k >= 0 -> WizardColors.text
                        else -> WizardColors.textFaint
                    },
                )
            }
        }
    }
}

/**
 * «Faltan 12 semanas para Nacional Open», con la cuenta de [CompetitionRules.weeksUntil]. Sin fecha válida, o si la fecha ya
 * pasó, lo que dice la revisión («Compites, aún sin fecha»). Solo lee la competición con sus tres lecturas.
 */
private fun activationCompetitionLine(draft: SetupWizardDraft, today: LocalDate): String? {
    if (draft.activeCompetition() != CompetitionAnswer.WITH_DATE) return reviewCompetitionLine(draft)
    val date = CompetitionRules.parseMeetDate(draft.activeMeetDate()) ?: return reviewCompetitionLine(draft)
    val weeks = CompetitionRules.weeksUntil(today, date, draft.effectiveWeekStart())
    if (weeks < 1) return reviewCompetitionLine(draft)
    val event = draft.activeMeetName().trim().ifEmpty { "tu competición" }
    return if (weeks == 1) "Falta 1 semana para $event" else "Faltan $weeks semanas para $event"
}

// ─── Nutrición ───────────────────────────────────────────────────────────────

/**
 * Nutrición: los anillos de macros se llenan y las kcal cuentan hasta su valor (la base diaria media, nunca el objetivo de
 * hoy); debajo, la leyenda de gramos y, si hay meta, el objetivo y su fecha. Solo registro: sin anillos ni cifras.
 */
@Composable
internal fun ActivationNutritionMoment(
    trackingOnly: Boolean,
    kcal: Int?,
    proteinG: Int?,
    carbsG: Int?,
    fatG: Int?,
    fractions: MacroRingFractions?,
    goalLine: String?,
    goalDateLine: String?,
    settled: Boolean,
) {
    // Los anillos nacen al asentarse y se quedan llenos: el estado va antes de cualquier salida temprana.
    val reduced = wizardReducedMotion()
    var appeared by remember { mutableStateOf(reduced) }
    LaunchedEffect(settled) { if (settled) appeared = true }
    if (trackingOnly) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Registrarás tus comidas sin objetivo de calorías.",
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
            )
            ActivationPhrase(PHRASE_NUTRITION_TRACKING)
        }
        return
    }
    if (kcal == null) {
        Text(text = "Aún falta tu plan de comidas.", style = WizardTypography.note, color = WizardColors.textMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (fractions != null) {
                WizardMacroRings(
                    kcal = kcal,
                    fractions = fractions,
                    // Dentro del hueco de los anillos solo cabe una palabra corta; la base media se dice justo debajo.
                    caption = "kcal/día",
                    description = "Base diaria media: $kcal kcal. " +
                        "Proteína ${gramsText(proteinG)}, hidratos ${gramsText(carbsG)}, grasas ${gramsText(fatG)}.",
                    appeared = appeared,
                    reducedMotion = reduced,
                    modifier = Modifier.size(MacroRingsSize),
                )
                Text(text = "Base diaria media", style = WizardTypography.note, color = WizardColors.textMuted)
            } else {
                Text(text = "$kcal kcal", style = WizardTypography.controlValue, color = WizardColors.text)
            }
        }
        ActivationMacroLegend(proteinG = proteinG, carbsG = carbsG, fatG = fatG)
        goalLine?.let { Text(text = it, style = WizardTypography.controlLabel, color = WizardColors.text) }
        goalDateLine?.let { Text(text = it, style = WizardTypography.note, color = WizardColors.textMuted) }
        ActivationPhrase(PHRASE_NUTRITION_PLAN)
    }
}

/** Gramos de cada macro con su tinta de marca: proteína, hidratos y grasas. */
@Composable
private fun ActivationMacroLegend(proteinG: Int?, carbsG: Int?, fatG: Int?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ActivationMacroLegendItem(label = "Proteína", grams = proteinG, color = MacroColors.protein)
        ActivationMacroLegendItem(label = "Hidratos", grams = carbsG, color = MacroColors.carbs)
        ActivationMacroLegendItem(label = "Grasas", grams = fatG, color = MacroColors.fat)
    }
}

@Composable
private fun ActivationMacroLegendItem(label: String, grams: Int?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(color))
        Text(text = gramsText(grams), style = WizardTypography.controlLabel, color = WizardColors.text)
        Text(text = label, style = WizardTypography.note, color = WizardColors.textMuted)
    }
}

/** Gramos de un macro para la leyenda y la descripción: «150 g», o «sin datos» si no hay cifra. */
private fun gramsText(grams: Int?): String = grams?.let { "$it g" } ?: "sin datos"

// ─── RINGS ───────────────────────────────────────────────────────────────────

/**
 * RINGS: la torre se llena hasta sus valores (escalonada 120 ms por anillo) y una línea por canal. Sin valor, «sin calibrar»,
 * nunca un 100 %; la frase solo si hay algún valor.
 */
@Composable
internal fun ActivationRingsMoment(values: TowerRingValues, settled: Boolean) {
    val reduced = wizardReducedMotion()
    var appeared by remember { mutableStateOf(reduced) }
    LaunchedEffect(settled) { if (settled) appeared = true }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(RingsTowerHeight)) {
            // La torre nace al asentarse: su animación de entrada arranca entonces.
            if (appeared) {
                KpknTowerRings(values = values, modifier = Modifier.fillMaxWidth().height(RingsTowerHeight))
            }
        }
        ActivationRingLine(channel = RingChannel.ENERGIA, name = "Energía", percent = values[RingChannel.ENERGIA])
        ActivationRingLine(channel = RingChannel.MUSCULOS, name = "Músculos", percent = values[RingChannel.MUSCULOS])
        ActivationRingLine(channel = RingChannel.COLUMNA, name = "Columna", percent = values[RingChannel.COLUMNA])
        if (RingChannel.entries.any { values[it] != null }) ActivationPhrase(PHRASE_RINGS)
    }
}

/** Una línea de la torre: el punto del anillo, su nombre y su valor. */
@Composable
private fun ActivationRingLine(channel: RingChannel, name: String, percent: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(KpknRingColors.of(channel)) }
        Spacer(Modifier.width(8.dp))
        Text(
            text = ringLineText(name, percent),
            style = WizardTypography.controlLabel,
            color = if (percent == null) WizardColors.textMuted else WizardColors.text,
        )
    }
}

// ─── Pie: avisos, confirmaciones y activación ────────────────────────────────

/**
 * El pie fijo bajo el carrusel: los avisos en lenguaje llano (los que bloquean, con más contraste), las confirmaciones, un error
 * de la activación encima del botón (con el texto que ya da el ViewModel) y el botón de activar.
 */
@Composable
internal fun ActivationFooter(
    state: SetupWizardState,
    notices: List<ActivationNotice>,
    activateEnabled: Boolean,
    onConfirmActivation: (Boolean) -> Unit,
    onConfirmFixedRecipe: (Boolean) -> Unit,
    onActivate: () -> Unit,
) {
    val draft = state.draft
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = FooterNoticesMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            notices.forEach { notice ->
                if (notice.blocking) {
                    Text(text = notice.text, style = WizardTypography.bodySmall, color = WizardColors.text)
                } else {
                    SetupFormCaption(notice.text)
                }
            }
            if (state.requiresActivationConfirmation) {
                ReviewConfirmation(
                    title = "Confirmo la activación",
                    body = activationBody(state),
                    confirmed = draft.confirmActivation,
                    onToggle = { onConfirmActivation(!draft.confirmActivation) },
                )
            }
            if (state.fixedRecipeDiffers()) {
                ReviewConfirmation(
                    title = "Confirmo la rotación y la duración reales",
                    body = "La receta fija propone unos días y unos minutos distintos a los que declaraste.",
                    confirmed = draft.acceptFixedRecipeDifference,
                    onToggle = { onConfirmFixedRecipe(!draft.acceptFixedRecipeDifference) },
                )
            }
        }
        state.errors.filterKeys { key -> key != "preview" }.values.forEach { message ->
            Text(text = message, style = WizardTypography.bodySmall, color = WizardColors.danger)
        }
        ActivationButton(
            enabled = activateEnabled && !state.isCommitting,
            busy = state.isCommitting,
            onClick = onActivate,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * El botón de activar: la píldora de 56 dp del dock, con su etiqueta de prueba `setup-continue` y el estado real. Mientras
 * activa ([busy]) se desactiva y muestra un indicador.
 */
@Composable
internal fun ActivationButton(
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            // Con letra grande y en pantallas estrechas la etiqueta ocupa dos líneas: la píldora crece, nunca corta el texto.
            .heightIn(min = WizardSpacing.ctaHeight)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.background(WizardColors.cta)
                } else {
                    Modifier
                        .background(WizardColors.ctaDisabled)
                        .border(1.dp, WizardColors.glassBorder, CircleShape)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag("setup-continue")
            .semantics {
                contentDescription = ACTIVATE_LABEL
                if (busy) stateDescription = "Activando"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = WizardColors.ctaContent,
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(
                text = ACTIVATE_LABEL,
                style = WizardTypography.cta,
                color = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

/**
 * Un aviso del pie: su texto en lenguaje llano y si bloquea la activación (en ese caso se lee con más contraste).
 */
internal data class ActivationNotice(val text: String, val blocking: Boolean = false)

/**
 * Los avisos del pie, en lenguaje llano y solo lo que hay que saber antes de activar: sin líneas de estado internas. Si algo
 * bloquea, se dice qué falta; si se ajusta solo con los entrenos, se dice cómo. [nutritionPage] y [ringsPage] dicen si el alta
 * trae ese bloque. Función pura (con pruebas): el pie solo la pinta.
 */
internal fun activationNoticesOf(
    state: SetupWizardState,
    nutritionPage: Boolean,
    ringsPage: Boolean,
): List<ActivationNotice> {
    val draft = state.draft
    val context = draft.stepContext()
    val notices = ArrayList<ActivationNotice>()
    if (draft.programRoute != SetupProgramRoute.LATER) {
        val program = state.programPreview
        val previewError = state.previewError
        if (program != null) {
            timeReviewNote(draft, state.programSessionMinutes)?.let { notices += ActivationNotice(it) }
            // Una sesión que cae en un día cuyo lugar no tiene su material: el aviso persiste, porque es esa semana la que se activa.
            state.weekLayout?.placeConflicts?.forEach { conflict ->
                notices += ActivationNotice(placeConflictLine(conflict))
            }
            // B-03: solo lectura del informe ya calculado (planes propios); no cambia el programa.
            planReviewNotes(state.previewReport).forEach { note -> notices += ActivationNotice(note) }
        } else if (previewError != null) {
            notices += ActivationNotice(previewError, blocking = true)
        } else if (state.isPreviewLoading) {
            notices += ActivationNotice("Estamos preparando tu programa…")
        } else if (draft.selectedCatalogId == null) {
            notices += ActivationNotice("Aún no has elegido programa.", blocking = true)
        } else {
            notices += ActivationNotice("Falta preparar tu programa.", blocking = true)
        }
    }

    if (nutritionPage && context.nutritionStartChoice != "tracking_only") {
        val preparation = state.nutritionPreparation
        val plan = preparation?.plan ?: state.nutritionPlanPreview
        val error = preparation?.errors?.values?.firstOrNull() ?: state.nutritionErrors.values.firstOrNull()
        if (error != null) {
            notices += ActivationNotice(error, blocking = true)
        } else if (plan == null) {
            notices += ActivationNotice("Aún falta tu plan de comidas.", blocking = true)
        } else if (context.nutritionProfessional) {
            notices += ActivationNotice("Tu plan de comidas lo indica un profesional.")
        }
    }

    if (ringsPage) {
        val coverage = state.ringsCoveragePreview ?: RingsCoverage.NO_DATA
        val hasData = listOf(coverage.muscular, coverage.system, coverage.structure).any { it.hasData }
        if (state.ringsPreviewLoading) {
            notices += ActivationNotice("Calculando tus RINGS…")
        } else if (!hasData) {
            notices += ActivationNotice("Tus RINGS se ajustarán con tus primeros entrenos.")
        }
    }
    return notices
}

/** El título del overlay: lo que dice la cabecera según el alias y si el alta está lista para activar. */
internal fun activationTitleOf(alias: String?, ready: Boolean): String = when {
    ready && alias != null -> "$alias, tu KPKN está listo"
    ready -> "Tu KPKN está listo"
    alias != null -> "$alias, falta un paso"
    else -> "Falta un paso"
}

/**
 * Confirmación del alta: una fila con su casilla dibujada, título y cuerpo. Sin tarjeta; todo el renglón cambia el valor
 * (`Role.Checkbox`, con el estado marcado o no).
 */
@Composable
internal fun ReviewConfirmation(
    title: String,
    body: String,
    confirmed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WizardSpacing.touchTarget)
            .toggleable(value = confirmed, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = WizardTypography.controlLabel, color = WizardColors.text)
            Text(text = body, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
        }
        ReviewCheckMark(checked = confirmed)
    }
}

/** La casilla del wizard: un círculo con borde tenue que se llena de crema, con el check, al confirmar. */
@Composable
private fun ReviewCheckMark(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (checked) {
                    Modifier.background(WizardColors.cta)
                } else {
                    Modifier.border(1.5.dp, WizardColors.markBorder, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = WizardColors.ctaContent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
