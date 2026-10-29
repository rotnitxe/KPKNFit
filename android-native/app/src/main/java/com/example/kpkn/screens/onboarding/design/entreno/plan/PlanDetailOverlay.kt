package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.SwapKind
import com.example.kpkn.domain.training.SwapOption
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.layout.slotOrder
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * El detalle de un programa: una hoja a pantalla completa (un Dialog con desenfoque) con, en este orden y todo sobre la
 * página negra, sin cajas: la portada (a sangre, con parallax al hacer scroll), la descripción, los ejercicios
 * principales, la estructura en bloques, la semana tipo, por qué este programa, las notas honestas y la atribución. Fijo
 * abajo: la frase «Podrás modificarlo libremente después.» y el botón de elegir.
 */

/** Marcas de prueba: la hoja, su botón principal y su botón de cerrar. */
const val PLAN_DETAIL_TAG = "setup-plan-detail"
internal const val PLAN_DETAIL_CHOOSE_TAG = "setup-plan-detail-choose"
internal const val PLAN_DETAIL_CLOSE_TAG = "setup-plan-detail-close"
internal const val PLAN_DETAIL_SIBLING_TAG = "setup-plan-detail-sibling"

/** Alto del cuerpo de la cabecera (sin la barra de estado) y franja superior reservada al botón de cerrar. */
private val HEADER_BODY = 232.dp
private val HEADER_STRIP = 44.dp

/** Líneas de la descripción antes de «Ver más». */
private const val DESCRIPTION_LINES = 4

/** Título de cada sección: Syne ExtraBold. */
internal val SECTION_STYLE = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.1).sp,
)

/**
 * La hoja de detalle de un programa.
 *
 * - [selected]: si es el programa elegido; el botón pasa a «Programa elegido» (en verde).
 * - [onChoose]: «Elegir este programa» (quien llama elige y suele cerrar la hoja).
 * - [onClose]: la X de arriba y «atrás»; la hoja se desvanece antes de avisar.
 * - [weekStartDay]: primer día de la semana de la persona (1 = lunes); «Tu semana» empieza ahí.
 * - [siblingLabel] y [onSibling]: la acción de texto que lleva a la otra versión de la familia (PHUL original ↔ adaptada);
 *   sin ellas no se pinta.
 */
@Composable
fun PlanDetailOverlay(
    model: PlanDetailModel,
    selected: Boolean,
    onChoose: () -> Unit,
    onClose: () -> Unit,
    weekStartDay: Int = 1,
    siblingLabel: String? = null,
    onSibling: (() -> Unit)? = null,
    phases: List<PlanPhaseModel> = emptyList(),
    exercises: Map<Int, List<SetupPlanRevealExercise>> = emptyMap(),
    swapOptionsOf: (String) -> List<SwapOption> = { emptyList() },
    onSwap: (String, String) -> Unit = { _, _ -> },
    onUndoSwap: (String) -> Unit = {},
) {
    PlanDetailOverlay(
        model, selected, onChoose, onClose, wizardReducedMotion(), weekStartDay, siblingLabel, onSibling, phases,
        exercises = exercises, swapOptionsOf = swapOptionsOf, onSwap = onSwap, onUndoSwap = onUndoSwap,
    )
}

/**
 * La misma ficha fuera del asistente (lienzo Arreglos v1): un plan del catálogo (con su pie fijo «Podrás modificarlo libremente
 * después.» y [PlanFichaContent.primaryLabel]) o «Sobre el programa» de un programa propio (en modo lectura: sin
 * [PlanFichaContent.primaryLabel] no hay pie). [onPrimary] es el botón del pie; [onSecondary], la acción secundaria del final.
 */
@Composable
fun PlanFichaOverlay(
    ficha: PlanFicha,
    onClose: () -> Unit,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
) {
    PlanFichaOverlay(ficha, onClose, wizardReducedMotion(), onPrimary, onSecondary)
}

/** La ficha reutilizada con el «reducir movimiento» decidido por quien llama (pruebas). */
@Composable
internal fun PlanFichaOverlay(
    ficha: PlanFicha,
    onClose: () -> Unit,
    reduced: Boolean,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
) {
    // Sin acción no hay botón: la ficha queda en modo lectura.
    val content = if (onPrimary == null) ficha.content.copy(primaryLabel = null) else ficha.content
    val secondary = onSecondary?.takeIf { content.secondaryLabel != null }
    PlanDetailOverlay(
        model = ficha.detail,
        selected = false,
        onChoose = onPrimary ?: {},
        onClose = onClose,
        reduced = reduced,
        ficha = content,
        onSecondary = secondary,
    )
}

/** La hoja con el «reducir movimiento» decidido por quien llama (pruebas y vista previa de depuración). */
@Composable
internal fun PlanDetailOverlay(
    model: PlanDetailModel,
    selected: Boolean,
    onChoose: () -> Unit,
    onClose: () -> Unit,
    reduced: Boolean,
    weekStartDay: Int = 1,
    siblingLabel: String? = null,
    onSibling: (() -> Unit)? = null,
    phases: List<PlanPhaseModel> = emptyList(),
    exercises: Map<Int, List<SetupPlanRevealExercise>> = emptyMap(),
    swapOptionsOf: (String) -> List<SwapOption> = { emptyList() },
    onSwap: (String, String) -> Unit = { _, _ -> },
    onUndoSwap: (String) -> Unit = {},
    ficha: PlanFichaContent? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    var leaving by remember { mutableStateOf(false) }
    val closeNow by rememberUpdatedState(onClose)
    // Un solo aviso por cierre: el desvanecido tarda unos milisegundos y un segundo toque no debe repetirlo.
    val leave: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                if (!reduced) shown.animateTo(0f, tween(200))
                closeNow()
                // Si la hoja sigue aquí, el aviso no la retiró: vuelve a mostrarse en lugar de quedar invisible y bloqueando.
                delay(700)
                leaving = false
                shown.animateTo(1f, tween(200))
            }
        }
    }
    BlurOverlayDialog(onDismissRequest = leave, dismissOnBack = true, shown = { shown.value }) { blur ->
        LaunchedEffect(Unit) { if (!reduced) shown.animateTo(1f, tween(460)) }
        DetailSheet(
            model, selected, reduced, blur, { shown.value }, onChoose, leave,
            weekStartDay = weekStartDay, siblingLabel = siblingLabel, onSibling = onSibling, phases = phases,
            exercises = exercises, swapOptionsOf = swapOptionsOf, onSwap = onSwap, onUndoSwap = onUndoSwap,
            ficha = ficha, onSecondary = onSecondary,
        )
    }
}

/**
 * El contenido de la hoja (todo menos la ventana). Está aparte de [PlanDetailOverlay] para poder pintarlo sin la ventana (la
 * vista previa de depuración lo enseña a 360 dp y con la letra al 130 % dentro de la página).
 */
