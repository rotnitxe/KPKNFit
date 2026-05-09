package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.auge.SessionMuscleFilter
import java.util.UUID

enum class SplitTemporalScope { CURRENT_WEEK, SELECTED_WEEKS, CURRENT_BLOCK, WHOLE_PROGRAM }
enum class AdvancedSplitMode { GLOBAL, PER_BLOCK }
enum class SessionMigrationMode { MIGRATE, CLEAN }
enum class StartDaySessionMode { KEEP_DAYS, KEEP_SPLIT_ORDER }
enum class StartDayTemporalScope { ALL_WEEKS, FROM_SELECTED_WEEK }

data class SplitBlockOption(
    val id: String,
    val name: String,
    val macroName: String,
)

data class SplitWeekOption(
    val id: String,
    val name: String,
    val blockId: String,
    val blockName: String,
    val macroName: String,
    val sessions: List<Session>,
)

data class SplitPatternDay(
    val label: String,
    val dayOfWeek: Int,
)

data class SplitImpactSummary(
    val affectedWeeks: Int,
    val affectedSessions: Int,
    val willReplaceSessions: Boolean,
) {
    val isLargeDestructiveChange: Boolean
        get() = willReplaceSessions && (affectedWeeks > 1 || affectedSessions > 4)
}

data class SplitApplicationRequest(
    val program: Program,
    val selectedSplit: SplitTemplate,
    val selectedBlockId: String?,
    val selectedWeekId: String?,
    val startDay: Int,
    val temporalScope: SplitTemporalScope,
    val selectedWeekIds: Set<String> = emptySet(),
    val advancedMode: AdvancedSplitMode = AdvancedSplitMode.GLOBAL,
    val migrationMode: SessionMigrationMode = SessionMigrationMode.MIGRATE,
    val perBlockSelections: Map<String, String> = emptyMap(),
)

object SplitApplicationEngine {

    fun buildBlockOptions(program: Program): List<SplitBlockOption> {
        return program.macrocycles.flatMap { macro ->
            macro.blocks.map { block -> SplitBlockOption(block.id, block.name, macro.name) }
        }
    }

    fun buildWeekOptions(program: Program): List<SplitWeekOption> {
        return program.macrocycles.flatMap { macro ->
            macro.blocks.flatMap { block ->
                block.mesocycles.flatMap { meso ->
                    meso.weeks.map { week ->
                        SplitWeekOption(
                            id = week.id,
                            name = week.name,
                            blockId = block.id,
                            blockName = block.name,
                            macroName = macro.name,
                            sessions = week.sessions,
                        )
                    }
                }
            }
        }
    }

    fun impactSummary(request: SplitApplicationRequest): SplitImpactSummary {
        var affectedWeeks = 0
        var affectedSessions = 0
        request.program.forEachWeek { blockId, week ->
            if (shouldApplyToWeek(request, blockId, week.id)) {
                affectedWeeks++
                affectedSessions += week.sessions.size
            }
        }
        return SplitImpactSummary(
            affectedWeeks = affectedWeeks,
            affectedSessions = affectedSessions,
            willReplaceSessions = request.migrationMode == SessionMigrationMode.CLEAN && affectedSessions > 0,
        )
    }

    fun hasSessionsInTarget(request: SplitApplicationRequest): Boolean {
        return impactSummary(request).affectedSessions > 0
    }

