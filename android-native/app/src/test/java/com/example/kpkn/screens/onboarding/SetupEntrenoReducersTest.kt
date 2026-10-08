package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSuggestions
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reductores puros de los pasos de Entreno v2: lugares, material, objetivo, día fuerte, días, tiempo, capacidades,
 * músculos y marcas. Todos escriben un dato real del borrador y mantienen sus derivados (los campos que el motor
 * actual sigue leyendo); ninguno mueve el cursor ni registra respuestas.
 */
class SetupEntrenoReducersTest {

    private val gym = TrainingPlace.GYM
    private val home = TrainingPlace.HOME
    private val park = TrainingPlace.PUBLIC

    private fun answered(draft: SetupWizardDraft, vararg steps: SetupStepId): SetupWizardDraft = draft.copy(
        declaredSteps = draft.declaredSteps + steps,
        stepProgress = draft.stepProgress.copy(
            answers = draft.stepProgress.answers + steps.associateWith { SetupAnswerProvenance.USER_DECLARED },
        ),
    )

    // ── Lugares ────────────────────────────────────────────────────────────────

    @Test
    fun theFirstPlaceSeedsTheMaterialAndDerivesTheLegacyEnvironment() {
        val atGym = SetupWizardDraft().withPlaces(setOf(gym))
        assertEquals(setOf(gym), atGym.trainingPlaces)
        assertEquals("gym", atGym.trainingEnvironment)
        assertEquals(setOf(SetupEquipment.GYM), atGym.equipment)
        assertEquals(EquipmentSymbols.seedFor(setOf(gym)), atGym.selectedEquipmentSymbols())
        assertNotNull(atGym.trainingOptions.availability)

        val atHome = SetupWizardDraft().withPlaces(setOf(home))
        assertEquals("home", atHome.trainingEnvironment)
        assertTrue(atHome.equipment.isEmpty())
        assertTrue("casa no asume nada", EquipmentSymbols.isBodyweightOnly(atHome.trainingOptions.availability))

        val atPark = SetupWizardDraft().withPlaces(setOf(park))
        assertEquals("home", atPark.trainingEnvironment)
        assertEquals(EquipmentSymbols.seedFor(setOf(park)), atPark.selectedEquipmentSymbols())

        // Con gimnasio entre varios lugares, el entorno derivado es el del gimnasio.
        assertEquals("gym", SetupWizardDraft().withPlaces(setOf(home, gym)).trainingEnvironment)
    }

    @Test
    fun placesAreStoredInTheContractOrderAndTheSelectionIsReadBackFromThem() {
        val draft = SetupWizardDraft().withStepChoices(SetupStepId.EQUIPMENT, setOf("public", "gym"))
        assertEquals(listOf(gym, park), draft.trainingPlaces.toList())
        assertEquals(setOf("gym", "public"), draft.selectedValues(SetupStepId.EQUIPMENT))
        // La selección no se guarda aparte: sale de `trainingPlaces`.
        assertNull(draft.stepSelections[SetupStepId.EQUIPMENT])
    }

    @Test
    fun theOldSingleValueApiStillSetsExactlyThatPlace() {
        val draft = SetupWizardDraft().withStepChoice(SetupStepId.EQUIPMENT, "gym")
            .withStepChoice(SetupStepId.EQUIPMENT, "home")
        assertEquals(setOf(home), draft.trainingPlaces)
        // Un entorno antiguo se lee como el lugar que hoy le corresponde.
        assertEquals(setOf(gym), SetupWizardDraft().withStepChoice(SetupStepId.EQUIPMENT, "machines").trainingPlaces)
        // Un valor desconocido no cambia nada.
        assertSame(draft, draft.withStepChoice(SetupStepId.EQUIPMENT, "terraza"))
    }

    @Test
    fun addingAPlaceThatChangesNothingKeepsTheMaterialConfirmation() {
        val confirmed = answered(SetupWizardDraft().withPlaces(setOf(gym)), SetupStepId.AVAILABILITY)
        val withHome = confirmed.withPlaces(setOf(gym, home))
        assertEquals(confirmed.trainingOptions.availability, withHome.trainingOptions.availability)
        assertTrue(SetupStepId.AVAILABILITY in withHome.stepProgress.answers)
        assertTrue(SetupStepId.AVAILABILITY in withHome.declaredSteps)
        // Re-escribir los mismos lugares tampoco toca nada.
        assertEquals(withHome, withHome.withPlaces(setOf(gym, home)))
    }