@Composable
internal fun DetailSheet(
    model: PlanDetailModel,
    selected: Boolean,
    reduced: Boolean,
    blur: Boolean,
    shown: () -> Float,
    onChoose: () -> Unit,
    onClose: () -> Unit,
    weekStartDay: Int = 1,
    modifier: Modifier = Modifier,
    siblingLabel: String? = null,
    onSibling: (() -> Unit)? = null,
    phases: List<PlanPhaseModel> = emptyList(),
    exercises: Map<Int, List<SetupPlanRevealExercise>> = emptyMap(),
    swapOptionsOf: (String) -> List<SwapOption> = { emptyList() },
    onSwap: (String, String) -> Unit = { _, _ -> },
    onUndoSwap: (String) -> Unit = {},
    ficha: PlanFichaContent? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val card = model.card
    val accent = goalAccent(card.profile)
    // El pie fijo: en el asistente siempre; en la ficha reutilizada, solo si trae su botón (sin él, modo lectura).
    val showBar = ficha == null || ficha.primaryLabel != null
    // «Ejercicios principales»: los de la ficha o, en el asistente, sus nombres con la imagen de KPKN de la semana tipo.
    val exerciseRows = ficha?.exercises ?: remember(model.mainExercises, exercises) { wizardMainExerciseRows(model.mainExercises, exercises) }
    // «Cambiar» solo en el programa elegido: sus sustituciones viven en el borrador de ese programa.
    val swapState = remember { SwapPanelState() }
    val swaps = remember(exercises, selected, swapOptionsOf, onSwap, onUndoSwap) {
        ExerciseSwaps(byDay = exercises, enabled = selected, optionsOf = swapOptionsOf, onSwap = onSwap, onUndo = onUndoSwap)
    }
    // Atrás cierra primero el panel «Cambiar» y solo después la hoja.
    BackHandler(enabled = swapState.panelOf != null) { swapState.closePanel() }
    // La ficha de un ejercicio va sobre la hoja; al abrirla se cierra el panel «Cambiar», que no puede quedar debajo.
    val info = remember { ExerciseInfoState() }
    val openInfo: (SetupPlanRevealExercise) -> Unit = { exercise ->
        swapState.closePanel()
        info.show(exercise)
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barPx by remember { mutableIntStateOf(0) }
    val headerH = statusTop + HEADER_STRIP + HEADER_BODY
    val headerPx = with(density) { headerH.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .testTag(PLAN_DETAIL_TAG)
            .semantics { paneTitle = card.title }
            .graphicsLayer { alpha = shown() }
            // Sin desenfoque del sistema el velo es del todo opaco (con 0,985 el texto de la página de atrás asomaba unos niveles
            // de gris); con él, 0,84: aquí hay mucho texto.
            .background(OverlayScrim.copy(alpha = if (blur) 0.84f else 1f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            // La cabecera: la portada a sangre. Al abrir crece desde una tarjeta; al hacer scroll sube más despacio que
            // el contenido (parallax) y su ilustración se desplaza un poco más.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerH)
                    .clipToBounds(),
            ) {
                PlanCover(
                    model = card,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = scroll.value * 0.5f
                            val e = shown()
                            val s = lerpF(0.88f, 1f, e)
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            clip = true
                            shape = RoundedCornerShape((28f * (1f - e)).dp)
                        },
                    animate = true,
                    reduced = reduced,
                    parallax = { -(scroll.value / headerPx).coerceIn(0f, 1f) * 0.7f },
                    cornerRadius = 0.dp,
                    topInset = statusTop + HEADER_STRIP,
                    // La ficha reutilizada (catálogo, «Sobre el programa») sigue al lienzo `.pf-cover`: el arte arriba y el título a todo
                    // el ancho abajo, así un nombre corto («Mi PHAT») no se parte junto a la ilustración. El asistente conserva su banda.
                    layout = if (ficha != null) CoverLayout.POSTER else CoverLayout.BANNER,
                )
            }
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val e = seg(shown(), 0.3f, 0.85f)
                        alpha = e
                        translationY = (1f - e) * 36.dp.toPx()
                    },
            ) {
                Spacer(Modifier.height(20.dp))
                if (model.description.isNotBlank()) DescriptionBlock(model.description, accent)
                if (siblingLabel != null && onSibling != null) {
                    SiblingVersionAction(label = siblingLabel, onClick = onSibling)
                }
                ficha?.chips?.takeIf { it.isNotEmpty() }?.let { FichaChips(it) }
                if (exerciseRows.isNotEmpty()) {
                    SectionTitle(PlanCopy.SECTION_EXERCISES)
                    ExerciseList(exerciseRows.take(MAX_MAIN_EXERCISES))
                }
                if (model.blocks.isNotEmpty()) {
                    SectionTitle(PlanCopy.SECTION_STRUCTURE)
                    BlockTimeline(model.blocks, accent)
                }
                if (ficha != null) {
                    // La ficha reutilizada: «Tu semana» día por día (lunes a domingo, cada día se despliega) y sus secciones extra.
                    val note = ficha.weekNote?.takeIf { it.isNotBlank() }
                    if (ficha.days.isNotEmpty() || note != null) {
                        SectionTitle(PlanCopy.SECTION_WEEK)
                        if (ficha.days.isNotEmpty()) {
                            FichaWeek(ficha.days, ficha.openDay, accent, reduced)
                        } else if (note != null) {
                            Text(note, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
                        }
                    }
                    for (section in ficha.sections.filter { it.rows.isNotEmpty() }) {
                        SectionTitle(section.title)
                        FichaRows(section.rows, accent)
                    }
                } else if (model.week.isNotEmpty()) {
                    // Las fases de un programa de varias semanas van encima de «Tu semana», que enseña la primera.
                    PlanPhasesStrip(phases)
                    SectionTitle(PlanCopy.SECTION_WEEK)
                    WeekStrip(model.week, accent, weekStartDay, swaps = swaps, state = swapState, reduced = reduced, onInfo = openInfo)
                }
                if (model.reasons.isNotEmpty()) {
                    SectionTitle(PlanCopy.SECTION_REASONS)
                    ReasonList(model.reasons, accent)
                }
                if (model.notes.isNotEmpty()) {
                    // La ficha reutilizada titula las notas («Ten en cuenta»), como el lienzo; el asistente las deja sin título.
                    if (ficha != null) SectionTitle(PlanCopy.SECTION_NOTES) else Spacer(Modifier.height(28.dp))
                    NoteList(model.notes)
                }
                if (!model.attribution.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    // Con fuente publicada, la atribución abre la fuente original (era «Ver la fuente original» en la hoja vieja).
                    val sourceUrl = model.sourceUrl
                    val uriHandler = LocalUriHandler.current
                    Text(
                        text = if (sourceUrl != null) model.attribution + " ↗" else model.attribution,
                        style = WizardTypography.note,
                        color = WizardColors.textFaint,
                        modifier = if (sourceUrl != null) {
                            Modifier.clickable(role = Role.Button, onClickLabel = "Ver la fuente original") {
                                runCatching { uriHandler.openUri(sourceUrl) }
                            }
                        } else {
                            Modifier
                        },
                    )
                }
                val secondaryLabel = ficha?.secondaryLabel
                if (secondaryLabel != null && onSecondary != null) {
                    SecondaryPill(
                        label = secondaryLabel,
                        onClick = onSecondary,
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .fillMaxWidth()
                            .testTag(PLAN_FICHA_SECONDARY_TAG),
                    )
                }
            }
            // Sin pie (modo lectura), el final solo deja aire sobre la barra de navegación.
            Spacer(Modifier.height(if (showBar) with(density) { barPx.toDp() } + 24.dp else navBottom + 32.dp))
        }
        // Un velo bajo la barra de estado: el contenido que sube no se mezcla con la hora ni con los iconos del sistema.
        // Sólido por toda la barra y solo después se funde (antes se fundía desde arriba y, a la altura de la hora, dejaba
        // pasar el 65 % del texto: «principales» se leía debajo de los iconos).
        val statusFraction = statusTop.value / (statusTop.value + 18f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusTop + 18.dp)
                .graphicsLayer { alpha = (scroll.value / 120f).coerceIn(0f, 1f) }
                .background(
                    Brush.verticalGradient(
                        0f to OverlayScrim.copy(alpha = 0.97f),
                        statusFraction to OverlayScrim.copy(alpha = 0.97f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        CloseButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + 2.dp, end = 10.dp),
        )
        if (showBar) {
            BottomBar(
                selected = selected,
                navBottom = navBottom,
                onChoose = onChoose,
                label = ficha?.primaryLabel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barPx = it.height },
            )
        }
        // El panel «Cambiar» va sobre la hoja y su pie, y bajo la ficha del ejercicio.
        swapState.panelExercise?.takeIf { swapState.panelOf != null }?.let { exercise ->
            SwapSheet(
                exercise = exercise,
                accent = accent,
                options = swapState.options,
                picked = swapState.picked,
                onPick = { swapState.picked = it },
                onConfirm = { configId ->
                    swaps.onSwap(exercise.instanceId, configId)
                    swapState.closePanel()
                },
                onCancel = { swapState.closePanel() },
            )
        }
        // La ficha va la última: cubre la cabecera, el pie y el resto de la hoja.
        ExerciseInfoLayer(state = info, reduced = reduced, topInset = statusTop + HEADER_STRIP)
    }
}

