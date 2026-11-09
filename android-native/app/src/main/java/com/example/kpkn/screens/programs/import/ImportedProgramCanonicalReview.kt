package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.importer.ImportIssue
import com.example.kpkn.domain.training.importer.ImportIssueKind
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedProgramSourceMap
import com.example.kpkn.domain.training.importer.ImportedProgramBuilder
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

internal data class ImportedSessionEditorTarget(
    val programId: String,
    val sessionId: String,
    val weekId: String,
    val macroIndex: Int,
    val mesoIndex: Int,
    val dayOfWeek: Int?,
)

internal fun Program.editorTarget(sessionId: String): ImportedSessionEditorTarget? {
    macrocycles.forEachIndexed { macroIndex, macro ->
        var mesoIndex = 0
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                val currentMesoIndex = mesoIndex++
                meso.weeks.forEach { week ->
                    week.sessions.firstOrNull { it.id == sessionId }?.let { session ->
                        return ImportedSessionEditorTarget(
                            programId = id,
                            sessionId = session.id,
                            weekId = week.id,
                            macroIndex = macroIndex,
                            mesoIndex = currentMesoIndex,
                            dayOfWeek = session.dayOfWeek,
                        )
                    }
                }
            }
        }
    }
    return null
}

internal fun Program.editorTargetForExercise(exerciseId: String): ImportedSessionEditorTarget? {
    macrocycles.forEachIndexed { macroIndex, macro ->
        var mesoIndex = 0
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                val currentMesoIndex = mesoIndex++
                meso.weeks.forEach { week ->
                    week.sessions.firstOrNull { session -> session.allExercises().any { it.id == exerciseId } }?.let { session ->
                        return ImportedSessionEditorTarget(
                            programId = id,
                            sessionId = session.id,
                            weekId = week.id,
                            macroIndex = macroIndex,
                            mesoIndex = currentMesoIndex,
                            dayOfWeek = session.dayOfWeek,
                        )
                    }
                }
            }
        }
    }
    return null
}

/** Sesiones cuyo día provino explícitamente del documento y no debe sobrescribirse desde el selector global. */
internal fun explicitImportedSessionIds(source: ImportedProgramDraft?, sourceMap: ImportedProgramSourceMap): Set<String> =
    source?.weeks.orEmpty().flatMap { it.days }.filter { day -> day.dayOfWeek?.let { it in 1..7 } == true }
        .mapNotNull { sourceMap.sessionIdsByDayId[it.id] }.toSet()

internal fun Program.withImportedWeekdays(weekdays: Set<Int>, preservedSessionIds: Set<String> = emptySet()): Program {
    val trainingDays = weekdays.filter { it in 1..7 }.distinct().sorted()
    val updatedMacros = macrocycles.map { macro ->
        macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { meso ->
                meso.copy(weeks = meso.weeks.map { week ->
                    val assigned = ImportedProgramBuilder.assignWeekdays(
                        week.sessions.count { it.requirement == com.example.kpkn.data.models.SessionRequirement.REQUIRED },
                        trainingDays,
                    )
                    var trainingSessionIndex = 0
                    week.copy(sessions = week.sessions.map { session ->
                        if (session.id in preservedSessionIds || session.requirement != com.example.kpkn.data.models.SessionRequirement.REQUIRED) {
                            session
                        } else {
                            val day = assigned.getOrNull(trainingSessionIndex++)
                            session.copy(dayOfWeek = day, assignedDays = listOfNotNull(day))
                        }
                    })
                })
            })
        })
    }
    return copy(
        macrocycles = updatedMacros,
        startDay = trainingDays.firstOrNull(),
        weekDays = trainingDays.size.takeIf { it > 0 },
        schedulePlan = if (trainingDays.isEmpty()) null else {
            ProgramSchedulePlan(weekStartDay = trainingDays.first(), trainingDays = trainingDays.toSet())
        },
    )
}

