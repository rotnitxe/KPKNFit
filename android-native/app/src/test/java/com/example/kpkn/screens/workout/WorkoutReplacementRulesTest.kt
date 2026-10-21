package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ConsolidatedWeight
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LV5, bug 3: reemplazar dos veces (A por B y luego B por C) no deja en C nada de A ni de B. El hueco solo pasa sus series planificadas
 * (cuántas, con sus repeticiones), el descanso y la intensidad objetivo; el modo de carga, las cargas y la carga inteligente son del ejercicio.
 */
class WorkoutReplacementRulesTest {

    /** El ejercicio A con TODA la configuración que un entrenamiento real puede dejarle. */
    private fun exerciseA(): Exercise = Exercise(
        id = "slot-1",
        name = "Dominadas lastradas",
        exerciseDbId = "pull_up__pronated__medium",
        exerciseId = "pull_up__pronated__medium",
        canonicalExerciseId = "pull_up__pronated__medium",
        catalogRevision = "rev-a",
        catalogDefinitionId = "pull_up",
        catalogConfigurationId = "pull_up__pronated__medium",
        sets = listOf(
            ExerciseSet(
                id = "s0",
                targetReps = 6,
                targetRepsRange = RepRange(5, 8),
                targetRPE = 8.0,
                targetRIR = 2,
                restAfterSeconds = 150,
                isTopSet = true,
                weight = 25.0,
                loadModeV2 = LoadModeV2.LASTRE,
                unitModeV2 = UnitModeV2.REPS,
                intensityMode = IntensityMode.RPE,
                targetPercentageRM = 82.5,
                plannedTargetV2 = 6.0,
                isDropSet = true,
                isRestPause = true,
                advancedTechnique = "dropset",
                dropSets = listOf(com.example.kpkn.data.models.DropSetData(weight = 20.0, reps = 5)),
                plannedIntensityTechniques = listOf(PlannedTechnique(id = "t", type = TechniqueType.DROP_SET)),
                tagId = "tag-a",
                setupId = "setup-a",
                machineBrand = "Marca A",
                loadBasis = LoadBasis.PERCENT_TM,
                loadQuantityConvention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT,
                manualLoadRequiredSides = setOf("left"),
            ),
            ExerciseSet(
                id = "s1",
                targetReps = 6,
                isAmrap = true,
                targetRPE = 9.0,
                weight = 27.5,
                loadModeV2 = LoadModeV2.LASTRE,
                restAfterSeconds = 120,
            ),
            ExerciseSet(
                id = "s2",
                targetDuration = 40,
                isFailure = true,
                weight = 30.0,
                loadModeV2 = LoadModeV2.LASTRE,
            ),
        ),
        restTime = 120,
        trainingMode = TrainingMode.RM,
        reference1RM = 140.0,
        prFor1RM = PrReference(weight = 25.0, reps = 6),
        consolidatedWeight = ConsolidatedWeight(weightKg = 30.0, reps = 5),
        goal1RM = 160.0,
        calculated1RM = 150.0,
        isStarTarget = true,
        isFavorite = true,
        customUnit = "brazadas",
        trackRom = true,
        trackHeartRate = true,
        isUnilateral = true,
        unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
        unilateralSideOrder = UnilateralSideOrder.RIGHT_LEFT,
        unilateralIntensityMode = UnilateralIntensityMode.INDEPENDENT,
        restBetweenSidesSeconds = 30,
        isCalibratorAmrap = true,
        isCompetitionLift = true,
        loadQuantityConvention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT,
        loadReference = PlanLoadReference(
            kind = PlanLoadReferenceKind.EXERCISE_1RM,
            configurationId = "pull_up__pronated__medium",
        ),
        nativeProgressionManaged = true,
        techniqueModifier = TechniqueModifier.PAUSE_2S,
        warmupSets = listOf(WarmupSetDefinition(id = "w0", percentageOfWorkingWeight = 0.5, targetReps = 5)),
        setupDetails = ExerciseSetupDetails(seatPosition = "3", baseLoadKg = 10.0),
        setupCues = listOf("cue de A"),
        selectedAspects = mapOf("grip" to "wide"),
        // Lo del hueco, que SÍ se queda.
        occurrenceId = "occ-1",
        supersetId = "g1",
        supersetGroupRef = "g1",
        supersetRestBetween = 45,
        supersetRestAfter = 90,
        recipeDayId = "day-1",
        recipeSlotId = "slot-r1",
        slotRole = SlotRole.T1_MAIN,
        mobilitySeries = listOf(MobilitySeries(id = "m1", name = "Colgado pasivo")),
    )