// ---------------------------------------------------------------- piezas fijas

/** La acción de texto que lleva a la otra versión del programa: sin caja, con un objetivo táctil de 48 dp de alto. */
@Composable
private fun SiblingVersionAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .testTag(PLAN_DETAIL_SIBLING_TAG)
            .heightIn(min = 48.dp)
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, style = WizardTypography.cardTitle, color = WizardColors.text)
    }
}

/** La X de arriba a la derecha: un disco de vidrio neutro (blanco muy tenue y un filete uniforme) con su objetivo táctil de 48 dp. */
@Composable
internal fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier, tag: String = PLAN_DETAIL_CLOSE_TAG) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .testTag(tag)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = PlanCopy.CLOSE },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
                .background(WizardColors.glassFill, CircleShape)
                .border(1.dp, WizardColors.glassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val w = 2.dp.toPx()
                drawLine(WizardColors.text, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
                drawLine(WizardColors.text, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
            }
        }
    }
}

/**
 * El pie fijo: una frase amable y el botón principal, sobre un velo que funde el contenido que pasa por debajo. Con [label] (la ficha
 * reutilizada: «Configurar este plan») el botón dice eso y no tiene estado de «elegido».
 */
@Composable
private fun BottomBar(selected: Boolean, navBottom: Dp, onChoose: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val done = selected && label == null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.22f to OverlayScrim,
                    1f to OverlayScrim,
                ),
            )
            .padding(start = 24.dp, end = 24.dp, top = 46.dp, bottom = navBottom + 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(PlanCopy.EDIT_FREELY, style = WizardTypography.note, color = WizardColors.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        PillButton(
            label = label ?: if (done) PlanCopy.PROGRAM_CHOSEN else PlanCopy.CHOOSE_PROGRAM,
            container = if (done) WizardColors.done else WizardColors.cta,
            content = if (done) SymbolPalette.onOk else WizardColors.ctaContent,
            leadingCheck = done,
            onClick = onChoose,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PLAN_DETAIL_CHOOSE_TAG),
        )
    }
}

/** El botón principal: una píldora de tinta (o verde si ya está elegido), sin ondas de Material. */
@Composable
private fun PillButton(
    label: String,
    container: Color,
    content: Color,
    leadingCheck: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(100), label = "pillPress")
    Row(
        modifier = modifier
            .heightIn(min = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingCheck) {
            CheckGlyph(content, Modifier.size(16.dp), 2.4.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = WizardTypography.cta, color = content)
    }
}

/** Una marca de verificación de línea. */
@Composable
private fun CheckGlyph(color: Color, modifier: Modifier = Modifier, stroke: Dp = 2.dp) {
    Canvas(modifier) {
        val w = stroke.toPx()
        drawLine(color, Offset(size.width * 0.10f, size.height * 0.54f), Offset(size.width * 0.40f, size.height * 0.84f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.84f), Offset(size.width * 0.92f, size.height * 0.18f), w, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- secciones

@Composable
private fun SectionTitle(text: String) {
    FittedDisplayText(
        text = text,
        maxSp = 20f,
        minSp = 15f,
        styleOf = { sp -> SECTION_STYLE.copy(fontSize = sp.sp, lineHeight = (sp * 1.3f).sp) },
        color = WizardColors.text,
        maxLines = 3,
        modifier = Modifier
            .padding(top = 34.dp, bottom = 12.dp)
            .semantics { heading() },
    )
}

/**
 * La descripción: hasta cuatro líneas y, si hay más, «Ver más» que la despliega. Plegada nunca acaba en una palabra partida
 * («muert…» con letra grande): se mide y se corta en la última palabra entera que cabe, con «…».
 */
@Composable
private fun DescriptionBlock(text: String, accent: Color) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    // Cuántos caracteres del texto caben plegado: baja, midiendo, hasta que entran en las líneas sin partir una palabra.
    var keep by remember(text) { mutableIntStateOf(text.length) }
    val shown = if (expanded || keep >= text.length) text else ellipsizeAtWord(text, keep)
    Column(Modifier.animateContentSize()) {
        Text(
            text = shown,
            style = WizardTypography.body,
            color = WizardColors.text.copy(alpha = 0.88f),
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_LINES,
            // Lo que sobra no se pinta: el corte limpio lo hace `ellipsizeAtWord`, no el «…» de Compose, que parte palabras.
            overflow = TextOverflow.Clip,
            onTextLayout = { layout ->
                if (!expanded && layout.hasVisualOverflow) {
                    overflows = true
                    val visibleEnd = layout.getLineEnd(DESCRIPTION_LINES - 1, visibleEnd = true)
                    // Con el «…» puesto, un corte justo en el borde de la línea lo empuja a una quinta: se baja un carácter más.
                    val next = if (keep >= text.length) visibleEnd else minOf(visibleEnd, keep) - 1
                    if (next in 1 until keep) keep = next
                }
            },
        )
        if (overflows || expanded) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .plainClickable { expanded = !expanded },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (expanded) PlanCopy.SEE_LESS else PlanCopy.SEE_MORE,
                    style = WizardTypography.controlLabel,
                    color = accent,
                )
            }
        }
    }
}

/**
 * [text] cortado en [end] sin partir una palabra y terminado en «…»: si el corte cae justo antes de un espacio la última palabra
 * está entera; si no, se descarta la que quedó a medias (y los signos sueltos de antes del corte). Un texto que ya cabe, igual.
 */
internal fun ellipsizeAtWord(text: String, end: Int): String {
    val limit = end.coerceIn(0, text.length)
    if (limit >= text.length) return text
    val head = text.substring(0, limit)
    val whole = if (text[limit].isWhitespace()) head else head.substringBeforeLast(' ', head)
    return whole.trimEnd(' ', '\n', ',', ';', ':', '.', '-', '–', '—', '(') + "…"
}

/**
 * Los ejercicios principales: una lista de línea con un filete debajo de cada uno. La miniatura es la imagen del ejercicio que tiene
 * KPKN; si KPKN no la tiene, la fila va sin miniatura (ni pictograma, ni inicial, ni icono en su lugar).
 */
@Composable
private fun ExerciseList(rows: List<PlanFichaExercise>) {
    val divider = WizardColors.divider
    Column {
        for (row in rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.imageResId?.let { image ->
                    ExerciseImageThumb(image)
                    Spacer(Modifier.width(14.dp))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                ) {
                    Text(text = row.name, style = WizardTypography.cardTitle, color = WizardColors.text)
                    row.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(text = detail, style = WizardTypography.note, color = WizardColors.textFaint)
                    }
                }
            }
        }
    }
}

