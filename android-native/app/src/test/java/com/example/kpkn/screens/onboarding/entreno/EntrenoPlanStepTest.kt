package com.example.kpkn.screens.onboarding.entreno

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.PLAN_PREPARE_FAILED_MESSAGE
import com.example.kpkn.screens.onboarding.SetupDroppedSelection
import com.example.kpkn.screens.onboarding.SetupPlanReveal
import com.example.kpkn.screens.onboarding.SetupPlanRevealBlock
import com.example.kpkn.screens.onboarding.SetupPlanRevealDay
import com.example.kpkn.screens.onboarding.SetupPlanSweep
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupProgramRoute
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PLAN_DETAIL_CHOOSE_TAG
import com.example.kpkn.screens.onboarding.design.entreno.plan.PLAN_DETAIL_CLOSE_TAG
import com.example.kpkn.screens.onboarding.design.entreno.plan.PLAN_DETAIL_TAG
import com.example.kpkn.screens.onboarding.design.entreno.plan.PLAN_PREPARING_TAG
import com.example.kpkn.screens.onboarding.design.entreno.plan.PreparingTimeline
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withPlaces
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Entreno v2 · el paso PLAN de punta a punta en pantalla, alimentado con el estado que publica el ViewModel: el overlay
 * «preparando…» cubre el barrido y se retira al avisar; el perfil general revela su programa «a medida» y los de
 * disciplina, el carrusel; el detalle sale del programa ya preparado; el fallo ofrece «Reintentar» y el aplazado, volver.
 * El ViewModel no se inicializa (sus escrituras las cubren las pruebas del ViewModel); el movimiento reducido del
 * sistema hace que el overlay avise a los 0,6 s de estar listo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EntrenoPlanStepTest {

    @get:Rule
    val rule = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var vm: SetupWizardViewModel
    private var state by mutableStateOf(SetupWizardState(draft = SetupWizardDraft()))

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        android.provider.Settings.Global.putFloat(
            app.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        vm = SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = NoPersistence,
            environment = StubEnvironment,
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { store.put("entreno-plan-step-vm", it) }
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Composable
    private fun Page() {
        Column(Modifier.verticalScroll(rememberScrollState())) { EntrenoPlanStep(state, vm) }
    }

    private fun draftFor(profile: TrainingGoalProfile, step: SetupStepId = SetupStepId.PLAN): SetupWizardDraft =
        SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM)).withGoalProfile(profile)
            .copy(stepProgress = SetupStepProgress(block = SetupWizardBlock.TRAINING, currentStepId = step))

    private fun reveal(
        id: String,
        title: String,
        profile: TrainingGoalProfile,
        coverSeed: Int = 0,
        exercises: List<String> = listOf("Sentadilla", "Press de banca"),
    ) = SetupPlanReveal(
        planId = id,
        title = title,
        generated = id.startsWith("generated:"),
        profile = profile,
        kicker = if (id.startsWith("generated:")) "Hecho a tu medida" else "Plan KPKN",
        blurb = "Cuatro días con tu material, unos 60 min por sesión.",
        description = "El programa completo, semana a semana.",
        daysLabel = "4 días",
        minutesLabel = "~60 min",
        levelLabel = "Intermedio",
        badge = null,
        coverSeed = coverSeed,
        mainExercises = exercises,
        blocks = listOf(SetupPlanRevealBlock("Semana que se repite", "Cada semana", "La misma semana, cada semana.")),
        week = listOf(
            SetupPlanRevealDay(day = 1, title = "Torso", minutes = 60, exerciseCount = 6, isMain = true),
            SetupPlanRevealDay(day = 3, title = "Pierna", minutes = 58, exerciseCount = 5, isMain = false),
        ),
        reasons = listOf("Usa solo tu material.", "Cabe en tus 60 minutos.", "Prioriza los músculos que elegiste."),
        notes = emptyList(),
        attribution = null,
    )

    /**
     * Deja que lo último (un estado publicado, un toque) llegue a la pantalla con el reloj quieto: vacía la cola
     * principal y pasa un fotograma, unas cuantas veces (los diálogos y los efectos necesitan las dos cosas).
     */
    private fun settle() = repeat(SETTLE_ROUNDS) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
    }

    /** Publica un estado nuevo, como lo haría el ViewModel, y lo deja llegar a la pantalla. */
    private fun publish(next: SetupWizardState) {
        state = next
        settle()
    }

    /** Con el resultado ya listo, el aviso del overlay (0,6 s con movimiento reducido) y el fotograma que lo retira. */
    private fun finishOverlay() {
        settle()
        rule.mainClock.advanceTimeBy(PreparingTimeline.REDUCED_HOLD_MS + OVERLAY_SLACK_MS)
        settle()
    }

    /** Toca una acción por su semántica (sin depender de los tiempos de los gestos) y deja llegar el resultado. */
    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** Toca una acción de un diálogo (no hay desplazamiento que hacer). */
    private fun tapInDialog(tag: String) {
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    @Test
    fun aGeneralProfileRevealsItsTailoredProgramOnlyWhenThePreparingOverlayEnds() {
        rule.mainClock.autoAdvance = false
        state = SetupWizardState(draft = draftFor(TrainingGoalProfile.STRENGTH_MUSCLE), planSweep = SetupPlanSweep.LOADING)
        rule.setContent { Page() }
        settle()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertExists()
        rule.onNodeWithTag(REVEAL_TAG).assertDoesNotExist()
        // El motor tarda: el overlay sigue mientras no hay resultado.
        rule.mainClock.advanceTimeBy(10_000)
        settle()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertExists()

        publish(state.copy(planSweep = SetupPlanSweep.READY, planReveals = listOf(reveal(TAILORED, "Fuerza y masa muscular a medida", TrainingGoalProfile.STRENGTH_MUSCLE))))
        // Listo, pero la página no se revela hasta que el overlay avisa.
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertExists()
        rule.onNodeWithTag(REVEAL_TAG).assertDoesNotExist()

        finishOverlay()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertDoesNotExist()
        rule.onNodeWithTag(REVEAL_TAG).assertExists()
        rule.onNodeWithText("Tu programa está listo").assertExists()
        rule.onNodeWithTag("setup-plan-card-$TAILORED").assertExists()
        rule.onNodeWithTag("setup-plan-choose-$TAILORED").assertExists()
        rule.onNodeWithText("Cabe en tus 60 minutos.").assertExists()
        rule.onNodeWithTag(ANOTHER_VERSION_TAG).assertExists()
        rule.onNodeWithText("Podrás modificarlo libremente después.").assertExists()
        rule.onNodeWithTag(DEFER_TAG).assertExists()
    }

    @Test
    fun anotherVersionPlaysTheOverlayAgainEvenWhenTheResultLooksTheSame() {
        rule.mainClock.autoAdvance = false
        val first = reveal(TAILORED, "Fuerza y masa muscular a medida", TrainingGoalProfile.STRENGTH_MUSCLE)
        state = SetupWizardState(draft = draftFor(TrainingGoalProfile.STRENGTH_MUSCLE), planSweep = SetupPlanSweep.READY, planReveals = listOf(first))
        rule.setContent { Page() }
        // Al llegar al paso con el resultado ya listo, el overlay se ve igualmente (y avisa enseguida).
        settle()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertExists()
        finishOverlay()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertDoesNotExist()
        rule.onNodeWithTag(REVEAL_TAG).assertExists()

        // «Otra versión»: barrido nuevo con el MISMO resultado visible; el overlay corre hasta su aviso.
        publish(state.copy(planSweep = SetupPlanSweep.LOADING, planReveals = emptyList()))
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertExists()
        publish(state.copy(planSweep = SetupPlanSweep.READY, planReveals = listOf(first)))
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertExists()
        finishOverlay()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertDoesNotExist()
        rule.onNodeWithTag(REVEAL_TAG).assertExists()

        // Y con otra versión de verdad (otra portada, otros ejercicios), lo mismo.
        publish(state.copy(planSweep = SetupPlanSweep.LOADING, planReveals = emptyList()))
        publish(
            state.copy(
                planSweep = SetupPlanSweep.READY,
                planReveals = listOf(first.copy(coverSeed = 1, mainExercises = listOf("Sentadilla frontal", "Press inclinado"))),
            ),
        )
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertExists()
        finishOverlay()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertDoesNotExist()
        rule.onNodeWithTag(REVEAL_TAG).assertExists()
    }

    @Test
    fun aRevealedResultDoesNotReplayTheOverlayAfterTheScreenIsRecreated() {
        rule.mainClock.autoAdvance = false
        val restoration = StateRestorationTester(rule)
        state = SetupWizardState(
            draft = draftFor(TrainingGoalProfile.FUNCTIONAL_HEALTH),
            planSweep = SetupPlanSweep.READY,
            planReveals = listOf(reveal("generated:functional", "Funcional y saludable a medida", TrainingGoalProfile.FUNCTIONAL_HEALTH)),
        )
        restoration.setContent { Page() }
        finishOverlay()
        rule.onNodeWithTag(REVEAL_TAG).assertExists()

        restoration.emulateSavedInstanceStateRestore()
        settle()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertDoesNotExist()
        rule.onNodeWithTag(REVEAL_TAG).assertExists()
    }

    @Test
    fun aDisciplineShowsItsProgramsInTheCarouselWithTheChosenOneMarked() {
        // El paso aún no es el activo (la página se asoma por debajo): sin overlay, el resultado se ve tal cual.
        state = SetupWizardState(
            draft = draftFor(TrainingGoalProfile.POWERLIFTING, step = SetupStepId.TRAINING_MAX).copy(selectedCatalogId = AUTHORED),
            planSweep = SetupPlanSweep.READY,
            planReveals = listOf(
                reveal("generated:powerlifting", "Powerlifting a medida", TrainingGoalProfile.POWERLIFTING),
                reveal(AUTHORED, "Powerlifting KPKN", TrainingGoalProfile.POWERLIFTING, coverSeed = 1),
                reveal("native:texas-method", "Texas Method", TrainingGoalProfile.POWERLIFTING, coverSeed = 2),
            ),
        )
        rule.setContent { Page() }
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertDoesNotExist()
        rule.onNodeWithTag("setup-plan-card-$AUTHORED").assertIsSelected()
        rule.onNodeWithTag("setup-plan-card-generated:powerlifting").assertIsNotSelected()
        // El revelado del perfil general (y su «Otra versión») no es de las disciplinas.
        rule.onNodeWithTag(REVEAL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(ANOTHER_VERSION_TAG).assertDoesNotExist()
    }

    @Test
    fun theDetailShowsThePreparedProgramAndClosingItLeavesTheStepPending() {
        rule.mainClock.autoAdvance = false
        state = SetupWizardState(
            draft = draftFor(TrainingGoalProfile.BODYBUILDING),
            planSweep = SetupPlanSweep.READY,
            planReveals = listOf(
                reveal("generated:bodybuilding", "Culturismo a medida", TrainingGoalProfile.BODYBUILDING),
                reveal("native:phul", "PHUL", TrainingGoalProfile.BODYBUILDING, coverSeed = 1),
            ),
        )
        rule.setContent { Page() }
        finishOverlay()

        tap("setup-plan-open-generated:bodybuilding")
        rule.onNodeWithTag(PLAN_DETAIL_TAG).assertExists()
        // El detalle es el del programa abierto: su descripción y sus razones (que la página de una disciplina no enseña).
        rule.onNodeWithText("El programa completo, semana a semana.").assertExists()
        rule.onNodeWithText("Prioriza los músculos que elegiste.").assertExists()
        rule.onNodeWithText("Elegir este programa").assertExists()
        tapInDialog(PLAN_DETAIL_CLOSE_TAG)
        rule.onNodeWithTag(PLAN_DETAIL_TAG).assertDoesNotExist()
        // Sin elegir, la portada sigue ofreciendo «Elegir» y «Ver detalles».
        rule.onNodeWithTag("setup-plan-choose-generated:bodybuilding").assertExists()

        // «Elegir este programa» desde el detalle también lo cierra.
        tap("setup-plan-open-generated:bodybuilding")
        rule.onNodeWithTag(PLAN_DETAIL_TAG).assertExists()
        tapInDialog(PLAN_DETAIL_CHOOSE_TAG)
        rule.onNodeWithTag(PLAN_DETAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun aNewSweepClosesAnOpenDetail() {
        rule.mainClock.autoAdvance = false
        val tailored = reveal("generated:hybrid", "Fuerza y cardio a medida", TrainingGoalProfile.STRENGTH_CARDIO)
        state = SetupWizardState(draft = draftFor(TrainingGoalProfile.STRENGTH_CARDIO), planSweep = SetupPlanSweep.READY, planReveals = listOf(tailored))
        rule.setContent { Page() }
        finishOverlay()
        tap("setup-plan-open-generated:hybrid")
        rule.onNodeWithTag(PLAN_DETAIL_TAG).assertExists()

        publish(state.copy(planSweep = SetupPlanSweep.LOADING, planReveals = emptyList()))
        rule.onNodeWithTag(PLAN_DETAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertExists()
        publish(state.copy(planSweep = SetupPlanSweep.READY, planReveals = listOf(tailored)))
        finishOverlay()
        // El detalle no vuelve solo: el barrido nuevo pide mirarlo otra vez.
        rule.onNodeWithTag(PLAN_DETAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(REVEAL_TAG).assertExists()
    }

    @Test
    fun aFailedSweepSaysItInPlainWordsAndOffersToRetry() {
        state = SetupWizardState(
            draft = draftFor(TrainingGoalProfile.STRENGTH_CARDIO),
            planSweep = SetupPlanSweep.FAILED,
            errors = mapOf("candidates" to PLAN_PREPARE_FAILED_MESSAGE),
        )
        rule.setContent { Page() }
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertDoesNotExist()
        rule.onNodeWithText(PLAN_PREPARE_FAILED_MESSAGE).assertExists()
        rule.onNodeWithTag(RETRY_SWEEP_TAG).assertExists()
        rule.onNodeWithText("Reintentar").assertExists()
        rule.onNodeWithTag(REVEAL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(DEFER_TAG).assertExists()
    }

    @Test
    fun aChosenPlanThatNoLongerFitsIsExplainedWithoutABoxAndSeeingAlternativesClosesIt() {
        val dropped = "native:phul"
        state = SetupWizardState(
            draft = draftFor(TrainingGoalProfile.BODYBUILDING, step = SetupStepId.TRAINING_MAX).copy(selectedCatalogId = dropped),
            planSweep = SetupPlanSweep.READY,
            planReveals = listOf(reveal("generated:bodybuilding", "Culturismo a medida", TrainingGoalProfile.BODYBUILDING)),
            droppedSelection = SetupDroppedSelection(planId = dropped, title = "PHUL", rejection = null),
        )
        rule.setContent { Page() }
        rule.onNodeWithTag(DROPPED_NOTICE_TAG).assertExists()
        rule.onNodeWithText(
            "Tu plan elegido ya no encaja con tus respuestas. Ya no está entre los planes que corresponden a tus respuestas.",
        ).assertExists()
        rule.onNodeWithTag("$DROPPED_NOTICE_TAG-0").assertExists()
        rule.onNodeWithText("Ver alternativas").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithTag(DROPPED_NOTICE_TAG).assertDoesNotExist()
        // Las alternativas siguen ahí, debajo.
        rule.onNodeWithTag("setup-plan-card-generated:bodybuilding").assertExists()
    }

    @Test
    fun aDeferredProgramOffersToPrepareItAgainAndLeavesWhatHappensToTheSectionHeading() {
        state = SetupWizardState(
            draft = draftFor(TrainingGoalProfile.FUNCTIONAL_HEALTH).copy(programRoute = SetupProgramRoute.LATER),
            planSweep = SetupPlanSweep.IDLE,
        )
        rule.setContent { Page() }
        rule.onNodeWithTag(PLAN_PREPARING_TAG).assertDoesNotExist()
        // El contenido del paso no repite el título ni el subtítulo que ya pinta la sección (ver `wizardPageCopy`).
        rule.onNodeWithText("Lo armarás manualmente más adelante.").assertDoesNotExist()
        rule.onNodeWithTag("setup-plan-resume").assertExists()
        rule.onNodeWithTag(DEFER_TAG).assertDoesNotExist()
    }

    private object NoPersistence : SetupWizardPersistence {
        override suspend fun load(draftId: String): SetupDraft? = null
        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)

        override suspend fun discard(draftId: String) = Unit
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private object StubEnvironment : SetupWizardEnvironment {
        override val settings: Settings = Settings()
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }

    private companion object {
        const val TAILORED = "generated:strength-muscle"
        const val AUTHORED = "native:powerlifting-own"
        const val REVEAL_TAG = "setup-plan-reveal"
        const val ANOTHER_VERSION_TAG = "setup-plan-another-version"
        const val DEFER_TAG = "setup-plan-defer"

        /** Margen tras el aviso del overlay: el fotograma que lo retira y el de la página revelada. */
        const val OVERLAY_SLACK_MS = 500L

        /** Vueltas de «cola principal + fotograma» para que un cambio llegue a la pantalla con el reloj quieto. */
        const val SETTLE_ROUNDS = 4
    }
}
