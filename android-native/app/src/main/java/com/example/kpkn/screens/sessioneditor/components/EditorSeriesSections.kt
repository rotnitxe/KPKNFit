package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.screens.sessioneditor.formatMax2Decimals
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSetFormat

/*
 * Secciones de la serie abierta (ed-serie…): carga, repeticiones, intensidad y descanso, tal como las dibuja el lienzo.
 * Son piezas sin lógica de datos: la serie (InlineSetRow, RmSetFocus, AmrapSetFocus…) les pasa los valores y recibe los cambios.
 */

/** Los cuatro tipos de carga con su frase del menú (texto del lienzo). */
internal data class EdLoadType(val mode: LoadModeV2, val label: String, val spoken: String, val description: String, val hint: String)

internal val edLoadTypes = listOf(
    EdLoadType(LoadModeV2.LOAD, "Externa", "Carga externa", "Los kg que mueves. KPKN la estima con tu 1RM.", "estimada con tu 1RM"),
    EdLoadType(LoadModeV2.BODYWEIGHT, "Corporal", "Peso corporal", "Sin carga extra: solo tu cuerpo.", "sin carga extra"),
    EdLoadType(LoadModeV2.LASTRE, "Lastre", "Lastre", "Kg que añades a tu cuerpo, según tu récord con lastre.", "de lastre · según tu récord"),
    EdLoadType(LoadModeV2.ASSISTED, "Asistido", "Asistido", "Kg que te ayudan, según tu récord asistido.", "de ayuda · según tu récord"),
)

/** Cifra en el formato del lienzo: coma decimal y como mucho dos decimales. */
internal fun edNumber(value: Double): String = formatMax2Decimals(value).replace('.', ',')

/** Descanso `m:ss` de los campos del editor. */
internal fun edRest(seconds: Int): String = KpknSetFormat.rest(seconds)

/**
 * `ed-serie` · Carga: «Externa ⌄» (con su menú de cuatro tipos) y, a la derecha, la cifra estimada, que es el botón de Carga inteligente
 * (`move`: el módulo «Carga inteligente» de antes ya no existe). [percentSuffix] añade «· 75 % RM» en el modo RM.
 */
@Composable
internal fun EdLoadSection(
    mode: LoadModeV2,
    weightKg: Double?,
    onChangeMode: (LoadModeV2) -> Unit,
    onOpenSmartLoad: (() -> Unit)?,
    modifier: Modifier = Modifier,
    percentSuffix: String? = null,
) {
    val type = edLoadTypes.first { it.mode == mode }
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    EdSection(label = "Carga", hint = type.hint, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = RoundedCornerShape(22.dp)
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(shape)
                    .background(EdColors.field, shape)
                    // pulido:ed-T30 .ed-tp borde 0,16 s (abierto = #f2eee699)
                    .border(1.dp, if (open) EdColors.open else EdColors.line2, shape)
                    .edAnchor { anchor = it }
                    .clickable(role = Role.Button, onClickLabel = "Elegir el tipo de carga") { open = !open }
                    .semantics { contentDescription = "Tipo de carga: ${type.spoken}" }
                    .padding(start = 16.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(type.label, style = edText(14f, FontWeight.SemiBold, EdColors.ink, 17.5f), maxLines = 1)
                // pulido:ed-T31 .ed-tp svg giro 0,2 s · ed-X07 rotate(180deg) con el menú abierto
                Icon(KpknLineIcons.ChevronDown, null, tint = EdColors.muted, modifier = Modifier.width(16.dp).height(16.dp).rotate(if (open) 180f else 0f))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .then(
                        if (onOpenSmartLoad != null) Modifier.clickable(role = Role.Button, onClickLabel = "Abrir la carga inteligente", onClick = onOpenSmartLoad)
                        else Modifier,
                    )
                    // pulido:ed-J08 smart: parpadeo de la cifra (0,24 s, opacidad .4 → 1) al tocarla
                    .semantics(mergeDescendants = true) { contentDescription = "Carga de la serie" },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    mode == LoadModeV2.BODYWEIGHT -> Text("Peso corporal", style = edText(14f, FontWeight.SemiBold, EdColors.muted, 17.5f), maxLines = 1)
                    weightKg == null -> Text("Sin estimar", style = edText(14f, FontWeight.SemiBold, EdColors.muted, 17.5f), maxLines = 1)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        // .ed-read b: Syne 800, 22 px, interlineado 1, -0,4 de espaciado
                        Text(
                            (if (mode == LoadModeV2.LASTRE) "+" else "") + edNumber(weightKg),
                            modifier = Modifier.alignByBaseline(),
                            style = edDisplay(22f, FontWeight.ExtraBold, EdColors.ink, -0.4f),
                            maxLines = 1,
                        )
                        Text("kg", modifier = Modifier.alignByBaseline(), style = edText(12f, FontWeight.SemiBold, EdColors.muted, 14f), maxLines = 1)
                        if (percentSuffix != null) {
                            Text("· $percentSuffix", modifier = Modifier.alignByBaseline(), style = edText(12f, FontWeight.SemiBold, EdColors.muted, 14f), maxLines = 1)
                        }
                    }
                }
                if (onOpenSmartLoad != null) Icon(KpknLineIcons.ChevronRight, null, tint = EdColors.faint, modifier = Modifier.width(16.dp).height(16.dp))
            }
        }
    }
    val bounds = anchor
    if (open && bounds != null) {
        EdAnchoredMenu(anchor = bounds, width = 280.dp, estimatedHeight = 300.dp, onDismiss = { open = false }) {
            edLoadTypes.forEach { option ->
                EdMenuRow(
                    title = option.label,
                    subtitle = option.description,
                    checked = option.mode == mode,
                    description = "${option.spoken}${if (option.mode == mode) ", elegida" else ""}",
                    onClick = {
                        open = false
                        if (option.mode != mode) onChangeMode(option.mode)
                    },
                )
            }
        }
    }
}

