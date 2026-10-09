package com.example.kpkn.screens.onboarding.rings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.domain.onboarding.SetupRingsMapper
import com.example.kpkn.screens.onboarding.SetupBodyCaption
import com.example.kpkn.screens.onboarding.SetupBodyHint
import com.example.kpkn.screens.onboarding.SetupRetryOperation
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import java.time.LocalDate

/** Alto de la torre del resultado: igual que en la intro, para que la escala y los toques sean los mismos. */
private val ResultTowerHeight = 200.dp

/** Valor de partida visual de un deslizador sin dato ni estimación: queda a mitad y sin texto de porcentaje. */
private const val NeutralStart = 50

/** Grupos del resultado y sus pilares: los 13 de `INITIAL_RECOVERY_MUSCLE_PILLARS`, cada uno en un solo grupo. */
internal val RINGS_MUSCLE_GROUPS: List<Pair<String, List<String>>> = listOf(
    "Pecho" to listOf("Pectorales"),
    "Espalda" to listOf("Dorsales", "Trapecio", "Erectores Espinales"),
    "Hombros" to listOf("Deltoides"),
    "Brazos" to listOf("Bíceps", "Tríceps"),
    "Piernas" to listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas"),
    "Core" to listOf("Abdomen", "Core"),
)

/**
 * Paso `RINGS_RESULT` («Tus RINGS»): la torre con los mismos valores que Inicio, una línea por anillo con de dónde sale, los
 * deslizadores de Energía y Columna y, desplegable, un deslizador por músculo. Sustituye a las filas con botones de edición:
 * la lista de pasos de arriba ya permite volver.
 */
@Composable
internal fun RingsResultStep(state: SetupWizardState, vm: SetupWizardViewModel, modifier: Modifier = Modifier) {
    RingsResultBody(
        coverage = state.ringsCoveragePreview,
        muscleEstimates = state.ringsMuscleEstimates,
        answers = state.draft.ringsAnswers,
        error = state.ringsPreviewError,
        loading = state.ringsPreviewLoading,
        today = LocalDate.now(),
        onCommitChannel = { channel, value -> vm.setRingsManual(channel, value) },
        onCommitMuscle = { pillar, value -> vm.setMuscleManual(pillar, value) },
        onResetMuscles = { vm.resetMuscleManual() },
        onRetry = { vm.retryFailedOperation(SetupRetryOperation.RINGS_PREVIEW) },
        modifier = modifier,
    )
}

/**
 * Cuerpo del resultado sin estado de negocio: lo que se ve con estos datos. Lo usan [RingsResultStep] y las pruebas. Los
 * arrastres (lo que se ve mientras se mueve un deslizador) son estado de la pantalla; al soltar, el dato sube por el callback.
 */
