package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.domain.training.VolumeCalibrationEngine
import com.example.kpkn.screens.programdetail.design.progress.ProgressRatioRow
import com.example.kpkn.screens.programdetail.design.progress.ProgressRatioStyle
import com.example.kpkn.screens.programdetail.design.progress.ProgressRatioValueStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureAction
import com.example.kpkn.screens.programdetail.design.structure.StructureCaption
import com.example.kpkn.screens.programdetail.design.structure.StructureChip
import com.example.kpkn.screens.programdetail.design.structure.StructureInk
import com.example.kpkn.screens.programdetail.design.structure.StructurePrimaryPill
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetGroup
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetHeader
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetPadding
import com.example.kpkn.screens.programdetail.design.structure.StructureTextField
import com.example.kpkn.ui.components.KpknSheet

data class VolumeCalibrationResult(
    val mode: ProgramMode,
    val score: AthleteProfileScore,
    val recommendations: List<VolumeRecommendation>,
    val notes: String = "",
)

/** Transporte de los originales tras el callback legacy de tres valores, sin modificar su consumidor congelado. */
internal val LocalVolumeCalibrationSaved = staticCompositionLocalOf<(VolumeCalibrationResult) -> Unit> { {} }

/**
 * Hoja «Revisar mis respuestas» (la abre «Rangos de volumen»; sin escena en el lienzo, con su lenguaje): cada pregunta del asistente
 * como `.mgroup` con sus `.chip` y, debajo, qué significa la elegida; las notas en un `.field`; el resultado en filas `.ratio`; «Guardar
 * respuestas» en `.pill.pri` y «Cancelar» en `.act.faint`. Empieza con las respuestas guardadas en [initialScore], si las hay. La lógica
 * (estilo, puntajes y recomendaciones) no cambia.
 */
@Composable
fun VolumeCalibrationSheet(
    currentMode: ProgramMode,
    onDismiss: () -> Unit,
    onSave: (VolumeCalibrationResult) -> Unit,
    initialScore: AthleteProfileScore? = null,
) {
    val onOriginalsSaved = LocalVolumeCalibrationSaved.current
    var selectedStyle by rememberSaveable { mutableStateOf(initialScore?.trainingStyle ?: currentMode.toTrainingStyle()) }
    var technique by rememberSaveable { mutableIntStateOf(initialScore?.technicalScore?.coerceIn(1, 3) ?: 2) }
    var consistency by rememberSaveable { mutableIntStateOf(initialScore?.consistencyScore?.coerceIn(1, 3) ?: 2) }
    var strength by rememberSaveable { mutableIntStateOf(initialScore?.strengthScore?.coerceIn(1, 3) ?: 2) }
    var mobility by rememberSaveable { mutableIntStateOf(initialScore?.mobilityScore?.coerceIn(1, 3) ?: 2) }
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
                .padding(StructureSheetPadding),
        ) {
            StructureSheetHeader(
                title = "Revisar mis respuestas",
                subtitle = "Las mismas preguntas del asistente. Al guardar cambian el enfoque y los rangos de este programa, y el perfil de todos tus programas.",
            )
            ChoiceGroup("Objetivo principal", trainingStyleOptions, selectedStyle) { selectedStyle = it }
            ChoiceGroup("Técnica", techniqueOptions, technique) { technique = it }
            ChoiceGroup("Consistencia", consistencyOptions, consistency) { consistency = it }
            ChoiceGroup("Fuerza", strengthOptions, strength) { strength = it }
            ChoiceGroup("Movilidad", mobilityOptions, mobility) { mobility = it }
            StructureTextField(
                label = "Notas opcionales",
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Ej: compito pronto, tolero alta frecuencia, priorizo banca…",
                singleLine = false,
                modifier = Modifier.padding(top = 8.dp),
            )
            StructureSheetGroup(text = "Resultado")
            ResultRow("Enfoque", preview.mode.label())
            ResultRow("Perfil", "${preview.score.profileLevel.label()} · ${preview.score.totalScore}/12")
            preview.recommendations.take(6).forEach { recommendation ->
                ResultRow(recommendation.muscleGroup, "${recommendation.minEffectiveVolume}–${recommendation.maxRecoverableVolume}")
            }
            StructureCaption(text = "Series semanales por músculo: mínimo útil y máximo recuperable.", modifier = Modifier.padding(top = 6.dp))
            StructurePrimaryPill(
                text = "Guardar respuestas",
                onClick = {
                    val result = preview.copy(notes = notes.trim())
                    onSave(result)
                    onOriginalsSaved(result)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            StructureAction(text = "Cancelar", onClick = onDismiss, faint = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

/** Una pregunta: el `.mgroup`, sus opciones en `.chip` (6 dp de hueco) y qué significa la elegida. */
@Composable
private fun <T> ChoiceGroup(title: String, options: List<ChoiceOption<T>>, selected: T, onSelect: (T) -> Unit) {
    StructureSheetGroup(text = title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            StructureChip(text = option.label, selected = option.value == selected, onClick = { onSelect(option.value) })
        }
    }
    options.firstOrNull { it.value == selected }?.let { chosen ->
        StructureCaption(text = chosen.detail, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Una fila del resultado: el rótulo y la cifra en Syne 700 14. */
@Composable
private fun ResultRow(label: String, value: String) {
    ProgressRatioRow {
        Text(text = label, style = ProgressRatioStyle, color = StructureInk, modifier = Modifier.weight(1f))
        Text(text = value, style = ProgressRatioValueStyle, color = StructureInk)
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