    private val infoB = ExerciseMuscleInfo(
        id = "bench_press__barbell",
        name = "Press de Banca Plano",
        equipment = "Barra",
        catalogRevision = "rev-b",
        catalogDefinitionId = "bench_press",
        catalogConfigurationId = "bench_press__barbell",
    )

    private val infoC = ExerciseMuscleInfo(
        id = "lat_pulldown__assisted",
        name = "Jalón asistido",
        equipment = "Máquina asistida",
        catalogRevision = "rev-c",
        catalogDefinitionId = "lat_pulldown",
        catalogConfigurationId = "lat_pulldown__assisted",
    )

    @Test
    fun elNuevoHeredaSoloLasSeriesPlanificadasElDescansoYLaIntensidadObjetivo() {
        val b = replacementExerciseFor(exerciseA(), infoB)

        // Identidad del ejercicio nuevo.
        assertEquals("slot-1", b.id)
        assertEquals("Press de Banca Plano", b.name)
        assertEquals("bench_press__barbell", b.catalogConfigurationId)
        assertEquals("rev-b", b.catalogRevision)

        // Series: cuántas, con sus repeticiones o tiempo y su intensidad objetivo; sin cargas ni modo ni técnicas.
        assertEquals(listOf("s0", "s1", "s2"), b.sets.map { it.id })
        val s0 = b.sets[0]
        assertEquals(6, s0.targetReps)
        assertEquals(RepRange(5, 8), s0.targetRepsRange)
        assertEquals(8.0, s0.targetRPE ?: 0.0, 0.0)
        assertEquals(2, s0.targetRIR)
        assertEquals(150, s0.restAfterSeconds)
        assertTrue(s0.isTopSet)
        assertEquals(
            "la serie queda como el hueco más el modo de carga del ejercicio nuevo",
            ExerciseSet(
                id = "s0",
                targetReps = 6,
                targetRepsRange = RepRange(5, 8),
                targetRPE = 8.0,
                targetRIR = 2,
                restAfterSeconds = 150,
                isTopSet = true,
                loadModeV2 = LoadModeV2.LOAD,
                unitModeV2 = UnitModeV2.REPS,
            ),
            s0,
        )
        assertTrue(b.sets[1].isAmrap)
        assertEquals(120, b.sets[1].restAfterSeconds)
        assertTrue(b.sets[2].isFailure)
        assertEquals(40, b.sets[2].targetDuration)
        assertEquals(UnitModeV2.TIME, b.sets[2].unitModeV2)

        // Descanso del ejercicio y pertenencia al hueco.
        assertEquals(120, b.restTime)
        assertEquals("occ-1", b.occurrenceId)
        assertEquals("g1", b.supersetGroupRef)
        assertEquals(45, b.supersetRestBetween)
        assertEquals(90, b.supersetRestAfter)
        assertEquals("day-1", b.recipeDayId)
        assertEquals("slot-r1", b.recipeSlotId)
        assertEquals(SlotRole.T1_MAIN, b.slotRole)
        assertEquals(listOf("m1"), b.mobilitySeries.map { it.id })
    }