@Composable
internal fun RingsResultBody(
    coverage: RingsCoverage?,
    muscleEstimates: Map<String, Int>,
    answers: SetupRingsAnswers?,
    error: String?,
    loading: Boolean,
    today: LocalDate,
    onCommitChannel: (String, Int) -> Unit,
    onCommitMuscle: (String, Int) -> Unit,
    onResetMuscles: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val manualRings = answers?.manualRings.orEmpty()
    val manualMuscles = answers?.manualMusclesV2.orEmpty()
    // Arrastres en curso: la torre y el número siguen al dedo sin tocar el borrador (ni animar la torre).
    var dragEnergy by remember { mutableStateOf<Int?>(null) }
    var dragColumna by remember { mutableStateOf<Int?>(null) }
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val preview = ringsTowerValues(coverage)
    val tower = TowerRingValues(
        columna = dragColumna ?: preview.columna,
        musculos = preview.musculos,
        energia = dragEnergy ?: preview.energia,
    )
    val explanations = ringsExplanations(answers, coverage, today)
    val dragging = dragEnergy != null || dragColumna != null
    Column(
        modifier = modifier.fillMaxWidth().testTag("rings-result-body"),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.sectionGap),
    ) {
        if (error != null) {
            Text(error, style = WizardTypography.bodySmall, color = WizardColors.danger)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = WizardSpacing.touchTarget)) {
                Text("Reintentar", color = WizardColors.text)
            }
        } else if (loading) {
            SetupBodyHint(text = "Calculando tu punto de partida…")
        }

        Column(
            modifier = Modifier.fillMaxWidth().testTag("rings-result-lines"),
            verticalArrangement = Arrangement.spacedBy(WizardSpacing.titleGap),
        ) {
            KpknTowerRings(
                values = tower,
                animate = !dragging,
                modifier = Modifier.fillMaxWidth().height(ResultTowerHeight).testTag("rings-result-tower"),
            )
            Text(
                text = "Así llegas hoy",
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
            )
            // Una línea por anillo, en el orden de la torre: de arriba abajo, Energía, Músculos y Columna.
            RingLine(RingChannel.ENERGIA, "Energía", tower[RingChannel.ENERGIA], explanations[RecoveryChannelId.SYSTEM])
            RingLine(RingChannel.MUSCULOS, "Músculos", tower[RingChannel.MUSCULOS], explanations[RecoveryChannelId.MUSCULAR])
            RingLine(RingChannel.COLUMNA, "Columna", tower[RingChannel.COLUMNA], explanations[RecoveryChannelId.STRUCTURE])
        }

        RingsQuestionDivider()

        val energyEstimate = coverage?.system?.score
        val energyPerson = dragEnergy ?: manualRings["energia"]
        AdjustRow(
            name = "Energía",
            accent = KpknRingColors.energia,
            tag = "rings-result-energia",
            person = energyPerson,
            estimate = energyEstimate,
            onPreview = { dragEnergy = it },
            onCommit = { value ->
                dragEnergy = null
                onCommitChannel("energia", value)
            },
        )
        val columnaEstimate = coverage?.structure?.score
        val columnaPerson = dragColumna ?: manualRings["columna"]
        AdjustRow(
            name = "Columna",
            accent = KpknRingColors.columna,
            tag = "rings-result-columna",
            person = columnaPerson,
            estimate = columnaEstimate,
            onPreview = { dragColumna = it },
            onCommit = { value ->
                dragColumna = null
                onCommitChannel("columna", value)
            },
        )
        // La nota solo aparece si algún anillo o algún deslizador todavía no tiene dato.
        val someUncalibrated = coverage == null ||
            coverage.muscular.score == null || coverage.system.score == null || coverage.structure.score == null ||
            (energyPerson == null && energyEstimate == null) ||
            (columnaPerson == null && columnaEstimate == null) ||
            RINGS_MUSCLE_GROUPS.flatMap { it.second }.any { pillar ->
                manualMuscles[pillar] == null && muscleEstimates[pillar] == null
            }
        if (someUncalibrated) {
            SetupBodyCaption(text = RINGS_UNCALIBRATED_NOTE)
        }

        RingsQuestionDivider()

        MusclesToggle(expanded = expanded, onClick = { expanded = !expanded })
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("rings-result-muscles"),
                verticalArrangement = Arrangement.spacedBy(WizardSpacing.sectionGap),
            ) {
                RINGS_MUSCLE_GROUPS.forEach { (group, pillars) ->
                    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.titleGap)) {
                        Text(text = group, style = WizardTypography.controlLabel, color = WizardColors.textMuted)
                        pillars.forEach { pillar ->
                            MuscleAdjustRow(
                                pillar = pillar,
                                manual = manualMuscles[pillar],
                                estimate = muscleEstimates[pillar],
                                onCommit = { value -> onCommitMuscle(pillar, value) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = WizardSpacing.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onResetMuscles,
                        enabled = manualMuscles.isNotEmpty(),
                        modifier = Modifier.heightIn(min = WizardSpacing.touchTarget).testTag("rings-result-reset"),
                    ) {
                        Text(
                            text = "Restablecer",
                            style = WizardTypography.controlLabel,
                            color = if (manualMuscles.isNotEmpty()) WizardColors.text else WizardColors.textFaint,
                        )
                    }
                }
                Text(
                    text = "Tus ajustes mandan sobre la estimación.",
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                )
            }
        }
    }
}

