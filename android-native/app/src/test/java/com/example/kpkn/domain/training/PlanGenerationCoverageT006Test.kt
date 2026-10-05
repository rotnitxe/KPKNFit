package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingCapability
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.RecipeSessionKind
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.definitions.NativeCardioDefaults
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.onboarding.NativePlanFailureMapper
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluation
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluator
import com.example.kpkn.domain.onboarding.PlanMaterializationException
import com.example.kpkn.domain.onboarding.PlanMaterializationOutcome
import com.example.kpkn.domain.onboarding.PlanMaterializationPort
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import com.example.kpkn.domain.training.CoverageFixtures.EquipmentFixture
import com.example.kpkn.domain.training.CoverageFixtures.Profile
import com.example.kpkn.domain.training.CoverageFixtures.draft
import com.example.kpkn.domain.training.CoverageFixtures.legacyFixtures
import com.example.kpkn.domain.training.CoverageFixtures.levelOf
import com.example.kpkn.domain.training.CoverageFixtures.materializer
import com.example.kpkn.domain.training.CoverageFixtures.personalizer
import com.example.kpkn.domain.training.CoverageFixtures.plannerInput
import com.example.kpkn.domain.training.CoverageFixtures.request
import com.example.kpkn.domain.training.CoverageFixtures.weekdays
import com.example.kpkn.screens.onboarding.SetupExperience
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * T-006 Q1–Q3: reproducible planner publication sweep, actual native generation,
 * and fitter/cardio boundaries. Q1 intentionally stops before materialization;
 * only Q2 counts generated programs as viable.
 */
class PlanGenerationCoverageT006Test {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val profiles = Profile.entries
    private val experiences = SetupExperience.entries
    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val entries get() = PersonalizedPlanCatalog.entries()
    private val snapshot by lazy { CoverageFixtures.snapshot(entries, catalog) }