    @Test
    fun laConfiguracionDelEjercicioQueSeVaNoPasaAlNuevo() {
        val b = replacementExerciseFor(exerciseA(), infoB)

        // Carga inteligente: referencia, PR, meta y referencia de carga del plan.
        assertNull(b.reference1RM)
        assertNull(b.prFor1RM)
        assertNull(b.consolidatedWeight)
        assertNull(b.goal1RM)
        assertNull(b.calculated1RM)
        assertNull(b.loadReference)
        assertEquals(LoadQuantityConvention.UNSPECIFIED, b.loadQuantityConvention)
        assertFalse(b.nativeProgressionManaged)
        // Modo, unidad y seguimiento.
        assertEquals(TrainingMode.REPS, b.trainingMode)
        assertNull(b.customUnit)
        assertFalse(b.trackRom)
        assertFalse(b.trackHeartRate)
        assertFalse(b.isFavorite)
        assertFalse(b.isStarTarget)
        assertNull(b.techniqueModifier)
        // Lateralidad.
        assertFalse(b.isUnilateral)
        assertEquals(UnilateralMode.BILATERAL, b.unilateralMode)
        assertEquals(UnilateralSideOrder.LEFT_RIGHT, b.unilateralSideOrder)
        assertEquals(UnilateralIntensityMode.SHARED, b.unilateralIntensityMode)
        assertNull(b.restBetweenSidesSeconds)
        assertFalse(b.isCalibratorAmrap)
        assertFalse(b.isCompetitionLift)
        // Calentamiento, preparación del puesto y variante.
        assertTrue(b.warmupSets.isEmpty())
        assertNull(b.setupDetails)
        assertNull(b.selectedAspects)
        assertFalse(b.setupCues.contains("cue de A"))
        // Series: nada de cargas, técnicas ni etiquetas.
        b.sets.forEach { set ->
            assertNull(set.weight)
            assertNull(set.targetPercentageRM)
            assertNull(set.intensityMode)
            assertNull(set.loadBasis)
            assertEquals(LoadQuantityConvention.UNSPECIFIED, set.loadQuantityConvention)
            assertFalse(set.isDropSet)
            assertFalse(set.isRestPause)
            assertTrue(set.dropSets.isEmpty())
            assertTrue(set.plannedIntensityTechniques.isEmpty())
            assertNull(set.advancedTechnique)
            assertNull(set.tagId)
            assertNull(set.setupId)
            assertNull(set.machineBrand)
            assertTrue(set.manualLoadRequiredSides.isEmpty())
        }
    }

    @Test
    fun reemplazarBPorCNoDejaEnCNadaDeANiDeB() {
        val a = exerciseA()
        val b = replacementExerciseFor(a, infoB)
        // La persona edita B en la sesión: más series, otro modo de carga, cargas y carga inteligente propias.
        val editedB = b.copy(
            sets = b.sets.mapIndexed { index, set ->
                set.copy(
                    weight = 80.0 + index,
                    loadModeV2 = LoadModeV2.LASTRE,
                    targetPercentageRM = 70.0,
                    isDropSet = true,
                    tagId = "tag-b",
                )
            } + ExerciseSet(id = "s3", targetReps = 12, weight = 60.0, loadModeV2 = LoadModeV2.LASTRE),
            reference1RM = 200.0,
            prFor1RM = PrReference(weight = 100.0, reps = 5),
            loadReference = PlanLoadReference(PlanLoadReferenceKind.EXERCISE_TM, "bench_press__barbell"),
            customUnit = "reps lentas",
            trackRom = true,
            restTime = 75,
        )

        val c = replacementExerciseFor(editedB, infoC)

        assertEquals("lat_pulldown__assisted", c.catalogConfigurationId)
        // Del hueco de B: las 4 series (cuántas), el descanso que B tenía y sus repeticiones objetivo.
        assertEquals(listOf("s0", "s1", "s2", "s3"), c.sets.map { it.id })
        assertEquals(75, c.restTime)
        assertEquals(12, c.sets[3].targetReps)
        // El modo de carga sale del equipo de C (asistido), no del de A (lastre) ni del de B (lastre editado).
        assertTrue(c.sets.all { it.loadModeV2 == LoadModeV2.ASSISTED })
        // Ni cargas ni carga inteligente de A o de B.
        assertTrue(c.sets.all { it.weight == null && it.targetPercentageRM == null && !it.isDropSet && it.tagId == null })
        assertNull(c.reference1RM)
        assertNull(c.prFor1RM)
        assertNull(c.loadReference)
        assertNull(c.customUnit)
        assertFalse(c.trackRom)
    }

    @Test
    fun unEjercicioSinSeriesRecibeUnaSerieNuevaConElModoDelEquipo() {
        val empty = exerciseA().copy(sets = emptyList())

        val b = replacementExerciseFor(empty, infoB)

        assertEquals(1, b.sets.size)
        assertEquals(LoadModeV2.LOAD, b.sets.single().loadModeV2)
        assertEquals(UnitModeV2.REPS, b.sets.single().unitModeV2)
    }

