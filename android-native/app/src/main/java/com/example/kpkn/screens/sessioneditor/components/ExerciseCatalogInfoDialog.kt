package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.PagerDots
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.flow.collectLatest

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
    val selectionLabel: String? = null,
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
        )
    }
}

/**
 * Cuerpo de la ficha (SE6b): la fila superior con «Cerrar definición», las imágenes a todo el ancho con radio 20 y `PagerDots`, el nombre
 * en `title`, la línea de músculo, material y patrón en `caption`, la descripción en `body`, la configuración como módulo, los módulos
 * plegables de músculos y articulaciones, y al pie «Añadir» o «Quitar». Va suelto en los tests y en la hoja de render.
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
) {
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
            if (model.imageVariants.isNotEmpty()) {
                CatalogExerciseImageCarousel(
                    variants = model.imageVariants,
                    selectedImplementation = model.selectedImplementation,
                    onImplementationSettled = onImplementationSettled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(248.dp),
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
            model.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            }
            if (model.axes.isNotEmpty() || model.configurationLine != null) {
                KpknModule(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Configuración",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                        model.axes.forEach { axis ->
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
            CatalogInvolvementAccordions(
                info = model.muscleInfo,
                joints = model.joints,
                patternId = model.patternId,
                startExpanded = startExpanded,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            KpknPrimaryPill(
                text = model.selectionLabel ?: if (model.isSelected) "Quitar" else "Añadir",
                onClick = onToggleSelection,
                modifier = Modifier.fillMaxWidth(),
                enabled = model.canSelect,
            )
        }
    }
}

/**
 * Imágenes del ejercicio (SE6b): a todo el ancho, con radio 20 y `PagerDots` si hay varias. Desliza entre implementos y, al asentarse,
 * avisa del implemento de la página (quien la usa lo elige como hasta ahora). Sin imágenes no pinta nada.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CatalogExerciseImageCarousel(
    variants: List<ExerciseTechniqueImageVariant>,
    selectedImplementation: String?,
    onImplementationSettled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (variants.isEmpty()) return

    val selectedIndex = selectedCatalogImageVariantIndex(variants, selectedImplementation) ?: return
    val pagerState = rememberPagerState(initialPage = selectedIndex) { variants.size }
    val currentSelectedImplementation by rememberUpdatedState(selectedImplementation)
    val currentOnImplementationSettled by rememberUpdatedState(onImplementationSettled)

    LaunchedEffect(selectedIndex) {
        pagerState.scrollToPage(selectedIndex)
    }
    LaunchedEffect(pagerState, variants) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            variants.getOrNull(page)?.implementation?.let { implementation ->
                if (implementation != currentSelectedImplementation) {
                    currentOnImplementationSettled(implementation)
                }
            }
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(KpknSurface.inset),
        ) {
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
        if (variants.size > 1) {
            PagerDots(
                count = variants.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
