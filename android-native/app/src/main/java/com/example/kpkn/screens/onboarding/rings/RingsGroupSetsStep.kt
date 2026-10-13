package com.example.kpkn.screens.onboarding.rings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.domain.onboarding.SetupRingsMapper
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.CountRuler
import com.example.kpkn.screens.onboarding.design.entreno.RingsMuscleGlyph
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.dayFullName
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Series por grupo que admite la regla (0–20, el mismo rango que el reductor). */
private val SETS_RANGE = 0..20

/** Símbolo de cada grupo en el mosaico: 64 dp. */
private val GroupGlyph = 64.dp

/** Punto verde de un grupo que responde, y el hueco que lo separa del nombre (el mismo en todas las celdas). */
private val GroupDot = 4.dp
private val GroupDotGap = 6.dp

/** Separación entre las dos filas del mosaico. */
private val MosaicRowGap = 16.dp

/** Columnas del mosaico: tres grupos por fila, dos filas. */
private const val MOSAIC_COLUMNS = 3

/** Opacidad de un símbolo sin responder: atenuado, pero legible a 64 dp. */
private const val UNANSWERED_ALPHA = 0.5f

/** Fundido del nombre del grupo en el editor al cambiar de grupo. */
private const val TITLE_FADE_MS = 160

/** Espera tras soltar la regla de un grupo sin respuesta antes de pasar la selección al siguiente sin responder. */
private const val RULER_RELEASE_ADVANCE_MS = 400L

/**
 * Paso `RINGS_GROUP_SETS`: las series por grupo de las últimas 72 h. Seis símbolos en mosaico de 3 × 2 y, debajo, un solo editor
 * en el mismo sitio con la regla del grupo elegido. «No lo sé» es un interruptor de texto que deja el grupo sin dato. Omitible:
 * sin respuesta, el paso no cuenta series.
 */
@Composable
internal fun RingsGroupSetsStep(state: SetupWizardState, vm: SetupWizardViewModel, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    RingsGroupSetsContent(
        answers = state.draft.ringsAnswers,
        today = today,
        onSets = { group, sets -> vm.setGroupSets(group, sets) },
        modifier = modifier,
    )
}

/**
 * Cuerpo del paso sin estado propio (lo usa también la hoja de render). [onSets] escribe las series de un grupo; `null` es «No
 * lo sé». [initialSelected] es el grupo que edita el editor al empezar; sin él, el primero sin responder.
 */
