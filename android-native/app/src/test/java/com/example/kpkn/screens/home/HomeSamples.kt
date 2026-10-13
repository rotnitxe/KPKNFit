package com.example.kpkn.screens.home

import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.LoadAdvisory
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryChannelSnapshot
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.onboarding.RingsChannelCoverage
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionLocation
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.screens.home.design.HomeMastheadState
import com.example.kpkn.screens.home.design.homeDateLine
import java.time.LocalDate
import kotlin.math.sin

/** Un caso de Home para la hoja de render: lo que pinta la cabecera y «Tu semana». */
internal data class HomeSample(
    val masthead: HomeMastheadState,
    val days: List<HomeWeekDay>,
    val selected: LocalDate,
)

/** Datos de ejemplo de Home para las pruebas y la hoja de render. Fechas fijas: martes 13 de octubre de 2026. */
internal object HomeSamples {

    /** Martes 13 de octubre de 2026: el «hoy» de todos los ejemplos. */
    val today: LocalDate = LocalDate.of(2026, 10, 13)

    fun session(id: String, name: String, dayOfWeek: Int, main: Boolean = false): Session =
        Session(id = id, name = name, dayOfWeek = dayOfWeek, isMainSession = main)

    /** Una sesión de la semana [weekId]; «hoy» se deduce de su día ISO frente a [today]. */
    fun item(
        program: Program,
        weekId: String,
        session: Session,
        today: LocalDate = this.today,
        done: Boolean = false,
        ongoing: Boolean = false,
    ): TodaySessionItem {
        val day = session.dayOfWeek ?: 1
        return TodaySessionItem(
            session = session,
            program = program,
            location = SessionLocation(macroIndex = 0, mesoIndex = 0, weekId = weekId),
            isCompleted = done,
            dayOfWeek = day,
            isOngoing = ongoing,
            isToday = day == today.dayOfWeek.value,
        )
    }

    /** Sesiones de cada semana del programa de 12: lunes (principal), martes y jueves. */
    private val weekSessions: List<Session> = listOf(
        session("torso-a", "Torso A", 1, main = true),
        session("pierna-a", "Pierna A", 2),
        session("torso-b", "Torso B", 4),
    )