    fun apply(request: SplitApplicationRequest): Program {
        val blockAssignments = if (request.advancedMode == AdvancedSplitMode.PER_BLOCK) request.perBlockSelections else emptyMap()
        val selectedSplit = request.selectedSplit

        return request.program.copy(
            startDay = request.startDay,
            selectedSplitId = if (request.advancedMode == AdvancedSplitMode.GLOBAL) selectedSplit.id else request.program.selectedSplitId,
            customSplitPattern = if (request.advancedMode == AdvancedSplitMode.GLOBAL) selectedSplit.pattern else request.program.customSplitPattern,
            customSplitName = if (selectedSplit.id == "custom") selectedSplit.name else request.program.customSplitName,
            customSplitDescription = if (selectedSplit.id == "custom") selectedSplit.description else request.program.customSplitDescription,
            blockSplitSelections = if (request.advancedMode == AdvancedSplitMode.PER_BLOCK) blockAssignments else emptyMap(),
            splitTrialSeen = false,
            macrocycles = request.program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        val blockSplit = if (request.advancedMode == AdvancedSplitMode.PER_BLOCK) {
                            SPLIT_TEMPLATES.firstOrNull { it.id == blockAssignments[block.id] } ?: selectedSplit
                        } else {
                            selectedSplit
                        }
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (!shouldApplyToWeek(request, block.id, week.id)) {
                                            week
                                        } else {
                                            week.copy(
                                                sessions = buildSessionsForSplit(
                                                    pattern = blockSplit.pattern,
                                                    sessionDescriptions = blockSplit.sessionDescriptions,
                                                    startDay = request.startDay,
                                                    existingSessions = week.sessions,
                                                    migrationMode = request.migrationMode,
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    fun buildSessionsForSplit(
        pattern: List<String>,
        sessionDescriptions: Map<String, String> = emptyMap(),
        startDay: Int,
        existingSessions: List<Session>,
        migrationMode: SessionMigrationMode,
    ): List<Session> {
        val trainingDays = patternToTrainingDays(pattern, startDay)
        if (trainingDays.isEmpty()) return emptyList()

        if (existingSessions.isEmpty() || migrationMode == SessionMigrationMode.CLEAN) {
            return normalizeMainSessions(
                trainingDays.map { day ->
                    Session(
                        id = UUID.randomUUID().toString(),
                        name = day.label,
                        description = splitSessionDescription(day.label, sessionDescriptions),
                        exercises = emptyList(),
                        parts = emptyList(),
                        dayOfWeek = day.dayOfWeek,
                        assignedDays = listOf(day.dayOfWeek),
                        scheduleLabel = day.label,
                        isMainSession = true,
                    )
                }
            )
        }

        val unassignedDays = trainingDays.toMutableList()
        val reassigned = existingSessions.map { session ->
            val target = bestTrainingDayForSession(session, unassignedDays.ifEmpty { trainingDays })
            unassignedDays.removeAll { it.dayOfWeek == target.dayOfWeek && it.label == target.label }
            session.copy(
                dayOfWeek = target.dayOfWeek,
                description = session.description?.takeIf { it.isNotBlank() } ?: splitSessionDescription(target.label, sessionDescriptions),
                assignedDays = listOf(target.dayOfWeek),
                scheduleLabel = target.label,
            )
        }.toMutableList()

        val coveredDays = reassigned.mapNotNull { it.dayOfWeek }.toSet()
        trainingDays.filterNot { it.dayOfWeek in coveredDays }.forEach { missingDay ->
            reassigned.add(
                Session(
                    id = UUID.randomUUID().toString(),
                    name = missingDay.label,
                    description = splitSessionDescription(missingDay.label, sessionDescriptions),
                    exercises = emptyList(),
                    parts = emptyList(),
                    dayOfWeek = missingDay.dayOfWeek,
                    assignedDays = listOf(missingDay.dayOfWeek),
                    scheduleLabel = missingDay.label,
                    isMainSession = false,
                )
            )
        }

        return normalizeMainSessions(reassigned)
    }

    fun copySessionsWithNewIds(sessions: List<Session>): List<Session> {
        return normalizeMainSessions(sessions.map { it.deepCopyWithNewIds() })
    }

    fun applyStartDayChange(
        program: Program,
        selectedWeekId: String?,
        newStartDay: Int,
        temporalScope: StartDayTemporalScope,
        sessionMode: StartDaySessionMode,
    ): Program {
        val oldStartDay = program.startDay ?: 1
        val targetIds = when (temporalScope) {
            StartDayTemporalScope.ALL_WEEKS -> buildWeekOptions(program).map { it.id }.toSet()
            StartDayTemporalScope.FROM_SELECTED_WEEK -> weekIdsFrom(program, selectedWeekId)
        }

        return program.copy(
            startDay = newStartDay,
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id !in targetIds || sessionMode == StartDaySessionMode.KEEP_DAYS) {
                                            week
                                        } else {
                                            week.copy(
                                                sessions = normalizeMainSessions(
                                                    week.sessions.map { session ->
                                                        val shiftedDay = shiftDayFromStart(session.dayOfWeek ?: oldStartDay, oldStartDay, newStartDay)
                                                        session.copy(dayOfWeek = shiftedDay, assignedDays = listOf(shiftedDay))
                                                    }
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    fun patternToTrainingDays(pattern: List<String>, startDay: Int): List<SplitPatternDay> {
        val orderedDays = listOf(1, 2, 3, 4, 5, 6, 7)
        val offset = (startDay - 1).coerceIn(0, 6)
        val rotated = orderedDays.drop(offset) + orderedDays.take(offset)

        return pattern.mapIndexedNotNull { index, label ->
            if (label.equals("Descanso", ignoreCase = true)) null
            else SplitPatternDay(label = label, dayOfWeek = rotated[index % rotated.size])
        }
    }

    fun normalizeMainSessions(sessions: List<Session>): List<Session> {
        val mainByDay = mutableMapOf<Int, String>()
        val fallbackByDay = mutableMapOf<Int, String>()

        sessions.forEach { session ->
            val day = session.dayOfWeek ?: 1
            fallbackByDay.putIfAbsent(day, session.id)
            if (session.isMainSession && day !in mainByDay) mainByDay[day] = session.id
        }

        fallbackByDay.forEach { (day, sessionId) -> mainByDay.putIfAbsent(day, sessionId) }
        return sessions.map { session ->
            val day = session.dayOfWeek ?: 1
            session.copy(isMainSession = mainByDay[day] == session.id)
        }
    }

    private fun shouldApplyToWeek(request: SplitApplicationRequest, blockId: String, weekId: String): Boolean {
        return when (request.temporalScope) {
            SplitTemporalScope.CURRENT_WEEK -> weekId == request.selectedWeekId
            SplitTemporalScope.SELECTED_WEEKS -> weekId in request.selectedWeekIds
            SplitTemporalScope.CURRENT_BLOCK -> blockId == request.selectedBlockId
            SplitTemporalScope.WHOLE_PROGRAM -> true
        }
    }

    private inline fun Program.forEachWeek(block: (blockId: String, week: ProgramWeek) -> Unit) {
        macrocycles.forEach { macro ->
            macro.blocks.forEach { programBlock ->
                programBlock.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week -> block(programBlock.id, week) }
                }
            }
        }
    }

    private fun bestTrainingDayForSession(
        session: Session,
        trainingDays: List<SplitPatternDay>,
    ): SplitPatternDay {
        val sessionMuscles = collectSessionMuscles(session)
        val sessionText = buildString {
            append(session.name.lowercase())
            append(' ')
            append(session.description.orEmpty().lowercase())
        }
        if (sessionMuscles.isEmpty() && sessionText.isBlank()) {
            return trainingDays.first()
        }

        val scored = trainingDays.map { day -> day to scoreSplitDay(day.label, sessionMuscles, sessionText) }
        val bestScore = scored.maxOfOrNull { it.second } ?: 0
        return if (bestScore <= 0) trainingDays.first()
        else scored.first { it.second == bestScore }.first
    }

    private fun collectSessionMuscles(session: Session): Set<String> {
        val muscles = linkedSetOf<String>()

        fun collectFromExercises(exercises: List<Exercise>) {
            exercises.forEach { exercise ->
                val info = exercise.exerciseDbId?.lowercase()?.let { EXERCISE_DATABASE_BY_ID[it] }
                SessionMuscleFilter.relevantMusclesFor(info).forEach { involved ->
                    muscles.add(
                        normalizeCanonicalMuscle(
                            VolumeCalculator.normalizeMuscleGroup(
                                specificMuscle = involved.muscle,
                                emphasis = involved.emphasis,
                            )
                        )
                    )
                }
            }
        }

        collectFromExercises(session.exercises)
        session.parts.forEach { part: SessionPart -> collectFromExercises(part.exercises) }
        listOfNotNull(session.sessionB, session.sessionC, session.sessionD).forEach { nested -> muscles.addAll(collectSessionMuscles(nested)) }
        return muscles
    }

    private fun normalizeCanonicalMuscle(muscle: String): String {
        return when (muscle.lowercase()) {
            "cuadriceps", "cuádriceps" -> "Cuádriceps"
            "gluteos", "glúteos" -> "Glúteos"
            "biceps", "bíceps" -> "Bíceps"
            "triceps", "tríceps" -> "Tríceps"
            else -> muscle
        }
    }

    private fun scoreSplitDay(label: String, sessionMuscles: Set<String>, sessionText: String): Int {
        val keywords = splitKeywords(label)
        val textBonus = keywords.count { sessionText.contains(it.lowercase()) } * 3
        val muscleBonus = sessionMuscles.count { muscle ->
            keywords.any { keyword -> muscle.lowercase().contains(keyword.lowercase()) || keyword.lowercase().contains(muscle.lowercase()) }
        } * 4
        val genericBonus = when {
            label.contains("Torso", ignoreCase = true) && sessionMuscles.any { it in upperBodyKeywords } -> 5
            label.contains("Full", ignoreCase = true) && sessionMuscles.isNotEmpty() -> 4
            label.contains("Pierna", ignoreCase = true) && sessionMuscles.any { it in lowerBodyKeywords } -> 5
            else -> 0
        }
        return textBonus + muscleBonus + genericBonus
    }

    private fun splitKeywords(label: String): Set<String> {
        val lower = label.lowercase()
        val keywords = linkedSetOf<String>()
        if ("empuje" in lower || "push" in lower) keywords.addAll(listOf("Pectorales", "Tríceps", "Deltoides"))
        if ("tirón" in lower || "tiron" in lower || "pull" in lower || "tracción" in lower || "traccion" in lower) keywords.addAll(listOf("Dorsales", "Trapecio", "Bíceps", "Deltoides Posterior"))
        if ("pierna" in lower || "lower" in lower) keywords.addAll(listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas"))
        if ("torso" in lower || "upper" in lower) keywords.addAll(upperBodyKeywords)
        if ("full" in lower || "cuerpo completo" in lower || "sbd" in lower) keywords.addAll(upperBodyKeywords + lowerBodyKeywords)
        if ("pecho" in lower || "banca" in lower) keywords.add("Pectorales")
        if ("espalda" in lower) keywords.addAll(listOf("Dorsales", "Trapecio", "Erectores Espinales"))
        if ("hombro" in lower) keywords.addAll(listOf("Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior"))
        if ("brazo" in lower) keywords.addAll(listOf("Bíceps", "Tríceps"))
        if ("cuádriceps" in lower || "cuadriceps" in lower) keywords.add("Cuádriceps")
        if ("isquios" in lower || "femoral" in lower) keywords.add("Isquiosurales")
        if ("glúteo" in lower || "gluteo" in lower) keywords.add("Glúteos")
        if ("peso muerto" in lower || "deadlift" in lower) keywords.addAll(listOf("Isquiosurales", "Glúteos", "Erectores Espinales", "Trapecio"))
        if ("sentadilla" in lower || "squat" in lower) keywords.addAll(listOf("Cuádriceps", "Glúteos"))
        if (keywords.isEmpty()) keywords.add(label.replaceFirstChar { it.uppercase() })
        return keywords
    }

    private fun weekIdsFrom(program: Program, selectedWeekId: String?): Set<String> {
        if (selectedWeekId == null) return emptySet()
        val weeks = buildWeekOptions(program)
        val selectedIndex = weeks.indexOfFirst { it.id == selectedWeekId }
        if (selectedIndex < 0) return emptySet()
        return weeks.drop(selectedIndex).map { it.id }.toSet()
    }

    private fun splitSessionDescription(label: String, customDescriptions: Map<String, String>): String {
        customDescriptions[label]?.let { return it }
        val lower = label.lowercase()
        return when {
            lower.contains("descanso") -> "Día reservado para recuperación; no debería generar una sesión principal."
            lower.contains("empuje") || lower.contains("push") -> "Sesión enfocada en patrones de empuje: pecho, hombro anterior/lateral y tríceps."
            lower.contains("tirón") || lower.contains("tiron") || lower.contains("pull") -> "Sesión enfocada en tracción: espalda, deltoide posterior y bíceps."
            lower.contains("pierna") || lower.contains("lower") -> "Sesión de tren inferior: cuádriceps, isquiosurales, glúteos y pantorrillas según prioridad."
            lower.contains("torso") || lower.contains("upper") -> "Sesión de tren superior con empujes y tracciones balanceadas."
            lower.contains("cuerpo completo") || lower.contains("full body") -> "Sesión full body para distribuir volumen entre tren superior e inferior."
            lower.contains("pecho") && lower.contains("espalda") -> "Sesión antagonista para pecho y espalda, útil para alto volumen de torso."
            lower.contains("hombro") && lower.contains("brazo") -> "Sesión de especialización para deltoides, bíceps y tríceps."
            lower.contains("sentadilla") || lower.contains("squat") -> "Sesión con prioridad en sentadilla y accesorios compatibles."
            lower.contains("peso muerto") || lower.contains("deadlift") -> "Sesión con prioridad en peso muerto, cadena posterior y accesorios compatibles."
            lower.contains("banca") || lower.contains("bench") -> "Sesión con prioridad en press banca y musculatura de soporte."
            lower.contains("pesado") || lower.contains("max") -> "Día de mayor intensidad; mantén el volumen accesorio controlado."
            lower.contains("liviano") || lower.contains("recuperación") || lower.contains("recuperacion") -> "Día técnico o liviano para practicar patrones sin acumular demasiada fatiga."
            lower.contains("moderado") || lower.contains("volumen") -> "Día de volumen moderado para acumular trabajo sin llegar al máximo esfuerzo."
            lower.contains("accesorios") || lower.contains("hipertrofia") -> "Día accesorio para reforzar puntos débiles y completar volumen muscular."
            else -> "Sesión creada desde el split ${label}; ajusta ejercicios, volumen e intensidad según el objetivo de la semana."
        }
    }

    private fun shiftDayFromStart(day: Int, oldStartDay: Int, newStartDay: Int): Int {
        val oldOrder = rotateDays(oldStartDay)
        val newOrder = rotateDays(newStartDay)
        val index = oldOrder.indexOf(day).takeIf { it >= 0 } ?: 0
        return newOrder[index]
    }

    private fun rotateDays(startDay: Int): List<Int> {
        val safe = startDay.coerceIn(1, 7)
        return (safe..7).toList() + (1 until safe).toList()
    }

    private fun Session.deepCopyWithNewIds(): Session {
        return copy(
            id = UUID.randomUUID().toString(),
            parts = parts.map { part ->
                part.copy(
                    id = UUID.randomUUID().toString(),
                    exercises = part.exercises.map { it.deepCopyWithNewIds() },
                )
            },
            exercises = exercises.map { it.deepCopyWithNewIds() },
            sessionB = sessionB?.deepCopyWithNewIds(),
            sessionC = sessionC?.deepCopyWithNewIds(),
            sessionD = sessionD?.deepCopyWithNewIds(),
        )
    }

    private fun Exercise.deepCopyWithNewIds(): Exercise {
        return copy(
            id = UUID.randomUUID().toString(),
            warmupSets = warmupSets.map { it.copy(id = UUID.randomUUID().toString()) },
            sets = sets.map { it.copy(id = UUID.randomUUID().toString()) },
        )
    }

    private val upperBodyKeywords = setOf("Pectorales", "Dorsales", "Trapecio", "Bíceps", "Tríceps", "Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior")
    private val lowerBodyKeywords = setOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores")
}
