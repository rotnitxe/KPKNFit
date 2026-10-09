package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.catalogConfigurationDisplayName
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.nutritionGoalActive
import com.example.kpkn.domain.nutrition.nutritionTargetKgOf
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.CompetitionRules
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.domain.training.HighVolumeNotice
import com.example.kpkn.domain.training.PersonalizationReport
import com.example.kpkn.domain.training.VolumeSoftBand
import com.example.kpkn.screens.onboarding.design.MacroRingFractions
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardGenderGlyph
import com.example.kpkn.screens.onboarding.design.WizardGenderMark
import com.example.kpkn.screens.onboarding.design.WizardMacroRings
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.design.entreno.SymbolCanvas
import com.example.kpkn.screens.onboarding.design.entreno.programSourceArtOf
import com.example.kpkn.screens.onboarding.design.entreno.rememberEntrenoForeground
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCover
import com.example.kpkn.screens.onboarding.design.macroRingFractions
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.entreno.toCardModel
import com.example.kpkn.screens.onboarding.rings.ringsTowerValues
import com.example.kpkn.screens.programs.PlanInfoMode
import com.example.kpkn.screens.programs.PlanInfoSheet
import com.example.kpkn.screens.programs.ReadyWeekSnapshot
import com.example.kpkn.ui.components.PagerDots
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.theme.MacroColors
import kotlinx.coroutines.launch

/**
 * Revisión y activación: un carrusel a toda la anchura con una página por bloque de la ruta (Tú, Entreno, Nutrición y RINGS),
 * su indicador de páginas y, debajo, los avisos y las confirmaciones que el alta exige.
 *
 * Reglas:
 * - Sin tarjetas: cada página es la superficie negra con aire. «Editar» de cada página lleva al primer paso de su bloque
 *   ([reviewBlockTargets]) y la edición vuelve sola a la revisión.
 * - Los datos se leen del borrador y de las vistas previas. Nada se rellena: lo que no se sabe sale como «Sin declarar» o
 *   «Sin calibrar», nunca como un 100 % derivado.
 * - Entreno: el símbolo del objetivo y la portada salen del programa previsualizado. La competición se lee SOLO con
 *   `activeCompetition()`, `activeMeetDate()` y `activeMeetName()`.
 * - Nutrición: las kcal son la base diaria (media), nunca el objetivo de hoy; la fecha del objetivo sale de
 *   [nutritionGoalNoteOf], sin calcularla de nuevo.
 * - RINGS: la torre usa la cobertura de la vista previa ([ringsTowerValues]): los mismos valores que «Tus RINGS».
 * - Avisos (programa, lugares, nutrición, RINGS) y confirmaciones van debajo del carrusel, sin tarjeta.
 * - El CTA de activación sigue siendo del Host; esta pantalla no confirma ni avanza.
 */