    /** Programa de 12 semanas (la semana 3 es la actual) con el objetivo [goal] en su bloque. */
    fun twelveWeekProgram(
        name: String = "Camino al pico",
        goal: BlockGoal? = BlockGoal.ACCUMULATION,
    ): Program = Program(
        id = "p-12",
        name = name,
        structure = ProgramStructure.COMPLEX,
        startDay = 1,
        macrocycles = listOf(
            Macrocycle(
                id = "mac-1",
                name = "Macrociclo",
                blocks = listOf(
                    Block(
                        id = "blk-1",
                        name = "Bloque",
                        goal = goal,
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-1",
                                name = "Mesociclo",
                                weeks = (1..12).map { n ->
                                    ProgramWeek(id = "w$n", name = "Semana $n", sessions = weekSessions)
                                },
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    /** Sesiones de la semana 3: lunes hecho, martes (hoy) pendiente y jueves pendiente. */
    fun twelveWeekItems(
        program: Program = twelveWeekProgram(),
        today: LocalDate = this.today,
    ): List<TodaySessionItem> = weekSessions.map { s ->
        item(program, "w3", s, today = today, done = s.dayOfWeek == 1)
    }

    /** Ciclo simple de una sola semana, con su número de ciclo en 5. */
    fun simpleCyclicProgram(): Program = Program(
        id = "p-cyc",
        name = "Cuerpo completo",
        structure = ProgramStructure.SIMPLE,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        startDay = 1,
        runState = ProgramRunState(runId = "run-cyc", cycleNumber = 5),
        macrocycles = listOf(
            Macrocycle(
                id = "mac-c",
                name = "Ciclo",
                blocks = listOf(
                    Block(
                        id = "blk-c",
                        name = "Ciclo",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-c",
                                name = "Ciclo",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "cyc-w1",
                                        name = "Semana",
                                        sessions = listOf(session("cuerpo", "Cuerpo completo", 2)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    /** La única sesión del ciclo: martes (hoy), pendiente. */
    fun simpleCyclicItems(
        program: Program = simpleCyclicProgram(),
        today: LocalDate = this.today,
    ): List<TodaySessionItem> = listOf(item(program, "cyc-w1", session("cuerpo", "Cuerpo completo", 2), today = today))

    /** La muestra completa: cabecera (con o sin programa) y «Tu semana» si hay sesiones. */
    fun sample(
        program: Program?,
        items: List<TodaySessionItem>,
        name: String = "Alex",
        today: LocalDate = this.today,
    ): HomeSample {
        val days = if (program == null || items.isEmpty()) {
            emptyList()
        } else {
            HomeWeekResolver.week(items, program.startDay, today)
        }
        return HomeSample(
            masthead = HomeMastheadState(
                dateLine = homeDateLine(today),
                greeting = "Buenos días",
                name = name,
                initial = name.take(1).uppercase(),
                program = HomeWeekResolver.programLine(program, items, today),
            ),
            days = days,
            selected = today,
        )
    }

    /** Los cuatro casos de la hoja de render: programa de 12 semanas, ciclo simple, sin programa y nombre largo. */
    fun sheetCases(): List<Pair<String, HomeSample>> {
        val longProgram = twelveWeekProgram(name = "Powerlifting a medida con un nombre larguísimo que no cabe en una línea")
        return listOf(
            "prog12" to sample(twelveWeekProgram(), twelveWeekItems()),
            "cyclic" to sample(simpleCyclicProgram(), simpleCyclicItems()),
            "none" to sample(null, emptyList()),
            "long" to sample(longProgram, twelveWeekItems(longProgram), name = "Maximiliano Alejandro"),
        )
    }

    /** Nutrición con dos comidas registradas (desayuno y almuerzo): 1.240 de 2.300 kcal, con los macros a medio camino. */
    fun nutritionTwoMeals(): HomeNutritionState = HomeNutritionState(
        kcal = 1_240,
        kcalGoal = 2_300,
        protein = HomeMacroToday(grams = 92, goalGrams = 160),
        carbs = HomeMacroToday(grams = 130, goalGrams = 250),
        fat = HomeMacroToday(grams = 38, goalGrams = 70),
        mealKcal = mapOf(MealType.BREAKFAST to 420, MealType.LUNCH to 820),
    )

    /** Nutrición sin meta: lo comido, «sin objetivo» y los macros «sin meta». */
    fun nutritionNoGoal(): HomeNutritionState = HomeNutritionState(
        kcal = 980,
        kcalGoal = null,
        protein = HomeMacroToday(grams = 74),
        carbs = HomeMacroToday(grams = 96),
        fat = HomeMacroToday(grams = 33),
        mealKcal = mapOf(MealType.BREAKFAST to 380, MealType.LUNCH to 420, MealType.SNACK to 180),
    )

    /** Nutrición por encima de la meta: 2.450 kcal frente a 2.300, con 150 de más. */
    fun nutritionOverGoal(): HomeNutritionState = HomeNutritionState(
        kcal = 2_450,
        kcalGoal = 2_300,
        protein = HomeMacroToday(grams = 168, goalGrams = 160),
        carbs = HomeMacroToday(grams = 262, goalGrams = 250),
        fat = HomeMacroToday(grams = 74, goalGrams = 70),
        mealKcal = mapOf(
            MealType.BREAKFAST to 500,
            MealType.LUNCH to 900,
            MealType.SNACK to 250,
            MealType.DINNER to 800,
        ),
    )

    /** Días (hace n días) con pesaje en la tendencia de ejemplo: 20 días entre hace 30 días y hoy. */
    private val trendDaysAgo: List<Int> = listOf(30, 28, 27, 25, 23, 22, 20, 18, 17, 15, 13, 12, 10, 9, 7, 6, 4, 3, 1, 0)

    /** Tendencia de ejemplo: de 79,6 a 78,4 kg, con un leve vaivén en medio y los extremos exactos. */
    private fun bodyTrend(): List<HomeBodyPoint> = trendDaysAgo.map { ago ->
        val progress = ago / 30.0
        val wobble = 0.12 * sin(ago * 1.7) * (ago * (30 - ago)) / 225.0
        HomeBodyPoint(today.minusDays(ago.toLong()), 78.4 + 1.2 * progress + wobble)
    }

    /** Cuerpo con tendencia y objetivo de 72 kg: 78,4 kg, −1,2 kg en 30 días y, con altura real, IMC y FFMI. */
    fun bodyWithTrendAndGoal(): HomeBodyState {
        val trend = bodyTrend()
        return HomeBodyState(
            weightKg = trend.last().kg,
            weightChange30d = trend.last().kg - trend.first().kg,
            trend = trend,
            windowEnd = today,
            goalKg = 72.0,
            goalMonth = "abril",
            changeTowardGoal = true,
            bodyFatPct = 18.2,
            musclePct = 40.1,
            bmi = 24.2,
            ffmi = 19.8,
        )
    }

    /** Cuerpo sin pesajes: el estado vacío, con la invitación a registrar. */
    fun bodyNoWeights(): HomeBodyState = HomeBodyState()

    /** Cuerpo sin altura real: hay peso y tendencia, pero no IMC ni FFMI. */
    fun bodyWithoutHeight(): HomeBodyState = bodyWithTrendAndGoal().copy(bmi = null, ffmi = null)

    /**
     * Un snapshot de RINGS de ejemplo: la puntuación de cada canal (null = sin datos) y su procedencia (por defecto, historial
     * real). Con las bandas, las horas, el rango habitual (si [baselines]) y las sesiones de cada canal. Entre los músculos hay
     * uno sin sesiones: su 100 % es el valor por defecto del motor y no debe salir.
     */
    fun ringsSample(
        muscular: Int? = 64,
        energy: Int? = 72,
        structure: Int? = 80,
        sources: Map<RecoveryChannelId, RingsCoverageSource> = emptyMap(),
        baselines: Boolean = true,
        advisory: Boolean = false,
    ): AugeSnapshot {
        fun coverage(channel: RecoveryChannelId, score: Int?) = RingsChannelCoverage(
            channel = channel,
            source = if (score == null) RingsCoverageSource.NO_DATA else sources[channel] ?: RingsCoverageSource.REAL_HISTORY,
            uncertainty = 20,
            score = score,
        )
        fun channel(id: RecoveryChannelId, title: String, score: Int?, action: String, causes: List<String>) = RecoveryChannelSnapshot(
            id = id,
            title = title,
            shortTitle = title,
            score = score ?: 100,
            band = RecoveryBands.band(score ?: 100),
            description = "",
            action = action,
            causes = causes,
        )
        return AugeSnapshot(
            batteries = GlobalBatteries(muscular = muscular ?: 100, cnc = energy ?: 100, spinal = structure ?: 100),
            perMuscle = mapOf(
                "Pectorales" to MuscleRecoveryStatus("Pectorales", 58, 12, 20, 9, RecoveryStatus.RECOVERING),
                "Dorsales" to MuscleRecoveryStatus("Dorsales", 66, 6, 30, 12, RecoveryStatus.RECOVERING),
                "Cuádriceps" to MuscleRecoveryStatus("Cuádriceps", 81, 0, 40, 8, RecoveryStatus.OPTIMAL),
                "Isquiotibiales" to MuscleRecoveryStatus("Isquiotibiales", 74, 4, 26, 6, RecoveryStatus.OPTIMAL),
                "Trapecio" to MuscleRecoveryStatus("Trapecio", 90, 0, 60, 4, RecoveryStatus.FRESH),
                "Hombros" to MuscleRecoveryStatus("Hombros", 100, 0, -1, 0, RecoveryStatus.FRESH),
            ),
            dashboard = RecoveryDashboard(
                overallScore = 72,
                headline = "Buen estado para entrenar",
                summary = "",
                recommendation = "",
                confidenceLabel = "Media",
                channels = listOf(
                    channel(
                        RecoveryChannelId.MUSCULAR,
                        "Músculos",
                        muscular,
                        "Volumen normal y buena ejecución.",
                        listOf("Sentadilla pesada ayer", "Volumen alto en piernas"),
                    ),
                    channel(
                        RecoveryChannelId.SYSTEM,
                        "Energía",
                        energy,
                        "Empuja normal, sin ir al límite.",
                        listOf("Dos sesiones intensas esta semana"),
                    ),
                    channel(
                        RecoveryChannelId.STRUCTURE,
                        "Columna",
                        structure,
                        "Carga estructural normal con buena técnica.",
                        listOf("Peso muerto el jueves"),
                    ),
                ),
                dataLabel = "Historial real",
            ),
            isLoading = false,
            advisories = if (advisory) {
                listOf(
                    LoadAdvisory(
                        id = "adv-piernas",
                        channel = RecoveryChannelId.MUSCULAR,
                        level = LoadAdvisoryLevel.WATCH,
                        title = "Carga alta en piernas",
                        body = "Dos sesiones pesadas seguidas. Reparte el volumen esta semana.",
                        weekKey = "2026-W42",
                        dismissible = true,
                    ),
                )
            } else {
                emptyList()
            },
            hoursToNormal = mapOf(RecoveryChannelId.MUSCULAR to 14, RecoveryChannelId.SYSTEM to 0),
            personalBaseline = if (baselines) {
                mapOf(RecoveryChannelId.MUSCULAR to 78..92, RecoveryChannelId.STRUCTURE to 70..86)
            } else {
                emptyMap()
            },
            sparkline = mapOf(
                RecoveryChannelId.MUSCULAR to listOf(62, 70, 66, 74, 64),
                RecoveryChannelId.SYSTEM to listOf(58, 64, 70, 72),
                RecoveryChannelId.STRUCTURE to listOf(80, 76, 79, 82, 80),
            ),
            coverage = RingsCoverage(
                muscular = coverage(RecoveryChannelId.MUSCULAR, muscular),
                system = coverage(RecoveryChannelId.SYSTEM, energy),
                structure = coverage(RecoveryChannelId.STRUCTURE, structure),
            ),
        )
    }
}