    /** Q1: all 62,208 planner inputs; publication/filtering only, no fake generation. */
    @Test
    fun Q1_pure_planner_coverage_62208_inputs() {
        val fixtures = legacyFixtures()
        assertEquals("fixtures E0..E7", (0..7).map { "E$it" }, fixtures.map { it.id })
        assertEquals("four product goals", 4, profiles.size)
        assertEquals("four experience answers", 4, experiences.size)

        var totalInputs = 0
        var publishedCandidates = 0
        var emptyPublishedRows = 0
        val rejectedByReason = linkedMapOf<String, Int>()
        val failures = mutableListOf<String>()
        val membershipByInput = linkedMapOf<String, Set<String>>()

        profiles.forEach { profile ->
            experiences.forEach { experience ->
                (1..6).forEach { days ->
                    (20..100).forEach { minutes ->
                        fixtures.forEach { equipment ->
                            totalInputs++
                            val draft = draft(profile, experience, days, minutes, equipment)
                            if (draft.goal != profile.goal || draft.experience != experience ||
                                draft.daysPerWeek != days || draft.minutesPerSession != minutes ||
                                draft.selectedWeekdays != weekdays(days).toSet() ||
                                draft.trainingOptions.availability != equipment.availability
                            ) {
                                failures.add("inputs alterados ${profile.name}/$experience/$days/$minutes/${equipment.id}")
                            }
                            val published = SetupTrainingPlanner.candidates(plannerInput(draft))
                            publishedCandidates += published.size
                            val ids = published.map { it.id }.toSet()
                            if (ids.size != published.size) {
                                failures.add("IDs duplicados ${profile.name}/$experience/$days/$minutes/${equipment.id}")
                            }
                            val expectedNativeId = profile.nativeKind.entryId
                            if (expectedNativeId !in ids) {
                                failures.add("sin testigo publicado $expectedNativeId ${profile.name}/$experience/$days/$minutes/${equipment.id}")
                            }
                            published.forEach { entry ->
                                if (entry.publication != PublicationState.PUBLISHED) {
                                    failures.add("planner publicó ${entry.publication} ${entry.id}")
                                }
                                if (days !in entry.supportedFrequencies) {
                                    failures.add("planner publicó frecuencia no soportada ${entry.id}/$days")
                                }
                                if (entry.source != com.example.kpkn.data.programs.CatalogSource.PROTOCOL &&
                                    TrainingFocus.FULL_BODY !in entry.supportedFocuses
                                ) {
                                    failures.add("planner publicó foco no soportado ${entry.id}")
                                }
                                if (profile.reference != null && entry.source != com.example.kpkn.data.programs.CatalogSource.PROTOCOL &&
                                    entry.references.isNotEmpty() && profile.reference !in entry.references
                                ) {
                                    failures.add("planner filtró referencia incorrecta ${entry.id}/${profile.reference}")
                                }
                            }
                            val inputKey = "${profile.name}/${experience.name}/$days/${equipment.id}"
                            val previous = membershipByInput.putIfAbsent(inputKey, ids)
                            if (previous != null && previous != ids) {
                                failures.add("la duración mutó la publicación $inputKey/$minutes")
                            }
                            if (published.isEmpty()) {
                                emptyPublishedRows++
                                rejectedByReason.merge("NO_PUBLISHED_CANDIDATE", 1, Int::plus)
                            }
                        }
                    }
                }
            }
        }

        println(
            "[T006][Q1] totalInputs=$totalInputs publishedCandidates=$publishedCandidates " +
                "evaluatedCandidates=0 viableCandidates=0 rejectedByReason=$rejectedByReason " +
                "emptyPublishedRows=$emptyPublishedRows previewPassed=0 activationPassed=0 " +
                "reopenPassed=0 postWorkoutPassed=0",
        )
        assertEquals("Q1 fixture product", 62_208, totalInputs)
        assertEquals("ninguna combinación confirmable queda sin entrada publicada", 0, emptyPublishedRows)
        assertTrue(failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    /** Q2: real recipe + materializer + composition + shared duration, 2,304 rows. */
    @Test
    fun Q2_real_generation_coverage_2304_inputs() = runBlocking {
        val fixtures = legacyFixtures()
        val generator = personalizer() // load the approved exercise catalog once for this group
        var totalInputs = 0
        var publishedCandidates = 0
        var evaluatedCandidates = 0
        var viableCandidates = 0
        var physicalOrTimeNegativePasses = 0
        // B-02: filas viables que dependen de la banda blanda de glúteos (16 < x ≤ 17,5) o que llevan el
        // aviso de «volumen alto». Con el catálogo y los calendarios actuales deben ser 0 (último recurso).
        val rowsInSoftBand = mutableListOf<String>()
        var rowsWithHighVolumeNotice = 0
        val rejectedByReason = linkedMapOf<String, Int>()
        val failures = mutableListOf<String>()
        val elapsedStart = System.nanoTime()

        profiles.forEach { profile ->
            experiences.forEach { experience ->
                (1..6).forEach { days ->
                    fixtures.forEach { equipment ->
                        listOf(20, 60, 100).forEach minuteLoop@{ minutes ->
                            totalInputs++
                            val nativeId = profile.nativeKind.entryId
                            val request = request(profile, experience, days, minutes, equipment)
                            val published = SetupTrainingPlanner.candidates(
                                SetupTrainingPlannerInput(
                                    reference = profile.reference,
                                    frequency = days,
                                    equipment = equipment.tokens,
                                    level = levelOf(experience),
                                    focus = TrainingFocus.FULL_BODY,
                                ),
                            )
                            if (published.none { it.id == nativeId }) {
                                failures.add("no publicado $nativeId ${profile.name}/$experience/$days/${equipment.id}/$minutes")
                                return@minuteLoop
                            }
                            publishedCandidates++
                            evaluatedCandidates++
                            val result = PlanCandidateEvaluator.evaluate(
                                request = request,
                                snapshot = snapshot,
                                entryId = nativeId,
                                engine = materializer(generator, experience, equipment),
                            )
                            when (result) {
                                PlanCandidateEvaluation.CatalogLoading ->
                                    failures.add("catálogo no listo ${profile.name}/$experience/$days/${equipment.id}/$minutes")

                                is PlanCandidateEvaluation.Ready -> {
                                    viableCandidates++
                                    if (result.inputKey != request.inputKey || result.planId != nativeId) {
                                        failures.add("Ready no corresponde a input/plan ${profile.name}/$experience/$days/${equipment.id}/$minutes")
                                    }
                                    val program = result.preparedPlan
                                    val recipe = program.sourceRecipe
                                    if (recipe == null) {
                                        failures.add("sin receta generada ${profile.name}/$experience/$days/${equipment.id}/$minutes")
                                    } else {
                                        if (recipe.weeks.size != 6 || recipe.claimedDaysPerWeek != days) {
                                            failures.add("ciclo/calendario incompleto ${profile.name}/$experience/$days/${equipment.id}/$minutes")
                                        }
                                        val findings = ProgramRecipeValidator.hardFindings(recipe, CatalogCompositionTestSupport.metadata)
                                        if (findings.isNotEmpty()) {
                                            failures.add("composition ${profile.name}/$experience/$days/${equipment.id}/$minutes: ${findings.take(4)}")
                                        }
                                        // B-02: contador de filas en la banda blanda (la política la ve como W2 SOFT
                                        // «> MRV») y de filas con el aviso de volumen alto del ajustador.
                                        val softBandFinding = ProgramRecipeValidator
                                            .validate(recipe, CatalogCompositionTestSupport.metadata)
                                            .firstOrNull { it.severity == CompositionSeverity.SOFT && it.rule == "W2" && it.message.contains("> MRV") }
                                        if (softBandFinding != null) {
                                            rowsInSoftBand.add("${profile.name}/$experience/$days/${equipment.id}/$minutes: ${softBandFinding.message}")
                                        }
                                        if (result.report?.highVolume.orEmpty().isNotEmpty()) rowsWithHighVolumeNotice++
                                        if (profile == Profile.COMPLETE_ATHLETE) {
                                            val firstWeek = recipe.weeks.firstOrNull()
                                            val slots = firstWeek?.days.orEmpty().flatMap { it.slots }
                                            val hasCardio = firstWeek?.days.orEmpty().any { it.cardioBlocks.isNotEmpty() }
                                            val hasStrength = slots.any { it.intent == com.example.kpkn.data.protocols.SlotIntent.F || it.intent == com.example.kpkn.data.protocols.SlotIntent.FV }
                                            val hasHypertrophy = slots.any { it.intent == com.example.kpkn.data.protocols.SlotIntent.H || it.intent == com.example.kpkn.data.protocols.SlotIntent.I }
                                            val hasPower = slots.any { it.intent == com.example.kpkn.data.protocols.SlotIntent.P }
                                            if (!hasCardio || !hasStrength || !hasHypertrophy || !hasPower) {
                                                failures.add("Atleta sin cuatro componentes reales ${profile.name}/$experience/$days/${equipment.id}/$minutes")
                                            }
                                        }
                                    }
                                    val contractFindings = ProgramExecutionContract.validate(program)
                                    if (contractFindings.isNotEmpty()) {
                                        failures.add("programa no ejecutable ${profile.name}/$experience/$days/${equipment.id}/$minutes: ${contractFindings.take(3)}")
                                    }
                                    val weeks = program.macrocycles.flatMap { it.blocks }
                                        .flatMap { it.mesocycles }.flatMap { it.weeks }
                                    if (weeks.size != 6 || weeks.any { it.sessions.size != days }) {
                                        failures.add("sesiones no cubren seis semanas/$days días ${profile.name}/$experience/$days/${equipment.id}/$minutes")
                                    }
                                    weeks.flatMap { it.sessions }.forEach { session ->
                                        val estimate = SessionDurationEstimator.estimate(session)
                                        if (session.targetDurationMinutes != estimate.totalMinutes ||
                                            estimate.maxSessionMinutes > minutes
                                        ) {
                                            failures.add("duración compartida inválida ${session.id}: $estimate / budget=$minutes")
                                        }
                                    }
                                }

                                is PlanCandidateEvaluation.Rejected -> {
                                    rejectedByReason.merge(result.reasonCode.name, 1, Int::plus)
                                    when (result.reasonCode) {
                                        PlanRejectionReason.APPARATUS_ABSENT,
                                        PlanRejectionReason.PROFILE_MISMATCH -> {
                                            if (!physicallyIncompatible(profile, equipment)) {
                                                failures.add("rechazo físico inesperado ${result.reasonCode} ${profile.name}/$experience/$days/${equipment.id}/$minutes: ${result.details}")
                                            } else {
                                                physicalOrTimeNegativePasses++
                                            }
                                        }

                                        // Un TIME_BUDGET con mínimo honesto es válido en la rejilla 20/60/100,
                                        // pero NO prueba que los testigos de §17.2 sigan siendo positivos: eso
                                        // lo fija `Q2_required_positives` (que rechaza también TIME_BUDGET).
                                        PlanRejectionReason.TIME_BUDGET -> {
                                            if (result.requiredMinutes == null || result.requiredMinutes <= minutes) {
                                                failures.add("TIME_BUDGET sin mínimo real > presupuesto ${profile.name}/$experience/$days/${equipment.id}/$minutes: $result")
                                            } else {
                                                physicalOrTimeNegativePasses++
                                            }
                                        }

                                        // Composition is a product rejection, not an infrastructure error.
                                        // It remains visible as a matrix failure until an explicit §17.2
                                        // incompatibility justifies it; no broad catch converts it to a pass.
                                        PlanRejectionReason.COMPOSITION ->
                                            failures.add("COMPOSITION no aceptado como cobertura ${profile.name}/$experience/$days/${equipment.id}/$minutes: ${result.details}")

                                        PlanRejectionReason.INTERNAL_MATERIALIZATION,
                                        PlanRejectionReason.CATALOG_NOT_READY,
                                        PlanRejectionReason.RECIPE_UNAVAILABLE,
                                        PlanRejectionReason.LEVEL_UNSUITABLE,
                                        PlanRejectionReason.FREQUENCY,
                                        PlanRejectionReason.SPLIT,
                                        PlanRejectionReason.APPARATUS_UNKNOWN,
                                        PlanRejectionReason.UNRESOLVED_CONFIGURATION,
                                        PlanRejectionReason.NO_VALID_SUBSTITUTION,
                                        PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE ->
                                            failures.add("rechazo no clasificado/esperado ${result.reasonCode} ${profile.name}/$experience/$days/${equipment.id}/$minutes: ${result.details}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val elapsedMs = (System.nanoTime() - elapsedStart) / 1_000_000L
        println(
            "[T006][Q2] totalInputs=$totalInputs publishedCandidates=$publishedCandidates " +
                "evaluatedCandidates=$evaluatedCandidates viableCandidates=$viableCandidates " +
                "rejectedByReason=$rejectedByReason physicalOrTimeNegativePasses=$physicalOrTimeNegativePasses " +
                "rowsInSoftBand=${rowsInSoftBand.size} highVolumeNotices=$rowsWithHighVolumeNotice " +
                "previewPassed=0 activationPassed=0 reopenPassed=0 postWorkoutPassed=0 elapsedMs=$elapsedMs",
        )
        assertEquals("Q2 fixture product", 2_304, totalInputs)
        assertEquals("cada input elige un candidato publicado", totalInputs, publishedCandidates)
        assertEquals("cada input recibe evaluación real", totalInputs, evaluatedCandidates)
        assertEquals("contabilizar resultados", totalInputs, viableCandidates + rejectedByReason.values.sum())
        assertTrue("Q2 encontró rechazo interno o producto no previsto:\n${failures.take(80).joinToString("\n")}", failures.isEmpty())
        // B-02: la banda blanda de glúteos es solo un seguro de último recurso. Ninguna de las 2.304 filas puede
        // depender de ella ni llevar el aviso de volumen alto; si aparece una, es una regresión del catálogo.
        assertTrue(
            "Q2 B-02: filas en la banda blanda de glúteos (debe ser 0):\n${rowsInSoftBand.take(40).joinToString("\n")}",
            rowsInSoftBand.isEmpty(),
        )
        assertEquals("Q2 B-02: filas con aviso de volumen alto", 0, rowsWithHighVolumeNotice)
    }

    /** Escenario de calibración de volumen de la pasada calibrada de Q2 (B-02). */
    private class CalibrationScenario(
        val label: String,
        val calibration: Calibration,
        val recommendations: List<VolumeRecommendation>,
    )

    /**
     * B-02 · pasada CALIBRADA de Q2. El wizard real pasa `volumeRecommendations` (SetupWizardViewModel) y
     * `budgets()` usa `min(personal, global)`, así que las 2.304 filas de Q2 se repiten sin calibrar y con el
     * PISO y el TECHO de [VolumeCalibrationEngine] (los extremos de sus recomendaciones). Cada fila se genera
     * DOS veces con el mismo generador: con la tolerancia de glúteos apagada (comportamiento anterior a B-02)
     * y encendida. Toda fila viable antes debe salir IDÉNTICA después (programa e informe): lo único que la
     * banda puede mover es una fila que antes se rechazaba con COMPOSITION por pasarse de glúteos hasta 1,5
     * series (fila «en banda»). Esas filas se cuentan y se detallan en la salida, no se fuerzan a cero.
     */
    @Test
    fun Q2_calibrated_pass_changes_no_viable_row() = runBlocking {
        val fixtures = legacyFixtures()
        val repository = InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } }
        val legacyPersonalizer = SimpleCyclePersonalizer(repository, glutesSoftBand = 0.0)
        val bandPersonalizer = SimpleCyclePersonalizer(repository)
        val scenarios = listOf(
            CalibrationScenario("sin-calibrar", Calibration.UNCALIBRATED, emptyList()),
            CalibrationScenario(
                "piso",
                Calibration.CALIBRATED,
                VolumeCalibrationEngine.calculate(TrainingStyle.POWERLIFTER, 1, 1, 1, 1).recommendations,
            ),
            CalibrationScenario(
                "techo",
                Calibration.CALIBRATED,
                VolumeCalibrationEngine.calculate(TrainingStyle.BODYBUILDER, 3, 3, 3, 3).recommendations,
            ),
        )
        val problems = mutableListOf<String>()
        var changedViableTotal = 0

        for (scenario in scenarios) {
            var rows = 0
            var viableBefore = 0
            var viableAfter = 0
            var changedViable = 0
            var rescued = 0
            var policyBandRows = 0
            var noticeRows = 0
            val reasonsBefore = linkedMapOf<String, Int>()
            val reasonsAfter = linkedMapOf<String, Int>()
            val rescuedRows = mutableListOf<String>()
            val started = System.nanoTime()

            for (profile in profiles) {
                for (experience in experiences) {
                    for (days in 1..6) {
                        for (equipment in fixtures) {
                            for (minutes in listOf(20, 60, 100)) {
                                rows++
                                val label = "${scenario.label}/${profile.name}/$experience/${days}d/${equipment.id}/${minutes}min"
                                val programId = "t006-cal-$rows"
                                val input = PersonalizerInput(
                                    catalogEntryId = profile.nativeKind.entryId,
                                    focus = TrainingFocus.FULL_BODY,
                                    frequency = days,
                                    weekdays = weekdays(days),
                                    equipment = emptySet(),
                                    level = levelOf(experience),
                                    availableMinutes = minutes,
                                    cardio = if (profile == Profile.COMPLETE_ATHLETE) {
                                        CardioPreference(CardioType.WALK, 10)
                                    } else {
                                        null
                                    },
                                    calibration = scenario.calibration,
                                    volumeRecommendations = scenario.recommendations,
                                )
                                val options = TrainingOptions(availability = equipment.availability)
                                val legacyResult = legacyPersonalizer.personalize(programId, input, options)
                                val bandResult = bandPersonalizer.personalize(programId, input, options)

                                if (legacyResult.program != null) {
                                    viableBefore++
                                } else {
                                    reasonsBefore.merge(legacyResult.report.reasonCode ?: "SIN_MOTIVO", 1, Int::plus)
                                }
                                if (bandResult.program != null) {
                                    viableAfter++
                                } else {
                                    reasonsAfter.merge(bandResult.report.reasonCode ?: "SIN_MOTIVO", 1, Int::plus)
                                }

                                when {
                                    // Fila viable antes: debe salir exactamente igual (programa e informe).
                                    legacyResult.program != null -> if (bandResult != legacyResult) {
                                        changedViable++
                                        problems.add(
                                            "$label: una fila viable cambió con la banda (programa igual=" +
                                                "${bandResult.program == legacyResult.program}, informe igual=" +
                                                "${bandResult.report == legacyResult.report})",
                                        )
                                    }

                                    // Fila rechazada antes y entregada ahora: solo puede ser COMPOSITION de glúteos en banda.
                                    bandResult.program != null -> {
                                        rescued++
                                        val gluteNotice = bandResult.report.highVolume
                                            .firstOrNull { it.muscle == VolumeSoftBand.GLUTES_MUSCLE }
                                        rescuedRows.add("$label glúteos=${gluteNotice?.weeklySets}")
                                        if (legacyResult.report.reasonCode != "COMPOSITION" || gluteNotice == null) {
                                            problems.add(
                                                "$label: fila rescatada fuera de lo esperado " +
                                                    "(antes=${legacyResult.report.reasonCode}, aviso=$gluteNotice)",
                                            )
                                        }
                                    }

                                    // Rechazada antes y después: mismo motivo y mismos minutos mínimos.
                                    else -> if (legacyResult.report.reasonCode != bandResult.report.reasonCode ||
                                        legacyResult.report.maxSessionMinutes != bandResult.report.maxSessionMinutes
                                    ) {
                                        problems.add(
                                            "$label: el rechazo cambió (antes=${legacyResult.report.reasonCode}/" +
                                                "${legacyResult.report.maxSessionMinutes}, después=" +
                                                "${bandResult.report.reasonCode}/${bandResult.report.maxSessionMinutes})",
                                        )
                                    }
                                }

                                // Todo plan entregado: sin HARD y ≤ 17,5 series PRINCIPALES de glúteos.
                                val delivered = bandResult.program
                                if (delivered != null) {
                                    val recipe = requireNotNull(delivered.sourceRecipe)
                                    val findings = ProgramRecipeValidator.validate(recipe, CatalogCompositionTestSupport.metadata)
                                    val hard = findings.filter { it.severity == CompositionSeverity.HARD }
                                    if (hard.isNotEmpty()) problems.add("$label: plan entregado con hallazgos HARD: ${hard.take(3)}")
                                    val policySeesBand = findings.any {
                                        it.severity == CompositionSeverity.SOFT && it.rule == "W2" &&
                                            it.message.contains("GLUTES") && it.message.contains("> MRV")
                                    }
                                    val fitterNotice = bandResult.report.highVolume
                                        .any { it.muscle == VolumeSoftBand.GLUTES_MUSCLE }
                                    if (policySeesBand) policyBandRows++
                                    if (fitterNotice) noticeRows++
                                    if (policySeesBand && !fitterNotice) {
                                        problems.add("$label: la política ve volumen alto de glúteos y el ajustador no lo avisó")
                                    }
                                    val glutes = bandResult.report.muscles
                                        .firstOrNull { it.muscle == VolumeSoftBand.GLUTES_MUSCLE }
                                    if (glutes != null && glutes.directSets > 17.5 + 0.001) {
                                        problems.add("$label: glúteos principales ${glutes.directSets} > techo blando 17,5")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
            println(
                "[T006][Q2-cal] escenario=${scenario.label} filas=$rows viablesAntes=$viableBefore " +
                    "viablesDespues=$viableAfter filasViablesQueCambian=$changedViable " +
                    "filasRescatadasPorLaBanda=$rescued rechazosAntes=${reasonsBefore.toSortedMap()} " +
                    "rechazosDespues=${reasonsAfter.toSortedMap()} bandaSegunPolitica=$policyBandRows " +
                    "avisosDelAjustador=$noticeRows elapsedMs=$elapsedMs",
            )
            if (rescuedRows.isNotEmpty()) {
                println("[T006][Q2-cal] escenario=${scenario.label} filas en banda: ${rescuedRows.take(80)}")
            }
            assertEquals("Q2 calibrada (${scenario.label}): la rejilla de Q2", 2_304, rows)
            changedViableTotal += changedViable
        }

        assertEquals("Q2 calibrada: filas viables que cambian con la banda de glúteos", 0, changedViableTotal)
        assertTrue(
            "Q2 calibrada: incoherencias entre antes, después y la política:\n${problems.take(60).joinToString("\n")}",
            problems.isEmpty(),
        )
    }

    /**
     * Testigo positivo obligatorio de §17.2: toda combinación de [experiences] × [days] × [fixtures]
     * con [minutes] DEBE terminar en programa real. Q2 acepta TIME_BUDGET en cualquier fila con
     * `requiredMinutes > minutos` y no puede detectar que uno de estos testigos degrade a rechazo;
     * aquí ni TIME_BUDGET ni COMPOSITION ni ningún otro rechazo es aceptable.
     */
    private data class RequiredPositive(
        val witness: String,
        val profile: Profile,
        val experiences: List<SetupExperience>,
        val days: List<Int>,
        val minutes: Int,
        val fixtures: List<String>,
        val note: String,
    )

    /**
     * §17.2 #1, #2, #3, #4, #5 y #8 como filas del evaluador real (#7 —llegar al programa por RIR
     * sin 1RM ni marcas— se comprueba en TODAS las filas Ready). NEW = «principiante»; los niveles
     * que el plan llama «todos» se enumeran explícitamente. No incluye `3d/20` de Atleta (§17.2 #5
     * lo declara evaluación real sin éxito forzado) ni PHUL/PHAT (#6, no son perfiles propios).
     */
    private fun requiredPositives(): List<RequiredPositive> = listOf(
        RequiredPositive("#1", Profile.POWERBUILDING, SetupExperience.entries, listOf(5), 60, listOf("E5", "E6"),
            "Fuerza y músculo, gimnasio E5/E6, 5 días / 60 min, todos los niveles: al menos el propio real"),
        RequiredPositive("#2", Profile.STRENGTH, listOf(SetupExperience.NEW, SetupExperience.ADVANCED), listOf(3, 5, 6), 60, listOf("E4"),
            "Fuerza E4, principiante y avanzado, 3 días / 60 min; 5d y 6d / 60 no se bloquean"),
        RequiredPositive("#3", Profile.MUSCLE, listOf(SetupExperience.NEW), listOf(2, 3), 30, listOf("E0", "E1", "E2"),
            "Músculo E0/E1/E2 principiante 2d/30 y 3d/30"),
        RequiredPositive("#3", Profile.MUSCLE, listOf(SetupExperience.NEW), listOf(1, 6), 30, listOf("E0"),
            "Músculo E0 1d/30 y 6d/30 tienen receta"),
        RequiredPositive("#4", Profile.POWERBUILDING, listOf(SetupExperience.NEW, SetupExperience.INTERMEDIATE), listOf(3), 45, listOf("E2"),
            "Fuerza y músculo E2 (mancuernas sin banco) 3d/45"),
        RequiredPositive("#4", Profile.POWERBUILDING, listOf(SetupExperience.NEW, SetupExperience.INTERMEDIATE), listOf(1), 60, listOf("E4", "E5"),
            "Fuerza y músculo E4/E5 1d/60 tiene receta"),
        RequiredPositive("#5", Profile.COMPLETE_ATHLETE, listOf(SetupExperience.NEW), listOf(3, 5), 45, listOf("E0", "E2", "E6"),
            "Atleta E0/E2/E6 principiante 3d/45 y 5d/45: cuatro componentes semanales"),
        RequiredPositive("#5", Profile.COMPLETE_ATHLETE, listOf(SetupExperience.NEW), listOf(1, 2), 60, listOf("E0", "E2", "E6"),
            "Atleta E0/E2/E6 principiante 1d/60 y 2d/60"),
        RequiredPositive("#8", Profile.MUSCLE, listOf(SetupExperience.NEW, SetupExperience.INTERMEDIATE), listOf(4, 5, 6), 60, listOf("E2", "E4"),
            "Músculo E2/E4 4–6 días / 60 min: seis semanas reales"),
        RequiredPositive("#8", Profile.COMPLETE_ATHLETE, listOf(SetupExperience.NEW, SetupExperience.INTERMEDIATE, SetupExperience.ADVANCED), listOf(6), 60, listOf("E0", "E2"),
            "Atleta E0/E2 6 días / 60 min: seis semanas con cardio-only real"),
    )

    /** Contrato de un positivo Ready: receta, seis semanas, composición, ejecución, duración y RIR (#7). */
    private fun requiredPositiveContractProblems(
        label: String,
        positive: RequiredPositive,
        days: Int,
        ready: PlanCandidateEvaluation.Ready,
    ): List<String> {
        val problems = mutableListOf<String>()
        val program = ready.preparedPlan
        val recipe = program.sourceRecipe
        if (recipe == null) {
            problems.add("$label: sin receta generada")
            return problems
        }
        if (recipe.weeks.size != 6 || recipe.claimedDaysPerWeek != days) {
            problems.add("$label: ciclo/calendario incompleto (semanas=${recipe.weeks.size}, días=${recipe.claimedDaysPerWeek})")
        }
        val findings = ProgramRecipeValidator.hardFindings(recipe, CatalogCompositionTestSupport.metadata)
        if (findings.isNotEmpty()) problems.add("$label: composition ${findings.take(4)}")
        val contractFindings = ProgramExecutionContract.validate(program)
        if (contractFindings.isNotEmpty()) problems.add("$label: programa no ejecutable ${contractFindings.take(3)}")
        val weeks = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
        if (weeks.size != 6 || weeks.any { it.sessions.size != days }) {
            problems.add("$label: las seis semanas no materializan $days sesiones cada una")
        }
        weeks.flatMap { it.sessions }.forEach { session ->
            val estimate = SessionDurationEstimator.estimate(session)
            if (session.targetDurationMinutes != estimate.totalMinutes || estimate.maxSessionMinutes > positive.minutes) {
                problems.add("$label: duración compartida inválida ${session.id}: $estimate / budget=${positive.minutes}")
            }
        }
        // §17.2 #7: se llega al programa por RIR, sin 1RM ni marcas; las series de trabajo
        // ordinarias llevan RIR y la potencia (SPEED) su consigna propia.
        val firstWeekSlots = recipe.weeks.firstOrNull()?.days.orEmpty().flatMap { it.slots }
        val withoutRir = firstWeekSlots.filter { it.intent != SlotIntent.P }
            .filter { slot -> slot.sets.filter { !it.isWarmup }.any { it.rir == null } }
        if (withoutRir.isNotEmpty()) {
            problems.add("$label: series sin RIR (#7) en ${withoutRir.map { it.id }}")
        }
        if (positive.profile == Profile.COMPLETE_ATHLETE) {
            val hasCardio = recipe.weeks.firstOrNull()?.days.orEmpty().any { it.cardioBlocks.isNotEmpty() }
            val hasStrength = firstWeekSlots.any { it.intent == SlotIntent.F || it.intent == SlotIntent.FV }
            val hasHypertrophy = firstWeekSlots.any { it.intent == SlotIntent.H || it.intent == SlotIntent.I }
            val hasPower = firstWeekSlots.any { it.intent == SlotIntent.P }
            if (!hasCardio || !hasStrength || !hasHypertrophy || !hasPower) {
                problems.add("$label: Atleta sin cuatro componentes reales (cardio=$hasCardio fuerza=$hasStrength músculo=$hasHypertrophy potencia=$hasPower)")
            }
            if (days == 6) {
                val cardioOnly = recipe.weeks.firstOrNull()?.days.orEmpty()
                    .filter { it.sessionKind == RecipeSessionKind.CARDIO }
                if (cardioOnly.size != 2 || cardioOnly.any { it.cardioBlocks.isEmpty() || it.slots.isNotEmpty() }) {
                    problems.add("$label: Atleta 6d sin dos días cardio-only reales (${cardioOnly.size})")
                }
            }
        }
        return problems
    }

    /**
     * Q2 – positivos obligatorios de §17.2 (#1,#2,#3,#4,#5,#7,#8, incluidos 30 y 45 min). Cuenta
     * APARTE de las 2.304 filas de Q2: falla ante TIME_BUDGET y ante COMPOSITION (y ante cualquier
     * otro rechazo), que es exactamente lo que Q2 deja pasar como negativo honesto. Un rechazo
     * físico/temporal sigue siendo válido en Q2; aquí cada fila es un testigo de entrega.
     */
    @Test
    fun Q2_required_positives() = runBlocking {
        val fixtures = legacyFixtures().associateBy { it.id }
        val generator = personalizer()
        val failures = mutableListOf<String>()
        val rowsByWitness = linkedMapOf<String, Int>()
        val readyByWitness = linkedMapOf<String, Int>()
        val rejectedByReason = linkedMapOf<String, Int>()
        var totalRows = 0
        var published = 0
        var ready = 0
        val elapsedStart = System.nanoTime()

        requiredPositives().forEach { positive ->
            positive.experiences.forEach { experience ->
                positive.days.forEach { days ->
                    positive.fixtures.forEach fixtureLoop@{ fixtureId ->
                        val equipment = requireNotNull(fixtures[fixtureId]) { "fixture inexistente $fixtureId" }
                        totalRows++
                        rowsByWitness.merge(positive.witness, 1, Int::plus)
                        val label = "${positive.witness} ${positive.profile.name}/$experience/${days}d/${positive.minutes}min/$fixtureId"
                        val nativeId = positive.profile.nativeKind.entryId
                        val candidates = SetupTrainingPlanner.candidates(
                            SetupTrainingPlannerInput(
                                reference = positive.profile.reference,
                                frequency = days,
                                equipment = equipment.tokens,
                                level = levelOf(experience),
                                focus = TrainingFocus.FULL_BODY,
                            ),
                        )
                        if (candidates.none { it.id == nativeId }) {
                            failures.add("$label: $nativeId no se publica para este input")
                            return@fixtureLoop
                        }
                        published++
                        when (
                            val result = PlanCandidateEvaluator.evaluate(
                                request = request(positive.profile, experience, days, positive.minutes, equipment),
                                snapshot = snapshot,
                                entryId = nativeId,
                                engine = materializer(generator, experience, equipment),
                            )
                        ) {
                            PlanCandidateEvaluation.CatalogLoading ->
                                failures.add("$label: catálogo no listo")

                            is PlanCandidateEvaluation.Ready -> {
                                ready++
                                readyByWitness.merge(positive.witness, 1, Int::plus)
                                failures.addAll(requiredPositiveContractProblems(label, positive, days, result))
                            }

                            is PlanCandidateEvaluation.Rejected -> {
                                rejectedByReason.merge(result.reasonCode.name, 1, Int::plus)
                                failures.add(
                                    "$label: positivo obligatorio rechazado con ${result.reasonCode} " +
                                        "(requiredMinutes=${result.requiredMinutes}, etapa=${result.stage}): ${result.details}",
                                )
                            }
                        }
                    }
                }
            }
        }

        val elapsedMs = (System.nanoTime() - elapsedStart) / 1_000_000L
        println(
            "[T006][Q2-required] totalInputs=$totalRows publishedCandidates=$published " +
                "evaluatedCandidates=$published viableCandidates=$ready rejectedByReason=$rejectedByReason " +
                "rowsByWitness=$rowsByWitness readyByWitness=$readyByWitness " +
                "(fuera de las 2.304 filas de Q2) elapsedMs=$elapsedMs",
        )
        assertEquals(
            "cada testigo de §17.2 aporta filas",
            setOf("#1", "#2", "#3", "#4", "#5", "#8"),
            rowsByWitness.keys,
        )
        assertEquals("cada fila obligatoria se publica y se evalúa", totalRows, published)
        assertTrue(
            "Q2_required_positives: testigos de §17.2 sin programa real o con contrato roto " +
                "(TIME_BUDGET/COMPOSITION no son aceptables aquí):\n${failures.take(80).joinToString("\n")}",
            failures.isEmpty(),
        )
        assertEquals("todos los positivos obligatorios terminan en programa", totalRows, ready)
    }

    /**
     * Q3: full 20..100 scan for each distinct native recipe/material path below.
     * Adjacent outcome changes are reported with t−1/t/t+1 measured from the
     * real generator/evaluator, so no monotonicity assumption is baked in.
     */
    @Test
    fun Q3_fitter_viability_neighbors_and_cardio_default_boundaries() = runBlocking {
        val fixtures = legacyFixtures().associateBy { it.id }
        val generator = personalizer()
        val cases = listOf(
            Triple(Profile.STRENGTH, SetupExperience.NEW, "E4" to 3),
            Triple(Profile.MUSCLE, SetupExperience.INTERMEDIATE, "E2" to 3),
            Triple(Profile.POWERBUILDING, SetupExperience.INTERMEDIATE, "E2" to 3),
            Triple(Profile.COMPLETE_ATHLETE, SetupExperience.NEW, "E0" to 3),
        )
        var totalInputs = 0
        var publishedCandidates = 0
        var evaluatedCandidates = 0
        var viableCandidates = 0
        var totalBoundaries = 0
        val rejectedByReason = linkedMapOf<String, Int>()
        val boundaryReport = mutableListOf<String>()

        cases.forEach { (profile, experience, fixtureAndDays) ->
            val (fixtureId, days) = fixtureAndDays
            val equipment = requireNotNull(fixtures[fixtureId])
            val outcomes = linkedMapOf<Int, Boolean>()
            (20..100).forEach { minutes ->
                totalInputs++
                publishedCandidates++
                evaluatedCandidates++
                when (val evaluation = PlanCandidateEvaluator.evaluate(
                    request(profile, experience, days, minutes, equipment),
                    snapshot,
                    profile.nativeKind.entryId,
                    materializer(generator, experience, equipment),
                )) {
                    PlanCandidateEvaluation.CatalogLoading -> error("Q3 catálogo pendiente en ${profile.name}/$fixtureId/$days/$minutes")
                    is PlanCandidateEvaluation.Ready -> {
                        viableCandidates++
                        outcomes[minutes] = true
                    }
                    is PlanCandidateEvaluation.Rejected -> {
                        rejectedByReason.merge(evaluation.reasonCode.name, 1, Int::plus)
                        check(evaluation.reasonCode == PlanRejectionReason.TIME_BUDGET) {
                            "Q3 no esperaba ${evaluation.reasonCode} ${profile.name}/$fixtureId/$days/$minutes: ${evaluation.details}"
                        }
                        check(requireNotNull(evaluation.requiredMinutes) > minutes) {
                            "Q3 el mínimo real debe superar el presupuesto: $evaluation"
                        }
                        outcomes[minutes] = false
                    }
                }
            }
            val changedAt = (21..99).filter { minute -> outcomes.getValue(minute - 1) != outcomes.getValue(minute) }
            changedAt.forEach { minute ->
                totalBoundaries++
                val neighbors = listOf(minute - 1, minute, minute + 1)
                val observed = neighbors.joinToString(",") { "$it:${if (outcomes.getValue(it)) "READY" else "TIME_BUDGET"}" }
                boundaryReport += "${profile.name}/$fixtureId/${days}d@$minute[$observed]"
                // The complete per-minute scan above is the independent oracle;
                // this local assertion keeps every observed neighbor present.
                assertTrue(neighbors.all(outcomes::containsKey))
            }
        }

        // Real generation at each default transition. 30→10, 45/59→15, 60→20.
        val athlete = Profile.COMPLETE_ATHLETE
        val bodyweight = requireNotNull(fixtures["E0"])
        val cardioRows = linkedMapOf<Int, Int>()
        listOf(30, 45, 59, 60).forEach { minutes ->
            totalInputs++
            publishedCandidates++
            evaluatedCandidates++
            val evaluation = PlanCandidateEvaluator.evaluate(
                request(
                    profile = athlete,
                    experience = SetupExperience.NEW,
                    days = 1,
                    minutes = minutes,
                    equipment = bodyweight,
                ),
                snapshot,
                athlete.nativeKind.entryId,
                PlanMaterializationPort { entry, candidate ->
                    val result = generator.personalize(
                        "t006-cardio-${minutes}",
                        PersonalizerInput(
                            catalogEntryId = entry.id,
                            focus = candidate.focus,
                            frequency = candidate.daysPerWeek,
                            weekdays = weekdays(1),
                            equipment = emptySet(),
                            level = CatalogLevel.BEGINNER,
                            availableMinutes = minutes,
                            cardio = null,
                        ),
                        TrainingOptions(availability = bodyweight.availability),
                    )
                    val program = result.program
                    if (program == null) {
                        val expectedMinutes = requireNotNull(NativeCardioDefaults.defaultBlockMinutes(minutes))
                        // A1: el mapeo de producto es el del wizard (NativePlanFailureMapper), no un `when` propio.
                        val typed = NativePlanFailureMapper.typedFailure(result.report)
                        if (typed == null || typed.reason != PlanRejectionReason.TIME_BUDGET) {
                            error("cardio $minutes: rechazo no temporal ${result.report.reasonCode} ${result.report.limitations}")
                        }
                        throw PlanMaterializationException(
                            typed.stage,
                            typed.reason,
                            typed.message.orEmpty() + " (defaultCardio=${expectedMinutes}m)",
                            requiredMinutes = typed.requiredMinutes,
                        )
                    }
                    PlanMaterializationOutcome(program, program.sourceRecipe, result.report)
                },
            )
            when (evaluation) {
                is PlanCandidateEvaluation.Ready -> {
                    viableCandidates++
                    val blocks = requireNotNull(evaluation.preparedPlan.sourceRecipe).weeks.first().days
                        .flatMap { it.cardioBlocks }
                    val actualMinutes = blocks.map { it.details.effectiveDurationSeconds() / 60 }.maxOrNull()
                        ?: error("Atleta sin cardio real a $minutes min")
                    val expected = NativeCardioDefaults.defaultBlockMinutes(minutes)
                    assertEquals("default cardio a $minutes min", expected, actualMinutes)
                    cardioRows[minutes] = actualMinutes
                }
                is PlanCandidateEvaluation.Rejected -> {
                    rejectedByReason.merge(evaluation.reasonCode.name, 1, Int::plus)
                    assertEquals("el borde de duración es TIME_BUDGET", PlanRejectionReason.TIME_BUDGET, evaluation.reasonCode)
                    assertTrue("mínimo real > $minutes: $evaluation", requireNotNull(evaluation.requiredMinutes) > minutes)
                    val expected = NativeCardioDefaults.defaultBlockMinutes(minutes)
                    assertTrue(
                        "el rechazo conserva el default real usado: $evaluation",
                        evaluation.details.orEmpty().contains("defaultCardio=${expected}m"),
                    )
                    cardioRows[minutes] = NativeCardioDefaults.defaultBlockMinutes(minutes)
                }
                PlanCandidateEvaluation.CatalogLoading -> error("catálogo cardio pendiente a $minutes min")
            }
        }

        println(
            "[T006][Q3] totalInputs=$totalInputs publishedCandidates=$publishedCandidates " +
                "evaluatedCandidates=$evaluatedCandidates viableCandidates=$viableCandidates " +
                "rejectedByReason=$rejectedByReason boundaries=$totalBoundaries " +
                "boundaryNeighbors=${boundaryReport.joinToString(";")} cardioDefaults=$cardioRows " +
                "previewPassed=0 activationPassed=0 reopenPassed=0 postWorkoutPassed=0",
        )
        assertEquals("cardio defaults 30/45/59/60", mapOf(30 to 10, 45 to 15, 59 to 15, 60 to 20), cardioRows)
        assertTrue("se observaron cambios de viabilidad reales: $boundaryReport", totalBoundaries > 0)
    }

    private fun physicallyIncompatible(profile: Profile, equipment: EquipmentFixture): Boolean = when (profile) {
        Profile.STRENGTH -> equipment.id !in setOf("E4", "E5", "E6")
        Profile.POWERBUILDING -> "dumbbells" !in equipment.tokens &&
            !setOf("barbell", "rack", "bench").all { it in equipment.tokens }
        Profile.MUSCLE, Profile.COMPLETE_ATHLETE -> false
    }
}
