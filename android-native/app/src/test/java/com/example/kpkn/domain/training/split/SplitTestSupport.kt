package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.training.CatalogCompositionTestSupport

/**
 * Programas sintéticos con ejercicios REALES del catálogo v2 (ids de `CatalogIds`) y la estructura que arma el
 * materializador de planes: sesiones con partes «Principal» y «Accesorios», un ejercicio principal con aproximación.
 */
internal object SplitTestSupport {

    val catalog: ExerciseCatalogV2 get() = CatalogCompositionTestSupport.catalog

    val resolver: ExerciseTraitResolver by lazy { CatalogExerciseTraitResolver(catalog) }

    private val displayNames: Map<String, String> by lazy {
        catalog.families
            .flatMap { family -> family.definitions }
            .flatMap { definition -> definition.configurations.map { it.id to definition.canonicalName } }
            .toMap()
    }

    // ─── Grupos de ejercicios de uso común ────────────────────────────────────────────────────────

    val PUSH = listOf(CatalogIds.BP, CatalogIds.BP_INC_DB, CatalogIds.OHP, CatalogIds.LATERAL, CatalogIds.PUSHDOWN, CatalogIds.OH_TRI)
    val PULL = listOf(CatalogIds.ROW, CatalogIds.LAT, CatalogIds.CSR, CatalogIds.FACE, CatalogIds.CURL, CatalogIds.HAMMER)
    val LEGS = listOf(CatalogIds.SQ_HIGH, CatalogIds.RDL, CatalogIds.PRESS_LEG, CatalogIds.CURL_L, CatalogIds.LEG_EXT, CatalogIds.CALF)

    fun nameOf(configurationId: String): String = displayNames.getValue(configurationId)

    fun exercise(
        id: String,
        configurationId: String,
        sets: Int = 3,
        reps: Int = 10,
        approach: Boolean = false,
        supersetId: String? = null,
    ): Exercise = Exercise(
        id = id,
        name = nameOf(configurationId),
        exerciseDbId = configurationId,
        exerciseId = configurationId,
        catalogConfigurationId = configurationId,
        sets = List(sets) { index ->
            ExerciseSet(
                id = "$id-s$index",
                targetReps = reps,
                targetRepsRange = RepRange(reps, reps),
                targetRIR = 2,
                intensityMode = IntensityMode.RIR,
            )
        },
        warmupSets = if (approach) {
            listOf(
                WarmupSetDefinition(id = "$id-w0", percentageOfWorkingWeight = 50.0, targetReps = 5, restBetween = 45),
                WarmupSetDefinition(id = "$id-w1", percentageOfWorkingWeight = 75.0, targetReps = 3, restBetween = 60),
            )
        } else {
            emptyList()
        },
        restTime = 90,
        supersetId = supersetId,
        supersetGroupRef = supersetId,
    )

    /** Una sesión con su ejercicio principal (con aproximación) en la parte «Principal» y el resto en «Accesorios». */
    fun session(
        id: String,
        name: String,
        day: Int,
        configurations: List<String>,
        main: Boolean = true,
        supersets: Map<Int, String> = emptyMap(),
        warmup: List<WarmupExercise> = emptyList(),
        extraParts: List<SessionPart> = emptyList(),
        cardioFirst: Boolean = false,
        usesParts: Boolean = true,
    ): Session {
        val exercises = configurations.mapIndexed { index, configuration ->
            exercise(
                id = "$id-e$index",
                configurationId = configuration,
                sets = if (index == 0) 4 else 3,
                reps = if (index == 0) 6 else 10,
                approach = index == 0,
                supersetId = supersets[index],
            )
        }
        val groups = supersets.values.distinct().map { groupId ->
            SupersetGroup(
                id = groupId,
                exerciseOrder = exercises.filter { it.supersetGroupRef == groupId }.map { it.id },
                restBetweenExercises = 30,
                restAfterSuperset = 90,
            )
        }
        val strengthParts = if (usesParts && exercises.isNotEmpty()) {
            listOfNotNull(
                SessionPart(id = "$id#part:0", name = "Principal", exercises = exercises.take(1)),
                exercises.drop(1).takeIf { it.isNotEmpty() }?.let { SessionPart(id = "$id#part:1", name = "Accesorios", exercises = it) },
            )
        } else {
            emptyList()
        }
        return Session(
            id = id,
            name = name,
            exercises = if (usesParts) emptyList() else exercises,
            warmup = warmup,
            parts = strengthParts + extraParts,
            dayOfWeek = day,
            assignedDays = listOf(day),
            scheduleLabel = name,
            isMainSession = main,
            supersetGroups = groups,
            cardioFirst = cardioFirst,
        )
    }

    fun week(
        id: String,
        sessions: List<Session>,
        kind: WeekExecutionKind = WeekExecutionKind.TRAINING,
        progressionIndex: Int? = null,
    ): ProgramWeek = ProgramWeek(
        id = id,
        name = "Semana $id",
        sessions = sessions,
        executionKind = kind,
        progressionIndex = progressionIndex,
    )

