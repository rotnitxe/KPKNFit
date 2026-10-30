package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.training.SplitTemporalScope

/** Programas de varias semanas y peticiones para las pruebas del motor de migración de reparto. */
internal object MigrationTestSupport {

    val TORSO_A = listOf(CatalogIds.BP, CatalogIds.ROW, CatalogIds.OHP, CatalogIds.LAT, CatalogIds.CURL, CatalogIds.PUSHDOWN)
    val PIERNA_A = listOf(CatalogIds.SQ_HIGH, CatalogIds.RDL, CatalogIds.LEG_EXT, CatalogIds.CALF)
    val TORSO_B = listOf(CatalogIds.BP_INC_DB, CatalogIds.CSR, CatalogIds.LATERAL, CatalogIds.PULLOVER, CatalogIds.HAMMER, CatalogIds.OH_TRI)
    val PIERNA_B = listOf(CatalogIds.DL, CatalogIds.PRESS_LEG, CatalogIds.CURL_L, CatalogIds.CALF)

    fun split(id: String) = SPLIT_TEMPLATES.first { it.id == id }

    /** Una semana de torso y pierna ×2 (lunes, martes, jueves, viernes) con ids propios por semana. */
    fun ulWeek(
        weekId: String,
        kind: WeekExecutionKind = WeekExecutionKind.TRAINING,
        swapped: Map<String, String> = emptyMap(),
        extraParts: Map<String, SessionPart> = emptyMap(),
    ): ProgramWeek {
        fun configs(list: List<String>) = list.map { swapped[it] ?: it }
        fun session(key: String, name: String, day: Int, list: List<String>): Session =
            SplitTestSupport.session("$weekId-$key", name, day, configs(list), extraParts = listOfNotNull(extraParts[key]))
        return SplitTestSupport.week(
            weekId,
            listOf(
                session("u1", "Torso A", 1, TORSO_A),
                session("l1", "Pierna A", 2, PIERNA_A),
                session("u2", "Torso B", 4, TORSO_B),
                session("l2", "Pierna B", 5, PIERNA_B),
            ),
            kind = kind,
        )
    }

    /** La misma semana como descarga: la mitad de series en cada ejercicio (redondeando hacia arriba). */
    fun asDeload(week: ProgramWeek): ProgramWeek = week.copy(
        executionKind = WeekExecutionKind.DELOAD,
        sessions = week.sessions.map { session ->
            fun half(exercise: Exercise) = exercise.copy(sets = exercise.sets.take((exercise.sets.size + 1) / 2))
            session.copy(parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::half)) }, exercises = session.exercises.map(::half))
        },
    )

    fun program(vararg weeks: ProgramWeek, edit: (Program) -> Program = { it }): Program =
        SplitTestSupport.program(weeks.toList(), name = "Torso y pierna", edit = edit)

    fun cardioPart(id: String = "cardio"): SessionPart = SessionPart(
        id = "$id#part",
        name = "Cardio",
        exercises = listOf(
            Exercise(
                id = "$id-1",
                name = "Cinta",
                cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200),
            ),
        ),
        isCardioGroup = true,
        targetDurationMinutes = 20,
    )

    fun request(
        program: Program,
        splitId: String,
        weekdays: List<Int>,
        baseWeekId: String = ProgramWeeksOf.first(program),
        scope: List<String> = listOf(baseWeekId),
        completed: Set<String> = emptySet(),
        today: Int? = null,
        placements: Map<String, Int> = emptyMap(),
        targetMinutes: Int? = null,
        fitToTime: Boolean = false,
        scopeKind: SplitTemporalScope = SplitTemporalScope.SELECTED_WEEKS,
    ): MigrationRequest = MigrationRequest(
        program = program,
        split = split(splitId),
        weekdays = weekdays,
        resolver = SplitTestSupport.resolver,
        baseWeekId = baseWeekId,
        scopeWeekIds = scope,
        scope = scopeKind,
        completedSessionIds = completed,
        todayWeekday = today,
        placements = placements,
        targetMinutes = targetMinutes,
        fitToTime = fitToTime,
    )

    fun ok(plan: MigrationPlan): MigrationPlan.Ok = plan as? MigrationPlan.Ok
        ?: error("se esperaba un plan y salió: $plan")

    fun sessionsOfWeek(program: Program, weekId: String): List<Session> =
        SplitTestSupport.weeksOf(program).first { it.id == weekId }.sessions

    private object ProgramWeeksOf {
        fun first(program: Program): String = SplitTestSupport.weeksOf(program).first().id
    }
}
