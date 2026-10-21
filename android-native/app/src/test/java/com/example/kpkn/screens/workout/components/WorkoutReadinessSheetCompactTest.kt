package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.RecoveryBand
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryChannelSnapshot
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutReadinessSheetCompactTest {

    @get:Rule
    val rule = createComposeRule()

    private val sampleAuge = AugeSnapshot(
        dashboard = RecoveryDashboard(
            overallScore = 88,
            headline = "Listo",
            summary = "Recuperación normal",
            recommendation = "Entrena",
            confidenceLabel = "Alta",
            channels = listOf(
                RecoveryChannelSnapshot(
                    id = RecoveryChannelId.MUSCULAR,
                    title = "Músculos",
                    shortTitle = "Músculos",
                    score = 90,
                    band = RecoveryBand.NORMAL,
                    description = "Músculos",
                    action = "Volumen normal y buena ejecución.",
                ),
                RecoveryChannelSnapshot(
                    id = RecoveryChannelId.SYSTEM,
                    title = "Energía",
                    shortTitle = "Energía",
                    score = 85,
                    band = RecoveryBand.NORMAL,
                    description = "Energía",
                    action = "Buen día para intensidad y coordinación.",
                ),
                RecoveryChannelSnapshot(
                    id = RecoveryChannelId.STRUCTURE,
                    title = "Columna",
                    shortTitle = "Columna",
                    score = 80,
                    band = RecoveryBand.NORMAL,
                    description = "Columna",
                    action = "Carga estructural normal con buena técnica.",
                ),
            ),
        ),
    )

    @Test
    fun hojaDeEstadoNoTieneTextoSuperiorNiLasTresExplicaciones() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.size(360.dp, 880.dp)) {
                            WorkoutReadinessSheet(
                                showReadinessSheet = true,
                                gender = null,
                                sessionMuscleStartingBatteries = emptyMap(),
                                readinessNeuralStart = 85,
                                readinessMuscularStart = 90,
                                readinessSpinalStart = 80,
                                hazeState = HazeState(),
                                onSave = { _, _, _, _, _, _, _, _, _ -> },
                                augeSnapshot = sampleAuge,
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        // 12. Fuera el texto de arriba «Reporta tu estado antes de entrenar»
        rule.onNodeWithText("Reporta tu estado antes de entrenar", substring = true, ignoreCase = true).assertDoesNotExist()

        // 12.1. Fuera las tres líneas que explican cada canal
        rule.onNodeWithText("Volumen normal y buena ejecución", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Buen día para intensidad y coordinación", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Carga estructural normal con buena técnica", substring = true).assertDoesNotExist()

        // 12.1. La ayuda pasa a: «Toca un anillo para ajustarlo»
        rule.onNodeWithText("Toca un anillo para ajustarlo").assertExists()
        rule.onNodeWithText("Si no te representan los rings, presiona cada uno para ajustarlo", substring = true).assertDoesNotExist()
    }
}
