package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.screens.onboarding.SetupPlanReveals
import com.example.kpkn.screens.onboarding.design.entreno.plan.MAX_MAIN_EXERCISES
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanBlockModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFicha
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaContent
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaCopy
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaExercise
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaLine
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaOverlay
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaRow
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaRowIcon
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaSection
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaSession
import com.example.kpkn.screens.onboarding.design.entreno.plan.kpknImageOfExercise
import com.example.kpkn.screens.onboarding.design.entreno.plan.planFichaWeek
import com.example.kpkn.screens.programdetail.MAX_CHANGES
import com.example.kpkn.screens.programdetail.ProgramPlanDisplaySummary
import com.example.kpkn.screens.programs.PlanInfoFicha
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** «6 oct – 16 nov»: el primer y el último día del calendario del programa; null sin fechas. */
internal fun planCalendarRange(dates: Collection<LocalDate>): String? {
    val first = dates.minOrNull() ?: return null
    val last = dates.maxOrNull() ?: return null
    val format = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
    fun label(date: LocalDate) = format.format(date).replace(".", "")
    return "${label(first)} – ${label(last)}"
}

/** Un cambio registrado («Remo con barra → remo en máquina · Cambio manual») partido en título y línea pequeña. */
internal fun planChangeEntry(change: String): Pair<String, String> {
    val cut = change.indexOf(" · ")
    return if (cut < 0) change to "" else change.substring(0, cut) to change.substring(cut + 3)
}

/** El origen, al final y en letra pequeña: la procedencia y, si la hay, su fuente o su autor. */
internal fun planOriginLine(summary: ProgramPlanDisplaySummary): String =
    listOfNotNull(summary.provenanceLabel.takeIf { it.isNotBlank() }, summary.sourceLine?.takeIf { it.isNotBlank() }).joinToString(" · ")

/**
 * «Sobre el programa» (lienzo Arreglos v1, escenas p-about y p-about-end): la ficha del asistente en modo lectura, sin pie de elegir.
 * Conserva todo lo que tenía la hoja de texto: la descripción, el material, la sesión más larga y el calendario (chips), las cargas de
 * referencia por resolver y los cambios registrados («Cargas y cambios»), el origen (atribución) y «Ver cómo funciona», que abre la
 * ficha del catálogo cuando el programa viene de él.
 *
 * [description] es la descripción del programa, que se usa si el catálogo no trae resumen. [calendarRange] es «6 oct – 16 nov».
 * Con [program] la ficha enseña además los ejercicios principales (con su imagen de KPKN), la estructura y la semana día por día;
 * [currentWeekId] es la semana en curso (si no, la de la fecha de hoy o la primera de entreno). [isActive] pone «Activo» en la portada.
 */