@Composable
fun SetupReviewStep(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    val draft = state.draft
    val route = SetupStepGraph.stepIds(draft.stepContext())
    val targets = reviewBlockTargets(route)
    // «Editar» solo si el paso existe en la ruta y no es el que está abierto (nunca un no-op).
    fun editOf(block: SetupWizardBlock): (() -> Unit)? =
        targets[block]?.takeIf { it != state.currentStep }?.let { target -> ({ vm.editStep(target) }) }
    val planEntry = remember(draft.selectedCatalogId) {
        draft.selectedCatalogId?.let(PersonalizedPlanCatalog::find)
    }

    val pages = ArrayList<ReviewPage>()
    if (SetupWizardBlock.BASICS in targets) {
        pages += ReviewPage(title = "Tú", onEdit = editOf(SetupWizardBlock.BASICS)) { _ ->
            ReviewYouPage(
                alias = draft.name.ifBlank { null },
                ageLabel = draft.ageYears?.let { "$it años" },
                sexMark = reviewGenderMarkOf(draft),
                sexLabel = reviewSexLabelOf(draft),
                bodyLine = reviewBodyLine(draft),
            )
        }
    }
    if (SetupWizardBlock.TRAINING in targets) {
        val program = state.programPreview
        val week = program?.let { SetupPlanReveals.weekOf(it) }.orEmpty()
        val trainingLine = program?.let {
            reviewTrainingLine(
                days = week.size,
                minutes = draft.minutesPerSession ?: longestSessionMinutes(it),
                mainDay = week.firstOrNull { day -> day.isMain }?.day,
            )
        }
        pages += ReviewPage(title = "Entreno", onEdit = editOf(SetupWizardBlock.TRAINING)) { settled ->
            ReviewTrainingPage(
                later = draft.programRoute == SetupProgramRoute.LATER,
                card = reviewPlanCard(state, planEntry),
                trainingLine = trainingLine,
                placesLine = reviewPlacesMaterialLine(draft),
                competitionLine = reviewCompetitionLine(draft),
                settled = settled,
            )
        }
    }
    if (SetupWizardBlock.NUTRITION in targets) {
        val plan = state.nutritionPreparation?.plan ?: state.nutritionPlanPreview
        val tuning = nutritionPlanTuningOf(state)
        val goal = nutritionGoalOf(state)
        val goalDate = nutritionGoalNoteOf(state)
        // Con meta de peso activa (Definir o Volumen) se dice el objetivo aunque no haya fecha; la fecha solo si la calcula el motor.
        val goalActive = draft.nutritionDraft?.let { nutritionGoalActive(it.direction, nutritionTargetKgOf(it)) } == true
        pages += ReviewPage(title = "Nutrición", onEdit = editOf(SetupWizardBlock.NUTRITION)) { _ ->
            ReviewNutritionPage(
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
                goalDateLine = goalDate,
            )
        }
    }
    if (SetupWizardBlock.RINGS in targets) {
        pages += ReviewPage(title = "RINGS", onEdit = editOf(SetupWizardBlock.RINGS)) { _ ->
            ReviewRingsPage(values = ringsTowerValues(state.ringsCoveragePreview))
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReviewCarousel(pages = pages)
        ReviewNotices(
            state = state,
            planEntry = planEntry,
            nutritionPage = SetupWizardBlock.NUTRITION in targets,
            ringsPage = SetupWizardBlock.RINGS in targets,
        )
        if (state.requiresActivationConfirmation) {
            ReviewConfirmation(
                title = "Confirmo la activación",
                body = activationBody(state),
                confirmed = draft.confirmActivation,
                onToggle = { vm.confirmActivation(!draft.confirmActivation) },
            )
        }
        if (state.fixedRecipeDiffers()) {
            ReviewConfirmation(
                title = "Confirmo la rotación y la duración reales",
                body = "La receta fija propone unos días y unos minutos distintos a los que declaraste.",
                confirmed = draft.acceptFixedRecipeDifference,
                onToggle = { vm.acceptFixedRecipeDifference(!draft.acceptFixedRecipeDifference) },
            )
        }
    }
}

// ─── Carrusel ────────────────────────────────────────────────────────────────

/**
 * Una página del carrusel: su nombre («Entreno», también en el «Editar» de TalkBack), su «Editar» (null si no hay a dónde
 * volver) y su contenido. El contenido recibe `settled`: si la página ya está asentada en pantalla (para animar su símbolo).
 */
internal class ReviewPage(
    val title: String,
    val onEdit: (() -> Unit)?,
    val content: @Composable (settled: Boolean) -> Unit,
)

/**
 * Alto del carrusel con la letra normal. La página de Nutrición (anillos, legenda, objetivo y fecha) es la más alta: con
 * letra grande crece en proporción para que nada quede cortado.
 */
private val CarouselHeight = 420.dp

/** Lado del símbolo de la hoja (programa aún sin armar). */
private val SymbolSize = 72.dp

/** Anillos de macros y torre de RINGS. */
private val MacroRingsSize = 200.dp
private val TowerHeight = 160.dp

/** Alto de la banda de la portada del programa en Entreno: ancho completo, con el arte del objetivo a la derecha. */
private val PlanBandHeight = 168.dp

/**
 * Carrusel de la revisión: una página por bloque, a toda la anchura de la pantalla, con el indicador debajo. [initialPage]
 * solo cambia dónde empieza (las pruebas y la hoja de render lo usan para ver cada página).
 */
@Composable
internal fun ReviewCarousel(
    pages: List<ReviewPage>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pages.lastIndex),
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fullBleed(WizardSpacing.gutter)
                .fillMaxWidth()
                .height(CarouselHeight * fontScale)
                .testTag("review-carousel"),
            verticalAlignment = Alignment.Top,
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = WizardSpacing.gutter)
                    .testTag("review-page-$index"),
            ) {
                ReviewPageHeader(title = page.title, onEdit = page.onEdit)
                Spacer(Modifier.height(12.dp))
                page.content(pagerState.settledPage == index)
            }
        }
        PagerDots(
            count = pages.size,
            current = pagerState.currentPage,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/** Estira el contenido a toda la anchura de la pantalla: deshace el margen lateral (el gutter) de la página larga. */
private fun Modifier.fullBleed(gutter: Dp): Modifier = layout { measurable, constraints ->
    val bleed = gutter.roundToPx()
    val width = constraints.maxWidth + 2 * bleed
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
}

/** Título de la página en mayúsculas pequeñas y, a la derecha, «Editar» con 48 dp de área de toque. */
@Composable
private fun ReviewPageHeader(title: String, onEdit: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WizardSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
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
                    .semantics { contentDescription = "Editar $title" },
            ) {
                Text(text = "Editar", style = WizardTypography.controlLabel, color = WizardColors.text)
            }
        }
    }
}

