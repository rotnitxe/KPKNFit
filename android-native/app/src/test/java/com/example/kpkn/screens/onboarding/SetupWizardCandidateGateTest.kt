package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.Block
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
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanMaterializationException
import com.example.kpkn.domain.onboarding.PlanRejectionPresenter
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.PlanRepair
import com.example.kpkn.domain.onboarding.RejectionAction
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.WizChatMachineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Paquete A · pasos A.D2, A.D3 y A.D4 del plan de curaduría de programas (hallazgos B-01, B-05, B-06 y B-07).
 *
 * El [SetupWizardViewModel] es el REAL (catálogo de ejercicios, planificador, evaluador y caché de producción);
 * solo se sustituye, con el puerto [SetupWizardMaterializer], la decisión de cada plan —listo o rechazado con un
 * motivo cerrado—, para controlar CUÁL es el resultado de cada candidato sin depender de cuánto material pide
 * cada receta.
 *
 *  - D2 (B-01): el error del preview de la selección ya no esconde la lista de planes; cuando llega una lista
 *    nueva sin el plan elegido, éste pasa a `droppedSelection` con su rechazo y NO se relanza su preview;
 *    «Continuar» en PLAN exige una selección viable.
 *  - D3 (B-05, B-06): «Reintentar» no reproduce rechazos transitorios cacheados, y un catálogo que no quedó listo
 *    nunca se da por cargado: publica un rechazo CATALOG visible y el siguiente intento lo vuelve a cargar.
 *  - D4 (B-07): el pase a peso corporal solo se intenta si TODOS los rechazos del pase pedido son de material, y
 *    los rechazos y conteos que se publican son siempre los del pase pedido.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardCandidateGateTest {

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
    // D2 · B-01 — la puerta de la lista (función pura)
    // ═════════════════════════════════════════════════════════════════════════

    private fun card(id: String) = SetupPlanCandidate(
        id = id, title = "Plan $id", subtitle = "Subtítulo", description = "Descripción", source = "NATIVE",
    )

    private fun stateWith(
        cards: List<SetupPlanCandidate> = emptyList(),
        previewError: String? = null,
        errors: Map<String, String> = emptyMap(),
        rejections: List<SetupCandidateRejection> = emptyList(),
        loading: Boolean = false,
        selected: String? = null,
        dropped: SetupDroppedSelection? = null,
    ) = SetupWizardState(
        draft = SetupWizardDraft(selectedCatalogId = selected, trainingPath = SetupTrainingPath.PERSONALIZE),
        previewError = previewError,
        errors = errors,
        planCandidates = cards.take(3),
        availablePlanCandidates = cards,
        candidateRejections = rejections,
        isCandidateLoading = loading,
        droppedSelection = dropped,
    )

    private fun rejected(
        planId: String?,
        reason: PlanRejectionReason?,
        stage: SetupCandidateRejectionStage = SetupCandidateRejectionStage.MATERIAL,
        requiredMinutes: Int? = null,
        apparatusKey: String? = null,
    ) = SetupCandidateRejection(
        planId = planId,
        stage = stage,
        reason = "texto crudo del motor que nunca se pinta",
        reasonCode = reason,
        requiredMinutes = requiredMinutes,
        needsApparatusConfirmation = reason == PlanRejectionReason.APPARATUS_UNKNOWN ||
            reason == PlanRejectionReason.APPARATUS_ABSENT,
        apparatusKey = apparatusKey,
    )

    @Test
    fun theErrorOfTheSelectionPreviewDoesNotHideTheList() {
        val gate = candidateListGate(
            stateWith(
                cards = listOf(card("a"), card("b"), card("c"), card("d")),
                previewError = "No se pudo preparar la vista previa",
                selected = "a",
            ),
        )

        // Antes la puerta era `previewError`: este estado mostraba solo el aviso y ninguna tarjeta.
        assertTrue("la lista se renderiza: $gate", gate is CandidateListGate.Candidates)
        gate as CandidateListGate.Candidates
        assertEquals(listOf("a", "b", "c"), gate.cards.map { it.id })
        assertEquals("No se pudo preparar la vista previa", gate.previewError)
        assertNull(gate.dropped)
    }

    @Test
    fun aSearchFailureHidesTheListAndKeepsItsOwnMessage() {
        val gate = candidateListGate(
            stateWith(
                errors = mapOf("candidates" to "No pude comprobar los planes. Prueba de nuevo."),
                rejections = listOf(rejected(null, null, SetupCandidateRejectionStage.CATALOG)),
            ),
        )

        assertEquals(CandidateListGate.SearchFailed("No pude comprobar los planes. Prueba de nuevo."), gate)
    }

    @Test
    fun noViablePlanKeepsTheIncompatibilityExplanationInsteadOfAGenericDoor() {
        // Con rechazos POR PLAN (planId) y `errors["candidates"]` publicado, la pantalla sigue siendo la
        // explicación con acciones («Confirmar material», «Editar tiempo»), no el aviso genérico.
        val gate = candidateListGate(
            stateWith(
                errors = mapOf("candidates" to "2 planes publicados; ninguno es ejecutable con tu material."),
                rejections = listOf(
                    rejected("native:a", PlanRejectionReason.APPARATUS_UNKNOWN),
                    rejected("native:b", PlanRejectionReason.TIME_BUDGET, requiredMinutes = 40),
                ),
            ),
        )

        assertEquals(CandidateListGate.NoneViable, gate)
    }

    @Test
    fun aPreviewErrorWithoutListIsNeverADoor() {
        val gate = candidateListGate(stateWith(previewError = "Falla del preview"))

        assertEquals(CandidateListGate.NoneViable, gate)
    }

    @Test
    fun theLoadingStateWinsOverAnythingElse() {
        val gate = candidateListGate(
            stateWith(
                cards = listOf(card("a")),
                previewError = "Falla del preview",
                errors = mapOf("candidates" to "Falla"),
                loading = true,
            ),
        )

        assertEquals(CandidateListGate.Loading, gate)
    }

    @Test
    fun theDroppedSelectionNoticeOnlyAppliesWhileItMatchesTheCurrentSelection() {
        val dropped = SetupDroppedSelection("native:a", "Plan a", rejected("native:a", PlanRejectionReason.APPARATUS_ABSENT))
        val cards = listOf(card("native:b"), card("native:c"))

        fun droppedOf(selected: String?): SetupDroppedSelection? =
            (candidateListGate(stateWith(cards = cards, selected = selected, dropped = dropped)) as CandidateListGate.Candidates).dropped

        assertEquals("selección limpia: el aviso se explica", dropped, droppedOf(null))
        assertEquals("selección conservada (paso ya confirmado): el aviso se explica", dropped, droppedOf("native:a"))
        assertNull("elegido otro plan: el aviso ya no corresponde", droppedOf("native:b"))
    }

    // ── El aviso de la selección caída: texto llano y acción por motivo ──────

    @Test
    fun theDroppedSelectionNoticeExplainsEachReasonInPlainLanguageWithTheRightAction() {
        // C4/C.P11: el aviso lo escribe el presentador único de rechazos (`PlanRejectionPresenter`), no una tabla
        // local; el texto crudo del motor no se pinta nunca y las acciones son las del presentador.
        val draft = SetupWizardDraft(goal = SetupGoal.MUSCLE, daysPerWeek = 3, minutesPerSession = 60)

        fun noticeOf(reason: PlanRejectionReason?, requiredMinutes: Int? = null, key: String? = null) =
            droppedSelectionNotice(
                SetupDroppedSelection(
                    "native:a",
                    "Plan a",
                    rejected("native:a", reason, requiredMinutes = requiredMinutes, apparatusKey = key),
                ),
                draft,
            )

        fun RejectionNotice.act(): RejectionAction? = (primary?.effect as? NoticeEffect.Act)?.action

        // Material por confirmar: nombra la llave con su nombre corto («rack», el mismo del botón de un toque) y lleva al panel.
        val unknown = noticeOf(PlanRejectionReason.APPARATUS_UNKNOWN, key = "squat_rack")
        assertTrue(unknown.text.startsWith(DROPPED_SELECTION_LEAD))
        assertTrue(unknown.text, unknown.text.contains("Falta confirmar si tienes rack."))
        assertFalse("nunca la llave cruda: ${unknown.text}", unknown.text.contains("squat_rack"))
        assertEquals(RejectionAction.ConfirmApparatus, unknown.act())
        assertEquals("Confirmar material", unknown.primary?.label)
        // Sin llave la frase es general; una llave que el panel no conoce tampoco se pinta.
        assertTrue(noticeOf(PlanRejectionReason.APPARATUS_UNKNOWN).text.contains("Falta confirmar si tienes todo el material de este plan."))
        val unmapped = noticeOf(PlanRejectionReason.APPARATUS_UNKNOWN, key = "llave_que_no_existe")
        assertFalse(unmapped.text, unmapped.text.contains("llave_que_no_existe"))
        assertTrue(unmapped.text, unmapped.text.contains("Falta confirmar si tienes todo el material de este plan."))

        // Sin requisito ni llave revisable no se ofrece una confirmación de material imposible.
        val absent = noticeOf(PlanRejectionReason.APPARATUS_ABSENT)
        assertTrue(absent.text, absent.text.contains("Este plan necesita material que dijiste que no tienes."))
        assertEquals(RejectionAction.SeeAlternatives, absent.act())
        assertNull(absent.secondary)
        // Una llave real permite revisar la respuesta sobre ese aparato y ver alternativas.
        val absentRack = noticeOf(PlanRejectionReason.APPARATUS_ABSENT, key = "squat_rack")
        assertTrue(absentRack.text, absentRack.text.contains("Este plan necesita rack, que dijiste que no tienes."))
        assertEquals(RejectionAction.ConfirmApparatus, absentRack.act())
        assertEquals("Ver alternativas", absentRack.secondary?.label)
        assertEquals(NoticeEffect.Act(RejectionAction.SeeAlternatives), absentRack.secondary?.effect)

        // Tiempo: el presentador dice los minutos EXACTOS y el botón los aplica («Ajustar a N min»).
        val time = noticeOf(PlanRejectionReason.TIME_BUDGET, requiredMinutes = 75)
        assertTrue(time.text, time.text.contains("Con las series mínimas este plan necesita 75 min por sesión y elegiste 60."))
        assertEquals(RejectionAction.SetMinutes(75), time.act())
        assertEquals("Ajustar a 75 min", time.primary?.label)
        // Sin minutos que el asistente admita no hay ajuste: el texto lo dice y solo quedan las alternativas.
        val noMinutes = noticeOf(PlanRejectionReason.TIME_BUDGET)
        assertTrue(noMinutes.text, noMinutes.text.contains("no cabe en los 60 min que elegiste"))
        assertEquals(RejectionAction.SeeAlternatives, noMinutes.act())

        // El resto de motivos: la acción del presentador que lo arregla (o reintentar si es un fallo del motor).
        mapOf(
            PlanRejectionReason.PROFILE_MISMATCH to RejectionAction.ChangeGoal,
            PlanRejectionReason.LEVEL_UNSUITABLE to RejectionAction.SeeAlternatives,
            PlanRejectionReason.FREQUENCY to RejectionAction.ChangeDays,
            PlanRejectionReason.SPLIT to RejectionAction.ChangeSplit,
            PlanRejectionReason.COMPOSITION to RejectionAction.Retry,
            PlanRejectionReason.NO_VALID_SUBSTITUTION to RejectionAction.SeeAlternatives,
            PlanRejectionReason.INTERNAL_MATERIALIZATION to RejectionAction.Retry,
            PlanRejectionReason.CATALOG_NOT_READY to RejectionAction.Retry,
        ).forEach { (reason, expected) ->
            val other = noticeOf(reason)
            assertEquals("«$reason»", expected, other.act())
            assertTrue("«$reason»: ${other.text}", other.text.startsWith(DROPPED_SELECTION_LEAD))
        }
        // Un rechazo heredado sin motivo cerrado se trata como un fallo del motor.
        assertEquals(RejectionAction.Retry, noticeOf(null).act())

        // Sin rechazo (el plan ni se evaluó) también hay aviso, con «Ver alternativas» y «Cambiar objetivo».
        val unevaluated = droppedSelectionNotice(SetupDroppedSelection("native:a", "Plan a", null), draft)
        assertEquals(RejectionAction.SeeAlternatives, unevaluated.act())
        assertEquals(NoticeEffect.Act(RejectionAction.ChangeGoal), unevaluated.secondary?.effect)

        // Ningún aviso pinta el texto crudo del motor, un id ni un código.
        listOf(unknown, absent, time, noMinutes, unevaluated).forEach { notice ->
            listOf(notice.text, notice.primary?.label.orEmpty(), notice.secondary?.label.orEmpty()).forEach { text ->
                assertFalse(text, text.contains("texto crudo del motor"))
                assertFalse(text, text.contains("native:a"))
                assertFalse(text, text.contains("APPARATUS_") || text.contains("TIME_BUDGET"))
            }
        }
    }

    @Test
    fun theDroppedSelectionNoticeOffersTheOwnPlanRepairWhenItsRejectionCarriesOne() {
        val draft = SetupWizardDraft(goal = SetupGoal.STRENGTH, daysPerWeek = 3, minutesPerSession = 60)
        val repair = PlanRepair.ConfirmApparatus(listOf("squat_rack", "bench_flat"), setOf(EquipmentCategory.SUPPORT))
        val own = rejected("native:strength-foundation-v2", PlanRejectionReason.APPARATUS_UNKNOWN, apparatusKey = "squat_rack")
            .copy(missingRequirements = listOf("rack", "bench"), repairs = listOf(repair))

        val notice = droppedSelectionNotice(SetupDroppedSelection(own.planId!!, "Fuerza", own), draft)

        assertTrue(notice.text, notice.text.startsWith(DROPPED_SELECTION_LEAD))
        assertTrue(notice.text, notice.text.contains("Falta confirmar si tienes rack y banco."))
        // D5: el botón principal es la reparación de un toque y la navegación equivalente pasa a secundaria.
        assertEquals("Sí, tengo rack y banco", notice.primary?.label)
        assertEquals(NoticeEffect.Apply(listOf(repair)), notice.primary?.effect)
        assertEquals("Confirmar material", notice.secondary?.label)
        assertEquals(NoticeEffect.Act(RejectionAction.ConfirmApparatus), notice.secondary?.effect)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // D2 · B-01 — «Continuar» en PLAN exige una selección viable (función pura)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun theStepGateOnlyLetsAViableSelectionOfTheCurrentListContinue() {
        val viableList = listOf(card("native:a"), card("native:b"))

        fun gate(
            step: SetupStepId = SetupStepId.PLAN,
            selected: String? = "native:a",
            cards: List<SetupPlanCandidate> = viableList,
            loading: Boolean = false,
            path: SetupTrainingPath = SetupTrainingPath.PERSONALIZE,
        ) = planSelectionGate(
            SetupWizardState(
                draft = SetupWizardDraft(selectedCatalogId = selected, trainingPath = path),
                availablePlanCandidates = cards,
                isCandidateLoading = loading,
            ),
            step,
        )

        assertTrue("selección viable: avanza", gate().isEmpty())
        assertEquals(mapOf("plan" to PLAN_SELECTION_REQUIRED_MESSAGE), gate(selected = "native:zzz"))
        assertEquals("lista vacía", mapOf("plan" to PLAN_SELECTION_REQUIRED_MESSAGE), gate(cards = emptyList()))
        assertEquals("lista calculándose", mapOf("plan" to PLAN_CANDIDATES_LOADING_MESSAGE), gate(loading = true))
        assertEquals("Elige un plan de la lista para continuar", PLAN_SELECTION_REQUIRED_MESSAGE)
        // Sin plan elegido habla la validación del paso; otros pasos y la ruta «desde cero» no usan la puerta.
        assertTrue(gate(selected = null).isEmpty())
        assertTrue(gate(step = SetupStepId.SPLIT, selected = "native:zzz").isEmpty())
        assertTrue(gate(path = SetupTrainingPath.FROM_SCRATCH, cards = emptyList()).isEmpty())
    }

    @Test
    fun deferredProgramBypassesThePlanGateAndSaysTheProgramComesLater() {
        val state = SetupWizardState(
            draft = SetupWizardDraft(
                programRoute = SetupProgramRoute.LATER,
                selectedCatalogId = "native:zzz",
                trainingPath = SetupTrainingPath.PERSONALIZE,
                includeNutrition = true,
            ),
            availablePlanCandidates = emptyList(),
            isCandidateLoading = true,
        )
        assertTrue(planSelectionGate(state, SetupStepId.PLAN).isEmpty())
        assertTrue(SetupWizardValidation.validateStep(state.draft, SetupStepId.PLAN).none { it.isBlocking })
        val rows = trainingMilestoneRows(state)
        assertTrue(rows.any { it.first == "Plan" && it.second == DEFER_PROGRAM_REVIEW_VALUE })
        assertFalse(rows.any { it.first == "Autorregulación" || it.first == "Calentamientos" })
        assertEquals(
            "El programa lo armarás más adelante. El plan nutricional se activa al confirmar.",
            activationBody(state),
        )
        assertEquals(
            "No se activa ningún programa. Podrás armar el tuyo más adelante.",
            activationBody(state.copy(draft = state.draft.copy(includeNutrition = false))),
        )
    }

    @Test
    fun whileTheListIsLoadingTheGateSaysItIsReviewingThePlansInsteadOfAskingToChooseOne() {
        // H9: con la lista calculándose no se puede pedir «elige un plan de la lista»: todavía no hay lista.
        assertEquals(
            "Estamos revisando los planes; vuelve a tocar Continuar en un momento",
            PLAN_CANDIDATES_LOADING_MESSAGE,
        )
        fun gate(loading: Boolean, cards: List<SetupPlanCandidate>, selected: String? = "native:a") = planSelectionGate(
            SetupWizardState(
                draft = SetupWizardDraft(selectedCatalogId = selected, trainingPath = SetupTrainingPath.PERSONALIZE),
                availablePlanCandidates = cards,
                isCandidateLoading = loading,
            ),
            SetupStepId.PLAN,
        )

        assertEquals(mapOf("plan" to PLAN_CANDIDATES_LOADING_MESSAGE), gate(loading = true, cards = listOf(card("native:a"))))
        assertEquals(
            "calculándose y con la lista aún vacía",
            mapOf("plan" to PLAN_CANDIDATES_LOADING_MESSAGE),
            gate(loading = true, cards = emptyList()),
        )
        // Con la lista lista, el mensaje vuelve a ser el de elegir un plan viable.
        assertEquals(mapOf("plan" to PLAN_SELECTION_REQUIRED_MESSAGE), gate(loading = false, cards = listOf(card("native:b"))))
        assertTrue(gate(loading = false, cards = listOf(card("native:a"))).isEmpty())
        // Sin plan elegido no hay nada que decir de la carga: habla la validación del paso.
        assertTrue(gate(loading = true, cards = emptyList(), selected = null).isEmpty())
    }

    // ═════════════════════════════════════════════════════════════════════════
    // H6 — el aviso flotante no repite lo que el paso ya pinta (función pura)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun theFloatingErrorsSkipWhatTheStepAlreadyPaintsAndKeepEverythingElse() {
        // El paso PLAN pinta la búsqueda (`candidates`) y el preview de la selección (`preview`).
        assertTrue(stepRendersError(SetupStepId.PLAN, "candidates"))
        assertTrue(stepRendersError(SetupStepId.PLAN, "preview"))
        // La revisión del entreno y la final pintan el preview.
        assertTrue(stepRendersError(SetupStepId.TRAINING_REVIEW, "preview"))
        assertTrue(stepRendersError(SetupStepId.REVIEW_ACTIVATE, "preview"))
        // Lo demás no lo pinta ningún paso y sigue en el aviso flotante: guardado, activación, la puerta de «Continuar»…
        listOf("save", "commit", "plan", "initialize", "program", "review", "profile", "time", "schedule").forEach { key ->
            assertFalse("PLAN no pinta «$key»", stepRendersError(SetupStepId.PLAN, key))
            assertFalse("TRAINING_REVIEW no pinta «$key»", stepRendersError(SetupStepId.TRAINING_REVIEW, key))
            assertFalse("REVIEW_ACTIVATE no pinta «$key»", stepRendersError(SetupStepId.REVIEW_ACTIVATE, key))
        }
        // El preview y la búsqueda de planes solo los pinta su paso: en cualquier otro siguen en el aviso.
        SetupStepId.entries.filter { it != SetupStepId.PLAN && it != SetupStepId.TRAINING_REVIEW && it != SetupStepId.REVIEW_ACTIVATE }
            .forEach { step ->
                assertFalse("$step no pinta «preview»", stepRendersError(step, "preview"))
                assertFalse("$step no pinta «candidates»", stepRendersError(step, "candidates"))
            }
        assertFalse("la revisión del entreno no pinta la búsqueda", stepRendersError(SetupStepId.TRAINING_REVIEW, "candidates"))
    }

    // ═════════════════════════════════════════════════════════════════════════
    // H10 — el error del preview que lee la persona (función pura)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun thePreviewFailureTextNeverIsTheRawEngineMessageForCatalogAndInternalFailures() {
        val raw = "RAW-ENGINE-TEXT machine_config:quads_prensa_piernas__bilateral falló en com.example.Clase"
        fun typed(reason: PlanRejectionReason) =
            PlanMaterializationException(PlanEvaluationStage.MATERIALIZATION, reason, raw)

        // Catálogo que no quedó listo: el texto del presentador único.
        assertEquals(PlanRejectionPresenter.CATALOG_TEXT, previewFailureText(typed(PlanRejectionReason.CATALOG_NOT_READY)))
        // Fallos internos tipados y errores sin tipar: «Algo falló al preparar este plan…».
        listOf(
            PlanRejectionReason.INTERNAL_MATERIALIZATION,
            PlanRejectionReason.UNRESOLVED_CONFIGURATION,
            PlanRejectionReason.COMPOSITION,
            PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE,
        ).forEach { reason ->
            assertEquals("$reason", PlanRejectionPresenter.INTERNAL_TEXT, previewFailureText(typed(reason)))
        }
        listOf(
            IllegalStateException(raw),
            RuntimeException(raw),
            NullPointerException(),
            IllegalArgumentException(null as String?),
        ).forEach { error ->
            assertEquals(error.javaClass.simpleName, PlanRejectionPresenter.INTERNAL_TEXT, previewFailureText(error))
        }
        listOf(
            previewFailureText(typed(PlanRejectionReason.CATALOG_NOT_READY)),
            previewFailureText(typed(PlanRejectionReason.INTERNAL_MATERIALIZATION)),
            previewFailureText(IllegalStateException(raw)),
        ).forEach { shown ->
            assertFalse("«$shown» lleva el texto crudo", shown.contains("RAW-ENGINE-TEXT") || shown.contains("machine_config"))
        }
    }

    @Test
    fun thePreviewFailureTextKeepsTheStructuredCauseWithoutShowingRawTokensOrDiagnostics() {
        val message = "RAW-ENGINE-TEXT machine_config:quads_prensa_piernas__bilateral\nDiagnóstico W6 slot=private-id"
        listOf(
            PlanRejectionReason.APPARATUS_UNKNOWN,
            PlanRejectionReason.APPARATUS_ABSENT,
            PlanRejectionReason.TIME_BUDGET,
            PlanRejectionReason.PROFILE_MISMATCH,
            PlanRejectionReason.FREQUENCY,
            PlanRejectionReason.SPLIT,
            PlanRejectionReason.LEVEL_UNSUITABLE,
            PlanRejectionReason.NO_VALID_SUBSTITUTION,
            PlanRejectionReason.RECIPE_UNAVAILABLE,
        ).forEach { reason ->
            val error = PlanMaterializationException(PlanEvaluationStage.MATERIAL, reason, message,
                requiredMinutes = 75, missingRequirements = listOf("rack"))
            val shown = previewFailureText(error)
            assertTrue("$reason tiene causa comprensible", shown.isNotBlank())
            listOf("RAW-ENGINE-TEXT", "machine_config", "quads_prensa", "Diagnóstico", "private-id", "W6").forEach { raw ->
                assertFalse("$reason muestra $raw en «$shown»", raw in shown)
            }
        }
        val draft = SetupWizardDraft(goal = SetupGoal.STRENGTH, daysPerWeek = 3, minutesPerSession = 60)
        assertEquals("Falta confirmar si tienes rack.", previewFailureText(
            PlanMaterializationException(PlanEvaluationStage.MATERIAL, PlanRejectionReason.APPARATUS_UNKNOWN,
                message, missingRequirements = listOf("rack")), draft))
        assertEquals("Este plan necesita mancuernas, que dijiste que no tienes.", previewFailureText(
            PlanMaterializationException(PlanEvaluationStage.MATERIAL, PlanRejectionReason.APPARATUS_ABSENT,
                message, missingRequirements = listOf("dumbbells")), draft))
        assertEquals("Con las series mínimas este plan necesita 75 min por sesión y elegiste 60.", previewFailureText(
            PlanMaterializationException(PlanEvaluationStage.SESSION_DURATION, PlanRejectionReason.TIME_BUDGET,
                message, requiredMinutes = 75), draft))
    }

    // ═════════════════════════════════════════════════════════════════════════
    // H2 (b) — el motivo que explica por qué el planificador excluyó un plan (función pura)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun theSynthesizedRejectionExplainsTheFrequencyFirstAndThenTheGoal() {
        val fourDayMethod = checkNotNull(
            com.example.kpkn.data.programs.PersonalizedPlanCatalog.find(
                com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
            ),
        )
        val strengthOwn = checkNotNull(
            com.example.kpkn.data.programs.PersonalizedPlanCatalog.find(
                com.example.kpkn.data.protocols.definitions.NativeProfileKind.STRENGTH.entryId,
            ),
        )

        // Días: el plan usa 4 y la persona eligió 3.
        val frequency = checkNotNull(
            synthesizedRejectionFor(fourDayMethod, SetupWizardDraft(goal = SetupGoal.MUSCLE, daysPerWeek = 3)),
        )
        assertEquals(PlanRejectionReason.FREQUENCY, frequency.reasonCode)
        assertEquals(SetupCandidateRejectionStage.FREQUENCY, frequency.stage)
        assertEquals(fourDayMethod.id, frequency.planId)
        // Las dos cosas a la vez: manda la frecuencia, como en los filtros del planificador.
        assertEquals(
            PlanRejectionReason.FREQUENCY,
            synthesizedRejectionFor(strengthOwn, SetupWizardDraft(goal = SetupGoal.MUSCLE, daysPerWeek = 7))?.reasonCode,
        )
        // Objetivo: el plan propio de Fuerza no sirve a Músculo.
        val profile = checkNotNull(
            synthesizedRejectionFor(strengthOwn, SetupWizardDraft(goal = SetupGoal.MUSCLE, daysPerWeek = 3)),
        )
        assertEquals(PlanRejectionReason.PROFILE_MISMATCH, profile.reasonCode)
        assertEquals(SetupCandidateRejectionStage.PROFILE, profile.stage)
        // Ni lo uno ni lo otro: no hay motivo que sintetizar.
        assertNull(synthesizedRejectionFor(fourDayMethod, SetupWizardDraft(goal = SetupGoal.MUSCLE, daysPerWeek = 4)))
        assertNull(synthesizedRejectionFor(strengthOwn, SetupWizardDraft(goal = SetupGoal.STRENGTH, daysPerWeek = 3)))
        // Sin días elegidos y sin objetivo (o con uno legacy) tampoco.
        assertNull(synthesizedRejectionFor(strengthOwn, SetupWizardDraft(goal = null, daysPerWeek = null)))
        assertNull(synthesizedRejectionFor(strengthOwn, SetupWizardDraft(goal = SetupGoal.HEALTH, daysPerWeek = 3)))
        // El texto crudo nunca se pinta: el presentador lo escribe con las respuestas de la persona.
        val notice = droppedSelectionNotice(
            SetupDroppedSelection(fourDayMethod.id, fourDayMethod.displayName, frequency),
            SetupWizardDraft(goal = SetupGoal.MUSCLE, daysPerWeek = 3, minutesPerSession = 60),
        )
        assertEquals("$DROPPED_SELECTION_LEAD Este plan usa 4 días distintos; elegiste 3 días.", notice.text)
        assertEquals("Cambiar días", notice.primary?.label)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // D4 · B-07 — cuándo se intenta el pase a peso corporal (función pura)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun theBodyweightPassOnlyFollowsRejectionsThatAreAllAboutMaterial() {
        val unknown = rejected("a", PlanRejectionReason.APPARATUS_UNKNOWN)
        val absent = rejected("b", PlanRejectionReason.APPARATUS_ABSENT)

        assertTrue(bodyweightPassAllowed(0, listOf(unknown, absent)))
        assertTrue(bodyweightPassAllowed(0, listOf(absent)))

        // Algún viable, ningún rechazo (nada que explicar con el material) o un rechazo heredado sin código.
        assertFalse(bodyweightPassAllowed(1, listOf(absent)))
        assertFalse(bodyweightPassAllowed(0, emptyList()))
        assertFalse(bodyweightPassAllowed(0, listOf(rejected("c", null))))

        // Cualquier rechazo que no sea de material impide el pase: la persona debe ver el motivo real.
        listOf(
            PlanRejectionReason.TIME_BUDGET,
            PlanRejectionReason.PROFILE_MISMATCH,
            PlanRejectionReason.COMPOSITION,
            PlanRejectionReason.INTERNAL_MATERIALIZATION,
            PlanRejectionReason.CATALOG_NOT_READY,
            PlanRejectionReason.NO_VALID_SUBSTITUTION,
            PlanRejectionReason.UNRESOLVED_CONFIGURATION,
            PlanRejectionReason.FREQUENCY,
            PlanRejectionReason.LEVEL_UNSUITABLE,
        ).forEach { other ->
            assertFalse("$other", bodyweightPassAllowed(0, listOf(unknown, rejected("d", other), absent)))
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // D2 · B-01 — la selección que cae cuando llega una lista nueva (ViewModel real)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun aNewListWithoutTheChosenPlanDropsTheSelectionWithItsRejectionAndNeverRelaunchesItsPreview() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val script = ScriptedMaterializer()
            val vm = newVm(script)
            val chosen = startWithChosenPlan(vm, confirmPlan = false)

            // Desde aquí el plan elegido se rechaza por tiempo y cambia la huella de candidatos (el peso
            // entra en ella) sin tocar el material ni el tiempo.
            script.failure = { draft -> if (draft.selectedCatalogId == chosen) timeBudget(75) else null }
            val callsBefore = script.sweepCallsFor(chosen)
            vm.update { it.copy(weightKg = 71.5) }
            val after = awaitUntil(vm, "lista nueva sin el plan elegido y la selección limpia") {
                isIdle(it) && it.droppedSelection != null && it.draft.selectedCatalogId == null
            }

            val dropped = checkNotNull(after.droppedSelection)
            assertEquals(chosen, dropped.planId)
            assertTrue("el título visible, nunca el id", dropped.title.isNotBlank() && dropped.title != chosen)
            val why = checkNotNull(dropped.rejection) { "el rechazo del plan caído: ${stateDump(after)}" }
            assertEquals(chosen, why.planId)
            assertEquals(PlanRejectionReason.TIME_BUDGET, why.reasonCode)
            assertEquals(75, why.requiredMinutes)

            // La lista nueva sigue visible y sin el plan caído; la selección se limpió porque PLAN no se confirmó.
            assertTrue(after.availablePlanCandidates.isNotEmpty())
            assertTrue(after.availablePlanCandidates.none { it.id == chosen })
            assertNull(after.draft.selectedCatalogId)
            assertTrue(candidateListGate(after) is CandidateListGate.Candidates)

            // Sin preview relanzado: el programa del plan caído se retira, no hay error de preview y el
            // motor solo vio a ese plan UNA vez, en el barrido (no una segunda en un preview).
            assertNull(after.programPreview)
            assertNull(after.previewError)
            assertNull(after.errors["preview"])
            assertEquals(1, script.sweepCallsFor(chosen) - callsBefore)
        }

    @Test
    fun aConfirmedPlanStepKeepsTheDroppedSelectionAndMarksTheStepPendingWithoutRelaunchingItsPreview() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val script = ScriptedMaterializer()
            val vm = newVm(script)
            val chosen = startWithChosenPlan(vm, confirmPlan = true)
            assertFalse(
                "precondición: PLAN no está pendiente antes de la lista nueva",
                SetupStepId.PLAN in vm.state.value.draft.stepProgress.pendingReview,
            )

            script.failure = { draft -> if (draft.selectedCatalogId == chosen) apparatusAbsent() else null }
            val callsBefore = script.sweepCallsFor(chosen)
            vm.update { it.copy(weightKg = 71.5) }
            val after = awaitUntil(vm, "selección caída con el paso PLAN pendiente") {
                isIdle(it) && it.droppedSelection != null &&
                    SetupStepId.PLAN in it.draft.stepProgress.pendingReview
            }

            // Una respuesta confirmada no se borra: la selección se conserva y el paso queda por revisar.
            assertEquals(chosen, after.draft.selectedCatalogId)
            assertEquals(chosen, checkNotNull(after.droppedSelection).planId)
            assertEquals(
                PlanRejectionReason.APPARATUS_ABSENT,
                checkNotNull(after.droppedSelection?.rejection).reasonCode,
            )
            assertTrue(SetupStepId.PLAN in after.draft.stepProgress.answers)
            // Y su preview tampoco se relanza aunque el guardado del borrador pase otra vez por la cola.
            assertNull(after.programPreview)
            assertNull(after.previewError)
            assertNull(after.errors["preview"])
            assertEquals(1, script.sweepCallsFor(chosen) - callsBefore)

            // Otra edición del borrador (que también dispara la preparación del preview) sigue sin relanzarlo.
            vm.update { it.copy(name = "Ana") }
            awaitUntil(vm, "edición posterior asentada") { isIdle(it) && it.draft.name == "Ana" }
            assertEquals(1, script.sweepCallsFor(chosen) - callsBefore)
            assertNull(vm.state.value.previewError)

            // Cuando la causa se arregla (otra huella de candidatos y el plan vuelve a caber) la búsqueda nueva
            // retira la marca y el plan conservado se vuelve a preparar solo.
            script.failure = { null }
            vm.update { it.copy(weightKg = 72.5) }
            val recovered = awaitUntil(vm, "plan conservado otra vez viable y preparado") {
                isIdle(it) && it.droppedSelection == null && it.draft.selectedCatalogId == chosen &&
                    it.programPreview != null
            }
            assertTrue(recovered.availablePlanCandidates.any { it.id == chosen })
            assertNull(recovered.previewError)
        }

    @Test
    fun continuingFromPlanNeedsAViableSelectionAndChoosingAViableOneLetsItThrough() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val script = ScriptedMaterializer()
            val vm = newVm(script)
            val chosen = startWithChosenPlan(vm, confirmPlan = true)
            vm.update { it.copy(stepProgress = it.stepProgress.at(SetupStepId.PLAN, it.stepContext())) }
            awaitUntil(vm, "cursor en PLAN") { isIdle(it) && it.currentStep == SetupStepId.PLAN }

            script.failure = { draft -> if (draft.selectedCatalogId == chosen) timeBudget(75) else null }
            vm.update { it.copy(weightKg = 71.5) }
            val dropped = awaitUntil(vm, "selección caída") { isIdle(it) && it.droppedSelection != null }
            assertEquals(chosen, dropped.draft.selectedCatalogId)

            // El plan conservado ya no está entre los viables: Continuar no avanza y lo dice.
            val rejectedSubmit = vm.submitCurrentStep(SetupStepId.PLAN)
            assertEquals(SetupSubmitOutcome.REJECTED, rejectedSubmit.outcome)
            val afterReject = vm.state.value
            assertEquals(PLAN_SELECTION_REQUIRED_MESSAGE, afterReject.errors["plan"])
            assertEquals(SetupStepId.PLAN, afterReject.currentStep)
            assertEquals(chosen, afterReject.draft.selectedCatalogId)

            // Elegir un plan viable descarta el aviso y deja continuar.
            val viable = afterReject.availablePlanCandidates.first().id
            assertTrue(viable != chosen)
            vm.selectPlan(viable)
            val chosenNow = awaitUntil(vm, "plan viable elegido y preparado") {
                isIdle(it) && it.draft.selectedCatalogId == viable && it.programPreview != null
            }
            assertNull("elegir otro plan descarta el aviso", chosenNow.droppedSelection)

            val accepted = vm.submitCurrentStep(SetupStepId.PLAN)
            assertEquals(SetupSubmitOutcome.ACCEPTED, accepted.outcome)
            val advanced = awaitUntil(vm, "cursor tras PLAN") { it.currentStep != SetupStepId.PLAN }
            assertEquals(SetupStepId.AUTOREGULATION, advanced.currentStep)
        }

    @Test
    fun continuingFromPlanWithAnEmptyListIsRejectedEvenWithASelection() = runTest(dispatcher.scheduler, timeout = 3.minutes) {
        val vm = newVm(ScriptedMaterializer())
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        // Sin entradas completas de candidato la lista queda vacía; el plan elegido viene de un borrador anterior.
        vm.update {
            it.copy(
                selectedCatalogId = "native:muscle-foundation-v2",
                stepProgress = it.stepProgress.at(SetupStepId.PLAN, it.stepContext()),
            )
        }
        awaitUntil(vm, "cursor en PLAN con selección") {
            isIdle(it) && it.currentStep == SetupStepId.PLAN && it.draft.selectedCatalogId != null
        }
        assertTrue(vm.state.value.availablePlanCandidates.isEmpty())

        val result = vm.submitCurrentStep(SetupStepId.PLAN)

        assertEquals(SetupSubmitOutcome.REJECTED, result.outcome)
        assertEquals(PLAN_SELECTION_REQUIRED_MESSAGE, vm.state.value.errors["plan"])
        assertEquals(SetupStepId.PLAN, vm.state.value.currentStep)
    }

    @Test
    fun choosingAnotherPlanDismissesTheDroppedSelectionNotice() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val script = ScriptedMaterializer()
        val vm = newVm(script)
        val chosen = startWithChosenPlan(vm, confirmPlan = true)
        script.failure = { draft -> if (draft.selectedCatalogId == chosen) timeBudget(75) else null }
        vm.update { it.copy(weightKg = 71.5) }
        val dropped = awaitUntil(vm, "selección caída") { isIdle(it) && it.droppedSelection != null }

        val other = dropped.availablePlanCandidates.first().id
        vm.selectPlan(other)
        val after = awaitUntil(vm, "otro plan elegido") { isIdle(it) && it.draft.selectedCatalogId == other }

        assertNull(after.droppedSelection)
        assertNull((candidateListGate(after) as CandidateListGate.Candidates).dropped)
    }

    @Test
    fun theViableListKeepsThePlannerOrderSoAdaptedPlansAreNotPushedBehindLegacyOnes() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            // Todos los planes del planificador son viables: la lista publicada tiene que conservar su orden
            // editorial (rank), sin el antiguo `sortedBy { ADAPTED → 1 }` que empujaba «PHUL adaptado» por
            // detrás de BBB y de la versión anterior de PHUL (rank 990).
            val gear = AuthoredPlanFixtures.fullGym
            val vm = newVm(ScriptedMaterializer())
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update {
                it.withCandidateInputs(goal = SetupGoal.STRENGTH_MUSCLE, days = 4, availability = gear.availability)
            }
            val state = awaitUntil(vm, "lista completa") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }

            val ids = state.availablePlanCandidates.map { it.id }
            val plannerIds = SetupTrainingPlanner.candidates(
                SetupTrainingPlannerInput(
                    reference = TrainingReference.POWERBUILDING,
                    frequency = 4,
                    equipment = gear.equipment.tokens,
                    level = CatalogLevel.INTERMEDIATE,
                    focus = TrainingFocus.FULL_BODY,
                ),
            ).map { it.id }
            assertEquals("el orden publicado es el del planificador: $ids", plannerIds.filter { it in ids }, ids)
            val adapted = "adapted:phul-kpkn-r1"
            val legacy = "protocol:phul-verified"
            assertTrue("PHUL adaptado es candidato: $ids", adapted in ids)
            if (legacy in ids) {
                assertTrue(
                    "PHUL adaptado (rank 220) va antes que la versión anterior (990): $ids",
                    ids.indexOf(adapted) < ids.indexOf(legacy),
                )
            }
        }

    // ═════════════════════════════════════════════════════════════════════════
    // D4 · B-07 — el pase a peso corporal (ViewModel real)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun aTimeBudgetRejectionNeverTriggersTheBodyweightPassAndItsRejectionsArePublished() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            // El pase a peso corporal TENDRÍA candidatos viables: lo que decide es la regla, no el resultado.
            val script = ScriptedMaterializer()
            script.failure = { draft -> if (isBodyweightPass(draft)) null else timeBudget(80) }
            val vm = newVm(script)
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs() }
            val state = awaitUntil(vm, "barrido terminado") { isIdle(it) && it.candidateRejections.isNotEmpty() }

            assertTrue("el pase a peso corporal no se intenta: ${script.calls()}", script.calls().none { it.bodyweightPass })
            assertFalse(state.planAdaptedToBodyweight)
            assertTrue(state.availablePlanCandidates.isEmpty())
            assertTrue(state.candidateRejections.all { it.reasonCode == PlanRejectionReason.TIME_BUDGET })
            assertTrue(state.candidateRejections.all { it.requiredMinutes == 80 })
            assertFalse(state.errors["candidates"].isNullOrBlank())
            assertEquals(CandidateListGate.NoneViable, candidateListGate(state))
            assertEquals(state.candidateRejections.size, state.candidateCounts.nonViable)
            assertEquals(0, state.candidateCounts.viable)
        }

    @Test
    fun aMixOfMaterialAndTimeRejectionsDoesNotTriggerTheBodyweightPassEither() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val ownPlan = "native:muscle-foundation-v2"
            val script = ScriptedMaterializer()
            script.failure = { draft ->
                when {
                    isBodyweightPass(draft) -> null
                    draft.selectedCatalogId == ownPlan -> timeBudget(80)
                    else -> apparatusAbsent()
                }
            }
            val vm = newVm(script)
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs() }
            val state = awaitUntil(vm, "barrido terminado") { isIdle(it) && it.candidateRejections.isNotEmpty() }

            val codes = state.candidateRejections.mapNotNull { it.reasonCode }.toSet()
            assertTrue(
                "el plan propio se rechazó por tiempo y el resto por material: $codes",
                codes.containsAll(setOf(PlanRejectionReason.TIME_BUDGET, PlanRejectionReason.APPARATUS_ABSENT)),
            )
            assertTrue("sin pase corporal: ${script.calls()}", script.calls().none { it.bodyweightPass })
            assertFalse(state.planAdaptedToBodyweight)
            assertTrue(state.availablePlanCandidates.isEmpty())
        }

    @Test
    fun onlyMaterialRejectionsTriggerTheBodyweightPassAndThePublishedRejectionsAreTheRequestedPasses() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val script = ScriptedMaterializer()
            script.failure = { draft -> if (isBodyweightPass(draft)) null else apparatusAbsent() }
            val vm = newVm(script)
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs() }
            val state = awaitUntil(vm, "barrido terminado") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }

            // El pase corporal se intentó y sus tarjetas son las que se muestran...
            assertTrue("el pase a peso corporal se intentó: ${script.calls()}", script.calls().any { it.bodyweightPass })
            assertTrue(state.planAdaptedToBodyweight)
            assertTrue(
                state.availablePlanCandidates.all { candidate ->
                    candidate.reasons.any { it.startsWith("Plan KPKN adaptado a peso corporal") }
                },
            )
            // ... pero lo que se explica son los rechazos y el conteo del pase PEDIDO (antes: los del corporal,
            // que aquí no tienen ninguno).
            assertTrue("rechazos del pase pedido: ${state.candidateRejections}", state.candidateRejections.isNotEmpty())
            assertTrue(state.candidateRejections.all { it.reasonCode == PlanRejectionReason.APPARATUS_ABSENT })
            assertEquals(0, state.candidateCounts.viable)
            assertEquals(state.candidateRejections.size, state.candidateCounts.nonViable)
            assertEquals(state.candidateRejections.size, state.candidateCounts.evaluated)
            assertTrue(candidateListGate(state) is CandidateListGate.Candidates)
        }

    // ═════════════════════════════════════════════════════════════════════════
    // D3 · B-06 — un catálogo que no quedó listo nunca se da por cargado
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun aCatalogThatFailsPublishesAVisibleCatalogRejectionAndTheRetryLoadsItAgain() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val flaky = FlakyCatalogRepository(ApprovedAssetExerciseCatalogRepositoryV2(app), failures = 1)
            val vm = newVm(ScriptedMaterializer(), catalog = flaky)
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs() }

            val failed = awaitUntil(vm, "fallo del catálogo publicado") {
                !it.isCandidateLoading && it.errors["candidates"] != null
            }

            assertEquals(CATALOG_UNAVAILABLE_MESSAGE, failed.errors["candidates"])
            val why = failed.candidateRejections.single()
            assertNull("el rechazo es global: no hay candidato que evaluar", why.planId)
            assertEquals(SetupCandidateRejectionStage.CATALOG, why.stage)
            assertEquals(PlanRejectionReason.CATALOG_NOT_READY, why.reasonCode)
            assertEquals(CATALOG_UNAVAILABLE_MESSAGE, why.reason)
            assertTrue(failed.availablePlanCandidates.isEmpty())
            assertNull("la búsqueda no escribe previewError", failed.previewError)
            assertEquals(CandidateListGate.SearchFailed(CATALOG_UNAVAILABLE_MESSAGE), candidateListGate(failed))
            // El catálogo sigue sin estar listo: ninguna revisión de catálogo y ninguna marca de «cargado».
            assertNull(vm.exerciseCatalogRevision())
            val loadsAfterFailure = flaky.loadCalls
            assertTrue(loadsAfterFailure >= 1)

            // «Reintentar» vuelve a cargar el catálogo (antes lo daba por cargado y no lo intentaba más).
            vm.retryFailedOperation(SetupRetryOperation.CANDIDATES)
            val recovered = awaitUntil(vm, "catálogo recuperado y lista publicada") {
                isIdle(it) && it.availablePlanCandidates.isNotEmpty()
            }

            assertTrue("el reintento volvió a cargar el catálogo", flaky.loadCalls > loadsAfterFailure)
            assertNotNull(vm.exerciseCatalogRevision())
            assertNull(recovered.errors["candidates"])
            assertTrue(recovered.candidateRejections.none { it.planId == null })
            assertTrue(candidateListGate(recovered) is CandidateListGate.Candidates)
        }

    // ═════════════════════════════════════════════════════════════════════════
    // D3 · B-05 — «Reintentar» no reproduce rechazos transitorios (ViewModel real)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun retryingAfterATransientFailureEvaluatesTheCandidatesAgainInsteadOfReplayingTheCache() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            val script = ScriptedMaterializer()
            script.failure = { IllegalStateException("fallo transitorio del motor") }
            val vm = newVm(script)
            vm.initialize(SetupWizardMode.TRAINING_ONLY)
            awaitUntil(vm, "wizard cargado") { !it.isLoading }
            vm.update { it.withCandidateInputs() }
            val failed = awaitUntil(vm, "primer barrido fallido") { isIdle(it) && it.candidateRejections.isNotEmpty() }

            assertTrue(failed.availablePlanCandidates.isEmpty())
            assertTrue(failed.candidateRejections.all { it.reasonCode == PlanRejectionReason.INTERNAL_MATERIALIZATION })
            // Un fallo interno tampoco es de material: no hay pase a peso corporal.
            assertTrue(script.calls().none { it.bodyweightPass })
            val callsAfterFailure = script.calls().size

            script.failure = { null }
            vm.retryFailedOperation(SetupRetryOperation.CANDIDATES)
            val recovered = awaitUntil(vm, "lista tras el reintento") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }

            assertTrue("el reintento volvió a evaluar los planes", script.calls().size > callsAfterFailure)
            assertTrue(recovered.candidateRejections.none { it.reasonCode == PlanRejectionReason.INTERNAL_MATERIALIZATION })
            assertNull(recovered.errors["candidates"])
        }

    // ═════════════════════════════════════════════════════════════════════════
    // Arnés
    // ═════════════════════════════════════════════════════════════════════════

    private fun newVm(
        materializer: SetupWizardMaterializer? = null,
        catalog: ExerciseCatalogRepositoryV2? = null,
    ): SetupWizardViewModel =
        SetupWizardViewModel(
            app,
            SavedStateHandle(),
            persistence = InMemoryPersistence(),
            environment = FixedSettingsEnvironment(Settings()),
            materializeOverride = materializer,
            catalogRepositoryOverride = catalog,
        ).also { store.put("candidate-gate-vm-${vmCounter++}", it) }

    /**
     * Entradas completas de candidato (Músculo, 3 días, 60 min, mancuernas) o las que se pidan. El material va
     * declarado de forma EXPLÍCITA (categorías), como lo deja el paso de disponibilidad.
     */
    private fun SetupWizardDraft.withCandidateInputs(
        goal: SetupGoal = SetupGoal.MUSCLE,
        days: Int = 3,
        availability: EquipmentAvailability = EquipmentAvailability(categories = setOf(EquipmentCategory.DUMBBELLS)),
    ): SetupWizardDraft = copy(
        includeTraining = true,
        programRoute = SetupProgramRoute.CUSTOMIZABLE,
        trainingPath = SetupTrainingPath.PERSONALIZE,
        goal = goal,
        experience = SetupExperience.INTERMEDIATE,
        focus = SetupFocus.FULL_BODY,
        daysPerWeek = days,
        selectedWeekdays = listOf(1, 3, 5, 2, 4, 6).take(days).toSet(),
        minutesPerSession = 60,
        trainingEnvironment = "gym",
        trainingOptions = trainingOptions.copy(availability = availability),
    )

    /**
     * Wizard de entrenamiento con candidatos calculados y UN plan elegido y preparado. Con [confirmPlan] el paso
     * PLAN consta como confirmado (lo único que hace que una selección sobreviva a una lista nueva sin ese plan).
     * Devuelve el id del plan elegido.
     */
    private fun TestScope.startWithChosenPlan(vm: SetupWizardViewModel, confirmPlan: Boolean): String {
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withCandidateInputs() }
        val listed = awaitUntil(vm, "lista inicial de candidatos") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }
        val chosen = listed.availablePlanCandidates.first().id

        vm.selectPlan(chosen)
        awaitUntil(vm, "plan elegido y preparado") {
            isIdle(it) && it.draft.selectedCatalogId == chosen && it.programPreview != null
        }
        if (confirmPlan) {
            // El paso PLAN confirmado, sin dejarlo pendiente de revisión (la elección del plan ya lo marcó).
            vm.update {
                it.copy(
                    stepProgress = it.stepProgress
                        .recordAnswer(SetupStepId.PLAN, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
                        .reviewDone(SetupStepId.PLAN),
                )
            }
            awaitUntil(vm, "paso PLAN confirmado") {
                isIdle(it) && SetupStepId.PLAN in it.draft.stepProgress.answers
            }
        }
        return chosen
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
            "candidatos=${state.availablePlanCandidates.map { it.id }} " +
            "rechazos=${state.candidateRejections.map { "${it.planId}:${it.reasonCode}" }}"

    /**
     * Puerto de materialización guionado: por defecto TODO plan queda listo; [failure] decide, borrador a
     * borrador, qué plan se rechaza y con qué fallo (un `PlanMaterializationException` tipado o cualquier otra
     * excepción, que el evaluador convierte en `INTERNAL_MATERIALIZATION`).
     */
    private class ScriptedMaterializer : SetupWizardMaterializer {
        data class Call(val planId: String?, val bodyweightPass: Boolean, val minutes: Int?)

        private val log = CopyOnWriteArrayList<Call>()

        @Volatile
        var failure: (SetupWizardDraft) -> Throwable? = { null }

        override suspend fun materialize(draft: SetupWizardDraft): SetupPreview {
            log += Call(draft.selectedCatalogId, isBodyweightPass(draft), draft.minutesPerSession)
            failure(draft)?.let { throw it }
            return SetupPreview(cannedProgram(draft.commitId), null)
        }

        fun calls(): List<Call> = log.toList()

        fun callsFor(planId: String): Int = log.count { it.planId == planId }

        /**
         * Llamadas del barrido y del preview del plan, con las respuestas de la persona (sus 60 min). No cuenta los
         * sondeos del asesor de reparaciones (A.C3): esos evalúan el plan propio con OTROS minutos («Ajustar a N min»)
         * o con otro material, y son justo lo que esta prueba no mide («el motor solo vio al plan UNA vez»).
         */
        fun sweepCallsFor(planId: String): Int = log.count { it.planId == planId && it.minutes == SWEEP_MINUTES }
    }

    /**
     * Repositorio del catálogo que falla las primeras [failures] cargas (publica `Error`, como el real, sin lanzar)
     * y luego delega en el repositorio real.
     */
    private class FlakyCatalogRepository(
        private val real: ExerciseCatalogRepositoryV2,
        private var failures: Int,
    ) : ExerciseCatalogRepositoryV2 by real {
        private val flakyState = MutableStateFlow<ExerciseCatalogStateV2>(ExerciseCatalogStateV2.Loading)

        @Volatile
        var loadCalls = 0
            private set

        override val state: StateFlow<ExerciseCatalogStateV2> get() = flakyState

        override suspend fun load() {
            loadCalls += 1
            if (failures > 0) {
                failures -= 1
                flakyState.value = ExerciseCatalogStateV2.Error("catalogo_de_prueba_no_disponible")
                return
            }
            real.load()
            flakyState.value = real.state.value
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

        /** Minutos por sesión de las entradas de candidato de esta clase (`withCandidateInputs`). */
        const val SWEEP_MINUTES = 60

        fun timeBudget(minutes: Int) = PlanMaterializationException(
            PlanEvaluationStage.SESSION_DURATION,
            PlanRejectionReason.TIME_BUDGET,
            "Este plan necesita $minutes min por sesión.",
            requiredMinutes = minutes,
        )

        fun apparatusAbsent() = PlanMaterializationException(
            PlanEvaluationStage.MATERIAL,
            PlanRejectionReason.APPARATUS_ABSENT,
            "Esta receta necesita material que no has declarado: barbell",
            missingRequirements = listOf("barbell"),
        )

        /** El segundo pase evalúa el borrador adaptado: categorías de material vacías (solo peso corporal). */
        fun isBodyweightPass(draft: SetupWizardDraft): Boolean =
            draft.trainingOptions.availability?.categories?.isEmpty() == true

        /** Programa preparado mínimo y ejecutable (el mismo patrón que `SetupWizardActivationGateTest`). */
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
