package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.capabilitiesBodyweightRoute
import com.example.kpkn.screens.onboarding.capabilitiesRequired
import com.example.kpkn.screens.onboarding.capabilityLevelOfReps
import com.example.kpkn.screens.onboarding.capabilitySkills
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.CapabilityFigure
import com.example.kpkn.screens.onboarding.design.entreno.CapabilityLevels
import com.example.kpkn.screens.onboarding.design.entreno.CapabilitySymbols
import com.example.kpkn.screens.onboarding.design.entreno.CountRuler
import com.example.kpkn.screens.onboarding.design.entreno.LevelSegments
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.capabilityTag
import com.example.kpkn.screens.onboarding.design.entreno.rememberFigClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Tope de repeticiones seguidas que se pregunta: el mismo que acota el reductor (`MAX_SEQUENCE_REPS`, 0..30). */
private const val REPS_MAX = 30

/** Cuánto se espera tras el último cambio de la regla antes de escribir las repeticiones (como la regla de marcas). */
internal const val REPS_WRITE_DEBOUNCE_MS = 220L

/** Lado de la figura de cada fila de repeticiones y de la fila de niveles. */
private val RowFigureSize = 72.dp

/** Marca de prueba de la regla de repeticiones de una habilidad: `setup-reps-PULL_UP`. */
internal fun repsTag(skill: CapabilitySkill): String = "setup-reps-${skill.name}"

/** Marca de prueba de «No lo sé» de una habilidad con repeticiones: `setup-reps-PUSH_UP-unknown`. */
internal fun repsUnknownTag(skill: CapabilitySkill): String = repsTag(skill) + "-unknown"

/**
 * CAPABILITIES · «¿Qué ejercicios ya te salen?». Fuera de la ruta de peso corporal, una figura de palitos por ejercicio que hace
 * el movimiento con el ritmo de su nivel (Aún no · Algunas · Varias); tocar el símbolo avanza al siguiente nivel y tocar un
 * segmento lo fija. Dentro de la ruta de peso corporal (calistenia o solo peso corporal, «¿Cuántas te salen seguidas?»), cada
 * ejercicio con repeticiones es una fila con su figura, su nombre y la regla de 0 a 30: las dominadas son obligatorias si hay
 * barra; las demás se pueden marcar con «No lo sé». La sentadilla a una pierna no cuenta repeticiones y sigue con sus niveles.
 *
 * Qué ejercicios se ofrecen sale de `draft.capabilitySkills()`; escribe SOLO con `vm.setCapability(skill, level)` (fuera de la
 * ruta y para la sentadilla a una pierna) y `vm.setMaxReps(skill, reps)` (en la ruta: el nivel lo deriva el reductor).
 */
@Composable
internal fun EntrenoCapabilitiesStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    Column(modifier = Modifier.fillMaxWidth()) {
        if (draft.capabilitiesBodyweightRoute()) {
            BodyweightRepsStep(draft = draft, vm = vm)
        } else {
            CapabilitySymbols(skills = draft.capabilitySkills(), levels = draft.capabilities, onLevel = vm::setCapability)
        }
        EntrenoStepNote("Sin presión: siempre podrás cambiarlo.")
    }
}

/**
 * La ruta de peso corporal con el ViewModel: lo pedido se muestra al instante, pero se escribe una sola vez cuando la regla se
 * detiene (como las marcas), y lo que quede sin escribir se escribe al cerrar el paso.
 */
@OptIn(FlowPreview::class)
@Composable
private fun BodyweightRepsStep(draft: SetupWizardDraft, vm: SetupWizardViewModel) {
    // Lo pedido y todavía sin escribir: habilidad → repeticiones (null = «No lo sé»).
    val pending = remember { mutableStateMapOf<CapabilitySkill, Int?>() }
    val shown = draft.maxReps.toMutableMap().also { reps ->
        pending.forEach { (skill, count) -> if (count == null) reps.remove(skill) else reps[skill] = count }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { pending.toMap() }
            .debounce(REPS_WRITE_DEBOUNCE_MS)
            .collect { wanted -> wanted.forEach { (skill, count) -> vm.setMaxReps(skill, count) } }
    }
    // El borrador ya trae lo pedido: lo que se ve vuelve a ser suyo.
    LaunchedEffect(draft.maxReps, pending.toMap()) {
        pending.keys.filter { skill -> draft.maxReps[skill] == pending[skill] }.forEach { skill -> pending.remove(skill) }
    }
    // Si el paso se cierra con algo sin escribir, no se pierde.
    val currentReps by rememberUpdatedState(draft.maxReps)
    val flush by rememberUpdatedState {
        pending.forEach { (skill, count) -> if (currentReps[skill] != count) vm.setMaxReps(skill, count) }
    }
    DisposableEffect(Unit) { onDispose { flush() } }

    CapabilityRepsRows(
        skills = draft.capabilitySkills(),
        reps = shown,
        levels = draft.capabilities,
        required = draft.capabilitiesRequired(),
        onReps = { skill, count -> pending[skill] = count },
        onLevel = { skill, level -> vm.setCapability(skill, level) },
    )
}

