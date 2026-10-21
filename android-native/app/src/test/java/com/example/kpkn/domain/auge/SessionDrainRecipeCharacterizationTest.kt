package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/** Valores obtenidos antes de extraer la receta; los bits protegen incluso el último decimal. */
@RunWith(Parameterized::class)
class SessionDrainRecipeCharacterizationTest(private val caseId: String) {
    private lateinit var previousZone: TimeZone

    @Before fun fijarZona() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(SessionDrainCharacterizationFixtures.zone))
    }

    @After fun restaurarZona() { TimeZone.setDefault(previousZone) }

    @Test fun conservaResultadosDeLasFuncionesPublicas() {
        assertEquals(caseId, expectedSnapshots.getValue(caseId), snapshot(SessionDrainCharacterizationFixtures.cache))
    }

    @Test fun remapearLaCacheNoVaciaPuedeCambiarLosAnillosLegacy() {
        val cache = SessionDrainCharacterizationFixtures.cache
        val original = snapshot(cache)
        val remapped = snapshot(cache.copy(muscleDrainMultipliers = remapMuscleMultiplierMapToPillars(cache.muscleDrainMultipliers)))
        // Cierre, predictor y estrés ya remapean; los tres primeros consumidores no deben moverse.
        assertEquals(original.lines().take(5), remapped.lines().take(5))
        val expectedChange = when (caseId) {
            "superserie" -> Triple("history[0][Pectorales]", 91, 92)
            "cardio" -> Triple("history[6][Pectorales]", 97, 98)
            else -> null
        }
        if (expectedChange != null) {
            val (key, before, after) = expectedChange
            val originalLine = original.lines().single { it.startsWith("$key=") }
            val remappedLine = remapped.lines().single { it.startsWith("$key=") }
            assertNotEquals(originalLine, remappedLine)
            assertEquals(originalLine.replace("recoveryScore=$before", "recoveryScore=$after"), remappedLine)
        } else {
            assertEquals(original, remapped)
        }
        if (original != remapped) println("[2.2b1][$caseId]\n$remapped\n[/2.2b1]")
    }

    private fun snapshot(cache: AugeAdaptiveCache): String {
        val f = SessionDrainCharacterizationFixtures
        val exercises = f.completed.getValue(caseId)
        val log = f.log(caseId, f.baseMs)
        val breakdown = AugeFatigueEngine.calculateCompletedSessionDrainBreakdown(exercises, f.db, f.settings, cache, log.durationMinutes)
        val history = listOf(log, f.log("axial", f.baseMs - 18 * f.hourMs), f.log("superserie", f.baseMs - 52 * f.hourMs))
        val actual = buildString {
            appendLine("global=${breakdown.global};muscles=${breakdown.perMuscleMuscular.toSortedMap()}")
            f.muscles.forEach { muscle ->
                appendLine("stress[$muscle]=${f.number(AugeRecoveryEngine.calculateMuscleSessionStress(muscle, log, f.settings, f.db, cache))}")
            }
            appendLine("predicted=${AugeFatigueEngine.calculateAdjustedPredictedDrain(f.planned.getValue(caseId), f.db, f.settings, cache)}")
            listOf(0, 6, 24).forEach { hours ->
                val now = f.baseMs + hours * f.hourMs
                appendLine("history[$hours].energy=${AugeRecoveryEngine.calculateSystemicFatigue(history, null, f.settings, f.db, adaptiveCache = cache, nowOverride = now)}")
                appendLine("history[$hours].spinal=${AugeRecoveryEngine.calculateSpinalBattery(history, null, f.settings, f.db, adaptiveCache = cache, nowOverride = now)}")
                f.muscles.forEach { muscle ->
                    appendLine("history[$hours][$muscle]=${AugeRecoveryEngine.calculateMuscleBattery(muscle, history, null, f.settings, f.db, adaptiveCache = cache, nowOverride = now)}")
                }
            }
        }.trimEnd()
        return actual
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<String>> = SessionDrainCharacterizationFixtures.completed.keys.map { arrayOf(it) }

        private val expectedSnapshots = mapOf(
            "unilateral" to """
                global=PredictedDrain(cns=50, muscular=39, spinal=8);muscles={Cuádriceps=25, Erectores Espinales=2, Glúteos=12}
                stress[Pectorales]=0.000000[0]
                stress[Cuádriceps]=313.138244[4644216402439619441]
                stress[Erectores Espinales]=23.338819[4627262511298630878]
                predicted=PredictedDrain(cns=65, muscular=44, spinal=8)
                history[0].energy=(69, 31, 0)
                history[0].spinal=91
                history[0][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=52, effectiveSets=5, status=FRESH)
                history[0][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=94, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=10, status=OPTIMAL)
                history[0][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=5, status=FRESH)
                history[6].energy=(80, 20, 0)
                history[6].spinal=94
                history[6][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=58, effectiveSets=5, status=FRESH)
                history[6][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=95, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=10, status=OPTIMAL)
                history[6][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=5, status=FRESH)
                history[24].energy=(95, 5, 0)
                history[24].spinal=99
                history[24][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=76, effectiveSets=5, status=FRESH)
                history[24][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=96, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=10, status=FRESH)
                history[24][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=98, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=5, status=FRESH)
            """.trimIndent(),
            "superserie" to """
                global=PredictedDrain(cns=78, muscular=57, spinal=8);muscles={Bíceps=4, Deltoides=6, Dorsales=8, Erectores Espinales=3, Pectorales=17, Trapecio=4, Tríceps=16}
                stress[Pectorales]=430.198775[4646275753084504383]
                stress[Cuádriceps]=0.000000[0]
                stress[Erectores Espinales]=151.008128[4639517142719779935]
                predicted=PredictedDrain(cns=69, muscular=62, spinal=8)
                history[0].energy=(64, 36, 0)
                history[0].spinal=94
                history[0][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=91, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=10, status=OPTIMAL)
                history[0][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[0][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=94, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=5, status=OPTIMAL)
                history[6].energy=(76, 24, 0)
                history[6].spinal=96
                history[6][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=93, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=10, status=OPTIMAL)
                history[6][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[6][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=95, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=5, status=FRESH)
                history[24].energy=(94, 6, 0)
                history[24].spinal=99
                history[24][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=96, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=10, status=FRESH)
                history[24][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[24][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=5, status=FRESH)
            """.trimIndent(),
            "cardio" to """
                global=PredictedDrain(cns=43, muscular=40, spinal=8);muscles={Bíceps=1, Core=1, Cuádriceps=14, Deltoides=3, Dorsales=1, Erectores Espinales=5, Glúteos=6, Pectorales=7, Tríceps=3}
                stress[Pectorales]=134.717355[4638943962117049929]
                stress[Cuádriceps]=259.720667[4643276670481307199]
                stress[Erectores Espinales]=103.888267[4637010903800383998]
                predicted=PredictedDrain(cns=46, muscular=41, spinal=8)
                history[0].energy=(74, 26, 0)
                history[0].spinal=93
                history[0][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=7, status=FRESH)
                history[0][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=94, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=4, status=OPTIMAL)
                history[0][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=95, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=5, status=FRESH)
                history[6].energy=(84, 16, 0)
                history[6].spinal=95
                history[6][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=7, status=FRESH)
                history[6][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=95, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=4, status=FRESH)
                history[6][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=96, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=5, status=FRESH)
                history[24].energy=(96, 4, 0)
                history[24].spinal=99
                history[24][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=99, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=7, status=FRESH)
                history[24][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=4, status=FRESH)
                history[24][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=5, status=FRESH)
            """.trimIndent(),
            "tecnicas" to """
                global=PredictedDrain(cns=64, muscular=66, spinal=8);muscles={Bíceps=1, Deltoides=12, Dorsales=1, Erectores Espinales=0, Pectorales=35, Trapecio=1, Tríceps=16}
                stress[Pectorales]=937.712102[4651459418695749866]
                stress[Cuádriceps]=0.000000[0]
                stress[Erectores Espinales]=62.736113[4634026140307166209]
                predicted=PredictedDrain(cns=58, muscular=48, spinal=8)
                history[0].energy=(67, 33, 0)
                history[0].spinal=97
                history[0][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=83, hoursToRecovery=8, hoursSinceLastSession=0, effectiveSets=9, status=RECOVERING)
                history[0][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[0][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=96, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=5, status=FRESH)
                history[6].energy=(78, 22, 0)
                history[6].spinal=98
                history[6][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=86, hoursToRecovery=5, hoursSinceLastSession=6, effectiveSets=9, status=OPTIMAL)
                history[6][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[6][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=96, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=5, status=FRESH)
                history[24].energy=(95, 5, 0)
                history[24].spinal=100
                history[24][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=92, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=9, status=OPTIMAL)
                history[24][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[24][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=98, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=5, status=FRESH)
            """.trimIndent(),
            "axial" to """
                global=PredictedDrain(cns=54, muscular=39, spinal=8);muscles={Bíceps=3, Deltoides=7, Dorsales=5, Erectores Espinales=5, Glúteos=3, Isquiosurales=5, Trapecio=8, Tríceps=3}
                stress[Pectorales]=0.000000[0]
                stress[Cuádriceps]=0.000000[0]
                stress[Erectores Espinales]=144.399958[4639284638432422277]
                predicted=PredictedDrain(cns=54, muscular=39, spinal=8)
                history[0].energy=(68, 32, 0)
                history[0].spinal=90
                history[0][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=52, effectiveSets=5, status=FRESH)
                history[0][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[0][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=95, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=10, status=OPTIMAL)
                history[6].energy=(79, 21, 0)
                history[6].spinal=93
                history[6][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=58, effectiveSets=5, status=FRESH)
                history[6][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[6][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=95, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=10, status=FRESH)
                history[24].energy=(95, 5, 0)
                history[24].spinal=99
                history[24][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=76, effectiveSets=5, status=FRESH)
                history[24][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[24][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=10, status=FRESH)
            """.trimIndent(),
            "sin-ficha" to """
                global=PredictedDrain(cns=52, muscular=45, spinal=10);muscles={Deltoides=1, Erectores Espinales=4, Glúteos=5, Isquiosurales=10, Pectorales=4, Tríceps=2}
                stress[Pectorales]=109.528284[4637407784732285832]
                stress[Cuádriceps]=0.000000[0]
                stress[Erectores Espinales]=93.118024[4636253015334077926]
                predicted=PredictedDrain(cns=52, muscular=45, spinal=10)
                history[0].energy=(71, 29, 0)
                history[0].spinal=88
                history[0][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=7, status=FRESH)
                history[0][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[0][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=96, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=5, status=FRESH)
                history[6].energy=(81, 19, 0)
                history[6].spinal=92
                history[6][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=98, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=7, status=FRESH)
                history[6][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[6][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=96, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=5, status=FRESH)
                history[24].energy=(95, 5, 0)
                history[24].spinal=99
                history[24][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=99, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=7, status=FRESH)
                history[24][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[24][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=98, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=5, status=FRESH)
            """.trimIndent(),
            "filtrado-rir" to """
                global=PredictedDrain(cns=26, muscular=16, spinal=0);muscles={Deltoides=3, Pectorales=9, Tríceps=4}
                stress[Pectorales]=173.399924[4640304984013649747]
                stress[Cuádriceps]=0.000000[0]
                stress[Erectores Espinales]=0.000000[0]
                predicted=PredictedDrain(cns=32, muscular=22, spinal=0)
                history[0].energy=(81, 19, 0)
                history[0].spinal=98
                history[0][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=96, hoursToRecovery=0, hoursSinceLastSession=0, effectiveSets=8, status=FRESH)
                history[0][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[0][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=18, effectiveSets=5, status=FRESH)
                history[6].energy=(88, 12, 0)
                history[6].spinal=99
                history[6][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=97, hoursToRecovery=0, hoursSinceLastSession=6, effectiveSets=8, status=FRESH)
                history[6][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[6][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=98, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=5, status=FRESH)
                history[24].energy=(97, 3, 0)
                history[24].spinal=100
                history[24][Pectorales]=MuscleRecoveryStatus(muscleName=Pectorales, recoveryScore=98, hoursToRecovery=0, hoursSinceLastSession=24, effectiveSets=8, status=FRESH)
                history[24][Cuádriceps]=MuscleRecoveryStatus(muscleName=Cuádriceps, recoveryScore=100, hoursToRecovery=0, hoursSinceLastSession=-1, effectiveSets=0, status=FRESH)
                history[24][Erectores Espinales]=MuscleRecoveryStatus(muscleName=Erectores Espinales, recoveryScore=99, hoursToRecovery=0, hoursSinceLastSession=42, effectiveSets=5, status=FRESH)
            """.trimIndent(),
        )
    }
}