/** La imagen del ejercicio que tiene KPKN, en un cuadro redondeado de 42 dp (como la miniatura del catálogo de ejercicios). */
@Composable
private fun ExerciseImageThumb(imageResId: Int) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SwapThumbFill),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(imageResId),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

// ---------------------------------------------------------------- la ficha reutilizada (catálogo y «Sobre el programa»)

/** Las etiquetas bajo la descripción (enfoque, material, alcance…): píldoras tenues de solo lectura. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FichaChips(chips: List<String>) {
    FlowRow(
        modifier = Modifier.padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (chip in chips) {
            Box(
                Modifier
                    .heightIn(min = 30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(chip, style = WizardTypography.note, color = WizardColors.textMuted)
            }
        }
    }
}

/**
 * «Tu semana» día por día: de lunes a domingo, cada día con su abreviatura, el glifo de su sesión, su título y «7 ejercicios · ~75
 * min»; el descanso, en gris. Un día de entreno se despliega (uno a la vez) y enseña sus ejercicios con «3 × 3–5 · RIR 2».
 */
@Composable
private fun FichaWeek(days: List<PlanFichaDay>, initialOpen: Int?, accent: Color, reduced: Boolean) {
    var open by remember(days, initialOpen) { mutableStateOf(initialOpen?.takeIf { day -> days.any { it.day == day && it.lines.isNotEmpty() } }) }
    Column(Modifier.fillMaxWidth()) {
        for (day in days) {
            val expandable = !day.rest && day.lines.isNotEmpty()
            val isOpen = open == day.day
            FichaDayRow(day, accent, expandable, isOpen) { open = if (isOpen) null else day.day }
            if (expandable) {
                AnimatedVisibility(
                    visible = isOpen,
                    enter = if (reduced) EnterTransition.None else expandVertically(tween(240)) + fadeIn(tween(240)),
                    exit = if (reduced) ExitTransition.None else shrinkVertically(tween(240)) + fadeOut(tween(240)),
                ) {
                    FichaDayLines(day.lines)
                }
            }
        }
    }
}

/** Una fila de «Tu semana»: «Lun», el círculo con el glifo de la sesión (un guion en el descanso), el título y la línea pequeña. */
@Composable
private fun FichaDayRow(day: PlanFichaDay, accent: Color, expandable: Boolean, open: Boolean, onToggle: () -> Unit) {
    val divider = WizardColors.divider
    val name = PlanCopy.WEEKDAY_NAMES[day.day - 1]
    val description = buildString {
        append(name.replaceFirstChar { it.uppercase() }).append(": ").append(day.title)
        day.subtitle?.let { append(", ").append(it) }
    }
    val pattern = remember(day.glyphOf) { day.glyphOf?.let(::exercisePatternOf) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .then(
                if (expandable) {
                    Modifier
                        .testTag(planFichaDayTag(day.day))
                        .semantics(mergeDescendants = true) {
                            contentDescription = description
                            stateDescription = if (open) DAY_OPEN else DAY_CLOSED
                        }
                        .clickable(role = Role.Button, onClickLabel = "Ver los ejercicios del $name", onClick = onToggle)
                } else {
                    Modifier.clearAndSetSemantics { contentDescription = description }
                },
            )
            .then(if (open) Modifier else Modifier.drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            })
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.label,
            style = WizardTypography.controlLabel,
            color = if (day.rest) WizardColors.textFaint else WizardColors.text,
            modifier = Modifier.width(DAY_LABEL_WIDTH),
        )
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .size(42.dp)
                .background(DayCircleFill, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (pattern != null && !day.rest) {
                PatternGlyph(pattern, accent, Modifier.size(22.dp))
            } else {
                // El descanso: un guion corto en tinta apagada.
                val dash = WizardColors.textFaint.copy(alpha = 0.6f)
                Canvas(Modifier.size(16.dp)) {
                    val y = size.height / 2f
                    drawLine(dash, Offset(0f, y), Offset(size.width, y), 1.6.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(day.title, style = WizardTypography.cardTitle, color = if (day.rest) WizardColors.textFaint else WizardColors.text)
            day.subtitle?.let { Text(it, style = WizardTypography.note, color = WizardColors.textFaint) }
        }
        if (expandable) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (open) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                contentDescription = null,
                tint = WizardColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Los ejercicios de un día desplegado: el nombre a la izquierda y la prescripción corta a la derecha, alineados con el título. */
@Composable
private fun FichaDayLines(lines: List<PlanFichaLine>) {
    val divider = WizardColors.divider
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .padding(start = DAY_LABEL_WIDTH + 14.dp + 42.dp + 14.dp, top = 2.dp, bottom = 10.dp),
    ) {
        for (line in lines) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(line.name, style = WizardTypography.note, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
                if (line.prescription.isNotBlank()) {
                    Text(
                        text = line.prescription,
                        style = DAY_LINE_PRESCRIPTION,
                        color = WizardColors.text,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                }
            }
        }
    }
}

/** Las filas de una sección extra («Cargas y cambios»): un círculo con el icono del acento, el título y la línea pequeña. */
@Composable
private fun FichaRows(rows: List<PlanFichaRow>, accent: Color) {
    val divider = WizardColors.divider
    Column {
        for (row in rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (row.icon) {
                            PlanFichaRowIcon.TARGET -> KpknLineIcons.Target
                            PlanFichaRowIcon.SWAP -> KpknLineIcons.Swap
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                ) {
                    Text(row.title, style = WizardTypography.cardTitle, color = WizardColors.text)
                    if (row.subtitle.isNotBlank()) Text(row.subtitle, style = WizardTypography.note, color = WizardColors.textFaint)
                }
            }
        }
    }
}

/** La acción secundaria del final («Ver cómo funciona»): una píldora de contorno, pequeña, a todo el ancho. */
@Composable
private fun SecondaryPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(100), label = "secondaryPillPress")
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .border(1.dp, WizardColors.text.copy(alpha = 0.24f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = WizardTypography.controlLabel, color = WizardColors.text)
    }
}

/** Ancho de la abreviatura del día («Mié») en «Tu semana» día por día. */
private val DAY_LABEL_WIDTH = 36.dp

/** El fondo del círculo de cada día (#141414, el del lienzo). */
private val DayCircleFill = Color(0xFF141414)

/** La prescripción de un ejercicio del día desplegado: cifras tabulares. */
private val DAY_LINE_PRESCRIPTION = WizardTypography.note.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")

/** Ancho mínimo y máximo de la columna de un bloque en la línea de tiempo horizontal. */
private val BLOCK_MIN_COL = 112.dp
private val BLOCK_MAX_COL = 210.dp

/** Aire a la derecha de cada bloque de la línea de tiempo horizontal. */
private val BLOCK_GAP = 14.dp

/** Cada texto de un bloque tiene su estilo: el nombre, las semanas y la frase. */
internal enum class BlockText { LABEL, WEEKS, DETAIL }

/**
 * ¿Cabe cada palabra de [block] entera en [widthPx]? [widthOf] da el ancho de un texto con el estilo de cada parte. Si alguna no
 * cabe, la columna partiría una palabra a media palabra y la estructura pasa a la disposición vertical.
 */
internal fun blockWordsFit(block: PlanBlockModel, widthPx: Float, widthOf: (String, BlockText) -> Float): Boolean {
    fun fits(text: String, kind: BlockText) = text.split(' ').filter { it.isNotEmpty() }.all { widthOf(it, kind) <= widthPx }
    return fits(block.label, BlockText.LABEL) && fits(block.weeksLabel, BlockText.WEEKS) && fits(block.detail, BlockText.DETAIL)
}

