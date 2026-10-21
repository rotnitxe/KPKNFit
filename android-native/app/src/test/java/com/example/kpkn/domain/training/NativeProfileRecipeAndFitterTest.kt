package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.RecipeSessionKind
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.definitions.NativeCardioEscalation
import com.example.kpkn.data.protocols.definitions.NativeCandidateTable
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.protocols.definitions.NativeSlotKey
import com.example.kpkn.data.protocols.definitions.NativeProgressionContracts
import com.example.kpkn.data.protocols.definitions.NativeProgressionOutcome
import com.example.kpkn.data.protocols.definitions.NativeDayArchetype
import com.example.kpkn.data.protocols.definitions.NativeDoseLevel
import com.example.kpkn.data.protocols.definitions.NativeDoseTable
import com.example.kpkn.data.protocols.definitions.NativeProfileCalendars
import com.example.kpkn.data.protocols.definitions.parseNativeArchetype
import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * T-004a (paquete F): los cuatro planes propios como recetas ejecutables para
 * dias 1..6 con las dosis/calendarios/progresion de SS11-12, AC-F2 (fitter) y
 * AC-F4 (progresion).
 */
class NativeProfileRecipeAndFitterTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        private val BARBELL = setOf("bodyweight", "barbell", "rack", "bench")
        private val DUMBBELLS = setOf("bodyweight", "dumbbells")
        private val BODYWEIGHT = setOf("bodyweight")
        private val SUPPORTED_BODYWEIGHT_PULL = setOf("bodyweight", "pull_up_bar", "low_bar_support")
    }

    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } },
    )

    private fun generate(
        entryId: String,
        days: Int,
        level: CatalogLevel,
        equipment: Set<String>,
        minutes: Int,
        cardioPreference: CardioPreference? = null,
        volumeRecommendations: List<VolumeRecommendation> = emptyList(),
        programId: String = "np-${entryId.removePrefix("native:")}-$days-${level.name.lowercase()}-$minutes",
    ): PersonalizationResult = personalizer().personalize(
        programId,
        PersonalizerInput(
            catalogEntryId = entryId,
            focus = TrainingFocus.FULL_BODY,
            frequency = days,
            equipment = equipment,
            level = level,
            availableMinutes = minutes,
            cardio = cardioPreference,
            volumeRecommendations = volumeRecommendations,
        ),
    )

    private fun weeksOf(program: com.example.kpkn.data.models.Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    private fun sessionsOf(program: com.example.kpkn.data.models.Program) = weeksOf(program).flatMap { it.sessions }

    private fun requireProgram(result: PersonalizationResult, context: String): com.example.kpkn.data.models.Program {
        assertNotNull("$context: ${result.report.limitations} (${result.report.reasonCode})", result.program)
        return result.program!!
    }

    private fun com.example.kpkn.data.protocols.SlotRecipe.workingSetsForTest() = sets.filter { !it.isWarmup }

    /** Matriz 11.1-12: cuatro perfiles x dias 1..6 x niveles x material. */
    private val matrix: List<Triple<String, Set<String>, Int>> = buildList {
        NativeProfileKind.STRENGTH.entryId.let { id ->
            (1..6).forEach { days -> add(Triple(id, BARBELL, days)) }
        }
        NativeProfileKind.MUSCLE.entryId.let { id ->
            (1..6).forEach { days -> add(Triple(id, BODYWEIGHT, days)) }
            add(Triple(id, DUMBBELLS, 4))
            add(Triple(id, DUMBBELLS, 6))
        }
        NativeProfileKind.POWERBUILDING.entryId.let { id ->
            (1..6).forEach { days -> add(Triple(id, BARBELL, days)) }
            add(Triple(id, DUMBBELLS, 3))
            add(Triple(id, DUMBBELLS, 5))
        }
        NativeProfileKind.COMPLETE_ATHLETE.entryId.let { id ->
            (1..6).forEach { days -> add(Triple(id, BODYWEIGHT, days)) }
            add(Triple(id, BARBELL, 2))
            add(Triple(id, BARBELL, 4))
        }
    }

    @Test
    fun matrix_materializes_feasible_programs_without_exceeding_glute_mrv() {
        val failures = mutableListOf<String>()
        matrix.forEach { (entryId, equipment, days) ->
            // ADVANCED comparte la tabla de dosis de INTERMEDIATE pero se audita aparte: un
            // cambio futuro de dosis por nivel no puede romper el MRV de glúteos sin que se vea.
            listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED).forEach { level ->
                val context = "$entryId/$days dias/${level.name}/$equipment"
                val result = generate(entryId, days, level, equipment, minutes = 100)
                // MUSCLE E0 6d/30 and COMPLETE_ATHLETE E0 6d/60 are explicit
                // §17.2 positives. Never accept their COMPOSITION result as an
                // expected negative; let the strict matrix assertion below fail.
                if (result.program == null) {
                    failures += "$context -> ${result.report.limitations} (${result.report.reasonCode})"
                    return@forEach
                }
                val program = result.program!!
                val recipe = program.sourceRecipe
                if (recipe == null) {
                    failures += "$context -> sin sourceRecipe"
                    return@forEach
                }
                val problems = mutableListOf<String>()
                result.report.muscles.forEach { row ->
                    val ceiling = VolumeSoftBand.softCeilingFor(row.muscle, row.mrv)
                    if (row.directSets > ceiling + 0.001) problems += "${row.muscle}: principales ${row.directSets} > techo $ceiling"
                }
                if (recipe.weeks.size != 6) problems += "semanas=${recipe.weeks.size}"
                if (recipe.claimedDaysPerWeek != days) problems += "claimedDays=${recipe.claimedDaysPerWeek}"
                if (recipe.repeats) problems += "repeats=true"
                val expectedLevel = when (level) {
                    CatalogLevel.BEGINNER -> "principiante"
                    CatalogLevel.INTERMEDIATE -> "intermedio"
                    CatalogLevel.ADVANCED -> "avanzado"
                }
                if (recipe.claimedLevel != expectedLevel) problems += "claimedLevel=${recipe.claimedLevel}"
                val expectedProfile = if (entryId == NativeProfileKind.COMPLETE_ATHLETE.entryId) {
                    RecipeCompositionProfile.MIXED_CARDIO
                } else {
                    RecipeCompositionProfile.NATIVE_COMPACT
                }
                if (recipe.compositionProfile != expectedProfile) problems += "perfil=${recipe.compositionProfile}"
                ProgramRecipeValidator.hardFindings(recipe, metadata).forEach { problems += "${it.rule} ${it.scope}: ${it.message}" }
                val weeks = weeksOf(program)
                weeks.forEachIndexed { index, week ->
                    if (week.sessions.size != days) problems += "semana ${index + 1} con ${week.sessions.size} sesiones"
                    week.sessions.forEach { session ->
                        val duration = session.targetDurationMinutes
                        if (duration == null || duration !in 1..100) problems += "sesion ${session.id} duracion=$duration"
                    }
                }
                if (weeks.size == 6) {
                    weeks.take(5).forEach { week ->
                        if (week.executionKind != WeekExecutionKind.TRAINING) {
                            problems += "semana ${week.progressionIndex} no es TRAINING"
                        }
                    }
                    if (weeks.last().executionKind != WeekExecutionKind.DELOAD) problems += "semana 6 no es DELOAD"
                } else {
                    problems += "semanas materializadas=${weeks.size}"
                }
                ProgramExecutionContract.validate(program).forEach { problems += "ejecucion: ${it.message}" }
                if (problems.isNotEmpty()) failures += "$context -> $problems"
                // Determinismo: mismos inputs -> exactamente el mismo programa.
                val again = generate(entryId, days, level, equipment, minutes = 100)
                if (again.program != program) failures += "$context -> no determinista"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Misma matriz con 20 y 60 min y los tres niveles: cada fila es un programa que cabe en el
     * presupuesto con el glúteo dentro del MRV real en las seis semanas, o un TIME_BUDGET tipado con
     * un mínimo mayor que el presupuesto. COMPOSITION nunca es aceptable (sería un MRV que el fitter
     * no pudo corregir). Los positivos obligatorios de §17.2 los fija `Q2_required_positives`.
     */
    @Test
    fun matrix_at_20_and_60_minutes_never_returns_composition_or_exceeds_glute_mrv() {
        val lookup = catalog.toLegacyConfigurationLookup().values.toList()
        val realGluteMrv = com.example.kpkn.data.programs.VolumeLandmarks.byGroup
            .getValue(com.example.kpkn.data.programs.KpknMuscleGroup.GLUTES).mrv
        val failures = mutableListOf<String>()
        var programs = 0
        var timeBudgets = 0
        matrix.forEach { (entryId, equipment, days) ->
            listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED).forEach { level ->
                listOf(20, 60).forEach { minutes ->
                    val context = "$entryId/$days dias/${level.name}/$minutes min/$equipment"
                    val result = generate(entryId, days, level, equipment, minutes = minutes)
                    val program = result.program
                    if (program == null) {
                        val required = result.report.maxSessionMinutes
                        if (result.report.reasonCode != "TIME_BUDGET" || required == null || required <= minutes) {
                            failures += "$context -> rechazo no temporal (${result.report.reasonCode}, mínimo=$required): " +
                                result.report.limitations
                        } else {
                            timeBudgets++
                        }
                        return@forEach
                    }
                    programs++
                    val weeks = weeksOf(program)
                    if (weeks.size != 6) failures += "$context -> semanas materializadas=${weeks.size}"
                    val recipe = program.sourceRecipe
                    if (recipe == null) {
                        failures += "$context -> sin sourceRecipe"
                    } else {
                        ProgramRecipeValidator.hardFindings(recipe, metadata)
                            .forEach { failures += "$context -> ${it.rule} ${it.scope}: ${it.message}" }
                    }
                    weeks.forEachIndexed { index, week ->
                        val glutes = VolumeCalculator.calculateRoleSeparatedMuscleVolume(week.sessions, lookup)["Glúteos"]
                        val weeklyGlutes = glutes?.directSets ?: 0.0
                        val ceiling = VolumeSoftBand.softCeilingFor("Glúteos", realGluteMrv)
                        if (weeklyGlutes > ceiling + 0.001) {
                            failures += "$context -> semana ${index + 1}: glúteos principales=$weeklyGlutes > techo $ceiling"
                        }
                        week.sessions.forEach { session ->
                            val measured = SessionDurationEstimator.estimate(session).totalMinutes
                            if (session.targetDurationMinutes != measured || measured > minutes) {
                                failures += "$context -> '${session.id}' mide $measured min, sella ${session.targetDurationMinutes}"
                            }
                        }
                    }
                }
            }
        }
        println("[NativeProfileMatrix] 20/60 min programs=$programs timeBudgets=$timeBudgets")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue("el barrido debe generar programas (programs=$programs)", programs > 0)
    }

    @Test
    fun weeks_deload_and_rir_follow_section_12_1() {
        val program = requireProgram(
            generate(NativeProfileKind.STRENGTH.entryId, 3, CatalogLevel.BEGINNER, BARBELL, 60),
            "fuerza3 principiante",
        )
        val recipe = requireNotNull(program.sourceRecipe)
        assertEquals(6, recipe.weeks.size)
        recipe.weeks.dropLast(1).forEach { week ->
            assertEquals(0, week.blockIndex)
            assertEquals(BlockGoal.ACCUMULATION, week.blockGoal)
            assertEquals(WeekExecutionKind.TRAINING, week.kind)
            // Principiante: RIR 3 en semanas 1-5 (12.1), SPEED siempre RIR 5.
            week.days.flatMap { it.slots }.forEach { slot ->
                slot.workingSetsForTest().forEach { set ->
                    val expected = if (slot.role == SlotRole.SPEED) 5 else 3
                    assertEquals("RIR ${slot.id} w${week.weekNumber}", expected, set.rir)
                }
            }
        }
        val deload = recipe.weeks.last()
        assertEquals(1, deload.blockIndex)
        assertEquals(BlockGoal.DELOAD, deload.blockGoal)
        assertEquals(WeekExecutionKind.DELOAD, deload.kind)
        assertEquals("Descarga KPKN", deload.blockName)
        // Descarga: ceil(sets/2) minimo 1 por slot ordinario, RIR 4 (12.1).
        recipe.weeks[4].days.forEachIndexed { dayIndex, baseDay ->
            val deloadDay = deload.days[dayIndex]
            assertEquals(baseDay.slots.size, deloadDay.slots.size)
            baseDay.slots.forEachIndexed { slotIndex, baseSlot ->
                val deloadSlot = deloadDay.slots[slotIndex]
                val baseSets = baseSlot.workingSetsForTest().size
                val expectedSets = kotlin.math.ceil(baseSets / 2.0).toInt().coerceAtLeast(1)
                val deloadSets = deloadSlot.workingSetsForTest()
                assertEquals("descarga ${deloadSlot.id}", expectedSets, deloadSets.size)
                val expectedRir = if (deloadSlot.role == SlotRole.SPEED) 5 else 4
                deloadSets.forEach { assertEquals("RIR descarga ${deloadSlot.id}", expectedRir, it.rir) }
            }
        }
        // Intermedio: RIR 3 en semana 1 y RIR 2 en semanas 2-5.
        val inter = requireProgram(
            generate(NativeProfileKind.STRENGTH.entryId, 3, CatalogLevel.INTERMEDIATE, BARBELL, 60),
            "fuerza3 intermedio",
        )
        val interRecipe = requireNotNull(inter.sourceRecipe)
        fun rirs(weekIndex: Int) = interRecipe.weeks[weekIndex].days.flatMap { it.slots }
            .filter { it.role != SlotRole.SPEED }
            .flatMap { it.workingSetsForTest() }
            .mapNotNull { it.rir }
            .distinct()
        assertEquals(listOf(3), rirs(0))
        assertEquals(listOf(2), rirs(1))
        assertEquals(listOf(2), rirs(4))
    }

    @Test
    fun native_block_dispatch_checks_the_six_week_split_without_changing_legacy() {
        val program = requireProgram(
            generate(NativeProfileKind.STRENGTH.entryId, 3, CatalogLevel.BEGINNER, BARBELL, 100),
            "fuerza3 para validar bloques",
        )
        val recipe = requireNotNull(program.sourceRecipe)
        assertTrue(ProgramRecipeValidator.hardFindings(recipe, metadata).none { it.rule == "BLOCK" })

        val brokenNative = recipe.copy(
            weeks = recipe.weeks.map { week ->
                if (week.weekNumber == 6) {
                    week.copy(
                        blockIndex = 0,
                        blockGoal = BlockGoal.ACCUMULATION,
                        kind = WeekExecutionKind.TRAINING,
                    )
                } else {
                    week
                }
            },
        )
        assertTrue(
            "la semana 6 fuera del bloque DELOAD debe bloquear en perfil nativo",
            ProgramRecipeValidator.hardFindings(brokenNative, metadata)
                .any { it.rule == "BLOCK" && it.scope == "w6" },
        )
        val legacyFindings = ProgramRecipeValidator.hardFindings(
            brokenNative.copy(compositionProfile = RecipeCompositionProfile.LEGACY_STANDARD),
            metadata,
        )
        assertFalse(
            "LEGACY_STANDARD conserva sus reglas de bloque originales",
            legacyFindings.any { it.rule == "BLOCK" && it.scope == "w6" && it.message.contains("blockIndex") },
        )
    }

    @Test
    fun lift_slots_follow_the_sbd_vs_empty_rule() {
        val strength = requireProgram(
            generate(NativeProfileKind.STRENGTH.entryId, 4, CatalogLevel.INTERMEDIATE, BARBELL, 60),
            "fuerza4",
        ).sourceRecipe
        assertEquals(
            setOf(
                com.example.kpkn.data.protocols.LiftSlot.SQUAT,
                com.example.kpkn.data.protocols.LiftSlot.BENCH,
                com.example.kpkn.data.protocols.LiftSlot.DEADLIFT,
            ),
            strength!!.liftSlots.keys,
        )
        val powerbuildingBarbell = requireProgram(
            generate(NativeProfileKind.POWERBUILDING.entryId, 4, CatalogLevel.INTERMEDIATE, BARBELL, 60),
            "pb4 barra",
        ).sourceRecipe
        assertEquals(3, powerbuildingBarbell!!.liftSlots.size)
        // Sin barra: mapa vacio y referencias por configuracion (14.3).
        val powerbuildingDumbbell = requireProgram(
            generate(NativeProfileKind.POWERBUILDING.entryId, 3, CatalogLevel.INTERMEDIATE, DUMBBELLS, 60),
            "pb3 mancuernas",
        ).sourceRecipe
        assertTrue("powerbuilding DB no puede prometer SBD", powerbuildingDumbbell!!.liftSlots.isEmpty())
        val muscle = requireProgram(
            generate(NativeProfileKind.MUSCLE.entryId, 3, CatalogLevel.INTERMEDIATE, BODYWEIGHT, 60),
            "musculo3",
        ).sourceRecipe
        assertTrue("Musculo usa identidad por configuracion", muscle!!.liftSlots.isEmpty())
        val athlete = requireProgram(
            generate(NativeProfileKind.COMPLETE_ATHLETE.entryId, 3, CatalogLevel.INTERMEDIATE, BODYWEIGHT, 60),
            "atleta3",
        ).sourceRecipe
        assertTrue("Atleta nunca usa sbdSlots()", athlete!!.liftSlots.isEmpty())
    }

    @Test
    fun discipline_material_gates_explain_what_is_missing() {
        val strengthNoBarbell = generate(NativeProfileKind.STRENGTH.entryId, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 60)
        assertNull(strengthNoBarbell.program)
        assertTrue(
            strengthNoBarbell.report.limitations.joinToString(),
            strengthNoBarbell.report.limitations.any { it.contains("sentadilla, banca y peso muerto con barra") },
        )
        val strengthNoBench = generate(
            NativeProfileKind.STRENGTH.entryId,
            3,
            CatalogLevel.BEGINNER,
            setOf("bodyweight", "barbell", "rack"),
            60,
        )
        assertNull(strengthNoBench.program)
        assertTrue(
            strengthNoBench.report.limitations.joinToString(),
            strengthNoBench.report.limitations.any { it.contains("banco") },
        )
        val powerbuildingBodyweight = generate(
            NativeProfileKind.POWERBUILDING.entryId,
            3,
            CatalogLevel.BEGINNER,
            BODYWEIGHT,
            60,
        )
        assertNull(powerbuildingBodyweight.program)
        val message = powerbuildingBodyweight.report.limitations.joinToString()
        assertTrue(message, message.contains("Musculo") || message.contains("Músculo"))
        assertTrue(message, message.contains("Atleta"))
        // Musculo y Atleta si cubren solo cuerpo (11.1).
        assertNotNull(
            "musculo corporal 3 dias: " + generate(NativeProfileKind.MUSCLE.entryId, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 60).report.limitations,
            generate(NativeProfileKind.MUSCLE.entryId, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 60).program,
        )
        assertNotNull(
            "atleta corporal 3 dias: " + generate(NativeProfileKind.COMPLETE_ATHLETE.entryId, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 60).report.limitations,
            generate(NativeProfileKind.COMPLETE_ATHLETE.entryId, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 60).program,
        )
    }

    @Test
    fun low_bar_support_supplies_beginner_horizontal_pull_for_four_day_muscle() {
        val program = requireProgram(
            generate(
                NativeProfileKind.MUSCLE.entryId,
                days = 4,
                level = CatalogLevel.BEGINNER,
                equipment = SUPPORTED_BODYWEIGHT_PULL,
                minutes = 60,
            ),
            "músculo principiante 4d con barra baja confirmada",
        )
        val recipe = requireNotNull(program.sourceRecipe)

        assertEquals("se materializa el ciclo completo", 6, weeksOf(program).size)
        val upperPullDay = recipe.weeks.first().days.single { it.id == "d3-UB" }
        val supportedRow = upperPullDay.slots.single { it.lift.configurationId == "back_remo_invertido__default" }
        assertEquals(SlotIntent.H, supportedRow.intent)
        assertTrue(
            "la barra baja confirmada resuelve H6 sin eximir la composición",
            ProgramRecipeValidator.hardFindings(recipe, metadata).isEmpty(),
        )

        val withoutSupport = requireProgram(
            generate(
                NativeProfileKind.MUSCLE.entryId,
                days = 4,
                level = CatalogLevel.BEGINNER,
                equipment = BODYWEIGHT,
                minutes = 60,
            ),
            "músculo principiante 4d sin soporte de tirón",
        )
        assertTrue(
            "no debe inferirse una barra baja no declarada",
            sessionsOf(withoutSupport).flatMap { it.allExercises() }
                .none { it.catalogConfigurationId == "back_remo_invertido__default" },
        )
        assertTrue(
            "la limitación de tirón debe permanecer explícita",
            withoutSupport.description.orEmpty().contains("Sin banda o barra de apoyo"),
        )
    }

    @Test
    fun low_bar_row_fills_beginner_five_and_six_day_muscle_pull_days() {
        listOf(5, 6).forEach { days ->
            val program = requireProgram(
                generate(
                    NativeProfileKind.MUSCLE.entryId,
                    days = days,
                    level = CatalogLevel.BEGINNER,
                    equipment = SUPPORTED_BODYWEIGHT_PULL,
                    minutes = 100,
                ),
                "músculo principiante $days días con barra baja confirmada",
            )
            val recipe = requireNotNull(program.sourceRecipe)
            val pullDays = recipe.weeks.first().days.filter { it.id?.endsWith("-PL") == true }

            assertEquals("$days días debe conservar sus días PL", if (days == 5) 1 else 2, pullDays.size)
            pullDays.forEach { day ->
                val row = day.slots.single { it.lift.configurationId == "back_remo_invertido__default" }
                assertEquals("R/V sobre el mismo remo se fusionan hasta 4 series en ${day.id}", 4, row.workingSetsForTest().size)
                assertTrue("mínimo NATIVE_COMPACT en ${day.id}", ProgramRecipeValidator.hardFindings(recipe, metadata)
                    .none { it.rule == "H6" && it.scope.endsWith("/${day.label}") })
            }
        }
    }

    @Test
    fun pullup_bar_only_completes_one_day_athlete_at_both_trained_levels_without_cutting_cardio() {
        listOf(CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED).forEach { level ->
            listOf(10, 30).forEach { cardioMinutes ->
                val result = generate(
                    NativeProfileKind.COMPLETE_ATHLETE.entryId,
                    days = 1,
                    level = level,
                    equipment = setOf("bodyweight", "pull_up_bar"),
                    minutes = 90,
                    cardioPreference = CardioPreference(CardioType.WALK, cardioMinutes),
                )
                val program = requireProgram(result, "E13 Atleta $level 1 día cardio=$cardioMinutes")
                val recipe = requireNotNull(program.sourceRecipe)
                val firstDay = recipe.weeks.first().days.single()
                val vertical = firstDay.slots.single { it.id == "v" }
                assertEquals(SlotIntent.H, vertical.intent)
                assertEquals(SlotRole.T3_ACCESSORY, vertical.role)
                assertEquals(3, vertical.workingSetsForTest().size)
                assertEquals("usa la barra real", com.example.kpkn.data.protocols.CatalogIds.PULLUP,
                    vertical.lift.configurationId)
                assertFalse("no inventa remo", firstDay.slots.any { it.id == "r" })
                assertTrue("potencia sigue presente", firstDay.slots.any { it.intent == SlotIntent.P })
                assertTrue("fuerza relativa sigue presente", firstDay.slots.any { it.intent == SlotIntent.F })
                assertTrue("el suelo H semanal sigue en cuatro series", firstDay.slots
                    .filter { it.intent == SlotIntent.H }.sumOf { it.workingSetsForTest().size } >= 4)
                assertTrue("todos los HARD siguen vigentes", ProgramRecipeValidator.hardFindings(recipe, metadata).isEmpty())
                recipe.weeks.forEach { week ->
                    assertEquals("el cardio elegido se conserva en cada semana", cardioMinutes * 60,
                        week.days.single().cardioBlocks.single().details.effectiveDurationSeconds())
                }
                assertTrue("la persona recibe una explicación del tirón vertical",
                    result.report.planNotes.any { "tirón vertical" in it })
                weeksOf(program).flatMap { it.sessions }.forEach { session ->
                    assertTrue(SessionDurationEstimator.estimate(session).totalMinutes <= 90)
                }
            }
        }
    }

    @Test
    fun low_bar_support_completes_one_day_athlete_hypertrophy_floor() {
        listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED).forEach { level ->
            val program = requireProgram(
                generate(
                    NativeProfileKind.COMPLETE_ATHLETE.entryId,
                    days = 1,
                    level = level,
                    equipment = SUPPORTED_BODYWEIGHT_PULL,
                    minutes = 60,
                ),
                "atleta $level 1d con barra baja confirmada",
            )
            val recipe = requireNotNull(program.sourceRecipe)
            val firstWeek = recipe.weeks.first()
            val hypertrophySets = firstWeek.days.flatMap { it.slots }
                .filter { it.intent == SlotIntent.H }
                .sumOf { it.workingSetsForTest().size }

            assertEquals("se materializan seis semanas para $level", 6, weeksOf(program).size)
            assertTrue("la barra baja real debe producir el remo H", firstWeek.days.flatMap { it.slots }
                .any { it.lift.configurationId == "back_remo_invertido__default" && it.intent == SlotIntent.H })
            assertTrue("H=$hypertrophySets para $level debe respetar el suelo semanal 4", hypertrophySets >= 4)
            assertTrue("W6 no debe perder otros mínimos para $level", ProgramRecipeValidator.hardFindings(recipe, metadata).isEmpty())
        }
    }

    @Test
    fun no_pull_muscle_calendar_has_a_real_glute_mrv_minimum_counterexample() {
        val expectedGluteConfigurations = mapOf(
            "S" to "quads_sentadilla_sin_carga__default",
            "BG" to "glutes_puente_gluteos__bilateral__bodyweight",
            "U" to "reverse_lunge__bodyweight",
        )

        // The literal 3×BL recipe remains a real counterexample: three direct
        // GLUTES H slots per lower day at the beginner floor are 18 > MRV 16.
        // The recipe-specific correction below must not alter those shared
        // dose/MRV constants or the unaffected 1–4 day archetypes.
        (1..4).forEach { days ->
            val originalFrequency = com.example.kpkn.data.protocols.definitions.NativeProfileCalendars
                .muscle(days, pullAvailable = false)
            originalFrequency.filter { it.name == "BL" }.forEach { day ->
                assertEquals(
                    "$days días mantiene intacto BL",
                    listOf(NativeSlotKey.S, NativeSlotKey.BG, NativeSlotKey.U, NativeSlotKey.G),
                    day.slots.map { it.key },
                )
            }
        }
        listOf(5, 6).forEach { days ->
            val calendar = com.example.kpkn.data.protocols.definitions.NativeProfileCalendars.muscle(
                days,
                pullAvailable = false,
            )
            val gluteDays = calendar.filter { day -> day.name == "BL" }
            assertEquals("$days días conserva tres BL por §11.3", 3, gluteDays.size)
            val gluteSlots = gluteDays.flatMap { day ->
                day.slots.filter { slot -> slot.intent == SlotIntent.H && slot.key.name in expectedGluteConfigurations }
            }
            assertEquals("$days días conserva S/U en los tres BL y un puente H", 7, gluteSlots.size)
            assertEquals("$days días sustituye BG:H exactamente en dos BL", 2, gluteDays.count { day ->
                day.slots.any { it.key == NativeSlotKey.B && it.intent == SlotIntent.H } &&
                    day.slots.none { it.key == NativeSlotKey.BG }
            })
            assertEquals("$days días conserva un puente H semanal", 1, gluteDays.count { day ->
                day.slots.any { it.key == NativeSlotKey.BG && it.intent == SlotIntent.H }
            })
            gluteSlots.forEach { slot ->
                val id = expectedGluteConfigurations.getValue(slot.key.name)
                val configuration = catalog.families.flatMap { it.definitions }
                    .flatMap { it.configurations }.single { it.id == id }
                assertTrue("$id debe contar glúteo real", "gluteus_maximus" in configuration.profile.primaryMuscles)
            }
            gluteDays.filter { day -> day.slots.any { it.key == NativeSlotKey.B } }.forEach { day ->
                assertEquals(1, day.slots.count { it.key == NativeSlotKey.B && it.intent == SlotIntent.H })
                val candidateById = catalog.families.flatMap { family -> family.definitions }
                    .flatMap { definition -> definition.configurations }.associateBy { it.id }
                val firstEligibleBodyweightPress = NativeCandidateTable.candidatesFor(NativeSlotKey.B, SlotIntent.H)
                    .first { candidateId ->
                        val candidate = candidateById[candidateId] ?: return@first false
                        candidate.evidence.reviewStatus == CatalogReviewStatusV2.APPROVED &&
                            candidate.profile.equipmentId == "bodyweight" &&
                            "gluteus_maximus" !in candidate.profile.primaryMuscles
                    }
                assertEquals("B:H usa la regresión corporal aprobada", "knee_push_up__default",
                    firstEligibleBodyweightPress)
            }
        }

        val minimumHPerSlot = com.example.kpkn.data.protocols.definitions.NativeDoseTable
            .doseFor(SlotIntent.H, com.example.kpkn.data.protocols.definitions.NativeDoseLevel.BEGINNER, bodyweightRepRange = true)
            .sets
        val literalWeeklyGluteFloor = 3 * expectedGluteConfigurations.size * minimumHPerSlot
        val realGluteMrv = com.example.kpkn.data.programs.VolumeLandmarks.byGroup
            .getValue(com.example.kpkn.data.programs.KpknMuscleGroup.GLUTES).mrv
        assertEquals("H principiante conserva 2 series por slot", 2, minimumHPerSlot)
        assertEquals("tres BL × tres ejercicios glúteo-directos × dos series", 18, literalWeeklyGluteFloor)
        assertEquals("no se eleva el MRV compartido", 16, realGluteMrv)
        assertTrue("el mínimo literal excede MRV sin importar los minutos", literalWeeklyGluteFloor > realGluteMrv)
    }

    /**
     * DEV-r2-01 (docs/WIZARD_PLAN_DEVIATIONS.md), contraejemplo mínimo de Atleta 6 días sin tirón,
     * análogo al de Músculo: el calendario LITERAL de r2 §11.4 (XH con puente H y día 6
     * `[U,H; B,H; puente,H; C,C]`) suma XA 6 + XH 6 + XB 4 + D6 4 = 20 series de glúteo directo a la
     * dosis mínima de principiante, y el fitter solo puede bajar dos Fv (2→1) hasta 18; el MRV real es
     * 16 (HARD, §14.3). El calendario implementado queda exactamente en 16.
     */
    @Test
    fun no_pull_athlete_six_day_calendar_has_a_real_glute_mrv_minimum_counterexample() {
        // Slots que cuentan 1,0 serie de glúteo directa por serie en el catálogo aprobado.
        val gluteConfigurationByKey = mapOf(
            "PS" to NativeCandidateTable.PS_BODYWEIGHT,
            "S" to "quads_sentadilla_sin_carga__default",
            "BG" to NativeCandidateTable.GLUTE_BRIDGE_BODYWEIGHT,
            "U" to "reverse_lunge__bodyweight",
        )
        val configurationsById = catalog.families.flatMap { it.definitions }
            .flatMap { it.configurations }.associateBy { it.id }
        gluteConfigurationByKey.forEach { (key, id) ->
            val configuration = requireNotNull(configurationsById[id]) { "$key: $id no existe en el catálogo" }
            assertTrue("$key/$id debe contar glúteo real", "gluteus_maximus" in configuration.profile.primaryMuscles)
        }
        val kneePushUp = requireNotNull(configurationsById["knee_push_up__default"])
        assertFalse("la flexión de rodillas no suma glúteos", "gluteus_maximus" in kneePushUp.profile.primaryMuscles)

        fun minimumSets(slot: com.example.kpkn.data.protocols.definitions.NativeArchetypeSlot): Int =
            NativeDoseTable.doseFor(slot.intent, NativeDoseLevel.BEGINNER, bodyweightRepRange = true).sets
        fun glutesOf(day: NativeDayArchetype): Int = day.slots
            .filter { it.key.name in gluteConfigurationByKey }
            .sumOf { minimumSets(it) }

        val implemented = NativeProfileCalendars.athlete(6, pullAvailable = false)
        assertEquals("seis arquetipos de día", 6, implemented.size)
        // Literal de r2: XA/XB sin cambios; XH = el de 5 días (con puente H); día 6 con puente H.
        val literalXa = implemented[0]
        val literalXh = NativeProfileCalendars.athlete(5, pullAvailable = false)[3]
        val literalXb = implemented[3]
        val literalD6 = NativeDayArchetype("D6", parseNativeArchetype("U:H,B:H,BG:H,C:C"))
        assertEquals("XH literal de r2 = [S,H; B,H; puente,H; U,H; C,C]",
            listOf(NativeSlotKey.S, NativeSlotKey.B, NativeSlotKey.BG, NativeSlotKey.U, NativeSlotKey.C),
            literalXh.slots.map { it.key })
        val literal = listOf(literalXa, literalXh, literalXb, literalD6)
        val literalPerDay = literal.map { glutesOf(it) }
        assertEquals("XA 6 / XH 6 / XB 4 / D6 4 a la dosis mínima de principiante", listOf(6, 6, 4, 4), literalPerDay)
        val literalWeekly = literalPerDay.sum()

        // Suelo del fitter §12.3: H ya está en 2 y los I/C no tocan glúteos; solo cabe Fv 2→1 y
        // únicamente en «práctica adicional» (la misma clave aparece otro día como F/Fv/H).
        val literalReducibleFv = literal.withIndex().sumOf { (dayIndex, day) ->
            day.slots.count { slot ->
                slot.intent == SlotIntent.FV && slot.key.name in gluteConfigurationByKey &&
                    literal.withIndex().any { (otherIndex, other) ->
                        otherIndex != dayIndex && other.slots.any {
                            it.key == slot.key && it.intent in setOf(SlotIntent.F, SlotIntent.FV, SlotIntent.H)
                        }
                    }
            }
        }
        val literalFitterFloor = literalWeekly - literalReducibleFv
        val realGluteMrv = com.example.kpkn.data.programs.VolumeLandmarks.byGroup
            .getValue(com.example.kpkn.data.programs.KpknMuscleGroup.GLUTES).mrv

        assertEquals("no se eleva el MRV compartido", 16, realGluteMrv)
        assertEquals("tres+tres+dos+dos slots glúteo-directos × dos series", 20, literalWeekly)
        assertEquals("solo dos Fv son reducibles (S de XA y puente de XB)", 2, literalReducibleFv)
        assertEquals("suelo del fitter del calendario literal", 18, literalFitterFloor)
        assertTrue("el mínimo literal ($literalWeekly) excede el MRV sin importar los minutos", literalWeekly > realGluteMrv)
        assertTrue("ni el suelo del fitter ($literalFitterFloor) cabe en el MRV", literalFitterFloor > realGluteMrv)

        // Calendario implementado (DEV-r2-01): XH y D6 cambian puente H por una segunda B:H.
        val implementedPerDay = listOf(implemented[0], implemented[2], implemented[3], implemented[5]).map { glutesOf(it) }
        assertEquals("XA 6 / XH 4 / XB 4 / D6 2", listOf(6, 4, 4, 2), implementedPerDay)
        assertEquals("el calendario implementado queda exactamente en el MRV", realGluteMrv, implementedPerDay.sum())
        listOf(implemented[2], implemented[5]).forEach { day ->
            assertEquals("${day.name} sin puente", 0, day.slots.count { it.key == NativeSlotKey.BG })
            assertEquals("${day.name} con dos B:H fusionables", 2,
                day.slots.count { it.key == NativeSlotKey.B && it.intent == SlotIntent.H })
        }
        assertEquals("el puente se conserva en XA (H) y en XB (Fv)", 2,
            implemented.flatMap { it.slots }.count { it.key == NativeSlotKey.BG })
    }

    @Test
    fun no_pull_muscle_five_and_six_day_recipes_fit_real_glute_mrv_at_every_level() {
        val lookup = catalog.toLegacyConfigurationLookup().values.toList()
        val realGluteMrv = com.example.kpkn.data.programs.VolumeLandmarks.byGroup
            .getValue(com.example.kpkn.data.programs.KpknMuscleGroup.GLUTES).mrv

        listOf(5, 6).forEach { days ->
            listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED).forEach { level ->
                val context = "Músculo E0 $days días/$level"
                val result = generate(
                    NativeProfileKind.MUSCLE.entryId,
                    days = days,
                    level = level,
                    equipment = BODYWEIGHT,
                    minutes = 100,
                )
                val program = requireProgram(result, context)
                val recipe = requireNotNull(program.sourceRecipe)
                val trainingWeek = recipe.weeks.first()
                val lowerDays = trainingWeek.days.filter { day ->
                    day.slots.any { it.lift.configurationId == "quads_sentadilla_sin_carga__default" && it.intent == SlotIntent.H } &&
                        day.slots.any { it.lift.configurationId == "reverse_lunge__bodyweight" && it.intent == SlotIntent.H }
                }

                assertEquals("$context conserva seis semanas y $days sesiones", 6, weeksOf(program).size)
                assertEquals("$context conserva tres exposiciones inferiores", 3, lowerDays.size)
                assertTrue("$context mantiene ambas exposiciones S/U en cada BL", lowerDays.all { day ->
                    day.slots.any { it.lift.configurationId == "quads_sentadilla_sin_carga__default" } &&
                        day.slots.any { it.lift.configurationId == "reverse_lunge__bodyweight" }
                })
                assertEquals("$context tiene dos B:H no-glúteo", 2, lowerDays.count { day ->
                    day.slots.any { it.lift.configurationId == "knee_push_up__default" && it.intent == SlotIntent.H } &&
                        day.slots.none { it.lift.configurationId == "glutes_puente_gluteos__bilateral__bodyweight" }
                })
                assertEquals("$context conserva un puente H semanal", 1, lowerDays.count { day ->
                    day.slots.any { it.lift.configurationId == "glutes_puente_gluteos__bilateral__bodyweight" && it.intent == SlotIntent.H }
                })
                assertTrue("$context conserva el aviso de tirón limitado", program.description.orEmpty()
                    .contains("Sin banda o barra de apoyo"))
                assertTrue("$context declara la sustitución por el límite de glúteo", program.description.orEmpty()
                    .contains(GLUTE_BRIDGE_ONCE_NOTE))
                assertTrue("$context valida H6 y el resto de composición", ProgramRecipeValidator
                    .hardFindings(recipe, metadata).isEmpty())

                val resolvedConfigurations = catalog.families.flatMap { it.definitions }
                    .flatMap { it.configurations }.associateBy { it.id }
                recipe.weeks.first().days.flatMap { it.slots }.forEach { slot ->
                    val configuration = requireNotNull(resolvedConfigurations[slot.lift.configurationId])
                    assertEquals("$context ${slot.lift.configurationId} está aprobado", CatalogReviewStatusV2.APPROVED,
                        configuration.evidence.reviewStatus)
                    assertEquals("$context ${slot.lift.configurationId} solo requiere cuerpo", "bodyweight",
                        configuration.profile.equipmentId)
                }
                trainingWeek.days.flatMap { it.slots }.filter { it.intent == SlotIntent.H }.forEach { slot ->
                    assertTrue("$context ${slot.id} conserva el suelo H=2", slot.workingSetsForTest().size >= 2)
                }

                weeksOf(program).forEachIndexed { weekIndex, week ->
                    assertEquals("$context w${weekIndex + 1} conserva $days sesiones", days, week.sessions.size)
                    val glutes = VolumeCalculator.calculateRoleSeparatedMuscleVolume(week.sessions, lookup)["Glúteos"]
                    val weeklyGlutes = glutes?.directSets ?: 0.0
                    val ceiling = VolumeSoftBand.softCeilingFor("Glúteos", realGluteMrv)
                    assertTrue("$context w${weekIndex + 1}: glúteos principales=$weeklyGlutes > techo $ceiling",
                        weeklyGlutes <= ceiling + 0.001)
                }
            }
        }
    }

    @Test
    fun section_17_2_muscle_e0_six_day_30_remains_a_required_positive() {
        val result = generate(
            NativeProfileKind.MUSCLE.entryId,
            days = 6,
            level = CatalogLevel.BEGINNER,
            equipment = BODYWEIGHT,
            minutes = 30,
        )

        assertNotNull(
            "§17.2 exige seis semanas reales para Músculo E0 6d/30; " +
                "no debe aceptarse COMPOSITION como negativo: ${result.report.limitations}",
            result.program,
        )
        assertEquals(6, weeksOf(requireNotNull(result.program)).size)
    }

    @Test
    fun section_17_2_athlete_e0_six_day_60_is_a_materialized_positive_at_every_level() {
        val lookup = catalog.toLegacyConfigurationLookup().values.toList()

        listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED).forEach { level ->
            val context = "Atleta completo E0 6 días/60/$level"
            val result = generate(
                NativeProfileKind.COMPLETE_ATHLETE.entryId,
                days = 6,
                level = level,
                equipment = BODYWEIGHT,
                minutes = 60,
                cardioPreference = CardioPreference(CardioType.WALK, minutes = 10),
            )
            val program = requireProgram(result, context)
            val recipe = requireNotNull(program.sourceRecipe)
            val weeks = weeksOf(program)
            val realGluteMrv = result.report.muscles.single { it.muscle == "Glúteos" }.mrv

            assertEquals("$context completa seis semanas", 6, weeks.size)
            assertEquals("$context no promete SBD", emptyMap<com.example.kpkn.data.protocols.LiftSlot, String>(), recipe.liftSlots)
            assertTrue("$context no inventa slots de tirón", recipe.weeks.all { week ->
                week.days.flatMap { it.slots }.none { slot ->
                    slot.id == "r" || slot.id == "v" || slot.lift.liftSlot != null
                }
            })
            assertTrue("$context informa el cambio de puente", program.description.orEmpty()
                .contains(ATHLETE_SIX_DAY_BRIDGE_NOTE))
            assertTrue("$context conserva H6/composición", ProgramRecipeValidator
                .hardFindings(recipe, metadata).isEmpty())
            val resolvedConfigurations = catalog.families.flatMap { it.definitions }
                .flatMap { it.configurations }.associateBy { it.id }

            recipe.weeks.forEachIndexed { weekIndex, weekRecipe ->
                val weekContext = "$context semana ${weekIndex + 1}"
                val materializedWeek = weeks[weekIndex]
                assertEquals("$weekContext conserva seis sesiones", 6, weekRecipe.days.size)
                assertEquals("$weekContext materializa seis sesiones", 6, materializedWeek.sessions.size)
                assertEquals("$weekContext conserva dos exposiciones P", 2,
                    weekRecipe.days.count { day -> day.slots.any { it.intent == SlotIntent.P } })
                assertEquals("$weekContext conserva dos bloques cardio", 2,
                    weekRecipe.days.sumOf { day -> day.cardioBlocks.size })
                assertTrue("$weekContext cambia BG:H por B:H solo en XH/D6", listOf("d3-XH", "d6-D6b").all { dayId ->
                    val day = weekRecipe.days.single { it.id == dayId }
                    day.slots.any { it.intent == SlotIntent.H && it.lift.configurationId == "knee_push_up__default" } &&
                        day.slots.none { it.lift.configurationId == "glutes_puente_gluteos__bilateral__bodyweight" }
                })
                listOf("d3-XH", "d6-D6b").forEach { dayId ->
                    val mergedPress = weekRecipe.days.single { it.id == dayId }.slots
                        .single { it.intent == SlotIntent.H && it.lift.configurationId == "knee_push_up__default" }
                    assertTrue("$weekContext $dayId fusiona H/H hasta el tope de cuatro",
                        mergedPress.workingSetsForTest().size <= 4)
                    if (level == CatalogLevel.BEGINNER && weekIndex < 5) {
                        assertEquals("$weekContext $dayId conserva la dosis H/H fusionada", 4,
                            mergedPress.workingSetsForTest().size)
                    }
                }
                weekRecipe.days.flatMap { it.slots }.forEach { slot ->
                    val configuration = requireNotNull(resolvedConfigurations[slot.lift.configurationId])
                    assertEquals("$weekContext ${slot.lift.configurationId} está aprobado",
                        CatalogReviewStatusV2.APPROVED, configuration.evidence.reviewStatus)
                    assertEquals("$weekContext ${slot.lift.configurationId} solo requiere cuerpo",
                        "bodyweight", configuration.profile.equipmentId)
                }

                val speedSlots = weekRecipe.days.flatMap { it.slots }.filter { it.intent == SlotIntent.P }
                speedSlots.forEach { slot ->
                    assertEquals("$weekContext P role", SlotRole.SPEED, slot.role)
                    assertEquals("$weekContext P technique", TechniqueModifier.SPEED, slot.technique)
                    assertEquals("$weekContext P rest", 90, slot.restSeconds)
                    assertTrue("$weekContext P conserva 3 reps/RIR5", slot.workingSetsForTest().all {
                        it.reps == 3 && it.rir == 5
                    })
                }

                val materializedExercises = materializedWeek.sessions.flatMap { it.allExercises() }
                val materializedSpeed = materializedExercises.filter { it.slotRole == SlotRole.SPEED }
                assertEquals("$weekContext materializa ambos slots P", 2, materializedSpeed.size)
                materializedSpeed.forEach { exercise ->
                    assertEquals("${exercise.id} SPEED technique", TechniqueModifier.SPEED, exercise.techniqueModifier)
                    assertTrue("${exercise.id} SPEED usa 3 reps/RIR5", exercise.sets.all {
                        it.targetReps == 3 && it.targetRIR == 5
                    })
                }
                val cardioExercises = materializedExercises.filter { it.cardioDetails != null }
                assertEquals("$weekContext materializa ambos bloques cardio", 2, cardioExercises.size)
                cardioExercises.forEach { exercise ->
                    assertEquals("${exercise.id} conserva cardio elegido de 10 min", 10 * 60,
                        requireNotNull(exercise.cardioDetails).effectiveDurationSeconds())
                }

                val gluteVolume = VolumeCalculator.calculateRoleSeparatedMuscleVolume(
                    materializedWeek.sessions,
                    lookup,
                )["Glúteos"]
                val weeklyGlutes = gluteVolume?.directSets ?: 0.0
                val ceiling = VolumeSoftBand.softCeilingFor("Glúteos", realGluteMrv)
                assertTrue("$weekContext glúteos principales=$weeklyGlutes > techo $ceiling",
                    weeklyGlutes <= ceiling + 0.001)
                assertTrue("$weekContext todas las sesiones caben en 60 min", materializedWeek.sessions.all {
                    (it.targetDurationMinutes ?: Int.MAX_VALUE) <= 60
                })

                val expectedOrdinaryRirs = weekRecipe.days.flatMap { it.slots }
                    .filter { it.intent != SlotIntent.P }
                    .flatMap { it.workingSetsForTest() }
                    .mapNotNull { it.rir }
                    .toSet()
                val materializedOrdinaryRirs = materializedExercises
                    .filter { it.slotRole != null && it.slotRole != SlotRole.SPEED && it.cardioDetails == null }
                    .flatMap { it.sets }
                    .mapNotNull { it.targetRIR }
                    .toSet()
                assertEquals("$weekContext conserva el RIR de la receta", expectedOrdinaryRirs,
                    materializedOrdinaryRirs)
            }
        }
    }

    @Test
    fun athlete_has_real_speed_and_cardio_components() {
        val program = requireProgram(
            generate(NativeProfileKind.COMPLETE_ATHLETE.entryId, 3, CatalogLevel.INTERMEDIATE, BODYWEIGHT, 60),
            "atleta3",
        )
        val recipe = requireNotNull(program.sourceRecipe)
        // SPEED real (11.5): rol, tecnica, 3 reps, 90 s, RIR 5, prioridad.
        val speedSlots = recipe.weeks.first().days.flatMap { it.slots }.filter { it.role == SlotRole.SPEED }
        assertTrue("sin slots SPEED", speedSlots.isNotEmpty())
        speedSlots.forEach { slot ->
            assertEquals(TechniqueModifier.SPEED, slot.technique)
            assertEquals(SlotIntent.P, slot.intent)
            assertEquals(90, slot.restSeconds)
            assertEquals(SlotPriority.SPEED, slot.priority)
            slot.workingSetsForTest().forEach { set ->
                assertEquals(3, set.reps)
                assertEquals(5, set.rir)
            }
        }
        // Cardio real (nunca solo texto): bloques >=10 min materializados.
        val cardioParts = sessionsOf(program).flatMap { it.parts }.filter { it.isCardioGroup }
        assertTrue("sin partes de cardio", cardioParts.isNotEmpty())
        cardioParts.forEach { part ->
            val details = part.exercises.mapNotNull { it.cardioDetails }
            assertEquals(part.id, part.exercises.size, details.size)
            details.forEach { assertTrue("cardio < 10 min", it.effectiveDurationSeconds() >= 600) }
        }
        // Dia dedicado: cardio primero y sin slots de resistencia (11.4/14.3).
        val dedicatedSession = sessionsOf(program).firstOrNull { session ->
            session.parts.firstOrNull()?.isCardioGroup == true &&
                session.allExercises().any { it.cardioDetails != null } &&
                session.allExercises().any { it.cardioDetails == null }
        }
        assertNotNull("día dedicado: cardio primero y accesorios después", dedicatedSession)
        assertTrue(requireNotNull(dedicatedSession).cardioFirst)
        val dedicatedRecipeDay = recipe.weeks.first().days.first { it.sessionKind == RecipeSessionKind.CARDIO_ACCESSORY }
        assertTrue(dedicatedRecipeDay.slots.isNotEmpty())
        val kinds = recipe.weeks.first().days.map { it.sessionKind }.toSet()
        assertTrue("kinds=$kinds", RecipeSessionKind.STRENGTH in kinds && RecipeSessionKind.CARDIO_ACCESSORY in kinds)
    }

    /**
     * Cambió de sentido (Entreno v2): el día de Atleta corporal de 1 día mide ahora 39 min con 10 de cardio (antes 41):
     * las dos aproximaciones técnicas por patrón (2 × 90 s) se sustituyeron por la regla nueva, que en un día de peso
     * corporal fácil solo pide la movilidad breve del primer ejercicio (un movimiento de 40 s). Con el cardio por
     * defecto de 15 min a partir de 45 el día mide 44 y ya cabe: el límite 44→45 sigue sumando exactamente 5 min de
     * cardio, pero deja de verse como un rechazo (antes 46 > 45).
     */
    @Test
    fun cardio_defaults_use_session_time_boundaries_44_and_45() {
        val result44 = generate(
            NativeProfileKind.COMPLETE_ATHLETE.entryId,
            1,
            CatalogLevel.BEGINNER,
            BODYWEIGHT,
            44,
            programId = "np-athlete-boundary-44",
        )
        val program44 = requireProgram(result44, "atleta a 44 min")
        val actual44 = requireNotNull(result44.report.maxSessionMinutes)
        assertEquals("el día cabe en SESSION_TIME=44", 39, actual44)
        val cardio44 = requireNotNull(program44.sourceRecipe).weeks.first().days
            .flatMap { it.cardioBlocks }
            .single()
        assertEquals("44 min selecciona el default de cardio de 10 min", 10 * 60, cardio44.details.effectiveDurationSeconds())

        val result45 = generate(
            NativeProfileKind.COMPLETE_ATHLETE.entryId,
            1,
            CatalogLevel.BEGINNER,
            BODYWEIGHT,
            45,
            programId = "np-athlete-boundary-45",
        )
        val program45 = requireProgram(result45, "atleta a 45 min")
        val actual45 = requireNotNull(result45.report.maxSessionMinutes)
        assertTrue("SESSION_TIME=45 cabe con el cardio de 15 min: $actual45", actual45 <= 45)
        val cardio45 = requireNotNull(program45.sourceRecipe).weeks.first().days
            .flatMap { it.cardioBlocks }
            .single()
        assertEquals("45 min selecciona el default de cardio de 15 min", 15 * 60, cardio45.details.effectiveDurationSeconds())
        // El mismo día solo cambia el default de cardio: 44→10 min, 45→15.
        assertEquals("el límite 44→45 añade exactamente 5 min de cardio", 5, actual45 - actual44)
    }

    /**
     * §11.4 («Elegir minutos explícitos reemplaza el default por bloque y se valida completo; no se
     * recorta silenciosamente»), §12.4 («conservar esos minutos, sin reducirlos») y §12.3 (el
     * cardio no es una palanca del fitter): los minutos de cardio los fijan el default por
     * SESSION_TIME o la elección del usuario, el estimador cuenta el bloque entero y, si el día
     * no cabe, el resultado es TIME_BUDGET con el mínimo real, no una sesión con el cardio acortado
     * (antes el «paso 4b» llegaba a dejar bloques de 9 min). Día base: Atleta corporal 1 día,
     * principiante = 39 min con 10 min de cardio (41 antes de Entreno v2: la aproximación técnica por patrón se
     * sustituyó por la movilidad breve del primer ejercicio); cada escalón ofertado añade 5/10/20 min.
     */
    @Test
    fun athlete_cardio_minutes_are_validated_whole_and_never_trimmed_to_fit() {
        fun attempt(budget: Int, cardioMinutes: Int) = generate(
            NativeProfileKind.COMPLETE_ATHLETE.entryId,
            1,
            CatalogLevel.BEGINNER,
            BODYWEIGHT,
            budget,
            cardioPreference = CardioPreference(CardioType.WALK, cardioMinutes),
        )
        listOf(10 to 39, 15 to 44, 20 to 49, 30 to 59).forEach { (cardioMinutes, floor) ->
            val exact = attempt(floor, cardioMinutes)
            val program = requireProgram(exact, "cardio de $cardioMinutes min en su mínimo de $floor min")
            assertEquals("el día mide su mínimo con cardio de $cardioMinutes", floor, exact.report.maxSessionMinutes)
            val block = requireNotNull(program.sourceRecipe).weeks.first().days.flatMap { it.cardioBlocks }.single()
            assertEquals("el cardio conserva sus minutos", cardioMinutes * 60, block.details.effectiveDurationSeconds())
            val below = attempt(floor - 1, cardioMinutes)
            assertNull("un minuto menos no se compensa acortando el cardio de $cardioMinutes min", below.program)
            assertEquals("TIME_BUDGET", below.report.reasonCode)
            assertEquals("mínimo real con cardio de $cardioMinutes", floor, below.report.maxSessionMinutes)
        }
    }

    /**
     * Músculo corporal de 3 días alterna FA/FB/FA ↔ FB/FA/FB, así que una misma posición de día
     * la ocupan dos planes de día y W5 exige los mismos accesorios en todas las semanas del bloque.
     * El fitter debe retirar I y C «de la tabla» (en las dos apariciones de BFA y en BFB) y llegar al
     * mismo piso que el día único. Antes quedaba atascado en 25 min (BFB sin ajustar) y sus 21–24 min
     * "no cabían" aunque la receta mínima sí.
     *
     * Cambió de sentido (Entreno v2): el piso era 21 min (3 H + 2 aproximaciones técnicas = 1260 s) y a 20 min
     * había rechazo. Ahora el primer ejercicio de peso corporal fácil solo lleva una movilidad breve (40 s):
     * 3 H + 40 s = 1120 s = 19 min, así que el presupuesto mínimo admitido (20 min) ya cabe y el mínimo informado
     * es 19, por debajo de lo que se puede elegir: el rechazo de tiempo deja de existir para este plan.
     */
    @Test
    fun muscle_bodyweight_three_day_alternation_reports_and_reaches_its_real_floor() {
        val atFloor = generate(NativeProfileKind.MUSCLE.entryId, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 20)
        assertEquals("el mínimo informado es el real, no el de un fitter atascado", 19, atFloor.report.maxSessionMinutes)
        val program = requireProgram(atFloor, "musculo corporal 3 días en su mínimo")
        val recipe = requireNotNull(program.sourceRecipe)
        assertTrue(
            "W5 y el resto de reglas duras se cumplen con los accesorios retirados",
            ProgramRecipeValidator.hardFindings(recipe, metadata).isEmpty(),
        )
        sessionsOf(program).forEach { session ->
            val measured = SessionDurationEstimator.estimate(session).totalMinutes
            assertTrue("${session.id} mide $measured min con 20 de presupuesto", measured <= 20)
            assertEquals("${session.id} sella la medida real", measured, session.targetDurationMinutes)
        }
        val weeks = recipe.weeks
        assertEquals(listOf("d1-BFA", "d2-BFB", "d3-BFA"), weeks[0].days.map { it.id })
        assertEquals(listOf("d1-BFB", "d2-BFA", "d3-BFB"), weeks[1].days.map { it.id })
        weeks.take(5).forEach { week ->
            week.days.forEach { day ->
                assertEquals("${day.id}: solo quedan los tres H", 3, day.slots.size)
                assertTrue("${day.id}: sin accesorios I/C", day.slots.all { it.intent == SlotIntent.H })
            }
        }
        assertEquals(
            "las dos apariciones de BFA pierden los mismos accesorios",
            weeks[0].days[0].slots.map { it.lift.configurationId },
            weeks[0].days[2].slots.map { it.lift.configurationId },
        )
    }

    /**
     * Paquete A · C2 (DEC-w2-03): `requiredMinutes` es el mínimo EXACTO del generador, no lo que midió el mejor esfuerzo
     * del fitter. El oráculo es independiente del sondeo: para cada fila se barren con el generador público TODOS los
     * presupuestos entre el elegido y el informado, y el informado debe ser el primero con programa (viable con él y
     * TIME_BUDGET en cada minuto anterior). Las filas de Atleta con 10 min de cardio y 20 de presupuesto son las que
     * antes informaban de más (esfuerzo de 31 min y programa ya con 30). La fila de Atleta sin minutos de cardio
     * explícitos tiene una viabilidad que NO crece con el presupuesto (el cardio por defecto sube de 10 a 15 min en
     * 45), así que el generador la barre minuto a minuto en lugar de bisecar.
     */
    @Test
    fun time_budget_reports_the_exact_first_viable_minute() {
        val generator = personalizer()
        class Row(
            val label: String,
            val entryId: String,
            val days: Int,
            val level: CatalogLevel,
            val equipment: Set<String>,
            val budget: Int,
            val cardio: CardioPreference?,
        )
        fun walk(minutes: Int) = CardioPreference(CardioType.WALK, minutes)
        val athlete = NativeProfileKind.COMPLETE_ATHLETE.entryId
        val muscle = NativeProfileKind.MUSCLE.entryId
        val rows = listOf(
            Row("Atleta 3d principiante, cardio 10, 20 min", athlete, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 20, walk(10)),
            Row("Atleta 4d principiante, cardio 10, 20 min", athlete, 4, CatalogLevel.BEGINNER, BODYWEIGHT, 20, walk(10)),
            Row("Atleta 5d principiante, cardio 10, 30 min", athlete, 5, CatalogLevel.BEGINNER, BODYWEIGHT, 30, walk(10)),
            Row("Atleta 1d principiante, cardio 20, 45 min", athlete, 1, CatalogLevel.BEGINNER, BODYWEIGHT, 45, walk(20)),
            Row("Atleta 2d intermedio con barra, cardio 15, 40 min", athlete, 2, CatalogLevel.INTERMEDIATE, BARBELL, 40, walk(15)),
            Row("Atleta 1d principiante, cardio por defecto, 30 min", athlete, 1, CatalogLevel.BEGINNER, BODYWEIGHT, 30, null),
            Row("Músculo 3d principiante corporal, 20 min", muscle, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 20, null),
            Row("Músculo 6d intermedio con mancuernas, 30 min", muscle, 6, CatalogLevel.INTERMEDIATE, DUMBBELLS, 30, null),
            Row("Fuerza 1d principiante con barra, 20 min", NativeProfileKind.STRENGTH.entryId, 1, CatalogLevel.BEGINNER, BARBELL, 20, null),
            Row(
                "Fuerza y músculo 3d intermedio con mancuernas, 20 min",
                NativeProfileKind.POWERBUILDING.entryId,
                3,
                CatalogLevel.INTERMEDIATE,
                DUMBBELLS,
                20,
                null,
            ),
            // Entreno v2: con la aproximación nueva el peso corporal y las máquinas bajan su piso (varias filas de
            // arriba ya caben a 20 min y salen del oráculo); lo que sube es la barra: la movilidad y la rampa del
            // primer ejercicio pesado. Estas filas mantienen el barrido con rechazos de tiempo reales.
            Row("Fuerza 3d intermedio con barra, 20 min", NativeProfileKind.STRENGTH.entryId, 3, CatalogLevel.INTERMEDIATE, BARBELL, 20, null),
            Row("Fuerza 4d intermedio con barra, 20 min", NativeProfileKind.STRENGTH.entryId, 4, CatalogLevel.INTERMEDIATE, BARBELL, 20, null),
            Row("Fuerza y músculo 3d intermedio con barra, 20 min", NativeProfileKind.POWERBUILDING.entryId, 3, CatalogLevel.INTERMEDIATE, BARBELL, 20, null),
        )
        fun attempt(row: Row, minutes: Int) = generator.personalize(
            "exact-${row.entryId.removePrefix("native:")}-${row.days}-$minutes",
            PersonalizerInput(
                catalogEntryId = row.entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = row.days,
                equipment = row.equipment,
                level = row.level,
                availableMinutes = minutes,
                cardio = row.cardio,
            ),
        )

        var checked = 0
        val failures = mutableListOf<String>()
        rows.forEach { row ->
            val rejected = attempt(row, row.budget)
            // Una fila que cabe en su presupuesto no es un rechazo de tiempo: no entra en el oráculo.
            if (rejected.program != null) return@forEach
            val required = rejected.report.maxSessionMinutes
            if (rejected.report.reasonCode != "TIME_BUDGET" || required == null || required <= row.budget || required > 100) {
                failures += "${row.label}: rechazo ${rejected.report.reasonCode} con mínimo $required (presupuesto ${row.budget})"
                return@forEach
            }
            checked++
            val firstLine = rejected.report.limitations.first().lineSequence().first()
            val expectedLine = "Con las series mínimas este plan necesita $required min por sesión y elegiste ${row.budget}."
            if (firstLine != expectedLine) failures += "${row.label}: primera línea «$firstLine» en vez de «$expectedLine»"
            if (attempt(row, required).program == null) failures += "${row.label}: con $required min no hay programa"
            (row.budget + 1 until required).forEach { minutes ->
                val below = attempt(row, minutes)
                if (below.program != null) {
                    failures += "${row.label}: con $minutes min ya hay programa y se informaron $required"
                } else if (below.report.reasonCode != "TIME_BUDGET") {
                    failures += "${row.label}: con $minutes min el rechazo es ${below.report.reasonCode}, no TIME_BUDGET"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue("al menos 8 de las 13 filas son rechazos de tiempo (fueron $checked)", checked >= 8)
    }

    @Test
    fun athlete_explicit_cardio_at_session_limit_returns_precise_time_budget() {
        val result = generate(
            NativeProfileKind.COMPLETE_ATHLETE.entryId,
            1,
            CatalogLevel.BEGINNER,
            BODYWEIGHT,
            30,
            cardioPreference = CardioPreference(CardioType.WALK, 30),
        )

        assertNull("no se debe publicar una sesión parcial", result.program)
        assertEquals("TIME_BUDGET", result.report.reasonCode)
        val required = requireNotNull(result.report.maxSessionMinutes)
        assertTrue("el mínimo real ($required) debe superar 30 min", required > 30)
    }

    @Test
    fun fitter_returns_time_budget_with_required_minutes() {
        val result = generate(NativeProfileKind.STRENGTH.entryId, 1, CatalogLevel.BEGINNER, BARBELL, 20)
        assertNull("a 20 min la dosis minima de Fuerza no cabe", result.program)
        assertEquals("TIME_BUDGET", result.report.reasonCode)
        val required = result.report.maxSessionMinutes
        assertNotNull("debe calcular los minutos minimos", required)
        assertTrue("requerido=$required debe superar 20", required!! > 20)
        val message = result.report.limitations.joinToString()
        assertTrue(message, message.contains("min"))
        assertFalse("no es un fallo interno", result.report.limitations.any { it.contains("COMPOSITION") })
    }

    @Test
    fun strength_does_not_treat_generic_gym_as_confirmed_sbd_equipment() {
        val result = generate(
            NativeProfileKind.STRENGTH.entryId,
            3,
            CatalogLevel.BEGINNER,
            setOf("general_gym"),
            100,
        )
        assertNull(result.program)
        assertEquals("APPARATUS_ABSENT", result.report.reasonCode)
        assertTrue(
            result.report.limitations.joinToString(),
            result.report.limitations.any { it.contains("barra") && it.contains("rack") && it.contains("banco") },
        )
    }

    @Test
    fun fitter_keeps_daily_minimums_and_the_two_exercise_fuerza_rule() {
        // Principiante 5 dias: los dias de dos ejercicios conservan su I/C con
        // 2 series (11.3) y >=4 series efectivas (14.3).
        val beginner = requireProgram(
            generate(NativeProfileKind.STRENGTH.entryId, 5, CatalogLevel.BEGINNER, BARBELL, 60),
            "fuerza5 principiante",
        )
        val recipe = requireNotNull(beginner.sourceRecipe)
        val twoExerciseDay = recipe.weeks.first().days.first { it.id?.contains("f5c") == true }
        assertEquals(2, twoExerciseDay.slots.size)
        val coreSlot = twoExerciseDay.slots.first { it.intent == SlotIntent.C }
        assertEquals("el I/C de un dia de 2 ejercicios mantiene 2 series", 2, coreSlot.workingSetsForTest().size)
        recipe.weeks.first().days.forEach { day ->
            val ordinary = day.slots.filter { it.role != SlotRole.SPEED }
            val sets = ordinary.sumOf { it.workingSetsForTest().size }
            val configurations = ordinary.map { it.lift.configurationId }.distinct()
            assertTrue("${day.id} con ${configurations.size} configuraciones", configurations.size >= 2)
            assertTrue("${day.id} con $sets series", sets >= 4)
        }
        // Musculo corporal con presupuesto ajustado: el fitter reduce sin bajar
        // de los minimos declarados y registra cada ajuste.
        val fitted = requireProgram(
            generate(NativeProfileKind.MUSCLE.entryId, 1, CatalogLevel.INTERMEDIATE, BODYWEIGHT, 30),
            "musculo1 a 30 min",
        )
        assertTrue(
            "el fitter debe registrar sus ajustes: ${fitted.description}",
            fitted.description.orEmpty().contains("12.3"),
        )
        val fittedRecipe = requireNotNull(fitted.sourceRecipe)
        fittedRecipe.weeks.first().days.forEach { day ->
            val ordinary = day.slots.filter { it.role != SlotRole.SPEED }
            assertTrue(ordinary.map { it.lift.configurationId }.distinct().size >= 2)
            assertTrue(ordinary.sumOf { it.workingSetsForTest().size } >= 4)
        }
        val duration = sessionsOf(fitted).maxOf { it.targetDurationMinutes ?: 0 }
        assertTrue("duracion=$duration debe caber en 30", duration <= 30)
    }

    @Test
    fun fitter_can_reduce_fv_on_a_practice_day_with_an_unrelated_f_main() {
        // Cambió de sentido (Entreno v2): la aproximación obligatoria (movilidad + rampa del primer ejercicio pesado)
        // sube el piso de Fuerza 3 días intermedio con barra de ≤ 20 a 24 min, así que el presupuesto ajustado en el que
        // el fitter recorta Fv 2→1 es ahora el de 24 min (antes 20).
        val program = requireProgram(
            generate(NativeProfileKind.STRENGTH.entryId, 3, CatalogLevel.INTERMEDIATE, BARBELL, 24),
            "fuerza3 intermedio a 24 min",
        )
        val recipe = requireNotNull(program.sourceRecipe)
        val week = recipe.weeks.first()
        val reducedPracticeSlots = week.days.flatMap { it.slots }
            .filter { it.intent == SlotIntent.FV && it.workingSetsForTest().size == 1 }

        assertTrue("el fitter debe usar Fv 2→1 cuando solo queda una exposición adicional ajustable", reducedPracticeSlots.isNotEmpty())
        reducedPracticeSlots.forEach { practice ->
            val practiceDayIndex = week.days.indexOfFirst { day -> day.slots.any { it === practice } }
            assertTrue(
                "${practice.id} conserva otra exposición en un día distinto",
                week.days.withIndex().any { (index, day) ->
                    index != practiceDayIndex &&
                        day.slots.any { it.id == practice.id && it.intent in setOf(SlotIntent.F, SlotIntent.FV, SlotIntent.H) }
                },
            )
        }
        assertTrue(
            "la exposición secundaria de banca debe seguir presente",
            week.days.count { day -> day.slots.any { it.id == "b" && it.workingSetsForTest().isNotEmpty() } } >= 2,
        )
        assertTrue("todas las sesiones ajustadas caben en 24 min", sessionsOf(program).all { (it.targetDurationMinutes ?: 0) <= 24 })

        val isolated = reducedPracticeSlots.first()
        val invalidOneSetPractice = recipe.copy(
            weeks = recipe.weeks.map { candidateWeek ->
                if (candidateWeek.weekNumber != week.weekNumber) candidateWeek else {
                    candidateWeek.copy(
                        days = candidateWeek.days.map { day ->
                            day.copy(slots = day.slots.map { slot ->
                                if (slot === isolated) slot.copy(id = "isolated-fv") else slot
                            })
                        },
                    )
                }
            },
        )
        assertTrue(
            "la política debe rechazar Fv=1 sin otra exposición",
            ProgramRecipeValidator.hardFindings(invalidOneSetPractice, metadata)
                .any { it.rule == "W6" && it.message.contains("sin otra exposición semanal") },
        )
    }

    @Test
    fun fitter_reduces_weekly_mrv_on_a_day_that_already_fits_time() {
        val result = generate(
            NativeProfileKind.STRENGTH.entryId,
            6,
            CatalogLevel.INTERMEDIATE,
            BARBELL,
            20,
            volumeRecommendations = listOf(
                VolumeRecommendation(
                    muscleGroup = "Pectorales",
                    minEffectiveVolume = 2,
                    maxAdaptiveVolume = 3,
                    maxRecoverableVolume = 4,
                ),
            ),
        )
        val program = requireProgram(result, "fuerza6 con MRV de pectorales=4")
        val pectorals = result.report.muscles.single { it.muscle == "Pectorales" }
        assertTrue(
            "series principales ${pectorals.directSets} exceden MRV personal ${pectorals.mrv}",
            pectorals.directSets <= pectorals.mrv + 0.001,
        )
        val benchMain = requireNotNull(program.sourceRecipe).weeks.first().days.flatMap { it.slots }
            .single { it.lift.liftSlot == com.example.kpkn.data.protocols.LiftSlot.BENCH && it.intent == SlotIntent.F }
        assertEquals("la F de banca se reduce en su día que cabe en tiempo", 2, benchMain.workingSetsForTest().size)
    }

    @Test
    fun native_fitter_persists_the_shared_full_session_estimate() {
        val program = requireProgram(
            generate(
                NativeProfileKind.COMPLETE_ATHLETE.entryId,
                days = 2,
                level = CatalogLevel.INTERMEDIATE,
                equipment = BARBELL,
                minutes = 60,
                cardioPreference = CardioPreference(CardioType.WALK, minutes = 10),
            ),
            "atleta2 para comprobar duración compartida",
        )

        sessionsOf(program).forEach { session ->
            val expected = SessionDurationEstimator.estimate(session).totalMinutes
            assertEquals("duración de ${session.id}", expected, session.targetDurationMinutes)
        }
    }

    @Test
    fun muscle_three_day_calendar_alternates_in_even_weeks() {
        val program = requireProgram(
            generate(NativeProfileKind.MUSCLE.entryId, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 100),
            "musculo corporal 3 días",
        )
        val weeks = requireNotNull(program.sourceRecipe).weeks
        assertEquals(listOf("d1-BFA", "d2-BFB", "d3-BFA"), weeks[0].days.map { it.id })
        assertEquals(listOf("d1-BFB", "d2-BFA", "d3-BFB"), weeks[1].days.map { it.id })
        assertEquals(listOf("d1-BFA", "d2-BFB", "d3-BFA"), weeks[2].days.map { it.id })
    }

    @Test
    fun athlete_bodyweight_f_and_fv_are_relative_strength_at_rir_three() {
        val program = requireProgram(
            generate(NativeProfileKind.COMPLETE_ATHLETE.entryId, 2, CatalogLevel.INTERMEDIATE, BODYWEIGHT, 100),
            "atleta corporal intermedio",
        )
        val relativeStrengthSlots = requireNotNull(program.sourceRecipe).weeks.first().days
            .flatMap { it.slots }
            .filter { it.intent == SlotIntent.F || it.intent == SlotIntent.FV }
        assertTrue("no se materializó fuerza relativa corporal", relativeStrengthSlots.isNotEmpty())
        relativeStrengthSlots.forEach { slot ->
            val work = slot.workingSetsForTest()
            assertEquals("${slot.id}: 2 series", 2, work.size)
            assertTrue("${slot.id}: rango 5–8", work.all { it.repsMin == 5 && it.repsMax == 8 })
            assertTrue("${slot.id}: RIR 3", work.all { it.rir == 3 })
        }
    }

    @Test
    fun same_day_duplicate_configurations_merge_h_to_h_up_to_four_sets() {
        val program = requireProgram(
            generate(NativeProfileKind.MUSCLE.entryId, 4, CatalogLevel.INTERMEDIATE, DUMBBELLS, 60),
            "musculo4 mancuernas",
        )
        val recipe = requireNotNull(program.sourceRecipe)
        val ubDay = recipe.weeks.first().days.first { it.id?.contains("UB") == true }
        val configurations = ubDay.slots.map { it.lift.configurationId }
        assertEquals("slots duplicados en UB: $configurations", configurations.size, configurations.distinct().size)
        ubDay.slots.firstOrNull { it.lift.configurationId == "conventional_row__dumbbells" }?.let { mergedRow ->
            assertTrue("fusion H/H con mas de 4 series", mergedRow.workingSetsForTest().size <= 4)
        }
        // 13.3: supplementalOf siempre apunta a un slot del mismo dia.
        ubDay.slots.forEach { slot ->
            if (slot.supplementalOf != null) {
                assertTrue(
                    "supplementalOf apunta a un slot del dia",
                    ubDay.slots.any { it.id == slot.supplementalOf },
                )
            }
        }
    }

    @Test
    fun progression_contracts_expose_double_bodyweight_and_cardio_rules() {
        val loaded = NativeProgressionContracts(
            strategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
            exposuresBeforeProposal = 2,
        )
        assertEquals(NativeProgressionOutcome.NO_RECORD, loaded.exposureOutcome(null, true, true))
        assertEquals(NativeProgressionOutcome.KEEP, loaded.exposureOutcome(1, true, true))
        assertEquals(NativeProgressionOutcome.PROPOSE_INCREMENT, loaded.exposureOutcome(2, true, true))
        assertEquals(NativeProgressionOutcome.KEEP_OR_REGRESS, loaded.exposureOutcome(2, false, true))
        assertEquals(NativeProgressionOutcome.KEEP_OR_REGRESS, loaded.exposureOutcome(3, true, false))
        // Cardio: escalones ofertados 10->15->20->30, nunca 12/14.
        assertEquals(15, NativeCardioEscalation.nextOffered(10, dedicatedDay = false, userChosenMinutes = null))
        assertNull(
            "tras resistencia se detiene en 15",
            NativeCardioEscalation.nextOffered(15, dedicatedDay = false, userChosenMinutes = null),
        )
        assertEquals(15, NativeCardioEscalation.nextOffered(10, dedicatedDay = true, userChosenMinutes = null))
        assertEquals(20, NativeCardioEscalation.nextOffered(15, dedicatedDay = true, userChosenMinutes = null))
        assertEquals(30, NativeCardioEscalation.nextOffered(20, dedicatedDay = true, userChosenMinutes = null))
        assertNull(NativeCardioEscalation.nextOffered(30, dedicatedDay = true, userChosenMinutes = null))
        assertNull(
            "20/30 elegidos por el usuario se conservan sin reducir",
            NativeCardioEscalation.nextOffered(20, dedicatedDay = false, userChosenMinutes = 20),
        )
        assertNull(
            "si no cabe el escalon, se mantiene",
            NativeCardioEscalation.nextOffered(10, dedicatedDay = true, userChosenMinutes = null, budgetAllows = false),
        )
        // La receta expone su progresion (12.4).
        val bodyweightRecipe = requireNotNull(
            requireProgram(
                generate(NativeProfileKind.MUSCLE.entryId, 3, CatalogLevel.BEGINNER, BODYWEIGHT, 60),
                "musculo corporal",
            ).sourceRecipe,
        )
        assertEquals(
            NativeProgressionStrategy.BODYWEIGHT_VARIANT_ESCALATION,
            bodyweightRecipe.nativeProgression?.strategy,
        )
        assertEquals(2, bodyweightRecipe.nativeProgression?.exposuresBeforeProposal)
        val loadedRecipe = requireNotNull(
            requireProgram(
                generate(NativeProfileKind.STRENGTH.entryId, 3, CatalogLevel.INTERMEDIATE, BARBELL, 60),
                "fuerza barra",
            ).sourceRecipe,
        )
        assertEquals(NativeProgressionStrategy.REP_RANGE_THEN_LOAD, loadedRecipe.nativeProgression?.strategy)
    }

    // ─── B-02: tolerancia blanda de glúteos (límite 16, techo blando 17,5) ──────────────────────────────

    /**
     * Catálogo de prueba: suma `gluteus_medius` como músculo SECUNDARIO (0,5 serie de glúteo por serie, igual
     * en la política que en el contador de volumen real) a las configuraciones indicadas. Empuja un plan real
     * unas décimas por encima de su límite sin tocar el catálogo de producción.
     */
    private fun catalogWithExtraGluteSecondary(vararg configurationIds: String): ExerciseCatalogV2 {
        val targets = configurationIds.toSet()
        var touched = 0
        val modified = catalog.copy(
            families = catalog.families.map { family ->
                family.copy(
                    definitions = family.definitions.map { definition ->
                        definition.copy(
                            configurations = definition.configurations.map { configuration ->
                                if (configuration.id !in targets) {
                                    configuration
                                } else {
                                    touched++
                                    configuration.copy(
                                        profile = configuration.profile.copy(
                                            secondaryMuscles = (configuration.profile.secondaryMuscles + "gluteus_medius").distinct(),
                                        ),
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
        assertEquals("las configuraciones a modificar existen en el catálogo", targets.size, touched)
        return modified
    }

    private fun personalizerFor(
        catalogV2: ExerciseCatalogV2,
        glutesSoftBand: Double = GLUTES_SOFT_BAND,
    ) = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(catalogV2).also { runBlocking { it.load() } },
        glutesSoftBand,
    )

    /** Músculo corporal de 6 días, principiante, 100 min: testigo de mínimos directos de glúteo. */
    private val muscleBodyweightSixDaysInput = PersonalizerInput(
        catalogEntryId = NativeProfileKind.MUSCLE.entryId,
        focus = TrainingFocus.FULL_BODY,
        frequency = 6,
        equipment = BODYWEIGHT,
        level = CatalogLevel.BEGINNER,
        availableMinutes = 100,
    )

    /** Máximo TOTAL de glúteos para comprobar informes; no se usa como techo de PRIMARY. */
    private fun peakGluteTotalSets(program: com.example.kpkn.data.models.Program, catalogV2: ExerciseCatalogV2): Double {
        val lookup = catalogV2.toLegacyConfigurationLookup().values.toList()
        return weeksOf(program).maxOf { week ->
            val glutes = VolumeCalculator.calculateRoleSeparatedMuscleVolume(week.sessions, lookup)["Glúteos"]
            (glutes?.directSets ?: 0.0) + (glutes?.indirectSets ?: 0.0)
        }
    }

    /** Series de trabajo semanales de una configuración en una receta (máximo entre semanas). */
    private fun weeklySetsOf(recipe: com.example.kpkn.data.protocols.TrainingPlanRecipe, configurationId: String): Int =
        recipe.weeks.maxOf { week ->
            week.days.flatMap { it.slots }
                .filter { it.lift.configurationId == configurationId }
                .sumOf { it.workingSetsForTest().size }
        }

    @Test
    fun extra_secondary_glute_total_is_allowed_without_consuming_the_primary_band() {
        val control = personalizerFor(catalog).personalize("np-indirect", muscleBodyweightSixDaysInput)
        val controlProgram = requireProgram(control, "control Músculo corporal 6d")
        val targets = arrayOf(NativeCandidateTable.GLUTE_BRIDGE_BODYWEIGHT, "reverse_lunge__bodyweight")
        val boosted = catalogWithExtraGluteSecondary(*targets)
        val boostedMetadata = CatalogCompositionMetadataProvider.fromCatalog(boosted)
        val originalRecipe = requireNotNull(controlProgram.sourceRecipe)
        val originalPrimary = originalRecipe.weeks.maxOf { week ->
            SessionCompositionPolicy.weeklyPrimaryGroupSets(week, metadata)[com.example.kpkn.data.programs.KpknMuscleGroup.GLUTES] ?: 0.0
        }
        val expectedTotal = peakGluteTotalSets(controlProgram, catalog) +
            targets.sumOf { id -> weeklySetsOf(originalRecipe, id) * 0.5 }
        assertTrue("la premisa excede incluso 17,5 por trabajo secundario: $expectedTotal", expectedTotal > 17.5)
        listOf(0.0, GLUTES_SOFT_BAND).forEach { band ->
            val result = personalizerFor(boosted, band).personalize("np-indirect", muscleBodyweightSixDaysInput)
            val program = requireProgram(result, "total indirecto alto con banda=$band")
            assertEquals("el total indirecto no cambia dosis ni retiradas", originalRecipe, program.sourceRecipe)
            assertEquals(expectedTotal, peakGluteTotalSets(program, boosted), 0.001)
            assertTrue("no consume banda de PRIMARY", result.report.highVolume.isEmpty())
            val recipe = requireNotNull(program.sourceRecipe)
            val primary = recipe.weeks.maxOf { week ->
                SessionCompositionPolicy.weeklyPrimaryGroupSets(week, boostedMetadata)[com.example.kpkn.data.programs.KpknMuscleGroup.GLUTES] ?: 0.0
            }
            assertEquals(originalPrimary, primary, 0.001)
            assertTrue("el total se informa en lenguaje llano", result.report.planNotes.any {
                it.startsWith("Glúteos:") && "principales" in it && "secundario o de estabilización" in it
            })
            assertTrue("no se entrega una receta con HARD", ProgramRecipeValidator.hardFindings(recipe, boostedMetadata).isEmpty())
            assertTrue("la política informa el exceso indirecto", ProgramRecipeValidator.validate(recipe, boostedMetadata).any {
                it.severity == CompositionSeverity.SOFT && it.rule == "W2_INDIRECT_VOLUME"
            })
        }
    }

    private fun catalogWithAddedRole(muscle: String, primary: Boolean): ExerciseCatalogV2 = catalog.copy(
        families = catalog.families.map { family -> family.copy(
            definitions = family.definitions.map { definition -> definition.copy(
                configurations = definition.configurations.map { configuration ->
                    val profile = configuration.profile
                    configuration.copy(profile = profile.copy(
                        primaryMuscles = (profile.primaryMuscles.filterNot { it == muscle } + if (primary) listOf(muscle) else emptyList()).distinct(),
                        secondaryMuscles = profile.secondaryMuscles.filterNot { it == muscle },
                        stabilizerMuscles = (profile.stabilizerMuscles.filterNot { it == muscle } + if (primary) emptyList() else listOf(muscle)).distinct(),
                    ))
                },
            ) },
        ) },
    )

    @Test
    fun native_fitter_preserves_stabilizer_total_but_refuses_personal_primary_overflow() {
        val input = PersonalizerInput(
            NativeProfileKind.MUSCLE.entryId, TrainingFocus.FULL_BODY, 1,
            equipment = BARBELL, level = CatalogLevel.INTERMEDIATE, availableMinutes = 100,
            volumeRecommendations = listOf(VolumeRecommendation("Cuello", 0, 1, 1)),
        )
        val indirectCatalog = catalogWithAddedRole("neck", primary = false)
        val allowed = personalizerFor(indirectCatalog).personalize("np-role-neck", input)
        val program = requireProgram(allowed, "Cuello solo estabilizador con límite personal 1")
        val row = allowed.report.muscles.single { it.muscle == "Cuello" }
        assertEquals(1, row.mrv)
        assertEquals(0.0, row.directSets, 0.001)
        assertTrue("el total real supera el límite solo por estabilización", row.indirectSets > row.mrv)
        val firstWeek = weeksOf(program).first()
        val workSets = firstWeek.sessions.sumOf { session -> session.allExercises().sumOf { VolumeCalculator.countEffectiveSets(it.sets) } }
        assertEquals("peso STABILIZER 0,4 intacto", workSets * 0.4, row.indirectSets, 0.001)
        assertTrue("aviso informativo sin banda", allowed.report.highVolume.isEmpty())
        assertTrue(allowed.report.planNotes.any { it.startsWith("Cuello:") && "0 principales" in it })
        val overflowCatalog = catalogWithAddedRole("neck", primary = true)
        val rejected = personalizerFor(overflowCatalog).personalize("np-role-neck-primary", input)
        assertNull("las series PRIMARY no pueden superar el límite personal 1", rejected.program)
        assertEquals("COMPOSITION", rejected.report.reasonCode)
    }

    @Test
    fun shared_policy_group_does_not_assign_another_muscles_work_to_a_personal_limit() {
        val rhomboidsCatalog = catalogWithAddedRole("rhomboids", primary = true)
        val fixture = rhomboidsCatalog.copy(families = rhomboidsCatalog.families.map { family ->
            family.copy(definitions = family.definitions.map { definition ->
                definition.copy(configurations = definition.configurations.map { configuration ->
                    configuration.copy(profile = configuration.profile.copy(
                        primaryMuscles = configuration.profile.primaryMuscles.filterNot { it == "trapezius" },
                    ))
                })
            })
        })
        val input = PersonalizerInput(
            NativeProfileKind.MUSCLE.entryId, TrainingFocus.FULL_BODY, 1,
            equipment = BARBELL, level = CatalogLevel.INTERMEDIATE, availableMinutes = 100,
            volumeRecommendations = listOf(VolumeRecommendation("Trapecio", 0, 1, 1)),
        )
        val result = personalizerFor(fixture).personalize("np-shared-primary", input)
        val program = requireProgram(result, "Romboides PRIMARY con Trapecio personal=1")
        assertEquals(0.0, result.report.muscles.single { it.muscle == "Trapecio" }.directSets, 0.001)
        assertTrue(result.report.muscles.single { it.muscle == "Romboides" }.directSets > 1.0)
        assertTrue(ProgramRecipeValidator.hardFindings(requireNotNull(program.sourceRecipe),
            CatalogCompositionMetadataProvider.fromCatalog(fixture)).isEmpty())
    }

    @Test
    fun primary_glutes_above_the_soft_ceiling_are_rejected_with_composition() {
        val boosted = catalogWithAddedRole("gluteus_maximus", primary = true)
        // Incluso tras quitar C/I opcionales, 6 días conservan al menos 24 H principales:
        // tres BU con B 2 y tres BL con tres H 2. No se toca calendario ni dosis.
        listOf(0.0, GLUTES_SOFT_BAND).forEachIndexed { index, band ->
            val result = personalizerFor(boosted, band).personalize("np-primary-over-$index", muscleBodyweightSixDaysInput)
            assertNull("PRIMARY por encima de 17,5 se rechaza con banda=$band", result.program)
            assertEquals("COMPOSITION", result.report.reasonCode)
            assertTrue(result.report.limitations.any { "Glúteos" in it })
            assertTrue(result.report.highVolume.isEmpty())
        }
    }

    @Test
    fun the_soft_band_is_never_wider_than_what_the_policy_tolerates() {
        val catalogRepository = InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } }
        var failure: IllegalArgumentException? = null
        try {
            SimpleCyclePersonalizer(catalogRepository, GLUTES_SOFT_BAND + 0.5)
        } catch (expected: IllegalArgumentException) {
            failure = expected
        }
        assertNotNull("el ajustador no acepta una holgura mayor que la de la política", failure)
        // El rango válido incluye el apagado (0) y el valor de producción.
        SimpleCyclePersonalizer(catalogRepository, 0.0)
        SimpleCyclePersonalizer(catalogRepository, GLUTES_SOFT_BAND)
    }
}
