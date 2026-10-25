package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.domain.training.VolumeCalibrationEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

data class VolumeCalibrationResult(
    val mode: ProgramMode,
    val score: AthleteProfileScore,
    val recommendations: List<VolumeRecommendation>,
    val notes: String = "",
)

/** Transporte de los originales tras el callback legacy de tres valores, sin modificar su consumidor congelado. */
internal val LocalVolumeCalibrationSaved = staticCompositionLocalOf<(VolumeCalibrationResult) -> Unit> { {} }

/**
 * Hoja «Calibrar volumen» (P4): las opciones como píldoras `KpknChip` y filas `KpknRadioRow`, el campo de notas como `KpknTextField`, el
 * resumen en un módulo KPKN y la acción principal en `KpknPrimaryPill`. La lógica (estilo, puntajes y recomendaciones) no cambia; la usan
 * también Home y Programas.
 */
@Composable
fun VolumeCalibrationSheet(
    currentMode: ProgramMode,
    onDismiss: () -> Unit,
    onSave: (VolumeCalibrationResult) -> Unit,
) {
    val onOriginalsSaved = LocalVolumeCalibrationSaved.current
    var selectedStyle by rememberSaveable { mutableStateOf(currentMode.toTrainingStyle()) }
    var technique by rememberSaveable { mutableIntStateOf(2) }
    var consistency by rememberSaveable { mutableIntStateOf(2) }
    var strength by rememberSaveable { mutableIntStateOf(2) }
    var mobility by rememberSaveable { mutableIntStateOf(2) }
    var notes by rememberSaveable { mutableStateOf("") }

    val preview = remember(selectedStyle, technique, consistency, strength, mobility) {
        buildVolumeCalibration(
            style = selectedStyle,
            technique = technique,
            consistency = consistency,
            strength = strength,
            mobility = mobility,
        )
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text("Calibrar volumen", style = KpknType.title, color = WizardColors.text)
            Text(
                "Tus respuestas son autovaloraciones guardadas. Los rangos de series son referencias de planificación y este resultado también elige el enfoque del programa.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )

            ChoiceSection(
                title = "Objetivo principal",
                helper = "Esto cambia el enfoque del programa y la base de recomendaciones.",
                options = trainingStyleOptions,
                selectedValue = selectedStyle,
                onSelect = { selectedStyle = it },
            )

            ScoreSection(
                title = "Técnica",
                helper = "Qué tan sólida sientes tu ejecución en ejercicios clave.",
                selectedValue = technique,
                options = techniqueOptions,
                onSelect = { technique = it },
            )

            ScoreSection(
                title = "Consistencia",
                helper = "Qué tan estable ha sido tu entrenamiento últimamente.",
                selectedValue = consistency,
                options = consistencyOptions,
                onSelect = { consistency = it },
            )

            ScoreSection(
                title = "Fuerza",
                helper = "Cómo percibes tu nivel actual de fuerza respecto a tu experiencia.",
                selectedValue = strength,
                options = strengthOptions,
                onSelect = { strength = it },
            )

            ScoreSection(
                title = "Movilidad",
                helper = "Tu libertad para moverte y entrenar sin rigidez importante.",
                selectedValue = mobility,
                options = mobilityOptions,
                onSelect = { mobility = it },
            )

            KpknTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notas opcionales",
                placeholder = "Ej: compito pronto, tolero alta frecuencia, priorizo banca…",
                singleLine = false,
                minLines = 2,
            )

            KpknModule {
                KpknModuleHeader(label = "Resumen KPKN")
                Spacer(Modifier.height(10.dp))
                val modeLabel = when (preview.mode) {
                    ProgramMode.POWERLIFTING -> "Powerlifting"
                    ProgramMode.POWERBUILDING -> "Powerbuilding"
                    ProgramMode.HYPERTROPHY -> "Hipertrofia"
                }
                Text(
                    "Enfoque: $modeLabel",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                )
                Text(
                    "Perfil: ${preview.score.profileLevel.label()} · Puntaje ${preview.score.totalScore}/12",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
                preview.recommendations.take(6).forEach { recommendation ->
                    Text(
                        "${recommendation.muscleGroup}: ${recommendation.minEffectiveVolume}-${recommendation.maxRecoverableVolume} series/sem",
                        style = KpknType.body,
                        color = WizardColors.text,
                    )
                }
            }

            KpknPrimaryPill(
                text = "Guardar calibración",
                onClick = {
                    val result = preview.copy(notes = notes.trim())
                    onSave(result)
                    onOriginalsSaved(result)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                KpknTextAction(text = "Cancelar", onClick = onDismiss)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Elección de una sola opción de texto (objetivo principal) en píldoras `KpknChip`, con su ayuda debajo del título. */
@Composable
private fun ChoiceSection(
    title: String,
    helper: String,
    options: List<ChoiceOption<TrainingStyle>>,
    selectedValue: TrainingStyle,
    onSelect: (TrainingStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = KpknType.bodyStrong, color = WizardColors.text)
        Text(helper, style = KpknType.caption, color = WizardColors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                KpknChip(
                    text = option.label,
                    selected = option.value == selectedValue,
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}

/** Puntaje de 1 a 3 en filas de radio: cada nivel dice qué significa en su detalle. */
@Composable
private fun ScoreSection(
    title: String,
    helper: String,
    selectedValue: Int,
    options: List<ChoiceOption<Int>>,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = KpknType.bodyStrong, color = WizardColors.text)
        Text(helper, style = KpknType.caption, color = WizardColors.textMuted)
        options.forEach { option ->
            KpknRadioRow(
                selected = option.value == selectedValue,
                title = option.label,
                subtitle = option.detail,
                onClick = { onSelect(option.value) },
            )
        }
    }
}

private data class ChoiceOption<T>(
    val value: T,
    val label: String,
    val detail: String,
)

private val trainingStyleOptions = listOf(
    ChoiceOption(TrainingStyle.BODYBUILDER, "Culturismo", "Prioriza masa muscular, simetría y estímulo local."),
    ChoiceOption(TrainingStyle.POWERBUILDER, "Powerbuilding", "Combina fuerza útil con suficiente volumen de hipertrofia."),
    ChoiceOption(TrainingStyle.POWERLIFTER, "Powerlifting", "Da prioridad al rendimiento en los levantamientos principales."),
)

private val techniqueOptions = listOf(
    ChoiceOption(1, "Base en construcción", "Todavía estás puliendo patrones y estabilidad."),
    ChoiceOption(2, "Buena base", "Entrenas con control sólido en la mayoría de ejercicios."),
    ChoiceOption(3, "Muy sólida", "Puedes sostener técnica incluso con cargas altas."),
)

private val consistencyOptions = listOf(
    ChoiceOption(1, "Irregular", "Has tenido semanas cortadas o estás retomando."),
    ChoiceOption(2, "Estable", "Tu frecuencia ya es bastante consistente."),
    ChoiceOption(3, "Muy constante", "Entrenar es parte firme de tu rutina."),
)

private val strengthOptions = listOf(
    ChoiceOption(1, "Inicial", "Sigues construyendo fuerza base."),
    ChoiceOption(2, "Intermedia", "Ya mueves cargas respetables con regularidad."),
    ChoiceOption(3, "Alta", "Tu nivel de fuerza ya exige una mejor gestión del volumen."),
)

private val mobilityOptions = listOf(
    ChoiceOption(1, "Limitada", "Sueles notar rigidez o rangos acotados."),
    ChoiceOption(2, "Correcta", "Te mueves bien en casi todo tu entrenamiento."),
    ChoiceOption(3, "Muy buena", "Tu movilidad acompaña bien la carga y la técnica."),
)

internal fun buildVolumeCalibration(
    style: TrainingStyle,
    technique: Int,
    consistency: Int,
    strength: Int,
    mobility: Int,
): VolumeCalibrationResult {
    val output = VolumeCalibrationEngine.calculate(style, technique, consistency, strength, mobility)
    return VolumeCalibrationResult(output.mode, output.score, output.recommendations)
}

private fun ProgramMode.label(): String = when (this) {
    ProgramMode.POWERLIFTING -> "Powerlifting"
    ProgramMode.POWERBUILDING -> "Powerbuilding"
    ProgramMode.HYPERTROPHY -> "Hipertrofia"
}

private fun ProgramMode.toTrainingStyle(): TrainingStyle = when (this) {
    ProgramMode.POWERLIFTING -> TrainingStyle.POWERLIFTER
    ProgramMode.POWERBUILDING -> TrainingStyle.POWERBUILDER
    ProgramMode.HYPERTROPHY -> TrainingStyle.BODYBUILDER
}

private fun TrainingStyle.toProgramMode(): ProgramMode = when (this) {
    TrainingStyle.POWERLIFTER -> ProgramMode.POWERLIFTING
    TrainingStyle.POWERBUILDER -> ProgramMode.POWERBUILDING
    TrainingStyle.BODYBUILDER -> ProgramMode.HYPERTROPHY
}

private fun AthleteProfileLevel.label(): String = when (this) {
    AthleteProfileLevel.BEGINNER -> "Base"
    AthleteProfileLevel.ADVANCED -> "Avanzado"
}
