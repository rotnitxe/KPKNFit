package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.ExerciseTechniqueImageVariant
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalJointKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalMuscleKnowledgeForVolumeLabel
import com.example.kpkn.data.exercises.catalogv2.canonicalPatternKnowledge
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.domain.training.NativeProgressionText
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknHazeEffect
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Eje de la configuración que cambia el implemento: el mismo que mueven las páginas de la foto (CF1). */
private const val ImplementAxis = "implement"

/** Etiqueta de prueba de la fila de píldoras de implemento (CF1). */
internal const val CatalogImplementPillsTag = "catalog_implement_pills"

/** Vidrio de las píldoras: filete blanco al 12 %. */
private val ImplementEdge = Color.White.copy(alpha = 0.12f)

/** Zona táctil de cada opción (48 dp) y alto visual del vidrio dentro de ella (36 dp). */
private val ImplementTouchHeight = 48.dp
private val ImplementGlassHeight = 36.dp

/** Margen interior de la fila y alto del indicador de tinta. */
private val ImplementInset = 4.dp
private val ImplementIndicatorHeight = 28.dp

/** Anchura del fundido de los bordes cuando la fila se desplaza. */
private val ImplementEdgeFade = 16.dp

/** Padding horizontal del texto dentro de cada píldora (CF1). */
private val ImplementTextPad = 12.dp

/** Alto de la foto (SE6b). */
private val CatalogPhotoHeight = 248.dp

/**
 * Lo que pinta la ficha, ya resuelto por quien la abre (SE6b). Sin estado: las acciones llegan como lambdas.
 * [caption] es «Pectorales · Barra · Empuje horizontal»; [configurationLine] es el resumen de la configuración o el aviso de que falta
 * elegir. Con [canSelect] en falso, «Añadir» se apaga (hay opciones sin cerrar).
 */
internal data class CatalogDetailModel(
    val name: String,
    val caption: String?,
    val description: String?,
    val imageVariants: List<ExerciseTechniqueImageVariant>,
    val selectedImplementation: String?,
    val axes: List<CatalogDetailAxis>,
    val configurationLine: String?,
    val configurationIncomplete: Boolean,
    val muscleInfo: ExerciseMuscleInfo?,
    val joints: List<JointInvolvementV2>,
    val patternId: String?,
    val isSelected: Boolean,
    val canSelect: Boolean,
)

/** Un eje de la configuración con sus opciones visibles (las que el catálogo deja elegir). */
internal data class CatalogDetailAxis(
    val axis: String,
    val label: String,
    val options: List<CatalogDetailOption>,
)

internal data class CatalogDetailOption(
    val value: String,
    val label: String,
    val selected: Boolean,
    val enabled: Boolean,
)

/**
 * Ficha de información del ejercicio (SE6b, SESSION_EDITOR_V1 §4): una `KpknSheet` casi a pantalla completa. Su contenido es
 * [CatalogDetailContent]; aquí solo va la hoja.
 */
@Composable
internal fun ExerciseCatalogInfoDialog(
    model: CatalogDetailModel,
    onDismiss: () -> Unit,
    onToggleSelection: () -> Unit,
    onSelectOption: (axis: String, value: String) -> Unit,
    onImplementationSettled: (String) -> Unit,
    /** Sin el pie «Añadir/Quitar» (ER1f): la ficha del editor ya es de un ejercicio de la sesión. */
    showFooter: Boolean = true,
) {
    KpknSheet(
        onDismissRequest = onDismiss,
        maxHeightFraction = 0.94f,
        stableHeightFraction = 0.94f,
    ) {
        CatalogDetailContent(
            model = model,
            onDismiss = onDismiss,
            onToggleSelection = onToggleSelection,
            onSelectOption = onSelectOption,
            onImplementationSettled = onImplementationSettled,
            showFooter = showFooter,
        )
    }
}

