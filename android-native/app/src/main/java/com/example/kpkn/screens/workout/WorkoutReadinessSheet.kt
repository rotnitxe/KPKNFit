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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.liveEnter
import com.example.kpkn.screens.workout.components.liveExit
import com.example.kpkn.screens.workout.components.liveSpec
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetChoiceRow
import com.example.kpkn.screens.workout.components.LiveSheetField
import com.example.kpkn.screens.workout.components.LiveSheetPrimary
import com.example.kpkn.screens.workout.components.LiveSheetSecondary
import com.example.kpkn.screens.workout.components.LiveSheetSectionLabel
import com.example.kpkn.screens.workout.components.LiveSheetShortCopy
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate

private val ReadinessInk = WizardColors.text
private val ReadinessBody = TextStyle(fontFamily = WizardFonts.body)
private val ReadinessSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)

/**
 * «Antes de entrenar» (`readiness` del prototipo del Refinamiento 02): la hoja de la sesión con «ANTES DE ENTRENAR», «¿Cómo llegas
 * hoy?» y ✕; energía, sueño y estrés como escalas con la cifra en Syne; la molestia muscular por zona en recuadros que se tocan (0 a 5);
 * las molestias previas en píldoras con su buscador; el modo de voz y «Confirmar y continuar» al pie. Mide lo que mide su contenido: sin
 * hueco abajo. Se guarda como observación de bienestar y no cambia cargas ni series.
 */
