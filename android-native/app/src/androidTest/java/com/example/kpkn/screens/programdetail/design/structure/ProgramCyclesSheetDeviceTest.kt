package com.example.kpkn.screens.programdetail.design.structure

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.LoopType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgramCyclesSheetDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cycleSheetUsesSpanishCycleLabels() {
        composeRule.setContent {
            MaterialTheme {
                ProgramCyclesSheet(
                    model = ProgramCyclesModel(
                        currentCycle = 2,
                        loops = listOf(ProgramCycleLoop("deload", "Descarga", LoopType.DELOAD, 4, false)),
                        weeks = listOf(
                            ProgramCycleWeek(
                                occurrenceId = "deload-week-1",
                                loopTitle = "Descarga",
                                loopType = LoopType.DELOAD,
                                weekName = "S1",
                                cycle = 2,
                                originCycle = null,
                                dayLabel = "lunes",
                                countdown = "en 3 días",
                                blockId = "block-1",
                                weekId = "week-1",
                            ),
                        ),
                        legacyEventCount = 0,
                        collisionLines = emptyList(),
                    ),
                    onDismiss = {},
                    onSaveLoop = {},
                    onLoopAction = { _, _ -> },
                    onAddTemplate = {},
                    onMigrateLegacy = {},
                    onPostponeWeek = {},
                    onCancelWeek = {},
                    onViewWeek = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Loops").assertIsDisplayed()
        composeRule.onNodeWithText("TUS LOOPS").assertIsDisplayed()
        composeRule.onNodeWithText("Nuevo loop").assertIsDisplayed()
        composeRule.onNodeWithText("SEMANAS DE LOOP").assertIsDisplayed()
        composeRule.onNodeWithText("LOOPS LISTOS").assertIsDisplayed()
    }
}