/**
 * Cuerpo de la ficha (SE6b): la fila superior con «Cerrar definición», la foto a todo el ancho con las píldoras de implemento encima
 * (CF1), el nombre en `title`, la línea de músculo, material y patrón en `caption`, la descripción en `body`, la configuración como módulo
 * (sin el eje del implemento), los módulos plegables de músculos y articulaciones, y al pie «Añadir» o «Quitar». Va suelto en los tests y
 * en la hoja de render.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogDetailContent(
    model: CatalogDetailModel,
    onDismiss: () -> Unit,
    onToggleSelection: () -> Unit,
    onSelectOption: (axis: String, value: String) -> Unit,
    onImplementationSettled: (String) -> Unit,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = false,
    showFooter: Boolean = true,
) {
    // Los implementos van sobre la foto como píldoras (CF1); el resto de ejes sigue en «Configuración».
    val implementOptions = model.axes.firstOrNull { it.axis == ImplementAxis }?.options.orEmpty()
    val configAxes = model.axes.filter { it.axis != ImplementAxis }
    var muscleKnowledge by remember { mutableStateOf<CanonicalKnowledge?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        KpknTopRow(
            onBack = null,
            actions = {
                KpknIconButton(
                    icon = KpknLineIcons.Close,
                    contentDescription = "Cerrar definición",
                    onClick = onDismiss,
                )
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (model.imageVariants.isNotEmpty() || implementOptions.size > 1) {
                CatalogExerciseImageCarousel(
                    variants = model.imageVariants,
                    selectedImplementation = model.selectedImplementation,
                    onImplementationSettled = onImplementationSettled,
                    implementOptions = implementOptions,
                    onSelectImplementation = { value -> onSelectOption(ImplementAxis, value) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = model.name,
                    style = KpknType.title,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                model.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                    Text(
                        text = caption,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
            model.muscleInfo?.let { info ->
                CatalogMusclesModule(
                    exercise = info,
                    onKnowledge = { muscleKnowledge = it },
                )
            }
            model.description?.takeIf { it.isNotBlank() }?.let { description ->
                CatalogDescription(text = description)
            }
            if (configAxes.isNotEmpty() || model.configurationLine != null) {
                KpknModule(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Configuración",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                        configAxes.forEach { axis ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = axis.label,
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    axis.options.forEach { option ->
                                        KpknChip(
                                            text = option.label,
                                            selected = option.selected,
                                            enabled = option.enabled,
                                            onClick = { onSelectOption(axis.axis, option.value) },
                                        )
                                    }
                                }
                            }
                        }
                        model.configurationLine?.let { line ->
                            Text(
                                text = line,
                                style = KpknType.caption,
                                color = if (model.configurationIncomplete) SymbolPalette.energia else WizardColors.text,
                            )
                        }
                    }
                }
            }
            // Los músculos van arriba, a la vista (CF1): aquí solo el patrón y las articulaciones.
            CatalogInvolvementAccordions(
                info = null,
                joints = model.joints,
                patternId = model.patternId,
                startExpanded = startExpanded,
            )
            muscleKnowledge?.let { knowledge ->
                CanonicalKnowledgeOverlay(
                    knowledge = knowledge,
                    onDismiss = { muscleKnowledge = null },
                )
            }
        }
        if (showFooter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                KpknPrimaryPill(
                    text = if (model.isSelected) "Quitar" else "Añadir",
                    onClick = onToggleSelection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = model.canSelect,
                )
            }
        }
    }
}

/**
 * Imágenes del ejercicio (SE6b) con las píldoras de implemento encima (CF1). La foto va a todo el ancho, con radio 20; desliza entre
 * implementos y, al asentarse, avisa del implemento de la página (quien la usa lo elige como hasta ahora). Las píldoras se ven con dos o
 * más implementos, con foto o sin ella. Sin foto ni píldoras no pinta nada.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CatalogExerciseImageCarousel(
    variants: List<ExerciseTechniqueImageVariant>,
    selectedImplementation: String?,
    onImplementationSettled: (String) -> Unit,
    implementOptions: List<CatalogDetailOption>,
    onSelectImplementation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = selectedCatalogImageVariantIndex(variants, selectedImplementation)
    val showPills = implementOptions.size > 1
    // La foto es la fuente del desenfoque de las píldoras: el vidrio emborrona lo que hay detrás.
    val haze = remember { HazeState() }
    val reduced = wizardReducedMotion()
    val pagerState = rememberPagerState(initialPage = selectedIndex ?: 0) { maxOf(variants.size, 1) }
    val currentSelectedImplementation by rememberUpdatedState(selectedImplementation)
    val currentOnImplementationSettled by rememberUpdatedState(onImplementationSettled)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    // Al elegir un implemento, la foto va a su página: animado, o de golpe con movimiento reducido.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null && pagerState.currentPage != selectedIndex) {
            if (reduced) pagerState.scrollToPage(selectedIndex) else pagerState.animateScrollToPage(selectedIndex)
        }
    }
    LaunchedEffect(pagerState, variants) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            // Sin foto para la elección no hay página que avisar: la foto no debe elegir la primera sola.
            if (currentSelectedIndex == null) return@collectLatest
            variants.getOrNull(page)?.implementation?.let { implementation ->
                if (implementation != currentSelectedImplementation) {
                    currentOnImplementationSettled(implementation)
                }
            }
        }
    }

    // Lo que se ve en la foto manda sobre las píldoras: al deslizar la foto, el indicador la sigue.
    val shownImplementation = if (selectedIndex != null) {
        variants.getOrNull(pagerState.currentPage)?.implementation
    } else {
        selectedImplementation
    }

    if (selectedIndex != null) {
        Box(modifier = modifier.height(CatalogPhotoHeight)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(KpknSurface.inset),
            ) {
                Box(modifier = Modifier.fillMaxSize().hazeSource(state = haze)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = variants.size > 1,
                        pageSpacing = 0.dp,
                    ) { page ->
                        val variant = variants[page]
                        Image(
                            painter = painterResource(variant.imageResId),
                            contentDescription = "Ilustración del ejercicio con ${variant.label}",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            if (showPills) {
                // A 12 dp del borde: la zona táctil de 48 dp deja 6 dp hasta la foto, y el vidrio de 36 dp queda 6 dp más arriba.
                CatalogImplementPills(
                    options = implementOptions,
                    shownValue = shownImplementation,
                    haze = haze,
                    onSelect = onSelectImplementation,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                )
            }
        }
    } else if (showPills) {
        CatalogImplementPills(
            options = implementOptions,
            shownValue = shownImplementation,
            haze = haze,
            onSelect = onSelectImplementation,
            modifier = modifier.padding(horizontal = 12.dp),
        )
    }
}

/**
 * Píldoras de implemento (CF1): una fila de vidrio con cada opción y un indicador de tinta que se desliza hasta la elegida. El vidrio mide
 * 36 dp de alto y cada opción tiene una zona táctil de 48. Si no caben, la fila se desplaza en horizontal con un fundido en los bordes.
 * Con movimiento reducido, el indicador salta.
 */