@Composable
internal fun WorkoutReadinessSheet(
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
    // «Notas y disciplina» se retiró de esta hoja (pedido del usuario); el parámetro se conserva para el llamador.
    @Suppress("UNUSED_PARAMETER") onContext: () -> Unit = {},
) {
    val context = LocalContext.current
    var energy by rememberSaveable(initial) { mutableStateOf(initial?.energy) }
    var sleep by rememberSaveable(initial) { mutableStateOf(initial?.sleepQuality?.takeIf { "sleep" in initial.capturedFields }) }
    var stress by rememberSaveable(initial) { mutableStateOf(initial?.stressLevel?.takeIf { "stress" in initial.capturedFields }) }
    var doms by remember(initial) { mutableStateOf(initial?.muscleDoms ?: emptyMap()) }
    var discomforts by remember(initial) { mutableStateOf(initial?.preWorkoutDiscomforts ?: emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var voiceMenu by remember { mutableStateOf(false) }
    var pendingVoiceMode by rememberSaveable { mutableStateOf<VoiceCaptureMode?>(null) }
    var discomfortTouched by rememberSaveable { mutableStateOf(false) }

    val muscles = remember(session) { readinessMuscles(session) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingVoiceMode?.let(onVoiceMode)
        pendingVoiceMode = null
    }
    val confirm = {
        if (energy == null && sleep == null && stress == null && doms.isEmpty() && !discomfortTouched) {
            onDismiss()
        } else {
            onConfirm(
                DailyWellbeingLog(
                    id = "wellbeing-${LocalDate.now()}",
                    date = LocalDate.now().toString(),
                    sleepQuality = sleep ?: 3,
                    stressLevel = stress ?: 3,
                    energy = energy,
                    doms = initial?.doms ?: 1,
                    muscleDoms = doms,
                    preWorkoutDiscomforts = discomforts,
                    source = WellbeingSource.DAILY_CHECK_IN,
                    capturedFields = buildSet {
                        if (energy != null) add("energy")
                        if (sleep != null) add("sleep")
                        if (stress != null) add("stress")
                        if (doms.isNotEmpty()) add("muscleDoms")
                        if (discomfortTouched) add("discomforts")
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
            LiveSheetPrimary("Confirmar y continuar", { if (!saving) confirm() }, enabled = !saving, icon = KpknLineIcons.Check,
                tag = "readiness-confirm")
        },
    ) {
        LiveSheetSubtitle(listOfNotNull(session.name, weekLabel).joinToString(" · "))
        ScaleRow("Energía", energy, 0..10) { energy = it }
        ScaleRow("Sueño", sleep, 1..5) { sleep = it }
        ScaleRow("Estrés", stress, 1..5) { stress = it }

        if (muscles.isNotEmpty()) {
            LiveSheetSectionLabel("Molestia muscular · DOMS")
            DomsGrid(muscles, doms) { muscle, value -> doms = doms + (muscle to value) }
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
            // Al buscar, las píldoras cambian de número: la altura se ajusta animada.
            modifier = Modifier.padding(bottom = 0.dp).animateContentSize(),
            tagPrefix = "readiness-discomfort",
        )
        LiveSheetField(query, { query = it }, "Buscar otra molestia", Modifier.fillMaxWidth().padding(bottom = 14.dp),
            keyboardType = KeyboardType.Text, placeholder = "Buscar otra molestia", tag = "readiness-search")

        // El aviso de error se despliega y se pliega; se recuerda el último texto para poder fundirlo al salir.
        val motionReduced = wizardReducedMotion()
        var heldError by remember { mutableStateOf(error) }
        if (error != null) heldError = error
        AnimatedVisibility(visible = error != null, enter = liveEnter(motionReduced), exit = liveExit(motionReduced)) {
            heldError?.let { LiveSheetShortCopy(it, error = true) }
        }
        Box {
            LiveSheetSecondary(if (voiceEnabled) voiceLabel(captureMode) else "Habilitar modo de voz", { voiceMenu = true },
                icon = KpknLineIcons.Mic, tag = "readiness-voice")
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

/** `.readiness-scale`: la etiqueta y la cifra elegida (Syne 19) arriba; debajo las opciones a lo ancho (`.scale-options`). */
@Composable
private fun ScaleRow(label: String, value: Int?, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), Modifier.weight(1f), style = ReadinessBody.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                color = LiveSheetTokens.Faint)
            Text(value?.toString() ?: "—", style = ReadinessSyne.copy(fontSize = 21.sp, lineHeight = 24.sp), color = ReadinessInk)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            range.forEach { option ->
                val on = option == value
                val reduced = wizardReducedMotion()
                val fill by animateColorAsState(if (on) ReadinessInk else Color(0x0DFFFFFF), liveSpec(reduced, 180), label = "readiness-scale-fill")
                val ink by animateColorAsState(if (on) Color(0xFF111111) else LiveSheetTokens.Muted, liveSpec(reduced, 180), label = "readiness-scale-ink")
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                        .background(fill)
                        .clickable(role = Role.RadioButton) { onChange(option) }
                        .semantics { selected = on; contentDescription = "$label $option" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$option", style = ReadinessBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = ink)
                }
            }
        }
    }
}

/** `.doms-grid`: dos columnas de recuadros (zona y cifra); cada toque sube un punto y del 5 vuelve a 0. */
@Composable
private fun DomsGrid(muscles: List<String>, values: Map<String, Int>, onChange: (String, Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        muscles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pair.forEach { muscle ->
                    val value = values[muscle] ?: 0
                    val shape = RoundedCornerShape(13.dp)
                    val reduced = wizardReducedMotion()
                    val fill by animateColorAsState(if (value > 0) Color(0x09FFFFFF) else Color.Transparent, liveSpec(reduced, 180), label = "readiness-doms-fill")
                    val line by animateColorAsState(if (value > 0) Color(0x35FFFFFF) else LiveSheetTokens.Line, liveSpec(reduced, 180), label = "readiness-doms-line")
                    Row(
                        Modifier.weight(1f).heightIn(min = 56.dp).clip(shape)
                            .background(fill)
                            .border(1.dp, line, shape)
                            .clickable(role = Role.Button) { onChange(muscle, if (value >= 5) 0 else value + 1) }
                            .semantics { contentDescription = "Molestia muscular en $muscle: ${if (value == 0) "sin molestia" else "$value de 5"}" }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(muscle, Modifier.weight(1f), style = ReadinessBody.copy(fontSize = 13.sp, lineHeight = 17.sp), color = ReadinessInk,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (value == 0) "—" else "$value", style = ReadinessSyne.copy(fontSize = 15.sp), color = ReadinessInk)
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

private fun readinessMuscles(session: Session): List<String> {
    val setsByMuscle = mutableMapOf<String, Int>()
    session.allExercises().forEach { exercise ->
        val count = exercise.sets.count { !it.isIneffective }
        exercise.effectiveMuscles.orEmpty().forEach { muscle ->
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
