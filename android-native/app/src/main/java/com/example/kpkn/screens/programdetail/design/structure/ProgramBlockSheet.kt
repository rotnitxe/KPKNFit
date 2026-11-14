package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockMaterializationStatus
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.domain.templates.SessionTemplateEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.util.UUID

/** Las seis chips de objetivo de e-block, en su orden. «Competición» es la puesta a punto (`TAPER`), la que pinta la fase de competición. */
internal val BLOCK_SHEET_GOALS: List<Pair<BlockGoal, String>> = listOf(
    BlockGoal.ACCUMULATION to "Acumulación",
    BlockGoal.INTENSIFICATION to "Intensificación",
    BlockGoal.REALIZATION to "Realización",
    BlockGoal.DELOAD to "Descarga",
    BlockGoal.TAPER to "Competición",
    BlockGoal.DENSITY to "Densidad",
)

/**
 * e-block, hoja del bloque: el nombre del bloque como título (o «Nuevo bloque») y [subtitle] («Semanas 4 y 5 · 29 oct – 9 nov»); los
 * campos «Nombre» y «Nota»; «Objetivo» con sus seis chips (se puede dejar sin elegir); «Progresión» como desplegable y, debajo, la vista
 * previa del motor en `.cap`. «Guardar» entrega los cuatro datos a la vez (sin nombre no se guarda) y, si hay [onDelete], «Eliminar
 * bloque» en `--danger`. Un objetivo que no está entre las chips se conserva mientras no se toque otra.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramBlockSheet(
    block: Block?,
    subtitle: String?,
    suggestedName: String,
    preview: String?,
    weekSources: List<ProgramBlockWeekSource> = emptyList(),
    onSave: (name: String, description: String?, goal: BlockGoal?, scheme: BlockProgressionScheme?, weekCount: Int, sourceWeekId: String?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(block?.id) { mutableStateOf(block?.name ?: suggestedName) }
    var description by remember(block?.id) { mutableStateOf(block?.description ?: "") }
    var goal by remember(block?.id) { mutableStateOf(block?.goal) }
    var scheme by remember(block?.id) { mutableStateOf(block?.progressionScheme) }
    var schemesOpen by remember(block?.id) { mutableStateOf(false) }
    var weekCountText by remember(block?.id) { mutableStateOf("4") }
    var copyWeek by remember(block?.id) { mutableStateOf(false) }
    var sourceWeekId by remember(block?.id) { mutableStateOf(weekSources.firstOrNull()?.id) }
    var sourcesOpen by remember(block?.id) { mutableStateOf(false) }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding),
        ) {
            StructureSheetHeader(title = block?.name?.takeIf { it.isNotBlank() } ?: "Nuevo bloque", subtitle = subtitle)
            StructureTextField(label = "Nombre", value = name, onValueChange = { name = it })
            StructureTextField(label = "Nota", value = description, onValueChange = { description = it }, placeholder = "Opcional", singleLine = false)
            StructureSheetGroup(text = "Objetivo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BLOCK_SHEET_GOALS.forEach { (option, label) ->
                    StructureChip(text = label, selected = goal == option, onClick = { goal = if (goal == option) null else option })
                }
            }
            StructureSheetGroup(text = "Progresión")
            StructureField(
                label = null,
                onClick = { schemesOpen = !schemesOpen },
                modifier = Modifier.semantics { stateDescription = if (schemesOpen) "Desplegado" else "Plegado" },
                trailing = {
                    Icon(
                        imageVector = if (schemesOpen) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = StructureInk,
                    )
                },
            ) {
                Text(
                    text = blockProgressionLabel(scheme ?: BlockProgressionScheme.NONE),
                    style = StructureFieldValueStyle,
                    color = StructureInk,
                )
            }
            if (schemesOpen) {
                BLOCK_SCHEME_CHOICES.forEach { option ->
                    SchemeOption(
                        text = blockProgressionLabel(option),
                        selected = (scheme ?: BlockProgressionScheme.NONE) == option,
                        onClick = {
                            scheme = option
                            schemesOpen = false
                        },
                    )
                }
            }
            if (preview != null) {
                StructureCaption(text = preview, modifier = Modifier.padding(top = 8.dp))
            }
            if (block == null) {
                val weekCount = weekCountText.toIntOrNull()?.coerceIn(1, 52) ?: 0
                StructureSheetGroup(text = "Semanas")
                StructureTextField(
                    label = "Número de semanas",
                    value = weekCountText,
                    onValueChange = { weekCountText = it.filter(Char::isDigit).take(2) },
                    placeholder = "4",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                StructureSheetGroup(text = "Origen de las semanas")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StructureChip(text = "Empezar vacío", selected = !copyWeek, onClick = { copyWeek = false })
                    if (weekSources.isNotEmpty()) {
                        StructureChip(text = "Copiar una semana", selected = copyWeek, onClick = { copyWeek = true })
                    }
                }
                if (copyWeek && weekSources.isNotEmpty()) {
                    val selectedSource = weekSources.firstOrNull { it.id == sourceWeekId } ?: weekSources.first()
                    StructureField(
                        label = null,
                        onClick = { sourcesOpen = !sourcesOpen },
                        modifier = Modifier.semantics { stateDescription = if (sourcesOpen) "Desplegado" else "Plegado" },
                        trailing = {
                            Icon(
                                imageVector = if (sourcesOpen) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = StructureInk,
                            )
                        },
                    ) {
                        Text(text = selectedSource.label, style = StructureFieldValueStyle, color = StructureInk)
                    }
                    if (sourcesOpen) {
                        weekSources.forEach { source ->
                            StructureMenuRow(
                                icon = if (source.id == selectedSource.id) KpknLineIcons.Check else KpknLineIcons.Calendar,
                                title = source.label,
                                onClick = { sourceWeekId = source.id; sourcesOpen = false },
                            )
                        }
                    }
                }
                if (weekCount == 0) StructureCaption(text = "Elige entre 1 y 52 semanas.", color = WizardColors.danger)
            }
            StructurePrimaryPill(
                text = "Guardar",
                onClick = {
                    val count = weekCountText.toIntOrNull()?.coerceIn(1, 52) ?: 0
                    if (count > 0) onSave(name.trim(), description.trim().ifBlank { null }, goal, scheme, count, sourceWeekId.takeIf { copyWeek })
                    else if (block != null) onSave(name.trim(), description.trim().ifBlank { null }, goal, scheme, 1, null)
                },
                enabled = name.isNotBlank() && (block != null || (weekCountText.toIntOrNull() ?: 0) in 1..52),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            if (block != null && onDelete != null) {
                StructureDangerAction(text = "Eliminar bloque", onClick = onDelete)
            }
        }
    }
}

/** Fuente elegible para sembrar un bloque nuevo; las semanas de loop y descanso no se ofrecen. */
internal data class ProgramBlockWeekSource(val id: String, val label: String, val week: ProgramWeek)

