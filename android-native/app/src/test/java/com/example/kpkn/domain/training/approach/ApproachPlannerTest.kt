package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.protocols.SlotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de la aproximación y la movilidad obligatorias con sesiones sintéticas (sin catálogo de ejercicios):
 * `infoOf` entrega a mano las articulaciones de cada ejercicio, como las que trae `jointInvolvement`.
 *
 * Regla del usuario: aproximación solo en ejercicios pesados; aproximación y movilidad obligatorias en el PRIMER
 * ejercicio; en el segundo y el tercero solo si son pesados y tocan una articulación que no cubrió lo anterior.
 */
class ApproachPlannerTest {

    private fun info(vararg joints: String, compound: Boolean = true, heavy: Boolean = true) =
        ApproachExerciseInfo(joints = linkedSetOf(*joints), isCompound = compound, canBeHeavy = heavy)

    private val upperPress = arrayOf(
        JointMobility.GLENOHUMERAL, JointMobility.ELBOW, JointMobility.SCAPULOTHORACIC, JointMobility.WRIST,
    )

    private val infos: Map<String, ApproachExerciseInfo> = mapOf(
        "squat" to info(JointMobility.KNEE, JointMobility.HIP, JointMobility.ANKLE, JointMobility.LUMBAR_SPINE),
        "front-squat" to info(JointMobility.KNEE, JointMobility.HIP, JointMobility.ANKLE, JointMobility.LUMBAR_SPINE),
        "bench" to info(*upperPress),
        "incline" to info(*upperPress),
        "ohp" to info(
            JointMobility.GLENOHUMERAL, JointMobility.SCAPULOTHORACIC, JointMobility.ELBOW, JointMobility.LUMBAR_SPINE,
        ),
        "rdl" to info(
            JointMobility.HIP, JointMobility.KNEE, JointMobility.LUMBAR_SPINE, JointMobility.SACROILIAC, JointMobility.WRIST,
        ),
        "deadlift" to info(
            JointMobility.HIP, JointMobility.KNEE, JointMobility.LUMBAR_SPINE, JointMobility.SACROILIAC,
            JointMobility.WRIST, JointMobility.ANKLE,
        ),
        "curl" to info(JointMobility.ELBOW, JointMobility.WRIST, compound = false, heavy = false),
        "lateral" to info(
            JointMobility.GLENOHUMERAL, JointMobility.SCAPULOTHORACIC, compound = false, heavy = false,
        ),
        "leg-curl" to info(
            JointMobility.KNEE, JointMobility.HIP, JointMobility.ANKLE, compound = false, heavy = false,
        ),
        "pull-up" to info(
            JointMobility.GLENOHUMERAL, JointMobility.SCAPULOTHORACIC, JointMobility.ELBOW, heavy = false,
        ),
        "no-joints" to ApproachExerciseInfo(joints = emptySet(), isCompound = true, canBeHeavy = true),
    )

    private val infoOf: (Exercise) -> ApproachExerciseInfo? = { infos[it.id] }

    // ─── Fábricas ───────────────────────────────────────────────────────────

    private fun workingSet(
        id: String,
        reps: Int? = null,
        range: IntRange? = null,
        rir: Int? = null,
        rpe: Double? = null,
        percent: Double? = null,
        weight: Double? = null,
    ) = ExerciseSet(
        id = id,
        targetReps = reps ?: range?.last,
        targetRepsRange = range?.let { RepRange(it.first, it.last) },
        targetRIR = rir,
        targetRPE = rpe,
        targetPercentageRM = percent,
        weight = weight,
    )

    private fun sets(count: Int = 3, make: (String) -> ExerciseSet): List<ExerciseSet> =
        List(count) { make("set-$it") }

    /** 3×5 con RIR 2: pesado por esfuerzo. */
    private fun heavySets(count: Int = 3) = sets(count) { workingSet(it, reps = 5, rir = 2) }

    /** 3×8–12 con RIR 2: volumen de accesorio, no pesado. */
    private fun accessorySets(count: Int = 3) = sets(count) { workingSet(it, range = 8..12, rir = 2) }

    private fun lift(
        id: String,
        sets: List<ExerciseSet> = heavySets(),
        role: SlotRole? = null,
        competition: Boolean = false,
        warmupSets: List<WarmupSetDefinition> = emptyList(),
        mobility: List<MobilitySeries> = emptyList(),
    ) = Exercise(
        id = id,
        name = id,
        sets = sets,
        slotRole = role,
        isCompetitionLift = competition,
        warmupSets = warmupSets,
        mobilitySeries = mobility,
    )

