package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.WizardColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La portada: la variación por semilla es determinista y cambia de verdad entre semillas; el texto se lee sobre el campo
 * (contraste medido, no supuesto); los datos de abajo salen de los tres textos del modelo; y la portada pinta su texto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanCoverTest {

    @get:Rule
    val rule = createComposeRule()

    private fun card(profile: TrainingGoalProfile? = TrainingGoalProfile.POWERLIFTING, seed: Int = 3, badge: String? = null) = PlanCardModel(
        id = "texas", title = "Texas Method", kicker = "Mark Rippetoe", blurb = "Un clásico de fuerza.",
        profile = profile, daysLabel = "3 días", minutesLabel = "75 min", levelLabel = "Intermedio",
        badge = badge, coverSeed = seed,
    )

    @Test
    fun theSameSeedAlwaysGivesTheSameVariant() {
        for (seed in listOf(0, 1, 7, -3, 12345, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals(coverVariantOf(seed), coverVariantOf(seed))
        }
    }

    @Test
    fun theVariantStaysInItsRanges() {
        for (seed in -50..200) {
            val v = coverVariantOf(seed)
            assertTrue("ángulo $seed ${v.angleDeg}", v.angleDeg in 28f..152f)
            assertTrue("dx $seed ${v.artDx}", v.artDx in -0.09f..0.09f)
            assertTrue("dy $seed ${v.artDy}", v.artDy in -0.05f..0.05f)
            assertTrue("escala $seed ${v.artScale}", v.artScale in 0.90f..1.04f)
            assertTrue("giro $seed ${v.tiltDeg}", v.tiltDeg in -5f..5f)
            assertTrue("marcas $seed ${v.decor}", v.decor in 0 until COVER_DECOR_STYLES)
        }
    }

    @Test
    fun differentSeedsGiveDifferentCovers() {
        val variants = (0 until 24).map { coverVariantOf(it) }.toSet()
        assertTrue("casi todas distintas: ${variants.size}", variants.size >= 22)
        // Y las tres familias de marcas de fondo aparecen.
        val styles = (0 until 60).map { coverVariantOf(it).decor }.toSet()
        assertEquals((0 until COVER_DECOR_STYLES).toSet(), styles)
        assertNotEquals(coverVariantOf(1).angleDeg, coverVariantOf(2).angleDeg)
    }

    @Test
    fun theTextReadsOnTheLightestPartOfTheField() {
        // El peor caso es el extremo teñido del degradado de la disciplina con el acento más claro.
        val accents = TrainingGoalProfile.entries.map { goalAccent(it) }.distinct() + goalAccent(null)
        for (accent in accents) {
            val field = CoverPalette.tint(accent)
            val primary = contrastRatio(WizardColors.text, field)
            val secondary = contrastRatio(WizardColors.textMuted, field)
            assertTrue("tinta sobre $accent: $primary", primary >= 7f)
            assertTrue("gris cálido sobre $accent: $secondary", secondary >= 4.5f)
        }
    }

    @Test
    fun theContrastRatioFollowsWcag() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.05f)
        assertEquals(1f, contrastRatio(Color.Gray, Color.Gray), 0.001f)
        // Simétrica: no importa cuál de los dos es el texto.
        assertEquals(contrastRatio(Color.Black, Color.Red), contrastRatio(Color.Red, Color.Black), 0.0001f)
    }

    @Test
    fun theTitleShrinksUntilItsLongestWordFits() {
        // Si cabe al tamaño máximo, no se toca.
        assertEquals(24f, fitTitleSp(24f, 14f) { true }, 0f)
        // Una palabra que mide 10 px por punto cabe en 100 px a 10 sp o menos: baja de punto en punto hasta ahí.
        assertEquals(10f, fitTitleSp(24f, 8f) { it * 10f <= 100f }, 0f)
        // Si ni al mínimo cabe, se queda en el mínimo (y entonces sí se parte la palabra).
        assertEquals(14f, fitTitleSp(24f, 14f) { false }, 0f)
    }

    @Test
    fun theFactsFlowIntoWholeLinesWithoutADanglingDot() {
        val facts = listOf("4 días", "60 min", "Intermedio")
        val sep = "  ·  "
        // Todo en una línea si cabe.
        assertEquals("4 días  ·  60 min  ·  Intermedio", flowFacts(facts, sep) { true })
        // Si no caben los tres, saltan ENTRE datos: nunca un punto colgando al final de una línea.
        assertEquals("4 días  ·  60 min\nIntermedio", flowFacts(facts, sep) { it.length <= 20 })
        assertEquals("4 días\n60 min\nIntermedio", flowFacts(facts, sep) { it.length <= 10 })
        // Un dato que no cabe ni solo va solo en su línea.
        assertEquals("4 días\n60 min\nIntermedio", flowFacts(facts, sep) { false })
        assertEquals("", flowFacts(emptyList(), sep) { true })
    }

    @Test
    fun theThreeFactsAreJoinedAndBlanksSkipped() {
        assertEquals("3 días  ·  75 min  ·  Intermedio", coverFacts(card()))
        assertEquals("3 días  ·  Intermedio", coverFacts(card().copy(minutesLabel = " ")))
        assertEquals("", coverFacts(card().copy(daysLabel = "", minutesLabel = "", levelLabel = "")))
    }

    @Test
    fun everyDisciplineAndTheGeneralOnesHaveAnAccent() {
        for (profile in TrainingGoalProfile.entries) assertTrue(goalAccent(profile).alpha == 1f)
        // Los generales y la ausencia de perfil comparten la tinta con el detalle verde.
        assertEquals(goalAccent(null), goalAccent(TrainingGoalProfile.STRENGTH_MUSCLE))
        assertEquals(goalAccent(null), goalAccent(TrainingGoalProfile.FUNCTIONAL_HEALTH))
        // Los acentos de disciplina del brief.
        assertEquals(Color(0xFFF49A6E), goalAccent(TrainingGoalProfile.POWERLIFTING))
        assertEquals(Color(0xFFF7CF73), goalAccent(TrainingGoalProfile.POWERBUILDING))
        assertEquals(Color(0xFFC9B8FF), goalAccent(TrainingGoalProfile.BODYBUILDING))
        assertEquals(Color(0xFF43D18C), goalAccent(TrainingGoalProfile.CALISTHENICS))
        assertEquals(Color(0xFF8FB2FF), goalAccent(TrainingGoalProfile.WEIGHTLIFTING))
        assertEquals(Color(0xFFF49A6E), goalAccent(TrainingGoalProfile.ARMWRESTLING))
        assertEquals(Color(0xFFF7CF73), goalAccent(TrainingGoalProfile.STRONGMAN))
    }

    @Test
    fun thePosterPaintsTitleKickerBadgeAndFacts() {
        rule.setContent {
            PlanCover(card(badge = "Recomendado"), Modifier.size(260.dp, 347.dp), reduced = true)
        }
        rule.onNodeWithText("Texas Method").assertExists()
        rule.onNodeWithText("MARK RIPPETOE").assertExists()
        rule.onNodeWithText("RECOMENDADO").assertExists()
        rule.onNodeWithText("3 días  ·  75 min  ·  Intermedio").assertExists()
    }

    @Test
    fun aLongTitleStillPaintsAllItsWords() {
        rule.setContent {
            PlanCover(card().copy(title = "Powerbuilding de autor", kicker = "Hecho a tu medida"), Modifier.size(237.dp, 316.dp), reduced = true)
        }
        rule.onNodeWithText("Powerbuilding de autor").assertExists()
        rule.onNodeWithText("HECHO A TU MEDIDA").assertExists()
    }

    @Test
    fun theBannerPaintsTheSameTexts() {
        rule.setContent {
            PlanCover(
                model = card(),
                modifier = Modifier.size(360.dp, 300.dp),
                reduced = true,
                cornerRadius = 0.dp,
                topInset = 40.dp,
                layout = CoverLayout.BANNER,
            )
        }
        rule.onNodeWithText("Texas Method").assertExists()
        rule.onNodeWithText("MARK RIPPETOE").assertExists()
        rule.onNodeWithText("3 días  ·  75 min  ·  Intermedio").assertExists()
    }

    @Test
    fun aCoverWithoutProfileStillPaints() {
        rule.setContent { PlanCover(card(profile = null), Modifier.size(200.dp, 267.dp), reduced = true) }
        rule.onNodeWithText("Texas Method").assertExists()
    }
}