/**
 * La torre de «Tus RINGS» desde la cobertura de la vista previa: Columna = estructura, Músculos = muscular y Energía = sistema.
 * Sin dato, null («Sin calibrar»); nunca un 100 % inventado. La usan este resultado y la revisión final, para enseñar los mismos valores.
 */
internal fun ringsTowerValues(coverage: RingsCoverage?): TowerRingValues = TowerRingValues(
    columna = coverage?.structure?.score,
    musculos = coverage?.muscular?.score,
    energia = coverage?.system?.score,
)

/** Un deslizador por pilar: su arrastre en curso vive aquí y no toca el borrador hasta soltar. */
@Composable
private fun MuscleAdjustRow(pillar: String, manual: Int?, estimate: Int?, onCommit: (Int) -> Unit) {
    var drag by remember(pillar) { mutableStateOf<Int?>(null) }
    AdjustRow(
        name = ringsPillarLabel(pillar),
        accent = KpknRingColors.musculos,
        tag = "rings-result-muscle-$pillar",
        person = drag ?: manual,
        estimate = estimate,
        onPreview = { drag = it },
        onCommit = { value ->
            drag = null
            onCommit(value)
        },
    )
}

/**
 * Un deslizador con su nombre y su valor. [person] es el dato de la persona (o su arrastre en curso); sin él se muestra la
 * [estimate] atenuada y, si tampoco hay estimación, «Sin calibrar» a mitad de recorrido. Nunca un 100 % afirmativo.
 */
@Composable
private fun AdjustRow(
    name: String,
    accent: Color,
    tag: String,
    person: Int?,
    estimate: Int?,
    onPreview: (Int?) -> Unit,
    onCommit: (Int) -> Unit,
) {
    val shown = person ?: estimate ?: NeutralStart
    val dimmed = person == null
    val stateText = when {
        person != null -> "$person %"
        estimate != null -> "Estimación $estimate %"
        else -> "Sin calibrar"
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = name, style = WizardTypography.controlLabel, color = WizardColors.text, modifier = Modifier.weight(1f))
            Text(
                text = stateText,
                style = WizardTypography.header,
                color = if (dimmed) WizardColors.textMuted else WizardColors.text,
            )
        }
        RingsChannelSlider(
            accent = accent,
            name = name,
            value = shown,
            onPreview = onPreview,
            onCommit = onCommit,
            tag = tag,
            dimmed = dimmed,
            stateText = stateText,
        )
    }
}

/** Acción de texto que despliega los músculos: 48 dp de área de toque y el estado dicho a TalkBack. */
@Composable
private fun MusclesToggle(expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag("rings-result-muscles-toggle")
            .semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" }
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Quiero ajustar mis músculos a mano",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Chevron(expanded)
    }
}

/** Chevron con la tinta de la marca: hacia abajo si está plegado y hacia arriba si está desplegado. */
@Composable
private fun Chevron(expanded: Boolean) {
    Canvas(Modifier.size(12.dp)) {
        val top = if (expanded) size.height * 0.7f else size.height * 0.3f
        val bottom = if (expanded) size.height * 0.3f else size.height * 0.7f
        drawLine(WizardColors.text, Offset(0f, top), Offset(size.width / 2f, bottom), 2.dp.toPx(), StrokeCap.Round)
        drawLine(WizardColors.text, Offset(size.width / 2f, bottom), Offset(size.width, top), 2.dp.toPx(), StrokeCap.Round)
    }
}

