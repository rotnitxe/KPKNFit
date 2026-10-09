package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.AvoidGroups
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.training.CardioPreference
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Punto de partida del alta (C2): el generador lee las marcas con repeticiones, la variante de cada básico, lo que la persona evita,
 * la base de cardio, el estilo de mesa y las repeticiones seguidas. Con todo en su valor por defecto, el programa y su id son los de
 * antes de esos campos (la huella de abajo se tomó del código anterior).
 */
class StartingPointGeneratorTest {

    private val catalog get() = RoutineTestSupport.catalog

    /** Definición del catálogo de cada configuración: «evitar» se aplica por definición. */
    private val definitionOf: Map<String, String> by lazy {
        catalog.families.flatMap { it.definitions }.flatMap { definition -> definition.configurations.map { it.id to definition.id } }.toMap()
    }

    private val highBar = "high_bar_back_squat__barbell"
    private val frontSquat = "front_squat__barbell"
    private val militaryBarbell = "military_press__barbell"
    private val pronations = setOf("pronation__dumbbells", "pronation__cable")
    private val supinations = setOf("supination__dumbbells", "supination__cable")
    private val wristFlexions = setOf(
        "forearms_curl_muneca_sentado__barbell", "forearms_curl_muneca_de_pie_tras_espalda_barra__default",
        "forearms_curl_muneca_sentado__ez_bar", "forearms_curl_muneca_sentado__dumbbells", "forearms_curl_muneca_sentado__cable",
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Huella de un programa: ids, configuraciones, referencias de 1RM, cargas y descansos (lo que el usuario activa). */
    private fun digestOf(routine: GeneratedRoutine): String {
        val sb = StringBuilder()
        sb.append(routine.program.id).append('\n')
        sb.append(routine.program.description).append('\n')
        sessionsOf(routine).forEach { session ->
            sb.append(session.id).append('|').append(session.name).append('|').append(session.dayOfWeek).append('|').append(session.placeId).append('\n')
            session.allExercises().forEach { ex ->
                sb.append(ex.id).append('|').append(ex.catalogConfigurationId).append('|').append(ex.reference1RM).append('|').append(ex.variantName).append('|')
                ex.sets.forEach { s ->
                    sb.append(s.targetReps).append(',').append(s.targetPercentageRM).append(',').append(s.weight).append(',').append(s.restAfterSeconds).append(';')
                }
                sb.append('\n')
            }
        }
        return sha256(sb.toString())
    }

    private fun sessionsOf(routine: GeneratedRoutine): List<Session> =
        routine.program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private fun gymRequest(
        mode: RoutineMode,
        level: RoutineLevel,
        days: Int,
        minutes: Int,
        seed: Int = 0,
        cardio: CardioPreference? = null,
        marks: Map<LiftMark, Double> = emptyMap(),
    ): RoutineRequest = RoutineTestSupport.request(RoutineTestSupport.gym, mode, level, days, minutes, seed = seed, cardio = cardio, marks = marks)

    // ─── Lo de siempre no cambia ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun withEveryNewFieldAtItsDefaultTheProgramsAndTheIdsAreTheSameAsBefore() {
        val profiles = listOf(
            RoutineTestSupport.gym, RoutineTestSupport.bodyOnly, RoutineTestSupport.homeDumbbellsBand,
            RoutineTestSupport.park, RoutineTestSupport.gymAndHome,
        )
        val all = StringBuilder()
        var count = 0
        for (mode in RoutineMode.entries) for (profile in profiles) for (level in RoutineLevel.entries) {
            for (days in listOf(1, 3, 6)) for (minutes in listOf(45, 90)) {
                val request = RoutineTestSupport.request(profile, mode, level, days, minutes, seed = count % 3)
                all.append(digestOf(RoutineGenerator.generate(request))).append('\n')
                count++
            }
        }
        val marksCases = listOf(
            mapOf(LiftMark.SQUAT to 120.0, LiftMark.BENCH to 90.0, LiftMark.DEADLIFT to 150.0),
            mapOf(LiftMark.BENCH to 80.0),
        )
        for (mode in listOf(RoutineMode.GENERAL_HYBRID, RoutineMode.GENERAL_FUNCTIONAL, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineMode.CUSTOM_POWERBUILDING, RoutineMode.CUSTOM_POWERLIFTING, RoutineMode.DISCIPLINE_ARMWRESTLING)) {
            for (level in listOf(RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) for (days in listOf(3, 5)) {
                val cardioCases = listOf(null, CardioPreference(CardioType.BIKE_STATIONARY, 20), CardioPreference(null, 25))
                for (cardio in cardioCases) for (marks in marksCases) {
                    val request = RoutineTestSupport.request(RoutineTestSupport.gym, mode, level, days, 60, seed = 1, cardio = cardio, marks = marks)
                    all.append(digestOf(RoutineGenerator.generate(request))).append('\n')
                    count++
                }
            }
        }
        assertEquals("pedidos recorridos", BASELINE_COUNT, count)
        assertEquals("huella de los programas de siempre", BASELINE_DIGEST, sha256(all.toString()))
    }

    /** Huella tomada del código anterior a los campos del punto de partida (mismos 1.344 pedidos que la prueba de arriba). */
    private companion object {
        const val BASELINE_COUNT = 1344
        const val BASELINE_DIGEST = "92bb8ea1ed3ed1cdadfb7736f8b9f4ccdf7c327d9c6cd2c31c76257e040a2e82"
    }

    @Test
    fun theNewFieldsEnterTheIdOnlyWhenTheyAreSet() {
        val base = gymRequest(RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 3, 60)
        fun idOf(request: RoutineRequest): String = RoutineGenerator.generate(request).program.id
        val plain = idOf(base)
        assertEquals("valores por defecto: mismo id", plain, idOf(base.copy(avoided = emptySet(), cardioBase = null, armStyle = null)))
        assertFalse("evitar cambia el id", plain == idOf(base.copy(avoided = setOf(AvoidGroup.DEADLIFT))))
        assertFalse("la base de cardio cambia el id", plain == idOf(base.copy(cardioBase = CardioBase.LOW)))
        assertFalse("las repeticiones seguidas cambian el id", plain == idOf(base.copy(maxReps = mapOf(CapabilitySkill.PULL_UP to 8))))
        assertEquals("con los mismos campos, el mismo id", idOf(base.copy(armStyle = ArmStyle.HOOK)), idOf(base.copy(armStyle = ArmStyle.HOOK)))
    }

    // ─── Marcas con repeticiones ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun aPrWithRepsGivesTheHybridOneRmAndThePrOnlyOnTheMarkedSquat() {
        // Varias semillas: el desempate entre la sentadilla alta y la frontal cambia de una a otra; la alta tiene que salir en alguna.
        val routines = (0..4).map { seed ->
            RoutineGenerator.generate(
                gymRequest(RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 3, 60, seed = seed, marks = mapOf(LiftMark.SQUAT to 100.0))
                    .copy(markReps = mapOf(LiftMark.SQUAT to 5)),
            )
        }
        val exercises = routines.flatMap { sessionsOf(it) }.flatMap { it.allExercises() }
        val expected = calculateHybrid1RM(100.0, 5)
        assertEquals("1RM híbrido de 100 kg × 5", 112.5, expected, 1e-9)
        val highBarExercises = exercises.filter { it.catalogConfigurationId == highBar }
        assertTrue("alguna semilla pone la sentadilla alta", highBarExercises.isNotEmpty())
        highBarExercises.forEach { ex ->
            assertEquals("la alta lleva el 1RM híbrido", expected, ex.reference1RM ?: -1.0, 1e-9)
            assertEquals("y el PR de la carga inteligente", PrReference(weight = 100.0, reps = 5), ex.prFor1RM)
        }
        // La frontal (factor 0,8) sin variante elegida: su equivalente de la sentadilla por su factor.
        exercises.filter { it.catalogConfigurationId == frontSquat }.forEach { ex ->
            assertEquals(90.0, ex.reference1RM ?: -1.0, 1e-6)
            assertNull(ex.prFor1RM)
        }
        assertTrue("ningún otro ejercicio lleva el PR", exercises.filter { it.prFor1RM != null }.all { it.catalogConfigurationId == highBar })
    }

