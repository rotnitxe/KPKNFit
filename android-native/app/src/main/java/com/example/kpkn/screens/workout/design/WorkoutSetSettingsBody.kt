package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknType

/** Modos de la serie que ofrece el ajuste, en el mismo orden que el desplegable de antes. */
internal val WorkoutSetModeOptions: List<Pair<TrainingMode, String>> = listOf(
    TrainingMode.REPS to "Repeticiones",
    TrainingMode.AMRAP to "AMRAP",
    TrainingMode.RM to "RM",
    TrainingMode.SOLO_RPE to "Solo RPE",
    TrainingMode.TIME to "Tiempo",
    TrainingMode.DISTANCE to "Distancia",
    TrainingMode.CUSTOM to "Personalizado",
)

/**
 * Cuerpo de «Editar series» (ajustes de la serie) con piezas KPKN, sin estado de negocio: el modo en chips, el unilateral con un
 * interruptor de 56 dp y, si lo es, el orden de lados y el descanso entre lados; la carga inteligente como fila tocable, y las series
 * ([setsSlot]). El «Confirmar cambios» lo pone quien llama, para que también aparezca con cardio. Cada cambio sale por su lambda.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutSetSettingsBody(
    trainingMode: TrainingMode,
    onTrainingModeChange: (TrainingMode) -> Unit,
    unilateral: Boolean,
    onUnilateralChange: (Boolean) -> Unit,
    sideOrder: UnilateralSideOrder,
    onSideOrderChange: (UnilateralSideOrder) -> Unit,
    restBetweenSidesSeconds: Int,
    onRestBetweenSidesChange: (Int) -> Unit,
    smartLoadSummary: String?,
    onOpenSmartLoad: () -> Unit,
    modifier: Modifier = Modifier,
    setsSlot: @Composable () -> Unit = {},
) {
    var pickingRest by remember { mutableStateOf(false) }
    val restLabel = KpknSetFormat.rest(restBetweenSidesSeconds)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KpknModule {
            KpknModuleHeader(label = "Tipo de serie")
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorkoutSetModeOptions.forEach { (mode, label) ->
                    KpknChip(
                        text = label,
                        selected = mode == trainingMode,
                        onClick = { if (mode != trainingMode) onTrainingModeChange(mode) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            WorkoutStructureDivider()
            WorkoutStructureSettingRow(
                label = "Unilateral",
                modifier = Modifier.toggleable(
                    value = unilateral,
                    role = Role.Switch,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onValueChange = onUnilateralChange,
                ),
                control = {
                    // El interruptor no lleva acción propia: la fila entera es el `Role.Switch` con el nombre «Unilateral».
                    KpknSwitch(
                        checked = unilateral,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                },
            )
            if (unilateral) {
                WorkoutStructureDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "Orden de lados", style = KpknType.bodyStrong, color = WizardColors.text)
                    KpknSegmentedControl(
                        options = listOf("Izquierda primero", "Derecha primero"),
                        selectedIndex = if (sideOrder == UnilateralSideOrder.LEFT_RIGHT) 0 else 1,
                        onSelect = { index ->
                            val next = if (index == 0) UnilateralSideOrder.LEFT_RIGHT else UnilateralSideOrder.RIGHT_LEFT
                            if (next != sideOrder) onSideOrderChange(next)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WorkoutStructureDivider()
                WorkoutStructureSettingRow(
                    label = "Descanso entre lados",
                    control = {
                        KpknNumberCell(
                            value = restLabel,
                            input = KpknCellInput.Picker(onOpen = { pickingRest = true }),
                            contentDescription = "Descanso entre lados, ${restLabel.ifEmpty { "sin tiempo" }}",
                            modifier = Modifier.width(96.dp),
                        )
                    },
                )
            }
        }

        KpknModule(
            onClick = onOpenSmartLoad,
            onClickLabel = "Configurar la carga inteligente",
        ) {
            WorkoutStructureSettingRow(
                label = "Carga inteligente",
                subtitle = smartLoadSummary ?: "Configurar",
                control = {
                    Icon(
                        imageVector = KpknLineIcons.ChevronRight,
                        contentDescription = null,
                        tint = WizardColors.textFaint,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }

        setsSlot()
    }

    if (pickingRest) {
        KpknNativeTimePickerDialog(
            title = "Descanso entre lados",
            initialHour = (restBetweenSidesSeconds / 60).coerceIn(0, 23),
            initialMinute = (restBetweenSidesSeconds % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { hour, minute ->
                onRestBetweenSidesChange((hour * 60 + minute).coerceAtLeast(0))
                pickingRest = false
            },
            onDismiss = { pickingRest = false },
        )
    }
}