internal object SessionDrainCharacterizationFixtures {
    val zone: ZoneId = ZoneId.of("UTC")
    val baseMs: Long = Instant.parse("2025-03-03T10:00:00Z").toEpochMilli()
    const val hourMs = 3_600_000L
    val settings = Settings(athleteType = AthleteType.POWERBUILDER, restTimerDefaultSeconds = 45)
    val cache = AugeAdaptiveCache(
        cnsDrainMultiplier = 1.11,
        spinalDrainMultiplier = 0.93,
        muscleDrainMultipliers = linkedMapOf("pectorales" to 1.07, "Pectorales" to 0.89, "Cuádriceps" to 1.13),
    )
    val db = AugeGoldenCorpus.EXERCISE_DB
    val muscles = listOf("Pectorales", "Cuádriceps", "Erectores Espinales")

    private fun sets(id: String, n: Int = 4, kg: Double = 80.0, reps: Int = 8, rpe: Double = 8.5) =
        List(n) { CompletedSet("$id-$it", weight = kg, reps = reps, rpe = rpe) }

    private fun exercise(id: String, series: List<CompletedSet> = sets(id), rest: Int = 150) = CompletedExercise(
        exerciseId = id, exerciseName = db.getValue(id).name, exerciseDbId = id, restTime = rest, sets = series,
    )