    @Test
    fun aMaxWithoutRepsGivesNoPr() {
        val routine = RoutineGenerator.generate(gymRequest(RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 3, 60, marks = mapOf(LiftMark.SQUAT to 100.0)))
        val exercises = sessionsOf(routine).flatMap { it.allExercises() }
        assertTrue("con 1RM la alta sale a 100 kg", exercises.filter { it.catalogConfigurationId == highBar }.all { it.reference1RM == 100.0 })
        assertTrue("con 1RM no hay PR", exercises.all { it.prFor1RM == null })
    }

    // ─── Variantes de los básicos ────────────────────────────────────────────────────────────────────────────

    @Test
    fun aFrontSquatChosenAsVariantIsTheMainSquatAndReadsItsOwnMark() {
        val request = gymRequest(RoutineMode.CUSTOM_POWERBUILDING, RoutineLevel.INTERMEDIATE, 3, 60, marks = mapOf(LiftMark.SQUAT to 100.0))
            .copy(liftVariants = mapOf(LiftMark.SQUAT to frontSquat))
        val exercises = sessionsOf(RoutineGenerator.generate(request)).flatMap { it.allExercises() }
        val front = exercises.filter { it.catalogConfigurationId == frontSquat }
        assertTrue("la frontal sale como sentadilla principal", front.isNotEmpty())
        // La marca es suya: 100 kg de frontal, y la alta (factor 1,0) sale a 100 / 0,8 = 125 kg.
        front.forEach { assertEquals(100.0, it.reference1RM ?: -1.0, 1e-6) }
        exercises.filter { it.catalogConfigurationId == highBar }.forEach { assertEquals(125.0, it.reference1RM ?: -1.0, 1e-6) }
    }

