package com.example.kpkn.screens.onboarding.entreno

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupLayoutSession
import com.example.kpkn.screens.onboarding.SetupPlaceConflict
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupSplitOption
import com.example.kpkn.screens.onboarding.SetupWeekLayout
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.layout.WEEK_LAYOUT_ADAPT_TAG
import com.example.kpkn.screens.onboarding.design.entreno.layout.WEEK_LAYOUT_BOARD_TAG
import com.example.kpkn.screens.onboarding.design.entreno.layout.WEEK_LAYOUT_RESET_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Entreno v2 · el paso WEEK_LAYOUT en pantalla, alimentado con la semana armada que publica el ViewModel
 * ([SetupWeekLayout]): el tablero de U5 con las sesiones en sus días, la confirmación de COPY antes de adaptar el
 * reparto (con el aviso de los planes de autor), «Restablecer» también sin repartos, el reparto rechazado y el fallo de
 * la vista previa sin cajas. El ViewModel no se inicializa (sus escrituras las cubren `SetupWizardEntrenoPlanTest` y
 * `SetupWeekLayoutsTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EntrenoWeekLayoutStepTest {

    @get:Rule
    val rule = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var vm: SetupWizardViewModel

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
        ).also { store.put("entreno-week-step-vm", it) }
    }

    @After
    fun tearDown() {
        store.clear()
    }

    private val draft = SetupWizardDraft()
        .copy(stepProgress = SetupStepProgress(block = SetupWizardBlock.TRAINING, currentStepId = SetupStepId.WEEK_LAYOUT))

    private val sessions = listOf(
        SetupLayoutSession("p-s0", "Torso", "Pecho y espalda", minutes = 60, exerciseCount = 6, isMain = true, place = TrainingPlace.GYM),
        SetupLayoutSession("p-s1", "Pierna", "Cuádriceps y glúteos", minutes = 55, exerciseCount = 5, isMain = false, place = TrainingPlace.GYM),
        SetupLayoutSession("p-s2", "Torso B", "Hombro y brazo", minutes = 50, exerciseCount = 5, isMain = false, place = TrainingPlace.GYM),
    )

    private val upperLower = SetupSplitOption("upper-lower", "Torso y pierna", "Alterna torso y pierna.", listOf("Torso", "Pierna", "Torso"))

    private fun layout(
        options: List<SetupSplitOption> = listOf(upperLower),
        authored: Boolean = false,
        canReset: Boolean = false,
        notes: List<String> = emptyList(),
        refusal: String? = null,
        placeConflicts: List<SetupPlaceConflict> = emptyList(),
    ) = SetupWeekLayout(
        weekStartDay = 1,
        sessions = sessions,
        assignment = mapOf(1 to "p-s0", 3 to "p-s1", 5 to "p-s2"),
        splitOptions = options,
        selectedSplitId = null,
        canReset = canReset,
        authoredStructure = authored,
        notes = notes,
        refusal = refusal,
        placeConflicts = placeConflicts,
    )

    private fun show(state: SetupWizardState) {
        rule.setContent { Page(state) }
        rule.waitForIdle()
    }

    @Composable
    private fun Page(state: SetupWizardState) {
        Column(Modifier.verticalScroll(rememberScrollState())) { EntrenoWeekLayoutStep(state, vm) }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    @Test
    fun theBoardShowsTheProgramSessionsInTheirDays() {
        show(SetupWizardState(draft = draft, weekLayout = layout()))
        rule.onNodeWithTag(WEEK_LAYOUT_BOARD_TAG).assertExists()
        rule.onNodeWithTag("setup-layout-session-p-s0").assertExists()
        rule.onNodeWithTag("setup-layout-session-p-s1").assertExists()
        rule.onNodeWithTag("setup-layout-split-upper-lower").assertExists()
        // Nada movido todavía: sin «Restablecer».
        rule.onNodeWithTag(WEEK_LAYOUT_RESET_TAG).assertDoesNotExist()
    }

    @Test
    fun adaptingToASplitAsksForConfirmationAndKeepingTheSplitCancelsIt() {
        show(SetupWizardState(draft = draft, weekLayout = layout()))
        tap("setup-layout-split-upper-lower")
        tap(WEEK_LAYOUT_ADAPT_TAG)
        rule.onNodeWithText("¿Adaptar tu programa a «Torso y pierna»?").assertExists()
        rule.onNodeWithText("Reubicamos los ejercicios y mantenemos tu volumen semanal.").assertExists()
        rule.onNodeWithText(AUTHORED_SPLIT_NOTICE).assertDoesNotExist()
        rule.onNodeWithTag(LAYOUT_ADAPT_CONFIRM_TAG).assertExists()
        rule.onNodeWithTag(LAYOUT_ADAPT_KEEP_TAG).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("¿Adaptar tu programa a «Torso y pierna»?").assertDoesNotExist()
    }

    @Test
    fun anAuthoredPlanWarnsThatAdaptingChangesItsStructure() {
        show(SetupWizardState(draft = draft, weekLayout = layout(authored = true)))
        tap("setup-layout-split-upper-lower")
        // El tablero lo dice junto a «Adaptar…» y la confirmación lo repite.
        rule.onNodeWithText(AUTHORED_SPLIT_NOTICE).assertExists()
        tap(WEEK_LAYOUT_ADAPT_TAG)
        rule.onAllNodesWithText(AUTHORED_SPLIT_NOTICE).assertCountEquals(2)
    }

    @Test
    fun withoutSplitsToOfferMovedSessionsCanStillBeReset() {
        val note = "Cada sesión usa el material de su lugar: mueve las sesiones en lugar de cambiar el reparto."
        show(SetupWizardState(draft = draft, weekLayout = layout(options = emptyList(), canReset = true, notes = listOf(note))))
        rule.onNodeWithTag("setup-layout-split-upper-lower").assertDoesNotExist()
        rule.onNodeWithTag(WEEK_LAYOUT_RESET_TAG).assertExists()
        rule.onNodeWithText(note).assertExists()
    }

    @Test
    fun aRefusedSplitIsExplainedInPlainWords() {
        show(SetupWizardState(draft = draft, weekLayout = layout(refusal = "Este reparto no está disponible.")))
        rule.onNodeWithTag(LAYOUT_REFUSAL_TAG).assertExists()
        rule.onNodeWithText("Este reparto no está disponible.").assertExists()
    }

    @Test
    fun aSessionThatDoesNotFitItsDaysPlaceIsWarnedBelowTheBoardAndKeepsItsPlaceLabel() {
        val conflict = SetupPlaceConflict(
            sessionId = "p-s1",
            title = "Pierna",
            day = 3,
            sessionPlace = TrainingPlace.GYM,
            dayPlace = TrainingPlace.HOME,
        )
        show(SetupWizardState(draft = draft, weekLayout = layout(canReset = true, placeConflicts = listOf(conflict))))
        rule.onNodeWithTag("$LAYOUT_PLACE_CONFLICT_TAG-p-s1").assertExists()
        rule.onNodeWithText("Pierna · miércoles").assertExists()
        rule.onNodeWithText("Esta sesión usa material del gimnasio; ese día entrenas en casa.").assertExists()
    }

    @Test
    fun withoutConflictsThereIsNoPlaceWarning() {
        show(SetupWizardState(draft = draft, weekLayout = layout()))
        rule.onNodeWithTag("$LAYOUT_PLACE_CONFLICT_TAG-p-s1").assertDoesNotExist()
    }

    @Test
    fun aPreviewFailureOffersToRetry() {
        show(SetupWizardState(draft = draft, weekLayout = layout(), previewError = "No pudimos preparar la vista previa."))
        rule.onNodeWithTag(LAYOUT_RETRY_TAG).assertExists()
        rule.onNodeWithTag(WEEK_LAYOUT_BOARD_TAG).assertDoesNotExist()
    }

    @Test
    fun aMissingWeekSaysItIsBeingPrepared() {
        show(SetupWizardState(draft = draft))
        rule.onNodeWithText("Preparando tu semana…").assertExists()
        rule.onNodeWithTag(WEEK_LAYOUT_BOARD_TAG).assertDoesNotExist()
    }

    @Test
    fun thePlaceOnlyJoinsTheFocusWhenTheWeekUsesSeveralPlaces() {
        assertEquals(listOf("Pecho y espalda", "Cuádriceps y glúteos", "Hombro y brazo"), boardSessionsOf(sessions).map { it.focus })
        val mixed = sessions.mapIndexed { index, session -> if (index == 1) session.copy(place = TrainingPlace.HOME) else session }
        assertEquals(
            listOf("Pecho y espalda · ${TrainingPlace.GYM.label}", "Cuádriceps y glúteos · ${TrainingPlace.HOME.label}"),
            boardSessionsOf(mixed).take(2).map { it.focus },
        )
        // El resto pasa tal cual.
        val board = boardSessionsOf(sessions).first()
        assertEquals(listOf("p-s0", "Torso", "60", "6", "true"), listOf(board.id, board.title, "${board.minutes}", "${board.exerciseCount}", "${board.isMain}"))
    }

    /**
     * El glifo del lugar de cada ficha sale del campo `place` de la sesión del tablero, no del texto del foco: con todas las
     * sesiones en el mismo lugar no se dice (sería ruido) y con varios lugares cada ficha lleva el suyo, igual que `placeId`.
     */
    @Test
    fun theBoardGetsThePlaceAsAFieldOnlyWhenTheWeekUsesSeveralPlaces() {
        assertEquals(listOf<TrainingPlace?>(null, null, null), boardSessionsOf(sessions).map { it.place })
        val mixed = sessions.mapIndexed { index, session -> if (index == 1) session.copy(place = TrainingPlace.HOME) else session }
        assertEquals(listOf(TrainingPlace.GYM, TrainingPlace.HOME, TrainingPlace.GYM), boardSessionsOf(mixed).map { it.place })
        // Sin lugar declarado en la sesión, tampoco hay glifo aunque haya otras con lugar.
        val partial = mixed.mapIndexed { index, session -> if (index == 2) session.copy(place = null) else session }
        assertEquals(listOf(TrainingPlace.GYM, TrainingPlace.HOME, null), boardSessionsOf(partial).map { it.place })
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
}