/**
 * La estructura: una línea de tiempo con un nodo por bloque, su nombre, sus semanas y una frase. Lo normal es horizontal: con
 * pocos bloques se reparten el ancho; con más, la línea se desliza de lado. Si alguna palabra no cabe en su columna (letra
 * grande, teléfono estrecho) pasa a vertical, con el riel a la izquierda, y no parte nada.
 */
@Composable
private fun BlockTimeline(blocks: List<PlanBlockModel>, accent: Color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val colW = maxOf(BLOCK_MIN_COL, minOf(BLOCK_MAX_COL, maxWidth / blocks.size))
        val fill = colW * blocks.size <= maxWidth
        // Con pocos bloques se reparten el ancho; con más, cada columna mide lo suyo y la línea se desliza.
        val columnWidth = if (fill) maxWidth / blocks.size else colW
        val innerPx = with(density) { (columnWidth - BLOCK_GAP).toPx() }
        val horizontal = remember(blocks, innerPx, density.fontScale, density.density) {
            blocks.all { block ->
                blockWordsFit(block, innerPx) { text, kind ->
                    val style = when (kind) {
                        BlockText.LABEL -> WizardTypography.cardTitle
                        BlockText.WEEKS -> BLOCK_WEEKS_STYLE
                        BlockText.DETAIL -> WizardTypography.note
                    }
                    measurer.measure(text = text, style = style, maxLines = 1, softWrap = false).size.width.toFloat()
                }
            }
        }
        if (horizontal) HorizontalBlocks(blocks, accent, columnWidth) else VerticalBlocks(blocks, accent)
    }
}

private val BLOCK_WEEKS_STYLE = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun HorizontalBlocks(blocks: List<PlanBlockModel>, accent: Color, columnWidth: Dp) {
    val scrollState = rememberScrollState()
    Row(
        Modifier
            // Si la línea se desliza, el borde derecho se funde: así el último bloque no parece cortado a media palabra.
            .drawWithContent {
                drawContent()
                if (scrollState.canScrollForward) {
                    val fadeW = 36.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, OverlayScrim),
                            startX = size.width - fadeW,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeW, 0f),
                        size = Size(fadeW, size.height),
                    )
                }
            }
            .horizontalScroll(scrollState),
    ) {
        blocks.forEachIndexed { i, block ->
            val last = i == blocks.lastIndex
            Column(
                Modifier
                    .width(columnWidth)
                    .padding(end = BLOCK_GAP),
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                ) {
                    val cy = size.height / 2f
                    val r = 5.dp.toPx()
                    if (!last) drawLine(accent.copy(alpha = 0.35f), Offset(r * 2f, cy), Offset(size.width + BLOCK_GAP.toPx(), cy), 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(accent, r, Offset(r, cy))
                }
                BlockTexts(block, accent)
            }
        }
    }
}

