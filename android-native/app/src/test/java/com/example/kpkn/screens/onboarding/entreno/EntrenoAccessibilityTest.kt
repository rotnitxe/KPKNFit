package com.example.kpkn.screens.onboarding.entreno

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.plan.contrastRatio
import com.example.kpkn.screens.onboarding.withCapability
import com.example.kpkn.screens.onboarding.withDayPlace
import com.example.kpkn.screens.onboarding.withFreshestDay
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withLiftMark
import com.example.kpkn.screens.onboarding.withMuscleToggled
import com.example.kpkn.screens.onboarding.withPlaces
import com.example.kpkn.screens.onboarding.withSessionMinutes
import com.example.kpkn.screens.onboarding.withWeekStart
import com.example.kpkn.screens.onboarding.withWeekdays
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Accesibilidad de los nueve pasos del bloque de Entreno, recorriendo el árbol de semántica de cada uno a 360 dp (con el margen de
 * 24 dp de la página larga) y con la letra al 100 % y al 130 %:
 *  - todo lo que se toca es un objetivo táctil de al menos 48 × 48 dp;
 *  - todo lo que se toca tiene un nombre que decir (descripción o texto) y un rol o un rango (los controles deslizantes);
 *  - lo que se lee, se lee en el orden en que se ve (los días de la semana, que se colocan según el inicio de la semana);
 *  - el texto de lectura tiene el contraste que pide WCAG sobre el negro de la página.
 * Los roles y los estados de cada control (casillas, opciones de radio, rangos) los fija cada prueba de su componente.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class EntrenoAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var vm: SetupWizardViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = NoPersistence,
            environment = StubEnvironment,
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { store.put("entreno-a11y-vm", it) }
    }

    @After
    fun tearDown() {
        store.clear()
    }

    /** Un paso con el borrador con el que se prueba. */
    private class StepCase(
        val name: String,
        val draft: SetupWizardDraft,
        val content: @Composable (SetupWizardState, SetupWizardViewModel) -> Unit,
    )

    /** Una sola composición (la regla solo admite una por prueba) en la que se cambia de paso y de letra con estado. */
    private fun host(cases: List<StepCase>): Pair<(Int) -> Unit, (Float) -> Unit> {
        var index by mutableIntStateOf(0)
        var scale by mutableFloatStateOf(1f)
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                // Como en la página larga: con el margen de 24 dp y sin alto limitado.
                Column(Modifier.padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
                    key(index) {
                        val case = cases[index]
                        case.content(SetupWizardState(draft = case.draft), vm)
                    }
                }
            }
        }
        rule.waitForIdle()
        return Pair({ i -> rule.runOnUiThread { index = i }; rule.waitForIdle() }, { f -> rule.runOnUiThread { scale = f }; rule.waitForIdle() })
    }

    private fun show(draft: SetupWizardDraft, content: @Composable (SetupWizardState, SetupWizardViewModel) -> Unit) {
        host(listOf(StepCase("paso", draft, content)))
    }

    // ------------------------------------------------------------ los nueve pasos

    private val gym = setOf(TrainingPlace.GYM)

    private val steps: List<StepCase> = listOf(
        StepCase("lugares", SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.HOME))) { s, v -> EntrenoPlacesStep(s, v) },
        StepCase("material", SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC))) { s, v -> EntrenoMaterialStep(s, v) },
        StepCase(
            "objetivo",
            SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME)).withGoalProfile(TrainingGoalProfile.FUNCTIONAL_HEALTH),
        ) { s, v -> EntrenoGoalStep(s, v) },
        StepCase("día con más energía", SetupWizardDraft().withFreshestDay(4)) { s, v -> EntrenoFreshDayStep(s, v) },
        StepCase(
            "días",
            SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.HOME)).withFreshestDay(4)
                .withWeekdays(setOf(1, 3, 4, 6)).withDayPlace(3, TrainingPlace.HOME),
        ) { s, v -> EntrenoWeekdaysStep(s, v) },
        StepCase("tiempo", SetupWizardDraft().withSessionMinutes(75)) { s, v -> EntrenoSessionTimeStep(s, v) },
        StepCase(
            "capacidades",
            SetupWizardDraft().withPlaces(setOf(TrainingPlace.PUBLIC)).withGoalProfile(TrainingGoalProfile.CALISTHENICS)
                .withCapability(CapabilitySkill.PULL_UP, CapabilityLevel.SOME),
        ) { s, v -> EntrenoCapabilitiesStep(s, v) },
        StepCase(
            "músculos",
            SetupWizardDraft().withMuscleToggled(MuscleSymbol.CHEST).withMuscleToggled(MuscleSymbol.BACK),
        ) { s, v -> EntrenoMusclesStep(s, v) },
        StepCase(
            "marcas",
            SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.POWERLIFTING)
                .copy(experience = SetupExperience.ADVANCED).withLiftMark(LiftMark.SQUAT, 140.0),
        ) { s, v -> EntrenoMarksStep(s, v) },
    )

    /** Los nodos que se tocan, se activan o se ajustan (el árbol fusionado, el que ve TalkBack). */
    private fun interactiveNodes(): List<SemanticsNode> {
        val found = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            val config = node.config
            if (config.contains(SemanticsActions.OnClick) || config.contains(SemanticsProperties.ToggleableState) ||
                config.contains(SemanticsActions.SetProgress)
            ) {
                found += node
            }
            node.children.forEach(::walk)
        }
        walk(rule.onRoot(useUnmergedTree = false).fetchSemanticsNode())
        return found
    }

    private fun nameOf(node: SemanticsNode): String {
        val config = node.config
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        return description.ifBlank { text }
    }

    @Test
    fun everyControlOfTheNineStepsIsAFortyEightDpTargetWithANameAndARole() {
        val (goTo, setScale) = host(steps)
        // Se juntan todos los fallos y se avisa una sola vez: una corrida larga debe enseñar todo lo que hay que arreglar.
        val failures = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f)) {
            setScale(scale)
            for ((index, case) in steps.withIndex()) {
                goTo(index)
                val name = case.name
                val nodes = interactiveNodes()
                if (nodes.isEmpty()) failures += "$name (letra $scale): no hay ningún control"
                for (node in nodes) {
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: nameOf(node)
                    val density = node.layoutInfo.density.density
                    val width = node.size.width / density
                    val height = node.size.height / density
                    if (height < 47.99f) failures += "$name (letra $scale): «$tag» mide $height dp de alto"
                    // El ancho de un control que es solo texto sale del texto, y Robolectric no mide las letras de verdad (una por
                    // píxel): solo se exige a los que no dependen de él. Los de texto llevan un ancho mínimo propio (ver el código).
                    if (width < 47.99f && tag.toString() !in TEXT_WIDTH_CONTROLS) failures += "$name (letra $scale): «$tag» mide $width dp de ancho"
                    if (nameOf(node).isBlank()) failures += "$name (letra $scale): «$tag» no tiene nombre"
                    val hasRole = node.config.contains(SemanticsProperties.Role)
                    val isRange = node.config.contains(SemanticsProperties.ProgressBarRangeInfo)
                    if (!(hasRole || isRange)) failures += "$name (letra $scale): «$tag» no tiene rol ni rango"
                }
            }
        }
        assertTrue("Controles que no cumplen:\n" + failures.distinct().joinToString("\n"), failures.isEmpty())
    }

    /** Controles de solo texto cuyo ancho sale del texto (Robolectric no lo mide de verdad): se comprueban en el teléfono. */
    private val TEXT_WIDTH_CONTROLS = setOf("setup-weekstart")

    // ------------------------------------------------------------ el orden de lectura

    /** Los días de entreno en el orden en que los leería TalkBack: por el índice de recorrido que lleva cada uno. */
    private fun dayReadingOrder(): List<Int> = (1..7)
        .map { day -> day to rule.onNodeWithTag("setup-weekday-$day").fetchSemanticsNode() }
        .sortedBy { (_, node) -> node.config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f }
        .map { it.first }

    @Test
    fun theWeekIsReadInTheOrderItIsShownFromTheDayTheWeekStarts() {
        // Con la semana empezando el jueves se ve jueves, viernes, sábado, domingo, lunes, martes y miércoles: así se lee.
        show(SetupWizardDraft().withWeekdays(setOf(1, 4, 6)).withFreshestDay(4)) { s, v -> EntrenoWeekdaysStep(s, v) }
        assertEquals(listOf(4, 5, 6, 7, 1, 2, 3), dayReadingOrder())
    }

    @Test
    fun aWeekThatStartsOnMondayIsReadFromMonday() {
        show(SetupWizardDraft().withWeekdays(setOf(2, 5)).withFreshestDay(1)) { s, v -> EntrenoWeekdaysStep(s, v) }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), dayReadingOrder())
    }

    @Test
    fun theWeekStartingOnASundayIsReadFromSunday() {
        show(SetupWizardDraft().withWeekdays(setOf(2, 5)).withFreshestDay(2).withWeekStart(7)) { s, v -> EntrenoWeekdaysStep(s, v) }
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), dayReadingOrder())
    }

    @Test
    fun theGridsAreReadRowByRowFromTopToBottom() {
        // Material: las filas de la cuadrícula en el orden en que se ven (por arriba y de izquierda a derecha).
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM))) { s, v -> EntrenoMaterialStep(s, v) }
        val tagged = interactiveNodes().filter { (it.config.getOrNull(SemanticsProperties.TestTag) ?: "").startsWith("setup-equipment-") }
        assertTrue(tagged.size >= 6)
        val visual = tagged.sortedWith(compareBy({ it.boundsInRoot.top.toInt() / 10 }, { it.boundsInRoot.left }))
        assertEquals(visual.map { it.id }, tagged.map { it.id })
    }

    // ------------------------------------------------------------ contraste del texto de lectura

    @Test
    fun theReadingTextHasTheContrastWcagAsksForOnTheBlackOfThePage() {
        val page = WizardColors.background
        assertTrue("tinta cálida", contrastRatio(WizardColors.text, page) >= 7f)
        assertTrue("gris cálido", contrastRatio(WizardColors.textMuted, page) >= 7f)
        // El gris más tenue es texto de lectura (etiquetas, notas): 4,5:1 como mínimo.
        assertTrue("gris tenue ${contrastRatio(WizardColors.textFaint, page)}", contrastRatio(WizardColors.textFaint, page) >= 4.5f)
        // Y también sobre el disco de vidrio (blanco al 6 %) de los símbolos de día y del dial.
        val glass = blend(WizardColors.glassFill, page)
        assertTrue("gris tenue sobre el vidrio", contrastRatio(WizardColors.textFaint, glass) >= 4.5f)
        assertTrue("aviso de bloqueo", contrastRatio(WizardColors.danger.copy(alpha = 0.9f).let { blend(it, page) }, page) >= 7f)
    }

    /** [top] (con su opacidad) sobre [under]. */
    private fun blend(top: Color, under: Color): Color {
        val a = top.alpha
        return Color(top.red * a + under.red * (1f - a), top.green * a + under.green * (1f - a), top.blue * a + under.blue * (1f - a), 1f)
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
