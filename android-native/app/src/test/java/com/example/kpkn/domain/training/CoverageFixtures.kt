package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.definitions.NativeProfileCalendars
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.onboarding.NativePlanFailureMapper
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluation
import com.example.kpkn.domain.onboarding.PlanCandidateRequest
import com.example.kpkn.domain.onboarding.PlanCatalogSnapshot
import com.example.kpkn.domain.onboarding.PlanCoverage
import com.example.kpkn.domain.onboarding.PlanDurationBreakdown
import com.example.kpkn.domain.onboarding.PlanGoalMatcher
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.domain.onboarding.PlanMaterializationOutcome
import com.example.kpkn.domain.onboarding.PlanMaterializationPort
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.SetupFocus
import com.example.kpkn.screens.onboarding.SetupGoal
import com.example.kpkn.screens.onboarding.SetupVolumeAnswers
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.inferredTrainingStyle
import com.example.kpkn.screens.onboarding.trainingReference
import kotlinx.coroutines.runBlocking

/**
 * Paquete A · A1 (curaduría de programas, 2026-10-03): fixtures de material y ayudantes
 * COMPARTIDOS entre [PlanGenerationCoverageT006Test] (rejilla histórica, solo E0–E7) y
 * [PlanCoverageContractTest] (contrato de cobertura C1–C5 sobre E0–E18).
 *
 * Aquí no hay ninguna aserción: solo la forma de las entradas y el puerto de materialización
 * del motor real. El fallo del fitter se traduce con [NativePlanFailureMapper], la MISMA
 * función que usa el wizard, y no con un `when` propio de las pruebas.
 */
internal object CoverageFixtures {

    /** Los cuatro objetivos de producto con su entrada propia (`nativeKind.entryId`). */
    enum class Profile(
        val goal: SetupGoal,
        val planGoal: PlanGoalProfile,
        val reference: TrainingReference?,
        val nativeKind: NativeProfileKind,
    ) {
        STRENGTH(SetupGoal.STRENGTH, PlanGoalProfile.STRENGTH, TrainingReference.POWERLIFTING, NativeProfileKind.STRENGTH),
        MUSCLE(SetupGoal.MUSCLE, PlanGoalProfile.MUSCLE, TrainingReference.HYPERTROPHY, NativeProfileKind.MUSCLE),
        POWERBUILDING(
            SetupGoal.STRENGTH_MUSCLE,
            PlanGoalProfile.STRENGTH_MUSCLE,
            TrainingReference.POWERBUILDING,
            NativeProfileKind.POWERBUILDING,
        ),
        COMPLETE_ATHLETE(
            SetupGoal.COMPLETE_ATHLETE,
            PlanGoalProfile.COMPLETE_ATHLETE,
            null,
            NativeProfileKind.COMPLETE_ATHLETE,
        ),
    }

    data class EquipmentFixture(val id: String, val availability: EquipmentAvailability) {
        val tokens: Set<String>
            get() = TrainingOptions(availability = availability).effectiveEquipment(emptySet())
    }

    // ─── Fixtures de material ──────────────────────────────────────────────────────────────

    /** Soportes confirmados de un gimnasio completo (los de E5/E6/E15/E18). */
    private val confirmedSupports: Map<String, ApparatusPresence> = mapOf(
        "bench_flat" to ApparatusPresence.PRESENT,
        "bench_adjustable" to ApparatusPresence.PRESENT,
        "squat_rack" to ApparatusPresence.PRESENT,
        "preacher_bench" to ApparatusPresence.PRESENT,
        "pullup_bar" to ApparatusPresence.PRESENT,
        "dip_bars" to ApparatusPresence.PRESENT,
        "low_bar_support" to ApparatusPresence.PRESENT,
        "ez_bar" to ApparatusPresence.PRESENT,
    )