/**
 * Las filas de la ruta de peso corporal, sin ViewModel: la prueba observa lo que elige la persona con [onReps] (null = «No lo
 * sé») y [onLevel]. Solo corre la fila tocada por última vez; el resto se queda en su cuadro de reposo.
 */
@Composable
internal fun CapabilityRepsRows(
    skills: List<CapabilitySkill>,
    reps: Map<CapabilitySkill, Int>,
    levels: Map<CapabilitySkill, CapabilityLevel>,
    required: Set<CapabilitySkill>,
    onReps: (CapabilitySkill, Int?) -> Unit,
    onLevel: (CapabilitySkill, CapabilityLevel) -> Unit,
) {
    val reduced = wizardReducedMotion()
    var lastTouched by rememberSaveable { mutableStateOf<String?>(null) }
    val clock = rememberFigClock(active = !reduced && lastTouched != null)
    Column(modifier = Modifier.fillMaxWidth()) {
        skills.forEach { skill ->
            val running = !reduced && lastTouched == skill.name
            if (skill == CapabilitySkill.PISTOL_SQUAT) {
                LevelRow(
                    skill = skill,
                    level = levels[skill],
                    running = running,
                    clock = clock,
                    reduced = reduced,
                    onLevel = { level ->
                        lastTouched = skill.name
                        onLevel(skill, level)
                    },
                )
            } else {
                RepsRow(
                    skill = skill,
                    count = reps[skill],
                    required = skill in required,
                    running = running,
                    clock = clock,
                    reduced = reduced,
                    onCount = { count ->
                        lastTouched = skill.name
                        onReps(skill, count)
                    },
                )
            }
        }
    }
}

/** Una habilidad con repeticiones: figura y nombre, la cuenta, la regla de 0 a 30 y «No lo sé» (o «Obligatoria»). */
@Composable
private fun RepsRow(
    skill: CapabilitySkill,
    count: Int?,
    required: Boolean,
    running: Boolean,
    clock: State<Float>,
    reduced: Boolean,
    onCount: (Int?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapabilityFigure(
                skill = skill,
                level = count?.let { capabilityLevelOfReps(skill, it) },
                clock = clock,
                reducedMotion = reduced,
                modifier = Modifier.size(RowFigureSize),
                running = running,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(text = skill.label, style = WizardTypography.controlLabel, color = WizardColors.text)
                Text(
                    text = countLine(count),
                    style = WizardTypography.note,
                    color = if (count != null) SymbolPalette.musculo else WizardColors.textFaint,
                )
            }
        }
        CountRuler(
            range = 0..REPS_MAX,
            value = count,
            onValue = onCount,
            unitLabel = "${skill.label}, repeticiones",
            tag = repsTag(skill),
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (required) {
                Text(
                    text = "Obligatoria",
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                // «No lo sé»: interruptor de texto. Activo = sin repeticiones (la regla queda atenuada); declarar es mover la regla.
                Text(
                    text = "No lo sé",
                    style = WizardTypography.controlLabel,
                    color = if (count == null) WizardColors.text else WizardColors.textFaint,
                    textDecoration = if (count == null) TextDecoration.Underline else TextDecoration.None,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag(repsUnknownTag(skill))
                        .toggleable(
                            value = count == null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Switch,
                            // Solo borra: con las repeticiones ya borradas el interruptor no declara nada.
                            onValueChange = { nowUnknown -> if (nowUnknown) onCount(null) },
                        )
                        .defaultMinSize(minWidth = 96.dp, minHeight = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * La sentadilla a una pierna dentro de la ruta de peso corporal: no cuenta repeticiones, así que conserva sus tres niveles (el
 * mismo control de siempre, con la figura de 72 dp). Tocar la figura avanza el nivel.
 */
@Composable
private fun LevelRow(
    skill: CapabilitySkill,
    level: CapabilityLevel?,
    running: Boolean,
    clock: State<Float>,
    reduced: Boolean,
    onLevel: (CapabilityLevel) -> Unit,
) {
    val levelText = level?.label ?: "sin responder"
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .size(RowFigureSize)
                .testTag(capabilityTag(skill))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Cambiar el nivel de ${skill.label}",
                ) { onLevel(CapabilityLevels.next(level)) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "${skill.label}, $levelText"
                    stateDescription = levelText
                },
        ) {
            CapabilityFigure(
                skill = skill,
                level = level,
                clock = clock,
                reducedMotion = reduced,
                modifier = Modifier.fillMaxSize(),
                running = running,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = skill.label, style = WizardTypography.controlLabel, color = WizardColors.text)
            LevelSegments(skill = skill, level = level, reducedMotion = reduced, onLevel = onLevel)
            Text(
                text = level?.label ?: " ",
                style = WizardTypography.note,
                color = if (level != null) SymbolPalette.musculo else WizardColors.textFaint,
                maxLines = 1,
            )
        }
    }
}

/** La cuenta de una habilidad con repeticiones, en español. */
private fun countLine(count: Int?): String = when (count) {
    null -> "Sin declarar"
    0 -> "Aún no te salen"
    1 -> "1 seguida"
    else -> "$count seguidas"
}
