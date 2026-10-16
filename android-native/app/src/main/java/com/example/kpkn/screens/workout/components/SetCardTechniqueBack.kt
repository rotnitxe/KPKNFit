package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.workout.SetTechniqueScope
import com.example.kpkn.domain.workout.techniqueScope
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.LiveSetPrPolicy
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

internal data class TechniqueCheckRow(
    val weightText: String = "",
    val repsText: String = RestPausePlanDefaults.Reps.toString(),
    val restText: String = RestPausePlanDefaults.PauseSeconds.toString(),
    val done: Boolean = false,
)

private const val LiveDropSetMax = 5
/** Relleno horizontal de una celda de cifra, hueco entre la cifra y su unidad, y margen de seguridad de la anchura mínima (LR1c2). */
private val TechniqueCellPadding = 4.dp
private val TechniqueUnitGap = 4.dp
private val TechniqueCellSlack = 2.dp

/** Etiqueta de prueba del círculo «Hecho» de cada fila de drops o rest-pause (LR0). */
internal const val TechniqueDoneToggleTag = "technique_done_toggle"

/**
 * Reverso de técnica de la tarjeta de la serie (LR1c2): la piel de LR1a. Es un `module` (con aviso teñido si la serie es fallida), los
 * conmutadores son píldoras (activa en tinta), las filas son celdas `inset` con la cifra y la unidad, y la nota es un `KpknTextField`.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SetCardTechniqueBack(
    currentSet: ExerciseSet,
    coverWeightLabel: String,
    coverValueLabel: String,
    coverIntensityLabel: String?,
    mainWeight: Double,
    mainReps: Int,
    sessionAccentColor: Color,
    isFailedSet: Boolean,
    onFailedSetChange: (Boolean) -> Unit,
    onFlipBack: () -> Unit,
    onCompleteTechniques: (dropSets: List<DropSetData>, restPauses: List<RestPauseData>) -> Unit,
    noteText: String = "",
    onNoteChange: (String) -> Unit = {},
    initialDropEnabled: Boolean = false,
    initialRestPauseEnabled: Boolean = false,
    onDropEnabledChange: (Boolean) -> Unit = {},
    onRestPauseEnabledChange: (Boolean) -> Unit = {},
    showPrMediaShortcut: Boolean = false,
    onOpenPrMedia: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val plannedGuide = remember(currentSet) {
        // Volume-replaced editor plans run inside the front card controls.
        // Never arm the manual back-face editors for them: the back card is
        // a flip replacement, not an overlay, and must not resurrect a
        // second technique surface on top of the scheduled flow.
        if (currentSet.techniqueScope() == SetTechniqueScope.VOLUME_REPLACED) null
        else currentSet.resolvePlannedTechniqueGuide()
    }
    var dropEnabled by remember(currentSet.id) {
        mutableStateOf(initialDropEnabled || plannedGuide?.kind == TechniqueType.DROP_SET)
    }
    var restPauseEnabled by remember(currentSet.id) {
        mutableStateOf(initialRestPauseEnabled || plannedGuide?.kind == TechniqueType.REST_PAUSE)
    }
    var dropRows by remember(currentSet.id, mainWeight, mainReps) {
        val count = plannedGuide?.count?.takeIf { plannedGuide.kind == TechniqueType.DROP_SET }
            ?: DropSetPlanDefaults.DefaultDrops
        val suggestions = suggestedDropLoadsForMainSet(mainWeight, mainReps, count)
        mutableStateOf(
            suggestions.map { load ->
                TechniqueCheckRow(
                    weightText = if (load > 0) load.toTrimmedNumberString() else "",
                    repsText = RestPausePlanDefaults.Reps.toString(),
                )
            }.ifEmpty {
                listOf(TechniqueCheckRow())
            },
        )
    }
    var restPauseRows by remember(currentSet.id) {
        val count = plannedGuide?.count?.takeIf { plannedGuide.kind == TechniqueType.REST_PAUSE }
            ?: RestPausePlanDefaults.DefaultCount
        mutableStateOf(
            List(count.coerceIn(RestPausePlanDefaults.MinCount, RestPausePlanDefaults.MaxCount)) {
                TechniqueCheckRow(repsText = RestPausePlanDefaults.Reps.toString())
            },
        )
    }
    var committed by remember(currentSet.id) { mutableStateOf(false) }

    LaunchedEffect(dropEnabled) { onDropEnabledChange(dropEnabled) }
    LaunchedEffect(restPauseEnabled) { onRestPauseEnabledChange(restPauseEnabled) }
    // Con todas las filas marcadas como hechas, la técnica se registra en la serie (LR0).
    LaunchedEffect(dropEnabled, restPauseEnabled, dropRows, restPauseRows, committed) {
        if (committed) return@LaunchedEffect
        val dropsReady = !dropEnabled || shouldAutoCommitTechniqueRows(dropRows.map { it.done })
        val pausesReady = !restPauseEnabled || shouldAutoCommitTechniqueRows(restPauseRows.map { it.done })
        val hasTechnique = dropEnabled || restPauseEnabled
        if (hasTechnique && dropsReady && pausesReady &&
            ((dropEnabled && dropRows.isNotEmpty()) || (restPauseEnabled && restPauseRows.isNotEmpty()))
        ) {
            val submitted = commit(dropRows, restPauseRows, dropEnabled, restPauseEnabled, onCompleteTechniques)
            if (submitted) committed = true
        }
    }

    KpknModule(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        accent = if (isFailedSet) WizardColors.danger else null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KpknIconButton(
                    icon = KpknLineIcons.ChevronLeft,
                    contentDescription = "Volver a la serie",
                    onClick = onFlipBack,
                )
                Text(
                    "Avanzado",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    modifier = Modifier.weight(1f),
                )
                if (showPrMediaShortcut && onOpenPrMedia != null) {
                    PrMediaShortcut(
                        text = LiveSetPrPolicy.SHORTCUT_LABEL,
                        accent = sessionAccentColor,
                        onClick = onOpenPrMedia,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SoftToggleChip(
                            text = if (dropEnabled) "Drop-sets" else "Añadir drop-sets",
                            selected = dropEnabled,
                            onClick = {
                                dropEnabled = !dropEnabled
                                if (dropEnabled) restPauseEnabled = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SoftToggleChip(
                            text = if (restPauseEnabled) "Rest-pause" else "Añadir rest-pause",
                            selected = restPauseEnabled,
                            onClick = {
                                restPauseEnabled = !restPauseEnabled
                                if (restPauseEnabled) dropEnabled = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            dropEnabled -> TechniqueCarouselBlock(
                                rows = dropRows,
                                showWeight = true,
                                onRowsChange = { dropRows = it },
                                onAdd = {
                                    val nextIndex = dropRows.size
                                    val suggested = nextDropWeightKg(
                                        mainWeight = mainWeight,
                                        mainReps = mainReps,
                                        nextIndex = nextIndex,
                                        lastWeightText = dropRows.lastOrNull()?.weightText,
                                    )
                                    dropRows = dropRows + TechniqueCheckRow(
                                        weightText = suggested?.takeIf { it > 0 }?.toTrimmedNumberString().orEmpty(),
                                        repsText = DropSetPlanDefaults.DropReps.toString(),
                                    )
                                },
                                canAdd = dropRows.size < LiveDropSetMax,
                            )
                            restPauseEnabled -> TechniqueCarouselBlock(
                                rows = restPauseRows,
                                showWeight = false,
                                onRowsChange = { restPauseRows = it },
                                onAdd = {
                                    restPauseRows = restPauseRows + TechniqueCheckRow(
                                        repsText = RestPausePlanDefaults.Reps.toString(),
                                        restText = RestPausePlanDefaults.PauseSeconds.toString(),
                                    )
                                },
                                canAdd = restPauseRows.size < RestPausePlanDefaults.MaxCount,
                            )
                            else -> Text(
                                "Elige drop-sets o rest-pause",
                                style = KpknType.caption,
                                color = WizardColors.textFaint,
                            )
                        }
                    }
                }

                KpknTextField(
                    value = noteText,
                    onValueChange = onNoteChange,
                    label = "Notas",
                    placeholder = "Añadir una nota de esta serie",
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TechniquePill(
                text = "Error de ejecución",
                onClick = { onFailedSetChange(!isFailedSet) },
                fill = if (isFailedSet) WizardColors.danger else KpknSurface.inset,
                ink = if (isFailedSet) WizardColors.ctaContent else WizardColors.danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Atajo de la foto del récord: texto en el acento de la sesión sobre una píldora `inset`, sin ripple. */
