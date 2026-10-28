package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.domain.training.ExerciseProgressChoice
import com.example.kpkn.domain.training.NutritionGoalProgress
import com.example.kpkn.domain.training.StarredExerciseProgress
import com.example.kpkn.screens.home.ConceptosClaveScreen
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.profile.NutritionGoalModule
import com.example.kpkn.screens.profile.ProfileArchiveTiles
import com.example.kpkn.screens.profile.ProfileHero
import com.example.kpkn.screens.profile.ProfileProgressModule
import com.example.kpkn.screens.profile.ProfileSection
import com.example.kpkn.screens.profile.ProfileTechniqueCard
import com.example.kpkn.screens.profile.ProfileTechniqueUiState
import com.example.kpkn.screens.profile.StarredExercisesModule
import com.example.kpkn.screens.technique.hub.TechniqueHubSamples
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Hoja de render de Perfil y Conceptos clave (rediseño v1, `docs/design/profile-concepts-v1`). Solo corre con
 * KPKN_RENDER_DIR: deja un PNG por estado para compararlo con el prototipo aprobado, al 100 % y al 130 % de letra.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h2400dp-xxhdpi", application = android.app.Application::class)
class ProfileConceptsRenderSheetTest {
    @get:Rule val rule = createComposeRule()
    private var host: View? = null

    @Before fun onlyWhenAsked() {
        assumeTrue("La hoja requiere KPKN_RENDER_DIR", !System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
    }

    private fun show(scale: Float, phone: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            host = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    // Teléfono: 412 × 915 dp, como un Pixel; página: todo el alto que ocupe el contenido.
                    val frame = if (phone) Modifier.size(412.dp, 915.dp) else Modifier.width(412.dp)
                    Box(frame.background(Color.Black).testTag("sheet")) { content() }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(1_000L)
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("sheet", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val view = requireNotNull(host)
        val complete = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(complete).apply { drawColor(android.graphics.Color.BLACK) }) }
        val left = bounds.left.toInt().coerceIn(0, complete.width - 1)
        val top = bounds.top.toInt().coerceIn(0, complete.height - 1)
        val crop = Bitmap.createBitmap(
            complete, left, top,
            bounds.width.toInt().coerceIn(1, complete.width - left), bounds.height.toInt().coerceIn(1, complete.height - top),
        )
        val directory = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ─── Perfil ──────────────────────────────────────────────────────────────────────────────────────────────────────

    private val settings = Settings(
        username = "Alex Romero",
        userVitals = UserVitals(age = 29, weight = 78.4, height = 178.0, gender = Gender.MALE),
    )
    private val starred = listOf(
        StarredExerciseProgress("squat", "Sentadilla trasera", goal1RM = 160.0, bestEstimated1RM = 142.5, sessions = 18),
        StarredExerciseProgress("bench", "Press de banca", goal1RM = 110.0, bestEstimated1RM = 101.0, sessions = 22),
        StarredExerciseProgress("deadlift", "Peso muerto convencional", goal1RM = null, bestEstimated1RM = 185.0, sessions = 9),
    )
    private val progress = listOf(
        ExerciseProgressChoice("bench", "Press de banca", "Barra", 22),
        ExerciseProgressChoice("squat", "Sentadilla trasera", "Barra", 18),
        ExerciseProgressChoice("row", "Remo con barra", "Barra", 14),
    )

    @Composable private fun profilePage(populated: Boolean) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(60.dp))
            ProfileHero(if (populated) settings else Settings(), null, {}, {})
            ProfileSection("Ejercicios estrella", KpknLineIcons.Star) {
                StarredExercisesModule(if (populated) starred else emptyList()) {}
            }
            ProfileSection("Progreso nutricional") {
                NutritionGoalModule(if (populated) NutritionGoalProgress(GoalMetric.WEIGHT, 82.0, 78.4, 75.0, 51) else null)
            }
            ProfileSection("Progreso", KpknLineIcons.Layers) {
                ProfileProgressModule({}, {}, if (populated) progress else emptyList())
            }
            ProfileSection("Técnica en vídeo", KpknLineIcons.Camera) {
                ProfileTechniqueCard(
                    if (populated) ProfileTechniqueUiState(
                        TechniqueHubSamples.recent.take(3), 12, TechniqueHubSamples.recent[1].analysis.recordedAtMs,
                        TechniqueHubSamples.recent[1].analysis,
                    ) else ProfileTechniqueUiState(),
                    {},
                )
            }
            ProfileSection("Tu archivo") { ProfileArchiveTiles({}, {}) }
            Spacer(Modifier.height(32.dp))
        }
    }

    @Test fun profileData100() { show(1f, phone = false) { profilePage(true) }; capture("perfil_datos_100") }
    @Test fun profileData130() { show(1.3f, phone = false) { profilePage(true) }; capture("perfil_datos_130") }
    @Test fun profileEmpty100() { show(1f, phone = false) { profilePage(false) }; capture("perfil_vacio_100") }

    // ─── Conceptos clave ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun library100() { show(1f, phone = true) { ConceptosClaveScreen(null) {} }; capture("conceptos_biblioteca_100") }
    @Test fun library130() { show(1.3f, phone = true) { ConceptosClaveScreen(null) {} }; capture("conceptos_biblioteca_130") }

    @Test fun libraryTopics() {
        show(1f, phone = true) { ConceptosClaveScreen(null) {} }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("concept_topic_SUPPLEMENTS"))
        capture("conceptos_temas_100")
    }

    @Test fun search() {
        show(1f, phone = true) { ConceptosClaveScreen(null) {} }
        rule.onNodeWithTag("concept_search_field").performTextInput("creatina")
        capture("conceptos_busqueda_100")
    }

    @Test fun topic() {
        show(1f, phone = true) { ConceptosClaveScreen(null) {} }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("concept_topic_SUPPLEMENTS"))
        rule.onNodeWithTag("concept_topic_SUPPLEMENTS").performClick()
        capture("conceptos_tema_suplementos_100")
    }

    @Test fun reading100() { show(1f, phone = true) { ConceptosClaveScreen("creatina") {} }; capture("conceptos_lectura_100") }
    @Test fun reading130() { show(1.3f, phone = true) { ConceptosClaveScreen("creatina") {} }; capture("conceptos_lectura_130") }

    @Test fun readingEnd() {
        show(1f, phone = true) { ConceptosClaveScreen("creatina") {} }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("concept_practice_creatina"))
        capture("conceptos_lectura_practica_100")
    }

    @Test fun readingStudy() { show(1f, phone = true) { ConceptosClaveScreen("estudio-volumen-hipertrofia") {} }; capture("conceptos_lectura_estudio_100") }
}