    private fun sessionOf(vararg exercises: Exercise) = Session(id = "s", name = "Día", exercises = exercises.toList())

    private fun planned(
        session: Session,
        level: ApproachLevel = ApproachLevel.INTERMEDIATE,
        heavyPercent: Double = 80.0,
    ): Session = ApproachPlanner.apply(session, ApproachOptions(level, heavyPercent), infoOf)

    private fun Session.exercise(id: String): Exercise = allExercises().first { it.id == id }

    private fun Exercise.rampPercents() = warmupSets.map { it.percentageOfWorkingWeight }

    private fun Exercise.mobilitySeconds() = mobilitySeries.sumOf { it.durationSeconds ?: 0 }

    // ─── Primer ejercicio ───────────────────────────────────────────────────

    @Test
    fun the_first_heavy_exercise_gets_joint_mobility_and_a_ramp_on_the_working_load() {
        val result = planned(sessionOf(lift("squat")))
        val squat = result.exercise("squat")

        assertEquals(listOf(40.0, 60.0, 80.0), squat.rampPercents())
        assertEquals(listOf(8, 5, 3), squat.warmupSets.map { it.targetReps })
        assertTrue("descansos cortos de 30 a 60 s", squat.warmupSets.all { (it.restBetween ?: 0) in 30..60 })
        assertTrue("ids únicos", squat.warmupSets.map { it.id }.toSet().size == squat.warmupSets.size)

        assertTrue("hay movilidad previa", squat.mobilitySeries.isNotEmpty())
        val covered = JointMobility.jointsCoveredBy(squat.mobilitySeries)
        assertTrue(
            "cubre cadera, rodilla y tobillo: $covered",
            covered.containsAll(listOf(JointMobility.HIP, JointMobility.KNEE, JointMobility.ANKLE)),
        )
        squat.mobilitySeries.forEach { series ->
            assertNotNull("${series.id} está en el catálogo de movilidad", MobilityExerciseCatalog.findById(series.id))
            assertEquals(MobilityUnit.SECONDS, series.unit)
            assertEquals(1, series.sets)
            assertTrue("30–45 s: ${series.durationSeconds}", (series.durationSeconds ?: 0) in 30..45)
        }
        assertTrue("movilidad ≤ 4 min", squat.mobilitySeconds() <= 240)
    }

    @Test
    fun the_first_light_exercise_gets_only_a_brief_mobility_of_its_main_joint() {
        val result = planned(sessionOf(lift("curl", accessorySets())))
        val curl = result.exercise("curl")

        assertTrue("sin series de aproximación", curl.warmupSets.isEmpty())
        assertEquals(1, curl.mobilitySeries.size)
        val series = curl.mobilitySeries.single()
        assertEquals("la del codo, su articulación principal", JointMobility.movementIdsFor(JointMobility.ELBOW).first(), series.id)
        assertTrue((series.durationSeconds ?: 0) in 30..40)
    }

    @Test
    fun a_heavy_second_exercise_after_a_light_first_one_is_still_approached() {
        val result = planned(sessionOf(lift("curl", accessorySets()), lift("squat")))
        // El primer ejercicio es ligero: solo movilidad breve. La sentadilla es la segunda, pesada y con articulaciones nuevas.
        assertTrue(result.exercise("curl").warmupSets.isEmpty())
        assertEquals(1, result.exercise("curl").mobilitySeries.size)
        assertTrue(result.exercise("squat").warmupSets.isNotEmpty())
        assertTrue(result.exercise("squat").mobilitySeries.isNotEmpty())
    }

    @Test
    fun a_session_without_heavy_exercises_only_gets_the_brief_mobility_of_the_first_one() {
        val result = planned(
            sessionOf(
                lift("curl", accessorySets()),
                lift("lateral", accessorySets()),
                lift("leg-curl", accessorySets()),
            ),
        )
        assertTrue(result.allExercises().all { it.warmupSets.isEmpty() })
        assertEquals(1, result.exercise("curl").mobilitySeries.size)
        assertTrue(result.exercise("lateral").mobilitySeries.isEmpty())
        assertTrue(result.exercise("leg-curl").mobilitySeries.isEmpty())
    }