// ─── Páginas ─────────────────────────────────────────────────────────────────

/** Tú: el alias grande; debajo, el símbolo del sexo elegido con la edad y, en una línea pequeña, lo medido. */
@Composable
internal fun ReviewYouPage(
    alias: String?,
    ageLabel: String?,
    sexMark: WizardGenderMark?,
    sexLabel: String?,
    bodyLine: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = alias ?: "Sin declarar",
            style = WizardTypography.heroTitle,
            color = if (alias == null) WizardColors.textFaint else WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (sexMark != null) {
                WizardGenderGlyph(mark = sexMark, color = WizardColors.text, modifier = Modifier.size(44.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ageLabel?.let { Text(text = it, style = WizardTypography.controlLabel, color = WizardColors.text) }
                sexLabel?.let { Text(text = it, style = WizardTypography.note, color = WizardColors.textMuted) }
            }
        }
        bodyLine?.let { Text(text = it, style = WizardTypography.note, color = WizardColors.textMuted) }
    }
}

/**
 * Entreno: la portada del programa a todo el ancho, como banda (ella trae el arte del objetivo y el nombre); debajo, los días
 * por minutos con la sesión principal, los lugares y la competición. Si el programa se arma más adelante, la hoja con lápiz.
 */
@Composable
internal fun ReviewTrainingPage(
    later: Boolean,
    card: PlanCardModel?,
    trainingLine: String?,
    placesLine: String?,
    competitionLine: String?,
    settled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (later) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ReviewSheetSymbol(settled = settled)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Lo armarás más adelante", style = WizardTypography.controlLabel, color = WizardColors.text)
                    Text(
                        text = "No creamos un programa ahora. Podrás hacerlo desde cero cuando quieras.",
                        style = WizardTypography.note,
                        color = WizardColors.textMuted,
                    )
                }
            }
        } else {
            // La portada a todo el ancho, como banda: ella trae el arte del objetivo y el nombre del programa.
            if (card != null) {
                // Con letra grande la banda crece: la línea de abajo (días, minutos y nivel) no se corta con «…».
                PlanCover(
                    model = card,
                    modifier = Modifier.fillMaxWidth().height(PlanBandHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                )
            } else {
                Text(text = "Programa pendiente", style = WizardTypography.note, color = WizardColors.textMuted)
            }
            trainingLine?.let { Text(text = it, style = WizardTypography.controlLabel, color = WizardColors.text) }
        }
        placesLine?.let { Text(text = it, style = WizardTypography.note, color = WizardColors.textMuted) }
        competitionLine?.let { Text(text = it, style = WizardTypography.note, color = WizardColors.text) }
    }
}

/**
 * Nutrición: los anillos de macros con las kcal de la base diaria (media) en el centro y los gramos debajo; si hay meta con
 * fecha, el objetivo. Sin afinado (pauta profesional) se enseñan solo las cifras; en solo registro, ni anillos ni cifras.
 */
@Composable
internal fun ReviewNutritionPage(
    trackingOnly: Boolean,
    kcal: Int?,
    proteinG: Int?,
    carbsG: Int?,
    fatG: Int?,
    fractions: MacroRingFractions?,
    goalLine: String?,
    goalDateLine: String?,
) {
    if (trackingOnly) {
        Text(
            text = "Registrarás tus comidas sin objetivo de calorías.",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
        )
        return
    }
    if (kcal == null) {
        Text(text = "Plan nutricional pendiente.", style = WizardTypography.note, color = WizardColors.textMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (fractions != null) {
            ReviewMacroRings(kcal = kcal, fractions = fractions, proteinG = proteinG, carbsG = carbsG, fatG = fatG)
        } else {
            Text(text = "$kcal kcal", style = WizardTypography.controlValue, color = WizardColors.text)
        }
        ReviewMacroLegend(proteinG = proteinG, carbsG = carbsG, fatG = fatG)
        goalLine?.let { Text(text = it, style = WizardTypography.controlLabel, color = WizardColors.text) }
        goalDateLine?.let { Text(text = it, style = WizardTypography.note, color = WizardColors.textMuted) }
    }
}

/** RINGS: la torre con los valores de «Tus RINGS» y una línea por anillo; sin valor, «sin calibrar», nunca un 100 %. */
@Composable
internal fun ReviewRingsPage(values: TowerRingValues) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KpknTowerRings(values = values, modifier = Modifier.fillMaxWidth().height(TowerHeight))
        ReviewRingLine(channel = RingChannel.ENERGIA, name = "Energía", percent = values[RingChannel.ENERGIA])
        ReviewRingLine(channel = RingChannel.MUSCULOS, name = "Músculos", percent = values[RingChannel.MUSCULOS])
        ReviewRingLine(channel = RingChannel.COLUMNA, name = "Columna", percent = values[RingChannel.COLUMNA])
    }
}

