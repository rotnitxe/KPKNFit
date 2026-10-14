package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.screens.sessioneditor.components.formatCatalogSupersetRestLabel
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Configuración de una superserie antes de crearla desde la selección del catálogo (SE6b): hoja KPKN. Las rondas van en
 * `KpknNumberCell` con sus botones; los descansos siguen con el mismo selector de siempre (±15 s y los atajos).
 */
@Composable
internal fun CatalogSupersetConfiguratorDialog(
    selectedExercises: List<ExerciseMuscleInfo>,
    initialConfig: CatalogSupersetConfig = CatalogSupersetConfig(),
    onDismiss: () -> Unit,
    onConfirm: (CatalogSupersetConfig) -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        CatalogSupersetConfiguratorContent(
            selectedExercises = selectedExercises,
            initialConfig = initialConfig,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

/** El cuerpo de la configuración, suelto para los tests y la hoja de render. Sin estado externo: guarda sus valores y avisa al crear. */
@Composable
internal fun CatalogSupersetConfiguratorContent(
    selectedExercises: List<ExerciseMuscleInfo>,
    initialConfig: CatalogSupersetConfig,
    onDismiss: () -> Unit,
    onConfirm: (CatalogSupersetConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rounds by remember { mutableStateOf(initialConfig.rounds) }
    var restBetween by remember { mutableStateOf(initialConfig.restBetweenExercisesSeconds) }
    var restAfter by remember { mutableStateOf(initialConfig.restAfterSupersetSeconds) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Superserie · ${selectedExercises.size} ejercicios",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            Text(
                text = "Configurar superserie",
                style = KpknType.title,
                color = WizardColors.text,
            )
        }

        KpknModule {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Orden", style = KpknType.bodyStrong, color = WizardColors.text)
                selectedExercises.forEachIndexed { index, info ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(SymbolPalette.columna.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${'A' + index}",
                                style = KpknType.caption,
                                color = SymbolPalette.columna,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = info.name,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        KpknModule {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Rondas",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    modifier = Modifier.weight(1f),
                )
                StepperRow(
                    value = "$rounds",
                    contentDescription = "Rondas: $rounds",
                    onStep = { delta -> rounds = (rounds + delta).coerceIn(1, 10) },
                    minusDescription = "Quitar una ronda",
                    plusDescription = "Añadir una ronda",
                )
            }
        }

        KpknModule {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RestRow(
                    title = "Descanso entre ejercicios",
                    seconds = restBetween,
                    presets = listOf(0, 30, 60, 90),
                    onChange = { restBetween = it },
                )
            }
        }

        KpknModule {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RestRow(
                    title = "Descanso tras la superserie",
                    seconds = restAfter,
                    presets = listOf(60, 90, 120, 180),
                    onChange = { restAfter = it },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknTextAction(
                text = "Cancelar",
                onClick = onDismiss,
            )
            KpknPrimaryPill(
                text = "Crear superserie",
                onClick = {
                    onConfirm(
                        CatalogSupersetConfig(
                            rounds = rounds,
                            restBetweenExercisesSeconds = restBetween,
                            restAfterSupersetSeconds = restAfter,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Un valor con «−» y «+» a los lados: el número va en `KpknNumberCell` (SE1), que también acepta un arrastre horizontal. */
@Composable
private fun StepperRow(
    value: String,
    contentDescription: String,
    onStep: (Int) -> Unit,
    minusDescription: String,
    plusDescription: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KpknIconButton(
            icon = KpknLineIcons.Minus,
            contentDescription = minusDescription,
            onClick = { onStep(-1) },
        )
        KpknNumberCell(
            value = value,
            input = KpknCellInput.ReadOnly,
            contentDescription = contentDescription,
            onStep = onStep,
            modifier = Modifier.width(72.dp),
        )
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = plusDescription,
            onClick = { onStep(1) },
        )
    }
}

/** Un descanso: título y el valor con sus botones de ±15 s, y debajo los atajos de siempre (SE6b). Mismo rango: 0 a 600 s. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestRow(
    title: String,
    seconds: Int,
    presets: List<Int>,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            StepperRow(
                value = formatCatalogSupersetRestLabel(seconds),
                contentDescription = "$title: ${formatCatalogSupersetRestLabel(seconds)}",
                onStep = { delta -> onChange((seconds + delta * 15).coerceIn(0, 600)) },
                minusDescription = "Quitar 15 segundos",
                plusDescription = "Añadir 15 segundos",
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { sec ->
                KpknChip(
                    text = formatCatalogSupersetRestLabel(sec),
                    selected = seconds == sec,
                    onClick = { onChange(sec) },
                )
            }
        }
    }
}

