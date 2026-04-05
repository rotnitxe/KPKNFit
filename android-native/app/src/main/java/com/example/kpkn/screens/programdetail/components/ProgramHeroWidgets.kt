package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation

data class VolumeCalibrationResult(
    val mode: ProgramMode,
    val score: AthleteProfileScore,
    val recommendations: List<VolumeRecommendation>,
)

private enum class HeroWidgetType(
    val title: String,
    val subtitle: String,
    val accent: Color,
) {
    MUSCLE("Músculo", "Promedio general de la batería muscular del programa.", Color(0xFF22C55E)),
    SNC("SNC", "Estado actual del sistema nervioso central.", Color(0xFF3B82F6)),
    SPINAL("Espinal", "Estado general de la carga espinal.", Color(0xFFA855F7)),
    VOLUME("Calibrar volumen", "Configura el volumen recomendado por músculo para este programa.", Color(0xFFF59E0B)),
    INCREASE("Aumentar volumen", "Sube 20% el volumen de la semana actual, músculo por músculo.", Color(0xFF14B8A6)),
    REDUCE("Disminuir volumen", "Baja 20% el volumen de la semana actual, músculo por músculo.", Color(0xFFE11D48));
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroWidgetsSection(
    muscularBattery: Int,
    sncBattery: Int,
    spinalBattery: Int,
    isVolumeCalibrated: Boolean,
    onOpenVolumeSetup: () -> Unit,
    onIncreaseVolumeCurrentWeek: () -> Unit,
    onReduceVolumeCurrentWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedWidgets by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var infoWidget by rememberSaveable { mutableStateOf<String?>(null) }
    var showCalibrationRequiredDialog by rememberSaveable { mutableStateOf(false) }
    var showIncreaseConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showReduceConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val widgets = HeroWidgetType.entries.mapNotNull { widget ->
        if (widget.name in selectedWidgets) widget else null
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            AddWidgetsChip(onClick = { pickerOpen = true })
        }

        items(widgets.take(3), key = { it.name }) { widget ->
                    when (widget) {
                        HeroWidgetType.MUSCLE -> WidgetChip(
                            title = widget.title,
                            value = "$muscularBattery%",
                            accent = widget.accent,
                            onInfo = { infoWidget = widget.name },
                            onRemove = {
                                selectedWidgets = selectedWidgets.filterNot { it == widget.name }
                            },
                        )
                        HeroWidgetType.SNC -> WidgetChip(
                            title = widget.title,
                            value = "$sncBattery%",
                            accent = widget.accent,
                            onInfo = { infoWidget = widget.name },
                            onRemove = {
                                selectedWidgets = selectedWidgets.filterNot { it == widget.name }
                            },
                        )
                        HeroWidgetType.SPINAL -> WidgetChip(
                            title = widget.title,
                            value = "$spinalBattery%",
                            accent = widget.accent,
                            onInfo = { infoWidget = widget.name },
                            onRemove = {
                                selectedWidgets = selectedWidgets.filterNot { it == widget.name }
                            },
                        )
                        HeroWidgetType.VOLUME -> WidgetChip(
                            title = widget.title,
                            value = if (isVolumeCalibrated) "Verificado" else "Alerta",
                            accent = if (isVolumeCalibrated) Color(0xFF22C55E) else widget.accent,
                            onInfo = { infoWidget = widget.name },
                            onClick = onOpenVolumeSetup,
                            onRemove = {
                                selectedWidgets = selectedWidgets.filterNot { it == widget.name }
                            },
                        )
                        HeroWidgetType.INCREASE -> WidgetChip(
                            title = widget.title,
                            value = if (isVolumeCalibrated) "+20%" else "Bloqueado",
                            accent = if (isVolumeCalibrated) widget.accent else Color(0xFFF59E0B),
                            onInfo = { infoWidget = widget.name },
                            onClick = {
                                if (isVolumeCalibrated) showIncreaseConfirmDialog = true
                                else showCalibrationRequiredDialog = true
                            },
                            onRemove = {
                                selectedWidgets = selectedWidgets.filterNot { it == widget.name }
                            },
                        )
                        HeroWidgetType.REDUCE -> WidgetChip(
                            title = widget.title,
                            value = if (isVolumeCalibrated) "-20%" else "Bloqueado",
                            accent = if (isVolumeCalibrated) widget.accent else Color(0xFFF59E0B),
                            onInfo = { infoWidget = widget.name },
                            onClick = {
                                if (isVolumeCalibrated) showReduceConfirmDialog = true
                                else showCalibrationRequiredDialog = true
                            },
                            onRemove = {
                                selectedWidgets = selectedWidgets.filterNot { it == widget.name }
                            },
                        )
                    }
        }
    }

    if (pickerOpen) {
        ModalBottomSheet(onDismissRequest = { pickerOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Agrega widgets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    "Selecciona hasta 3 widgets para mostrar en el hero. Cada uno incluye un botón de información.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HeroWidgetType.entries.forEach { widget ->
                    val isSelected = widget.name in selectedWidgets
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                selectedWidgets = if (isSelected) {
                                    selectedWidgets.filterNot { it == widget.name }
                                } else if (selectedWidgets.size < 3) {
                                    selectedWidgets + widget.name
                                } else {
                                    selectedWidgets
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(widget.accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = when (widget) {
                                        HeroWidgetType.MUSCLE -> "M"
                                        HeroWidgetType.SNC -> "S"
                                        HeroWidgetType.SPINAL -> "E"
                                        HeroWidgetType.VOLUME -> "V"
                                        HeroWidgetType.INCREASE -> "+"
                                        HeroWidgetType.REDUCE -> "-"
                                    },
                                    color = widget.accent,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(widget.title, fontWeight = FontWeight.Black)
                                Text(
                                    widget.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Text(
                                if (isSelected) "Quitar" else if (selectedWidgets.size < 3) "Añadir" else "Límite",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    infoWidget?.let { widgetName ->
        val widget = HeroWidgetType.entries.firstOrNull { it.name == widgetName }
        if (widget != null) {
            AlertDialog(
                onDismissRequest = { infoWidget = null },
                title = { Text(widget.title, fontWeight = FontWeight.Black) },
                text = { Text(widget.subtitle) },
                confirmButton = {
                    TextButton(onClick = { infoWidget = null }) { Text("Entendido") }
                },
            )
        }
    }

    if (showCalibrationRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showCalibrationRequiredDialog = false },
            title = { Text("Primero calibra el volumen", fontWeight = FontWeight.Black) },
            text = {
                Text("Necesitamos una calibración base antes de aplicar automatizaciones sobre el volumen.")
            },
            confirmButton = {
                Button(onClick = {
                    showCalibrationRequiredDialog = false
                    onOpenVolumeSetup()
                }) {
                    Text("Calibrar ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrationRequiredDialog = false }) { Text("Cerrar") }
            },
        )
    }

    if (showIncreaseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showIncreaseConfirmDialog = false },
            title = { Text("Aumentar volumen semanal", fontWeight = FontWeight.Black) },
            text = { Text("Esto aumentará en 20% el volumen de la semana actual, músculo por músculo. ¿Deseas continuar?") },
            confirmButton = {
                Button(
                    onClick = {
                        showIncreaseConfirmDialog = false
                        onIncreaseVolumeCurrentWeek()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                ) {
                    Text("Sí, aumentar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIncreaseConfirmDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (showReduceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReduceConfirmDialog = false },
            title = { Text("Disminuir volumen semanal", fontWeight = FontWeight.Black) },
            text = { Text("Esto reducirá en 20% el volumen de la semana actual, músculo por músculo. ¿Deseas continuar?") },
            confirmButton = {
                Button(
                    onClick = {
                        showReduceConfirmDialog = false
                        onReduceVolumeCurrentWeek()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                ) {
                    Text("Sí, reducir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReduceConfirmDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun AddWidgetsChip(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .widthIn(min = 132.dp, max = 132.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
            Text(
                "Agrega widgets",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WidgetChip(
    title: String,
    value: String,
    accent: Color,
    onInfo: () -> Unit,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.45f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onRemove?.invoke()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = onRemove != null,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .widthIn(min = 126.dp, max = 142.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Quitar",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
            }
        },
        content = {
            Surface(
                modifier = Modifier
                    .widthIn(min = 126.dp, max = 142.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = onClick != null, onClick = { onClick?.invoke() ?: Unit }),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = accent,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            title,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 13.sp,
                        )
                    }

                    Surface(
                        modifier = Modifier.clickable(onClick = onInfo),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    ) {
                        Text(
                            "(i)",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Calibrar volumen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Usamos el perfil del atleta para proponerte series semanales por músculo. Este resultado también sincroniza el enfoque del programa.",
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
                    Text(
                        "Enfoque: ${preview.mode.label()}",
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(option.value) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (option.value == selectedValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.label, fontWeight = FontWeight.Bold)
                            Text(option.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (index < option.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
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

private fun buildVolumeCalibration(
    style: TrainingStyle,
    technique: Int,
    consistency: Int,
    strength: Int,
    mobility: Int,
): VolumeCalibrationResult {
    val totalScore = technique + consistency + strength + mobility
    val profileLevel = if (totalScore >= 8) AthleteProfileLevel.ADVANCED else AthleteProfileLevel.BEGINNER
    val optimalSets = when (style) {
        TrainingStyle.BODYBUILDER -> if (profileLevel == AthleteProfileLevel.ADVANCED) 18 else 15
        TrainingStyle.POWERBUILDER -> if (profileLevel == AthleteProfileLevel.ADVANCED) 16 else 14
        TrainingStyle.POWERLIFTER -> if (profileLevel == AthleteProfileLevel.ADVANCED) 14 else 12
    } + when {
        totalScore >= 11 -> 1
        totalScore <= 5 -> -1
        else -> 0
    }

    val scale = optimalSets.toFloat() / 15f
    val adjustedRecommendations = israetelBaseRecommendations.map { recommendation ->
        val scaledMin = (recommendation.minEffectiveVolume * scale).toInt().coerceAtLeast(4)
        val scaledAdaptive = (recommendation.maxAdaptiveVolume * scale).toInt().coerceAtLeast(scaledMin + 1)
        val scaledRecoverable = (recommendation.maxRecoverableVolume * scale).toInt().coerceAtLeast(scaledAdaptive + 1)
        recommendation.copy(
            minEffectiveVolume = scaledMin,
            maxAdaptiveVolume = scaledAdaptive,
            maxRecoverableVolume = scaledRecoverable,
        )
    }

    return VolumeCalibrationResult(
        mode = style.toProgramMode(),
        score = AthleteProfileScore(
            technicalScore = technique,
            consistencyScore = consistency,
            strengthScore = strength,
            mobilityScore = mobility,
            trainingStyle = style,
            totalScore = totalScore,
            profileLevel = profileLevel,
        ),
        recommendations = adjustedRecommendations,
    )
}

private val israetelBaseRecommendations = listOf(
    VolumeRecommendation("Cuadriceps", 8, 12, 18, 3),
    VolumeRecommendation("Isquiosurales", 6, 10, 16, 3),
    VolumeRecommendation("Gluteos", 6, 10, 16, 3),
    VolumeRecommendation("Pectorales", 8, 12, 18, 3),
    VolumeRecommendation("Dorsales", 10, 14, 20, 4),
    VolumeRecommendation("Trapecio", 6, 10, 16, 3),
    VolumeRecommendation("Erectores Espinales", 4, 8, 12, 2),
    VolumeRecommendation("Deltoides Anterior", 4, 8, 12, 3),
    VolumeRecommendation("Deltoides Lateral", 8, 14, 20, 4),
    VolumeRecommendation("Deltoides Posterior", 8, 14, 20, 4),
    VolumeRecommendation("Biceps", 6, 10, 16, 3),
    VolumeRecommendation("Triceps", 6, 10, 16, 3),
    VolumeRecommendation("Abdomen", 4, 8, 14, 4),
    VolumeRecommendation("Pantorrillas", 6, 10, 18, 4),
)

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