/** La hoja con lápiz del programa que se arma más adelante; con movimiento reducido, su cuadro final. */
@Composable
private fun ReviewSheetSymbol(settled: Boolean) {
    val reduced = wizardReducedMotion()
    val foreground = rememberEntrenoForeground()
    val clock = rememberSymbolClock(active = !reduced && foreground && settled)
    SymbolCanvas(
        art = programSourceArtOf(ProgramSource.LATER, reduced),
        selected = true,
        clock = clock,
        reducedMotion = reduced,
        modifier = Modifier.size(SymbolSize).semantics { contentDescription = "Programa pendiente" },
    )
}

/** Los anillos de macros: arrancan en cero y barren hasta su valor al entrar; con movimiento reducido, de golpe. */
@Composable
private fun ReviewMacroRings(kcal: Int, fractions: MacroRingFractions, proteinG: Int?, carbsG: Int?, fatG: Int?) {
    val reduced = wizardReducedMotion()
    var appeared by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { appeared = true }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
    }
}

/** Gramos de cada macro en la leyenda: proteína, hidratos y grasas con su tinta de marca. */
@Composable
private fun ReviewMacroLegend(proteinG: Int?, carbsG: Int?, fatG: Int?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ReviewMacroLegendItem(label = "Proteína", grams = proteinG, color = MacroColors.protein)
        ReviewMacroLegendItem(label = "Hidratos", grams = carbsG, color = MacroColors.carbs)
        ReviewMacroLegendItem(label = "Grasas", grams = fatG, color = MacroColors.fat)
    }
}

@Composable
private fun ReviewMacroLegendItem(label: String, grams: Int?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(color))
        Text(text = gramsText(grams), style = WizardTypography.controlLabel, color = WizardColors.text)
        Text(text = label, style = WizardTypography.note, color = WizardColors.textMuted)
    }
}