/** La estructura en vertical: el riel a la izquierda (un nodo por bloque) y los textos de cada bloque a su derecha. */
@Composable
private fun VerticalBlocks(blocks: List<PlanBlockModel>, accent: Color) {
    Column {
        blocks.forEachIndexed { i, block ->
            val last = i == blocks.lastIndex
            Row(Modifier.height(IntrinsicSize.Min)) {
                Canvas(
                    Modifier
                        .width(22.dp)
                        .fillMaxHeight(),
                ) {
                    val x = 5.dp.toPx()
                    val top = 11.dp.toPx()
                    if (!last) drawLine(accent.copy(alpha = 0.35f), Offset(x, top), Offset(x, size.height + top), 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(accent, 5.dp.toPx(), Offset(x, top))
                }
                Column(Modifier.padding(bottom = if (last) 0.dp else 22.dp)) { BlockTexts(block, accent) }
            }
        }
    }
}

/** El nombre, las semanas y la frase de un bloque. */
@Composable
private fun BlockTexts(block: PlanBlockModel, accent: Color) {
    Text(block.label, style = WizardTypography.cardTitle, color = WizardColors.text)
    Text(
        text = block.weeksLabel,
        style = BLOCK_WEEKS_STYLE,
        color = accent,
        modifier = Modifier.padding(top = 2.dp),
    )
    if (block.detail.isNotBlank()) {
        Text(
            text = block.detail,
            style = WizardTypography.note,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Por qué este programa: una marca de verificación del acento y una frase por razón. */
@Composable
private fun ReasonList(reasons: List<String>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (reason in reasons) {
            Row(verticalAlignment = Alignment.Top) {
                CheckGlyph(accent, Modifier.padding(top = 4.dp).size(16.dp), 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = reason,
                    style = WizardTypography.bodySmall,
                    color = WizardColors.text.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Las notas honestas del programa, con un punto de tinta tenue. */
@Composable
private fun NoteList(notes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (note in notes) {
            Row(verticalAlignment = Alignment.Top) {
                Canvas(Modifier.padding(top = 8.dp).size(10.dp)) {
                    drawCircle(WizardColors.textFaint, 2.5.dp.toPx(), Offset(size.width / 2f, size.height / 2f))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = note,
                    style = WizardTypography.bodySmall,
                    color = WizardColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- las fases

/** Una fase de la franja: su nombre, sus semanas y su objetivo (de él sale el tono). */
data class PlanPhaseModel(val label: String, val weeks: Int, val goal: BlockGoal?)

/** Marca de prueba de la franja de fases del detalle. */
internal const val PLAN_PHASES_TAG = "setup-plan-phases"

/**
 * El tono de una fase: la paleta de la marca según su objetivo (acumular, intensificar, realizar, descargar). Son tintas
 * planas: sin resplandor ni degradado.
 */
internal fun phaseAccentOf(goal: BlockGoal?): Color = when (goal) {
    BlockGoal.ACCUMULATION -> SymbolPalette.columna
    BlockGoal.INTENSIFICATION -> SymbolPalette.energia
    BlockGoal.REALIZATION, BlockGoal.PEAK, BlockGoal.SPECIFICITY -> SymbolPalette.musculo
    BlockGoal.DELOAD -> SymbolPalette.ok
    BlockGoal.TAPER, BlockGoal.DENSITY -> SymbolPalette.mente
    BlockGoal.CUSTOM, null -> WizardColors.textMuted
}

/** Lo que anuncia TalkBack de una fase: «Fase 2 de 3: Intensificación, 2 semanas». */
internal fun phaseDescription(index: Int, count: Int, phase: PlanPhaseModel): String =
    "Fase ${index + 1} de $count: ${phase.label}, ${SpanishPlurals.weeks(phase.weeks)}"

/**
 * La franja de fases de un programa de varias semanas: una barra con un tramo por fase, de ancho proporcional a sus semanas,
 * y debajo la lista («Acumulación · 3 sem»), que es la que cuenta a TalkBack. Sin fases no pinta nada.
 */
@Composable
internal fun PlanPhasesStrip(phases: List<PlanPhaseModel>, modifier: Modifier = Modifier) {
    if (phases.isEmpty()) return
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 26.dp)
            .testTag(PLAN_PHASES_TAG),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                // La barra es un dibujo: la lista de debajo es la que dice las fases.
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (phase in phases) {
                Box(
                    Modifier
                        .weight(phase.weeks.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(phaseAccentOf(phase.goal)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        phases.forEachIndexed { index, phase ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .semantics(mergeDescendants = true) { contentDescription = phaseDescription(index, phases.size, phase) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(8.dp)) { drawCircle(phaseAccentOf(phase.goal)) }
                Spacer(Modifier.width(10.dp))
                Text(text = "${phase.label} · ${phase.weeks} sem", style = WizardTypography.note, color = WizardColors.text)
            }
        }
    }
}

// ---------------------------------------------------------------- la semana

/**
 * Tu semana: los siete días como círculos con su inicial, desde [weekStartDay] (1 = lunes) y dando la vuelta. Los de
 * entreno van en el acento (el principal, con un punto). Si los títulos caben bajo su círculo, cada día de entreno lleva
 * debajo su título corto y sus minutos; si no (pantalla estrecha o letra grande), los círculos van solos y las sesiones
 * se enumeran debajo, una por fila, en ese mismo orden.
 */
@Composable
private fun WeekStrip(
    week: List<PlanDayModel>,
    accent: Color,
    weekStartDay: Int = 1,
    swaps: ExerciseSwaps,
    state: SwapPanelState,
    reduced: Boolean,
    onInfo: (SetupPlanRevealExercise) -> Unit,
) {
    val byDay = remember(week) { week.filter { it.day in 1..7 }.associateBy { it.day } }
    val order = remember(weekStartDay) { slotOrder(weekStartDay) }
    // Solo los días con ejercicios se pueden tocar (su círculo o su fila de sesión): un día sin lista no ofrece nada que desplegar.
    fun tappable(d: Int): Boolean = byDay[d] != null && swaps.byDay[d].orEmpty().isNotEmpty()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        // La fila sangra hasta el margen: cada día tiene así su objetivo táctil de 48 dp a 360 dp (como el calendario del wizard).
        val colW = (maxWidth + BLEED * 2 - gap * 6) / 7
        val colPx = with(density) { colW.toPx() }
        val captions = remember(byDay, colPx, density.fontScale) {
            widestTitlePx(byDay.values.map { it.title }) { text ->
                measurer.measure(text = text, style = DAY_CAPTION, maxLines = 1, softWrap = false).size.width.toFloat()
            } <= colPx
        }
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bleedHorizontally(BLEED),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (d in order) {
                    DayColumn(
                        day = d,
                        model = byDay[d],
                        accent = accent,
                        captions = captions,
                        modifier = Modifier.weight(1f),
                        onToggle = if (tappable(d)) ({ state.toggleDay(d) }) else null,
                        open = state.openDay == d,
                    )
                }
            }
            // La lista del día desplegado va bajo la fila. Al plegarla se sigue viendo el último día mientras se anima.
            AnimatedVisibility(
                visible = state.openDay != null,
                enter = if (reduced) EnterTransition.None else expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = if (reduced) ExitTransition.None else shrinkVertically(tween(260)) + fadeOut(tween(260)),
            ) {
                DayExercises(day = state.lastDay, accent = accent, swaps = swaps, state = state, reduced = reduced, onInfo = onInfo)
            }
            if (!captions) {
                Spacer(Modifier.height(14.dp))
                for (day in byDay.values.sortedBy { order.indexOf(it.day) }) {
                    SessionRow(
                        model = day,
                        accent = accent,
                        onToggle = if (tappable(day.day)) ({ state.toggleDay(day.day) }) else null,
                        open = state.openDay == day.day,
                    )
                }
            }
            if (byDay.values.any { it.isMain }) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(accent) }
                    Spacer(Modifier.width(8.dp))
                    Text("Sesión principal", style = WizardTypography.note, color = WizardColors.textMuted)
                }
            }
        }
    }
}

private val DAY_CAPTION = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold)

/** El ancho (píxeles) del título más ancho de los días de entreno: lo que decide si caben bajo su círculo. */
internal fun widestTitlePx(titles: List<String>, widthOf: (String) -> Float): Float =
    titles.maxOfOrNull { widthOf(it) } ?: 0f

/**
 * El círculo de un día: contorno del acento si se entrena, tenue si se descansa; la inicial dentro y, si es el principal, un
 * punto. El día desplegado ([open]) lleva además un relleno tenue del acento, que lo distingue sin caja ni resplandor.
 */
@Composable
private fun DayCircle(day: Int, trained: Boolean, main: Boolean, accent: Color, diameter: Dp, open: Boolean = false) {
    val initial = PlanCopy.WEEKDAY_INITIALS[day - 1]
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val w = if (trained) 2.dp.toPx() else 1.dp.toPx()
            val color = if (trained) accent else WizardColors.text.copy(alpha = 0.18f)
            if (trained && open) drawCircle(accent.copy(alpha = OPEN_DAY_FILL_ALPHA), size.minDimension / 2f)
            drawCircle(color, size.minDimension / 2f - w / 2f, style = Stroke(w))
            if (main) drawCircle(accent, 4.dp.toPx(), Offset(size.width * 0.86f, size.height * 0.14f))
        }
        Text(
            text = initial,
            style = WizardTypography.controlLabel,
            color = if (trained) WizardColors.text else WizardColors.textFaint,
        )
    }
}

/** Un día de la fila de círculos; con [captions], los de entreno llevan debajo su título corto y sus minutos. */
@Composable
private fun DayColumn(
    day: Int,
    model: PlanDayModel?,
    accent: Color,
    captions: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
    open: Boolean = false,
) {
    val description = weekDayDescription(day, model)
    val name = PlanCopy.WEEKDAY_NAMES[day - 1]
    Column(
        modifier = modifier
            .then(if (onToggle == null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier)
            .then(
                if (onToggle == null) {
                    Modifier
                } else {
                    // Un día tocable: el objetivo ocupa toda la columna y mide al menos 48 dp de alto (y la fila, 48 dp de ancho).
                    Modifier
                        .heightIn(min = 48.dp)
                        .testTag("setup-plan-day-$day")
                        .semantics(mergeDescendants = true) {
                            contentDescription = description
                            stateDescription = if (open) DAY_OPEN else DAY_CLOSED
                        }
                        .clickable(role = Role.Button, onClickLabel = "Ver los ejercicios del $name", onClick = onToggle)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DayCircle(day, model != null, model?.isMain == true, accent, 38.dp, open = open)
        if (captions && model != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = model.title,
                style = DAY_CAPTION,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = "${model.minutes} min",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Margen que la fila de días sangra a cada lado (el de la hoja): con él, cada día llega a los 48 dp de objetivo táctil. */
private val BLEED = 24.dp

/** Marca de prueba del panel «Cambiar» abierto. */
internal const val SWAP_PANEL_TAG = "setup-plan-swap-panel"
internal const val SWAP_SCRIM_TAG = "setup-plan-swap-scrim"
internal const val SWAP_CURRENT_TAG = "setup-plan-swap-current"
internal const val SWAP_KEEPS_TAG = "setup-plan-swap-keeps"
internal const val SWAP_CONFIRM_TAG = "setup-plan-swap-confirm"
internal const val SWAP_CANCEL_TAG = "setup-plan-swap-cancel"

/** Motivos cortos de una alternativa y el texto cuando no hay ninguna con el material de ese día. */
internal const val SWAP_REASON_SAME_MUSCLE = "Mismo músculo"
internal const val SWAP_REASON_SAME_PATTERN = "Mismo patrón"
internal const val SWAP_NONE_TEXT = "No hay otra opción con el material de este día."

/** Lo que TalkBack dice del estado de un día tocable. */
private const val DAY_OPEN = "Desplegado"
private const val DAY_CLOSED = "Plegado"

/** Relleno del acento del día desplegado: tenue, para que la inicial siga legible sobre él. */
private const val OPEN_DAY_FILL_ALPHA = 0.18f

private val SwapSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SwapCardShape = RoundedCornerShape(20.dp)

/** Los fondos del panel «Cambiar» (G4): la hoja #0E0E0E, el módulo del ejercicio #111 y el cuadro del pictograma #1A1A1A. */
private val SwapSheetFill = Color(0xFF0E0E0E)
private val SwapCardFill = Color(0xFF111111)
private val SwapThumbFill = Color(0xFF1A1A1A)

/** Lo que queda libre sobre la hoja «Cambiar»: se ve el día desplegado, oscurecido. */
private val SWAP_SHEET_TOP_GAP = 168.dp

/** El estado de «Cambiar» en la hoja: el día desplegado, el ejercicio con el panel abierto, sus alternativas y la elegida. */
private class SwapPanelState {
    /** El día cuya lista está desplegada (null = plegada). */
    var openDay by mutableStateOf<Int?>(null)

    /** El último día desplegado: mientras la lista se pliega, sigue mostrando sus ejercicios. */
    var lastDay by mutableStateOf<Int?>(null)

    /** El ejercicio cuyo panel «Cambiar» está abierto (su instancia), el propio ejercicio y sus alternativas. */
    var panelOf by mutableStateOf<String?>(null)
    var panelExercise by mutableStateOf<SetupPlanRevealExercise?>(null)
    var options by mutableStateOf<List<SwapOption>>(emptyList())

    /** La alternativa marcada en el panel: no se aplica hasta «Cambiar por …» (G4: confirmar antes de aplicar). */
    var picked by mutableStateOf<String?>(null)

    /** Abre el panel del ejercicio [exercise] con sus [alternatives]; parte con la primera, la más parecida, marcada. */
    fun openPanel(exercise: SetupPlanRevealExercise, alternatives: List<SwapOption>) {
        panelExercise = exercise
        options = alternatives
        picked = alternatives.firstOrNull()?.configId
        panelOf = exercise.instanceId
    }

    fun closePanel() {
        panelOf = null
        picked = null
    }

    /** Despliega el día [day] o, si ya lo está, lo pliega. Cambiar de día (o plegarlo) cierra el panel. */
    fun toggleDay(day: Int) {
        if (openDay == day) {
            openDay = null
        } else {
            openDay = day
            lastDay = day
        }
        closePanel()
    }
}

/** Lo que la hoja sabe de los ejercicios de cada día y lo que «Cambiar» puede hacer con ellos. */
private class ExerciseSwaps(
    val byDay: Map<Int, List<SetupPlanRevealExercise>>,
    /** Solo el programa elegido se puede cambiar: en los demás la lista se enseña sin «Cambiar». */
    val enabled: Boolean,
    val optionsOf: (String) -> List<SwapOption>,
    val onSwap: (String, String) -> Unit,
    val onUndo: (String) -> Unit,
)

/** La fila que sangra [dp] a cada lado: se mide con su ancho y se dibuja más ancha, sin mover el resto de la página. */
private fun Modifier.bleedHorizontally(dp: Dp): Modifier = layout { measurable, constraints ->
    val half = dp.roundToPx()
    val width = constraints.maxWidth + 2 * half
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-half, 0) }
}

/** Los ejercicios del día [day] de la semana tipo, uno por fila, con su «Cambiar» si el programa elegido se puede cambiar. */
@Composable
private fun DayExercises(
    day: Int?,
    accent: Color,
    swaps: ExerciseSwaps,
    state: SwapPanelState,
    reduced: Boolean,
    onInfo: (SetupPlanRevealExercise) -> Unit,
) {
    val exercises = day?.let { swaps.byDay[it] }.orEmpty()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        for (exercise in exercises) ExerciseRow(exercise, accent, swaps, state, reduced, onInfo)
    }
}

/**
 * Un ejercicio del día: su nombre y «4 × 6–8» son un solo objetivo táctil que abre su ficha del catálogo (con un chevron como
 * pista); si el programa elegido lo permite, «Cambiar». El panel de alternativas se abre bajo la fila; un ejercicio ya cambiado
 * dice «Cambiado · Deshacer».
 */
@Composable
private fun ExerciseRow(
    exercise: SetupPlanRevealExercise,
    accent: Color,
    swaps: ExerciseSwaps,
    state: SwapPanelState,
    reduced: Boolean,
    onInfo: (SetupPlanRevealExercise) -> Unit,
) {
    val divider = WizardColors.divider
    val canSwap = swaps.enabled && exercise.swappable
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // El área de toque: nombre y prescripción (48 dp como mínimo) abren la ficha del catálogo.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("setup-plan-exercise-${exercise.instanceId}")
                        .semantics(mergeDescendants = true) { contentDescription = ExerciseInfoCopy.SEE_EXERCISE + exercise.name }
                        .clickable(role = Role.Button) { onInfo(exercise) }
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            exercise.name,
                            style = WizardTypography.controlLabel,
                            color = WizardColors.text,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        ChevronGlyph(Modifier.size(12.dp))
                    }
                    exercise.setsLabel?.let { Text(it, style = WizardTypography.note, color = WizardColors.textMuted) }
                }
                if (exercise.swappedConfigId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cambiado · ", style = WizardTypography.note, color = WizardColors.textMuted)
                        Box(
                            Modifier
                                .testTag("setup-plan-undo-${exercise.instanceId}")
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClickLabel = "Deshacer el cambio de ${exercise.name}") {
                                    swaps.onUndo(exercise.instanceId)
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text("Deshacer", style = WizardTypography.controlLabel, color = accent)
                        }
                    }
                }
            }
            if (canSwap) {
                SwapAction(name = exercise.name, instanceId = exercise.instanceId) {
                    if (state.panelOf != exercise.instanceId) {
                        state.openPanel(exercise, swaps.optionsOf(exercise.instanceId))
                    }
                }
            }
        }
    }
}

/** «Cambiar»: unas flechas y el texto, en un objetivo táctil de 48 dp de alto. */
@Composable
private fun SwapAction(name: String, instanceId: String, onClick: () -> Unit) {
    Row(
        Modifier
            .testTag("setup-plan-swap-$instanceId")
            .padding(start = 8.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Cambiar $name", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwapArrows(WizardColors.text, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Cambiar", style = WizardTypography.controlLabel, color = WizardColors.text)
    }
}

/** La pista de que el nombre se abre: un chevron fino de 12 dp en tinta tenue. */
@Composable
private fun ChevronGlyph(modifier: Modifier = Modifier) {
    val color = WizardColors.textFaint
    Canvas(modifier) {
        val w = 1.4.dp.toPx()
        drawLine(color, Offset(size.width * 0.3f, size.height * 0.18f), Offset(size.width * 0.74f, size.height * 0.5f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.74f, size.height * 0.5f), Offset(size.width * 0.3f, size.height * 0.82f), w, StrokeCap.Round)
    }
}

/** Dos flechas enfrentadas: el icono de «Cambiar». */
@Composable
private fun SwapArrows(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = 1.6.dp.toPx()
        val top = size.height * 0.36f
        val bottom = size.height * 0.64f
        val head = size.height * 0.26f
        drawLine(color, Offset(0f, top), Offset(size.width, top), w, StrokeCap.Round)
        drawLine(color, Offset(size.width - head, top - head), Offset(size.width, top), w, StrokeCap.Round)
        drawLine(color, Offset(size.width - head, top + head), Offset(size.width, top), w, StrokeCap.Round)
        drawLine(color, Offset(size.width, bottom), Offset(0f, bottom), w, StrokeCap.Round)
        drawLine(color, Offset(head, bottom - head), Offset(0f, bottom), w, StrokeCap.Round)
        drawLine(color, Offset(head, bottom + head), Offset(0f, bottom), w, StrokeCap.Round)
    }
}

