package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Lo que puede hacer un día del reparto propio: los roles que el diagrama distingue, más el descanso. */
private val CustomDayPresets: List<String> = listOf(
    SPLIT_REST_LABEL,
    "Torso",
    "Pierna",
    "Empuje",
    "Tirón",
    "Cuerpo completo",
)

/**
 * Editor del reparto propio (P5): nombre y descripción con `KpknTextField`, el día de inicio, y los siete días como filas. Cada día
 * muestra su patrón en un `KpknChip`; al tocarlo se despliegan las opciones (descanso, torso, pierna, empuje, tirón, cuerpo completo),
 * que son justo las que el diagrama colorea. «Guardar» solo se activa con un nombre y al menos un día de entreno.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitCustomEditorSheet(
    program: Program,
    defaultStartDay: Int,
    onDismiss: () -> Unit,
    onApply: (SplitTemplate, Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(program.customSplitName ?: "Mi reparto") }
    var description by rememberSaveable { mutableStateOf(program.customSplitDescription ?: "") }
    var startDay by rememberSaveable { mutableStateOf(defaultStartDay) }
    val pattern = remember(program.id) {
        mutableStateListOf<String>().apply {
            addAll(program.customSplitPattern.takeIf { it.size == 7 } ?: List(7) { SPLIT_REST_LABEL })
        }
    }
    // Un día vacío se guarda como descanso: no cuenta como día de entreno.
    val hasTrainingDay = pattern.any { canonicalSplitLabel(it) != canonicalSplitLabel(SPLIT_REST_LABEL) }
    var editingPosition by remember { mutableStateOf<Int?>(null) }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("split_editor"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Reparto propio", style = KpknType.title, color = WizardColors.text)
            KpknTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KpknTextField(
                value = description,
                onValueChange = { description = it },
                label = "Descripción",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (ProgramCalendarEngine.isCalendarized(program)) {
                Text(
                    text = "El inicio de la semana lo fija el calendario del programa.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            } else {
                KpknModule {
                    KpknModuleHeader(label = "Inicio de semana")
                    Spacer(Modifier.height(10.dp))
                    SplitStartDayControl(startDay = startDay, onSelect = { startDay = it })
                }
            }
            KpknModule {
                KpknModuleHeader(label = "Distribución semanal")
                pattern.forEachIndexed { position, label ->
                    Spacer(Modifier.height(12.dp))
                    SplitCustomDayRow(
                        dayName = SPLIT_WEEKDAY_NAMES[splitWeekdayAt(startDay, position) - 1],
                        label = label,
                        expanded = editingPosition == position,
                        onToggle = { editingPosition = if (editingPosition == position) null else position },
                        onPick = { preset ->
                            pattern[position] = preset
                            editingPosition = null
                        },
                    )
                }
            }
            KpknPrimaryPill(
                text = "Guardar",
                onClick = {
                    val normalizedPattern = pattern.map { it.trim().ifBlank { SPLIT_REST_LABEL } }
                    onApply(
                        SplitTemplate(
                            id = "custom",
                            name = name.trim().ifBlank { "Mi reparto" },
                            description = description.trim().ifBlank { "Reparto propio" },
                            tags = listOf(SplitTag.PERSONALIZADO),
                            pattern = normalizedPattern,
                            difficulty = Difficulty.INTERMEDIO,
                            pros = listOf("Diseñado según tus reglas"),
                            cons = listOf("Depende de una buena distribución de fatiga"),
                        ),
                        startDay,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && hasTrainingDay,
                tag = "split_guardar_propio",
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * Un día del reparto propio: su nombre y su patrón en un `KpknChip` (encendido si es día de entreno). Tocar el chip despliega las
 * opciones; elegir una la guarda y cierra la fila.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitCustomDayRow(
    dayName: String,
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (String) -> Unit,
) {
    val training = splitDayMark(label).role != SplitDayRole.REST
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dayName,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.width(96.dp),
            )
            KpknChip(
                text = label.ifBlank { SPLIT_REST_LABEL },
                selected = training,
                onClick = onToggle,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            // Sangradas bajo el chip del día: el ancho de la columna de nombres son 96 dp.
            FlowRow(
                modifier = Modifier.padding(start = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CustomDayPresets.forEach { preset ->
                    KpknChip(
                        text = preset,
                        selected = canonicalSplitLabel(preset) == canonicalSplitLabel(label),
                        onClick = { onPick(preset) },
                    )
                }
            }
        }
    }
}