    @Test
    fun a_loaded_compound_in_first_place_with_moderate_reps_gets_a_short_ramp() {
        // 3×8–12 con RIR 2 no es «cercano al 1RM», pero el primer ejercicio siempre se aproxima si carga peso.
        val result = planned(sessionOf(lift("squat", accessorySets(), role = SlotRole.T3_ACCESSORY)))
        val squat = result.exercise("squat")
        assertEquals(listOf(50.0, 75.0), squat.rampPercents())
        assertTrue(squat.mobilitySeries.isNotEmpty())
    }

    @Test
    fun a_compound_that_cannot_carry_heavy_load_in_first_place_is_light() {
        val result = planned(sessionOf(lift("pull-up", sets(3) { workingSet(it, reps = 6, rir = 1) })))
        val pullUp = result.exercise("pull-up")
        assertTrue("peso corporal fácil: sin aproximación", pullUp.warmupSets.isEmpty())
        assertEquals("solo movilidad breve", 1, pullUp.mobilitySeries.size)
    }

    // ─── Segundo y tercer ejercicio ─────────────────────────────────────────

    @Test
    fun a_second_heavy_exercise_with_a_new_joint_gets_a_shorter_ramp_and_only_its_new_mobility() {
        val result = planned(sessionOf(lift("bench"), lift("squat")))
        val bench = result.exercise("bench")
        val squat = result.exercise("squat")

        assertEquals(3, bench.warmupSets.size)
        assertEquals("rampa más corta: 1–2 pasos", listOf(50.0, 75.0), squat.rampPercents())
        assertTrue(squat.warmupSets.size in 1..2)
        assertTrue(squat.mobilitySeries.isNotEmpty())
        // La movilidad del sentadilla es de cadera/rodilla/tobillo: ninguna repite hombro, codo o muñeca ya cubiertos.
        val upperJoints = setOf(
            JointMobility.GLENOHUMERAL, JointMobility.ELBOW, JointMobility.SCAPULOTHORACIC, JointMobility.WRIST,
        )
        squat.mobilitySeries.forEach { series ->
            val joints = JointMobility.jointsOf(series.id)
            assertTrue("${series.id} no debería repetir articulaciones superiores", joints.none { it in upperJoints })
            assertTrue(
                "${series.id} cubre alguna articulación nueva",
                joints.any { it in setOf(JointMobility.KNEE, JointMobility.HIP, JointMobility.ANKLE, JointMobility.LUMBAR_SPINE) },
            )
        }
    }

    @Test
    fun a_second_heavy_exercise_whose_joints_are_already_covered_is_left_alone() {
        val original = sessionOf(lift("squat"), lift("front-squat"))
        val result = planned(original)
        val second = result.exercise("front-squat")
        assertTrue(second.warmupSets.isEmpty())
        assertTrue(second.mobilitySeries.isEmpty())
        assertEquals(original.exercise("front-squat"), second)
    }

    @Test
    fun the_joints_covered_by_the_first_exercise_include_those_of_its_ramp() {
        // Press banca cubre hombro, codo, escápula y muñeca: el press inclinado no necesita nada.
        val result = planned(sessionOf(lift("bench"), lift("incline")))
        assertTrue(result.exercise("incline").warmupSets.isEmpty())
        assertTrue(result.exercise("incline").mobilitySeries.isEmpty())
    }

    @Test
    fun a_third_heavy_exercise_with_a_new_joint_is_also_approached() {
        // Banca (hombro/codo/escápula/muñeca) y sentadilla (rodilla/cadera/tobillo/lumbar) no cubren la sacroilíaca del RDL.
        val result = planned(sessionOf(lift("bench"), lift("squat"), lift("rdl")))
        val rdl = result.exercise("rdl")
        assertTrue(rdl.warmupSets.isNotEmpty())
        assertTrue(rdl.mobilitySeries.isNotEmpty())
        val covered = JointMobility.jointsCoveredBy(rdl.mobilitySeries)
        assertTrue("movilidad de la articulación nueva: $covered", JointMobility.SACROILIAC in covered)
        assertTrue("solo lo nuevo, no la cadera", rdl.mobilitySeconds() <= 90)
    }

    @Test
    fun a_third_heavy_exercise_with_everything_covered_gets_nothing() {
        val result = planned(sessionOf(lift("bench"), lift("squat"), lift("ohp")))
        val ohp = result.exercise("ohp")
        assertTrue(ohp.warmupSets.isEmpty())
        assertTrue(ohp.mobilitySeries.isEmpty())
    }