    /** E0–E7: idénticos a los de T006 antes de A1 (Q2 sigue siendo 2 304 filas con los mismos conteos). */
    fun legacyFixtures(): List<EquipmentFixture> {
        val allCategories = EquipmentCategory.entries.toSet()
        val confirmedMachines = EFFECTIVE_EQUIPMENT_KEYS
            .filter { it.category == EquipmentCategory.MACHINES }
            .associate { it.key to ApparatusPresence.PRESENT }
        val confirmedStations = EFFECTIVE_EQUIPMENT_KEYS
            .filter { it.category in setOf(EquipmentCategory.CABLE, EquipmentCategory.SMITH_MACHINE) }
            .associate { it.key to ApparatusPresence.PRESENT }

        return listOf(
            EquipmentFixture("E0", EquipmentAvailability()),
            EquipmentFixture("E1", EquipmentAvailability(setOf(EquipmentCategory.BAND))),
            EquipmentFixture("E2", EquipmentAvailability(setOf(EquipmentCategory.DUMBBELLS))),
            EquipmentFixture(
                "E3",
                EquipmentAvailability(
                    categories = setOf(EquipmentCategory.DUMBBELLS, EquipmentCategory.SUPPORT),
                    supports = mapOf(
                        "bench_flat" to ApparatusPresence.PRESENT,
                        "bench_adjustable" to ApparatusPresence.PRESENT,
                    ),
                ),
            ),
            EquipmentFixture(
                "E4",
                EquipmentAvailability(
                    categories = setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT),
                    supports = mapOf(
                        "bench_flat" to ApparatusPresence.PRESENT,
                        "squat_rack" to ApparatusPresence.PRESENT,
                    ),
                ),
            ),
            // Broad gym categories are present, but individual machines remain UNKNOWN.
            EquipmentFixture(
                "E5",
                EquipmentAvailability(categories = allCategories, supports = confirmedSupports),
            ),
            EquipmentFixture(
                "E6",
                EquipmentAvailability(
                    categories = allCategories,
                    apparatus = confirmedMachines + confirmedStations,
                    supports = confirmedSupports,
                ),
            ),
            EquipmentFixture(
                "E7",
                EquipmentAvailability(
                    categories = setOf(EquipmentCategory.SUPPORT, EquipmentCategory.PULL_UP_BAR),
                    supports = mapOf(
                        "pullup_bar" to ApparatusPresence.PRESENT,
                        "low_bar_support" to ApparatusPresence.PRESENT,
                    ),
                ),
            ),
        )
    }

    /**
     * E8–E18 (contrato de cobertura C1). El panel de aparatos NO tiene una llave de Smith (solo la
     * categoría `SMITH_MACHINE`; `smith_machine` es un `equipmentId` del catálogo), así que E11 no
     * declara ninguna llave de aparato.
     */
    fun extendedFixtures(): List<EquipmentFixture> {
        val allCategories = EquipmentCategory.entries.toSet()
        val present = ApparatusPresence.PRESENT
        val absent = ApparatusPresence.ABSENT

        // E9: máquinas y poleas declaradas llave por llave + banco plano.
        val machinesPreset = EquipmentAvailability(
            categories = setOf(EquipmentCategory.MACHINES, EquipmentCategory.CABLE),
            apparatus = EFFECTIVE_EQUIPMENT_KEYS
                .filter { it.category == EquipmentCategory.MACHINES || it.category == EquipmentCategory.CABLE }
                .associate { it.key to present },
            supports = mapOf(EquipmentKeys.BENCH_FLAT to present),
        )

        return listOf(
            // E8: gimnasio sin confirmar, todo UNKNOWN.
            EquipmentFixture("E8", EquipmentAvailability(categories = allCategories)),
            EquipmentFixture("E9", machinesPreset),
            EquipmentFixture("E10", EquipmentAvailability(categories = setOf(EquipmentCategory.KETTLEBELL))),
            EquipmentFixture(
                "E11",
                EquipmentAvailability(
                    categories = setOf(EquipmentCategory.SMITH_MACHINE, EquipmentCategory.SUPPORT),
                    supports = mapOf(EquipmentKeys.BENCH_FLAT to present),
                ),
            ),
            EquipmentFixture(
                "E12",
                EquipmentAvailability(
                    categories = setOf(EquipmentCategory.BAND, EquipmentCategory.PULL_UP_BAR),
                    supports = mapOf(EquipmentKeys.PULLUP_BAR to present),
                ),
            ),
            EquipmentFixture(
                "E13",
                EquipmentAvailability(
                    categories = setOf(EquipmentCategory.PULL_UP_BAR),
                    supports = mapOf(EquipmentKeys.PULLUP_BAR to present),
                ),
            ),
            EquipmentFixture(
                "E14",
                machinesPreset.copy(
                    categories = machinesPreset.categories + EquipmentCategory.CARDIO,
                    apparatus = machinesPreset.apparatus + (SetupApparatusPanel.OUTDOOR_BIKE_KEY to present),
                ),
            ),
            // E15: como E5, pero con rack y bancos negados de forma explícita.
            EquipmentFixture(
                "E15",
                EquipmentAvailability(
                    categories = allCategories,
                    supports = confirmedSupports + mapOf(
                        EquipmentKeys.SQUAT_RACK to absent,
                        EquipmentKeys.BENCH_FLAT to absent,
                        EquipmentKeys.BENCH_ADJUSTABLE to absent,
                    ),
                ),
            ),
            EquipmentFixture("E16", EquipmentAvailability(categories = setOf(EquipmentCategory.BARBELL))),
            // E17: barra, mancuernas y bancos; el rack no se declaró (UNKNOWN, no negado).
            EquipmentFixture(
                "E17",
                EquipmentAvailability(
                    categories = setOf(EquipmentCategory.BARBELL, EquipmentCategory.DUMBBELLS, EquipmentCategory.SUPPORT),
                    supports = mapOf(
                        EquipmentKeys.BENCH_FLAT to present,
                        EquipmentKeys.BENCH_ADJUSTABLE to present,
                    ),
                ),
            ),
            // E18: como E5 (soportes confirmados) y solo la prensa declarada entre los aparatos.
            EquipmentFixture(
                "E18",
                EquipmentAvailability(
                    categories = allCategories,
                    apparatus = mapOf(EquipmentKeys.LEG_PRESS to present),
                    supports = confirmedSupports,
                ),
            ),
        )
    }

    /** E0–E18. */
    fun allFixtures(): List<EquipmentFixture> = legacyFixtures() + extendedFixtures()

    // ─── Entradas del planificador y del evaluador ─────────────────────────────────────────

    fun levelOf(experience: SetupExperience): CatalogLevel = when (experience) {
        SetupExperience.NEW, SetupExperience.RETURNING -> CatalogLevel.BEGINNER
        SetupExperience.INTERMEDIATE -> CatalogLevel.INTERMEDIATE
        SetupExperience.ADVANCED -> CatalogLevel.ADVANCED
    }

    fun weekdays(days: Int): List<Int> = NativeProfileCalendars.DEFAULT_WEEKDAYS.getValue(days)

    fun draft(
        profile: Profile,
        experience: SetupExperience,
        days: Int,
        minutes: Int,
        equipment: EquipmentFixture,
    ) = SetupWizardDraft(
        goal = profile.goal,
        experience = experience,
        focus = SetupFocus.FULL_BODY,
        volumeAnswers = SetupVolumeAnswers(style = profile.goal.inferredTrainingStyle),
        daysPerWeek = days,
        selectedWeekdays = weekdays(days).toSet(),
        minutesPerSession = minutes,
        trainingOptions = SetupTrainingOptions(availability = equipment.availability),
    )

    /**
     * Entrada del planner tal como la arma el wizard (DEC-w2-06): Atleta completo lleva el
     * prefiltro de capacidades declaradas y los demás objetivos no filtran por capacidades.
     */
    fun plannerInput(draft: SetupWizardDraft): SetupTrainingPlannerInput =
        SetupTrainingPlannerInput(
            reference = draft.trainingReference(),
            frequency = draft.daysPerWeek,
            equipment = draft.trainingOptions.effectiveEquipment(emptySet()),
            level = levelOf(requireNotNull(draft.experience)),
            focus = TrainingFocus.FULL_BODY,
            requiredCapabilities = if (draft.goal == SetupGoal.COMPLETE_ATHLETE) {
                PlanGoalMatcher.requiredCapabilities(PlanGoalProfile.COMPLETE_ATHLETE)
            } else {
                emptySet()
            },
        )

    /**
     * Pedido normalizado de un candidato. [cardioMinutes] y [cardioType] solo cuentan para
     * Atleta completo (el único perfil que exige cardio); el tipo no cabe en
     * [PlanCandidateRequest], así que viaja en `inputKey` y en [materializer].
     */
    fun request(
        profile: Profile,
        experience: SetupExperience,
        days: Int,
        minutes: Int,
        equipment: EquipmentFixture,
        cardioMinutes: Int = 10,
        cardioType: CardioType = CardioType.WALK,
    ): PlanCandidateRequest {
        val isAthlete = profile == Profile.COMPLETE_ATHLETE
        val inputKey = buildList<Any> {
            add(profile.name)
            add(experience.name)
            add(days)
            add(weekdays(days).joinToString(","))
            add(minutes)
            add(equipment.id)
            add(equipment.tokens.sorted().joinToString(","))
            if (isAthlete) add("cardio=${cardioType.name}:$cardioMinutes")
        }.joinToString("|")
        return PlanCandidateRequest(
            inputKey = inputKey,
            goalProfile = profile.planGoal,
            level = levelOf(experience),
            focus = TrainingFocus.FULL_BODY,
            reference = profile.reference,
            daysPerWeek = days,
            weekdays = weekdays(days).toSet(),
            minutesPerSession = minutes,
            effectiveEquipment = equipment.tokens,
            cardioMinutes = if (isAthlete) cardioMinutes else null,
            requiresCardio = isAthlete,
            planCatalogRevision = PersonalizedPlanCatalog.REVISION,
            exerciseCatalogRevision = CatalogCompositionTestSupport.catalog.catalogRevision,
        )
    }

    /**
     * Paquete A · A.C1: evaluación `Ready` mínima, SIN programa real (un `Program` vacío). Sirve a las pruebas que solo
     * preguntan «¿queda Ready?» (el asesor de reparaciones) sin pagar ni retener un programa materializado: el
     * contrato de cobertura guarda miles de resultados en su memo y un programa por fila no cabe en la memoria del JVM.
     */
    fun stubReady(planId: String, inputKey: String): PlanCandidateEvaluation.Ready =
        PlanCandidateEvaluation.Ready(
            planId = planId,
            preparedPlan = Program(id = "stub-$planId", name = "stub"),
            recipeSnapshot = null,
            provenance = null,
            durationBreakdown = PlanDurationBreakdown(emptyList()),
            inputKey = inputKey,
            unresolvedWorkoutLoads = emptyList(),
            coverage = PlanCoverage(
                frequency = 1,
                hasStrength = true,
                hasHypertrophy = true,
                hasPower = true,
                hasCardio = true,
            ),
        )

    /** Un generador con el catálogo aprobado ya cargado (cárgalo UNA vez por prueba, no por fila). */
    fun personalizer(): SimpleCyclePersonalizer = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(CatalogCompositionTestSupport.catalog).also { runBlocking { it.load() } },
    )

    fun snapshot(entries: List<CatalogEntry>, catalog: ExerciseCatalogV2): PlanCatalogSnapshot =
        PlanCatalogSnapshot(
            entries = entries,
            planRevision = PersonalizedPlanCatalog.REVISION,
            exerciseCatalogRevision = catalog.catalogRevision,
        )

    /**
     * Puerto de materialización con el motor real. Un rechazo del fitter se traduce con
     * [NativePlanFailureMapper.typedFailure] (misma excepción tipada y mismos `requiredMinutes =
     * maxSessionMinutes` que el wizard); sin motivo cerrado reconocido se lanza una
     * [IllegalStateException] y el evaluador la clasifica como INTERNAL_MATERIALIZATION.
     */
    fun materializer(
        generator: SimpleCyclePersonalizer,
        experience: SetupExperience,
        equipment: EquipmentFixture,
        cardioType: CardioType = CardioType.WALK,
    ) = PlanMaterializationPort { entry: CatalogEntry, candidate: PlanCandidateRequest ->
        val result = generator.personalize(
            programId = "t006-${candidate.inputKey.hashCode().toUInt().toString(16)}-${entry.id.substringAfterLast(':')}",
            input = PersonalizerInput(
                catalogEntryId = entry.id,
                focus = candidate.focus,
                frequency = candidate.daysPerWeek,
                weekdays = candidate.weekdays.sorted(),
                equipment = emptySet(),
                level = levelOf(experience),
                availableMinutes = candidate.minutesPerSession,
                cardio = if (candidate.requiresCardio) {
                    CardioPreference(cardioType, requireNotNull(candidate.cardioMinutes))
                } else {
                    null
                },
            ),
            options = TrainingOptions(availability = equipment.availability),
        )
        val program = result.program
        if (program == null) {
            throw NativePlanFailureMapper.typedFailure(result.report)
                ?: IllegalStateException(
                    "${entry.id}: rechazo sin motivo de producto reconocido " +
                        "(${result.report.reasonCode}): ${result.report.limitations}",
                )
        }
        PlanMaterializationOutcome(
            program = program,
            recipe = program.sourceRecipe,
            report = result.report,
        )
    }
}