    fun program(
        weeks: List<ProgramWeek>,
        id: String = "prog",
        name: String = "Plan de prueba",
        edit: (Program) -> Program = { it },
    ): Program {
        val days = weeks.firstOrNull()?.sessions?.mapNotNull { it.dayOfWeek }?.toSet().orEmpty()
        return edit(
            Program(
                id = id,
                name = name,
                structure = ProgramStructure.SIMPLE,
                startDay = 1,
                schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = days),
                macrocycles = listOf(
                    Macrocycle(
                        id = "$id-macro",
                        name = "Macrociclo",
                        blocks = listOf(
                            Block(
                                id = "$id-block",
                                name = "Bloque",
                                mesocycles = listOf(Mesocycle(id = "$id-meso", name = "Mesociclo", weeks = weeks)),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    fun weeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    // ─── Programas de referencia ──────────────────────────────────────────────────────────────────

    /** Empuje / tirón / pierna en lunes, miércoles y viernes: 18 ejercicios. */
    fun ppl(edit: (Program) -> Program = { it }): Program = program(
        listOf(
            week(
                "w1",
                listOf(
                    session("push", "Empuje", 1, PUSH),
                    session("pull", "Tirón", 3, PULL),
                    session("legs", "Pierna", 5, LEGS),
                ),
            ),
        ),
        name = "PPL",
        edit = edit,
    )

    /** Torso y pierna ×2: cuatro sesiones. */
    fun upperLower4(): Program = program(
        listOf(
            week(
                "w1",
                listOf(
                    session("u1", "Torso A", 1, listOf(CatalogIds.BP, CatalogIds.ROW, CatalogIds.OHP, CatalogIds.LAT, CatalogIds.CURL, CatalogIds.PUSHDOWN)),
                    session("l1", "Pierna A", 2, listOf(CatalogIds.SQ_HIGH, CatalogIds.RDL, CatalogIds.LEG_EXT, CatalogIds.CALF)),
                    session("u2", "Torso B", 4, listOf(CatalogIds.BP_INC_DB, CatalogIds.CSR, CatalogIds.LATERAL, CatalogIds.PULLOVER, CatalogIds.HAMMER, CatalogIds.OH_TRI)),
                    session("l2", "Pierna B", 5, listOf(CatalogIds.DL, CatalogIds.PRESS_LEG, CatalogIds.CURL_L, CatalogIds.CALF)),
                ),
            ),
        ),
        name = "Torso y pierna",
    )

    /** Cuerpo completo ×3: seis ejercicios por sesión. */
    fun fullBody3(): Program = program(
        listOf(
            week(
                "w1",
                listOf(
                    session("a", "Cuerpo completo A", 1, listOf(CatalogIds.SQ_HIGH, CatalogIds.BP, CatalogIds.ROW, CatalogIds.CURL_L, CatalogIds.LATERAL, CatalogIds.CURL)),
                    session("b", "Cuerpo completo B", 3, listOf(CatalogIds.RDL, CatalogIds.OHP, CatalogIds.LAT, CatalogIds.LEG_EXT, CatalogIds.PUSHDOWN, CatalogIds.FACE)),
                    session("c", "Cuerpo completo C", 5, listOf(CatalogIds.PRESS_LEG, CatalogIds.BP_INC_DB, CatalogIds.CSR, CatalogIds.CALF, CatalogIds.OH_TRI, CatalogIds.HAMMER)),
                ),
            ),
        ),
        name = "Cuerpo completo",
    )

    /** Dos sesiones de cuerpo completo (12 ejercicios). */
    fun fullBody2(): Program = program(
        listOf(
            week(
                "w1",
                listOf(
                    session("a", "Cuerpo completo A", 2, listOf(CatalogIds.SQ_HIGH, CatalogIds.BP, CatalogIds.ROW, CatalogIds.CURL_L, CatalogIds.LATERAL, CatalogIds.CURL)),
                    session("b", "Cuerpo completo B", 5, listOf(CatalogIds.RDL, CatalogIds.OHP, CatalogIds.LAT, CatalogIds.LEG_EXT, CatalogIds.PUSHDOWN, CatalogIds.CALF)),
                ),
            ),
        ),
        name = "Cuerpo completo 2",
    )

    /** Un grupo por día: pecho, espalda, pierna, hombros y brazos. */
    fun broSplit5(): Program = program(
        listOf(
            week(
                "w1",
                listOf(
                    session("chest", "Pecho", 1, listOf(CatalogIds.BP, CatalogIds.BP_INC_DB, CatalogIds.FLY, CatalogIds.DIPS)),
                    session("back", "Espalda", 2, listOf(CatalogIds.ROW, CatalogIds.LAT, CatalogIds.CSR, CatalogIds.PULLOVER)),
                    session("legs", "Piernas", 3, LEGS),
                    session("shoulders", "Hombros", 5, listOf(CatalogIds.OHP, CatalogIds.LATERAL, CatalogIds.FACE, CatalogIds.SHRUG)),
                    session("arms", "Brazos", 6, listOf(CatalogIds.CURL, CatalogIds.PUSHDOWN, CatalogIds.HAMMER, CatalogIds.OH_TRI)),
                ),
            ),
        ),
        name = "Un grupo por día",
    )

    /** Las fuentes de referencia con nombre, para los barridos. */
    fun references(): List<Pair<String, Program>> = listOf(
        "PPL de 3 sesiones" to ppl(),
        "Torso y pierna de 4 sesiones" to upperLower4(),
        "Cuerpo completo de 3 sesiones" to fullBody3(),
        "Cuerpo completo de 2 sesiones" to fullBody2(),
        "Un grupo por día de 5 sesiones" to broSplit5(),
    )

    /** Días de entreno repartidos por la semana para [count] días, en orden (lunes primero). */
    fun spreadDays(count: Int): List<Int> = when (count) {
        1 -> listOf(3)
        2 -> listOf(2, 5)
        3 -> listOf(1, 3, 5)
        4 -> listOf(1, 2, 4, 5)
        5 -> listOf(1, 2, 3, 5, 6)
        6 -> listOf(1, 2, 3, 4, 5, 6)
        else -> (1..7).toList()
    }

    /** Todos los ejercicios (con sus partes) de las sesiones de [program], sin repetir ids. */
    fun exercisesOf(program: Program): List<Exercise> = sessionsOf(program).flatMap { it.allExercises() }
}
