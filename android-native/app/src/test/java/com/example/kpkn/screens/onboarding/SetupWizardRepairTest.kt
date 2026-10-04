package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.onboarding.PlanCandidateRequest
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.domain.onboarding.PlanMaterializationException
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.PlanRepair
import com.example.kpkn.domain.onboarding.PlanRepairAdvisor
import com.example.kpkn.domain.onboarding.RejectionAction
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

/**
 * Paquete A · A.C3 y A.C4 cableado, Paquete C · C.P11 y C.P6 (E-18) del plan de curaduría de programas.
 *
 * El [SetupWizardViewModel] es el REAL (catálogo de ejercicios, planificador, evaluador, caché y asesor de
 * reparaciones de producción); solo se sustituye, con el puerto [SetupWizardMaterializer], la decisión de cada
 * plan —listo o rechazado con un motivo cerrado— para que cada escenario no dependa de cuánto material o tiempo
 * pide el contenido de cada receta. La versión con el motor real de las mismas reparaciones (gimnasio sin confirmar,
 * hogar, TIME_BUDGET, selección caída y reintento) vive en `SetupExecutableAvailabilityMatrixTest` (grupo T020).
 *
 *  1. `applyRepairs` escribe con la API de pasos y el asesor PRUEBA lo mismo que luego se aplica.
 *  2. Plan propio rechazado con reparación: aviso encima de la lista (hay otros viables) y aviso de «ninguno
 *     viable», con las etiquetas de D5 y el rechazo del plan propio antes que el de menos minutos.
 *  3. Ningún texto que se pinta lleva ids, códigos ni el texto crudo del motor.
 *  4. E-18: el plan de la biblioteca entra como intención con el objetivo prefijado y sin confirmar ningún paso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardRepairTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var app: Application
    private var vmCounter = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Catálogo REAL aprobado, calentado fuera del presupuesto de cada prueba.
        val warm = runCatching { runBlocking { CatalogV2ProcessCache.getOrLoad(app) } }
        assertTrue(
            "El catálogo real exercise_catalog_v2.json no carga en Robolectric: ${warm.exceptionOrNull()?.message}",
            warm.isSuccess,
        )
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1 · Etiquetas de los botones de reparación (D5) — función pura
    // ═════════════════════════════════════════════════════════════════════════

    private fun confirm(vararg keys: String) =
        PlanRepair.ConfirmApparatus(keys.toList(), SetupApparatusPanel.categoriesFor(keys.toList()))

    @Test
    fun theRepairButtonsCarryTheLabelsTheOwnerDecided() {
        // D5: «Sí, tengo rack y banco», «Cambiar a Músculo», «Cambiar a Fuerza y músculo».
        assertEquals("Sí, tengo rack y banco", repairLabelOf(listOf(confirm("squat_rack", "bench_flat"))))
        assertEquals("Sí, tengo rack", repairLabelOf(listOf(confirm("squat_rack"))))
        assertEquals("Sí, tengo banco regulable", repairLabelOf(listOf(confirm("bench_adjustable"))))
        assertEquals(
            "Sí, tengo rack, banco y barra de dominadas",
            repairLabelOf(listOf(confirm("squat_rack", "bench_flat", "pullup_bar"))),
        )
        // Una llave del panel sin nombre corto usa la etiqueta del panel en minúscula; una desconocida nunca sale cruda.
        assertEquals("Sí, tengo prensa de piernas", repairLabelOf(listOf(confirm("leg_press"))))
        assertEquals("Sí, tengo material", repairLabelOf(listOf(confirm("llave_que_no_existe"))))

        assertEquals("Cambiar a Músculo", repairLabelOf(listOf(PlanRepair.SwitchGoal(PlanGoalProfile.MUSCLE))))
        assertEquals(
            "Cambiar a Fuerza y músculo",
            repairLabelOf(listOf(PlanRepair.SwitchGoal(PlanGoalProfile.STRENGTH_MUSCLE))),
        )
        assertEquals("Ajustar a 28 min", repairLabelOf(listOf(PlanRepair.SetMinutes(28))))
        assertEquals("Cardio de 20 min", repairLabelOf(listOf(PlanRepair.SetCardioMinutes(20))))
        assertEquals("Quitar el reparto", repairLabelOf(listOf(PlanRepair.ClearSplit)))
        assertNull(repairLabelOf(emptyList()))
    }

    @Test
    fun aChainedRepairNamesTheExtraMinutesInItsLabel() {
        assertEquals(
            "Sí, tengo rack · ajustar a 75 min",
            repairLabelOf(listOf(confirm("squat_rack"), PlanRepair.SetMinutes(75))),
        )
        assertEquals(
            "Cambiar a Músculo · ajustar a 45 min",
            repairLabelOf(listOf(PlanRepair.SwitchGoal(PlanGoalProfile.MUSCLE, alsoMinutes = 45))),
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2 · Qué cambia cada reparación y que el asesor prueba justo eso — funciones puras
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun everyRepairWritesThroughTheSameReducersAsTheStepApi() {
        val base = SetupWizardDraft().withCandidateInputs(SetupGoal.STRENGTH, DUMBBELLS_AND_BENCH)
            .copy(selectedSplitId = "pl_sbd_x3", selectedCatalogId = STRENGTH_OWN)

        val minutes = base.withRepair(PlanRepair.SetMinutes(75))
        assertEquals(75, minutes.minutesPerSession)
        assertEquals("el crudo canónico del paso", "75", minutes.inputTexts[SetupStepId.SESSION_TIME.name])

        val cardio = base.withRepair(PlanRepair.SetCardioMinutes(15))
        assertEquals(15, cardio.cardioMinutes)
        assertEquals(listOf("15"), cardio.stepSelections[SetupStepId.CARDIO_TIME])

        // Cambiar de objetivo es el paso GOAL: infiere el estilo, retira el reparto del objetivo anterior, sube los
        // minutos si hace falta y conserva la intención de plan (la reparación no elige ni borra planes).
        val switched = base.withRepair(PlanRepair.SwitchGoal(PlanGoalProfile.STRENGTH_MUSCLE, alsoMinutes = 45))
        assertEquals(SetupGoal.STRENGTH_MUSCLE, switched.goal)
        assertEquals(TrainingStyle.POWERBUILDER, switched.volumeAnswers.style)
        assertEquals(listOf("strength_muscle"), switched.stepSelections[SetupStepId.GOAL])
        assertEquals(45, switched.minutesPerSession)
        assertNull(switched.selectedSplitId)
        assertEquals(STRENGTH_OWN, switched.selectedCatalogId)
        assertNull("los objetivos legacy no se ofrecen de nuevo", goalChoiceValueOf(PlanGoalProfile.LEGACY_MIXED))

        assertNull(base.withRepair(PlanRepair.ClearSplit).selectedSplitId)
    }

    @Test
    fun confirmingMaterialWritesThePresenceAndKeepsTheSavedSelectionOfTheStepConsistent() {
        val withStored = SetupWizardDraft().withCandidateInputs(SetupGoal.STRENGTH, GYM_UNCONFIRMED).copy(
            stepSelections = mapOf(SetupStepId.AVAILABILITY to listOf("BARBELL", AVAILABILITY_BODYWEIGHT)),
        )

        val confirmed = withStored.withRepair(confirm("squat_rack", "bench_flat"))
        val availability = checkNotNull(confirmed.trainingOptions.availability)
        assertEquals(ApparatusPresence.PRESENT, availability.supports["squat_rack"])
        assertEquals(ApparatusPresence.PRESENT, availability.supports["bench_flat"])
        assertTrue(EquipmentCategory.SUPPORT in availability.categories)
        // «Solo peso corporal» ya no es verdad y la categoría que la reparación añade consta en la tarjeta del paso.
        val stored = checkNotNull(confirmed.stepSelections[SetupStepId.AVAILABILITY])
        assertTrue(stored.toString(), "BARBELL" in stored && "SUPPORT" in stored)
        assertFalse(stored.toString(), AVAILABILITY_BODYWEIGHT in stored)

        // Sin selección guardada no se inventa ninguna.
        val noStored = SetupWizardDraft().withCandidateInputs(SetupGoal.STRENGTH, GYM_UNCONFIRMED)
        assertEquals(noStored.stepSelections, noStored.withRepair(confirm("squat_rack")).stepSelections)
    }

    @Test
    fun theProbeDraftOfTheAdvisorIsTheDraftThatApplyingTheRepairLeaves() {
        val base = SetupWizardDraft().withCandidateInputs(SetupGoal.STRENGTH, GYM_UNCONFIRMED)
            .copy(selectedSplitId = "pl_sbd_x3")
        val availability = checkNotNull(base.trainingOptions.availability)

        // SetMinutes
        assertEquals(
            base.withRepair(PlanRepair.SetMinutes(75)),
            repairProbeDraft(base, requestOf(base, minutes = 75), availability),
        )
        // SwitchGoal con minutos: el sondeo del asesor lleva destino, minutos y sin reparto.
        assertEquals(
            base.withRepair(PlanRepair.SwitchGoal(PlanGoalProfile.MUSCLE, alsoMinutes = 45)),
            repairProbeDraft(base, requestOf(base, goal = PlanGoalProfile.MUSCLE, minutes = 45, splitId = null), availability),
        )
        // ClearSplit
        assertEquals(
            base.withRepair(PlanRepair.ClearSplit),
            repairProbeDraft(base, requestOf(base, splitId = null), availability),
        )
        // ConfirmApparatus: lo que manda es el material que recibe el sondeo.
        val confirmation = confirm("squat_rack", "bench_flat")
        assertEquals(
            base.withRepair(confirmation),
            repairProbeDraft(base, requestOf(base), confirmation.applyTo(availability)),
        )
        // SetCardioMinutes en Atleta completo.
        val athlete = SetupWizardDraft()
            .withCandidateInputs(SetupGoal.COMPLETE_ATHLETE, GYM_UNCONFIRMED, minutes = 45, cardio = 30)
        assertEquals(
            athlete.withRepair(PlanRepair.SetCardioMinutes(15)),
            repairProbeDraft(
                athlete,
                requestOf(athlete, cardioMinutes = 15),
                checkNotNull(athlete.trainingOptions.availability),
            ),
        )
    }

    @Test
    fun theOwnPlanOfEachGoalIsTheFoundationPlanOfItsProfile() {
        assertEquals(STRENGTH_OWN, ownPlanIdOf(PlanGoalProfile.STRENGTH))
        assertEquals(MUSCLE_OWN, ownPlanIdOf(PlanGoalProfile.MUSCLE))
        assertEquals(POWERBUILDING_OWN, ownPlanIdOf(PlanGoalProfile.STRENGTH_MUSCLE))
        assertEquals(ATHLETE_OWN, ownPlanIdOf(PlanGoalProfile.COMPLETE_ATHLETE))
        assertNull(ownPlanIdOf(PlanGoalProfile.LEGACY_MIXED))
        assertNull(ownPlanIdOf(PlanGoalProfile.LEGACY_HEALTH))
        SetupGoal.entries.forEach { goal ->
            assertEquals(goal.name, planGoalProfileOf(goal).toSetupGoal()?.name ?: goal.name)
        }
        assertEquals(PlanGoalProfile.LEGACY_HEALTH, planGoalProfileOf(null))
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3 · Avisos de rechazo: elección, texto y botones — funciones puras sobre un estado armado a mano
    // ═════════════════════════════════════════════════════════════════════════

    private fun card(id: String) = SetupPlanCandidate(
        id = id, title = "Plan $id", subtitle = "Subtítulo", description = "Descripción", source = "NATIVE",
    )

    private fun rejection(
        planId: String,
        reason: PlanRejectionReason,
        requiredMinutes: Int? = null,
        missing: List<String> = emptyList(),
        key: String? = null,
        repairs: List<PlanRepair> = emptyList(),
    ) = SetupCandidateRejection(
        planId = planId,
        stage = SetupCandidateRejectionStage.MATERIAL,
        reason = "$RAW_ENGINE_TEXT nunca se pinta",
        reasonCode = reason,
        requiredMinutes = requiredMinutes,
        needsApparatusConfirmation = reason == PlanRejectionReason.APPARATUS_UNKNOWN ||
            reason == PlanRejectionReason.APPARATUS_ABSENT,
        apparatusKey = key,
        missingRequirements = missing,
        repairs = repairs,
    )

    private fun stateOf(
        goal: SetupGoal?,
        cards: List<String> = emptyList(),
        rejections: List<SetupCandidateRejection> = emptyList(),
        minutes: Int = 60,
    ) = SetupWizardState(
        draft = SetupWizardDraft(goal = goal, daysPerWeek = 3, minutesPerSession = minutes),
        planCandidates = cards.take(3).map(::card),
        availablePlanCandidates = cards.map(::card),
        candidateRejections = rejections,
        errors = mapOf("candidates" to "$RAW_ENGINE_TEXT resumen"),
    )

    private val ownUnknownWithRepair: SetupCandidateRejection
        get() = rejection(
            STRENGTH_OWN,
            PlanRejectionReason.APPARATUS_UNKNOWN,
            missing = listOf("rack", "bench"),
            key = "squat_rack",
            repairs = listOf(confirm("squat_rack", "bench_flat")),
        )

    @Test
    fun theOwnPlanNoticeSitsAboveTheListWithTheRepairAsItsMainButton() {
        val state = stateOf(SetupGoal.STRENGTH, cards = listOf("protocol:a", "protocol:b"), rejections = listOf(ownUnknownWithRepair))

        val notice = checkNotNull(ownPlanNotice(state, droppedPlanId = null))

        assertEquals(
            "No pudimos armar tu plan de fuerza con tus respuestas. " +
                "Falta confirmar si tienes rack de sentadilla y banco plano.",
            notice.text,
        )
        assertEquals("Sí, tengo rack y banco", notice.primary?.label)
        assertEquals(NoticeEffect.Apply(listOf(confirm("squat_rack", "bench_flat"))), notice.primary?.effect)
        assertEquals("la navegación equivalente pasa a secundaria", "Confirmar material", notice.secondary?.label)
        assertEquals(NoticeEffect.Act(RejectionAction.ConfirmApparatus), notice.secondary?.effect)
        assertPlainLanguage(notice)
        // La puerta de la lista lo entrega junto a las tarjetas.
        val gate = candidateListGate(state)
        assertTrue("$gate", gate is CandidateListGate.Candidates)
        assertEquals(notice, (gate as CandidateListGate.Candidates).ownPlanNotice)
    }

    @Test
    fun theOwnPlanNoticeOnlyAppearsWhenThereIsSomethingToRepair() {
        val withRepair = listOf(ownUnknownWithRepair)
        val cards = listOf("protocol:a")

        // Sin reparación: el rechazo no tiene botón de un toque, no hay aviso encima de la lista.
        val unrepairable = listOf(ownUnknownWithRepair.copy(repairs = emptyList()))
        assertNull(ownPlanNotice(stateOf(SetupGoal.STRENGTH, cards, unrepairable), droppedPlanId = null))
        // El propio ya es viable: nada que reparar aunque quede un rechazo de un barrido anterior.
        assertNull(ownPlanNotice(stateOf(SetupGoal.STRENGTH, cards + STRENGTH_OWN, withRepair), droppedPlanId = null))
        // Ya lo explica el aviso de la selección caída (con las mismas reparaciones).
        assertNull(ownPlanNotice(stateOf(SetupGoal.STRENGTH, cards, withRepair), droppedPlanId = STRENGTH_OWN))
        // Otro plan caído no lo tapa.
        assertNotNull(ownPlanNotice(stateOf(SetupGoal.STRENGTH, cards, withRepair), droppedPlanId = "protocol:a"))
        // Sin objetivo o con uno legacy no hay plan propio.
        assertNull(ownPlanNotice(stateOf(null, cards, withRepair), droppedPlanId = null))
        assertNull(ownPlanNotice(stateOf(SetupGoal.HEALTH, cards, withRepair), droppedPlanId = null))
        // El rechazo de otro plan, aunque traiga reparaciones, no es el del plan propio del objetivo.
        val other = listOf(ownUnknownWithRepair.copy(planId = "protocol:other"))
        assertNull(ownPlanNotice(stateOf(SetupGoal.STRENGTH, cards, other), droppedPlanId = null))
    }

    @Test
    fun withNoViablePlanTheNoticeSpeaksOfTheOwnPlanBeforeTheOneThatNeedsFewerMinutes() {
        // Antes se leía el primer rechazo de la lista, casi siempre el trivial de un plan que la persona ni pidió.
        val others = (1..3).map { rejection("protocol:o$it", PlanRejectionReason.TIME_BUDGET, requiredMinutes = 65) }
        val own = rejection(
            MUSCLE_OWN,
            PlanRejectionReason.TIME_BUDGET,
            requiredMinutes = 75,
            repairs = listOf(PlanRepair.SetMinutes(75)),
        )
        val state = stateOf(SetupGoal.MUSCLE, rejections = others + own)

        val notice = incompatibilityNotice(state)

        assertEquals("Con las series mínimas este plan necesita 75 min por sesión y elegiste 60.", notice.text)
        assertEquals("Ajustar a 75 min", notice.primary?.label)
        assertEquals(NoticeEffect.Apply(listOf(PlanRepair.SetMinutes(75))), notice.primary?.effect)
        assertNull("el «Ajustar» del presentador y el de la reparación son el mismo botón", notice.secondary)
        assertPlainLanguage(notice)
    }

    @Test
    fun withNoViablePlanAndNoOwnPlanTheNoticePicksTheMostActionableRejection() {
        val state = stateOf(
            SetupGoal.MUSCLE,
            rejections = listOf(
                rejection("protocol:a", PlanRejectionReason.PROFILE_MISMATCH),
                rejection("protocol:b", PlanRejectionReason.TIME_BUDGET, requiredMinutes = 90),
                rejection("protocol:c", PlanRejectionReason.TIME_BUDGET, requiredMinutes = 45),
                rejection("protocol:d", PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("rack"), key = "squat_rack"),
            ),
        )

        val notice = incompatibilityNotice(state)

        // Material por confirmar con llave antes que el tiempo; el texto nombra la llave con la etiqueta del panel.
        assertTrue(notice.text, notice.text.contains("Falta confirmar si tienes rack de sentadilla."))
        assertEquals("Confirmar material", notice.primary?.label)
        assertEquals(NoticeEffect.Act(RejectionAction.ConfirmApparatus), notice.primary?.effect)
        assertPlainLanguage(notice)
    }

    @Test
    fun withNoViablePlanThereAreNoAlternativesToSeeSoTheButtonFallsBackToRetry() {
        // El presentador solo ofrece «Ver alternativas» para estos motivos; sin lista no hay nada que ver.
        listOf(
            PlanRejectionReason.LEVEL_UNSUITABLE,
            PlanRejectionReason.RECIPE_UNAVAILABLE,
            PlanRejectionReason.NO_VALID_SUBSTITUTION,
        ).forEach { reason ->
            val notice = incompatibilityNotice(stateOf(SetupGoal.MUSCLE, rejections = listOf(rejection("protocol:a", reason))))
            assertEquals("$reason", "Reintentar", notice.primary?.label)
            assertEquals("$reason", NoticeEffect.Act(RejectionAction.Retry), notice.primary?.effect)
            assertNull("$reason", notice.secondary)
        }
        // Profile: «Cambiar objetivo» sobrevive y «Ver alternativas» no.
        val profile = incompatibilityNotice(
            stateOf(SetupGoal.MUSCLE, rejections = listOf(rejection("protocol:a", PlanRejectionReason.PROFILE_MISMATCH))),
        )
        assertEquals("Cambiar objetivo", profile.primary?.label)
        assertNull(profile.secondary)
        // Sin ningún rechazo por plan queda el resumen de la búsqueda y «Reintentar».
        val empty = incompatibilityNotice(stateOf(SetupGoal.MUSCLE))
        assertEquals("$RAW_ENGINE_TEXT resumen", empty.text)
        assertEquals("Reintentar", empty.primary?.label)
    }

    @Test
    fun fuerzaYMusculoWithoutExternalResistanceExplainsTheRequirementInsteadOfTheGenericDisciplineSentence() {
        val own = rejection(
            POWERBUILDING_OWN,
            PlanRejectionReason.PROFILE_MISMATCH,
            repairs = listOf(PlanRepair.SwitchGoal(PlanGoalProfile.MUSCLE)),
        )
        val state = stateOf(SetupGoal.STRENGTH_MUSCLE, rejections = listOf(own))

        val notice = incompatibilityNotice(state)

        assertEquals(OWN_POWERBUILDING_RESISTANCE_TEXT, notice.text)
        assertEquals("Cambiar a Músculo", notice.primary?.label)
        assertEquals("Cambiar objetivo", notice.secondary?.label)
        assertFalse(notice.text, notice.text.contains("Este plan es de"))
        assertPlainLanguage(notice)
        // El mismo motivo en un plan que no es el propio conserva la frase de disciplina del presentador.
        val other = rejection("protocol:phul-verified", PlanRejectionReason.PROFILE_MISMATCH)
        val generic = incompatibilityNotice(stateOf(SetupGoal.MUSCLE, rejections = listOf(other)))
        assertNotEquals(OWN_POWERBUILDING_RESISTANCE_TEXT, generic.text)
    }

    @Test
    fun aCardioRepairNamesItsMinutesInTheButtonAndExplainsItInTheText() {
        val own = rejection(
            ATHLETE_OWN,
            PlanRejectionReason.TIME_BUDGET,
            requiredMinutes = null,
            repairs = listOf(PlanRepair.SetCardioMinutes(15)),
        )

        val notice = incompatibilityNotice(stateOf(SetupGoal.COMPLETE_ATHLETE, rejections = listOf(own), minutes = 20))

        assertEquals(
            "Con las series mínimas este plan no cabe en los 20 min que elegiste. Con 15 min de cardio sí cabe.",
            notice.text,
        )
        assertEquals("Cardio de 15 min", notice.primary?.label)
        assertNull("sin lista no hay «Ver alternativas»", notice.secondary)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4 · ViewModel real: el asesor prueba, la persona toca, el plan propio queda viable
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun strengthInAnUnconfirmedGymOffersTheOneTapConfirmationAndApplyingItMakesTheOwnPlanViable() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val script = Script()
            // El plan propio de Fuerza necesita rack y banco CONFIRMADOS; el resto de planes cabe siempre.
            script.verdict = { draft ->
                when {
                    draft.selectedCatalogId != STRENGTH_OWN -> null
                    draft.hasSupport("squat_rack") && draft.hasSupport("bench_flat") -> null
                    else -> apparatusUnknown("rack", "bench")
                }
            }
            val vm = newVm(script)
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs(SetupGoal.STRENGTH, GYM_UNCONFIRMED) }
            val before = awaitUntil(vm, "lista con el plan propio rechazado y otros planes viables") {
                isIdle(it) && it.candidateRejections.any { r -> r.planId == STRENGTH_OWN } &&
                    it.availablePlanCandidates.isNotEmpty()
            }

            // El rechazo del plan propio trae la reparación que el asesor PROBÓ.
            val own = before.candidateRejections.single { it.planId == STRENGTH_OWN }
            assertEquals(PlanRejectionReason.APPARATUS_UNKNOWN, own.reasonCode)
            assertEquals(
                listOf<PlanRepair>(PlanRepair.ConfirmApparatus(listOf("squat_rack", "bench_flat"), setOf(EquipmentCategory.SUPPORT))),
                own.repairs,
            )
            // Los demás rechazos no llevan reparaciones: solo el plan propio.
            assertTrue(before.candidateRejections.filter { it.planId != STRENGTH_OWN }.all { it.repairs.isEmpty() })

            // Aviso encima de la lista con las etiquetas de D5.
            val gate = candidateListGate(before) as CandidateListGate.Candidates
            val notice = checkNotNull(gate.ownPlanNotice)
            assertEquals(
                "No pudimos armar tu plan de fuerza con tus respuestas. " +
                    "Falta confirmar si tienes rack de sentadilla y banco plano.",
                notice.text,
            )
            assertEquals("Sí, tengo rack y banco", notice.primary?.label)
            assertEquals("Confirmar material", notice.secondary?.label)
            assertPlainLanguage(notice)

            // El toque aplica la reparación y el plan propio pasa a viable (se recalcula solo).
            performNoticeEffect(checkNotNull(notice.primary).effect, vm)
            val after = awaitUntil(vm, "plan propio viable tras confirmar el material") {
                isIdle(it) && it.availablePlanCandidates.any { c -> c.id == STRENGTH_OWN }
            }
            val availability = checkNotNull(after.draft.trainingOptions.availability)
            assertEquals(ApparatusPresence.PRESENT, availability.supports["squat_rack"])
            assertEquals(ApparatusPresence.PRESENT, availability.supports["bench_flat"])
            assertTrue("la persona tocó el paso de material", SetupStepId.AVAILABILITY in after.draft.declaredSteps)
            assertTrue(after.candidateRejections.none { it.planId == STRENGTH_OWN })
            assertNull((candidateListGate(after) as CandidateListGate.Candidates).ownPlanNotice)

            // Y el plan propio se puede elegir y prepara su programa.
            vm.selectPlan(STRENGTH_OWN)
            val chosen = awaitUntil(vm, "plan propio elegido y preparado") {
                isIdle(it) && it.draft.selectedCatalogId == STRENGTH_OWN && it.programPreview != null
            }
            assertNull(chosen.previewError)
        }

    @Test
    fun strengthAtHomeWithDumbbellsOffersFuerzaYMusculoAndWithoutThemOffersMusculo() =
        runTest(dispatcher.scheduler, timeout = 8.minutes) {
            data class Case(
                val availability: EquipmentAvailability,
                val destination: PlanGoalProfile,
                val label: String,
                val destinationOwn: String,
            )
            listOf(
                Case(DUMBBELLS_AND_BENCH, PlanGoalProfile.STRENGTH_MUSCLE, "Cambiar a Fuerza y músculo", POWERBUILDING_OWN),
                Case(BANDS_ONLY, PlanGoalProfile.MUSCLE, "Cambiar a Músculo", MUSCLE_OWN),
            ).forEach { case ->
                val script = Script()
                // Fuerza necesita barra; el plan de los otros objetivos cabe.
                script.verdict = { draft ->
                    if (draft.selectedCatalogId == STRENGTH_OWN && draft.goal == SetupGoal.STRENGTH) {
                        apparatusAbsent("barbell")
                    } else {
                        null
                    }
                }
                val vm = newVm(script)
                vm.initialize(SetupWizardMode.TRAINING_ONLY)
                awaitUntil(vm, "wizard cargado") { !it.isLoading }
                vm.update { it.withCandidateInputs(SetupGoal.STRENGTH, case.availability) }
                val before = awaitUntil(vm, "plan propio de Fuerza rechazado: ${case.label}") {
                    isIdle(it) && it.candidateRejections.any { r -> r.planId == STRENGTH_OWN }
                }

                val own = before.candidateRejections.single { it.planId == STRENGTH_OWN }
                assertEquals(PlanRejectionReason.APPARATUS_ABSENT, own.reasonCode)
                assertEquals(listOf<PlanRepair>(PlanRepair.SwitchGoal(case.destination)), own.repairs)
                val notice = noticeShownFor(before)
                assertEquals(case.label, notice.primary?.label)
                assertPlainLanguage(notice)

                performNoticeEffect(checkNotNull(notice.primary).effect, vm)
                val after = awaitUntil(vm, "destino viable: ${case.label}") {
                    isIdle(it) && it.availablePlanCandidates.any { c -> c.id == case.destinationOwn }
                }
                val goal = checkNotNull(case.destination.toSetupGoal())
                assertEquals(goal, after.draft.goal)
                // Cambiar de objetivo pasa por el paso GOAL: infiere el estilo de volumen y marca el paso.
                assertEquals(goal.inferredTrainingStyle, after.draft.volumeAnswers.style)
                assertTrue(SetupStepId.GOAL in after.draft.declaredSteps)
                assertNull(after.draft.selectedSplitId)
                assertTrue(after.candidateRejections.none { it.planId == STRENGTH_OWN })
                store.clear()
            }
        }

    @Test
    fun withNoViablePlanTheTimeRepairAdjustsTheMinutesToTheOwnPlanAndNotToTheOneThatNeedsFewer() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val script = Script()
            // Todos los planes piden más de los 60 min elegidos: el propio 75 y los demás 65.
            script.verdict = { draft ->
                val required = if (draft.selectedCatalogId == MUSCLE_OWN) 75 else 65
                if ((draft.minutesPerSession ?: 0) < required) timeBudget(required) else null
            }
            val vm = newVm(script)
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs(SetupGoal.MUSCLE, GYM_UNCONFIRMED) }
            val before = awaitUntil(vm, "ningún plan viable") {
                isIdle(it) && it.candidateRejections.any { r -> r.planId == MUSCLE_OWN } &&
                    it.availablePlanCandidates.isEmpty()
            }

            assertEquals(CandidateListGate.NoneViable, candidateListGate(before))
            val own = before.candidateRejections.single { it.planId == MUSCLE_OWN }
            assertEquals(listOf<PlanRepair>(PlanRepair.SetMinutes(75)), own.repairs)
            // (e) La primaria que se cuenta es la del plan propio, no la del que pide menos minutos.
            val primary = com.example.kpkn.domain.onboarding.PlanRejectionPresenter.primary(
                before.candidateRejections.map { it.toRejectionView() },
                MUSCLE_OWN,
            )
            assertEquals(MUSCLE_OWN, primary?.planId)
            val notice = incompatibilityNotice(before)
            assertEquals("Con las series mínimas este plan necesita 75 min por sesión y elegiste 60.", notice.text)
            assertEquals("Ajustar a 75 min", notice.primary?.label)
            assertPlainLanguage(notice)

            performNoticeEffect(checkNotNull(notice.primary).effect, vm)
            val after = awaitUntil(vm, "planes viables con 75 min") {
                isIdle(it) && it.availablePlanCandidates.any { c -> c.id == MUSCLE_OWN }
            }
            assertEquals(75, after.draft.minutesPerSession)
            assertTrue(SetupStepId.SESSION_TIME in after.draft.declaredSteps)
            assertTrue(after.candidateRejections.none { it.reasonCode == PlanRejectionReason.TIME_BUDGET })
        }

    @Test
    fun theAthleteLowersTheCardioOnlyWhenMoreMinutesDoNotFitAndKeepsItWhenTheyDo() =
        runTest(dispatcher.scheduler, timeout = 8.minutes) {
            // Cardio de 30 min en una sesión de 20: el plan necesita más de lo que el asistente admite (120 > 100) y solo
            // cabe con 15 min de cardio o menos. El asesor prefiere el mayor cardio que cabe (20 no cabe, 15 sí).
            val lowerCardio = Script()
            lowerCardio.verdict = { draft ->
                if (draft.selectedCatalogId == ATHLETE_OWN && (draft.cardioMinutes ?: 0) > 15) timeBudget(120) else null
            }
            val vm = newVm(lowerCardio)
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs(SetupGoal.COMPLETE_ATHLETE, GYM_UNCONFIRMED, minutes = 20, cardio = 30) }
            val before = awaitUntil(vm, "Atleta rechazado por tiempo") {
                isIdle(it) && it.candidateRejections.any { r -> r.planId == ATHLETE_OWN }
            }
            val own = before.candidateRejections.single { it.planId == ATHLETE_OWN }
            assertEquals(listOf<PlanRepair>(PlanRepair.SetCardioMinutes(15)), own.repairs)
            val notice = incompatibilityNotice(before)
            assertEquals("Cardio de 15 min", notice.primary?.label)
            assertTrue(notice.text, notice.text.endsWith("Con 15 min de cardio sí cabe."))
            assertPlainLanguage(notice)

            performNoticeEffect(checkNotNull(notice.primary).effect, vm)
            val after = awaitUntil(vm, "Atleta viable con 15 min de cardio") {
                isIdle(it) && it.availablePlanCandidates.any { c -> c.id == ATHLETE_OWN }
            }
            assertEquals(15, after.draft.cardioMinutes)
            assertEquals(listOf("15"), after.draft.stepSelections[SetupStepId.CARDIO_TIME])
            assertEquals("los minutos de sesión no se tocan", 20, after.draft.minutesPerSession)
            store.clear()

            // Si más minutos bastan (85 ≤ 100), el cardio no se toca: solo «Ajustar a N min».
            val moreMinutes = Script()
            moreMinutes.verdict = { draft ->
                if (draft.selectedCatalogId == ATHLETE_OWN && (draft.minutesPerSession ?: 0) < 85) timeBudget(85) else null
            }
            val second = newVm(moreMinutes)
            second.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(second, "segundo wizard cargado") { !it.isLoading }
            second.update { it.withCandidateInputs(SetupGoal.COMPLETE_ATHLETE, GYM_UNCONFIRMED, minutes = 20, cardio = 30) }
            val beforeMinutes = awaitUntil(second, "Atleta rechazado por tiempo (minutos)") {
                isIdle(it) && it.candidateRejections.any { r -> r.planId == ATHLETE_OWN }
            }
            assertEquals(
                listOf<PlanRepair>(PlanRepair.SetMinutes(85)),
                beforeMinutes.candidateRejections.single { it.planId == ATHLETE_OWN }.repairs,
            )
            assertEquals("Ajustar a 85 min", incompatibilityNotice(beforeMinutes).primary?.label)
        }

    @Test
    fun clearingTheSplitAndLoweringTheCardioWriteThroughTheStepApi() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val vm = newVm(Script())
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        vm.update {
            it.withCandidateInputs(SetupGoal.COMPLETE_ATHLETE, GYM_UNCONFIRMED, minutes = 60, cardio = 30)
                .copy(selectedSplitId = "ul_x4")
        }
        awaitUntil(vm, "borrador con reparto y cardio") {
            isIdle(it) && it.draft.selectedSplitId == "ul_x4" && it.draft.cardioMinutes == 30
        }

        vm.applyRepair(PlanRepair.ClearSplit)
        val cleared = awaitUntil(vm, "reparto retirado") { isIdle(it) && it.draft.selectedSplitId == null }
        assertFalse("quitar el reparto no marca ningún paso", SetupStepId.SPLIT in cleared.draft.declaredSteps)

        vm.applyRepair(PlanRepair.SetCardioMinutes(10))
        val lowered = awaitUntil(vm, "cardio bajado") { isIdle(it) && it.draft.cardioMinutes == 10 }
        assertTrue(SetupStepId.CARDIO_TIME in lowered.draft.declaredSteps)
        assertEquals(CardioType.WALK, lowered.draft.cardioType)
    }

    @Test
    fun applyingNothingChangesNothing() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val vm = newVm(Script())
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        val revision = vm.state.value.draft.revision

        vm.applyRepairs(emptyList())
        advanceUntilIdle()

        assertEquals(revision, vm.state.value.draft.revision)
    }

    @Test
    fun everyPresenterActionMapsToTheStepApi() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val vm = newVm(Script())
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withCandidateInputs(SetupGoal.MUSCLE, GYM_UNCONFIRMED) }
        awaitUntil(vm, "candidatos calculados") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }

        listOf(
            RejectionAction.ChangeGoal to SetupStepId.GOAL,
            RejectionAction.ChangeDays to SetupStepId.DAYS,
            RejectionAction.ChangeSplit to SetupStepId.SPLIT,
            RejectionAction.ConfirmApparatus to SetupStepId.AVAILABILITY,
        ).forEach { (action, step) ->
            performRejectionAction(action, vm)
            awaitUntil(vm, "$action lleva al paso $step") { isIdle(it) && it.currentStep == step }
        }

        // «Ver alternativas» no toca el asistente: solo avisa a quien pinta el aviso.
        var closed = false
        val stepBefore = vm.state.value.currentStep
        performRejectionAction(RejectionAction.SeeAlternatives, vm) { closed = true }
        advanceUntilIdle()
        assertTrue(closed)
        assertEquals(stepBefore, vm.state.value.currentStep)

        // «Ajustar a N min» es la reparación SetMinutes.
        performRejectionAction(RejectionAction.SetMinutes(75), vm)
        awaitUntil(vm, "minutos ajustados") { isIdle(it) && it.draft.minutesPerSession == 75 }

        // «Reintentar» vuelve a calcular los candidatos sin tocar las respuestas.
        val answers = vm.state.value.draft.minutesPerSession
        performRejectionAction(RejectionAction.Retry, vm)
        val retried = awaitUntil(vm, "candidatos tras reintentar") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }
        assertEquals(answers, retried.draft.minutesPerSession)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5 · E-18: el plan de la biblioteca como intención
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun aLibraryPlanEntersAsAnIntentionWithItsGoalPrefilledAndNothingConfirmed() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val vm = newVm(Script())
            vm.initialize(SetupWizardMode.FULL, draftId = "preselect-own", preselectedPlanId = POWERBUILDING_OWN)
            val state = awaitUntil(vm, "wizard cargado") { !it.isLoading }

            assertEquals("la intención", POWERBUILDING_OWN, state.draft.selectedCatalogId)
            assertEquals("el objetivo del plan, prefijado", SetupGoal.STRENGTH_MUSCLE, state.draft.goal)
            assertEquals(listOf("strength_muscle"), state.draft.stepSelections[SetupStepId.GOAL])
            assertEquals(TrainingStyle.POWERBUILDER, state.draft.volumeAnswers.style)
            // Sin saltarse pasos: nada se confirma ni se marca como declarado.
            assertFalse(SetupStepId.GOAL in state.draft.stepProgress.answers)
            assertFalse(SetupStepId.PLAN in state.draft.stepProgress.answers)
            assertFalse(SetupStepId.GOAL in state.draft.declaredSteps)
            assertTrue("el asistente sigue en su primer paso", state.currentStep != SetupStepId.PLAN)
            assertEquals(SetupProgramRoute.CUSTOMIZABLE, state.draft.programRoute)
            assertEquals(SetupTrainingPath.PERSONALIZE, state.draft.trainingPath)
        }

    @Test
    fun everyOwnPlanPrefillsItsOwnGoalAndAPlanThatServesSeveralGoalsPrefillsNone() {
        val seeded = { id: String -> checkNotNull(SetupWizardDraft().withPreselectedPlan(id)) { "preselección de $id" } }
        assertEquals(SetupGoal.STRENGTH, seeded(STRENGTH_OWN).goal)
        assertEquals(SetupGoal.MUSCLE, seeded(MUSCLE_OWN).goal)
        assertEquals(SetupGoal.STRENGTH_MUSCLE, seeded(POWERBUILDING_OWN).goal)
        assertEquals(SetupGoal.COMPLETE_ATHLETE, seeded(ATHLETE_OWN).goal)
        // Una plantilla de culturismo sirve solo a Músculo.
        val bodybuilding = PersonalizedPlanCatalog.listedEntries().first { it.template?.trackLabel == "Culturismo" }
        assertEquals(SetupGoal.MUSCLE, seeded(bodybuilding.id).goal)
        // PHUL (Músculo y Fuerza y músculo) y 5/3/1 BBB (Fuerza y Fuerza y músculo): la persona elige.
        listOf(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, "protocol:wendler-531-bbb").forEach { id ->
            val draft = seeded(id)
            assertNull(id, draft.goal)
            assertEquals(id, draft.selectedCatalogId)
        }
        // Con un objetivo ya elegido que el plan sirve, no se toca.
        val own = checkNotNull(
            SetupWizardDraft(goal = SetupGoal.MUSCLE).withPreselectedPlan(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID),
        )
        assertEquals(SetupGoal.MUSCLE, own.goal)
    }

    @Test
    fun anIdThatDoesNotExistOrIsNotOfferedIsIgnored() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        // Existe pero está oculto (C.P2b): el planificador nunca lo ofrece, la preselección tampoco.
        val hidden = PersonalizedPlanCatalog.entries().first { !it.listed }.id
        listOf("native:no-existe", hidden, "   ", "").forEach { id ->
            val vm = newVm(Script())
            vm.initialize(SetupWizardMode.FULL, draftId = "preselect-ignored-${id.length}", preselectedPlanId = id)
            val state = awaitUntil(vm, "wizard cargado sin preselección: «$id»") { !it.isLoading }
            assertNull("«$id»", state.draft.selectedCatalogId)
            assertNull("«$id»", state.draft.goal)
        }
        assertNull(SetupWizardDraft(includeTraining = false).withPreselectedPlan(STRENGTH_OWN))
    }

    @Test
    fun aViableLibraryPlanIsAlreadyChosenWhenTheWizardReachesThePlanStep() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val vm = newVm(Script())
            vm.initialize(SetupWizardMode.TRAINING_ONLY, preselectedPlanId = MUSCLE_OWN)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            // La persona contesta lo demás (aquí de golpe); la selección sigue siendo la intención.
            vm.update { it.withCandidateInputs(SetupGoal.MUSCLE, GYM_UNCONFIRMED) }

            val ready = awaitUntil(vm, "plan de la biblioteca viable y preparado") {
                isIdle(it) && it.programPreview != null && it.availablePlanCandidates.any { c -> c.id == MUSCLE_OWN }
            }

            assertEquals(MUSCLE_OWN, ready.draft.selectedCatalogId)
            assertNull(ready.droppedSelection)
            assertEquals(setOf(MUSCLE_OWN), ready.draft.selectedValues(SetupStepId.PLAN))
            assertNull(ready.previewError)
        }

    @Test
    fun aLibraryPlanThatDoesNotFitFallsWithTheDroppedSelectionNoticeAndItsRepair() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val script = Script()
            script.verdict = { draft ->
                if (draft.selectedCatalogId == MUSCLE_OWN && (draft.minutesPerSession ?: 0) < 75) timeBudget(75) else null
            }
            val vm = newVm(script)
            vm.initialize(SetupWizardMode.TRAINING_ONLY, preselectedPlanId = MUSCLE_OWN)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs(SetupGoal.MUSCLE, GYM_UNCONFIRMED) }

            val dropped = awaitUntil(vm, "selección de la biblioteca caída") { isIdle(it) && it.droppedSelection != null }

            val fallen = checkNotNull(dropped.droppedSelection)
            assertEquals(MUSCLE_OWN, fallen.planId)
            // C.P5: el nombre de la ficha, nunca el id ni el alias heredado.
            assertEquals(checkNotNull(PersonalizedPlanCatalog.find(MUSCLE_OWN)).displayName, fallen.title)
            assertEquals(listOf<PlanRepair>(PlanRepair.SetMinutes(75)), checkNotNull(fallen.rejection).repairs)
            val notice = droppedSelectionNotice(fallen, dropped.draft)
            assertTrue(notice.text, notice.text.startsWith(DROPPED_SELECTION_LEAD))
            assertTrue(notice.text, notice.text.contains("necesita 75 min por sesión y elegiste 60"))
            assertEquals("Ajustar a 75 min", notice.primary?.label)
            assertPlainLanguage(notice)

            performNoticeEffect(checkNotNull(notice.primary).effect, vm)
            val fixed = awaitUntil(vm, "plan de la biblioteca viable con 75 min") {
                isIdle(it) && it.availablePlanCandidates.any { c -> c.id == MUSCLE_OWN }
            }
            assertNull(fixed.droppedSelection)
        }

    @Test
    fun theLibraryPlanIsNotAppliedAgainOverWhatThePersonChangedAfterTheViewModelIsRecreated() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val handle = SavedStateHandle()
            val persistence = InMemoryPersistence()
            val first = newVm(Script(), handle, persistence)
            first.initialize(SetupWizardMode.TRAINING_ONLY, preselectedPlanId = STRENGTH_OWN)
            awaitUntil(first, "primer wizard cargado") { !it.isLoading }
            assertEquals(STRENGTH_OWN, first.state.value.draft.selectedCatalogId)
            // La persona cambia de objetivo y de plan (el borrador se guarda).
            first.update { it.copy(selectedCatalogId = MUSCLE_OWN, goal = SetupGoal.MUSCLE) }
            awaitUntil(first, "cambio guardado") { isIdle(it) && it.draft.selectedCatalogId == MUSCLE_OWN }

            // El proceso muere y el ViewModel se recrea con los mismos argumentos de navegación.
            val second = newVm(Script(), handle, persistence)
            second.initialize(SetupWizardMode.TRAINING_ONLY, preselectedPlanId = STRENGTH_OWN)
            val restored = awaitUntil(second, "segundo wizard cargado") { !it.isLoading }

            assertEquals(MUSCLE_OWN, restored.draft.selectedCatalogId)
            assertEquals(SetupGoal.MUSCLE, restored.draft.goal)
        }

    // ═════════════════════════════════════════════════════════════════════════
    // Arnés
    // ═════════════════════════════════════════════════════════════════════════

    private fun newVm(
        script: Script,
        handle: SavedStateHandle = SavedStateHandle(),
        persistence: SetupWizardPersistence = InMemoryPersistence(),
    ): SetupWizardViewModel =
        SetupWizardViewModel(
            app,
            handle,
            persistence = persistence,
            environment = FixedSettingsEnvironment(Settings()),
            materializeOverride = script,
        ).also { store.put("repair-vm-${vmCounter++}", it) }

    /** Entradas completas de candidato (3 días y 60 min si no se piden otros) con el material declarado. */
    private fun SetupWizardDraft.withCandidateInputs(
        goal: SetupGoal,
        availability: EquipmentAvailability,
        minutes: Int = 60,
        days: Int = 3,
        cardio: Int? = null,
    ): SetupWizardDraft = copy(
        includeTraining = true,
        programRoute = SetupProgramRoute.CUSTOMIZABLE,
        trainingPath = SetupTrainingPath.PERSONALIZE,
        goal = goal,
        experience = SetupExperience.INTERMEDIATE,
        focus = SetupFocus.FULL_BODY,
        daysPerWeek = days,
        selectedWeekdays = listOf(1, 3, 5, 2, 4, 6).take(days).toSet(),
        minutesPerSession = minutes,
        cardioType = if (cardio != null) CardioType.WALK else cardioType,
        cardioMinutes = cardio ?: cardioMinutes,
        trainingEnvironment = "gym",
        trainingOptions = trainingOptions.copy(availability = availability),
    )

    private fun SetupWizardDraft.hasSupport(key: String): Boolean =
        trainingOptions.availability?.supports?.get(key) == ApparatusPresence.PRESENT

    /** El pedido normalizado que el asesor recibiría del barrido para [draft], con lo que se pida cambiar. */
    private fun requestOf(
        draft: SetupWizardDraft,
        goal: PlanGoalProfile = planGoalProfileOf(draft.goal),
        minutes: Int = requireNotNull(draft.minutesPerSession),
        cardioMinutes: Int? = draft.cardioMinutes,
        splitId: String? = draft.selectedSplitId,
    ) = PlanCandidateRequest(
        inputKey = "base",
        goalProfile = goal,
        level = CatalogLevel.INTERMEDIATE,
        focus = TrainingFocus.FULL_BODY,
        reference = PlanRepairAdvisor.referenceOf(goal),
        daysPerWeek = requireNotNull(draft.daysPerWeek),
        weekdays = draft.selectedWeekdays,
        minutesPerSession = minutes,
        effectiveEquipment = emptySet(),
        cardioMinutes = if (goal == PlanGoalProfile.COMPLETE_ATHLETE) cardioMinutes else null,
        requiresCardio = goal == PlanGoalProfile.COMPLETE_ATHLETE,
        selectedSplitId = splitId,
        planCatalogRevision = "plan-rev",
        exerciseCatalogRevision = "exercise-rev",
    )

    /** El aviso que la pantalla del paso PLAN pinta para el plan propio, tal como lo decide la puerta de la lista. */
    private fun noticeShownFor(state: SetupWizardState): RejectionNotice = when (val gate = candidateListGate(state)) {
        is CandidateListGate.Candidates -> checkNotNull(gate.ownPlanNotice) { "la lista no explica el plan propio" }
        CandidateListGate.NoneViable -> incompatibilityNotice(state)
        else -> throw AssertionError("la puerta de la lista no explica ningún rechazo: $gate")
    }

    /**
     * Ningún texto que se pinta lleva ids con prefijo, guiones bajos de códigos (`APPARATUS_UNKNOWN`, tokens de
     * material), códigos cerrados ni el texto crudo del motor.
     */
    private fun assertPlainLanguage(notice: RejectionNotice) {
        val texts = listOf(notice.text, notice.primary?.label.orEmpty(), notice.secondary?.label.orEmpty())
        texts.forEach { text ->
            assertFalse("«$text» lleva un id con prefijo", ID_WITH_PREFIX.containsMatchIn(text))
            assertFalse("«$text» lleva un guion bajo", text.contains('_'))
            assertFalse("«$text» lleva un código cerrado", CLOSED_CODE.containsMatchIn(text))
            assertFalse("«$text» pinta el texto crudo del motor", text.contains(RAW_ENGINE_TEXT))
        }
        assertTrue("el aviso trae texto", notice.text.isNotBlank())
        assertTrue("el aviso trae al menos un botón", notice.buttons.isNotEmpty())
        assertTrue("como mucho dos botones", notice.buttons.size <= 2)
    }

    private fun isIdle(state: SetupWizardState): Boolean =
        !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
            state.machineState != WizChatMachineState.PersistingAnswer &&
            state.machineState != WizChatMachineState.Committing &&
            state.machineState != WizChatMachineState.PreparingPreview &&
            !state.isPreviewLoading && !state.isCandidateLoading && !state.ringsPreviewLoading

    private fun TestScope.awaitUntil(
        vm: SetupWizardViewModel,
        what: String,
        timeoutMs: Long = AWAIT_BUDGET_MS,
        condition: (SetupWizardState) -> Boolean,
    ): SetupWizardState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            advanceUntilIdle()
            val state = vm.state.value
            if (condition(state)) return state
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("timeout esperando: $what | ${stateDump(state)}")
            }
            Thread.sleep(5)
        }
    }

    private fun stateDump(state: SetupWizardState): String =
        "machine=${state.machineState} step=${state.currentStep} errores=${state.errors} " +
            "previewError=${state.previewError} cargando=${state.isPreviewLoading}/${state.isCandidateLoading} " +
            "seleccion=${state.draft.selectedCatalogId} caida=${state.droppedSelection?.planId} " +
            "objetivo=${state.draft.goal} minutos=${state.draft.minutesPerSession} " +
            "candidatos=${state.availablePlanCandidates.map { it.id }} " +
            "rechazos=${state.candidateRejections.map { "${it.planId}:${it.reasonCode}:${it.repairs}" }}"

    /**
     * Puerto de materialización guionado: por defecto TODO plan queda listo; [verdict] decide, borrador a borrador,
     * qué plan se rechaza y con qué fallo tipado. El plan que se evalúa viene en `draft.selectedCatalogId`.
     */
    private class Script : SetupWizardMaterializer {
        private val log = CopyOnWriteArrayList<SetupWizardDraft>()

        @Volatile
        var verdict: (SetupWizardDraft) -> Throwable? = { null }

        override suspend fun materialize(draft: SetupWizardDraft): SetupPreview {
            log += draft
            verdict(draft)?.let { throw it }
            return SetupPreview(cannedProgram(draft.commitId), null)
        }
    }

    private class InMemoryPersistence : SetupWizardPersistence {
        private val drafts = mutableMapOf<String, SetupDraft>()
        override suspend fun load(draftId: String): SetupDraft? = drafts[draftId]
        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            SetupDraft(draftId, payloadJson, revision, catalogRevision, System.currentTimeMillis())
                .also { drafts[it.draftId] = it }

        override suspend fun discard(draftId: String) {
            drafts.remove(draftId)
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }

    private companion object {
        const val AWAIT_BUDGET_MS = 90_000L

        /** Texto que los fallos guionados llevan como causa cruda: ningún aviso debe pintarlo. */
        const val RAW_ENGINE_TEXT = "RAW-ENGINE-TEXT"

        val STRENGTH_OWN: String = NativeProfileKind.STRENGTH.entryId
        val MUSCLE_OWN: String = NativeProfileKind.MUSCLE.entryId
        val POWERBUILDING_OWN: String = NativeProfileKind.POWERBUILDING.entryId
        val ATHLETE_OWN: String = NativeProfileKind.COMPLETE_ATHLETE.entryId

        /** Gimnasio con todas las categorías y ningún soporte ni aparato confirmado. */
        val GYM_UNCONFIRMED = EquipmentAvailability(categories = EquipmentCategory.entries.toSet())

        /** Hogar con mancuernas y bancos confirmados, sin barra. */
        val DUMBBELLS_AND_BENCH = EquipmentAvailability(
            categories = setOf(EquipmentCategory.DUMBBELLS, EquipmentCategory.SUPPORT),
            supports = mapOf(
                "bench_flat" to ApparatusPresence.PRESENT,
                "bench_adjustable" to ApparatusPresence.PRESENT,
            ),
        )

        /** Solo bandas. */
        val BANDS_ONLY = EquipmentAvailability(categories = setOf(EquipmentCategory.BAND))

        val ID_WITH_PREFIX = Regex("""\b(native|template|protocol|original|adapted):[a-z0-9]""")
        val CLOSED_CODE = Regex(
            """APPARATUS|TIME_BUDGET|PROFILE_MISMATCH|LEVEL_UNSUITABLE|UNRESOLVED|NO_VALID|INTERNAL_|CATALOG_NOT|LOAD_BASIS""",
        )

        fun timeBudget(minutes: Int) = PlanMaterializationException(
            PlanEvaluationStage.SESSION_DURATION,
            PlanRejectionReason.TIME_BUDGET,
            "$RAW_ENGINE_TEXT: la sesión más larga estima $minutes min",
            requiredMinutes = minutes,
        )

        fun apparatusUnknown(vararg tokens: String) = PlanMaterializationException(
            PlanEvaluationStage.MATERIAL,
            PlanRejectionReason.APPARATUS_UNKNOWN,
            "$RAW_ENGINE_TEXT: falta confirmar ${tokens.joinToString()}",
            missingRequirements = tokens.toList(),
        )

        fun apparatusAbsent(vararg tokens: String) = PlanMaterializationException(
            PlanEvaluationStage.MATERIAL,
            PlanRejectionReason.APPARATUS_ABSENT,
            "$RAW_ENGINE_TEXT: material declarado ausente ${tokens.joinToString()}",
            missingRequirements = tokens.toList(),
        )

        /** Programa preparado mínimo y ejecutable (el mismo patrón que `SetupWizardCandidateGateTest`). */
        fun cannedProgram(id: String): Program {
            val exercise = Exercise(
                id = "$id-ex",
                name = "Press de banca",
                sets = (1..3).map { ExerciseSet(id = "$id-set-$it", targetReps = 8, weight = 20.0) },
                restTime = 90,
            )
            val session = Session(
                id = "$id-session",
                name = "Día 1",
                exercises = listOf(exercise),
                dayOfWeek = 1,
                assignedDays = listOf(1),
            )
            val week = ProgramWeek("$id-week", "Semana", sessions = listOf(session))
            val mesocycle = Mesocycle("$id-meso", "Meso", weeks = listOf(week))
            val block = Block("$id-block", "Bloque", mesocycles = listOf(mesocycle))
            return Program(
                id = id,
                name = "Plan preparado",
                startDay = 1,
                weekDays = 1,
                macrocycles = listOf(Macrocycle("$id-macro", "Macro", blocks = listOf(block))),
                schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(1)),
            )
        }
    }
}
