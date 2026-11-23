package com.example.kpkn.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.training.insights.VolumeLandmarks
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetChoiceRow
import com.example.kpkn.screens.workout.components.LiveSheetField
import com.example.kpkn.screens.workout.components.LiveSheetPrimary
import com.example.kpkn.screens.workout.components.LiveSheetSecondary
import com.example.kpkn.screens.workout.components.LiveSheetSectionLabel
import com.example.kpkn.screens.workout.components.LiveSheetShortCopy
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.screens.workout.components.liveEnter
import com.example.kpkn.screens.workout.components.liveExit
import com.example.kpkn.screens.workout.components.liveSpec
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknExpressiveSlider
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate
import kotlin.math.roundToInt

private val ReadinessInk = WizardColors.text
private val ReadinessBody = TextStyle(fontFamily = WizardFonts.body)
private val ReadinessSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)

/**
 * «Antes de entrenar» (#5): hoja de check-in pre-sesión con sliders expresivos ([KpknExpressiveSlider])
 * para Energía, Sueño y Estrés, sección siempre visible de Agujetas (DOMS) por músculo con
 * `ExerciseMuscleResolver.effectiveMusclesForVolume` y selector «+ Otro músculo» sobre los 17 grupos
 * canónicos, más Molestias previas y modo de voz.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreWorkoutCheckInSheet(
    session: Session,
    weekLabel: String?,
    initial: DailyWellbeingLog?,
    previousDiscomforts: List<String> = emptyList(),
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onConfirm: (DailyWellbeingLog) -> Unit,
    saving: Boolean = false,
    error: String? = null,
    voiceEnabled: Boolean = false,
    captureMode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
    onVoiceMode: (VoiceCaptureMode) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onContext: () -> Unit = {},
) {
    val context = LocalContext.current
    var energy by rememberSaveable(initial) { mutableStateOf(initial?.energy) }
    var sleep by rememberSaveable(initial) { mutableStateOf(initial?.sleepQuality?.takeIf { "sleep" in initial.capturedFields }) }
    var stress by rememberSaveable(initial) { mutableStateOf(initial?.stressLevel?.takeIf { "stress" in initial.capturedFields }) }
    var doms by remember(initial) { mutableStateOf(initial?.muscleDoms ?: emptyMap()) }
    var extraMuscles by remember(initial) { mutableStateOf(initial?.muscleDoms?.keys?.toList().orEmpty()) }
    var showAddMusclePicker by rememberSaveable { mutableStateOf(false) }
    var muscleSearchQuery by rememberSaveable { mutableStateOf("") }
    var discomforts by remember(initial) { mutableStateOf(initial?.preWorkoutDiscomforts ?: emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var voiceMenu by remember { mutableStateOf(false) }
    var pendingVoiceMode by rememberSaveable { mutableStateOf<VoiceCaptureMode?>(null) }
    var discomfortTouched by rememberSaveable { mutableStateOf(false) }

    val sessionMuscles = remember(session) { checkInSessionMuscles(session) }
    val visibleDomsMuscles = remember(sessionMuscles, extraMuscles, doms) {
        (sessionMuscles + extraMuscles + doms.keys).distinct()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingVoiceMode?.let(onVoiceMode)
        pendingVoiceMode = null
    }
    val confirm = {
        val cleanedDoms = doms.filterValues { it > 0 }
        val hadInitialDoms = !initial?.muscleDoms.isNullOrEmpty()
        val hadInitialDiscomforts = initial?.capturedFields?.contains("discomforts") == true
        if (energy == null && sleep == null && stress == null && cleanedDoms.isEmpty() && !hadInitialDoms && !discomfortTouched && !hadInitialDiscomforts) {
            onDismiss()
        } else {
            onConfirm(
                DailyWellbeingLog(
                    id = "wellbeing-${LocalDate.now()}",
                    date = LocalDate.now().toString(),
                    sleepQuality = sleep ?: 3,
                    stressLevel = stress ?: 3,
                    energy = energy,
                    doms = cleanedDoms.values.maxOrNull() ?: if (hadInitialDoms) 1 else (initial?.doms ?: 1),
                    muscleDoms = cleanedDoms,
                    preWorkoutDiscomforts = discomforts,
                    source = WellbeingSource.DAILY_CHECK_IN,
                    capturedFields = buildSet {
                        if (energy != null) add("energy")
                        if (sleep != null) add("sleep")
                        if (stress != null) add("stress")
                        if (cleanedDoms.isNotEmpty() || hadInitialDoms) add("muscleDoms")
                        if (discomfortTouched || hadInitialDiscomforts) add("discomforts")
                    },
                ),
            )
        }
    }

    LiveSheet(
        title = "¿Cómo llegas hoy?",
        eyebrow = "ANTES DE ENTRENAR",
        onDismiss = { if (!saving) onDismiss() },
        dismissible = !saving,
        tag = "readiness-sheet",
        footer = {
            LiveSheetPrimary(
                "Confirmar y continuar",
                { if (!saving) confirm() },
                enabled = !saving,
                icon = KpknLineIcons.Check,
                tag = "readiness-confirm",
            )
        },
    ) {
        LiveSheetSubtitle(listOfNotNull(session.name, weekLabel).joinToString(" · "))

        ScaleRow(
            label = "Energía",
            tag = "energy",
            value = energy,
            range = 0..10,
            startLabel = "Muy baja",
            endLabel = "Muy alta",
            minColor = Color(0xFF5B7FA6),
            midColor = Color(0xFFE6E1D5),
            maxColor = Color(0xFFF49A6E),
            sparkAtMax = true,
            coolGlowAtMin = true,
            onChange = { energy = it },
        )
        ScaleRow(
            label = "Sueño",
            tag = "sleep",
            value = sleep,
            range = 1..5,
            startLabel = "Muy malo",
            endLabel = "Muy bueno",
            minColor = Color(0xFF7A6CA8),
            midColor = Color(0xFFE6E1D5),
            maxColor = Color(0xFF5CE1B8),
            sparkAtMax = true,
            coolGlowAtMin = true,
            onChange = { sleep = it },
        )
        ScaleRow(
            label = "Estrés",
            tag = "stress",
            value = stress,
            range = 1..5,
            startLabel = "Bajo",
            endLabel = "Alto",
            minColor = Color(0xFF5CE1B8),
            midColor = Color(0xFFE6E1D5),
            maxColor = Color(0xFFFF5E57),
            sparkAtMax = true,
            coolGlowAtMin = true,
            onChange = { stress = it },
        )

        // Sección «Agujetas (DOMS)» SIEMPRE visible
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LiveSheetSectionLabel("Agujetas (DOMS)")
            KpknTextAction(
                text = if (showAddMusclePicker) "Cerrar" else "+ Otro músculo",
                onClick = { showAddMusclePicker = !showAddMusclePicker },
            )
        }
        if (visibleDomsMuscles.isNotEmpty()) {
            DomsGrid(
                muscles = visibleDomsMuscles,
                values = doms,
                onChange = { muscle, value ->
                    doms = if (value <= 0) doms - muscle else doms + (muscle to value)
                },
                onRemove = { muscle ->
                    doms = doms - muscle
                    extraMuscles = extraMuscles - muscle
                },
            )
        } else {
            Text(
                "Toca «+ Otro músculo» para registrar agujetas en cualquier grupo muscular.",
                style = ReadinessBody.copy(fontSize = 12.sp),
                color = LiveSheetTokens.Muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (showAddMusclePicker) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveSheetField(
                    muscleSearchQuery,
                    { muscleSearchQuery = it },
                    "Buscar músculo",
                    Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Text,
                    placeholder = "Ej. Cuádriceps, Dorsales, Gemelos…",
                    tag = "readiness-doms-search",
                )
                val q = muscleSearchQuery.trim().lowercase()
                val candidates = VolumeLandmarks.all
                    .map { it.name }
                    .filter { it !in visibleDomsMuscles && (q.isEmpty() || it.lowercase().contains(q)) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    candidates.forEach { muscle ->
                        KpknChip(
                            text = "+ $muscle",
                            selected = false,
                            onClick = {
                                extraMuscles = (extraMuscles + muscle).distinct()
                                doms = doms + (muscle to 2)
                                showAddMusclePicker = false
                                muscleSearchQuery = ""
                            },
                        )
                    }
                }
            }
        }

        LiveSheetSectionLabel("Molestias previas")
        val chips = remember(previousDiscomforts, query) { discomfortChips(previousDiscomforts, query) }
        LiveSheetChoiceRow(
            options = chips,
            selected = { id -> if (id == "none") discomforts == listOf("none") else id in discomforts },
            onSelect = { id ->
                discomfortTouched = true
                discomforts = if (id == "none") {
                    if (discomforts == listOf("none")) emptyList() else listOf("none")
                } else {
                    val current = discomforts.filter { it != "none" }
                    if (id in current) current - id else current + id
                }
            },
            modifier = Modifier.padding(bottom = 0.dp).animateContentSize(),
            tagPrefix = "readiness-discomfort",
        )
        LiveSheetField(
            query,
            { query = it },
            "Buscar otra molestia",
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            keyboardType = KeyboardType.Text,
            placeholder = "Buscar otra molestia",
            tag = "readiness-search",
        )

        val motionReduced = wizardReducedMotion()
        var heldError by remember { mutableStateOf(error) }
        if (error != null) heldError = error
        AnimatedVisibility(visible = error != null, enter = liveEnter(motionReduced), exit = liveExit(motionReduced)) {
            heldError?.let { LiveSheetShortCopy(it, error = true) }
        }
        Box {
            LiveSheetSecondary(
                if (voiceEnabled) voiceLabel(captureMode) else "Habilitar modo de voz",
                { voiceMenu = true },
                icon = KpknLineIcons.Mic,
                tag = "readiness-voice",
            )
            DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                listOf(VoiceCaptureMode.HANDS_FREE to "Manos libres", VoiceCaptureMode.MUSIC to "Con música").forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = KpknType.body, color = ReadinessInk) },
                        onClick = {
                            voiceMenu = false
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) onVoiceMode(mode) else {
                                pendingVoiceMode = mode
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScaleRow(
    label: String,
    tag: String,
    value: Int?,
    range: IntRange,
    startLabel: String,
    endLabel: String,
    minColor: Color,
    midColor: Color,
    maxColor: Color,
    sparkAtMax: Boolean,
    coolGlowAtMin: Boolean,
    onChange: (Int) -> Unit,
) {
    val answered = value != null
    val displayValue = value?.toFloat() ?: ((range.first + range.last) / 2f)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                Modifier.weight(1f),
                style = ReadinessBody.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                color = LiveSheetTokens.Faint,
            )
            Text(
                value?.toString() ?: "Sin responder",
                style = ReadinessSyne.copy(fontSize = if (answered) 21.sp else 14.sp, lineHeight = 24.sp),
                color = if (answered) ReadinessInk else LiveSheetTokens.Muted,
                modifier = Modifier.testTag("readiness-value-$tag"),
            )
        }
        KpknExpressiveSlider(
            value = displayValue,
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.count() - 2).coerceAtLeast(0),
            minColor = minColor,
            midColor = midColor,
            maxColor = maxColor,
            sparkAtMax = sparkAtMax && answered,
            coolGlowAtMin = coolGlowAtMin && answered,
            bubbleLabel = { it.roundToInt().toString() },
            semanticLabel = if (value == null) {
                "$label, sin responder, rango de ${range.first} a ${range.last}"
            } else {
                "$label, $value de ${range.first} a ${range.last}"
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("readiness-scale-$tag"),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(startLabel, Modifier.weight(1f), style = ReadinessBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
            Text(endLabel, style = ReadinessBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
        }
    }
}

@Composable
private fun DomsGrid(
    muscles: List<String>,
    values: Map<String, Int>,
    onChange: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    val reduced = wizardReducedMotion()
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        muscles.forEach { muscle ->
            val value = values[muscle] ?: 0
            val shape = RoundedCornerShape(14.dp)
            val fill by animateColorAsState(if (value > 0) Color(0x0DFFFFFF) else Color.Transparent, liveSpec(reduced, 180), label = "doms-fill")
            val line by animateColorAsState(if (value > 0) Color(0x38FFFFFF) else LiveSheetTokens.Line, liveSpec(reduced, 180), label = "doms-line")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, line, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        muscle,
                        Modifier.weight(1f),
                        style = ReadinessBody.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = ReadinessInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (value) {
                            1 -> "1 · Leve"
                            2 -> "2 · Moderada"
                            3 -> "3 · Notable"
                            4 -> "4 · Fuerte"
                            5 -> "5 · Muy fuerte"
                            else -> "Sin agujetas"
                        },
                        style = ReadinessBody.copy(fontSize = 11.sp),
                        color = if (value > 0) Color(0xFFF49A6E) else LiveSheetTokens.Muted,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    if (value > 0) {
                        Icon(
                            imageVector = KpknLineIcons.Close,
                            contentDescription = "Quitar agujetas de $muscle",
                            tint = LiveSheetTokens.Muted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onRemove(muscle) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..5).forEach { level ->
                        val selected = value == level
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0xFFF49A6E) else Color.White.copy(alpha = 0.06f))
                                .clickable(role = Role.Button) {
                                    onChange(muscle, if (selected) 0 else level)
                                }
                                .semantics {
                                    contentDescription = "Molestia muscular en $muscle nivel $level de 5"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$level",
                                style = ReadinessSyne.copy(fontSize = 13.sp),
                                color = if (selected) Color(0xFF141414) else ReadinessInk,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun checkInSessionMuscles(
    session: Session,
    catalogIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo> = catalogExerciseIndex(),
): List<String> {
    val setsByMuscle = mutableMapOf<String, Int>()
    session.allExercises().forEach { exercise ->
        val count = exercise.sets.count { !it.isIneffective }
        ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, catalogIndex).forEach { muscle ->
            setsByMuscle[muscle.muscle] = (setsByMuscle[muscle.muscle] ?: 0) + count
        }
    }
    return setsByMuscle.entries.sortedByDescending { it.value }.take(6).map { it.key }
}

private fun discomfortChips(frequent: List<String>, query: String): List<Pair<String, String>> {
    val normalized = query.trim().lowercase()
    val all = DISCOMFORT_CATALOG_BY_ID.map { (id, entry) -> id to entry.label }
        .filter { normalized.isEmpty() || it.second.lowercase().contains(normalized) }
    val ordered = buildList {
        add("none" to "Ninguna")
        frequent.forEach { id -> all.firstOrNull { it.first == id }?.let { add(it) } }
        all.filter { candidate -> none { it.first == candidate.first } }.forEach { add(it) }
    }
    return ordered.take(if (normalized.isEmpty()) 4 else 7)
}

private fun voiceLabel(mode: VoiceCaptureMode): String = when (mode) {
    VoiceCaptureMode.HANDS_FREE -> "Voz · manos libres"
    VoiceCaptureMode.MUSIC -> "Voz · con música"
}