@Composable
private fun CatalogImplementPills(
    options: List<CatalogDetailOption>,
    shownValue: String?,
    haze: HazeState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val chosen = options.indexOfFirst { it.value == shownValue }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(ImplementTouchHeight)
            .testTag(CatalogImplementPillsTag),
    ) {
        val density = LocalDensity.current
        // Reparto igual cuando caben: cada opción ocupa al menos su parte de la fila. Si su texto pide más, se ensancha y la fila se desplaza.
        val share = (maxWidth - ImplementInset * 2) / options.size
        val widths = options.map { option ->
            val text = measurer.measure(AnnotatedString(option.label), KpknType.chip, maxLines = 1, softWrap = false)
            maxOf(share, with(density) { text.size.width.toDp() } + ImplementTextPad * 2)
        }
        // Dónde empieza y cuánto mide la elegida: se suman los anchos ya calculados, sin esperar a medir en pantalla.
        val chosenStart = if (chosen >= 0) widths.take(chosen).fold(0.dp) { sum, width -> sum + width } else 0.dp
        val chosenWidth = if (chosen >= 0) widths[chosen] else 0.dp
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ImplementGlassHeight)
                .clip(KpknShape.pill)
                .kpknHazeEffect(haze)
                .border(1.dp, ImplementEdge, KpknShape.pill),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ImplementInset)
                .horizontalScroll(scroll)
                .scrollEdgeFade(scroll),
        ) {
            Box {
                if (chosen >= 0) {
                    ImplementIndicator(
                        x = with(density) { chosenStart.toPx() },
                        width = with(density) { chosenWidth.toPx() },
                    )
                }
                Row(
                    modifier = Modifier
                        .height(ImplementTouchHeight)
                        .selectableGroup(),
                ) {
                    options.forEachIndexed { index, option ->
                        val selected = index == chosen
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .width(widths[index])
                                .height(ImplementTouchHeight)
                                .selectable(
                                    selected = selected,
                                    enabled = option.enabled,
                                    interactionSource = interaction,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelect(option.value) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.label,
                                style = KpknType.chip,
                                color = when {
                                    selected -> WizardColors.ctaContent
                                    option.enabled -> WizardColors.text
                                    else -> WizardColors.textFaint
                                },
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Indicador de tinta de la píldora elegida (CF1): se desliza con el resorte de la casa (amortiguación 0,8, rigidez media-baja, unos
 * 300 ms) hasta la posición medida. Con movimiento reducido salta.
 */
@Composable
private fun ImplementIndicator(x: Float, width: Float) {
    val spec: AnimationSpec<Float> = if (wizardReducedMotion()) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    }
    val slideX by animateFloatAsState(targetValue = x, animationSpec = spec, label = "implementSlideX")
    val slideWidth by animateFloatAsState(targetValue = width, animationSpec = spec, label = "implementSlideWidth")
    val density = LocalDensity.current
    val top = with(density) { ((ImplementTouchHeight - ImplementIndicatorHeight) / 2).roundToPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset(slideX.roundToInt(), top) }
            .size(width = with(density) { slideWidth.toDp() }, height = ImplementIndicatorHeight)
            .clip(KpknShape.pill)
            .background(WizardColors.text, KpknShape.pill),
    )
}

/**
 * Funde los bordes de una fila que se desplaza, solo por el lado donde aún hay contenido. Es una máscara DstIn sobre la propia fila (LR1:
 * una fila con scroll no se corta con un borde recto).
 */
private fun Modifier.scrollEdgeFade(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadeStart = scroll.value > 0
        val fadeEnd = scroll.value < scroll.maxValue
        if (fadeStart || fadeEnd) {
            val edge = (ImplementEdgeFade.toPx() / size.width).coerceIn(0f, 0.5f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to (if (fadeStart) Color.Transparent else Color.Black),
                        edge to Color.Black,
                        (1f - edge) to Color.Black,
                        1f to (if (fadeEnd) Color.Transparent else Color.Black),
                    ),
                    startX = 0f,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** Cuántos músculos se ven antes de «Ver todos» (CF1). */
private const val MuscleRowsShown = 4

/** Líneas de la descripción antes de «Leer más» (CF1). */
private const val DescriptionLines = 4

/** Espacio duro: «0,5 serie» y «50 %» no se parten entre dos líneas (CF1). */
private const val NonBreakingSpace = '\u00A0'

/**
 * Módulo «Músculos» de la ficha (CF1): siempre abierto, justo debajo del título y antes de la descripción. Los músculos van de más a
 * menos, cada uno con su barra y su aporte por serie. Se ven cuatro y «Ver todos» despliega el resto en el sitio. Tocar un músculo abre
 * su definición canónica, como antes.
 */
@Composable
private fun CatalogMusclesModule(
    exercise: ExerciseMuscleInfo,
    onKnowledge: (CanonicalKnowledge) -> Unit,
) {
    val contributions = remember(exercise) {
        oneSeriesVolumeContributions(exercise).sortedByDescending { it.seriesEquivalent }
    }
    var expanded by remember(exercise) { mutableStateOf(false) }
    val shown = if (expanded) contributions else contributions.take(MuscleRowsShown)
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Músculos · aporte por 1 serie",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
            shown.forEach { contribution ->
                CatalogMuscleRow(contribution = contribution, onKnowledge = onKnowledge)
            }
            if (!expanded && contributions.size > MuscleRowsShown) {
                KpknTextAction(text = "Ver todos", onClick = { expanded = true })
            }
        }
    }
}

/** Una fila del módulo «Músculos» (CF1): nombre y papel, «0,5 serie · 50 %» a la derecha y la barra en el color del músculo. */
@Composable
private fun CatalogMuscleRow(
    contribution: MuscleVolumeContribution,
    onKnowledge: (CanonicalKnowledge) -> Unit,
) {
    val knowledge = canonicalMuscleKnowledgeForVolumeLabel(contribution.muscle)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (knowledge != null) {
                    Modifier.clickable(role = Role.Button, onClick = { onKnowledge(knowledge) })
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contribution.muscle,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = roleVolumeLabel(contribution.role).substringBefore(" · "),
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = muscleFigures(contribution.seriesEquivalent),
                style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.textMuted,
                maxLines = 1,
            )
        }
        KpknProgressBar(
            progress = contribution.seriesEquivalent.toFloat(),
            color = SymbolPalette.musculo,
            trackColor = Color.White.copy(alpha = 0.08f),
            height = 6.dp,
        )
    }
}

/** «1 serie · 100 %», «0,5 series · 50 %»: coma decimal, plural salvo una serie y espacio duro antes de la unidad (CF1). */
internal fun muscleFigures(value: Double): String {
    val amount = NativeProgressionText.formatKg(value.coerceIn(0.0, 1.0))
    val unit = if (amount == "1") "serie" else "series"
    val percent = formatVolumePercent(value).replace("%", "$NonBreakingSpace%")
    return "$amount$NonBreakingSpace$unit · $percent"
}

/**
 * Descripción de la ficha (CF1): recortada a cuatro líneas, con «Leer más» si no cabe y «Leer menos» para volver. Si el texto cabe entero,
 * no hay botón.
 */
@Composable
private fun CatalogDescription(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = text,
            style = KpknType.body,
            color = WizardColors.textMuted,
            maxLines = if (expanded) Int.MAX_VALUE else DescriptionLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) clipped = result.hasVisualOverflow },
        )
        if (clipped) {
            KpknTextAction(
                text = if (expanded) "Leer menos" else "Leer más",
                onClick = { expanded = !expanded },
            )
        }
    }
}