@Composable
private fun PrMediaShortcut(
    text: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.chip,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TechniqueCarouselBlock(
    rows: List<TechniqueCheckRow>,
    showWeight: Boolean,
    onRowsChange: (List<TechniqueCheckRow>) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean,
) {
    // Las filas ocupan todo el ancho, una debajo de otra; «Añadir» va al final, alineado al inicio (LR1c2).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEachIndexed { index, row ->
            TechniqueCompactRow(
                index = index,
                row = row,
                showWeight = showWeight,
                onChange = { updated ->
                    onRowsChange(rows.toMutableList().also { it[index] = updated })
                },
            )
        }
        if (canAdd) {
            AddTechniqueChip(onAdd = onAdd)
        }
    }
}

@Composable
private fun TechniqueCompactRow(
    index: Int,
    row: TechniqueCheckRow,
    showWeight: Boolean,
    onChange: (TechniqueCheckRow) -> Unit,
) {
    // Anchura mínima de cada celda: la cifra de muestra más larga («102,5» de peso, «12» de reps y «120» de descanso) con su unidad,
    // medida con el texto real a la letra escalada, más el relleno. Así la cifra nunca se corta, ni al 130 % (LR1c2).
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val estiloCifra = KpknType.chip.copy(fontFeatureSettings = "'tnum' 1")
    val estiloUnidad = KpknType.caption
    fun anchoDe(texto: String, estilo: TextStyle): Dp =
        with(density) { measurer.measure(AnnotatedString(texto), estilo).size.width.toDp() }
    val unidadKg = anchoDe("kg", estiloUnidad)
    val unidadR = anchoDe("r", estiloUnidad)
    val unidadS = anchoDe("s", estiloUnidad)
    val minPeso = anchoDe("102,5", estiloCifra) + TechniqueCellPadding * 2 + TechniqueUnitGap + unidadKg + TechniqueCellSlack
    val minReps = anchoDe("12", estiloCifra) + TechniqueCellPadding * 2 + TechniqueUnitGap + unidadR + TechniqueCellSlack
    val minDescanso = anchoDe("120", estiloCifra) + TechniqueCellPadding * 2 + TechniqueUnitGap + unidadS + TechniqueCellSlack
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TechniqueDoneToggle(
            done = row.done,
            onToggle = { onChange(row.copy(done = !row.done)) },
        )
        Text(
            "${index + 1}",
            modifier = Modifier.width(12.dp),
            style = KpknType.caption,
            color = WizardColors.textFaint,
        )
        if (showWeight) {
            TechniqueValueCell(
                value = row.weightText,
                onValueChange = { onChange(row.copy(weightText = it)) },
                unit = "kg",
                keyboardType = KeyboardType.Decimal,
                unitWidth = unidadKg,
                minWidth = minPeso,
                modifier = Modifier.weight(1.15f),
            )
        }
        TechniqueValueCell(
            value = row.repsText,
            onValueChange = { onChange(row.copy(repsText = it)) },
            unit = "r",
            keyboardType = KeyboardType.Number,
            unitWidth = unidadR,
            minWidth = minReps,
            modifier = Modifier.weight(0.85f),
        )
        if (!showWeight) {
            TechniqueValueCell(
                value = row.restText,
                onValueChange = { onChange(row.copy(restText = it)) },
                unit = "s",
                keyboardType = KeyboardType.Number,
                unitWidth = unidadS,
                minWidth = minDescanso,
                modifier = Modifier.weight(0.85f),
            )
        }
    }
}