/** Una línea de la torre: el anillo, su valor y de dónde sale. Sin valor dice «Sin calibrar», nunca un 100 %. */
@Composable
private fun RingLine(channel: RingChannel, name: String, value: Int?, explanation: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp)) { drawCircle(KpknRingColors.of(channel)) }
            Spacer(Modifier.width(8.dp))
            Text(text = name, style = WizardTypography.controlLabel, color = WizardColors.text, modifier = Modifier.weight(1f))
            Text(
                text = value?.let { "$it %" } ?: "Sin calibrar",
                style = WizardTypography.controlLabel,
                color = if (value == null) WizardColors.textMuted else WizardColors.text,
            )
        }
        if (explanation != null) {
            Text(text = explanation, style = WizardTypography.note, color = WizardColors.textMuted)
        }
    }
}

/**
 * Una línea por anillo: de dónde sale su valor. Función pura (sin Compose), para poder probarla. Sin cobertura (todavía
 * calculando) no hay nada que explicar. Un ajuste de la persona manda sobre cualquier otra razón.
 */
internal fun ringsExplanations(
    answers: SetupRingsAnswers?,
    coverage: RingsCoverage?,
    today: LocalDate,
): Map<RecoveryChannelId, String> {
    if (coverage == null) return emptyMap()
    val a = answers ?: SetupRingsAnswers()
    val days = a.trainedDayOffsets.size
    val restedAll = a.sessionDaysState == RingsSessionDaysState.DECLARED && days == 0
    // Los días de la ventana de 72 h: «desde el martes», con el mismo texto que la pregunta de series.
    val since = ringsWindowStartLabel(
        oldestOffset = a.trainedDayOffsets.filter { it <= SetupRingsMapper.GROUP_SETS_MAX_DAYS_BACK }.maxOrNull(),
        today = today,
    )?.removeSuffix(".")?.replaceFirstChar { it.lowercase() }
    val training = when (days) {
        0 -> null
        1 -> "tu día de entreno"
        else -> "tus $days días de entreno"
    }
    val seriesTotal = a.groupSets.values.sum()
    return mapOf(
        RecoveryChannelId.SYSTEM to when {
            "energia" in a.manualRings -> "Ajustado por ti."
            coverage.system.score == null -> "Sin datos de tu energía: queda sin calibrar."
            restedAll -> "No entrenaste esta semana: llegas con el cuerpo descansado."
            training == null -> "Por lo que nos contaste."
            a.intensityLevel != null -> "Por $training y la intensidad que elegiste."
            else -> "Por $training."
        },
        RecoveryChannelId.MUSCULAR to when {
            a.manualMusclesV2.isNotEmpty() -> "Ajustado por ti."
            coverage.muscular.score == null -> "Sin datos de tus músculos: queda sin calibrar."
            restedAll -> "No entrenaste esta semana: llegas con el cuerpo descansado."
            training == null -> "Por lo que nos contaste."
            seriesTotal > 0 -> "Por $training y ${seriesPhrase(seriesTotal)}${since?.let { " $it" }.orEmpty()}."
            else -> "Por $training."
        },
        RecoveryChannelId.STRUCTURE to when {
            "columna" in a.manualRings -> "Ajustado por ti."
            coverage.structure.score == null -> "Sin datos de tu columna: queda sin calibrar."
            restedAll -> "No entrenaste esta semana: llegas con el cuerpo descansado."
            training == null -> "Por lo que nos contaste."
            a.mainLifts.isNotEmpty() -> "Por $training y ${liftsPhrase(a.mainLifts.size)}."
            else -> "Por $training."
        },
    )
}

private fun seriesPhrase(sets: Int): String = if (sets == 1) "1 serie" else "$sets series"

private fun liftsPhrase(count: Int): String = if (count == 1) "tu ejercicio principal" else "tus $count ejercicios principales"

/** Etiqueta visible de un pilar: solo la primera letra en mayúscula («Erectores espinales»). La clave del motor no cambia. */
internal fun ringsPillarLabel(pillar: String): String = pillar.lowercase().replaceFirstChar { it.uppercase() }

/** Nota del resultado: solo se pinta cuando algún anillo o deslizador todavía no tiene dato. */
internal const val RINGS_UNCALIBRATED_NOTE = "«Sin calibrar» quiere decir que aún no hay datos para ese anillo."