@Composable
internal fun RingsGroupSetsContent(
    answers: SetupRingsAnswers?,
    today: LocalDate,
    onSets: (RingsMuscleGroup, Int?) -> Unit,
    modifier: Modifier = Modifier,
    initialSelected: RingsMuscleGroup? = null,
) {
    val declared = answers?.sessionDaysState == RingsSessionDaysState.DECLARED
    val oldest = if (declared) {
        answers?.trainedDayOffsets.orEmpty()
            .filter { it <= SetupRingsMapper.GROUP_SETS_MAX_DAYS_BACK }
            .maxOrNull()
    } else {
        null
    }
    // «Desde el martes.» pasa a «desde el martes» para la línea del editor: «Piernas · desde el martes».
    val since = ringsWindowStartLabel(oldest, today)?.removeSuffix(".")?.replaceFirstChar { it.lowercaseChar() }
    val scope = rememberCoroutineScope()
    val latestAnswers by rememberUpdatedState(answers)
    var editing by remember { mutableStateOf(initialSelected ?: firstUnansweredRingsGroup(answers)) }
    val gesture = remember { RingsRulerGesture() }

    /** Elige el grupo del editor. Una selección automática pendiente queda anulada. */
    fun select(group: RingsMuscleGroup) {
        gesture.advance?.cancel()
        editing = group
    }

    /** Al tocar la regla: anota si el grupo ya tenía respuesta y cancela un salto pendiente. */
    val onRulerPress: () -> Unit = {
        gesture.advance?.cancel()
        gesture.answeredAtPress = ringsGroupAnswered(latestAnswers, editing)
    }

    /**
     * Al soltar la regla de un grupo sin respuesta, tras 400 ms la selección pasa al siguiente sin responder, pero solo si el
     * gesto le dio una cifra: un desplazamiento de la página sobre la regla no mueve nada.
     */
    val onRulerRelease: () -> Unit = {
        val released = editing
        if (!gesture.answeredAtPress) {
            gesture.advance = scope.launch {
                delay(RULER_RELEASE_ADVANCE_MS)
                if (ringsGroupAnswered(latestAnswers, released)) {
                    nextUnansweredRingsGroup(released, latestAnswers)?.let { editing = it }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        RingsMuscleGroup.entries.chunked(MOSAIC_COLUMNS).forEachIndexed { row, groups ->
            if (row > 0) Spacer(Modifier.height(MosaicRowGap))
            Row(modifier = Modifier.fillMaxWidth()) {
                groups.forEach { group ->
                    RingsGroupCell(
                        group = group,
                        sets = answers?.groupSets?.get(group),
                        unknown = answers?.groupSetsUnknown?.contains(group) == true,
                        selected = group == editing,
                        onSelect = { select(group) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(WizardSpacing.sectionGap))
        val chosen = editing
        RingsGroupEditor(
            group = chosen,
            sets = answers?.groupSets?.get(chosen),
            unknown = answers?.groupSetsUnknown?.contains(chosen) == true,
            since = since,
            onWrite = { sets -> onSets(chosen, sets) },
            onRulerPress = onRulerPress,
            onRulerRelease = onRulerRelease,
        )
    }
}

/**
 * Una celda del mosaico: el símbolo del grupo de 64 dp, su nombre y la cifra («12 series», «No lo sé» o «—»). Sin caja. Responder
 * deja un punto verde de 4 dp en el símbolo; sin responder, la celda se atenúa. Un filete de acento marca la que edita el editor.
 */
@Composable
private fun RingsGroupCell(
    group: RingsMuscleGroup,
    sets: Int?,
    unknown: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val answered = sets != null || unknown
    val lit = answered || selected
    val summary = when {
        sets != null -> seriesLabel(sets)
        unknown -> "No lo sé"
        else -> "Sin responder"
    }
    // La altura de la cifra y de la unidad sale de su tipografía en el tamaño actual: al crecer la letra, la celda crece con ella.
    val density = LocalDensity.current
    val numberHeight = with(density) { WizardTypography.controlValue.lineHeight.toDp() }
    val unitHeight = with(density) { WizardTypography.note.lineHeight.toDp() }
    Column(
        modifier = modifier
            .testTag("rings-group-${group.name}")
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) { contentDescription = "${group.label}: $summary" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(GroupGlyph).alpha(if (lit) 1f else UNANSWERED_ALPHA)) {
            RingsMuscleGlyph(muscle = group.muscle(), modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        // El punto verde va tras el nombre; su hueco va también a la izquierda, así el nombre queda centrado tenga o no punto.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(GroupDot + GroupDotGap))
            Text(
                text = group.label,
                style = WizardTypography.caption,
                color = when {
                    selected -> SymbolPalette.musculo
                    lit -> WizardColors.text
                    else -> WizardColors.textMuted
                },
            )
            Spacer(Modifier.width(GroupDotGap))
            Box(Modifier.size(GroupDot).background(if (answered) SymbolPalette.ok else Color.Transparent, CircleShape))
        }
        // El subrayado del grupo que edita va bajo el nombre; ocupa su sitio aunque no esté elegido, así no cambia el alto.
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .fillMaxWidth(0.56f)
                .height(2.dp)
                .background(if (selected) SymbolPalette.musculo else Color.Transparent),
        )
        Box(modifier = Modifier.height(numberHeight), contentAlignment = Alignment.Center) {
            when {
                sets != null -> Text(text = sets.toString(), style = WizardTypography.controlValue, color = WizardColors.text)
                unknown -> Text(text = "No lo sé", style = WizardTypography.note, color = WizardColors.textMuted)
                else -> Text(text = "—", style = WizardTypography.controlValue, color = WizardColors.textMuted)
            }
        }
        Box(modifier = Modifier.height(unitHeight), contentAlignment = Alignment.TopCenter) {
            if (sets != null) {
                Text(
                    text = if (sets == 1) "serie" else "series",
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                )
            }
        }
    }
}

/**
 * El editor único de las series: la línea del grupo («Piernas · desde el martes») con «No lo sé» a la derecha y, debajo, la regla
 * de 0 a 20. Va siempre en el mismo sitio, así que la página no crece en vertical al elegir otro grupo.
 */
@Composable
private fun RingsGroupEditor(
    group: RingsMuscleGroup,
    sets: Int?,
    unknown: Boolean,
    since: String?,
    onWrite: (Int?) -> Unit,
    onRulerPress: () -> Unit,
    onRulerRelease: () -> Unit,
) {
    val reduced = wizardReducedMotion()
    Column(modifier = Modifier.fillMaxWidth().testTag("rings-group-editor")) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Crossfade(
                targetState = group,
                modifier = Modifier.weight(1f),
                animationSpec = if (reduced) snap<Float>() else tween<Float>(TITLE_FADE_MS),
                label = "ringsGroupTitle",
            ) { shown ->
                Text(
                    text = if (since == null) shown.label else "${shown.label} · $since",
                    style = WizardTypography.controlLabel,
                    color = WizardColors.text,
                    modifier = Modifier.testTag("rings-group-window"),
                )
            }
            RingsTextSwitch(
                label = "No lo sé",
                checked = unknown,
                // Apagarlo no escribe nada: la persona elige la cifra en la regla.
                onCheckedChange = { checked -> if (checked) onWrite(null) },
                tag = "rings-group-unknown-${group.name}",
            )
        }
        // Cada grupo tiene su propia regla: empieza en su valor (o en el centro) sin recorrer la distancia desde el grupo anterior.
        key(group) {
            CountRuler(
                range = SETS_RANGE,
                value = sets,
                onValue = { value -> onWrite(value) },
                unitLabel = "series",
                tag = "rings-group-ruler-${group.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    // Mira el gesto sin consumirlo: el primer contacto y el último dedo que se levanta.
                    .pointerInput(group) {
                        awaitPointerEventScope {
                            var down = false
                            while (true) {
                                val pressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                                if (pressed && !down) onRulerPress()
                                if (!pressed && down) onRulerRelease()
                                down = pressed
                            }
                        }
                    },
            )
        }
    }
}

/** Estado del gesto sobre la regla de un grupo: si ya tenía respuesta al tocarla y el salto automático pendiente. */
private class RingsRulerGesture {
    var answeredAtPress = false
    var advance: Job? = null
}

/** Un grupo responde con una cifra o con «No lo sé». */
private fun ringsGroupAnswered(answers: SetupRingsAnswers?, group: RingsMuscleGroup): Boolean =
    answers?.groupSets?.containsKey(group) == true || answers?.groupSetsUnknown?.contains(group) == true

/** El primer grupo sin respuesta, en el orden del mosaico; si todos responden, el primero. */
internal fun firstUnansweredRingsGroup(answers: SetupRingsAnswers?): RingsMuscleGroup =
    RingsMuscleGroup.entries.firstOrNull { !ringsGroupAnswered(answers, it) } ?: RingsMuscleGroup.entries.first()

/** El siguiente grupo sin respuesta después de [from], dando la vuelta al mosaico; `null` si no queda ninguno. */
internal fun nextUnansweredRingsGroup(from: RingsMuscleGroup, answers: SetupRingsAnswers?): RingsMuscleGroup? {
    val groups = RingsMuscleGroup.entries
    val start = groups.indexOf(from)
    return (1 until groups.size)
        .map { groups[(start + it) % groups.size] }
        .firstOrNull { !ringsGroupAnswered(answers, it) }
}

/**
 * La ventana de las series en el lenguaje de la pantalla: «Desde hoy.», «Desde ayer.» o «Desde el martes.», según el día más
 * antiguo entrenado dentro de las 72 h ([oldestOffset]: 0 = hoy). Sin días en la ventana, nada.
 */
internal fun ringsWindowStartLabel(oldestOffset: Int?, today: LocalDate): String? = when (oldestOffset) {
    null -> null
    0 -> "Desde hoy."
    1 -> "Desde ayer."
    else -> "Desde el ${dayFullName(today.minusDays(oldestOffset.toLong()).dayOfWeek.value).lowercase()}."
}

/** El resumen de una celda con series: «1 serie» o «12 series». */
private fun seriesLabel(sets: Int): String = if (sets == 1) "1 serie" else "$sets series"

/** El músculo de [MuscleArt] que representa cada grupo de la pregunta. */
private fun RingsMuscleGroup.muscle(): MuscleSymbol = when (this) {
    RingsMuscleGroup.CHEST -> MuscleSymbol.CHEST
    RingsMuscleGroup.BACK -> MuscleSymbol.BACK
    RingsMuscleGroup.SHOULDERS -> MuscleSymbol.SHOULDERS
    RingsMuscleGroup.ARMS -> MuscleSymbol.ARMS
    RingsMuscleGroup.LEGS -> MuscleSymbol.QUADS
    RingsMuscleGroup.CORE -> MuscleSymbol.ABS
}