    @Test
    fun elEstadoEnVivoDelEjercicioQueSeVaSeOlvidaYElDeLosDemasSeQueda() {
        val state = WorkoutUiState(
            persistedLoadModeBySet = mapOf(
                "slot-1_0" to LoadModeV2.LASTRE,
                "slot-1_1_L" to LoadModeV2.LASTRE,
                "other_0" to LoadModeV2.BODYWEIGHT,
            ),
            persistedLoadModeByExercise = mapOf(
                "slot-1" to LoadModeV2.LASTRE,
                "slot-1|na" to LoadModeV2.LASTRE,
                "other" to LoadModeV2.BODYWEIGHT,
            ),
            loadSuggestionPulseTokens = mapOf("slot-1_0" to 5L, "other_0" to 6L),
        )

        val cleaned = state.withoutLoadStateOfExercise("slot-1")

        assertEquals(mapOf("other_0" to LoadModeV2.BODYWEIGHT), cleaned.persistedLoadModeBySet)
        assertEquals(mapOf("other" to LoadModeV2.BODYWEIGHT), cleaned.persistedLoadModeByExercise)
        assertEquals(mapOf("other_0" to 6L), cleaned.loadSuggestionPulseTokens)
    }

    @Test
    fun elModoDeCargaGuardadoDeAnoPisaAlDelEjercicioNuevoSiNoSeLimpia() {
        // Lo que pasaba: el id del hueco no cambia al reemplazar, y el modo con el que se registró A mandaba sobre el plan de B.
        val state = WorkoutUiState(
            persistedLoadModeBySet = mapOf("slot-1_0" to LoadModeV2.LASTRE),
            persistedLoadModeByExercise = mapOf("slot-1" to LoadModeV2.LASTRE),
        )
        val plannedB = LoadModeV2.LOAD

        fun effective(of: WorkoutUiState): LoadModeV2 = resolveEffectiveLoadMode(
            draftLoadMode = null,
            persistedLoadMode = resolvePersistedLoadModeForSet(
                exerciseId = "slot-1",
                setIdx = 0,
                tagId = null,
                persistedLoadModeBySet = of.persistedLoadModeBySet,
                persistedLoadModeByExercise = of.persistedLoadModeByExercise,
            ),
            plannedLoadMode = plannedB,
            defaultCatalogMode = null,
        )

        assertEquals("sin limpiar manda el modo viejo", LoadModeV2.LASTRE, effective(state))
        assertEquals("limpio manda el plan del ejercicio nuevo", plannedB, effective(state.withoutLoadStateOfExercise("slot-1")))
    }

    @Test
    fun laCargaSugeridaDelNuevoSaleDeSuPropioHistorialYNuncaDelAnterior() {
        val a = exerciseA()
        val b = replacementExerciseFor(a, infoB)

        fun log(exercise: Exercise, weight: Double, reps: Int) = WorkoutLog(
            id = "log-${exercise.resolvedCanonicalExerciseId()}",
            programId = "programa",
            sessionId = "sesion",
            sessionName = "Sesión",
            date = "2026-10-01T10:00:00.000Z",
            durationMinutes = 60,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    canonicalExerciseId = exercise.resolvedCanonicalExerciseId(),
                    sets = listOf(CompletedSet(id = "c", weight = weight, reps = reps)),
                ),
            ),
        )
        // El press de banca tiene su historial (100 kg x 5) y las dominadas el suyo (40 kg x 5): el hueco es el mismo, los ejercicios no.
        val ownHistory = log(b, weight = 100.0, reps = 5)
        val previousHistory = log(a, weight = 40.0, reps = 5)

        val fromOwnHistory = calculateSuggestedLoad(b, b.sets.first(), listOf(previousHistory, ownHistory))
        val withoutOwnHistory = calculateSuggestedLoad(b, b.sets.first(), listOf(previousHistory))

        assertNotNull("el nuevo sugiere carga con su historial", fromOwnHistory)
        assertTrue("sale de los 100 kg del press, no de los 40 de las dominadas ($fromOwnHistory)", (fromOwnHistory ?: 0.0) in 60.0..120.0)
        assertNull("sin historial propio no sugiere nada con el del anterior", withoutOwnHistory)
    }
}