/**
 * Los módulos plegables de la ficha (SE6b): «Músculos · aporte por 1 serie» y «Articulaciones», cada uno con su cabecera tocable y un
 * `ChevronDown` que gira. Plegados por defecto, como antes. Con movimiento reducido, sin animación.
 */
@Composable
internal fun CatalogInvolvementAccordions(
    info: ExerciseMuscleInfo?,
    joints: List<JointInvolvementV2>,
    patternId: String? = null,
    /** Abiertos desde el principio: lo usan la hoja de render y los tests; en la app van plegados. */
    startExpanded: Boolean = false,
) {
    var muscleExpanded by remember { mutableStateOf(startExpanded) }
    var jointExpanded by remember { mutableStateOf(startExpanded) }
    var canonicalExplain by remember { mutableStateOf<CanonicalKnowledge?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        patternId?.let { id ->
            canonicalPatternKnowledge(id)?.let { pattern ->
                KpknChip(
                    text = "Patrón · ${pattern.name}",
                    selected = false,
                    onClick = { canonicalExplain = pattern },
                )
            }
        }
        info?.let { muscleInfo ->
            CatalogFoldableModule(
                title = "Músculos · aporte por 1 serie",
                expanded = muscleExpanded,
                onToggle = { muscleExpanded = !muscleExpanded },
            ) {
                MuscleInvolvementSection(
                    exercise = muscleInfo,
                    onKnowledge = { canonicalExplain = it },
                )
            }
        }
        if (joints.isNotEmpty()) {
            CatalogFoldableModule(
                title = "Articulaciones",
                expanded = jointExpanded,
                onToggle = { jointExpanded = !jointExpanded },
            ) {
                JointInvolvementSection(
                    joints = joints,
                    onKnowledge = { canonicalExplain = it },
                )
            }
        }
        canonicalExplain?.let { knowledge ->
            CanonicalKnowledgeOverlay(
                knowledge = knowledge,
                onDismiss = { canonicalExplain = null },
            )
        }
    }
}