/**
 * `ed-serie` / `ed-serie-rango` · Repeticiones: campo con − y + («Fijas») o dos campos mín y máx («Rango»), con el conmutador pequeño en
 * la misma línea. Se guarda como siempre: `targetReps = máx` y `targetRepsRange`.
 */
@Composable
internal fun EdRepsSection(
    rangeMode: Boolean,
    reps: Int?,
    rangeMin: Int,
    rangeMax: Int,
    onRangeModeChange: (Boolean) -> Unit,
    onStepReps: (Int) -> Unit,
    onCommitReps: (String) -> Unit,
    onStepMin: (Int) -> Unit,
    onCommitMin: (String) -> Unit,
    onStepMax: (Int) -> Unit,
    onCommitMax: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Repeticiones",
) {
    EdSection(
        label = label,
        modifier = modifier,
        trailing = {
            EdMiniSegmented(
                options = listOf("Fijas", "Rango"),
                selected = if (rangeMode) 1 else 0,
                onSelect = { onRangeModeChange(it == 1) },
                description = "Fijas o rango",
            )
        },
    ) {
        if (rangeMode) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EdStepperField(
                    text = rangeMin.toString(),
                    label = "Repeticiones mínimas",
                    small = "mín",
                    compact = true,
                    onStep = onStepMin,
                    canDecrement = rangeMin > 1,
                    canIncrement = rangeMin < rangeMax,
                    onCommitText = onCommitMin,
                    modifier = Modifier.weight(1f),
                    minusDescription = "Menos repeticiones mínimas",
                    plusDescription = "Más repeticiones mínimas",
                )
                EdStepperField(
                    text = rangeMax.toString(),
                    label = "Repeticiones máximas",
                    small = "máx",
                    compact = true,
                    onStep = onStepMax,
                    canDecrement = rangeMax > rangeMin,
                    canIncrement = rangeMax < 100,
                    onCommitText = onCommitMax,
                    modifier = Modifier.weight(1f),
                    minusDescription = "Menos repeticiones máximas",
                    plusDescription = "Más repeticiones máximas",
                )
            }
        } else {
            EdStepperField(
                text = reps?.toString() ?: "—",
                label = "Repeticiones",
                onStep = onStepReps,
                canDecrement = (reps ?: 0) > 1,
                canIncrement = (reps ?: 0) < 100,
                onCommitText = onCommitReps,
                minusDescription = "Menos repeticiones",
                plusDescription = "Más repeticiones",
            )
        }
    }
}

