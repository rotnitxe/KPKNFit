package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class GateOption(val id: String, val title: String, val subtitle: String, val icon: ImageVector)

/**
 * Puerta de aprobación visual (Fase 1).
 *
 * Prototipo navegable de las cinco pantallas representativas que el plan exige
 * aprobar **antes** de desarrollar todo el recorrido:
 *
 * 1. selección por tarjetas, 2. peso con regla horizontal, 3. altura con rueda
 * vertical, 4. grasa con personaje y slider, 5. transición de hitos.
 *
 * Usa datos de prueba controlados y **no guarda ni activa programas, planes,
 * observaciones ni ajustes**: todo el estado vive en memoria de composición. La
 * entrada que lo abre es temporal y se retira en la Fase 2, cuando el wizard
 * real ocupe su sitio.
 */
@Composable
fun WizardVisualGateScreen(onBack: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    var experienceId by rememberSaveable { mutableStateOf("") }
    var showSelectionError by rememberSaveable { mutableStateOf(false) }
    var massUnit by rememberSaveable { mutableStateOf(WizardMassUnit.KG.name) }
    var weightKg by rememberSaveable { mutableStateOf("") }
    var heightUnit by rememberSaveable { mutableStateOf(WizardHeightUnit.CM.name) }
    var heightCm by rememberSaveable { mutableStateOf("") }
    var bodyFatPercent by rememberSaveable { mutableStateOf("") }
    var bodyFatSource by rememberSaveable { mutableStateOf("") }

    val mass = WizardMassUnit.valueOf(massUnit)
    val height = WizardHeightUnit.valueOf(heightUnit)
    val weight = weightKg.toDoubleOrNull()
    val heightValue = heightCm.toIntOrNull()
    val bodyFatParsed = bodyFatPercent.toDoubleOrNull()
    val bodyFat = if (bodyFatParsed == null || bodyFatSource.isBlank()) {
        null
    } else {
        WizardBodyFatValue(
            percent = bodyFatParsed,
            source = WizardBodyFatSource.valueOf(bodyFatSource),
        )
    }

    when (step) {
        0 -> GateSelectionStep(
            selectedId = experienceId,
            showError = showSelectionError,
            onSelect = { experienceId = it; showSelectionError = false },
            onNext = {
                if (experienceId.isBlank()) showSelectionError = true else step = 1
            },
            onBack = onBack,
        )

        1 -> WizardScaffold(
            block = WizardBlock.BASICS,
            title = "Datos básicos",
            progress = 2f / 5f,
            onBack = { step = 0 },
            onExit = onBack,
            ctaLabel = "Continuar",
            ctaEnabled = weight != null,
            onCta = { step = 2 },
        ) {
            Text("¿Cuál es tu peso actual?", style = WizardTypography.question, color = WizardColors.text)
            Text(
                text = "Sirve para calcular tu gasto y ajustar las cargas. Puedes alternar entre kilogramos y libras: el valor real no cambia.",
                style = WizardTypography.bodySmall,
                color = WizardColors.textMuted,
            )
            WizardMassUnitToggle(selected = mass, onSelected = { massUnit = it.name })
            WizardWeightRule(
                unit = mass,
                valueKg = weight,
                onValueChange = { weightKg = it.toString() },
            )
        }

        2 -> WizardScaffold(
            block = WizardBlock.BASICS,
            title = "Datos básicos",
            progress = 3f / 5f,
            onBack = { step = 1 },
            onExit = onBack,
            ctaLabel = "Continuar",
            ctaEnabled = heightValue != null,
            onCta = { step = 3 },
        ) {
            Text("¿Cuál es tu altura?", style = WizardTypography.question, color = WizardColors.text)
            Text(
                text = "Se usa en las ecuaciones de gasto energético. Desliza la rueda; el valor central es el que se confirma.",
                style = WizardTypography.bodySmall,
                color = WizardColors.textMuted,
            )
            WizardHeightUnitToggle(selected = height, onSelected = { heightUnit = it.name })
            WizardHeightWheel(
                unit = height,
                cm = heightValue,
                onValueChange = { heightCm = it.toString() },
            )
        }

        3 -> WizardScaffold(
            block = WizardBlock.BASICS,
            title = "Datos básicos",
            progress = 4f / 5f,
            onBack = { step = 2 },
            onExit = onBack,
            ctaLabel = "Continuar",
            ctaEnabled = bodyFat != null,
            onCta = { step = 4 },
        ) {
            Text("¿Cómo describirías tu grasa corporal hoy?", style = WizardTypography.question, color = WizardColors.text)
            Text(
                text = "Es tu estado actual, no una meta. Confirma la estimación o el porcentaje que ya conozcas; no se puede omitir.",
                style = WizardTypography.bodySmall,
                color = WizardColors.textMuted,
            )
            WizardPhysiqueSelector(
                candidate = bodyFat,
                onCandidateChange = {
                    bodyFatPercent = it.percent.toString()
                    bodyFatSource = it.source.name
                },
                // La puerta necesita la versión completa (fuente + pill): la
                // pantalla productiva usa el modo VISUAL_ONLY por defecto.
                mode = WizardPhysiqueMode.COMPLETE,
            )
        }

        else -> WizardScaffold(
            block = WizardBlock.BASICS,
            title = "Datos básicos",
            progress = 5f / 5f,
            onBack = { step = 3 },
            onExit = onBack,
            ctaLabel = "Cerrar prototipo",
            ctaEnabled = true,
            onCta = onBack,
        ) {
            Text("Tus datos, revisados", style = WizardTypography.question, color = WizardColors.text)
            Text(
                text = "Así se enlazan los bloques del alta. Cada etapa se marca como completada solo cuando queda revisada.",
                style = WizardTypography.bodySmall,
                color = WizardColors.textMuted,
            )
            WizardMilestones(
                heroTitle = "¡Empecemos!",
                heroSubtitle = "Tu programa personalizado te espera",
                items = listOf(
                    WizardMilestoneItem(
                        block = WizardBlock.BASICS,
                        title = "Datos básicos",
                        body = "Edad, sexo usado por la ecuación, altura, peso y grasa corporal actual.",
                        state = WizardMilestoneState.DONE,
                    ),
                    WizardMilestoneItem(
                        block = WizardBlock.TRAINING,
                        title = "Entreno",
                        body = "Recomendación o protocolo, equipo, calendario, prioridades de orden y calentamientos.",
                        state = WizardMilestoneState.CURRENT,
                    ),
                    WizardMilestoneItem(
                        block = WizardBlock.NUTRITION,
                        title = "Nutrición",
                        body = "Presupuesto energético, macros y reparto semanal según el gasto previsto.",
                        state = WizardMilestoneState.PENDING,
                    ),
                    WizardMilestoneItem(
                        block = WizardBlock.RINGS,
                        title = "Rings",
                        body = "Entrenamiento reciente, sensaciones y molestias para calibrar la recuperación.",
                        state = WizardMilestoneState.PENDING,
                    ),
                    WizardMilestoneItem(
                        block = WizardBlock.REVIEW,
                        title = "Revisión y activación",
                        body = "Se activa el programa y el plan nutricional juntos, al final del alta.",
                        state = WizardMilestoneState.PENDING,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun GateSelectionStep(
    selectedId: String,
    showError: Boolean,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    WizardScaffold(
        block = WizardBlock.BASICS,
        title = "Datos básicos",
        progress = 1f / 5f,
        onBack = onBack,
        onExit = onBack,
        ctaLabel = "Continuar",
        ctaEnabled = true,
        onCta = onNext,
    ) {
        Text("¿Cuál es tu experiencia con las pesas?", style = WizardTypography.question, color = WizardColors.text)
        Text(
            text = "Define el punto de partida de tu plan. Si llevas tiempo parado, elige el nivel en el que estabas cómodo antes de la pausa.",
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            val options = listOf(
                GateOption("none", "Sin experiencia", "Nunca has entrenado con cargas de forma regular", Icons.Filled.Person),
                GateOption("beginner", "Principiante", "Menos de un año entrenando con constancia", Icons.Filled.FitnessCenter),
                GateOption("intermediate", "Intermedio", "Entre uno y tres años, con técnica consolidada", Icons.Filled.BarChart),
                GateOption("advanced", "Avanzado", "Más de tres años y progresión medible", Icons.Filled.Info),
            )
            options.forEach { option ->
                val disabled = option.id == "advanced"
                WizardChoiceCard(
                    title = option.title,
                    subtitle = if (disabled) "No disponible para la ruta de recomendación" else option.subtitle,
                    icon = option.icon,
                    selected = selectedId == option.id,
                    enabled = !disabled,
                    onClick = { onSelect(option.id) },
                )
            }
        }
        if (showError && selectedId.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WizardColors.cardFill, WizardShapes.card)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Elige una opción para continuar. Tu experiencia cambia la selección de ejercicios y las cargas iniciales.",
                    style = WizardTypography.caption,
                    color = WizardColors.danger,
                )
            }
        }
    }
}