    @Test
    fun aDeclaredPressMarkMakesTheBarbellPressTheVerticalPushOfTheWeek() {
        val plain = sessionsOf(RoutineGenerator.generate(gymRequest(RoutineMode.CUSTOM_POWERBUILDING, RoutineLevel.INTERMEDIATE, 3, 60)))
        val marked = sessionsOf(
            RoutineGenerator.generate(gymRequest(RoutineMode.CUSTOM_POWERBUILDING, RoutineLevel.INTERMEDIATE, 3, 60, marks = mapOf(LiftMark.OVERHEAD_PRESS to 60.0))),
        )
        assertFalse("sin marca, el press con barra no entra en el empuje vertical", plain.any { s -> s.allExercises().any { it.catalogConfigurationId == militaryBarbell } })
        val sessionsWithBarbell = marked.count { s -> s.allExercises().any { it.catalogConfigurationId == militaryBarbell } }
        assertTrue("con marca entra en el empuje vertical de la semana ($sessionsWithBarbell sesiones)", sessionsWithBarbell >= 1)
        marked.flatMap { it.allExercises() }.filter { it.catalogConfigurationId == militaryBarbell }
            .forEach { assertEquals(60.0, it.reference1RM ?: -1.0, 1e-6) }
    }

    // ─── Ejercicios a evitar ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun avoidingDeadliftsLeavesNoDeadliftFamilyInTheWeekOfBodybuilding() {
        val request = gymRequest(RoutineMode.CUSTOM_BODYBUILDING, RoutineLevel.INTERMEDIATE, 5, 60).copy(avoided = setOf(AvoidGroup.DEADLIFT))
        val family = AvoidGroups.definitionIdsOf(AvoidGroup.DEADLIFT)
        val found = sessionsOf(RoutineGenerator.generate(request)).flatMap { it.allExercises() }
            .mapNotNull { it.catalogConfigurationId }
            .mapNotNull { definitionOf[it] }
            .filter { it in family }
        assertTrue("ningún ejercicio de la familia del peso muerto: $found", found.isEmpty())
    }

    @Test
    fun avoidingJumpsRemovesTheExplosiveWorkAndTheNoteSaysThereIsNoSubstitute() {
        val request = gymRequest(RoutineMode.GENERAL_HYBRID, RoutineLevel.INTERMEDIATE, 3, 60).copy(avoided = setOf(AvoidGroup.JUMPS))
        val routine = RoutineGenerator.generate(request)
        val power = sessionsOf(routine).single { it.name == "Cardio y potencia" }
        assertTrue("sin fuerza explosiva en la sesión de potencia", power.allExercises().all { it.cardioDetails != null })
        assertTrue("la nota dice que no hay sustituto: ${routine.notes}", routine.notes.any { it.startsWith("Sin sustituto para Saltos e impacto") })
    }

    // ─── Base de cardio ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun aLowCardioBaseKeepsIntervalsOutOfTheHybridWeekAndAnIntervalBaseKeepsThem() {
        fun intervalBlocks(routine: GeneratedRoutine): Int =
            sessionsOf(routine).flatMap { it.allExercises() }.mapNotNull { it.cardioDetails }.sumOf { it.intervalBlocks.size }
        val low = RoutineGenerator.generate(
            gymRequest(RoutineMode.GENERAL_HYBRID, RoutineLevel.INTERMEDIATE, 5, 60, cardio = CardioPreference(null, 20, CardioIntensity.BAJA))
                .copy(cardioBase = CardioBase.LOW),
        )
        assertEquals("con base baja no hay intervalos", 0, intervalBlocks(low))
        val intervals = RoutineGenerator.generate(
            gymRequest(RoutineMode.GENERAL_HYBRID, RoutineLevel.INTERMEDIATE, 5, 60, cardio = CardioPreference(null, 20, CardioIntensity.ALTA))
                .copy(cardioBase = CardioBase.INTERVALS),
        )
        assertTrue("con intervalos, el día de zona 2 de 5 días pasa a potencia", intervalBlocks(intervals) > 0)
    }

    // ─── Estilo de mesa ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun theTableStylesWriteTheirEmphasisAndNeverTakeSetsAwayFromTheirHoles() {
        fun table(style: ArmStyle?): Session =
            sessionsOf(RoutineGenerator.generate(gymRequest(RoutineMode.DISCIPLINE_ARMWRESTLING, RoutineLevel.INTERMEDIATE, 4, 90).copy(armStyle = style)))
                .single { it.name == "Mesa y hombro" }
        fun sets(session: Session, ids: Set<String>): Int =
            session.allExercises().filter { (it.catalogConfigurationId ?: "") in ids }.sumOf { ex -> ex.sets.count { !it.isEmptySlot } }

        val plain = table(null)
        val topRoll = table(ArmStyle.TOP_ROLL)
        val hook = table(ArmStyle.HOOK)
        // La serie más se pide como hueco prioritario: el presupuesto semanal de antebrazo puede no concederla (se mide y se informa).
        println("ARMWRESTLING pronation sets plain=${sets(plain, pronations)} topRoll=${sets(topRoll, pronations)}")
        println("ARMWRESTLING flexion sets plain=${sets(plain, wristFlexions)} hook=${sets(hook, wristFlexions)}")
        assertTrue("top roll no quita series a la pronación", sets(topRoll, pronations) >= sets(plain, pronations))
        assertTrue("hook no quita series a la flexión de muñeca", sets(hook, wristFlexions) >= sets(plain, wristFlexions))
        assertTrue("hook no quita series a la supinación", sets(hook, supinations) >= sets(plain, supinations))
        assertTrue(topRoll.focus.orEmpty().contains("Énfasis en pronación (top roll)"))
        assertTrue(hook.focus.orEmpty().contains("Énfasis en flexión de muñeca y supinación (hook)"))
        assertEquals("sin estilo, el texto de siempre", "Pronación, supinación, bíceps y hombro", plain.focus)
    }

    // ─── Repeticiones seguidas ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun theRepsOfALadderFollowFiftyToSeventyPercentOfTheMaximum() {
        assertEquals(4..6, BodyweightLadders.repsWithMax(5..10, 8))
        assertEquals(1..1, BodyweightLadders.repsWithMax(3..6, 1))
        assertEquals(10..10, BodyweightLadders.repsWithMax(5..10, 30))
        assertEquals(8..12, BodyweightLadders.repsWithMax(8..12, null))
        assertEquals(8..12, BodyweightLadders.repsWithMax(8..12, 0))
        assertNull(BodyweightLadders.repsWithMax(null, 8))
    }

    @Test
    fun aMaximumOfEightPullUpsPrescribesFourToSixRepsInTheLadder() {
        val request = RoutineTestSupport.request(
            RoutineTestSupport.park, RoutineMode.DISCIPLINE_CALISTHENICS, RoutineLevel.INTERMEDIATE, 3, 60,
            capabilities = mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.MANY),
        ).copy(maxReps = mapOf(CapabilitySkill.PULL_UP to 8))
        val pullUpIds = BodyweightLadders.pullUp.allIds
        // Solo las series con repeticiones: la suspensión isométrica de la escalera va por tiempo.
        val repRanges = sessionsOf(RoutineGenerator.generate(request)).flatMap { it.allExercises() }
            .filter { (it.catalogConfigurationId ?: "") in pullUpIds }
            .flatMap { ex -> ex.sets.filter { !it.isEmptySlot }.mapNotNull { set -> set.effectiveRepRange()?.let { ex.name to it } } }
        assertTrue("sale alguna dominada con repeticiones", repRanges.isNotEmpty())
        repRanges.forEach { (name, range) -> assertTrue("$name: ${range.min}–${range.max}", range.min >= 4 && range.max <= 6) }
    }
}
