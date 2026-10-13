package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.domain.training.VolumeCalibrationEngine

data class VolumeCalibrationResult(
    val mode: ProgramMode,
    val score: AthleteProfileScore,
    val recommendations: List<VolumeRecommendation>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeCalibrationSheet(
    currentMode: ProgramMode,
    onDismiss: () -> Unit,
    onSave: (VolumeCalibrationResult) -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Calibrar volumen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Usamos tus datos y tu historial para proponerte series semanales por músculo. Este resultado también sincroniza el enfoque del programa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas opcionales") },
                placeholder = { Text("Ej: compito pronto, tolero alta frecuencia, priorizo banca...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
            )

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Resumen KPKN", fontWeight = FontWeight.Black)
                    val modeLabel = when (preview.mode) {
                        ProgramMode.POWERLIFTING -> "Powerlifting"
                        ProgramMode.POWERBUILDING -> "Powerbuilding"
                        ProgramMode.HYPERTROPHY -> "Hipertrofia"
                    }
                    Text(
                        "Enfoque: $modeLabel",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Perfil: ${preview.score.profileLevel.label()} · Puntaje ${preview.score.totalScore}/12",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    preview.recommendations.take(6).forEach { recommendation ->
                        Text(
                            "${recommendation.muscleGroup}: ${recommendation.minEffectiveVolume}-${recommendation.maxRecoverableVolume} series/sem",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(
                    onClick = { onSave(preview) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Guardar calibración")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChoiceSection(
    title: String,
    helper: String,
    options: List<ChoiceOption<TrainingStyle>>,
    selectedValue: TrainingStyle,
    onSelect: (TrainingStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.Black)
        Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option.value == selectedValue,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun ScoreSection(
    title: String,
    helper: String,
    selectedValue: Int,
    options: List<ChoiceOption<Int>>,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.Black)
        Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                val selectedBg = MaterialTheme.colorScheme.primaryContainer
                val selectedContent = MaterialTheme.colorScheme.onPrimaryContainer
                val idleBg = MaterialTheme.colorScheme.surfaceContainerLowest
                val idleContent = MaterialTheme.colorScheme.onSurface
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(option.value) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) selectedBg else idleBg,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                option.label,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) selectedContent else idleContent,
                            )
                            Text(
                                option.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) selectedContent.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index < option.value) {
                                                if (isSelected) selectedContent else MaterialTheme.colorScheme.primary
                                            } else {
                                                if (isSelected) selectedContent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
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