    @Test
    fun aPlaceThatChangesTheMaterialRetiresTheMaterialConfirmationWithoutLosingAnythingElse() {
        val confirmed = answered(SetupWizardDraft().withPlaces(setOf(home)), SetupStepId.AVAILABILITY, SetupStepId.EQUIPMENT)
            .withMaterial(setOf(EquipmentSymbolId.DUMBBELLS))
        val withPark = confirmed.withPlaces(setOf(home, park))
        // Se conservan las mancuernas y se suma la estructura del parque.
        assertEquals(
            setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS),
            withPark.selectedEquipmentSymbols(),
        )
        assertFalse(SetupStepId.AVAILABILITY in withPark.stepProgress.answers)
        assertFalse(SetupStepId.AVAILABILITY in withPark.declaredSteps)
        assertTrue("EQUIPMENT sigue confirmado", SetupStepId.EQUIPMENT in withPark.stepProgress.answers)
    }

    @Test
    fun switchingFromTheGymToHomeKeepsWhatHomeStillOffersAndWhatWasTurnedOffStaysOff() {
        val atGym = SetupWizardDraft().withPlaces(setOf(gym))
            .withMaterialToggled(EquipmentSymbolId.RINGS)
            .withMaterialToggled(EquipmentSymbolId.RACK)
        val before = atGym.selectedEquipmentSymbols()
        assertTrue(EquipmentSymbolId.RINGS in before)
        assertFalse(EquipmentSymbolId.RACK in before)
        // En casa se ofrece el mismo material que en el gimnasio: nada de lo marcado sale ni nada apagado se enciende.
        val atHome = atGym.withPlaces(setOf(home))
        assertEquals(before, atHome.selectedEquipmentSymbols())
    }

    @Test
    fun leavingTheGymForAParkDropsTheEquipmentTheParkDoesNotOffer() {
        val atPark = SetupWizardDraft().withPlaces(setOf(gym)).withPlaces(setOf(park))
        val offered = EquipmentSymbols.symbolsFor(setOf(park)).toSet()
        assertTrue(atPark.selectedEquipmentSymbols().all { it in offered })
        assertTrue(EquipmentSymbolId.PULL_UP_BAR in atPark.selectedEquipmentSymbols())
        assertFalse(EquipmentSymbolId.RACK in atPark.selectedEquipmentSymbols())
        assertFalse(EquipmentSymbolId.MACHINES in atPark.selectedEquipmentSymbols())
    }

    @Test
    fun removingAPlaceKeepsWhatTheRemainingPlacesStillOffer() {
        val both = SetupWizardDraft().withPlaces(setOf(gym, park))
        val onlyPark = both.withPlaces(setOf(park))
        val offered = EquipmentSymbols.symbolsFor(setOf(park)).toSet()
        assertEquals(
            EquipmentSymbols.seedFor(setOf(gym, park)).filterTo(linkedSetOf()) { it in offered },
            onlyPark.selectedEquipmentSymbols(),
        )
    }

    @Test
    fun withoutPlacesTheDeclaredMaterialIsKeptAndTheEnvironmentIsCleared() {
        val atGym = SetupWizardDraft().withPlaces(setOf(gym))
        val none = atGym.withPlaceToggled(gym)
        assertTrue(none.trainingPlaces.isEmpty())
        assertNull(none.trainingEnvironment)
        assertEquals(atGym.trainingOptions.availability, none.trainingOptions.availability)
        assertEquals(setOf(gym), none.withPlaceToggled(gym).trainingPlaces)
    }

    @Test
    fun placesPerDayOnlyExistWithTwoOrMorePlacesAndDefaultToTheFirstInOrder() {
        val draft = SetupWizardDraft().withPlaces(setOf(park, home)).withWeekdays(setOf(1, 3, 5))
        // Por defecto, el primero en el orden gimnasio, casa, espacios públicos.
        assertEquals(home, draft.placeForDay(1))
        assertEquals(mapOf(1 to home, 3 to home, 5 to home), draft.effectiveDayPlaces())
        val moved = draft.withDayPlace(3, park)
        assertEquals(park, moved.placeForDay(3))
        assertEquals(home, moved.placeForDay(5))
        // Un lugar que no se eligió o un día que no se entrena no se escriben.
        assertSame(moved, moved.withDayPlace(3, gym))
        assertSame(moved, moved.withDayPlace(2, park))
        // Quitar un lugar descarta los lugares por día que ya no existen; con uno solo no hay elección por día.
        assertEquals(emptyMap<Int, TrainingPlace>(), moved.withPlaces(setOf(home)).dayPlaces)
        assertEquals(home, moved.withPlaces(setOf(home)).placeForDay(3))
        // Un día que deja de entrenarse pierde su lugar.
        assertFalse(3 in moved.withWeekdays(setOf(1, 5)).dayPlaces)
        // Volver al lugar por defecto.
        assertFalse(3 in moved.withDayPlace(3, null).dayPlaces)
    }

    // ── Material ───────────────────────────────────────────────────────────────

    @Test
    fun theMaterialIsWrittenAsEngineAvailabilityAndReadBackAsSymbols() {
        val draft = SetupWizardDraft().withPlaces(setOf(gym))
            .withMaterial(setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.BENCH, EquipmentSymbolId.DUMBBELLS))
        assertEquals(
            setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.BENCH, EquipmentSymbolId.DUMBBELLS),
            draft.selectedEquipmentSymbols(),
        )
        val availability = checkNotNull(draft.trainingOptions.availability)
        assertEquals(
            setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT, EquipmentCategory.DUMBBELLS),
            availability.categories,
        )
        assertEquals(ApparatusPresence.PRESENT, availability.presenceOf("bench_flat"))
        assertEquals(ApparatusPresence.ABSENT, availability.presenceOf("squat_rack"))
        assertEquals(draft.selectedValues(SetupStepId.AVAILABILITY), draft.selectedEquipmentSymbols().map { it.name }.toSet())
    }

    @Test
    fun anEmptyOrBodyweightOnlySelectionIsAValidBodyweightDeclaration() {
        val gymDraft = SetupWizardDraft().withPlaces(setOf(gym))
        for (selection in listOf(emptySet(), setOf(EquipmentSymbolId.BODYWEIGHT_ONLY))) {
            val draft = gymDraft.withMaterial(selection)
            assertEquals(com.example.kpkn.data.models.EquipmentAvailability(), draft.trainingOptions.availability)
            assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), draft.selectedEquipmentSymbols())
            assertTrue(SetupWizardValidation.validateStep(draft, SetupStepId.AVAILABILITY).none { it.isBlocking })
        }
    }

    @Test
    fun bodyweightOnlyIsExclusiveInTheToggleAndInTheGenericApi() {
        val draft = SetupWizardDraft().withPlaces(setOf(gym))
        val withBody = draft.withMaterialToggled(EquipmentSymbolId.BODYWEIGHT_ONLY)
        assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), withBody.selectedEquipmentSymbols())
        assertEquals(
            setOf(EquipmentSymbolId.BANDS),
            withBody.withMaterialToggled(EquipmentSymbolId.BANDS).selectedEquipmentSymbols(),
        )
        val viaGeneric = draft.withStepChoices(
            SetupStepId.AVAILABILITY,
            setOf(EquipmentSymbolId.BARBELL.name, EquipmentSymbolId.BODYWEIGHT_ONLY.name),
        )
        assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), viaGeneric.selectedEquipmentSymbols())
    }

    @Test
    fun symbolsThePlacesDoNotOfferAreIgnored() {
        val atPark = SetupWizardDraft().withPlaces(setOf(park))
        val draft = atPark.withMaterial(setOf(EquipmentSymbolId.RACK, EquipmentSymbolId.PULL_UP_BAR))
        assertEquals(setOf(EquipmentSymbolId.PULL_UP_BAR), draft.selectedEquipmentSymbols())
    }

    @Test
    fun writingTheMaterialDoesNotRetireItsOwnConfirmationButMarksAnIncompatibleGoalForReview() {
        val gymDraft = answered(SetupWizardDraft().withPlaces(setOf(gym)), SetupStepId.AVAILABILITY)
            .withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        val noRack = gymDraft.withMaterialToggled(EquipmentSymbolId.RACK)
        assertTrue("el paso de material sigue siendo suyo", SetupStepId.AVAILABILITY in noRack.stepProgress.answers)
        assertEquals(TrainingGoalProfile.POWERLIFTING, noRack.goalProfile)
        assertTrue(SetupStepId.GOAL in noRack.stepProgress.pendingReview)
        // Y con material que encaja no se marca nada.
        assertFalse(SetupStepId.GOAL in gymDraft.withMaterialToggled(EquipmentSymbolId.BANDS).stepProgress.pendingReview)
    }

    // ── Objetivo ───────────────────────────────────────────────────────────────

    @Test
    fun theGoalProfileIsTheDataAndTheLegacyGoalAndStyleAreDerived() {
        for (profile in TrainingGoalProfile.entries) {
            val draft = SetupWizardDraft().withGoalProfile(profile)
            assertEquals(profile, draft.goalProfile)
            assertEquals(GoalProfileMapping.setupGoalOf(profile), draft.goal)
            assertEquals(GoalProfileMapping.trainingStyleOf(profile), draft.volumeAnswers.style)
            assertEquals(setOf(EntrenoStepValuesAccess.value(profile)), draft.selectedValues(SetupStepId.GOAL))
        }
        val powerlifting = SetupWizardDraft().withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        assertEquals(SetupGoal.STRENGTH, powerlifting.goal)
        assertEquals(TrainingStyle.POWERLIFTER, powerlifting.volumeAnswers.style)
    }

    @Test
    fun theOldGoalValuesStillWorkAsTodaysProfiles() {
        fun goalOf(value: String) = SetupWizardDraft().withStepChoice(SetupStepId.GOAL, value)
        assertEquals(TrainingGoalProfile.POWERLIFTING, goalOf("strength").goalProfile)
        assertEquals(SetupGoal.STRENGTH, goalOf("strength").goal)
        assertEquals(TrainingGoalProfile.BODYBUILDING, goalOf("muscle").goalProfile)
        assertEquals(SetupGoal.MUSCLE, goalOf("muscle").goal)
        assertEquals(TrainingGoalProfile.STRENGTH_MUSCLE, goalOf("strength_muscle").goalProfile)
        assertEquals(SetupGoal.COMPLETE_ATHLETE, goalOf("complete_athlete").goal)
        assertEquals(SetupGoal.FUNCTIONAL, goalOf("health").goal)
        assertSame(goalOf("strength").goalProfile, goalOf("powerlifting").goalProfile)
        assertNull(goalOf("crossfit").goalProfile)
    }

    @Test
    fun changingTheProfileRecalibratesAndKeepsWhatWasDeclared() {
        val calibrated = SetupWizardDraft()
            .withStepChoice(SetupStepId.VOLUME_TECHNIQUE, "2")
            .withStepChoice(SetupStepId.VOLUME_CONSISTENCY, "2")
            .withStepChoice(SetupStepId.VOLUME_STRENGTH, "2")
            .withStepChoice(SetupStepId.VOLUME_MOBILITY, "2")
            .withStepChoice(SetupStepId.CARDIO_TYPE, "RUN_OUTDOOR")
            .withLiftMark(LiftMark.SQUAT, 120.0)
        assertNull("sin perfil no hay estilo ni calibración", calibrated.volumeCalibrationProfile)
        val asPowerlifter = calibrated.withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        assertEquals(TrainingStyle.POWERLIFTER, asPowerlifter.volumeCalibrationProfile?.trainingStyle)
        val asBodybuilder = asPowerlifter.withGoalProfile(TrainingGoalProfile.BODYBUILDING)
        assertEquals(TrainingStyle.BODYBUILDER, asBodybuilder.volumeCalibrationProfile?.trainingStyle)
        assertEquals(asPowerlifter.cardioType, asBodybuilder.cardioType)
        assertEquals(asPowerlifter.liftMarks, asBodybuilder.liftMarks)
        // Los perfiles generales también calibran (estilo de fuerza y músculo).
        assertEquals(
            TrainingStyle.POWERBUILDER,
            calibrated.withGoalProfile(TrainingGoalProfile.FUNCTIONAL_HEALTH).volumeCalibrationProfile?.trainingStyle,
        )
    }

    @Test
    fun aSpecificProfileThatDoesNotFitTheMaterialCannotBeConfirmedButIsKept() {
        val atHome = SetupWizardDraft().withPlaces(setOf(home)).withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        assertEquals(TrainingGoalProfile.POWERLIFTING, atHome.goalProfile)
        val check = SetupWizardValidation.validateStep(atHome, SetupStepId.GOAL).single()
        assertEquals(SetupValueState.INVALID, check.state)
        assertTrue(check.isBlocking)
        assertTrue("dice qué le falta", check.message.orEmpty().contains("Necesita barra, rack y banco."))
        // Un perfil general nunca bloquea.
        assertTrue(
            SetupWizardValidation.validateStep(atHome.withGoalProfile(TrainingGoalProfile.FUNCTIONAL_HEALTH), SetupStepId.GOAL)
                .none { it.isBlocking },
        )
    }

    @Test
    fun theMuscleSuggestionsOfTheProfileArePreloadedOnlyWhileThePersonHasNotTouchedPriorities() {
        val suggested = SetupWizardDraft().withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        assertEquals(
            MuscleSuggestions.forProfile(TrainingGoalProfile.POWERLIFTING),
            MuscleSymbols.symbolsOf(suggested.trainingOptions.orderPriorities),
        )
        assertFalse(SetupStepId.PRIORITIES in suggested.stepProgress.answers)
        assertFalse(SetupStepId.PRIORITIES in suggested.declaredSteps)
        // Cambiar de perfil cambia la sugerencia mientras nadie haya tocado el paso.
        val armWrestling = suggested.withGoalProfile(TrainingGoalProfile.ARMWRESTLING)
        assertEquals(
            setOf(MuscleSymbol.FOREARMS, MuscleSymbol.BICEPS),
            MuscleSymbols.symbolsOf(armWrestling.trainingOptions.orderPriorities),
        )
        // Con Culturismo («libre») no se sugiere nada.
        assertTrue(armWrestling.withGoalProfile(TrainingGoalProfile.BODYBUILDING).trainingOptions.orderPriorities.isEmpty())
        // Una elección de la persona (aunque sea omitir) nunca se pisa.
        val touched = suggested.copy(declaredSteps = suggested.declaredSteps + SetupStepId.PRIORITIES).withMusclesCleared()
        assertTrue(touched.withGoalProfile(TrainingGoalProfile.STRONGMAN).trainingOptions.orderPriorities.isEmpty())
    }

    // ── Día fuerte, días y tiempo ──────────────────────────────────────────────

    @Test
    fun theFreshDayIsTheWeekStartUntilThePersonMovesTheStart() {
        val fresh = SetupWizardDraft().withFreshestDay(4)
        assertEquals(4, fresh.freshestDay)
        assertEquals(4, fresh.weekStartDay)
        assertEquals(setOf("4"), fresh.selectedValues(SetupStepId.FRESH_DAY))
        // Cambiar el día fuerte arrastra el inicio mientras nadie lo haya movido…
        assertEquals(6, fresh.withFreshestDay(6).weekStartDay)
        // …y un inicio elegido a mano se respeta.
        val manual = fresh.withWeekStart(2).withFreshestDay(6)
        assertEquals(6, manual.freshestDay)
        assertEquals(2, manual.weekStartDay)
        // Volver a «seguir al día fuerte».
        assertEquals(6, manual.withWeekStart(null).weekStartDay)
        // Valores fuera de 1..7 no se escriben.
        assertNull(fresh.withFreshestDay(9).freshestDay)
        assertSame(fresh.freshestDay, fresh.withStepChoice(SetupStepId.FRESH_DAY, "4").freshestDay)
    }

    @Test
    fun weekdaysAreOneToSevenAndKeepTheDayCountInSync() {
        val draft = SetupWizardDraft().withWeekdays(setOf(5, 1, 3))
        assertEquals(setOf(1, 3, 5), draft.selectedWeekdays)
        assertEquals(3, draft.daysPerWeek)
        assertEquals(setOf("1", "3", "5"), draft.selectedValues(SetupStepId.WEEKDAYS))
        assertEquals(7, SetupWizardDraft().withWeekdays((1..7).toSet()).daysPerWeek)
        assertEquals(setOf(1, 7), SetupWizardDraft().withWeekdays(setOf(0, 1, 7, 8)).selectedWeekdays)
        val cleared = draft.withWeekdays(emptySet())
        assertNull("sin días no hay número de días", cleared.daysPerWeek)
        assertTrue(SetupWizardValidation.validateStep(cleared, SetupStepId.WEEKDAYS).single().isBlocking)
        assertTrue(SetupWizardValidation.validateStep(draft, SetupStepId.WEEKDAYS).none { it.isBlocking })
        assertTrue(SetupWizardValidation.validateStep(draft.withWeekdays((1..7).toSet()), SetupStepId.WEEKDAYS).none { it.isBlocking })
    }

    @Test
    fun theGenericToggleApiBuildsTheWeekAndTheDayCountFollows() {
        var draft = SetupWizardDraft()
        for (day in listOf("2", "4", "6")) {
            draft = draft.withStepChoices(
                SetupStepId.WEEKDAYS,
                setupToggleExclusive(draft.selectedValues(SetupStepId.WEEKDAYS), day, emptySet()),
            )
        }
        assertEquals(setOf(2, 4, 6), draft.selectedWeekdays)
        assertEquals(3, draft.daysPerWeek)
        assertEquals(setOf(2, 6), draft.withWeekdayToggled(4).selectedWeekdays)
        assertEquals(2, draft.withWeekdayToggled(4).daysPerWeek)
    }

    @Test
    fun sessionMinutesAreAMultipleOfFiveWithinThirtyAndOneHundredEighty() {
        assertEquals(45, SetupWizardDraft().withStepNumber(SetupStepId.SESSION_TIME, 47.0).minutesPerSession)
        // El reloj ya no ofrece menos de 30: los 5, 20 y 25 min de antes se escriben como el mínimo.
        assertEquals(30, SetupWizardDraft().withSessionMinutes(5).minutesPerSession)
        assertEquals(30, SetupWizardDraft().withSessionMinutes(20).minutesPerSession)
        assertEquals(30, SetupWizardDraft().withSessionMinutes(25).minutesPerSession)
        assertEquals(30, SetupWizardDraft().withStepNumber(SetupStepId.SESSION_TIME, 22.0).minutesPerSession)
        assertEquals(30, SetupWizardDraft().withSessionMinutes(30).minutesPerSession)
        assertEquals(180, SetupWizardDraft().withSessionMinutes(500).minutesPerSession)
        assertEquals(75, SetupWizardDraft().withSessionMinutes(75).minutesPerSession)
        assertNull(SetupWizardDraft().withSessionMinutes(75).withSessionMinutes(null).minutesPerSession)
        // Es un dial: no deja texto crudo y el texto libre no escribe nada.
        val dial = SetupWizardDraft().withSessionMinutes(60)
        assertNull(dial.inputTexts[SetupStepId.SESSION_TIME.name])
        assertEquals(60, dial.withStepText(SetupStepId.SESSION_TIME, "90").minutesPerSession)
        assertTrue(SetupWizardValidation.validateStep(dial, SetupStepId.SESSION_TIME).none { it.isBlocking })
        assertTrue(SetupWizardValidation.validateStep(SetupWizardDraft(), SetupStepId.SESSION_TIME).single().isBlocking)
        // Un valor de un borrador antiguo fuera del rango nuevo es inválido (más de 180 o menos de 30); los de 30..100 siguen valiendo.
        assertTrue(SetupWizardValidation.validateStep(SetupWizardDraft(minutesPerSession = 200), SetupStepId.SESSION_TIME).single().isBlocking)
        assertTrue(SetupWizardValidation.validateStep(SetupWizardDraft(minutesPerSession = 25), SetupStepId.SESSION_TIME).single().isBlocking)
        assertTrue(SetupWizardValidation.validateStep(SetupWizardDraft(minutesPerSession = 32), SetupStepId.SESSION_TIME).none { it.isBlocking })
    }

    // ── Capacidades, músculos y marcas ─────────────────────────────────────────

    @Test
    fun capabilitiesStoreOneLevelPerSkillAndEveryOfferedSkillNeedsOne() {
        val atPark = SetupWizardDraft().withPlaces(setOf(park))
        val skills = atPark.capabilitySkills()
        assertTrue(CapabilitySkill.PULL_UP in skills && CapabilitySkill.DIP in skills)
        var draft = atPark
        assertTrue(SetupWizardValidation.validateStep(draft, SetupStepId.CAPABILITIES).single().isBlocking)
        for (skill in skills) draft = draft.withCapability(skill, CapabilityLevel.SOME)
        assertTrue(SetupWizardValidation.validateStep(draft, SetupStepId.CAPABILITIES).none { it.isBlocking })
        assertEquals(CapabilityLevel.MANY, draft.withCapability(CapabilitySkill.PULL_UP, CapabilityLevel.MANY).capabilities[CapabilitySkill.PULL_UP])
        assertFalse(CapabilitySkill.PULL_UP in draft.withCapability(CapabilitySkill.PULL_UP, null).capabilities)
        // Cambiar el material pide el nivel de lo que se ofrece de nuevo.
        val withoutBar = draft.withMaterial(setOf(EquipmentSymbolId.BENCH))
        assertFalse(CapabilitySkill.PULL_UP in withoutBar.capabilitySkills())
        assertTrue(SetupWizardValidation.validateStep(withoutBar, SetupStepId.CAPABILITIES).none { it.isBlocking })
    }

    @Test
    fun musclesWriteTheCanonicalSetAndAOnePointBagUpToFive() {
        val draft = SetupWizardDraft().withMuscleToggled(MuscleSymbol.CHEST).withMuscleToggled(MuscleSymbol.FOREARMS)
        assertEquals(setOf("Pectorales", "Antebrazo"), draft.priorityMuscles)
        assertEquals(mapOf("Pectorales" to 1, "Antebrazo" to 1), draft.trainingOptions.orderPriorities)
        assertEquals(setOf("Pectorales", "Antebrazo"), draft.selectedValues(SetupStepId.PRIORITIES))
        // Quitar uno y el tope de cinco.
        assertEquals(mapOf("Pectorales" to 1), draft.withMuscleToggled(MuscleSymbol.FOREARMS).trainingOptions.orderPriorities)
        val five = listOf(
            MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS, MuscleSymbol.BICEPS, MuscleSymbol.TRICEPS,
        ).fold(SetupWizardDraft()) { acc, symbol -> acc.withMuscleToggled(symbol) }
        assertEquals(5, five.trainingOptions.orderPriorities.size)
        assertSame(five, five.withMuscleToggled(MuscleSymbol.ABS).let { if (it == five) five else it })
        assertEquals(5, five.withMuscleToggled(MuscleSymbol.ABS).trainingOptions.orderPriorities.size)
        assertFalse(MuscleSymbol.ABS in MuscleSymbols.symbolsOf(five.withMuscleToggled(MuscleSymbol.ABS).trainingOptions.orderPriorities))
        // Omitir deja la bolsa vacía y es válido.
        val skipped = five.withMusclesCleared()
        assertTrue(skipped.trainingOptions.orderPriorities.isEmpty())
        assertTrue(skipped.priorityMuscles.isEmpty())
        assertTrue(SetupWizardValidation.validateStep(skipped, SetupStepId.PRIORITIES).none { it.isBlocking })
        assertTrue(SetupWizardValidation.validateStep(five, SetupStepId.PRIORITIES).none { it.isBlocking })
        // La API genérica con los músculos canónicos del motor.
        val viaGeneric = SetupWizardDraft().withStepChoices(SetupStepId.PRIORITIES, setOf("Glúteos", "Isquiosurales", "Erectores Espinales"))
        assertEquals(setOf("Glúteos", "Isquiosurales"), viaGeneric.priorityMuscles)
    }

    @Test
    fun marksKeepTheBigThreeInThePowerliftingProfileAndEverythingInLiftMarks() {
        val draft = SetupWizardDraft().withLiftMark(LiftMark.SQUAT, 140.0).withLiftMark(LiftMark.OVERHEAD_PRESS, 60.0)
        assertTrue(draft.knowsTrainingMarks)
        assertEquals(140.0, draft.powerliftingProfile?.squat1RM)
        assertNull(draft.powerliftingProfile?.bench1RM)
        assertEquals(mapOf(LiftMark.SQUAT to 140.0, LiftMark.OVERHEAD_PRESS to 60.0), draft.liftMarks)
        // El press militar solo viaja en `liftMarks`: sin ninguna de las tres no hay perfil de powerlifting.
        val onlyPress = SetupWizardDraft().withLiftMark(LiftMark.OVERHEAD_PRESS, 60.0)
        assertTrue(onlyPress.knowsTrainingMarks)
        assertNull(onlyPress.powerliftingProfile)
        // «No la sé» borra la marca; sin marcas el paso sigue siendo válido.
        val cleared = draft.withLiftMark(LiftMark.SQUAT, null).withLiftMark(LiftMark.OVERHEAD_PRESS, null)
        assertFalse(cleared.knowsTrainingMarks)
        assertNull(cleared.powerliftingProfile)
        assertTrue(SetupWizardValidation.validateStep(cleared, SetupStepId.TRAINING_MAX).none { it.isBlocking })
        // Un valor fuera de rango no cambia nada.
        assertSame(draft, draft.withLiftMark(LiftMark.BENCH, 5000.0))
        assertSame(draft, draft.withLiftMark(LiftMark.BENCH, Double.NaN))
        assertEquals(setOf("SQUAT", "OVERHEAD_PRESS"), draft.selectedValues(SetupStepId.TRAINING_MAX))
    }

    @Test
    fun theMarksUnitIsOnlyForDisplay() {
        val draft = SetupWizardDraft().withLiftMark(LiftMark.DEADLIFT, 180.0).withMarksUnit("lb")
        assertEquals("lb", draft.marksUnit)
        assertEquals(180.0, draft.liftMarks[LiftMark.DEADLIFT])
        // Una unidad que no existe no cambia la que había.
        assertEquals("lb", draft.withMarksUnit("stone").marksUnit)
        assertEquals("kg", draft.withMarksUnit("kg").marksUnit)
    }

    // ── Derivados al confirmar ─────────────────────────────────────────────────

    @Test
    fun aNoviceGetsTheTechniqueDerivedAtConfirmationAndNotInventedByThem() {
        val novice = SetupWizardDraft().withStepChoice(SetupStepId.EXPERIENCE, "new")
        assertNull("elegir la experiencia no responde la técnica", novice.volumeAnswers.technique)
        val confirmed = novice.confirmCurrentStep(SetupStepId.EXPERIENCE)
        assertEquals(1, confirmed.volumeAnswers.technique)
        assertEquals(SetupAnswerProvenance.DERIVED, confirmed.stepProgress.answers[SetupStepId.VOLUME_TECHNIQUE])
        assertFalse(SetupStepId.VOLUME_TECHNIQUE in confirmed.declaredSteps)
        assertTrue(SetupWizardValidation.validateStep(confirmed, SetupStepId.VOLUME_TECHNIQUE).none { it.isBlocking })
        // La procedencia de las otras tres respuestas no se toca.
        assertEquals(com.example.kpkn.data.models.CalibrationResponseState.UNKNOWN, confirmed.volumeAnswers.responseState)
    }

    @Test
    fun leavingTheNoviceExperienceAsksTheTechniqueAgain() {
        val novice = SetupWizardDraft().withStepChoice(SetupStepId.EXPERIENCE, "new").confirmCurrentStep(SetupStepId.EXPERIENCE)
        val intermediate = novice.withStepChoice(SetupStepId.EXPERIENCE, "intermediate").confirmCurrentStep(SetupStepId.EXPERIENCE)
        assertNull(intermediate.volumeAnswers.technique)
        assertFalse(SetupStepId.VOLUME_TECHNIQUE in intermediate.stepProgress.answers)
        assertTrue(SetupWizardValidation.validateStep(intermediate, SetupStepId.VOLUME_TECHNIQUE).single().isBlocking)
    }

    @Test
    fun aTechniqueTheUserDeclaredIsNeverDroppedWhenTheExperienceChanges() {
        val declared = SetupWizardDraft()
            .withStepChoice(SetupStepId.EXPERIENCE, "intermediate")
            .withStepChoice(SetupStepId.VOLUME_TECHNIQUE, "3")
            .copy(declaredSteps = setOf(SetupStepId.VOLUME_TECHNIQUE))
            .confirmCurrentStep(SetupStepId.VOLUME_TECHNIQUE)
        val after = declared.withStepChoice(SetupStepId.EXPERIENCE, "advanced").confirmCurrentStep(SetupStepId.EXPERIENCE)
        assertEquals(3, after.volumeAnswers.technique)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, after.stepProgress.answers[SetupStepId.VOLUME_TECHNIQUE])
    }

    @Test
    fun theReducersNeverMoveTheCursorOrRecordAnswers() {
        val start = SetupWizardDraft()
        val written = start.withPlaces(setOf(gym, home))
            .withMaterialToggled(EquipmentSymbolId.RINGS)
            .withGoalProfile(TrainingGoalProfile.STRENGTH_MUSCLE)
            .withFreshestDay(2)
            .withWeekdays(setOf(2, 4))
            .withSessionMinutes(60)
            .withCapability(CapabilitySkill.PUSH_UP, CapabilityLevel.SOME)
            .withMuscleToggled(MuscleSymbol.BACK)
            .withLiftMark(LiftMark.BENCH, 90.0)
        assertEquals(start.stepProgress.currentStepId, written.stepProgress.currentStepId)
        assertEquals(start.stepProgress.answers, written.stepProgress.answers)
        assertEquals(start.stepProgress.revision, written.stepProgress.revision)
    }
}

/** El valor estable del paso GOAL de un perfil (el que viaja por `setStepChoice`). */
private object EntrenoStepValuesAccess {
    fun value(profile: TrainingGoalProfile): String = com.example.kpkn.domain.onboarding.EntrenoStepValues.goalValue(profile)
}