/** Celda de cifra de una fila de drop o de rest-pause: `inset`, la cifra centrada y la unidad al final, con teclado decimal si toca. */
@Composable
private fun TechniqueValueCell(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    keyboardType: KeyboardType,
    unitWidth: Dp,
    minWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .widthIn(min = minWidth)
            .height(32.dp)
            .clip(shape)
            .background(KpknSurface.inset, shape)
            .padding(horizontal = TechniqueCellPadding),
        singleLine = true,
        textStyle = KpknType.chip.copy(
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "'tnum' 1",
        ),
        cursorBrush = SolidColor(WizardColors.text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = unitWidth + TechniqueUnitGap),
                    contentAlignment = Alignment.Center,
                ) {
                    inner()
                }
                Text(
                    unit,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                    maxLines = 1,
                )
            }
        },
    )
}

/** «Añadir» de la fila de drops o de rest-pause: píldora con filete de 1 dp en blanco al 12 %, sobre el módulo (sin relleno nuevo). */
@Composable
private fun AddTechniqueChip(onAdd: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .clip(KpknShape.pill)
                .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                KpknLineIcons.Plus,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = WizardColors.text,
            )
            Text(
                "Añadir",
                style = KpknType.chip,
                color = WizardColors.text,
                maxLines = 1,
            )
        }
    }
}

