package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionDrainRecipeTest {
    private val fixtures = SessionDrainCharacterizationFixtures

    private fun options() = RecipeOptions(
        volumeCounting = RecipeOptions.VolumeCounting.EFFECTIVE_SET_COUNT,
        axialGatePosition = RecipeOptions.AxialGatePosition.NOT_APPLIED,
        muscleMultiplierSource = RecipeOptions.MuscleMultiplierSource.NONE,
        cardioHandling = RecipeOptions.CardioHandling.CONSUMER_HOOK,
        softCapUnits = RecipeOptions.SoftCapUnits.CHANNEL_PERCENT,
    )

    private fun recipe(
        choices: RecipeOptions,
        settings: Settings = fixtures.settings,
        cache: AugeAdaptiveCache = fixtures.cache,
        db: Map<String, ExerciseMuscleInfo> = fixtures.db,
    ) = SessionDrainRecipe(
        settings, AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings), cache, choices,
        resolveInfo = { identity -> db[identity.exerciseDbId] ?: db.values.firstOrNull { it.name == identity.exerciseName } },
    )

    private fun exercise(sets: List<CompletedSet> = listOf(CompletedSet("set", weight = 40.0, reps = 4, rpe = 7.0))) =
        CompletedExercise("bench", "Press banca", exerciseDbId = "bench", sets = sets, restTime = 180)

    private fun plannedExercise() = Exercise(
        id = "plan", name = "Press banca", exerciseDbId = "bench",
        sets = listOf(ExerciseSet("target", targetReps = 4, targetRPE = 7.0, weight = 40.0)),
    )

    private data class Seen(
        val context: RecipeExerciseContext,
        val raw: SetDrain,
        val adjusted: AdjustedSetDrain,
        val count: Int,
    )

    private fun completed(
        choices: RecipeOptions,
        exercises: List<CompletedExercise> = listOf(exercise()),
        settings: Settings = fixtures.settings,
        cache: AugeAdaptiveCache = fixtures.cache,
        db: Map<String, ExerciseMuscleInfo> = fixtures.db,
    ): List<Seen> {
        val seen = mutableListOf<Seen>()
        recipe(choices, settings, cache, db).forEachCompleted(
            exercises, onCardio = {},
            onSet = { _, _, context, raw, adjusted, count -> seen += Seen(context, raw, adjusted, count) },
        )
        return seen
    }

    private fun planned(choices: RecipeOptions, exercise: Exercise): Seen {
        val seen = mutableListOf<Seen>()
        recipe(choices).forEachPlanned(
            listOf(exercise), onCardio = {},
            onSet = { _, _, context, raw, adjusted, count -> seen += Seen(context, raw, adjusted, count) },
        )
        return seen.single()
    }

    @Test fun conservacionSinAcumuladoSeCalculaAMano() {
        val result = SessionDrainRecipe.adjustSet(
            SetDrain(muscularDrainPct = 10.0, cnsDrainPct = 20.0, spinalDrainPct = 30.0), 0.0, options(), 1.0,
        )
        assertEquals(8.5, result.muscular, 0.0)
        assertEquals(17.0, result.cns, 0.0)
        assertEquals(25.5, result.spinal, 0.0)
        assertEquals(17.0, result.accumulatedDrain, 0.0)
    }

    @Test fun rendimientosYPromedioDeTresCanalesSeCalculanAMano() {
        val result = SessionDrainRecipe.adjustSet(
            SetDrain(muscularDrainPct = 10.0, cnsDrainPct = 20.0, spinalDrainPct = 30.0), 100.0, options(), 1.0,
        )
        assertEquals(5.151515151515152, result.muscular, 1e-14)
        assertEquals(10.303030303030304, result.cns, 1e-14)
        assertEquals(15.454545454545455, result.spinal, 1e-14)
        assertEquals(110.3030303030303, result.accumulatedDrain, 1e-13)
    }

    @Test fun descansoCrudoEsDelEjercicioYElDefaultPlanificadoEsDelUsuario() {
        val ex = exercise().copy(supersetId = "ss", supersetRestBetween = 20)
        val actual = completed(options(), listOf(ex)).single()
        assertEquals(180, actual.context.restTime)
        assertEquals(1.3, actual.context.densityMultiplier, 0.0)
        assertEquals(45, planned(options(), plannedExercise()).context.restTime)
        assertEquals(180, planned(options(), plannedExercise().copy(restTime = 180)).context.restTime)
        val target = planned(options(), plannedExercise().copy(supersetGroupRef = "group"))
        assertEquals(1.3, target.context.densityMultiplier, 0.0)
    }

    @Test fun conteoUnilateralUsaMitadesRedondeadasOEnteros() {
        val ex = exercise(List(4) { i ->
            CompletedSet("lr-$i", weight = 24.0, reps = 4, rpe = 7.0, side = if (i % 2 == 0) "left" else "right")
        })
        val integer = completed(options(), listOf(ex))
        val halves = completed(options().copy(volumeCounting = RecipeOptions.VolumeCounting.UNILATERAL_HALVES_ROUNDED), listOf(ex))
        assertEquals(listOf(1, 2, 3, 4), integer.map { it.count })
        assertEquals(listOf(1, 1, 2, 2), halves.map { it.count })
        assertNotEquals(integer[1].raw, halves[1].raw)
    }

    @Test fun puertaAxialCambiaElPromedioSoloSiSeAplicaAntesDeAcumular() {
        val raw = SetDrain(muscularDrainPct = 10.0, cnsDrainPct = 20.0, spinalDrainPct = 30.0)
        val beforeOptions = options().copy(axialGatePosition = RecipeOptions.AxialGatePosition.BEFORE_ACCUMULATION)
        val afterOptions = options().copy(axialGatePosition = RecipeOptions.AxialGatePosition.AFTER_ACCUMULATION)
        val before = SessionDrainRecipe.adjustSet(raw, 0.0, beforeOptions, 0.4)
        val after = SessionDrainRecipe.adjustSet(raw, 0.0, afterOptions, 0.4)
        val none = SessionDrainRecipe.adjustSet(raw, 0.0, options(), 0.4)
        assertEquals(10.2, before.spinal, 1e-14)
        assertEquals(11.9, before.accumulatedDrain, 1e-14)
        assertEquals(25.5, after.spinal, 0.0)
        assertEquals(17.0, after.accumulatedDrain, 0.0)
        assertEquals(after, none)

        val db = fixtures.db + ("bench" to fixtures.db.getValue("bench").copy(axialLoadFactor = 0.4))
        val beforeContext = completed(beforeOptions, db = db).single().context
        val afterContext = completed(afterOptions, db = db).single().context
        val noneContext = completed(options(), db = db).single().context
        assertEquals(1.0, beforeContext.postAccumulationAxialGate, 0.0)
        assertEquals(0.4, afterContext.postAccumulationAxialGate, 0.0)
        assertEquals(1.0, noneContext.postAccumulationAxialGate, 0.0)
        assertEquals(12.24, after.spinal * 1.2 * afterContext.postAccumulationAxialGate, 1e-14)
    }

    @Test fun multiplicadoresConservanClaveDirectaPromedioRemapeadoONinguno() {
        val cache = fixtures.cache.copy(muscleDrainMultipliers = linkedMapOf("pectorales" to 1.4, "Pectorales" to 0.8))
        val direct = completed(options().copy(muscleMultiplierSource = RecipeOptions.MuscleMultiplierSource.DIRECT_KEYS), cache = cache).single()
        val remapped = completed(options().copy(muscleMultiplierSource = RecipeOptions.MuscleMultiplierSource.REMAPPED_PILLARS), cache = cache).single()
        val none = completed(options(), cache = cache).single()
        assertEquals(1.4, direct.context.muscleMultiplier, 0.0)
        assertEquals(1.1, remapped.context.muscleMultiplier, 1e-15)
        assertEquals(1.0, none.context.muscleMultiplier, 0.0)
        assertTrue(direct.raw.muscularDrainPct > remapped.raw.muscularDrainPct)
        assertTrue(remapped.raw.muscularDrainPct > none.raw.muscularDrainPct)
    }

    @Test fun migracionV2YV3ConservaMultiplicadoresMuscularesNoVacios() {
        listOf(2, 3).forEach { version ->
            val migrated = AugeAdaptiveCacheMigration.migrate(fixtures.cache, version)
            assertEquals(fixtures.cache.muscleDrainMultipliers, migrated.muscleDrainMultipliers)
            assertEquals(1.07, migrated.muscleDrainMultipliers.getValue("pectorales"), 0.0)
        }
    }

    @Test fun densidadUsaGrupoOIdHeredadoEnPlanesYMetadatosCompletosAlCerrar() {
        val ex = exercise().copy(
            supersetId = "ss", supersetExerciseCount = 3, supersetRounds = 5,
            supersetRestBetween = 20, supersetRestAfter = 60,
        )
        val full = completed(options(), listOf(ex)).single()
        assertEquals(1.45, full.context.densityMultiplier, 0.0)
        val target = plannedExercise().copy(supersetGroupRef = "group", restTime = 20)
        val group = planned(options(), target)
        val legacy = planned(options(), target.copy(supersetGroupRef = null, supersetId = "ss"))
        assertEquals(1.3, group.context.densityMultiplier, 0.0)
        assertEquals(group.raw, legacy.raw)
        assertEquals(1.0, planned(options(), target.copy(supersetGroupRef = null)).context.densityMultiplier, 0.0)
    }

    @Test fun pilarDelSnapshotSeparaLosContadoresAunqueCoincidaElCatalogo() {
        val first = exercise().copy(effectiveMuscles = listOf(InvolvedMuscle("Tríceps", MuscleRole.PRIMARY, 1.0)))
        val inputs = listOf(first, exercise())
        val snapshot = completed(options(), inputs)
        assertEquals("Tríceps", snapshot.first().context.primaryMuscle)
        assertEquals(listOf(1, 1), snapshot.map { it.count })
    }

    @Test fun fichaAusenteSiempreUsaHeuristicaYCuentaUnaSerie() {
        val ex = exercise().copy(exerciseId = "unknown", exerciseDbId = null, exerciseName = "Movimiento propio")
        val heuristic = completed(options(), listOf(ex))
        assertEquals(1, heuristic.size)
        assertTrue(heuristic.single().raw.muscularDrainPct > 0.0)
        assertEquals(1, heuristic.single().count)
        assertEquals(2.5, heuristic.single().context.metrics.efc, 0.0)
    }

    @Test fun cardioSeDelegaAlConsumidorOSeProcesaComoFuerzaLegacy() {
        val ex = exercise(listOf(CompletedSet("time", timeSeconds = 60, rpe = 8.0))).copy(
            cardioDetails = CardioDetails(CardioType.ROW_MACHINE, targetDurationSeconds = 60),
        )
        fun visits(handling: RecipeOptions.CardioHandling): Pair<Int, Int> {
            var cardio = 0
            var strength = 0
            recipe(options().copy(cardioHandling = handling)).forEachCompleted(
                listOf(ex), onCardio = { cardio++ }, onSet = { _, _, _, _, _, _ -> strength++ },
            )
            return cardio to strength
        }
        assertEquals(PredictedDrain(cns = 10, muscular = 14, spinal = 8), AugeFatigueEngine.DEFAULT_FALLBACK_PREDICTED_DRAIN)
        assertEquals(1 to 0, visits(RecipeOptions.CardioHandling.CONSUMER_HOOK))
        assertEquals(0 to 1, visits(RecipeOptions.CardioHandling.LEGACY_STRENGTH))
    }

    @Test fun topesSeExpresanEnLaUnidadDelConsumidorOSonAusentes() {
        assertEquals(10.0, recipe(options().copy(softCapUnits = RecipeOptions.SoftCapUnits.NONE)).softCap(10.0, 20.0, 100.0), 0.0)
        assertEquals(9.0, recipe(options()).softCap(10.0, 20.0, 100.0), 0.0)
        assertEquals(9.0, recipe(options().copy(softCapUnits = RecipeOptions.SoftCapUnits.LOG_PERCENT)).softCap(10.0, 20.0, 100.0), 0.0)
        assertEquals(90.0, recipe(options().copy(softCapUnits = RecipeOptions.SoftCapUnits.MUSCLE_STRESS_UNITS)).softCap(100.0, 200.0, 1000.0), 0.0)
    }

    @Test fun unidadDePesoDelUsuarioNoCambiaElDrenajeCrudo() {
        val kilos = completed(options(), settings = fixtures.settings.copy(weightUnit = WeightUnit.KG)).single()
        val libras = completed(options(), settings = fixtures.settings.copy(weightUnit = WeightUnit.LBS)).single()
        assertEquals(kilos.raw, libras.raw)
        assertEquals(kilos.adjusted, libras.adjusted)
    }

    @Test fun identidadSinteticaSeConservaSinCambiarLaCarga() {
        val ex = plannedExercise()
        val set = ex.sets.single()
        val preserved = SessionDrainRecipe.plannedSet(ex, set)
        assertEquals(set.id, preserved.id)
        val metrics = completed(options()).single().context.metrics
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(fixtures.settings)
        val emptyDrain = AugeFatigueEngine.calculateSetBatteryDrain(preserved.copy(id = ""), metrics, tanks)
        val preservedDrain = AugeFatigueEngine.calculateSetBatteryDrain(preserved, metrics, tanks)
        assertEquals(emptyDrain, preservedDrain)
    }

    @Test fun adaptadorConservaSesentaKilosOchoRepsRirYFalloSinAnadirTecnicas() {
        val set = ExerciseSet(
            "default", targetRIR = 3, intensityMode = IntensityMode.RIR,
            targetDuration = 60, dropSets = listOf(DropSetData(30.0, 5)), restPauses = listOf(RestPauseData(15, 2)),
        )
        val actual = SessionDrainRecipe.plannedSet(plannedExercise(), set)
        assertEquals(60.0, actual.weight, 0.0)
        assertEquals(8, actual.reps)
        assertEquals(3, requireNotNull(actual.rir))
        assertEquals(3.0, requireNotNull(actual.actualIntensityValue), 0.0)
        assertTrue(actual.timeSeconds == null)
        assertTrue(actual.dropSets.isEmpty() && actual.restPauses.isEmpty())
        val failure = SessionDrainRecipe.plannedSet(plannedExercise(), set.copy(intensityMode = IntensityMode.FAILURE))
        assertTrue(failure.isFailure)
    }

    @Test fun adaptadorRmUsaPorcentajeYReferenciaSoloCuandoCorresponde() {
        val ex = plannedExercise().copy(trainingMode = TrainingMode.RM, reference1RM = 200.0)
        val set = ex.sets.single().copy(targetPercentageRM = 75.0, weight = 20.0)
        fun weight(exercise: Exercise) = SessionDrainRecipe.plannedSet(exercise, set).weight
        assertEquals(150.0, weight(ex), 0.0)
        assertEquals(20.0, weight(ex.copy(reference1RM = 0.0)), 0.0)
        assertEquals(20.0, weight(ex.copy(trainingMode = TrainingMode.REPS)), 0.0)
    }

    @Test fun singlePesadoSumaQuincePorCientoSoloAlNeuralAntesDelTope() {
        val base = AdjustedSetDrain(8.5, 17.0, 25.5, 17.0)
        val single = CompletedSet("single", weight = 100.0, reps = 1, rpe = 9.5)
        val actual = SessionDrainRecipe.withHeavySingleBonus(base, single)
        assertEquals(19.55, actual.cns, 1e-14)
        assertEquals(base.accumulatedDrain, actual.accumulatedDrain, 0.0)
        assertEquals(base.muscular, actual.muscular, 0.0)
        assertEquals(base.spinal, actual.spinal, 0.0)
        assertEquals(actual, SessionDrainRecipe.withHeavySingleBonus(base, single.copy(reps = 3)))
        assertEquals(base, SessionDrainRecipe.withHeavySingleBonus(base, single.copy(reps = 4)))
        assertEquals(base, SessionDrainRecipe.withHeavySingleBonus(base, single.copy(weight = 0.0)))
        assertEquals(base, SessionDrainRecipe.withHeavySingleBonus(base, single.copy(rpe = 9.4)))
        assertEquals(actual, SessionDrainRecipe.withHeavySingleBonus(base, single.copy(rpe = null, rir = 0)))
    }

    @Test fun bonosPorDuracionRespetanLosUmbrales75Y90() {
        assertEquals(10.0, SessionDrainRecipe.durationCns(10.0, 75), 0.0)
        assertEquals(10.8, SessionDrainRecipe.durationCns(10.0, 76), 1e-14)
        assertEquals(10.8, SessionDrainRecipe.durationCns(10.0, 90), 1e-14)
        assertEquals(11.5, SessionDrainRecipe.durationCns(10.0, 91), 1e-14)
        assertEquals(10.0, SessionDrainRecipe.durationSpinal(10.0, 90), 0.0)
        assertEquals(10.8, SessionDrainRecipe.durationSpinal(10.0, 91), 1e-14)
    }

    @Test fun constantesDeLaRecetaSoloSeConsumenEnElArchivoCompartido() {
        val relative = "src/main/java/com/example/kpkn/domain/auge"
        val root = listOf(File(relative), File("app/" + relative), File("android-native/app/" + relative)).first { it.isDirectory }
        val sources = root.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(sources.size >= 20)
        val reference = Regex("""AugeUtils\.SESSION_(?:CONSERVATION_FACTOR|DECAY_K)""")
        val consumers = sources.filter { reference.containsMatchIn(it.readText(Charsets.UTF_8)) }.map { it.name }
        assertEquals(listOf("SessionDrainRecipe.kt"), consumers)
    }
}
