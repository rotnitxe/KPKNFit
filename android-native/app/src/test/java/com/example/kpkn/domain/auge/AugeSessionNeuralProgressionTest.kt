package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.abs

/**
 * D3 (2.6): el progresivo neural de una sesión cuenta series duras globales.
 *
 * Una serie dura es efectiva, no es calentamiento ni omitida y su RPE efectivo llega a 8. El contador
 * se reinicia una vez por sesión (no al cambiar de pilar), sube antes del factor de la serie actual y
 * nunca alimenta al muscular ni al espinal, que conservan su conteo por pilar.
 */
class AugeSessionNeuralProgressionTest {

    private val settings = Settings(athleteType = AthleteType.POWERBUILDER, restTimerDefaultSeconds = 120)
    private val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
    private val db = AugeGoldenCorpus.EXERCISE_DB

    private data class Seen(
        val set: CompletedSet,
        val context: RecipeExerciseContext,
        val raw: SetDrain,
        val accumulatedSets: Int,
    )

    private fun recipe() = SessionDrainRecipe(
        settings = settings,
        tanks = tanks,
        adaptiveCache = AugeAdaptiveCache(),
        options = RecipeOptions(
            cardioHandling = RecipeOptions.CardioHandling.CONSUMER_HOOK,
            softCapUnits = RecipeOptions.SoftCapUnits.CHANNEL_PERCENT,
        ),
        resolveInfo = { identity -> db[identity.exerciseDbId] ?: db[identity.exerciseName] },
    )

    private fun walk(exercises: List<CompletedExercise>): List<Seen> {
        val seen = mutableListOf<Seen>()
        recipe().forEachCompleted(
            exercises = exercises,
            onCardio = {},
            onSet = { _, set, context, raw, _, accumulated ->
                seen += Seen(set, context, raw, accumulated)
            },
        )
        return seen
    }

    private fun expected(seen: Seen, hardSessionSets: Int?): SetDrain =
        AugeFatigueEngine.calculateSetBatteryDrain(
            set = seen.set,
            metrics = seen.context.metrics,
            tanks = tanks,
            accumulatedSets = seen.accumulatedSets,
            restTime = seen.context.restTime,
            densityMultiplier = seen.context.densityMultiplier,
            weightUnit = settings.weightUnit,
            hardSessionSets = hardSessionSets,
        )

    private fun exercise(id: String, sets: List<CompletedSet>) = CompletedExercise(
        exerciseId = id,
        exerciseName = db.getValue(id).name,
        exerciseDbId = id,
        restTime = 120,
        sets = sets,
    )

    private fun hardSets(prefix: String, count: Int, weight: Double = 20.0, reps: Int = 3, rpe: Double = 9.0) =
        List(count) { CompletedSet("$prefix-$it", weight = weight, reps = reps, rpe = rpe) }

    // ─── Tabla del progresivo ────────────────────────────────────────────────

    @Test
    fun systemicProgressiveTableForTheNewGlobalIndex() {
        val expectedNew = mapOf(
            7 to 1.884091,
            8 to 2.122702,
            9 to 2.380868,
            10 to 2.600000,
            11 to 2.600000,
            12 to 2.600000,
        )
        expectedNew.forEach { (sets, value) ->
            assertEquals("índice $sets", value, AugeFatigueEngine.calculateSystemicProgressiveMultiplier(sets), 1e-6)
        }
        // Lo que daría un reinicio por pilar en la segunda posición de la sesión.
        val expectedReset = mapOf(1 to 1.0, 2 to 1.055000, 3 to 1.161049, 4 to 1.301926, 5 to 1.471580, 6 to 1.666448)
        expectedReset.forEach { (sets, value) ->
            assertEquals("pilar $sets", value, AugeFatigueEngine.calculateSystemicProgressiveMultiplier(sets), 1e-6)
        }
        // Comparación explícita del punto 7: global vs. reinicio.
        assertEquals(1.884091, AugeFatigueEngine.calculateSystemicProgressiveMultiplier(7), 1e-6)
        assertEquals(1.0, AugeFatigueEngine.calculateSystemicProgressiveMultiplier(1), 1e-6)
    }

    // ─── Sin reinicios al cambiar de pilar ───────────────────────────────────

    @Test
    fun hardSetsContinueAcrossPillarsWithoutResetting() {
        val seen = walk(
            listOf(
                exercise("bench", hardSets("b", 6)),
                exercise("squat", hardSets("s", 6)),
            ),
        )
        assertEquals(12, seen.size)

        seen.forEachIndexed { index, item ->
            val global = index + 1
            assertEquals("serie ${index + 1} usa el índice global", expected(item, global).cnsDrainPct, item.raw.cnsDrainPct, 1e-9)
            assertEquals("serie ${index + 1} conserva el espinal por pilar", expected(item, item.accumulatedSets).spinalDrainPct, item.raw.spinalDrainPct, 1e-9)
        }

        val firstOfSecondPillar = seen[6]
        assertEquals(1, firstOfSecondPillar.accumulatedSets) // El conteo por pilar arranca de nuevo…
        assertEquals(1.884091, AugeFatigueEngine.calculateSystemicProgressiveMultiplier(7), 1e-6) // …el neural no.
        assertNotEquals(expected(firstOfSecondPillar, 1).cnsDrainPct, firstOfSecondPillar.raw.cnsDrainPct, 1e-6)
    }

    // ─── Series suaves, calentamientos y omitidas ────────────────────────────