/** Adds one empty session shell to an imported draft; all exercise and set data remains the user's responsibility. */
internal fun Program.withEmptyImportedSession(targetWeekId: String? = null): Pair<Program, String>? {
    if (targetWeekId != null) {
        val targetWeek = macrocycles.asSequence().flatMap { it.blocks.asSequence() }
            .flatMap { it.mesocycles.asSequence() }.flatMap { it.weeks.asSequence() }
            .firstOrNull { it.id == targetWeekId } ?: return null
        val sessionId = "$id-empty-session-${java.util.UUID.randomUUID()}"
        val days = schedulePlan?.trainingDays.orEmpty().sorted()
        val assignedDay = ImportedProgramBuilder.assignWeekdays(targetWeek.sessions.size + 1, days).lastOrNull()
        val updatedWeek = targetWeek.copy(
            sessions = targetWeek.sessions + Session(
                id = sessionId,
                name = "Sesión nueva",
                dayOfWeek = assignedDay,
                assignedDays = listOfNotNull(assignedDay),
            ),
        )
        val updatedMacros = macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso ->
                    meso.copy(weeks = meso.weeks.map { week -> if (week.id == targetWeekId) updatedWeek else week })
                })
            })
        }
        return copy(macrocycles = updatedMacros) to sessionId
    }
    val macro = macrocycles.firstOrNull() ?: Macrocycle(id = "$id-macro", name = "Mi plan")
    val block = macro.blocks.firstOrNull() ?: Block(id = "$id-block", name = "Bloque importado")
    val meso = block.mesocycles.firstOrNull() ?: Mesocycle(id = "$id-meso", name = "Importado")
    val week = meso.weeks.firstOrNull() ?: ProgramWeek(id = "$id-empty-week", name = "Semana 1")
    val sessionId = "$id-empty-session-${java.util.UUID.randomUUID()}"
    val days = schedulePlan?.trainingDays.orEmpty().sorted()
    val assignedDay = ImportedProgramBuilder.assignWeekdays(week.sessions.size + 1, days).lastOrNull()
    val session = Session(
        id = sessionId,
        name = "Sesión nueva",
        dayOfWeek = assignedDay,
        assignedDays = listOfNotNull(assignedDay),
    )
    val updatedWeek = week.copy(sessions = week.sessions + session)
    val updatedMeso = if (meso.weeks.isEmpty()) meso.copy(weeks = listOf(updatedWeek))
    else meso.copy(weeks = meso.weeks.mapIndexed { index, old -> if (index == 0) updatedWeek else old })
    val updatedBlock = if (block.mesocycles.isEmpty()) block.copy(mesocycles = listOf(updatedMeso))
    else block.copy(mesocycles = block.mesocycles.mapIndexed { index, old -> if (index == 0) updatedMeso else old })
    val updatedMacro = if (macro.blocks.isEmpty()) macro.copy(blocks = listOf(updatedBlock))
    else macro.copy(blocks = macro.blocks.mapIndexed { index, old -> if (index == 0) updatedBlock else old })
    val updatedMacros = if (macrocycles.isEmpty()) listOf(updatedMacro)
    else macrocycles.mapIndexed { index, old -> if (index == 0) updatedMacro else old }
    return copy(macrocycles = updatedMacros) to sessionId
}