/**
 * El panel «Cambiar» (G4): una hoja sobre el día desplegado, con el aspecto del reemplazo del catálogo. Arriba el ejercicio que se
 * reemplaza («REEMPLAZANDO»), después las alternativas más parecidas como lista de una sola elección y, fijo abajo, lo que se
 * mantiene del plan, «Cambiar por …» y «Cancelar». Marcar una alternativa no cambia nada: solo el botón aplica ([onConfirm]); tocar
 * el velo o «Cancelar» lo cierra sin tocar el programa.
 */
@Composable
private fun SwapSheet(
    exercise: SetupPlanRevealExercise,
    accent: Color,
    options: List<SwapOption>,
    picked: String?,
    onPick: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickedOption = options.firstOrNull { it.configId == picked }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize()) {
        // El velo: oscurece el plan y, al tocarlo, cancela.
        Box(
            Modifier
                .fillMaxSize()
                .testTag(SWAP_SCRIM_TAG)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Cancelar el cambio", onClick = onCancel),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = statusTop + SWAP_SHEET_TOP_GAP)
                .testTag(SWAP_PANEL_TAG)
                .semantics { paneTitle = "Cambiar ${exercise.name}" }
                .clip(SwapSheetShape)
                .background(SwapSheetFill)
                .border(1.dp, Color.White.copy(alpha = 0.08f), SwapSheetShape)
                // Lo de dentro no deja pasar el toque al velo (sin fundir la semántica de sus hijos, como haría un clickable).
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp)),
            )
            Text(
                text = "Cambiar",
                style = SECTION_STYLE,
                color = WizardColors.text,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() },
            )
            // Lo que se reemplaza.
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .testTag(SWAP_CURRENT_TAG)
                    .background(SwapCardFill, SwapCardShape)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), SwapCardShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val currentImage = remember(exercise) { kpknImageOfRevealExercise(exercise) }
                currentImage?.let { SwapThumb(it, 48.dp) }
                Column(Modifier.weight(1f)) {
                    Text("REEMPLAZANDO", style = WizardTypography.eyebrow, color = WizardColors.textFaint)
                    Text(exercise.name, style = WizardTypography.controlLabel, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    exercise.setsLabel?.let { Text(it, style = WizardTypography.note, color = WizardColors.textMuted) }
                }
            }
            if (options.isEmpty()) {
                Text(
                    SWAP_NONE_TEXT,
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).weight(1f, fill = false),
                )
            } else {
                Text(
                    "PRIMERO LOS MÁS PARECIDOS",
                    style = WizardTypography.eyebrow,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .selectableGroup()
                        .semantics { contentDescription = "Alternativas" },
                ) {
                    for (option in options) SwapOptionRow(option, accent, selected = option.configId == picked) { onPick(option.configId) }
                    Text(
                        "Solo con el material de este día.",
                        style = WizardTypography.note,
                        color = WizardColors.textFaint,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            // Fijo abajo: lo que se mantiene, el botón que aplica y «Cancelar».
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to SwapSheetFill, 1f to SwapSheetFill))
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = navBottom + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = exercise.setsLabel?.let { "Se mantienen las series del plan: $it" } ?: "Se mantienen las series del plan.",
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(SWAP_KEEPS_TAG),
                )
                PillButton(
                    label = pickedOption?.let { "Cambiar por ${it.displayName}" } ?: "Elige una alternativa",
                    container = if (pickedOption != null) WizardColors.cta else WizardColors.ctaDisabled,
                    content = if (pickedOption != null) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
                    leadingCheck = false,
                    onClick = { pickedOption?.let { onConfirm(it.configId) } },
                    modifier = Modifier.fillMaxWidth().testTag(SWAP_CONFIRM_TAG),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(SWAP_CANCEL_TAG)
                        .clickable(role = Role.Button, onClickLabel = "Cancelar el cambio", onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancelar", style = WizardTypography.controlLabel, color = WizardColors.textMuted)
                }
            }
        }
    }
}

