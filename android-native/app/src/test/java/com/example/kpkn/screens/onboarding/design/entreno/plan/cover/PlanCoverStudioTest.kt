package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.entreno.plan.CoverLayout
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCover
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las portadas con identidad propia (`CoverStudio`) pintan su texto en cartel (3:4) y en banda, sin errores, sobre cada estilo y fondo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanCoverStudioTest {

    @get:Rule
    val rule = createComposeRule()

    /** Una tarjeta de un programa «a medida» (con identidad). Los textos son únicos por tarjeta para encontrarlos. */
    private fun card(id: String, title: String, profile: TrainingGoalProfile?, badge: String? = null) = PlanCardModel(
        id = id,
        title = title,
        kicker = "Semana que se repite",
        blurb = "Un programa a medida.",
        profile = profile,
        daysLabel = "4 días",
        minutesLabel = "60 min",
        levelLabel = "Intermedio",
        badge = badge,
        coverSeed = 5,
    )

    @Test
    fun theStudioPosterPaintsItsTextOnEachStyleAndPalette() {
        rule.setContent {
            Column {
                // GRID con banda negra arriba (fondo de tinta), NUMERAL sobre papel y PLATES sobre negro.
                PlanCover(card("generated:hybrid:blocks", "Motor y fuerza", TrainingGoalProfile.STRENGTH_CARDIO, badge = "Para ti"), Modifier.size(260.dp, 347.dp), reduced = true)
                PlanCover(card("generated:powerbuilding:periodized", "Temporada powerbuilding", TrainingGoalProfile.POWERBUILDING), Modifier.size(260.dp, 347.dp), reduced = true)
                PlanCover(card("generated:strength-muscle", "Base sólida", TrainingGoalProfile.STRENGTH_MUSCLE), Modifier.size(260.dp, 347.dp), reduced = true)
            }
        }
        rule.onNodeWithText("Motor y fuerza").assertExists()
        rule.onNodeWithText("PARA TI").assertExists()
        rule.onNodeWithText("Temporada powerbuilding").assertExists()
        rule.onNodeWithText("Base sólida").assertExists()
    }

    @Test
    fun theStudioBannerPaintsItsTextToo() {
        rule.setContent {
            PlanCover(
                model = card("generated:hybrid:periodized", "Temporada híbrida", TrainingGoalProfile.STRENGTH_CARDIO),
                modifier = Modifier.size(360.dp, 300.dp),
                reduced = true,
                cornerRadius = 0.dp,
                topInset = 40.dp,
                layout = CoverLayout.BANNER,
            )
        }
        rule.onNodeWithText("Temporada híbrida").assertExists()
        rule.onNodeWithText("SEMANA QUE SE REPITE").assertExists()
        rule.onNodeWithText("4 días  ·  60 min  ·  Intermedio").assertExists()
    }
}