/** Crea las semanas de un bloque con IDs nuevos y conserva el contenido solo cuando se eligió copiar una semana. */
internal fun newProgramBlock(
    name: String,
    description: String?,
    goal: BlockGoal?,
    scheme: BlockProgressionScheme?,
    weekCount: Int,
    sourceWeek: ProgramWeek?,
): Block {
    val weeks = (1..weekCount.coerceIn(1, 52)).map { index ->
        val sessions = sourceWeek?.sessions?.map { SessionTemplateEngine.cloneSessionContent(it) }.orEmpty()
        ProgramWeek(
            id = UUID.randomUUID().toString(),
            name = "Semana $index",
            description = sourceWeek?.description,
            sessions = sessions,
            executionKind = if (sessions.isEmpty()) WeekExecutionKind.TRAINING else sourceWeek?.executionKind ?: WeekExecutionKind.TRAINING,
        )
    }
    val blank = sourceWeek == null || sourceWeek.sessions.isEmpty()
    return Block(
        id = UUID.randomUUID().toString(),
        name = name,
        description = description,
        mesocycles = listOf(Mesocycle(UUID.randomUUID().toString(), "Mesociclo 1", weeks = weeks)),
        goal = goal,
        progressionScheme = scheme,
        materializationPending = blank,
        materializationStatus = if (blank) BlockMaterializationStatus.OUTDATED else BlockMaterializationStatus.USER_MODIFIED,
    )
}

/** Una opción del desplegable de progresión: el nombre y, si es la elegida, la marca. 44 dp de alto. */
@Composable
private fun SchemeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = StructureFieldValueStyle,
            color = if (selected) StructureInk else StructureMuted,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(imageVector = KpknLineIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = StructureInk)
        }
    }
}

/**
 * «Semanas 4 y 5 · 29 oct – 9 nov»: los números de las semanas del bloque y, si hay fechas, su rango. Una semana: «Semana 4»; tres o más
 * seguidas: «Semanas 1 a 3». Sin semanas, null. Pura.
 */
internal fun blockSheetSubtitle(weekNumbers: List<Int>, dates: String?): String? {
    if (weekNumbers.isEmpty()) return dates
    val sorted = weekNumbers.sorted()
    val weeks = when (sorted.size) {
        1 -> "Semana ${sorted[0]}"
        2 -> "Semanas ${sorted[0]} y ${sorted[1]}"
        else -> "Semanas ${sorted.first()} a ${sorted.last()}"
    }
    return listOfNotNull(weeks, dates).joinToString(" · ")
}