@Composable
internal fun ImportedProgramCanonicalReview(
    source: ImportedProgramDraft,
    sourceMap: ImportedProgramSourceMap,
    program: Program,
    onEditSession: (ImportedSessionEditorTarget) -> Unit,
    issues: List<ImportIssue> = emptyList(),
    onShowWarnings: () -> Unit = {},
    onCreateSession: (weekId: String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sourceByExerciseId = source.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
        .associateBy { it.id }
    val sourceByProgramExercise = sourceMap.programExerciseIdsBySourceExerciseId.entries
        .groupBy({ it.value }, { sourceByExerciseId[it.key] })
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            "El programa editable ya conserva cada sesión y cada serie. Abre una sesión para cambiar sus opciones completas.",
            style = WizardTypography.note,
            color = WizardColors.textMuted,
        )
        if (issues.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().importGlassPanel(compact = true).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Falta revisar", style = WizardTypography.controlLabel, color = WizardColors.text)
                issues.forEach { issue ->
                    val message = when (issue.kind) {
                        ImportIssueKind.NO_WEEKS -> "No quedó ninguna semana."
                        ImportIssueKind.EMPTY_WEEK -> "La semana ${issue.weekIndex} no tiene ejercicios."
                        ImportIssueKind.INCOMPLETE_EXERCISE -> "Completa las series o la prescripción de «${issue.rawName.orEmpty()}» antes de guardar."
                        ImportIssueKind.EMPTY_DAY -> {
                            val session = issue.dayId?.let { id -> program.macrocycles.flatMap { it.blocks }
                                .flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
                                .firstOrNull { it.id == id } }
                            "El día «${session?.name.orEmpty()}» de la semana ${issue.weekIndex} no tiene ejercicios."
                        }
                        ImportIssueKind.UNDECIDED_EXERCISE -> "Falta elegir una variante del catálogo para «${issue.rawName}»."
                        ImportIssueKind.PENDING_WARNINGS ->
                            if (issue.count == 1) "Hay 1 aviso sin revisar." else "Hay ${issue.count} avisos sin revisar."
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message, style = WizardTypography.note, color = WizardColors.text, modifier = Modifier.weight(1f))
                        val target = issue.exerciseId?.let(program::editorTargetForExercise)
                            ?: issue.dayId?.let(program::editorTarget)
                        if (target != null) {
                            ImportTextAction(label = "Abrir editor", onClick = { onEditSession(target) })
                        } else if (issue.kind == ImportIssueKind.PENDING_WARNINGS) {
                            ImportTextAction(label = "Ver avisos", onClick = onShowWarnings)
                        } else if (issue.kind == ImportIssueKind.NO_WEEKS || issue.kind == ImportIssueKind.EMPTY_WEEK) {
                            val targetWeekId = issue.weekIndex?.let { index ->
                                program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
                                    .getOrNull(index - 1)?.id
                            }
                            ImportTextAction(label = "Crear sesión vacía", onClick = { onCreateSession(targetWeekId) })
                        }
                    }
                }
            }
        }
        program.macrocycles.forEachIndexed { macroIndex, macro ->
            var mesoIndex = 0
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    val currentMesoIndex = mesoIndex++
                    meso.weeks.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val weekLabel = when (week.executionKind) {
                                com.example.kpkn.data.models.WeekExecutionKind.DELOAD -> "${week.name} · Descarga"
                                com.example.kpkn.data.models.WeekExecutionKind.REST -> "${week.name} · Descanso"
                                com.example.kpkn.data.models.WeekExecutionKind.TRAINING -> week.name
                            }
                            Text(weekLabel, style = WizardTypography.eyebrow, color = WizardColors.textMuted)
                            week.sessions.forEach { session ->
                                Column(
                                    Modifier.fillMaxWidth().importGlassPanel(compact = true).padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(session.name, style = WizardTypography.controlLabel, color = WizardColors.text, modifier = Modifier.weight(1f))
                                        ImportTextAction(
                                            label = "Editar sesión completa",
                                            onClick = {
                                                onEditSession(
                                                    ImportedSessionEditorTarget(
                                                        programId = program.id,
                                                        sessionId = session.id,
                                                        weekId = week.id,
                                                        macroIndex = macroIndex,
                                                        mesoIndex = currentMesoIndex,
                                                        dayOfWeek = session.dayOfWeek,
                                                    ),
                                                )
                                            },
                                            modifier = Modifier.testTag("import-full-editor-${session.id}"),
                                        )
                                    }
                                    session.description?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = WizardTypography.note, color = WizardColors.textMuted)
                                    }
                                    if (session.warmup.isNotEmpty()) {
                                        Text("Preparación: ${session.warmup.size} elementos", style = WizardTypography.note, color = WizardColors.textMuted)
                                    }
                                    session.allExercises().forEach { exercise ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(exercise.name, style = WizardTypography.cardSubtitle, color = WizardColors.text)
                                            if (exercise.sets.isEmpty()) {
                                                Text("Sin series registradas", style = WizardTypography.note, color = WizardColors.textMuted)
                                            } else {
                                                exercise.sets.forEachIndexed { setIndex, set ->
                                                    val details = buildList {
                                                        set.targetRepsRange?.let { add("${it.min}–${it.max} reps") }
                                                            ?: set.targetReps?.let { add("$it reps") }
                                                        set.targetDuration?.let { add("${it}s") }
                                                        set.weight?.let { add("${decimalText(it)} kg") }
                                                        set.targetPercentageRM?.let { add("${decimalText(it)}% 1RM") }
                                                        set.targetRPE?.let { add("RPE ${decimalText(it)}") }
                                                        set.targetRIR?.let { add("RIR $it") }
                                                        (set.restAfterSeconds ?: exercise.restTime)?.let { add("descanso ${it}s") }
                                                    }
                                                    Text(
                                                        if (details.isEmpty()) "Serie ${setIndex + 1}" else "Serie ${setIndex + 1}: ${details.joinToString(" · ")}",
                                                        style = WizardTypography.note,
                                                        color = WizardColors.textMuted,
                                                    )
                                                }
                                            }
                                            exercise.executionCues.filter { it.isNotBlank() }.distinct().forEach {
                                                Text(it, style = WizardTypography.note, color = WizardColors.textMuted)
                                            }
                                            sourceByProgramExercise[exercise.id].orEmpty().filterNotNull()
                                                .mapNotNull { it.sourceText?.takeIf(String::isNotBlank) }
                                                .distinct().forEach { original ->
                                                    Text("Origen: $original", style = WizardTypography.note, color = WizardColors.textMuted)
                                                }
                                        }
                                    }
                                    if (session.exercises.isEmpty() && session.parts.isEmpty()) {
                                        Text("Sesión vacía", style = WizardTypography.note, color = WizardColors.textMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