private val intensityOptions = listOf(
    Triple(IntensityMode.RPE, "RPE", "Esfuerzo de 1 a 10"),
    Triple(IntensityMode.RIR, "RIR", "Repeticiones que te dejas en reserva"),
    Triple(IntensityMode.FAILURE, "Fallo", "Hasta no poder más"),
)

/**
 * `ed-serie-intensidad` / `ed-serie-fallo` · Intensidad en un solo campo: la unidad (RPE, RIR o Fallo) vive dentro, a la izquierda, y se
 * cambia con un menú; después van − valor +. Con «Fallo» no hay valor («Hasta el fallo») y debajo salen las técnicas ([beyondFailure]).
 * Sin intensidad (opcional) el campo es el botón punteado «Añadir intensidad».
 */
@Composable
internal fun EdIntensitySection(
    unit: IntensityMode?,
    valueText: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onAdd: () -> Unit,
    onPickUnit: (IntensityMode) -> Unit,
    onStep: (Int) -> Unit,
    onCommitText: (String) -> Unit,
    modifier: Modifier = Modifier,
    beyondFailure: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val current = if (unit == null) null else intensityOptions.firstOrNull { it.first == unit } ?: intensityOptions.first()
    val hint = when (current?.first) {
        null -> "opcional"
        IntensityMode.RPE -> "esfuerzo de 1 a 10"
        IntensityMode.RIR -> "reps que te dejas en reserva"
        else -> "sin valor: hasta no poder más"
    }
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    EdSection(label = "Intensidad", hint = hint, modifier = modifier) {
        if (current == null) {
            EdGhostButton(text = "Añadir intensidad", onClick = onAdd)
        } else {
            val isFailure = current.first == IntensityMode.FAILURE
            EdStepperField(
                text = valueText.ifBlank { "—" },
                label = "Valor de intensidad",
                onStep = onStep,
                canDecrement = canDecrement && !isFailure,
                canIncrement = canIncrement && !isFailure,
                keyboard = if (current.first == IntensityMode.RPE) KeyboardType.Decimal else KeyboardType.Number,
                onCommitText = if (isFailure) null else onCommitText,
                scrubbable = !isFailure,
                showSteppers = !isFailure,
                leading = {
                    Row(
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            // pulido:ed-T33 .ed-unit fondo 0,16 s (#ffffff08)
                            .background(Color.White.copy(alpha = 0.031f))
                            .edAnchor { anchor = it }
                            .clickable(role = Role.Button, onClickLabel = "Elegir la unidad de intensidad") { open = !open }
                            .semantics { contentDescription = "Unidad de intensidad: ${current.second}" },
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(current.second, style = edText(14f, FontWeight.SemiBold, EdColors.ink, 17.5f), maxLines = 1)
                        // pulido:ed-T34 .ed-unit svg giro 0,2 s · ed-X09 rotate(180deg) con el menú abierto
                        Icon(KpknLineIcons.ChevronDown, null, tint = EdColors.muted, modifier = Modifier.width(16.dp).height(16.dp).rotate(if (open) 180f else 0f))
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(EdColors.line2))
                },
                centerContent = if (isFailure) ({
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        EdIcon(KpknLineIcons.Flame, 18.dp, EdColors.faint)
                        Text("Hasta el fallo", style = edText(15f, FontWeight.Medium, EdColors.faint, 18.75f), maxLines = 1)
                    }
                }) else null,
                minusDescription = "Menos intensidad",
                plusDescription = "Más intensidad",
            )
            if (isFailure && beyondFailure != null) {
                Column(Modifier.padding(top = 8.dp), content = beyondFailure)
            }
        }
    }
    val bounds = anchor
    if (open && bounds != null) {
        EdAnchoredMenu(anchor = bounds, width = 280.dp, estimatedHeight = 230.dp, onDismiss = { open = false }) {
            intensityOptions.forEach { (mode, label, description) ->
                EdMenuRow(
                    title = label,
                    subtitle = description,
                    checked = mode == current?.first,
                    description = "$label${if (mode == current?.first) ", elegida" else ""}",
                    onClick = {
                        open = false
                        if (mode != current?.first) onPickUnit(mode)
                    },
                )
            }
        }
    }
}