    private val cardio = CardioDetails(CardioType.ROW_MACHINE, targetDurationSeconds = 900, intensityLevel = 8)
    val completed = linkedMapOf(
        "unilateral" to listOf(
            exercise("lunge-uni", sets("lr", 8, 24.0).mapIndexed { i, s -> s.copy(side = if (i % 2 == 0) "left" else "right") }),
            exercise("squat", sets("sq", 2, 95.0)),
        ),
        "superserie" to listOf("bench", "row", "incline").map { id ->
            exercise(id, sets(id, 5, 65.0, 10), 180).copy(
                supersetId = "ss", supersetExerciseCount = 3, supersetRounds = 5,
                supersetRestBetween = 20, supersetRestAfter = 60,
                effectiveMuscles = if (id == "incline") listOf(InvolvedMuscle("Tríceps", MuscleRole.PRIMARY, 0.7)) else null,
            )
        },
        "cardio" to listOf(
            exercise("bench", sets("pre", 2)),
            CompletedExercise("row-cardio", "Remo cardio", cardioDetails = cardio, sets = listOf(
                CompletedSet("cardio-warmup", timeSeconds = 60, rpe = 5.0, isWarmup = true),
                CompletedSet("cardio-work", timeSeconds = 840, rpe = 8.5),
            )),
            exercise("squat", sets("post", 3, 120.0)),
        ),
        "tecnicas" to listOf(
            exercise("bench", sets("drop", 4, 75.0).map { it.copy(
                dropSets = listOf(DropSetData(55.0, 6), DropSetData(40.0, 5)),
                restPauses = listOf(RestPauseData(15, 3), RestPauseData(20, 2)),
                isFailure = true, isPartial = true, partialReps = 2, assistedReps = 1, debt = 1.25,
            ) }),
            exercise("row", sets("rp", 3, 60.0)),
        ),
        "axial" to listOf(
            exercise("deadlift", sets("single", 5, 205.0, 1, 9.5), 300),
            exercise("ohp", sets("ohp", 4, 65.0, 3, 9.5), 240),
            exercise("row", sets("heavy-row", 3, 100.0, 5, 9.0)),
        ),
        "sin-ficha" to listOf(
            CompletedExercise("custom", "Movimiento propio", sets = sets("custom", 4, 55.0)),
            CompletedExercise("hinge", "Peso muerto custom", sets = sets("hinge", 3, 110.0)),
            exercise("bench", sets("known", 2)),
        ),
        "filtrado-rir" to listOf(exercise("bench", listOf(
            CompletedSet("warmup", weight = 20.0, reps = 15, rpe = 8.0, isWarmup = true),
            CompletedSet("skipped", weight = 90.0, reps = 8, rpe = 9.0, skipped = true),
            CompletedSet("failed", weight = 120.0, reps = 0, rpe = 10.0, isFailedSet = true),
            CompletedSet("low", weight = 55.0, reps = 8, rpe = 5.9),
            CompletedSet("threshold", weight = 60.0, reps = 8, rpe = 6.0),
            CompletedSet("rir", weight = 80.0, reps = 8, rir = 1, actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 1.0),
            CompletedSet("time", weight = 30.0, timeSeconds = 35, rpe = 8.0),
        ), 60)),
    )