/** Una línea de la torre: el punto del anillo, su nombre y su valor («Músculos 64 %» o «Columna sin calibrar»). */
@Composable
private fun ReviewRingLine(channel: RingChannel, name: String, percent: Int?) {
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

// ─── Avisos y confirmaciones ─────────────────────────────────────────────────

/**
 * Avisos bajo el carrusel, sin tarjeta: lo que el programa pide saber antes de activarlo, y el estado de la nutrición y de
 * RINGS. Cada aviso dice lo que pasa; ninguno decide por la persona.
 */
@Composable
private fun ReviewNotices(
    state: SetupWizardState,
    planEntry: CatalogEntry?,
    nutritionPage: Boolean,
    ringsPage: Boolean,
) {
    val draft = state.draft
    val context = draft.stepContext()
    var showPlanInfo by rememberSaveable(draft.draftId, draft.commitId) { mutableStateOf(false) }
    if (draft.programRoute != SetupProgramRoute.LATER) {
        val program = state.programPreview
        if (program == null) {
            SetupFormCaption(
                state.previewError
                    ?: if (state.isPreviewLoading) "Preparando el programa…" else "Todavía no hay un programa preparado.",
            )
        } else {
            timeReviewNote(draft, state.programSessionMinutes)?.let { note -> SetupFormCaption(note) }
            // Una sesión que cae en un día cuyo lugar no tiene su material: el aviso persiste, porque es esa semana la que se activa.
            state.weekLayout?.placeConflicts?.forEach { conflict ->
                SetupFormCaption(
                    placeConflictLine(conflict),
                    modifier = Modifier.testTag("review-place-conflict-${conflict.sessionId}"),
                )
            }
            // B-03: solo lectura del informe ya calculado (planes propios); no cambia el programa.
            planReviewNotes(state.previewReport).forEach { note -> SetupFormCaption(note) }
            if (planEntry != null) {
                // Ya está elegido: la hoja es de solo lectura (sin botón primario).
                TextButton(onClick = { showPlanInfo = true }, modifier = Modifier.testTag("review-plan-info")) {
                    Text(text = "Ver cómo funciona", color = WizardColors.text)
                }
                if (showPlanInfo) {
                    // C.P6: los planes sin receta enseñan la semana REAL del programa que se va a activar.
                    val readyWeek = remember(program) { reviewReadyWeek(program) }
                    PlanInfoSheet(
                        entry = planEntry,
                        mode = PlanInfoMode.READ_ONLY,
                        readyWeek = readyWeek,
                        onDismiss = { showPlanInfo = false },
                        onOpenConcept = LocalOpenConcept.current,
                    )
                }
            }
        }
    }

    if (nutritionPage && context.nutritionStartChoice != "tracking_only") {
        val preparation = state.nutritionPreparation
        val plan = preparation?.plan ?: state.nutritionPlanPreview
        if (context.nutritionProfessional) {
            SetupFormCaption("Modo: pauta indicada por un profesional.")
        } else {
            val status = preparation?.let { preparationStatusLabel(it.status) }
                ?: if (plan != null) "Vista previa del plan" else "Pendiente"
            SetupFormCaption("Estado: $status")
        }
        val reparto = preparation?.takeIf { it.days.isNotEmpty() }
            ?.let { "Reparto: ${SpanishPlurals.days(it.days.size)} con objetivo" }
        val distribution = preparation?.distributionStatus?.let { "Distribución: ${distributionLabel(it)}" }
        listOfNotNull(reparto, distribution).takeIf { it.isNotEmpty() }?.let { lines ->
            SetupFormCaption(lines.joinToString(" · "))
        }
        val error = preparation?.errors?.values?.firstOrNull() ?: state.nutritionErrors.values.firstOrNull()
        if (error != null) SetupFormCaption(error)
    }

    if (ringsPage) {
        val coverage = state.ringsCoveragePreview ?: RingsCoverage.NO_DATA
        val sources = listOf(coverage.muscular, coverage.system, coverage.structure)
            .filter { it.hasData }
            .map { it.label }
            .distinct()
        SetupFormCaption(
            when {
                sources.isNotEmpty() -> "Procedencia por canal: ${sources.joinToString(" · ")}."
                state.ringsPreviewLoading -> "Preparando la vista previa de RINGS…"
                else -> "Sin datos de RINGS todavía: no se afirma ningún porcentaje."
            },
        )
    }
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

// ─── Lecturas del estado (puras) ─────────────────────────────────────────────

/**
 * El bloque → su primer paso de edición en la ruta (sin hitos). Es el destino de «Editar» de su página. Función pura para
 * probarla sin montar el ViewModel.
 */
internal fun reviewBlockTargets(route: List<SetupStepId>): Map<SetupWizardBlock, SetupStepId> {
    val targets = LinkedHashMap<SetupWizardBlock, SetupStepId>()
    for (step in route) {
        if (SetupStepGraph.isMilestone(step)) continue
        val block = SetupStepGraph.blockOf(step)
        if (!targets.containsKey(block)) targets[block] = step
    }
    return targets
}

/** El símbolo del sexo elegido: el mismo que dibuja su paso. Null si la respuesta no tiene símbolo («No lo sé», hormonal). */
internal fun reviewGenderMarkOf(draft: SetupWizardDraft): WizardGenderMark? =
    when (draft.selectedValues(SetupStepId.EQUATION_SEX).firstOrNull()) {
        "female" -> WizardGenderMark.FEMALE
        "male" -> WizardGenderMark.MALE
        "trans_male" -> WizardGenderMark.TRANS_MALE
        "trans_female" -> WizardGenderMark.TRANS_FEMALE
        null -> when (draft.nutritionDraft?.equationSex) {
            EerSex.FEMALE -> WizardGenderMark.FEMALE
            EerSex.MALE -> WizardGenderMark.MALE
            else -> null
        }
        else -> null
    }

/** La respuesta del sexo en palabras para la revisión (la misma que enseñaba su fila). */
internal fun reviewSexLabelOf(draft: SetupWizardDraft): String? =
    draft.selectedValues(SetupStepId.EQUATION_SEX).firstOrNull()?.let { value ->
        when (value) {
            "female" -> "Mujer"
            "male" -> "Hombre"
            "trans_male" -> "Hombre trans"
            "trans_female" -> "Mujer trans"
            "unknown" -> "No lo sé"
            // Las tres respuestas hormonales dicen lo mismo que la fila-resumen del paso.
            else -> equationSexHormonalSummary(value) ?: value
        }
    } ?: when (draft.nutritionDraft?.equationSex) {
        EerSex.FEMALE -> "Femenino"
        EerSex.MALE -> "Masculino"
        EerSex.AVERAGE -> "Promedio"
        null -> null
    }

/** El porcentaje de grasa que se enseña en la línea de medidas: solo si hay un número con origen; «No lo sé» no lo tiene. */
internal fun reviewBodyFatPercentOf(draft: SetupWizardDraft): Double? = when (draft.bodyFatSource) {
    SetupBodyFatSource.MEASURED, SetupBodyFatSource.VISUAL_ESTIMATE -> draft.bodyFatPercent
    SetupBodyFatSource.UNKNOWN -> null
    null -> if (draft.bodyFatState() == SetupBodyFatState.ON_FILE) {
        draft.bodyFatPercent ?: draft.importedBodyFatPercent
    } else {
        null
    }
}

/** «176 cm · 78 kg · 18 % de grasa»: lo que se midió, en una línea; lo que falte, fuera. Null si no hay nada. */
internal fun reviewBodyLine(draft: SetupWizardDraft): String? = listOfNotNull(
    draft.heightCm?.let { "${it.toInt()} cm" },
    draft.weightKg?.let { WizardWeightScale.formatWithUnit(it, wizardMassUnitOf(draft)) },
    reviewBodyFatPercentOf(draft)?.let { "${formatPercent(it)} % de grasa" },
).joinToString(" · ").ifEmpty { null }

/**
 * «4 días × 60 min · tu sesión principal el jueves». Null sin sesiones. [minutes] y [mainDay] son opcionales: sin ellos la línea
 * dice solo los días o los días y el tiempo.
 */
internal fun reviewTrainingLine(days: Int, minutes: Int?, mainDay: Int?): String? {
    if (days <= 0) return null
    val head = listOfNotNull(SpanishPlurals.days(days), minutes?.takeIf { it > 0 }?.let { "$it min" }).joinToString(" × ")
    val main = mainDay?.let { WEEKDAY_LABELS.getOrNull(it) }?.takeIf { it.isNotEmpty() }?.lowercase()
    return if (main == null) head else "$head · tu sesión principal el $main"
}

/**
 * La competición que cuenta para el programa: «Compites el 14 de marzo de 2027 · Nacional Open»; sin nombre, sin la parte final.
 * «Sí, sin fecha» dice «Compites, aún sin fecha». Null si no compite (o el paso no está en la ruta).
 */
internal fun reviewCompetitionLine(draft: SetupWizardDraft): String? = when (draft.activeCompetition()) {
    CompetitionAnswer.WITH_DATE -> {
        val date = CompetitionRules.parseMeetDate(draft.activeMeetDate())
        if (date == null) {
            "Compites, aún sin fecha"
        } else {
            val name = draft.activeMeetName().trim()
            "Compites el ${CompetitionRules.dateLabel(date)}" + if (name.isEmpty()) "" else " · $name"
        }
    }
    CompetitionAnswer.NO_DATE -> "Compites, aún sin fecha"
    CompetitionAnswer.NO, null -> null
}

/** Lugares y material en una línea («Gimnasio y casa · Barra y discos, Rack +12»); null mientras no se hayan declarado. */
internal fun reviewPlacesMaterialLine(draft: SetupWizardDraft): String? =
    listOfNotNull(placesSummaryText(draft.trainingPlaces), materialReviewValue(draft)).joinToString(" · ").ifEmpty { null }

/** Una línea de la torre: «Músculos 64 %», o «Columna sin calibrar» si el canal no tiene valor. Nunca un 100 % de relleno. */
internal fun ringLineText(name: String, percent: Int?): String =
    if (percent == null) "$name sin calibrar" else "$name $percent %"

/**
 * La portada del programa: la misma ficha que enseña PLAN (`planReveals`). Sin ella (la lista no llegó a esta sesión), una mínima
 * con el nombre y los días de la semana tipo. Null sin programa previsualizado.
 */
internal fun reviewPlanCard(state: SetupWizardState, planEntry: CatalogEntry?): PlanCardModel? {
    val draft = state.draft
    state.planReveals.firstOrNull { it.planId == draft.selectedCatalogId }?.let { return it.toCardModel() }
    val program = state.programPreview ?: return null
    val days = SetupPlanReveals.weekOf(program).size
    return PlanCardModel(
        id = draft.selectedCatalogId ?: program.name,
        title = planReviewValue(planEntry, program.name) ?: program.name,
        kicker = "",
        blurb = "",
        profile = draft.goalProfile,
        daysLabel = if (days > 0) SpanishPlurals.days(days) else "",
        minutesLabel = "",
        levelLabel = "",
    )
}

/** Gramos de un macro para la leyenda y la descripción: «150 g», o «sin datos» si no hay cifra. */
private fun gramsText(grams: Int?): String = grams?.let { "$it g" } ?: "sin datos"

// ─── Ayudantes puros del archivo (con pruebas) ───────────────────────────────

/**
 * Copy de la confirmación según el alcance real: solo registro no promete plan.
 */
internal fun activationBody(state: SetupWizardState): String {
    val later = state.draft.programRoute == SetupProgramRoute.LATER
    val trackingOnly = state.draft.stepContext().nutritionStartChoice == "tracking_only"
    return when {
        later && !state.draft.includeNutrition ->
            "No se activa ningún programa. Podrás armar el tuyo más adelante."
        later && trackingOnly ->
            "No se activa ningún programa. Podrás armar el tuyo más adelante. En solo registro no hay plan nutricional que activar."
        later ->
            "El programa lo armarás más adelante. El plan nutricional se activa al confirmar."
        !state.draft.includeNutrition -> "El programa se activa al confirmar."
        trackingOnly ->
            "El programa se activa al confirmar. En solo registro no hay plan nutricional que activar."
        else -> "El programa y el plan nutricional se activan juntos al confirmar."
    }
}

/**
 * Valor de la fila «Grasa corporal». Si quien vuelve no actuó en el paso, se
 * muestra el dato previo de Ajustes de forma honesta («guardada en Ajustes»),
 * sin presentarlo como una respuesta nueva; sin nada: null («Sin declarar»).
 */
internal fun bodyFatReviewValue(draft: SetupWizardDraft): String? = when (val source = draft.bodyFatSource) {
    SetupBodyFatSource.UNKNOWN -> "No lo sé"
    SetupBodyFatSource.MEASURED, SetupBodyFatSource.VISUAL_ESTIMATE ->
        draft.bodyFatPercent?.let { percent ->
            "${formatPercent(percent)} % · " +
                if (source == SetupBodyFatSource.MEASURED) "medido" else "estimación visual"
        }
    null -> if (draft.bodyFatState() == SetupBodyFatState.ON_FILE) {
        (draft.bodyFatPercent ?: draft.importedBodyFatPercent)
            ?.let { "Guardada en Ajustes: ${formatPercent(it)} %" }
    } else {
        null
    }
}

/**
 * B-03 · Aviso de volumen alto: «Volumen alto en Glúteos: 17 series por semana
 * (recomendado 16). Es un exceso pequeño y aceptable.» Solo existe si el plan
 * propio supera el máximo recomendado dentro de la tolerancia aprobada.
 */
internal fun highVolumeReviewText(notice: HighVolumeNotice): String =
    "Volumen alto en ${notice.muscle}: ${VolumeSoftBand.formatSets(notice.weeklySets)} series principales por semana " +
        "(recomendado ${notice.recommendedSets}). Es un exceso pequeño y aceptable."

/**
 * B-03 · Lo que la revisión cuenta del plan: primero los avisos de volumen alto
 * (`report.highVolume`, el máximo semanal) y luego las notas del plan en lenguaje
 * llano. Sin informe (p. ej. PHUL/PHAT, que no lo traen) no se muestra nada.
 */
internal fun planReviewNotes(report: PersonalizationReport?): List<String> =
    report?.let { it.highVolume.map(::highVolumeReviewText) + it.planNotes }.orEmpty()

/** «1 sesión · 3 ejercicios» de la vista previa de la primera semana. */
internal fun previewSessionsSummary(sessions: Int, exercises: Int): String =
    "${SpanishPlurals.sessions(sessions)} · ${SpanishPlurals.exercises(exercises)}"

/** «6 sesiones en 4 semanas» del desplegable semana a semana. */
internal fun allSessionsSummary(sessions: Int, weeks: Int): String =
    "${SpanishPlurals.sessions(sessions)} en ${SpanishPlurals.weeks(weeks)}"

/**
 * C.P7 · Valor de la fila «Plan»: el título editorial del plan elegido del catálogo
 * (sin «Plan de …» ni ids). Sin plan del catálogo (programa desde cero, id que ya no
 * existe) conserva el nombre del programa de la vista previa; sin nombre, null
 * («Sin declarar»).
 */
internal fun planReviewValue(entry: CatalogEntry?, programName: String): String? =
    entry?.displayName ?: programName.ifBlank { null }

/**
 * Entreno v2 · el programa que se activa en una línea: «4 días · ~60 min por sesión · Gimnasio y En casa». Los días salen
 * de la primera semana de entreno del programa previsualizado; los minutos, de su sesión más larga con el estimador común
 * ([longestSessionMinutes]: el mismo número con el que la revisión decide si cabe); el lugar, del de cada sesión
 * (`Session.placeId`) o, si el programa no lo dice, de los lugares declarados. Null sin sesiones.
 */
internal fun programReviewDetail(program: Program, draft: SetupWizardDraft): String? {
    val week = SetupPlanReveals.weekOf(program)
    if (week.isEmpty()) return null
    val places = week.mapNotNull { it.place }.distinct().ifEmpty { draft.trainingPlaces.toList() }
        .sortedBy { it.ordinal }
        .map { it.label }
    return listOfNotNull(
        SpanishPlurals.days(week.size),
        longestSessionMinutes(program)?.takeIf { it > 0 }?.let { "~$it min por sesión" },
        places.takeIf { it.isNotEmpty() }?.let { names ->
            if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " y " + names.last()
        },
    ).joinToString(" · ")
}

/**
 * La primera semana REAL del programa de la vista previa, para la hoja «Cómo funciona» de la revisión (C.P6): sin
 * calentamientos, con el día de cada sesión y las series redactadas como las de una receta. Misma construcción que
 * `SetupWizardViewModel.readyWeekSnapshotFor` (la de las tarjetas del paso PLAN). Null si el programa no trae ninguna
 * sesión: la hoja dice entonces «se genera con tus días, tu tiempo y tu material».
 */
internal fun reviewReadyWeek(program: Program): ReadyWeekSnapshot? =
    ReadyWeekSnapshot.from(
        program = program,
        names = { configurationId -> catalogConfigurationDisplayName(configurationId) },
        equipmentOf = { configurationId -> CompositionMetadataHolder.current?.metadata(configurationId)?.equipmentId },
    ).takeIf { snapshot -> snapshot.sessions.isNotEmpty() }

/**
 * El material en una línea para la revisión: «Solo peso corporal» o los tres primeros implementos y cuántos más
 * («Barra y discos, Rack, Banco +12»); null mientras no se haya declarado.
 */
internal fun materialReviewValue(draft: SetupWizardDraft): String? {
    if (draft.trainingOptions.availability == null) return null
    val symbols = draft.selectedEquipmentSymbols()
    val implements = symbols.filter { it != EquipmentSymbolId.BODYWEIGHT_ONLY }.sortedBy { it.ordinal }
    if (implements.isEmpty()) return EquipmentSymbolId.BODYWEIGHT_ONLY.label
    val shown = implements.take(MATERIAL_REVIEW_NAMES).joinToString(", ") { it.label }
    val rest = implements.size - MATERIAL_REVIEW_NAMES
    return if (rest > 0) "$shown +$rest" else shown
}

private const val MATERIAL_REVIEW_NAMES = 3

/** «3 días · lun, mié, vie · 60 min»: los días elegidos y el tiempo por sesión; null mientras falten los dos. */
internal fun daysAndTimeReviewValue(draft: SetupWizardDraft): String? =
    listOfNotNull(
        weekdaysSummaryText(draft.selectedWeekdays),
        draft.minutesPerSession?.let { minutes -> "$minutes min" },
    ).joinToString(" · ").ifEmpty { null }

private val WEEKDAY_LABELS = listOf("", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

private fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value).replace('.', ',')

/** Estado del plan de nutrición en la revisión (el mismo texto que su fila-resumen). */
private fun preparationStatusLabel(status: NutritionPlanPreparationStatus): String = when (status) {
    NutritionPlanPreparationStatus.READY -> "Lista para activar"
    NutritionPlanPreparationStatus.BLOCKED_EQUATION -> "Faltan datos de la ecuación"
    NutritionPlanPreparationStatus.SELF_DEFINED_MANUAL -> "Revisa tus valores"
    NutritionPlanPreparationStatus.TRACKING_ONLY -> "Solo registro"
}

private fun distributionLabel(status: NutritionDistributionStatus): String = when (status) {
    NutritionDistributionStatus.VARIABLE -> "Variable según el gasto previsto"
    NutritionDistributionStatus.UNIFORM_BY_CHOICE -> "Uniforme por elección"
    NutritionDistributionStatus.UNIFORM_BY_LIMITS -> "Uniforme por límites"
    NutritionDistributionStatus.PROVISIONAL_UNIFORM -> "Uniforme provisional (gasto sin estimar)"
    NutritionDistributionStatus.KEPT_PREVIOUS -> "Reparto anterior conservado"
}