/**
 * La imagen del ejercicio que tiene KPKN en un cuadro redondeado, como en el reemplazo del catálogo. Quien llama solo la pinta si KPKN
 * tiene esa imagen: sin ella, la fila va sin miniatura.
 */
@Composable
private fun SwapThumb(imageResId: Int, box: Dp) {
    Box(
        Modifier
            .size(box)
            .clip(RoundedCornerShape(12.dp))
            .background(SwapThumbFill),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(imageResId),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Una alternativa: la imagen de KPKN (si la tiene), su nombre, su matiz y el motivo corto, y a la derecha su marca; 52 dp de alto como mínimo. */
@Composable
private fun SwapOptionRow(option: SwapOption, accent: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("setup-plan-swap-option-${option.configId}")
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val image = remember(option.configId) { kpknImageOfConfiguration(option.configId) }
        image?.let { SwapThumb(it, 44.dp) }
        Column(Modifier.weight(1f)) {
            Text(option.displayName, style = WizardTypography.controlLabel, color = WizardColors.text)
            Text(swapReasonOf(option), style = WizardTypography.note, color = WizardColors.textMuted)
        }
        if (selected) {
            Box(Modifier.size(24.dp).background(WizardColors.cta, CircleShape), contentAlignment = Alignment.Center) {
                CheckGlyph(WizardColors.ctaContent, Modifier.size(14.dp), 2.4.dp)
            }
        } else {
            Box(Modifier.size(24.dp).border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape))
        }
    }
}

/** El motivo corto de una alternativa: «Mismo músculo» o «Mismo patrón», con el matiz de la configuración delante si lo hay. */
internal fun swapReasonOf(option: SwapOption): String {
    val reason = if (option.kind == SwapKind.EQUIVALENT) SWAP_REASON_SAME_MUSCLE else SWAP_REASON_SAME_PATTERN
    return if (option.variant.isBlank()) reason else "${option.variant} · $reason"
}

/**
 * Una sesión de la semana, en una fila: su círculo, el título y «75 min · 6 ejercicios» (más un punto si es la principal).
 * Si el día es tocable ([onToggle]), la fila entera despliega sus ejercicios igual que su círculo en «Tu semana».
 */
@Composable
private fun SessionRow(model: PlanDayModel, accent: Color, onToggle: (() -> Unit)? = null, open: Boolean = false) {
    val description = weekDayDescription(model.day, model)
    val name = PlanCopy.WEEKDAY_NAMES[model.day - 1]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (onToggle == null) {
                    Modifier.clearAndSetSemantics { contentDescription = description }
                } else {
                    Modifier
                        .testTag("setup-plan-session-${model.day}")
                        .semantics(mergeDescendants = true) {
                            contentDescription = description
                            stateDescription = if (open) DAY_OPEN else DAY_CLOSED
                        }
                        .clickable(role = Role.Button, onClickLabel = "Ver los ejercicios del $name", onClick = onToggle)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayCircle(model.day, trained = true, main = false, accent = accent, diameter = 34.dp, open = open)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(model.title, style = WizardTypography.cardTitle, color = WizardColors.text)
            Text(
                text = "${model.minutes} min · ${model.exerciseCount} ejercicios",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
            )
        }
        if (model.isMain) Canvas(Modifier.size(10.dp)) { drawCircle(accent) }
    }
}

/** Lo que anuncia TalkBack de un día: «Lunes: Torso, 60 min, 5 ejercicios, sesión principal» o «Martes: descanso». */
internal fun weekDayDescription(day: Int, model: PlanDayModel?): String {
    val name = PlanCopy.WEEKDAY_NAMES[day - 1].replaceFirstChar { it.uppercase() }
    if (model == null) return "$name: descanso"
    return buildString {
        append(name).append(": ").append(model.title).append(", ").append(model.minutes).append(" min, ")
        append(model.exerciseCount).append(" ejercicios")
        if (model.isMain) append(", sesión principal")
    }
}