    @Test
    fun from_the_fourth_exercise_on_nothing_is_added() {
        val result = planned(
            sessionOf(
                lift("curl", accessorySets()),
                lift("lateral", accessorySets()),
                lift("leg-curl", accessorySets()),
                lift("squat"),
            ),
        )
        val fourth = result.exercise("squat")
        assertTrue(fourth.warmupSets.isEmpty())
        assertTrue(fourth.mobilitySeries.isEmpty())
    }

    @Test
    fun cardio_and_empty_exercises_do_not_take_a_position() {
        val cardio = Exercise(id = "cardio", name = "Cardio", cardioDetails = CardioDetails(type = CardioType.WALK))
        val empty = Exercise(id = "empty", name = "Sin series")
        val result = planned(sessionOf(cardio, empty, lift("squat")))
        assertTrue("la sentadilla es el primer ejercicio de fuerza", result.exercise("squat").warmupSets.isNotEmpty())
        assertTrue(result.exercise("cardio").warmupSets.isEmpty() && result.exercise("cardio").mobilitySeries.isEmpty())
        assertTrue(result.exercise("empty").mobilitySeries.isEmpty())
    }

    // ─── Qué es «pesado» ────────────────────────────────────────────────────

    /** El sentadilla va segundo, tras un press banca, para que sus articulaciones sean nuevas: ¿recibe rampa? */
    private fun isApproachedAsSecond(
        sets: List<ExerciseSet>,
        role: SlotRole? = null,
        competition: Boolean = false,
        id: String = "squat",
        options: ApproachOptions = ApproachOptions(),
    ): Boolean {
        val session = sessionOf(lift("bench"), lift(id, sets, role, competition))
        val result = ApproachPlanner.apply(session, options, infoOf)
        return result.exercise(id).warmupSets.isNotEmpty()
    }

    @Test
    fun a_working_percentage_at_or_above_the_threshold_is_heavy() {
        assertTrue(isApproachedAsSecond(sets { workingSet(it, reps = 5, percent = 80.0) }))
        assertTrue(isApproachedAsSecond(sets { workingSet(it, reps = 3, percent = 92.5) }))
        assertFalse(isApproachedAsSecond(sets { workingSet(it, reps = 5, percent = 79.0) }))
        assertTrue(
            "el umbral es configurable",
            isApproachedAsSecond(sets { workingSet(it, reps = 8, percent = 72.0) }, options = ApproachOptions(heavyPercentOf1Rm = 70.0)),
        )
    }

    @Test
    fun low_reps_close_to_failure_are_heavy() {
        assertTrue(isApproachedAsSecond(sets { workingSet(it, reps = 5, rir = 2) }))
        assertTrue(isApproachedAsSecond(sets { workingSet(it, reps = 6, rir = 1) }))
        assertTrue(isApproachedAsSecond(sets { workingSet(it, reps = 5, rpe = 7.5) }))
        assertTrue("un rango 5–8 empieza en 5", isApproachedAsSecond(sets { workingSet(it, range = 5..8, rir = 2) }))
        assertFalse("7 repeticiones ya no es pesado sin otra señal", isApproachedAsSecond(sets { workingSet(it, reps = 7, rir = 2) }))
        assertFalse("RIR 3 no es pesado sin otra señal", isApproachedAsSecond(sets { workingSet(it, reps = 5, rir = 3) }))
        assertFalse("RPE 7 no es pesado sin otra señal", isApproachedAsSecond(sets { workingSet(it, reps = 5, rpe = 7.0) }))
    }

    @Test
    fun a_main_compound_up_to_eight_reps_is_heavy_without_any_other_signal() {
        assertTrue(isApproachedAsSecond(sets { workingSet(it, range = 4..6, rir = 3) }, role = SlotRole.T1_MAIN))
        assertTrue(isApproachedAsSecond(sets { workingSet(it, range = 6..8, rir = 3) }, role = SlotRole.T2_SUPPLEMENTAL))
        assertTrue(isApproachedAsSecond(sets { workingSet(it, reps = 3) }, competition = true))
        assertTrue("sin repeticiones declaradas, un principal sigue siendo pesado", isApproachedAsSecond(sets { workingSet(it) }, role = SlotRole.T1_MAIN))
        assertFalse("accesorio con 5 repeticiones y RIR 3", isApproachedAsSecond(sets { workingSet(it, reps = 5, rir = 3) }, role = SlotRole.T3_ACCESSORY))
        assertFalse("principal pero a 8–12", isApproachedAsSecond(sets { workingSet(it, range = 8..12, rir = 2) }, role = SlotRole.T1_MAIN))
    }