/**
 * Casilla «Hecho» de una fila (LR1c2): tinta cuando está marcada y `inset` con filete de 1 dp al 24 % mientras no, para que se vea sobre
 * el módulo. Conserva la etiqueta de prueba y la descripción.
 */
@Composable
private fun TechniqueDoneToggle(
    done: Boolean,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(20.dp)
            .testTag(TechniqueDoneToggleTag)
            .clip(shape)
            .background(if (done) WizardColors.text else KpknSurface.inset, shape)
            .border(1.dp, Color.White.copy(alpha = 0.24f), shape)
            .toggleable(
                value = done,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                KpknLineIcons.Check,
                contentDescription = "Hecho",
                modifier = Modifier.size(14.dp),
                tint = WizardColors.ctaContent,
            )
        }
    }
}

/**
 * Píldora de texto tocable sin ripple de Material: zona táctil de 48 dp y píldora de 40 dp como mínimo, con el rótulo en `chip` (hasta
 * dos líneas antes que cortarse). Sirve para los conmutadores y el «Error de ejecución».
 */
@Composable
private fun TechniquePill(
    text: String,
    onClick: () -> Unit,
    fill: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    edge: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .clip(KpknShape.pill)
                .background(fill, KpknShape.pill)
                .then(if (edge != null) Modifier.border(1.dp, edge, KpknShape.pill) else Modifier)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.chip,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Conmutador de drop-sets o rest-pause: la opción activa en tinta con texto negro; la inactiva, `inset` con filete al 8 %. */
@Composable
private fun SoftToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TechniquePill(
        text = text,
        onClick = onClick,
        fill = if (selected) WizardColors.text else KpknSurface.inset,
        ink = if (selected) WizardColors.ctaContent else WizardColors.text,
        modifier = modifier,
        edge = if (selected) null else Color.White.copy(alpha = 0.08f),
    )
}

internal fun commit(
    dropRows: List<TechniqueCheckRow>,
    restPauseRows: List<TechniqueCheckRow>,
    dropEnabled: Boolean,
    restPauseEnabled: Boolean,
    onCompleteTechniques: (List<DropSetData>, List<RestPauseData>) -> Unit,
): Boolean {
    val drops = if (dropEnabled) {
        dropRows.mapNotNull { row ->
            // El teclado decimal escribe la coma: se lee como punto antes de convertir (LR0).
            val weight = row.weightText.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val reps = row.repsText.toIntOrNull() ?: return@mapNotNull null
            if (weight <= 0.0 || reps <= 0) null else DropSetData(weight, reps)
        }
    } else {
        emptyList()
    }
    val pauses = if (restPauseEnabled) {
        restPauseRows.mapNotNull { row ->
            val reps = row.repsText.toIntOrNull() ?: return@mapNotNull null
            if (reps <= 0) null else RestPauseData(
                restTime = row.restText.toIntOrNull()?.coerceAtLeast(0) ?: RestPausePlanDefaults.PauseSeconds,
                reps = reps,
            )
        }
    } else {
        emptyList()
    }
    if (drops.isEmpty() && pauses.isEmpty()) return false
    onCompleteTechniques(drops, pauses)
    return true
}

/**
 * Peso del drop que se añade en [nextIndex]: la sugerencia del plan o, si el plan no llega (4.º y 5.º drop), el último peso menos un
 * paso. El último peso lo escribe la persona con el teclado decimal, así que «62,5» cuenta como 62,5 (LR1c2).
 */
internal fun nextDropWeightKg(
    mainWeight: Double,
    mainReps: Int,
    nextIndex: Int,
    lastWeightText: String?,
): Double? {
    val suggested = suggestedDropLoadsForMainSet(
        mainWeight,
        mainReps,
        (nextIndex + 1).coerceAtMost(DropSetPlanDefaults.MaxDrops),
    ).getOrNull(nextIndex)
    return suggested ?: lastWeightText?.replace(',', '.')?.toDoubleOrNull()?.minus(DropSetPlanDefaults.DropKg)
}