/** Un módulo con cabecera tocable y `ChevronDown` (SE6b). El contenido aparece bajo la cabecera al desplegarlo. */
@Composable
private fun CatalogFoldableModule(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = wizardReducedMotion()
    KpknModule {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Plegar" else "Desplegar",
                    onClick = onToggle,
                )
                .semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = KpknLineIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                tint = WizardColors.textMuted,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduced) EnterTransition.None else fadeIn() + expandVertically(),
            exit = if (reduced) ExitTransition.None else fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/**
 * Aporte de cada músculo por una serie (SE6b): nombre en `bodyStrong`, su papel en `caption` y, a la derecha, «0,5 serie · 50 %» con cifras
 * tabulares, y debajo la barra en el color del músculo. Con [onKnowledge], tocar el músculo abre su definición canónica si la hay.
 */
@Composable
internal fun MuscleInvolvementSection(
    exercise: ExerciseMuscleInfo,
    onKnowledge: (CanonicalKnowledge) -> Unit = {},
) {
    val contributions = remember(exercise) { oneSeriesVolumeContributions(exercise) }
    if (contributions.isEmpty()) {
        Text(
            text = "No hay un desglose canónico disponible para esta configuración.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        contributions.forEach { contribution ->
            val knowledge = canonicalMuscleKnowledgeForVolumeLabel(contribution.muscle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (knowledge != null) {
                            Modifier.clickable(role = Role.Button, onClick = { onKnowledge(knowledge) })
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contribution.muscle,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = roleVolumeLabel(contribution.role).substringBefore(" · "),
                            style = KpknType.caption,
                            color = WizardColors.textFaint,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "${formatSeriesEquivalent(contribution.seriesEquivalent)} · ${formatVolumePercent(contribution.seriesEquivalent)}",
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.textMuted,
                    )
                }
                KpknProgressBar(
                    progress = contribution.seriesEquivalent.toFloat(),
                    color = SymbolPalette.musculo,
                    height = 6.dp,
                )
            }
        }
    }
}