    @Test
    fun an_isolation_or_a_light_exercise_is_never_heavy_even_with_heavy_looking_sets() {
        assertFalse(isApproachedAsSecond(sets { workingSet(it, reps = 5, rir = 1, percent = 90.0) }, id = "leg-curl"))
        assertFalse(isApproachedAsSecond(sets { workingSet(it, reps = 5, rir = 1) }, id = "curl", role = SlotRole.T1_MAIN))
        assertFalse(isApproachedAsSecond(sets { workingSet(it, reps = 5, rir = 1) }, id = "pull-up"))
    }

    @Test
    fun an_exercise_the_engine_does_not_know_is_treated_as_not_heavy() {
        val session = sessionOf(lift("bench"), lift("mystery", heavySets()))
        val result = ApproachPlanner.apply(session, ApproachOptions(), infoOf)
        assertTrue(result.exercise("mystery").warmupSets.isEmpty())
        assertTrue(result.exercise("mystery").mobilitySeries.isEmpty())
        // Y si es el primero tampoco se inventa nada: sin articulaciones ni carga conocidas no hay qué preparar.
        val first = ApproachPlanner.apply(sessionOf(lift("mystery", heavySets())), ApproachOptions(), infoOf)
        assertTrue(first.exercise("mystery").warmupSets.isEmpty() && first.exercise("mystery").mobilitySeries.isEmpty())
    }

    @Test
    fun a_heavy_first_exercise_without_known_joints_gets_the_ramp_but_no_mobility() {
        val result = planned(sessionOf(lift("no-joints", heavySets())))
        val exercise = result.exercise("no-joints")
        assertEquals(3, exercise.warmupSets.size)
        assertTrue(exercise.mobilitySeries.isEmpty())
    }

    // ─── Niveles ────────────────────────────────────────────────────────────

    @Test
    fun the_ramp_depends_on_the_level_and_gets_a_single_at_ninety_percent_or_more() {
        val heavy = sessionOf(lift("squat", sets { workingSet(it, reps = 3, percent = 85.0) }))
        val veryHeavy = sessionOf(lift("squat", sets { workingSet(it, reps = 1, percent = 92.0) }))

        assertEquals(listOf(40.0, 60.0, 80.0), planned(heavy, ApproachLevel.NOVICE).exercise("squat").rampPercents())
        assertEquals(listOf(40.0, 60.0, 80.0), planned(heavy, ApproachLevel.INTERMEDIATE).exercise("squat").rampPercents())
        assertEquals(listOf(40.0, 60.0, 80.0), planned(heavy, ApproachLevel.ADVANCED).exercise("squat").rampPercents())
        assertEquals(
            "las repeticiones de los primeros escalones bajan con la experiencia",
            listOf(6, 4, 2),
            planned(heavy, ApproachLevel.ADVANCED).exercise("squat").warmupSets.map { it.targetReps },
        )

        val advanced = planned(veryHeavy, ApproachLevel.ADVANCED).exercise("squat")
        assertEquals(listOf(40.0, 60.0, 80.0, 90.0), advanced.rampPercents())
        assertEquals(1, advanced.warmupSets.last().targetReps)
        assertEquals(4, planned(veryHeavy, ApproachLevel.INTERMEDIATE).exercise("squat").warmupSets.size)
        assertEquals("el novato no llega a series casi máximas: se queda en 3", 3, planned(veryHeavy, ApproachLevel.NOVICE).exercise("squat").warmupSets.size)
    }