    @Test
    fun softWarmupAndSkippedSetsDoNotIncreaseTheNeuralProgression() {
        val sets = listOf(
            CompletedSet("soft-1", weight = 20.0, reps = 5, rpe = 7.0),
            CompletedSet("warmup", weight = 20.0, reps = 10, rpe = 9.0, isWarmup = true),
            CompletedSet("hard-1", weight = 20.0, reps = 3, rpe = 8.0),
            CompletedSet("skipped", weight = 20.0, reps = 3, rpe = 10.0, skipped = true),
            CompletedSet("soft-2", weight = 20.0, reps = 5, rpe = 7.5),
            CompletedSet("hard-2", weight = 20.0, reps = 3, rpe = 8.5),
            CompletedSet("low", weight = 20.0, reps = 3, rpe = 5.9),
        )
        val seen = walk(listOf(exercise("bench", sets)))
        assertEquals(4, seen.size) // Calentamiento, omitida y RPE < 6 ni siquiera entran.

        // soft-1 no cuenta (índice 0); hard-1 estrena el índice 1; soft-2 sigue en 1; hard-2 sube a 2.
        assertEquals(0, seen[0].accumulatedSets - 1) // El contador por pilar sí avanza con la serie suave.
        assertEquals(expected(seen[0], 0).cnsDrainPct, seen[0].raw.cnsDrainPct, 1e-9)
        assertEquals(expected(seen[1], 1).cnsDrainPct, seen[1].raw.cnsDrainPct, 1e-9)
        assertEquals(expected(seen[2], 1).cnsDrainPct, seen[2].raw.cnsDrainPct, 1e-9)
        assertEquals(expected(seen[3], 2).cnsDrainPct, seen[3].raw.cnsDrainPct, 1e-9)
        // Una serie suave ignora cualquier índice duro: su multiplicador es 1,0.
        assertEquals(expected(seen[2], 12).cnsDrainPct, seen[2].raw.cnsDrainPct, 1e-9)
    }

    @Test
    fun unilateralHalvesCountLikeTheRecipeAlreadyDoes() {
        val uni = List(6) { index ->
            CompletedSet("u-$index", weight = 20.0, reps = 3, rpe = 9.0, side = if (index % 2 == 0) "left" else "right")
        }
        val seen = walk(
            listOf(
                exercise("bench", uni),
                exercise("squat", hardSets("s", 2)),
            ),
        )
        // 0,5→1 · 1,0→1 · 1,5→2 · 2,0→2 · 2,5→3 · 3,0→3 y después 4, 5.
        val hardIndices = listOf(1, 1, 2, 2, 3, 3, 4, 5)
        seen.forEachIndexed { index, item ->
            assertEquals("serie ${index + 1}", expected(item, hardIndices[index]).cnsDrainPct, item.raw.cnsDrainPct, 1e-9)
        }
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 1, 2), seen.map { it.accumulatedSets })
    }

    // ─── Muscular y espinal no cambian ───────────────────────────────────────

    @Test
    fun muscularAndSpinalIgnoreTheHardSessionCounter() {
        val seen = walk(
            listOf(
                exercise("bench", hardSets("b", 3)),
                exercise("squat", hardSets("s", 3, weight = 40.0, reps = 5, rpe = 8.5)),
            ),
        )
        seen.forEach { item ->
            val legacy = expected(item, item.accumulatedSets)
            val withZero = expected(item, 0)
            val withTwelve = expected(item, 12)
            assertEquals("muscular no depende del contador duro", legacy.muscularDrainPct, item.raw.muscularDrainPct, 1e-9)
            assertEquals("muscular no depende del contador duro", withZero.muscularDrainPct, item.raw.muscularDrainPct, 1e-9)
            assertEquals("muscular no depende del contador duro", withTwelve.muscularDrainPct, item.raw.muscularDrainPct, 1e-9)
            assertEquals("espinal conserva el conteo por pilar", legacy.spinalDrainPct, item.raw.spinalDrainPct, 1e-9)
            assertEquals("espinal conserva el conteo por pilar", withZero.spinalDrainPct, item.raw.spinalDrainPct, 1e-9)
            assertEquals("espinal conserva el conteo por pilar", withTwelve.spinalDrainPct, item.raw.spinalDrainPct, 1e-9)
        }
        // El CNS sí se mueve con el contador: la prueba no es vacía.
        assertTrueDifferent(seen[3].raw.cnsDrainPct, expected(seen[3], 1).cnsDrainPct)
    }

    @Test
    fun legacyConsumersWithoutSessionCounterKeepThePillarProgression() {
        val seen = walk(listOf(exercise("bench", hardSets("b", 3)))).last()
        val legacy = AugeFatigueEngine.calculateSetBatteryDrain(
            set = seen.set,
            metrics = seen.context.metrics,
            tanks = tanks,
            accumulatedSets = seen.accumulatedSets,
            restTime = seen.context.restTime,
            densityMultiplier = seen.context.densityMultiplier,
            weightUnit = settings.weightUnit,
        )
        val explicit = expected(seen, seen.accumulatedSets)
        assertEquals(legacy.cnsDrainPct, explicit.cnsDrainPct, 1e-12)
        assertEquals(legacy.spinalDrainPct, explicit.spinalDrainPct, 1e-12)
        assertEquals(legacy.muscularDrainPct, explicit.muscularDrainPct, 1e-12)
    }

    private fun assertTrueDifferent(a: Double, b: Double) {
        if (abs(a - b) <= 1e-6) throw AssertionError("Se esperaban valores distintos, ambos $a")
    }
}