@Composable
internal fun ProgramPlanSheet(
    summary: ProgramPlanDisplaySummary,
    onDismiss: () -> Unit,
    /** Abre la ficha de solo lectura del plan del catálogo (su id). Sin él, el botón no aparece. */
    onOpenPlanInfo: ((String) -> Unit)? = null,
    description: String? = null,
    calendarRange: String? = null,
    program: Program? = null,
    isActive: Boolean = true,
    currentWeekId: String? = null,
    onSaveDescription: ((String) -> Unit)? = null,
) {
    val canOpen = onOpenPlanInfo != null
    var editingDescription by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var draftDescription by androidx.compose.runtime.remember(description) {
        androidx.compose.runtime.mutableStateOf(description.orEmpty())
    }
    val ficha = remember(summary, program, description, calendarRange, isActive, currentWeekId, canOpen) {
        programPlanFicha(
            summary = summary,
            program = program,
            description = description,
            calendarRange = calendarRange,
            isActive = isActive,
            canOpenPlanInfo = canOpen,
            currentWeekId = currentWeekId,
        )
    }
    val entryId = summary.catalogEntry?.id
    val isManual = entryId == null
    PlanFichaOverlay(
        ficha = ficha,
        onClose = onDismiss,
        onSecondary = when {
            entryId != null && onOpenPlanInfo != null -> ({ onOpenPlanInfo(entryId) })
            else -> null
        },
        descriptionHeaderSlot = if (isManual && onSaveDescription != null) {
            {
                val currentText = description?.trim().orEmpty()
                com.example.kpkn.ui.design.KpknModule(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Sobre el programa",
                                style = com.example.kpkn.ui.design.KpknType.bodyStrong,
                                color = com.example.kpkn.screens.onboarding.design.WizardColors.text,
                            )
                            if (!editingDescription) {
                                com.example.kpkn.ui.design.KpknTextAction(
                                    text = if (currentText.isNotEmpty()) "Editar descripción" else "Añadir descripción",
                                    onClick = {
                                        draftDescription = currentText
                                        editingDescription = true
                                    },
                                )
                            }
                        }
                        if (editingDescription) {
                            com.example.kpkn.ui.design.KpknTextField(
                                value = draftDescription,
                                onValueChange = { draftDescription = it },
                                label = "Descripción",
                                placeholder = "Describe el objetivo, enfoque o notas clave…",
                                singleLine = false,
                                minLines = 3,
                                maxLines = 6,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                            ) {
                                com.example.kpkn.ui.design.KpknTextAction(
                                    text = "Cancelar",
                                    onClick = { editingDescription = false },
                                )
                                com.example.kpkn.ui.design.KpknTextAction(
                                    text = "Guardar",
                                    onClick = {
                                        onSaveDescription(draftDescription.trim())
                                        editingDescription = false
                                    },
                                )
                            }
                        } else if (currentText.isNotEmpty()) {
                            Text(
                                text = currentText,
                                style = com.example.kpkn.ui.design.KpknType.body,
                                color = com.example.kpkn.screens.onboarding.design.WizardColors.text,
                            )
                        } else {
                            Text(
                                text = ficha.detail.description.ifBlank {
                                    "Añade el objetivo, enfoque o notas clave de tu programa."
                                },
                                style = com.example.kpkn.ui.design.KpknType.body,
                                color = com.example.kpkn.screens.onboarding.design.WizardColors.textMuted,
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

/**
 * La ficha de «Sobre el programa», pura. [imageOf] es la imagen de KPKN de un ejercicio (null = esa fila sin miniatura) y [today] la
 * fecha con la que se busca la semana en curso si no llega [currentWeekId].
 */
internal fun programPlanFicha(
    summary: ProgramPlanDisplaySummary,
    program: Program?,
    description: String?,
    calendarRange: String?,
    isActive: Boolean,
    canOpenPlanInfo: Boolean,
    currentWeekId: String? = null,
    today: LocalDate = LocalDate.now(),
    imageOf: (Exercise) -> Int? = ::kpknImageOfExercise,
): PlanFicha {
    val entry = summary.catalogEntry
    val blocks = program?.macrocycles?.flatMap { it.blocks }.orEmpty()
    val totalWeeks = blocks.flatMap { it.mesocycles }.sumOf { it.weeks.size }
    val allSessions = blocks.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
    val exerciseCount = allSessions.flatMap { it.allExercises() }.map { it.id }.distinct().size
    val manualStructure = when {
        program == null || entry != null -> null
        allSessions.isEmpty() -> "Todavía no hay sesiones. Añade semanas, días y ejercicios para dar forma a tu programa."
        else -> "${SpanishPlurals.weeks(totalWeeks)} · ${SpanishPlurals.sessions(allSessions.size)} · ${SpanishPlurals.exercises(exerciseCount)}"
    }
    val week = program?.let { shownWeekOf(it, currentWeekId, today) }
    val weekStart = program?.startDay ?: program?.highEnergyDay ?: 1
    val sessions = week?.sessions.orEmpty()
        .mapIndexed { index, session -> (SetupPlanReveals.dayOf(session) ?: (index % 7 + 1)) to session }
        .sortedBy { ((it.first - weekStart + 7) % 7) }
    val days = if (sessions.isEmpty()) emptyList() else planFichaWeek(sessions.groupBy({ it.first }) { sessionOf(it.second) }, weekStart = weekStart)
    val card = PlanCardModel(
        id = entry?.id ?: program?.id ?: summary.planName,
        title = program?.name?.trim()?.takeIf { it.isNotEmpty() } ?: summary.planName,
        kicker = if (isActive) PlanFichaCopy.EYEBROW_PROGRAM_ACTIVE else PlanFichaCopy.EYEBROW_PROGRAM,
        blurb = summary.summary ?: manualStructure.orEmpty(),
        // Sin perfil: el acento verde de «hecho» del lienzo y la portada neutra.
        profile = null,
        daysLabel = entry?.let(PlanInfoFicha::goalLabelOf).orEmpty(),
        minutesLabel = if (totalWeeks > 0) SpanishPlurals.weeks(totalWeeks) else "",
        levelLabel = if (blocks.size > 1) SpanishPlurals.blocks(blocks.size) else "",
        coverSeed = Math.floorMod((program?.id ?: summary.planName).hashCode(), 9973),
        coverIdentity = program?.let { com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram(it) },
        coverImage = program?.coverImage,
    )
    val cleanDescription = description?.trim()?.takeIf { it.isNotEmpty() }
    val body = summary.summary ?: cleanDescription ?: manualStructure
    val detail = PlanDetailModel(
        card = card,
        description = body.orEmpty(),
        mainExercises = emptyList(),
        blocks = program?.let { SetupPlanReveals.blocksOf(it) }.orEmpty()
            .map { block -> PlanBlockModel(label = block.label, weeksLabel = block.weeksLabel, detail = block.detail) },
        week = emptyList(),
        reasons = emptyList(),
        notes = if (entry == null) emptyList() else summary.planNotes,
        attribution = planOriginLine(summary).ifBlank { null },
    )
    val chips = listOfNotNull(
        if (entry != null) summary.requiredMaterial.trim().takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() } else null,
        summary.maxSessionMinutes?.let { "Sesión más larga ≈ $it min" },
        calendarRange?.takeIf { it.isNotBlank() },
    )
    val rows = buildList {
        if (summary.pendingLoadCount > 0) {
            add(PlanFichaRow(PlanFichaRowIcon.TARGET, PlanFichaCopy.REFERENCE_LOADS, "${summary.pendingLoadCount} por resolver"))
        }
        summary.changes.take(MAX_CHANGES).map(::planChangeEntry).forEach { (title, line) ->
            add(PlanFichaRow(PlanFichaRowIcon.SWAP, title, line))
        }
    }
    val todayDay = today.dayOfWeek.value
    val content = PlanFichaContent(
        chips = chips,
        exercises = mainExercisesOf(sessions.map { it.second }, imageOf),
        days = days,
        openDay = days.firstOrNull { it.day == todayDay && !it.rest }?.day ?: days.firstOrNull { !it.rest }?.day,
        sections = if (rows.isEmpty()) emptyList() else listOf(PlanFichaSection(PlanFichaCopy.LOADS_AND_CHANGES, rows)),
        primaryLabel = null,
        secondaryLabel = if (canOpenPlanInfo && entry != null) PlanFichaCopy.HOW_IT_WORKS else null,
    )
    return PlanFicha(detail, content)
}

/** La semana que enseña «Tu semana»: la que está en curso; si no se sabe, la de la fecha de hoy; si no, la primera de entreno. */
internal fun shownWeekOf(program: Program, currentWeekId: String?, today: LocalDate): ProgramWeek? {
    val weeks = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
    currentWeekId?.takeIf { it.isNotBlank() }?.let { id ->
        weeks.firstOrNull { it.id == id && it.sessions.isNotEmpty() }?.let { return it }
    }
    val monday = today.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    fun parse(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    weeks.firstOrNull { week ->
        week.sessions.isNotEmpty() && week.trainingDayDates.values.mapNotNull(::parse).any { it in monday..sunday }
    }?.let { return it }
    weeks.firstOrNull { week ->
        val start = parse(week.startDate)
        val end = parse(week.endDate)
        week.sessions.isNotEmpty() && start != null && end != null && today in start..end
    }?.let { return it }
    return SetupPlanReveals.firstTrainingWeek(program)
}

/** Una sesión del programa como día de «Tu semana»: su nombre, sus ejercicios con la prescripción corta y su duración estimada. */
private fun sessionOf(session: Session): PlanFichaSession {
    val exercises = session.allExercises().filter { exercise ->
        exercise.cardioDetails != null || exercise.sets.any { set -> !set.isEmptySlot }
    }
    return PlanFichaSession(
        title = session.name.trim().ifEmpty { "Sesión" },
        lines = exercises.map { exercise -> PlanFichaLine(exercise.name, exercisePrescription(exercise)) },
        minutes = SessionDurationEstimator.estimate(session).totalMinutes,
        glyphOf = exercises.firstOrNull { it.cardioDetails == null }?.name,
    )
}

/** «4 × 6–8 · RIR 2» (o «· RPE 8»), como en la semana tipo del asistente; el cardio, sus minutos. Vacío sin cifras. */
internal fun exercisePrescription(exercise: Exercise): String {
    exercise.cardioDetails?.let { cardio ->
        val minutes = (cardio.effectiveDurationSeconds() + 59) / 60
        return if (minutes > 0) "$minutes min" else ""
    }
    val base = SetupPlanReveals.setsLabelOf(exercise) ?: return ""
    val first = exercise.sets.firstOrNull { !it.isEmptySlot }
    val effort = first?.targetRIR?.let { "RIR $it" }
        ?: first?.targetRPE?.let { rpe -> "RPE " + (if (rpe % 1.0 == 0.0) rpe.toInt().toString() else rpe.toString().replace('.', ',')) }
    return if (effort != null) "$base · $effort" else base
}

/**
 * Los ejercicios principales de la semana enseñada: el primer ejercicio de fuerza de cada sesión, en el orden de la semana, sin
 * repetir y como mucho seis; la línea pequeña dice la sesión y la prescripción.
 */
private fun mainExercisesOf(sessions: List<Session>, imageOf: (Exercise) -> Int?): List<PlanFichaExercise> =
    sessions.mapNotNull { session ->
        session.allExercises()
            .firstOrNull { it.cardioDetails == null && it.name.isNotBlank() && it.sets.any { set -> !set.isEmptySlot } }
            ?.let { session to it }
    }
        .distinctBy { (_, exercise) -> exercise.name.trim().lowercase() }
        .take(MAX_MAIN_EXERCISES)
        .map { (session, exercise) ->
            PlanFichaExercise(
                name = exercise.name,
                detail = listOf(session.name.trim(), exercisePrescription(exercise)).filter { it.isNotBlank() }.joinToString(" · "),
                imageResId = imageOf(exercise),
            )
        }
