package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MobilityExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLiveRelatorTest {

    @Test
    fun lastWarmupIdleBeatsWorkingFirstSet() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.WARMUP,
                setIndex = 0,
                hasHistory = true,
                warmupIsLastIncomplete = true,
                warmupCount = 3,
                warmupIncompleteIndex = 2,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(
            lower.contains("efectivas") ||
                lower.contains("snc") ||
                lower.contains("aprox") ||
                lower.contains("listo") ||
                lower.contains("lista") ||
                lower.contains("patrón") ||
                lower.contains("cargas") ||
                lower.contains("encendido") ||
                lower.contains("preparado") ||
                lower.contains("preparada"),
        )
        assertFalse(line.contains("primera serie"))
    }

    @Test
    fun mobilityPressMentionsCuffOrShoulderAndExercise() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.MOBILITY,
                family = RelatorFamily.PRESS,
                exerciseDisplayName = "Press banca",
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("manguito") || lower.contains("hombro") || lower.contains("movilidad"))
    }

    @Test
    fun mobilityCheckDoesNotReplaceIdle() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.MOBILITY,
                family = RelatorFamily.PRESS,
                lastChangedField = RelatorChangedField.MOBILITY_CHECK,
                mobilityCompleted = 1,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("marcado"))
        assertFalse(lower.contains("ítem"))
        assertTrue(lower.contains("movilidad") || lower.contains("hombro") || lower.contains("manguito"))
    }

    @Test
    fun firstWorkingSetUsesHistoryTeaseWhenAvailable() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.WORKING,
                setIndex = 0,
                setCount = 4,
                hasHistory = true,
                lastLiftedWeight = 80.0,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("serie 1"))
        assertTrue(lower.contains("80"))
        assertTrue(lower.contains("press"))
    }

    @Test
    fun firstWorkingSetWithoutHistoryDoesNotAskLastTime() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(phase = RelatorPhase.WORKING, setIndex = 0, hasHistory = false, lastLiftedWeight = null),
        ).text
        assertNotNull(line)
        assertTrue(line!!.lowercase().contains("serie 1"))
        assertFalse(line.contains("última vez"))
    }

    @Test
    fun programmedRpeDoesNotSpeakUntilUserChangesIt() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.NONE,
                enteredIntensity = 7.0,
                plannedIntensity = 8.0,
                intensityMode = IntensityMode.RPE,
                lastLiftedWeight = 80.0,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("serie 1"))
        assertFalse(lower.contains("piso"))
        assertFalse(lower.contains("lujo"))
        assertFalse(lower.contains("gesto"))
        assertFalse(lower.contains("rpe 7"))
    }

    @Test
    fun sessionMemoryUsesPreviousSetMarkNotOnlyKg() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                setIndex = 1,
                setCount = 4,
                lastLiftedWeight = 80.0,
                sessionLastSet = RelatorSessionSetMemory(setNumber = 1, weightKg = 80.0, reps = 8),
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("serie 2"))
        assertTrue(lower.contains("en la 1"))
        assertTrue(lower.contains("80"))
        assertTrue(lower.contains("8"))
    }

    @Test
    fun historyMemorySurvivesWithoutSessionIdBySetMark() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                setIndex = 0,
                historyLastSet = RelatorSessionSetMemory(setNumber = 1, weightKg = 92.5, reps = 5),
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("serie 1"))
        assertTrue(lower.contains("última vez") || lower.contains("ultima vez"))
        assertTrue(lower.contains("92.5") || lower.contains("92,5"))
        assertTrue(lower.contains("5"))
    }

    @Test
    fun programmedPrDraftDoesNotSpeakUntilUserChangesLoad() {
        val pr = RelatorPrHint(estimatedRmKg = 120.0, isStar = false)
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.NONE,
                enteredWeight = 110.0,
                enteredWeightRaw = "110",
                enteredReps = 3.0,
                prHint = pr,
                lastLiftedWeight = 80.0,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("serie 1"))
        assertFalse(lower.contains("nuevo pr"))
    }

    @Test
    fun userLoadChangeSpeaksPrWithEstimatedRm() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.WEIGHT,
                enteredWeight = 110.0,
                enteredWeightRaw = "110",
                enteredReps = 3.0,
                prHint = RelatorPrHint(estimatedRmKg = 120.0, isStar = false),
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("nuevo pr"))
        assertTrue(lower.contains("120"))
        assertFalse(lower.contains("meta"))
    }

    @Test
    fun starPrMentionsGoalPercent() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.REPS,
                enteredWeight = 100.0,
                enteredWeightRaw = "100",
                enteredReps = 5.0,
                prHint = RelatorPrHint(
                    estimatedRmKg = 116.0,
                    isStar = true,
                    goal1RmKg = 140.0,
                    goalPct = 83,
                ),
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("nuevo pr"))
        assertTrue(lower.contains("116"))
        assertTrue(lower.contains("83%"))
        assertTrue(lower.contains("meta"))
    }

    @Test
    fun restAfterPrAnnouncesImmediately() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.REST,
                lastChangedField = RelatorChangedField.NONE,
                prHint = RelatorPrHint(estimatedRmKg = 102.0, isStar = false),
                sessionLastSet = RelatorSessionSetMemory(setNumber = 1, weightKg = 90.0, reps = 5),
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("nuevo pr"))
        assertTrue(lower.contains("102"))
    }

    @Test
    fun discomfortWaitsForIdleCycle() {
        val hint = RelatorDiscomfortHint(label = "Hombro anterior", fromThisSession = false)
        val first = WorkoutLiveRelator.resolve(
            baseSnapshot(discomfortHint = hint, lastLiftedWeight = 80.0),
        ).text!!
        assertTrue(first.lowercase().contains("serie 1"))
        assertFalse(first.lowercase().contains("hombro"))
        val later = WorkoutLiveRelator.resolve(
            baseSnapshot(discomfortHint = hint, idleCycle = 1, lastLiftedWeight = 80.0),
        ).text!!
        assertTrue(later.lowercase().contains("hombro anterior"))
        assertTrue(later.lowercase().contains("última vez") || later.lowercase().contains("ultima vez"))
    }

    @Test
    fun supersetSituateNamesRoundAndExercise() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                exerciseDisplayName = "Press banca",
                setIndex = 1,
                setCount = 3,
                isSuperset = true,
                lastLiftedWeight = 70.0,
                sessionLastSet = RelatorSessionSetMemory(setNumber = 1, weightKg = 70.0, reps = 8),
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("superserie"))
        assertTrue(lower.contains("ronda 2"))
        assertTrue(lower.contains("press"))
        assertTrue(lower.contains("70"))
    }

    @Test
    fun conservativeWeightUsesMasculineByDefault() {
        val line = WorkoutLiveRelator.resolve(
            weightSnapshot(entered = 70.0, lastLifted = 80.0, suggested = 80.0, feminine = false),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(isWeightBelowCopy(lower))
        assertFalse(lower.contains("conservadora"))
    }

    @Test
    fun conservativeWeightUsesFeminineWhenProfileIsFemale() {
        val line = WorkoutLiveRelator.resolve(
            weightSnapshot(entered = 70.0, lastLifted = 80.0, suggested = 80.0, feminine = true),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(isWeightBelowCopy(lower))
        if (lower.contains("conservador")) {
            assertTrue(lower.contains("conservadora"))
        }
        assertFalse(lower.contains("fuiste conservador;"))
    }

    @Test
    fun intensityChangeDoesNotUseWeightCopy() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.WORKING,
                lastChangedField = RelatorChangedField.INTENSITY,
                enteredIntensity = 7.0,
                plannedIntensity = 8.0,
                enteredWeight = 90.0,
                enteredWeightRaw = "90",
                suggestedWeight = 80.0,
                lastLiftedWeight = 80.0,
                referenceWeight = 80.0,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("kg"))
        assertFalse(lower.contains("holgado"))
        assertTrue(
            lower.contains("rpe") ||
                lower.contains("control") ||
                lower.contains("margen") ||
                lower.contains("calidad") ||
                lower.contains("esfuerzo") ||
                lower.contains("trabajo") ||
                lower.contains("mesurad") ||
                lower.contains("contenido") ||
                lower.contains("limpio") ||
                lower.contains("técnica") ||
                lower.contains("patrón") ||
                lower.contains("liviano") ||
                lower.contains("moderado") ||
                lower.contains("cómodo") ||
                lower.contains("suave") ||
                lower.contains("calmo") ||
                lower.contains("puente"),
        )
    }

    @Test
    fun rpeFiveIsIneffectiveNotHolgado() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.INTENSITY,
                enteredIntensity = 5.0,
                plannedIntensity = 8.0,
                intensityMode = IntensityMode.RPE,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("holgado"))
        assertFalse(lower.contains("inefectiv"))
        assertFalse(lower.contains("no estimula"))
        assertFalse(lower.contains("no hay reclutamiento"))
        assertFalse(lower.contains("no sirve"))
        assertFalse(lower.contains("piso"))
        assertFalse(lower.contains("lujo"))
        assertFalse(lower.contains("gesto"))
        assertTrue(lower.contains("rpe 5"))
        assertTrue(lower.contains("8"))
        assertTrue(lower.contains("hipertrofia") || lower.contains("estímulo") || lower.contains("estimulo"))
    }

    @Test
    fun rirFiveIsIneffective() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.INTENSITY,
                enteredIntensity = 5.0,
                plannedIntensity = 2.0,
                intensityMode = IntensityMode.RIR,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("holgado"))
        assertFalse(lower.contains("inefectiv"))
        assertFalse(lower.contains("no sirve"))
        assertTrue(isOptimisticLightCopy(lower))
    }

    @Test
    fun rpeSixPointFiveIsMeasured() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.INTENSITY,
                enteredIntensity = 6.5,
                plannedIntensity = 8.0,
                intensityMode = IntensityMode.RPE,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("holgado"))
        assertFalse(lower.contains("inefectiv"))
        assertFalse(lower.contains("no sirve"))
        assertTrue(
            lower.contains("control") ||
                lower.contains("margen") ||
                lower.contains("calidad") ||
                lower.contains("limpio") ||
                lower.contains("contenido") ||
                lower.contains("mesurad") ||
                lower.contains("moderado") ||
                lower.contains("técnica") ||
                lower.contains("patron") ||
                lower.contains("patrón") ||
                lower.contains("rpe") ||
                lower.contains("esfuerzo") ||
                lower.contains("trabajo"),
        )
    }

    @Test
    fun failureMentionsRecruitmentAndCost() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.FAILURE,
                reachedFailure = true,
                plannedFailure = false,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(
            lower.contains("fallo") ||
                lower.contains("recluta") ||
                lower.contains("tope") ||
                lower.contains("límite") ||
                lower.contains("limite") ||
                lower.contains("máximo") ||
                lower.contains("maximo"),
        )
        assertTrue(
            lower.contains("costo") ||
                lower.contains("fatiga") ||
                lower.contains("articul") ||
                lower.contains("recupera") ||
                lower.contains("peaje") ||
                lower.contains("descanso") ||
                lower.contains("suficiente") ||
                lower.contains("juntas"),
        )
    }

    @Test
    fun smallBumpOverSuggestedStaysIdle() {
        val line = WorkoutLiveRelator.resolve(
            weightSnapshot(entered = 82.5, lastLifted = 80.0, suggested = 80.0, feminine = false),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("por encima"))
        assertFalse(lower.contains("subiste"))
        assertTrue(isPrimordialIdle(lower))
    }

    @Test
    fun clearJumpOverSuggestedSpeaksAbove() {
        val line = WorkoutLiveRelator.resolve(
            weightSnapshot(entered = 90.0, lastLifted = 80.0, suggested = 80.0, feminine = false),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(isWeightAboveCopy(lower))
    }

    @Test
    fun clearlyBelowLastLiftedSpeaks() {
        val line = WorkoutLiveRelator.resolve(
            weightSnapshot(entered = 80.0, lastLifted = 90.0, suggested = 80.0, feminine = false),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(isWeightBelowCopy(lower))
    }

    @Test
    fun twoKiloDropVsLastStaysIdle() {
        val line = WorkoutLiveRelator.resolve(
            weightSnapshot(entered = 80.0, lastLifted = 82.0, suggested = 80.0, feminine = false),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("claramente menos"))
        assertFalse(lower.contains("por debajo de lo que venías"))
        assertTrue(isPrimordialIdle(lower))
    }

    @Test
    fun twoExtraRepsStayIdle() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.REPS,
                enteredReps = 12.0,
                plannedReps = 10.0,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("volumen de reps"))
        assertFalse(lower.contains("por encima del plan"))
        assertTrue(isPrimordialIdle(lower))
    }

    @Test
    fun fourRepsBelowPlanSpeaks() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.REPS,
                enteredReps = 6.0,
                plannedReps = 10.0,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("menos") || lower.contains("recort") || lower.contains("debajo") || lower.contains("pocas"))
    }

    @Test
    fun oneDropsetUsesLightCopy() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.DROPSET,
                dropSetCount = 1,
                plannedDropCount = 0,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("drop"))
        assertFalse(lower.contains("varios"))
        assertFalse(lower.contains("2–3") || lower.contains("2-3"))
    }

    @Test
    fun threeDropsetsUseHighCostCopy() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.DROPSET,
                dropSetCount = 3,
                plannedDropCount = 0,
                family = RelatorFamily.HINGE,
                compound = RelatorCompound.DEADLIFT_CONV,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("drop"))
        assertTrue(lower.contains("costo") || lower.contains("snc") || lower.contains("varios") || lower.contains("2"))
    }

    @Test
    fun intraSessionTricepsHintBeatsGenericIdle() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                exerciseDisplayName = "Extensiones de tríceps",
                tissueHint = RelatorTissueHint(
                    muscleLabel = "tríceps",
                    sourceExerciseName = "Press banca",
                    jointCare = "codos",
                    window = RelatorTissueWindow.INTRA,
                    drainScore = 2.0,
                ),
                idleCycle = 1,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("tríceps") || lower.contains("triceps"))
        assertTrue(lower.contains("press") || lower.contains("vinieron") || lower.contains("codos"))
    }

    @Test
    fun dayLumbarHintUsesYesterdayCopy() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                exerciseDisplayName = "Sentadilla trasera alta",
                compound = RelatorCompound.BACK_SQUAT_HIGH,
                tissueHint = RelatorTissueHint(
                    muscleLabel = "lumbar",
                    sourceExerciseName = "Peso muerto",
                    jointCare = null,
                    window = RelatorTissueWindow.DAY,
                    drainScore = 32.0,
                ),
                idleCycle = 1,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertTrue(lower.contains("lumbar") || lower.contains("ayer"))
        assertTrue(lower.contains("ayer") || lower.contains("muerto") || lower.contains("pesado"))
    }

    @Test
    fun pickIntraHintPrefersTricepsFromPresses() {
        val hint = pickRelatorTissueHint(
            todayPrimaryMuscles = listOf("Tríceps"),
            todayStabilizers = listOf("Erectores"),
            intraSession = listOf(
                RelatorPriorExercise(
                    name = "Press banca",
                    primaryMuscles = listOf("Pectoral"),
                    secondaryMuscles = listOf("Tríceps"),
                ),
            ),
            yesterday = emptyList(),
        )
        assertNotNull(hint)
        assertEquals(RelatorTissueWindow.INTRA, hint!!.window)
        assertEquals("tríceps", hint.muscleLabel)
        assertEquals("codos", hint.jointCare)
        assertTrue(hint.sourceExerciseName.lowercase().contains("press"))
    }

    @Test
    fun pickDayHintUsesLumbarDrain() {
        val hint = pickRelatorTissueHint(
            todayPrimaryMuscles = listOf("Cuádriceps"),
            todayStabilizers = listOf("Erector spinae"),
            intraSession = emptyList(),
            yesterday = listOf(
                RelatorPriorExercise(
                    name = "Peso muerto",
                    primaryMuscles = listOf("Erectores"),
                    highIntensity = true,
                    drainByMuscle = mapOf("Erector spinae" to 32.0),
                ),
            ),
        )
        assertNotNull(hint)
        assertEquals(RelatorTissueWindow.DAY, hint!!.window)
        assertEquals("lumbar", hint.muscleLabel)
        assertTrue(hint.sourceExerciseName.lowercase().contains("muerto"))
    }

    @Test
    fun intraHintBeatsDayHint() {
        val hint = pickRelatorTissueHint(
            todayPrimaryMuscles = listOf("tríceps"),
            todayStabilizers = listOf("erectores"),
            intraSession = listOf(
                RelatorPriorExercise(
                    name = "Press banca",
                    primaryMuscles = listOf("pectoral"),
                    secondaryMuscles = listOf("tríceps"),
                ),
            ),
            yesterday = listOf(
                RelatorPriorExercise(
                    name = "Peso muerto",
                    primaryMuscles = listOf("erectores"),
                    highIntensity = true,
                    drainByMuscle = mapOf("erectores" to 40.0),
                ),
            ),
        )
        assertNotNull(hint)
        assertEquals(RelatorTissueWindow.INTRA, hint!!.window)
        assertEquals("tríceps", hint.muscleLabel)
    }

    @Test
    fun bodyweightDoesNotUseLoadTemplates() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.WORKING,
                loadKind = RelatorLoadKind.BODYWEIGHT,
                lastChangedField = RelatorChangedField.WEIGHT,
                enteredWeight = 80.0,
                enteredWeightRaw = "80",
                referenceWeight = 80.0,
                suggestedWeight = 80.0,
                lastLiftedWeight = 80.0,
            ),
        ).text
        assertNotNull(line)
        assertFalse(line!!.lowercase().contains("conservador"))
        assertFalse(line.lowercase().contains("ambicioso"))
        assertFalse(line.lowercase().contains("por encima"))
    }

    @Test
    fun timeModeDoesNotTalkAboutReps() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.WORKING,
                unit = RelatorUnit.TIME,
                lastChangedField = RelatorChangedField.REPS,
                enteredReps = 30.0,
                plannedReps = 20.0,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("reps"))
        assertTrue(lower.contains("tiempo") || lower.contains("intervalo") || lower.contains("reloj"))
    }

    @Test
    fun implausiblePartialWeightHoldsPreviousMessage() {
        val previous = "Estás en tu primera serie. ¿Más que la última vez?"
        val resolution = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.WORKING,
                lastChangedField = RelatorChangedField.WEIGHT,
                enteredWeight = 8.0,
                enteredWeightRaw = "8",
                referenceWeight = 80.0,
                suggestedWeight = 80.0,
                lastLiftedWeight = 80.0,
            ),
            previousText = previous,
        )
        assertTrue(resolution.holdPrevious)
        assertEquals(previous, resolution.text)
    }

    @Test
    fun shortExerciseNameKeepsTheFullFirstChunk() {
        assertEquals(
            "Crunch Abdominal de Rodillas en Polea Alta",
            baseSnapshot(exerciseDisplayName = "Crunch Abdominal de Rodillas en Polea Alta").shortExerciseName(),
        )
    }

    @Test
    fun catalogResolutionsKeepFullExerciseNameOnTwoLines() {
        val name = "Press de banca con agarre cerrado en máquina Smith inclinada"
        val samples = WorkoutLiveRelator.allResolvedSamples(name)
        assertTrue(samples.isNotEmpty())
        val withExercise = samples.filter { it.contains("Press", ignoreCase = true) }
        assertTrue(withExercise.isNotEmpty())
        withExercise.forEach { line ->
            assertTrue(
                "Chopped exercise in: $line",
                line.contains("agarre") ||
                    line.contains("Smith") ||
                    line.contains("máquina") ||
                    line.contains("maquina") ||
                    line.contains("inclinada"),
            )
        }
    }

    @Test
    fun headerFadeAndPagerNudgeStayUntouched() {
        assertEquals(0.05f, com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardTopNudgeFraction, 0.0f)
        assertEquals(0.05f, com.example.kpkn.ui.adapt.LiveViewportPolicyMath.NUDGE_CANONICAL, 0.0f)
        assertEquals(36, WorkoutLiveRelatorSlotHeight.value.toInt())
        assertEquals(13, WorkoutLiveRelatorFontSp.value.toInt())
        assertEquals(2, WorkoutLiveRelatorMaxLines)
        assertEquals(140, RELATOR_MAX_LINE_CHARS)
        assertEquals(48, com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerRelatorClearance.value.toInt())
        val tokens = com.example.kpkn.screens.workout.components.WorkoutUiTokens
        val expectedBand = tokens.LivePagerNormalExpandedBaseHeight.value *
            tokens.LivePagerCardScale *
            tokens.LivePagerCardHeightGrowFactor *
            0.05f + 48f
        assertEquals(expectedBand, tokens.liveRelatorBandHeight().value, 0.05f)
    }

    @Test
    fun parentContextKeyIsExerciseScopedAndSupersetAware() {
        assertEquals("ex:squat", relatorParentContextKey("squat", groupId = null, groupMemberCount = 1, unilateral = false))
        assertEquals("ex:lunge:uni", relatorParentContextKey("lunge", groupId = null, groupMemberCount = 1, unilateral = true))
        assertEquals("ss:group-a", relatorParentContextKey("a", groupId = "group-a", groupMemberCount = 2, unilateral = false))
    }

    @Test
    fun idleCopySticksAcrossSetsInSameParent() {
        val first = baseSnapshot(
            setIndex = 1,
            setCount = 4,
            parentContextKey = "ex:squat",
            setKey = "squat_1",
            lastLiftedWeight = 80.0,
            sessionLastSet = RelatorSessionSetMemory(setNumber = 1, weightKg = 80.0, reps = 8),
        )
        val second = baseSnapshot(
            setIndex = 2,
            setCount = 4,
            parentContextKey = "ex:squat",
            setKey = "squat_2",
            lastLiftedWeight = 80.0,
            sessionLastSet = RelatorSessionSetMemory(setNumber = 2, weightKg = 80.0, reps = 7),
        )
        assertEquals(RelatorSpeechBucket.IDLE_MID, first.speechBucket())
        assertEquals(RelatorSpeechBucket.IDLE_MID, second.speechBucket())
        val firstLine = WorkoutLiveRelator.resolve(first).text!!
        val secondLine = WorkoutLiveRelator.resolve(second).text!!
        assertTrue(firstLine.lowercase().contains("serie 2"))
        assertTrue(secondLine.lowercase().contains("serie 3"))
        assertTrue(firstLine.lowercase().contains("80"))
        assertTrue(secondLine.lowercase().contains("80"))
    }

    @Test
    fun dropsetFollowUp_tellsToCutRestAndDropFiveKg() {
        val snapshot = baseSnapshot(
            setIndex = 1,
            setCount = 3,
            lastLiftedWeight = 80.0,
        ).copy(isDropsetFollowUp = true)
        assertEquals(RelatorSpeechBucket.DROPSET_FOLLOWUP, snapshot.speechBucket())
        val line = WorkoutLiveRelator.resolve(snapshot).text!!
        assertTrue(line.lowercase().contains("sin descanso"))
        assertTrue(line.contains("5"))
    }

    @Test
    fun gapSetOfferAsksToReturnOrOmit() {
        val squat = RelatorAssistExercise("squat", "Sentadilla", setCount = 4, groupId = null, unilateral = false, isCardio = false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.WORKING,
                family = RelatorFamily.SQUAT,
                currentExerciseId = "squat",
                currentExerciseName = "Sentadilla",
                currentSetIndex = 2,
                currentExerciseIndex = 0,
                activeSide = null,
                sessionExercises = listOf(squat),
                completedSetKeys = setOf("squat_0"),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = null,
            ),
        )
        assertNotNull(offer)
        assertEquals(RelatorAssistKind.GAP_SET, offer!!.kind)
        assertTrue(offer.text.lowercase().contains("serie 2"))
        assertTrue(offer.actions.any { it.kind == RelatorAssistActionKind.OMIT_SET && it.span.contains("omitida") })
        assertTrue(offer.actions.any { it.kind == RelatorAssistActionKind.JUMP_TO_SET && it.setIndex == 1 })
        assertTrue(relatorActionSpans(offer.text, offer.actions).size == 2)
    }

    @Test
    fun skippedExerciseOfferCanMoveToEnd() {
        val bench = RelatorAssistExercise("bench", "Press banca", 3, null, false, false)
        val squat = RelatorAssistExercise("squat", "Sentadilla", 3, null, false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.WORKING,
                family = RelatorFamily.SQUAT,
                currentExerciseId = "squat",
                currentExerciseName = "Sentadilla",
                currentSetIndex = 0,
                currentExerciseIndex = 1,
                activeSide = null,
                sessionExercises = listOf(bench, squat),
                completedSetKeys = emptySet(),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = setOf("bench"),
                remainingSeconds = null,
            ),
        )
        assertNotNull(offer)
        assertEquals(RelatorAssistKind.GAP_EXERCISE, offer!!.kind)
        assertTrue(offer.actions.any { it.kind == RelatorAssistActionKind.MOVE_EXERCISE_END })
        assertTrue(offer.text.lowercase().contains("saltaste") || offer.text.lowercase().contains("pendiente"))
        assertTrue(offer.actions.all { offer.text.contains(it.clickableSpan(), ignoreCase = true) })
        assertEquals(2, relatorActionSpans(offer.text, offer.actions).size)
    }

    @Test
    fun gapExerciseOfferOnlyMentionsImmediatePrevious() {
        val press = RelatorAssistExercise("press", "Press banca", 3, null, false, false)
        val row = RelatorAssistExercise("row", "Remo", 3, null, false, false)
        val crunch = RelatorAssistExercise("crunch", "Crunch", 3, null, false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.WORKING,
                family = RelatorFamily.ISOLATION,
                currentExerciseId = "crunch",
                currentExerciseName = "Crunch",
                currentSetIndex = 0,
                currentExerciseIndex = 2,
                activeSide = null,
                sessionExercises = listOf(press, row, crunch),
                completedSetKeys = emptySet(),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = null,
            ),
        )
        assertNotNull(offer)
        assertEquals(RelatorAssistKind.GAP_EXERCISE, offer!!.kind)
        assertTrue(offer.text.contains("Remo") || offer.text.contains("row", ignoreCase = true) || offer.actions.any { it.exerciseId == "row" })
        assertFalse(offer.actions.any { it.exerciseId == "press" })
        assertEquals("row", offer.actions.first().exerciseId)
    }

    @Test
    fun gapExerciseOfferSilentWhenImmediatePreviousIsResolved() {
        val press = RelatorAssistExercise("press", "Press banca", 3, null, false, false)
        val row = RelatorAssistExercise("row", "Remo", 3, null, false, false)
        val crunch = RelatorAssistExercise("crunch", "Crunch", 3, null, false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.WORKING,
                family = RelatorFamily.ISOLATION,
                currentExerciseId = "crunch",
                currentExerciseName = "Crunch",
                currentSetIndex = 0,
                currentExerciseIndex = 2,
                activeSide = null,
                sessionExercises = listOf(press, row, crunch),
                completedSetKeys = setOf("row_0", "row_1", "row_2"),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = null,
            ),
        )
        assertTrue(offer == null || offer.kind != RelatorAssistKind.GAP_EXERCISE)
    }

    @Test
    fun gapSupersetOfferSilentUntilCurrentRoundIsResolved() {
        val pull = RelatorAssistExercise("pull", "Band Pull-Apart", 3, "ss1", false, false)
        val hinge = RelatorAssistExercise("hinge", "Buenos Días Zercher", 3, "ss1", false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.WORKING,
                family = RelatorFamily.PULL,
                currentExerciseId = "pull",
                currentExerciseName = "Band Pull-Apart",
                currentSetIndex = 0,
                currentExerciseIndex = 0,
                activeSide = null,
                sessionExercises = listOf(pull, hinge),
                completedSetKeys = emptySet(),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = null,
            ),
        )
        assertTrue(offer == null || offer.kind != RelatorAssistKind.GAP_SUPERSET)
    }

    @Test
    fun gapExerciseOfferSilentForSameSupersetPartner() {
        val pull = RelatorAssistExercise("pull", "Band Pull-Apart", 3, "ss1", false, false)
        val hinge = RelatorAssistExercise("hinge", "Buenos Días Zercher", 3, "ss1", false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.WORKING,
                family = RelatorFamily.HINGE,
                currentExerciseId = "hinge",
                currentExerciseName = "Buenos Días Zercher",
                currentSetIndex = 0,
                currentExerciseIndex = 1,
                activeSide = null,
                sessionExercises = listOf(pull, hinge),
                completedSetKeys = setOf("pull_0"),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = null,
            ),
        )
        assertTrue(offer == null || offer.kind != RelatorAssistKind.GAP_EXERCISE)
    }

    @Test
    fun relatorInlinePiecesSplitCopyAndActions() {
        val jump = RelatorAssistAction(
            kind = RelatorAssistActionKind.JUMP_TO_EXERCISE,
            label = "Vuelve",
            exerciseId = "row",
            span = "Vuelve",
        )
        val move = RelatorAssistAction(
            kind = RelatorAssistActionKind.MOVE_EXERCISE_END,
            label = "muévelo al final",
            exerciseId = "row",
            span = "muévelo al final",
        )
        val text = "Quedó pendiente Remo. Vuelve, o muévelo al final."
        val pieces = relatorInlinePieces(text, listOf(jump, move))
        assertEquals(5, pieces.size)
        assertEquals("Quedó pendiente Remo. ", (pieces[0] as RelatorInlinePiece.Copy).text)
        assertEquals("Vuelve", (pieces[1] as RelatorInlinePiece.Action).label)
        assertEquals(RelatorAssistActionKind.JUMP_TO_EXERCISE, (pieces[1] as RelatorInlinePiece.Action).action.kind)
        assertEquals(", o ", (pieces[2] as RelatorInlinePiece.Copy).text)
        assertEquals("muévelo al final", (pieces[3] as RelatorInlinePiece.Action).label)
        assertEquals(RelatorAssistActionKind.MOVE_EXERCISE_END, (pieces[3] as RelatorInlinePiece.Action).action.kind)
        assertEquals(".", (pieces[4] as RelatorInlinePiece.Copy).text)
    }

    @Test
    fun relatorActionSpansDoNotStartInsideAWord() {
        val action = RelatorAssistAction(
            kind = RelatorAssistActionKind.JUMP_TO_EXERCISE,
            label = "Vuelve",
            span = "Vuelve",
        )
        val inside = relatorActionSpans("Devuelve el peso y sigue.", listOf(action))
        assertTrue(inside.isEmpty())
        val standalone = relatorActionSpans("Pendiente Press banca. Vuelve ahora.", listOf(action))
        assertEquals(1, standalone.size)
        assertEquals("Vuelve", "Pendiente Press banca. Vuelve ahora.".substring(standalone.single().start, standalone.single().endExclusive))
        assertTrue(relatorIsStandaloneSpan("Pendiente Press banca. Vuelve ahora.", standalone.single().start, standalone.single().endExclusive))
        assertFalse(relatorIsStandaloneSpan("Devuelve", 2, 8))
    }

    @Test
    fun relatorLinkWrapChunksKeepSpacesOutsideWords() {
        val chunks = relatorLinkWrapChunks("Ve a Remo Barra")
        assertEquals(listOf("Ve", " ", "a", " ", "Remo", " ", "Barra"), chunks)
        assertTrue(chunks.any { it.isBlank() })
        assertEquals(listOf("Vuelve"), relatorLinkWrapChunks("Vuelve"))
    }

    @Test
    fun relatorInlinePiecesWithoutActionsStayCopy() {
        val text = "Estás en la serie 1 de Press banca."
        val pieces = relatorInlinePieces(text, emptyList())
        assertEquals(1, pieces.size)
        assertEquals(text, (pieces.single() as RelatorInlinePiece.Copy).text)
    }

    @Test
    fun timeCrunchOfferExposesDropsetAndHalveActions() {
        val squat = RelatorAssistExercise("squat", "Sentadilla", 4, null, false, false)
        val row = RelatorAssistExercise("row", "Remo", 4, null, false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.WORKING,
                family = RelatorFamily.SQUAT,
                currentExerciseId = "squat",
                currentExerciseName = "Sentadilla",
                currentSetIndex = 0,
                currentExerciseIndex = 0,
                activeSide = null,
                sessionExercises = listOf(squat, row),
                completedSetKeys = emptySet(),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = 180,
            ),
        )
        assertNotNull(offer)
        assertEquals(RelatorAssistKind.TIME, offer!!.kind)
        assertTrue(offer.actions.any { it.kind == RelatorAssistActionKind.PREVIEW_ULTRAFAST })
        assertTrue(offer.actions.any { it.kind == RelatorAssistActionKind.CONVERT_DROPSETS })
        assertTrue(offer.actions.any { it.kind == RelatorAssistActionKind.HALVE_SETS })
        assertTrue(offer.text.contains("modo Ultrarrápido"))
        assertTrue(offer.text.contains("Convierte a dropsets"))
        assertTrue(offer.text.contains("reduce series a la mitad"))
        assertEquals(3, relatorActionSpans(offer.text, offer.actions).size)
    }

    @Test
    fun timeCrunchOfferHidesUltraFastWhenAlreadyApplied() {
        val squat = RelatorAssistExercise("squat", "Sentadilla", 4, null, false, false)
        val row = RelatorAssistExercise("row", "Remo", 4, null, false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.WORKING,
                family = RelatorFamily.SQUAT,
                currentExerciseId = "squat",
                currentExerciseName = "Sentadilla",
                currentSetIndex = 0,
                currentExerciseIndex = 0,
                activeSide = null,
                sessionExercises = listOf(squat, row),
                completedSetKeys = emptySet(),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = 180,
                ultraFastApplied = true,
            ),
        )
        assertNotNull(offer)
        assertEquals(RelatorAssistKind.TIME, offer!!.kind)
        assertTrue(offer.actions.none { it.kind == RelatorAssistActionKind.PREVIEW_ULTRAFAST })
        assertTrue(offer.actions.any { it.kind == RelatorAssistActionKind.CONVERT_DROPSETS })
        assertEquals(2, relatorActionSpans(offer.text, offer.actions).size)
    }

    @Test
    fun squatMobilitySuggestsDeadBug() {
        val squat = RelatorAssistExercise("squat", "Sentadilla trasera", 3, null, false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.MOBILITY,
                family = RelatorFamily.SQUAT,
                currentExerciseId = "squat",
                currentExerciseName = "Sentadilla trasera",
                currentSetIndex = 0,
                currentExerciseIndex = 0,
                activeSide = null,
                sessionExercises = listOf(squat),
                completedSetKeys = emptySet(),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = null,
            ),
        )
        assertNotNull(offer)
        assertEquals(RelatorAssistKind.MOBILITY, offer!!.kind)
        val drill = MobilityExerciseCatalog.findById("mob_dead_bug_reach")
        assertNotNull(drill)
        assertTrue(offer.text.contains(shortMobilityCatalogName(drill!!)))
        assertEquals("mob_dead_bug_reach", offer.actions.single().mobilityId)
        assertTrue(relatorActionSpans(offer.text.replace("{ex}", "Sentadilla"), offer.actions).isNotEmpty())
        assertFalse(offer.text.contains("DEAD BUG"))
    }

    @Test
    fun hingeMobilitySuggestsCatalogNinetyNinety() {
        val hinge = RelatorAssistExercise("dl", "Peso muerto", 3, null, false, false)
        val offer = pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = RelatorPhase.MOBILITY,
                family = RelatorFamily.HINGE,
                currentExerciseId = "dl",
                currentExerciseName = "Peso muerto",
                currentSetIndex = 0,
                currentExerciseIndex = 0,
                activeSide = null,
                sessionExercises = listOf(hinge),
                completedSetKeys = emptySet(),
                omittedSetKeys = emptySet(),
                skippedExerciseIds = emptySet(),
                remainingSeconds = null,
            ),
        )
        assertNotNull(offer)
        val drill = MobilityExerciseCatalog.findById(offer!!.actions.single().mobilityId)
        assertNotNull(drill)
        assertEquals("mob_90_90_breathing", drill!!.id)
        assertTrue(drill.name.contains("90/90"))
        assertTrue(offer.text.contains(shortMobilityCatalogName(drill)))
        assertTrue(relatorActionSpans(offer.text.replace("{ex}", "Peso muerto"), offer.actions).isNotEmpty())
        val resolved = WorkoutLiveRelator.resolve(
            baseSnapshot(
                phase = RelatorPhase.MOBILITY,
                family = RelatorFamily.HINGE,
                exerciseDisplayName = "Peso muerto rumano con barra hexagonal",
                assistOffer = offer,
            ),
        )
        assertTrue(relatorActionSpans(resolved.text.orEmpty(), resolved.actions).isNotEmpty())
    }

    @Test
    fun suggestedMobilityAlwaysResolvesFromCatalog() {
        RelatorFamily.entries.forEach { family ->
            val suggestion = suggestedMobilityForFamily(family) ?: return@forEach
            val (drill, span) = suggestion
            assertEquals(drill.id, MobilityExerciseCatalog.findById(drill.id)?.id)
            assertTrue(drill.name.contains(span))
            assertTrue(span.isNotBlank())
            assertFalse(span.contains("DEAD BUG", ignoreCase = true))
        }
    }

    @Test
    fun relatorActionSpansBindTheMentionedWords() {
        val text = "Añade Respiración 90/90 a la movilidad de Peso muerto, para la pelvis."
        val action = RelatorAssistAction(
            kind = RelatorAssistActionKind.ADD_MOBILITY,
            label = "Respiración 90/90",
            exerciseId = "dl",
            mobilityId = "mob_90_90_breathing",
            span = "Respiración 90/90",
        )
        val spans = relatorActionSpans(text, listOf(action))
        assertEquals(1, spans.size)
        assertEquals("Respiración 90/90", text.substring(spans.single().start, spans.single().endExclusive))
        assertEquals("mob_90_90_breathing", spans.single().action.mobilityId)
    }

    @Test
    fun assistShowsImmediatelyWhenReady() {
        val offer = RelatorAssistOffer(
            kind = RelatorAssistKind.GAP_SET,
            text = "No has registrado la serie anterior. Vuelve y complétala, o márcala omitida.",
            actions = listOf(
                RelatorAssistAction(RelatorAssistActionKind.OMIT_SET, "LA MARCAS COMO OMITIDA", "squat", 0),
            ),
            stickyKey = "gap-set:squat:0",
        )
        val resolution = WorkoutLiveRelator.resolve(
            baseSnapshot(
                setIndex = 1,
                setCount = 3,
                assistOffer = offer,
                parentContextKey = "ex:squat",
                lastLiftedWeight = 80.0,
            ),
        )
        assertEquals(RelatorSpeechBucket.ASSIST_GAP_SET.phaseKey(), resolution.phaseKey)
        assertTrue(resolution.text!!.lowercase().contains("omitida") || resolution.text!!.lowercase().contains("serie"))
        assertEquals(1, resolution.actions.size)
    }

    @Test
    fun assistGapBeatsParentIdle() {
        val offer = RelatorAssistOffer(
            kind = RelatorAssistKind.GAP_SET,
            text = "No has registrado la serie anterior. Vuelve y complétala, o márcala omitida.",
            actions = listOf(
                RelatorAssistAction(RelatorAssistActionKind.OMIT_SET, "LA MARCAS COMO OMITIDA", "squat", 0),
            ),
            stickyKey = "gap-set:squat:0",
        )
        val resolution = WorkoutLiveRelator.resolve(
            baseSnapshot(
                setIndex = 1,
                setCount = 3,
                assistOffer = offer,
                parentContextKey = "ex:squat",
                idleCycle = 1,
            ),
        )
        assertEquals(RelatorSpeechBucket.ASSIST_GAP_SET.phaseKey(), resolution.phaseKey)
        assertTrue(resolution.text!!.lowercase().contains("omitida") || resolution.text!!.lowercase().contains("serie"))
        assertEquals(1, resolution.actions.size)
    }

    @Test
    fun situateDoesNotShuffleWhenNothingHappened() {
        val first = WorkoutLiveRelator.resolve(baseSnapshot(idleCycle = 0, lastLiftedWeight = 80.0)).text
        val second = WorkoutLiveRelator.resolve(baseSnapshot(idleCycle = 1, lastLiftedWeight = 80.0)).text
        assertNotNull(first)
        assertEquals(first, second)
        assertTrue(first!!.lowercase().contains("serie 1"))
    }

    @Test
    fun programmedLightIntensityStaysOptimisticIfSpoken() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                lastChangedField = RelatorChangedField.INTENSITY,
                enteredIntensity = 5.0,
                plannedIntensity = 5.0,
                intensityMode = IntensityMode.RPE,
            ),
        ).text
        assertNotNull(line)
        val lower = line!!.lowercase()
        assertFalse(lower.contains("inefectiv"))
        assertFalse(lower.contains("no sirve"))
        assertFalse(lower.contains("no estimula"))
    }

    @Test
    fun detectFieldPrefersWeightOverStaleIntensity() {
        val field = detectRelatorChangedField(
            phase = RelatorPhase.WORKING,
            draftDirty = true,
            weightText = "72.5",
            repsText = "10",
            intensityText = "8",
            prevWeight = "70",
            prevReps = "10",
            prevIntensity = "8",
            warmupDraft = "",
            prevWarmupDraft = "",
            mobilityDone = 0,
            prevMobilityDone = 0,
            timerRunning = false,
            prevTimerRunning = false,
            previousField = RelatorChangedField.INTENSITY,
        )
        assertEquals(RelatorChangedField.WEIGHT, field)
    }

    @Test
    fun untouchedWeightDoesNotCommentEvenIfDraftDiffers() {
        val first = weightSnapshot(entered = 90.0, lastLifted = 80.0, suggested = 80.0, feminine = false)
        val second = first.copy(lastChangedField = RelatorChangedField.NONE)
        assertEquals(RelatorSpeechBucket.WEIGHT_ABOVE, first.speechBucket())
        assertEquals(RelatorSpeechBucket.IDLE_FIRST_HIST, second.speechBucket())
    }

    @Test
    fun unusedDayHintWhenNoMatch() {
        assertNull(
            pickRelatorTissueHint(
                todayPrimaryMuscles = listOf("cuádriceps"),
                todayStabilizers = listOf("abdominales"),
                intraSession = emptyList(),
                yesterday = emptyList(),
            ),
        )
    }

    @Test
    fun firstSetUsesPreviousSessionMarkAndMidSetUsesTodayOnly() {
        val history = RelatorSessionSetMemory(setNumber = 1, weightKg = 100.0, reps = 5)
        val today = RelatorSessionSetMemory(setNumber = 1, weightKg = 80.0, reps = 8)
        val first = WorkoutLiveRelator.resolve(
            baseSnapshot(setIndex = 0, historyLastSet = history, lastLiftedWeight = 100.0),
        ).text!!.lowercase()
        assertTrue(first.contains("última vez") || first.contains("ultima vez"))
        assertTrue(first.contains("100"))
        assertFalse(first.contains("80×8") || first.contains("80x8"))

        val second = WorkoutLiveRelator.resolve(
            baseSnapshot(
                setIndex = 1,
                setCount = 4,
                sessionLastSet = today,
                historyLastSet = history,
                lastLiftedWeight = 80.0,
            ),
        ).text!!.lowercase()
        assertTrue(second.contains("en la 1"))
        assertTrue(second.contains("80"))
        assertFalse(second.contains("100"))
        assertFalse(second.contains("antes moviste"))
    }

    @Test
    fun failedSetCautionSpeaksBeforeNextSet() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(setIndex = 1, setCount = 4).copy(
                failedSetCaution = RelatorFailedSetCaution(
                    sourceExerciseId = "press",
                    sourceSetNumber = 1,
                    sameExercise = true,
                    stickyKey = "failed:press:0",
                ),
            ),
        ).text!!.lowercase()
        assertTrue(line.contains("fallida"))
        assertTrue(line.contains("articul") || line.contains("carga"))
    }

    @Test
    fun assistConfirmDropsetsSpeaksInFirstPerson() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot().copy(
                assistAck = RelatorAssistAck(
                    kind = RelatorAssistActionKind.CONVERT_DROPSETS,
                    applied = true,
                ),
            ),
        ).text!!.lowercase()
        assertTrue(line.contains("dropset"))
        assertTrue(line.contains("ok") || line.contains("aplico"))
        assertTrue(line.contains("articul") || line.contains("recupera"))
    }

    @Test
    fun assistConfirmReportsWhenNothingChanged() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot().copy(
                assistAck = RelatorAssistAck(
                    kind = RelatorAssistActionKind.PREVIEW_ULTRAFAST,
                    applied = false,
                ),
            ),
        ).text!!.lowercase()
        assertTrue(line.contains("ultrarrápido") || line.contains("ultrarrapido") || line.contains("aplicado"))
    }

    @Test
    fun axialConceptWaitsForIdleCycle() {
        val snap = baseSnapshot(
            family = RelatorFamily.SQUAT,
            exerciseDisplayName = "Sentadilla trasera",
            axialLoadFactor = 1.0,
        )
        val first = WorkoutLiveRelator.resolve(snap).text!!.lowercase()
        assertTrue(first.contains("serie 1"))
        assertFalse(first.contains("carga axial"))
        val later = WorkoutLiveRelator.resolve(snap.copy(idleCycle = 1)).text!!.lowercase()
        assertTrue(later.contains("carga axial"))
        assertEquals(RelatorSpeechBucket.CONCEPT_CUE.phaseKey(), WorkoutLiveRelator.resolve(snap.copy(idleCycle = 1)).phaseKey)
    }

    @Test
    fun axialConceptDoesNotBeatWeightReaction() {
        val line = WorkoutLiveRelator.resolve(
            weightSnapshot(entered = 70.0, lastLifted = 80.0, suggested = 80.0, feminine = false).copy(
                axialLoadFactor = 1.0,
                idleCycle = 1,
                family = RelatorFamily.SQUAT,
            ),
        ).text!!.lowercase()
        assertTrue(isWeightBelowCopy(line))
        assertFalse(line.contains("carga axial"))
    }

    @Test
    fun discomfortBeatsConceptCue() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                idleCycle = 1,
                axialLoadFactor = 1.0,
                discomfortHint = RelatorDiscomfortHint(label = "Lumbar", fromThisSession = false),
            ),
        ).text!!.lowercase()
        assertTrue(line.contains("lumbar"))
        assertFalse(line.contains("carga axial"))
    }

    @Test
    fun shownConceptIdDoesNotRepeatCargaAxial() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot(
                idleCycle = 1,
                axialLoadFactor = 1.0,
                intensityMode = IntensityMode.RPE,
                plannedIntensity = 8.0,
                shownConceptIds = setOf("carga-axial"),
            ),
        ).text!!.lowercase()
        assertTrue(line.contains("rpe"))
        assertFalse(line.contains("carga axial"))
    }

    @Test
    fun assistActionsStayDetachedOnConfirm() {
        val offer = RelatorAssistOffer(
            kind = RelatorAssistKind.TIME,
            text = "Quedan 4 minutos. Convierte a dropsets.",
            actions = listOf(
                RelatorAssistAction(RelatorAssistActionKind.CONVERT_DROPSETS, "Convierte a dropsets"),
            ),
            stickyKey = "time",
        )
        val resolution = WorkoutLiveRelator.resolve(
            baseSnapshot(assistOffer = offer).copy(
                assistAck = RelatorAssistAck(RelatorAssistActionKind.CONVERT_DROPSETS, applied = true),
            ),
        )
        assertEquals(RelatorSpeechBucket.ASSIST_CONFIRM.phaseKey(), resolution.phaseKey)
        assertTrue(resolution.actions.isEmpty())
    }

    @Test
    fun assistConfirmJumpAndMoveCarryShortExerciseName() {
        val move = WorkoutLiveRelator.resolve(
            baseSnapshot(exerciseDisplayName = "Press banca").copy(
                assistAck = RelatorAssistAck(
                    kind = RelatorAssistActionKind.MOVE_EXERCISE_END,
                    applied = true,
                    detail = "Remo T",
                ),
            ),
        )
        assertEquals(RelatorSpeechBucket.ASSIST_CONFIRM.phaseKey(), move.phaseKey)
        assertTrue(move.actions.isEmpty())
        assertTrue(move.text!!.contains("Remo T"))

        val jump = WorkoutLiveRelator.resolve(
            baseSnapshot(exerciseDisplayName = "Press banca").copy(
                assistAck = RelatorAssistAck(
                    kind = RelatorAssistActionKind.JUMP_TO_EXERCISE,
                    applied = true,
                    detail = "Remo T",
                ),
            ),
        )
        assertTrue(jump.text!!.contains("Remo T"))
        assertTrue(jump.actions.isEmpty())
    }

    @Test
    fun assistConfirmHasDistinctMoveAndJumpVariants() {
        val moveSnap = baseSnapshot().copy(
            assistAck = RelatorAssistAck(
                kind = RelatorAssistActionKind.MOVE_EXERCISE_END,
                applied = true,
                detail = "Remo T",
            ),
        )
        val jumpSnap = baseSnapshot().copy(
            assistAck = RelatorAssistAck(
                kind = RelatorAssistActionKind.JUMP_TO_EXERCISE,
                applied = true,
                detail = "Remo T",
            ),
        )
        val move = WorkoutLiveRelatorCatalog.variantsFor(RelatorSpeechBucket.ASSIST_CONFIRM, moveSnap)
        val jump = WorkoutLiveRelatorCatalog.variantsFor(RelatorSpeechBucket.ASSIST_CONFIRM, jumpSnap)
        assertTrue(move.distinct().size >= 2)
        assertTrue(jump.distinct().size >= 2)
        assertTrue(move.all { it.contains("Remo T") })
        assertTrue(jump.all { it.contains("Remo T") })
    }

    @Test
    fun assistConfirmAppliedFalseStillSpeaks() {
        val line = WorkoutLiveRelator.resolve(
            baseSnapshot().copy(
                assistAck = RelatorAssistAck(
                    kind = RelatorAssistActionKind.JUMP_TO_EXERCISE,
                    applied = false,
                    detail = "Press banca",
                ),
            ),
        ).text
        assertFalse(line.isNullOrBlank())
        assertTrue(line!!.lowercase().contains("saltar") || line.lowercase().contains("salto"))
    }

    @Test
    fun assistConfirmFingerprintSkipsSameVariantTwice() {
        val snap = baseSnapshot().copy(
            assistAck = RelatorAssistAck(
                kind = RelatorAssistActionKind.MOVE_EXERCISE_END,
                applied = true,
                detail = "Remo T",
            ),
        )
        val variants = WorkoutLiveRelatorCatalog.variantsFor(RelatorSpeechBucket.ASSIST_CONFIRM, snap)
        val extra = RelatorAssistActionKind.MOVE_EXERCISE_END.name
        val first = pickRelatorVariant(RelatorSpeechBucket.ASSIST_CONFIRM, variants, RelatorSpeechMemory(), extra)
        val second = pickRelatorVariant(
            RelatorSpeechBucket.ASSIST_CONFIRM,
            variants,
            RelatorSpeechMemory().record(first.fingerprint),
            extra,
        )
        assertTrue(first.text != second.text)
    }

    @Test
    fun shortAssistNameDoesNotTruncatePrepositions() {
        assertEquals("Press de banca", shortAssistName("Press de banca · Barra plana"))
        assertEquals("Press de banca con mancuernas", shortAssistName("Press de banca con mancuernas"))
        assertEquals("Elevaciones de talón", shortAssistName("Elevaciones de talón · De pie"))
        assertEquals("Fondos en paralelas", shortAssistName("Fondos en paralelas · Sin lastre"))
        assertFalse(shortAssistName("Press de banca").endsWith("de"))
        assertFalse(shortAssistName("Press de banca con mancuernas plano").endsWith("con"))
        assertFalse(shortAssistName("Fondos en paralelas").endsWith("en"))
    }

    private fun isWeightBelowCopy(lower: String): Boolean =
        lower.contains("kg") && (
            lower.contains("última") ||
                lower.contains("ultima") ||
                lower.contains("debajo") ||
                lower.contains("menos") ||
                lower.contains("bajaste") ||
                lower.contains("bajas") ||
                lower.contains("liviano")
            )

    private fun isWeightAboveCopy(lower: String): Boolean =
        lower.contains("kg") && (
            lower.contains("sugerid") ||
                lower.contains("encima") ||
                lower.contains("subiste") ||
                lower.contains("+")
            )

    private fun isOptimisticLightCopy(lower: String): Boolean =
        lower.contains("técnica") ||
            lower.contains("tecnica") ||
            lower.contains("estímulo") ||
            lower.contains("estimulo") ||
            lower.contains("rpe") ||
            lower.contains("rir")

    private fun isPrimordialIdle(lower: String): Boolean =
        lower.contains("primera") ||
            lower.contains("última") ||
            lower.contains("marca") ||
            lower.contains("sesión") ||
            lower.contains("serie") ||
            lower.contains("arrancas") ||
            lower.contains("hoy empiezas")

    private fun weightSnapshot(
        entered: Double,
        lastLifted: Double,
        suggested: Double,
        feminine: Boolean,
    ): LiveRelatorSnapshot = baseSnapshot(
        phase = RelatorPhase.WORKING,
        feminine = feminine,
        lastChangedField = RelatorChangedField.WEIGHT,
        enteredWeight = entered,
        enteredWeightRaw = entered.toString(),
        referenceWeight = suggested,
        suggestedWeight = suggested,
        lastLiftedWeight = lastLifted,
    )

    private fun baseSnapshot(
        phase: RelatorPhase = RelatorPhase.WORKING,
        family: RelatorFamily = RelatorFamily.PRESS,
        feminine: Boolean = false,
        exerciseDisplayName: String = "Press banca",
        setIndex: Int = 0,
        setCount: Int = 3,
        hasHistory: Boolean = true,
        warmupIncompleteIndex: Int? = null,
        warmupCount: Int = 0,
        warmupIsLastIncomplete: Boolean = false,
        lastChangedField: RelatorChangedField = RelatorChangedField.NONE,
        enteredWeight: Double? = null,
        enteredWeightRaw: String = "",
        referenceWeight: Double? = null,
        suggestedWeight: Double? = null,
        lastLiftedWeight: Double? = null,
        enteredReps: Double? = null,
        plannedReps: Double? = 10.0,
        enteredIntensity: Double? = null,
        plannedIntensity: Double? = 8.0,
        intensityMode: IntensityMode? = null,
        reachedFailure: Boolean = false,
        plannedFailure: Boolean = false,
        dropSetCount: Int = 0,
        plannedDropCount: Int = 0,
        compound: RelatorCompound = RelatorCompound.NONE,
        tissueHint: RelatorTissueHint? = null,
        loadKind: RelatorLoadKind = RelatorLoadKind.LOAD,
        unit: RelatorUnit = RelatorUnit.REPS,
        mobilityCompleted: Int = 0,
        idleCycle: Int = 0,
        parentContextKey: String = "ex:test",
        setKey: String = "test-set",
        assistOffer: RelatorAssistOffer? = null,
        isSuperset: Boolean = false,
        activeSideLabel: String? = null,
        sessionLastSet: RelatorSessionSetMemory? = null,
        historyLastSet: RelatorSessionSetMemory? = null,
        discomfortHint: RelatorDiscomfortHint? = null,
        prHint: RelatorPrHint? = null,
        axialLoadFactor: Double? = null,
        equipmentId: String? = null,
        movementPatternId: String? = null,
        shownConceptIds: Set<String> = emptySet(),
    ): LiveRelatorSnapshot = LiveRelatorSnapshot(
        visible = true,
        phase = phase,
        family = family,
        feminine = feminine,
        exerciseDisplayName = exerciseDisplayName,
        setIndex = setIndex,
        setCount = setCount,
        hasHistory = hasHistory,
        warmupIncompleteIndex = warmupIncompleteIndex,
        warmupCount = warmupCount,
        warmupIsLastIncomplete = warmupIsLastIncomplete,
        mobilityCompleted = mobilityCompleted,
        lastChangedField = lastChangedField,
        enteredWeight = enteredWeight,
        enteredWeightRaw = enteredWeightRaw,
        referenceWeight = referenceWeight,
        suggestedWeight = suggestedWeight,
        lastLiftedWeight = lastLiftedWeight,
        enteredReps = enteredReps,
        plannedReps = plannedReps,
        enteredIntensity = enteredIntensity,
        plannedIntensity = plannedIntensity,
        intensityMode = intensityMode,
        reachedFailure = reachedFailure,
        plannedFailure = plannedFailure,
        dropSetCount = dropSetCount,
        plannedDropCount = plannedDropCount,
        compound = compound,
        tissueHint = tissueHint,
        loadKind = loadKind,
        unit = unit,
        setKey = setKey,
        parentContextKey = parentContextKey,
        idleCycle = idleCycle,
        assistOffer = assistOffer,
        isSuperset = isSuperset,
        activeSideLabel = activeSideLabel,
        sessionLastSet = sessionLastSet,
        historyLastSet = historyLastSet,
        discomfortHint = discomfortHint,
        prHint = prHint,
        axialLoadFactor = axialLoadFactor,
        equipmentId = equipmentId,
        movementPatternId = movementPatternId,
        shownConceptIds = shownConceptIds,
    )
}