/**
 * Articulaciones (SE6b): cada una con su nombre, su papel y las acciones en etiquetas inset. Sin borde de tarjeta: se separan con un
 * filete. Tocar una abre su introducción canónica cuando la hay.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JointInvolvementSection(
    joints: List<JointInvolvementV2>,
    onKnowledge: (CanonicalKnowledge) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        joints.forEachIndexed { index, joint ->
            val knowledge = canonicalJointKnowledge(joint.jointId)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (knowledge != null) {
                            Modifier.clickable(role = Role.Button, onClick = { onKnowledge(knowledge) })
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = jointLabel(joint.jointId),
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = jointRoleLabel(joint.role),
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                if (joint.actions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        joint.actions.forEach { action ->
                            Box(
                                modifier = Modifier
                                    .clip(KpknShape.pill)
                                    .background(KpknSurface.inset)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = sentenceCase(action),
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
            if (index < joints.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(KpknSurface.divider),
                )
            }
        }
        Text(
            text = "Toca una articulación para abrir su introducción canónica cuando esté disponible.",
            style = KpknType.caption,
            color = WizardColors.textFaint,
        )
    }
}

/** «flexion» o «rotacion_externa» → «Flexión»… sin mayúsculas en el resto: tipo frase para las etiquetas de acción. */
private fun sentenceCase(value: String): String =
    value.replace('_', ' ').trim().lowercase().replaceFirstChar { it.uppercase() }