    val planned: Map<String, Session> = completed.mapValues { (id, exercises) ->
        Session(id = id, name = id, exercises = exercises.map { ex ->
            Exercise(
                id = ex.exerciseId, name = ex.exerciseName, exerciseDbId = ex.exerciseDbId,
                effectiveMuscles = ex.effectiveMuscles, restTime = if (id == "filtrado-rir") null else ex.restTime,
                supersetId = ex.supersetId, supersetGroupRef = if (id == "superserie") "group" else null,
                supersetRestBetween = ex.supersetRestBetween, supersetRestAfter = ex.supersetRestAfter,
                cardioDetails = ex.cardioDetails,
                trainingMode = if (id == "axial") TrainingMode.RM else TrainingMode.REPS,
                reference1RM = if (id == "axial") 220.0 else null,
                sets = ex.sets.map { s -> ExerciseSet(
                    id = s.id, targetReps = s.reps.takeIf { it > 0 }, targetDuration = s.timeSeconds,
                    targetRPE = s.rpe, targetRIR = s.rir, intensityMode = s.actualIntensityMode,
                    isFailure = s.isFailure, isIneffective = s.skipped || s.isWarmup || s.isFailedSet,
                    weight = if (id == "filtrado-rir") null else s.weight,
                    targetPercentageRM = if (id == "axial") 92.5 else null,
                    dropSets = s.dropSets, restPauses = s.restPauses,
                ) },
            )
        })
    }

    fun log(id: String, time: Long) = WorkoutLog(
        id = "$id-$time", programId = "p", sessionId = id, sessionName = id,
        date = Instant.ofEpochMilli(time).toString(), durationMinutes = if (id == "axial") 96 else 80,
        completedExercises = completed.getValue(id), mesoIndex = 0,
    )

    fun number(value: Double): String = String.format(Locale.ROOT, "%.6f", value) + "[${value.toBits()}]"
}