    @Test
    fun less_experience_means_more_mobility() {
        val session = sessionOf(lift("deadlift"))
        val novice = planned(session, ApproachLevel.NOVICE).exercise("deadlift")
        val intermediate = planned(session, ApproachLevel.INTERMEDIATE).exercise("deadlift")
        val advanced = planned(session, ApproachLevel.ADVANCED).exercise("deadlift")

        assertTrue("novato ≤ 4 min", novice.mobilitySeconds() <= 240)
        assertTrue("intermedio ≤ 2,5 min", intermediate.mobilitySeconds() <= 150)
        assertTrue("avanzado ≤ 2 min", advanced.mobilitySeconds() <= 120)
        assertTrue(novice.mobilitySeconds() >= intermediate.mobilitySeconds())
        assertTrue(intermediate.mobilitySeconds() >= advanced.mobilitySeconds())
        assertTrue(novice.mobilitySeries.size >= advanced.mobilitySeries.size)
        // Los tres cubren al menos las articulaciones principales.
        listOf(novice, intermediate, advanced).forEach { exercise ->
            val covered = JointMobility.jointsCoveredBy(exercise.mobilitySeries)
            assertTrue(JointMobility.HIP in covered && JointMobility.KNEE in covered)
        }
    }

    @Test
    fun later_exercises_get_a_shorter_ramp_for_every_level() {
        listOf(ApproachLevel.NOVICE, ApproachLevel.INTERMEDIATE, ApproachLevel.ADVANCED).forEach { level ->
            val squat = planned(sessionOf(lift("bench"), lift("squat")), level).exercise("squat")
            assertTrue("$level: 1–2 pasos", squat.warmupSets.size in 1..2)
            assertTrue("$level: el último escalón no llega al 90 % sin series casi máximas", squat.warmupSets.all { it.percentageOfWorkingWeight < 90.0 })
        }
        val veryHeavy = sessionOf(lift("bench"), lift("squat", sets { workingSet(it, reps = 1, percent = 95.0) }))
        val advanced = planned(veryHeavy, ApproachLevel.ADVANCED).exercise("squat")
        assertEquals(listOf(70.0, 90.0), advanced.rampPercents())
    }

    // ─── Lo que ya trae el plan ─────────────────────────────────────────────

    private fun authorWarmups(id: String) = listOf(
        WarmupSetDefinition("$id-w1", 45.0, 6, restBetween = 60),
        WarmupSetDefinition("$id-w2", 70.0, 3, restBetween = 90),
    )

    private fun authorMobility() = listOf(
        MobilitySeries(id = "autor-1", name = "Movilidad del autor", durationSeconds = 60, unit = MobilityUnit.SECONDS),
    )

    @Test
    fun author_warmups_are_kept_and_only_the_missing_mobility_is_completed() {
        val author = authorWarmups("squat")
        val result = planned(sessionOf(lift("squat", warmupSets = author)))
        val squat = result.exercise("squat")
        assertEquals("la aproximación del autor no se toca", author, squat.warmupSets)
        assertTrue("la movilidad obligatoria del primer ejercicio sí se completa", squat.mobilitySeries.isNotEmpty())
    }

    @Test
    fun author_mobility_is_kept_and_only_the_missing_ramp_is_completed() {
        val author = authorMobility()
        val result = planned(sessionOf(lift("squat", mobility = author)))
        val squat = result.exercise("squat")
        assertEquals("la movilidad del autor no se toca ni se duplica", author, squat.mobilitySeries)
        assertEquals(listOf(40.0, 60.0, 80.0), squat.rampPercents())
    }

    @Test
    fun an_exercise_with_both_declared_is_returned_untouched() {
        val original = sessionOf(lift("squat", warmupSets = authorWarmups("squat"), mobility = authorMobility()))
        assertEquals(original, planned(original))
    }

    @Test
    fun an_author_ramp_on_the_first_exercise_counts_as_covering_its_joints() {
        // Con aproximación propia en el press banca, el press inclinado no necesita nada más.
        val result = planned(sessionOf(lift("bench", warmupSets = authorWarmups("bench")), lift("incline")))
        assertTrue(result.exercise("incline").warmupSets.isEmpty())
        assertTrue(result.exercise("incline").mobilitySeries.isEmpty())
    }

    @Test
    fun author_mobility_of_a_known_movement_counts_as_covering_its_joints() {
        // El autor puso la sentadilla profunda con apoyo (cadera, rodilla y tobillo) en un ejercicio ligero del principio.
        val deepSquat = JointMobility.seriesFor(checkNotNull(JointMobility.movement("mob_supported_deep_squat")), "Autor", 40)
        val result = planned(sessionOf(lift("leg-curl", accessorySets(), mobility = listOf(deepSquat)), lift("squat")))
        val squat = result.exercise("squat")
        // Solo queda la lumbar sin cubrir, así que la mobilidad nueva (si hay) no repite cadera/rodilla/tobillo.
        squat.mobilitySeries.forEach { series ->
            val joints = JointMobility.jointsOf(series.id)
            assertTrue("${series.id} aporta algo nuevo", joints.any { it == JointMobility.LUMBAR_SPINE })
        }
        assertTrue(squat.warmupSets.isNotEmpty())
    }