/** Atajos de descanso de una serie: 0:30 … 5:00 (los de la escena `ed-serie-descanso`). */
internal val EdSetRestPresets = listOf(30, 60, 90, 120, 150, 180, 240, 300)

/**
 * `ed-serie-descanso` · Descanso interactivo: «−15 · ⏱ 2:00 · +15». Arrastrar el tiempo lo cambia (un paso de 15 s cada 16 dp) y tocarlo
 * abre los atajos. El rótulo dice de dónde viene: «Igual que el ejercicio» o «Propio · Restablecer».
 */
@Composable
internal fun EdRestSection(
    seconds: Int,
    inherited: Boolean,
    exerciseSeconds: Int,
    presetsOpen: Boolean,
    onPresetsOpenChange: (Boolean) -> Unit,
    onChange: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Descanso",
    presets: List<Int> = EdSetRestPresets,
    showOrigin: Boolean = true,
) {
    EdSection(
        label = label,
        modifier = modifier,
        gap = 8.dp,
        headerMin = 24.dp,
        trailing = if (showOrigin) ({
            if (inherited) {
                Text("Igual que el ejercicio", style = edText(12f, FontWeight.SemiBold, EdColors.faint, 14.4f), maxLines = 1)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Propio", style = edText(12f, FontWeight.SemiBold, EdColors.ink, 14.4f), maxLines = 1)
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clickable(role = Role.Button, onClickLabel = "Usar el descanso del ejercicio", onClick = onReset)
                            .semantics { contentDescription = "Usar el descanso del ejercicio" }
                            .padding(start = 6.dp, end = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EdIcon(KpknLineIcons.Repeat, 14.dp, EdColors.muted)
                        Text("Restablecer", style = edText(12f, FontWeight.SemiBold, EdColors.muted, 14.4f), maxLines = 1)
                    }
                }
            }
        }) else null,
    ) {
        EdRestField(
            seconds = seconds,
            highlighted = presetsOpen,
            onStep = { dir -> onChange((seconds + dir * 15).coerceIn(0, 900)) },
            onCenterClick = { onPresetsOpenChange(!presetsOpen) },
            description = "Descanso ${edRest(seconds)}: toca para elegir uno rápido",
        )
        if (presetsOpen) {
            // pulido:ed-A06 .ed-presets.anim entrada 0,26 s (6 px + fundido)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presets.forEach { value ->
                    EdPresetButton(text = edRest(value), on = seconds == value, onClick = { onChange(value) })
                }
                if (!inherited) {
                    EdPresetButton(text = "${edRest(exerciseSeconds)} del ejercicio", on = false, onClick = onReset, dashed = true, icon = KpknLineIcons.Repeat)
                }
            }
        }
    }
}

/** `.ed-restf`: el campo −15 · tiempo · +15 (con el reloj dentro) del descanso de una serie o del ejercicio. */
@Composable
internal fun EdRestField(
    seconds: Int,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onCenterClick: (() -> Unit)? = null,
    description: String = "Descanso ${edRest(seconds)}",
    minusDescription: String = "Quitar 15 segundos",
    plusDescription: String = "Sumar 15 segundos",
    stepLabel: Int = 15,
    scrubbable: Boolean = true,
) {
    EdStepperField(
        text = edRest(seconds),
        label = description,
        onStep = onStep,
        modifier = modifier,
        canDecrement = seconds > 0,
        canIncrement = seconds < 900,
        highlighted = highlighted,
        minusText = "−$stepLabel",
        plusText = "+$stepLabel",
        stepWidth = 60.dp,
        scrubbable = scrubbable,
        minusDescription = minusDescription,
        plusDescription = plusDescription,
        keyboard = null,
        onCenterClick = onCenterClick,
        centerContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                EdIcon(KpknLineIcons.Timer, 18.dp, EdColors.muted)
                Text(edRest(seconds), style = edText(20f, FontWeight.SemiBold, EdColors.ink, 24f, true), maxLines = 1)
            }
        },
    )
}