    // ─── Propiedades generales ──────────────────────────────────────────────

    @Test
    fun applying_it_twice_is_the_same_as_applying_it_once() {
        val sessions = listOf(
            sessionOf(lift("squat")),
            sessionOf(lift("curl", accessorySets()), lift("lateral", accessorySets())),
            sessionOf(lift("bench"), lift("squat"), lift("rdl"), lift("curl", accessorySets())),
            sessionOf(lift("squat", warmupSets = authorWarmups("squat")), lift("bench", mobility = authorMobility())),
            sessionOf(lift("pull-up", sets { workingSet(it, reps = 6, rir = 1) }), lift("deadlift")),
        )
        ApproachLevel.entries.forEach { level ->
            sessions.forEach { session ->
                val once = planned(session, level)
                assertEquals("idempotente con $level", once, planned(once, level))
            }
        }
    }

    @Test
    fun the_original_session_is_not_mutated_and_other_fields_survive() {
        val original = sessionOf(lift("squat")).copy(description = "texto", dayOfWeek = 3, targetDurationMinutes = 55)
        val before = original.copy()
        val result = planned(original)
        assertEquals(before, original)
        assertEquals("texto", result.description)
        assertEquals(3, result.dayOfWeek)
        assertEquals(55, result.targetDurationMinutes)
        assertEquals(original.exercise("squat").sets, result.exercise("squat").sets)
    }

    @Test
    fun exercises_inside_parts_are_completed_too_and_mirrored_ones_stay_consistent() {
        val squat = lift("squat")
        val bench = lift("bench")
        val session = Session(
            id = "s",
            name = "Día",
            exercises = listOf(squat), // espejo heredado del primer ejercicio
            parts = listOf(
                SessionPart(id = "p1", name = "Principal", exercises = listOf(squat, bench)),
            ),
        )
        val result = planned(session)
        val inParts = result.parts.single().exercises
        assertEquals(listOf(40.0, 60.0, 80.0), inParts.first { it.id == "squat" }.rampPercents())
        assertEquals("el espejo recibe lo mismo", inParts.first { it.id == "squat" }, result.exercises.single())
        assertTrue("el segundo ejercicio (banca) tiene articulaciones nuevas", inParts.first { it.id == "bench" }.warmupSets.isNotEmpty())
        assertEquals("un ejercicio duplicado cuenta una vez", 2, result.allExercises().distinctBy { it.id }.size)
    }

    @Test
    fun alternative_sessions_are_planned_on_their_own() {
        val variant = sessionOf(lift("bench"))
        val session = sessionOf(lift("squat")).copy(sessionB = variant)
        val result = planned(session)
        assertEquals(3, result.exercise("squat").warmupSets.size)
        val planned = checkNotNull(result.sessionB)
        assertEquals("la variante B tiene su propio primer ejercicio", 3, planned.exercise("bench").warmupSets.size)
    }

    @Test
    fun a_session_with_nothing_to_prepare_comes_back_equal() {
        val empty = Session(id = "e", name = "Vacío")
        assertEquals(empty, planned(empty))
        val cardioOnly = sessionOf(Exercise(id = "c", name = "Cardio", cardioDetails = CardioDetails(type = CardioType.WALK)))
        assertEquals(cardioOnly, planned(cardioOnly))
    }

    @Test
    fun the_default_contract_with_no_info_provider_changes_nothing() {
        val session = sessionOf(lift("squat"), lift("bench"))
        assertEquals(session, ApproachPlanner.apply(session))
    }

    @Test
    fun the_ramp_percentages_are_ascending_and_never_reach_the_working_load() {
        val result = planned(sessionOf(lift("bench"), lift("squat", sets { workingSet(it, reps = 1, percent = 95.0) })), ApproachLevel.INTERMEDIATE)
        result.allExercises().forEach { exercise ->
            val percents = exercise.rampPercents()
            assertEquals(percents.sorted(), percents)
            assertTrue(percents.all { it in 10.0..90.0 })
            assertTrue("sin repeticiones nulas", exercise.warmupSets.all { it.targetReps >= 1 })
        }
    }
}
