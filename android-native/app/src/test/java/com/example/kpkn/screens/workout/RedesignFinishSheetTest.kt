package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
class RedesignFinishSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun finishSummaryAggregatesEveryRecordedCardioBlock() {
        val cardioExercises = listOf(
            CompletedExercise(
                exerciseId = "run",
                exerciseName = "Cinta",
                cardioDetails = CardioDetails(type = CardioType.TREADMILL),
                sets = listOf(CompletedSet("run-set", timeSeconds = 900, distanceKm = 1.2, avgHeartRate = 140, calories = 180.0)),
            ),
            CompletedExercise(
                exerciseId = "bike",
                exerciseName = "Bicicleta",
                cardioDetails = CardioDetails(type = CardioType.BIKE_OUTDOOR),
                sets = listOf(CompletedSet("bike-set", timeSeconds = 1500, distanceKm = 1.3, avgHeartRate = 160, calories = 250.0)),
            ),
        )
        val model = buildFinishModel(
            name = "Cardio mixto",
            completedExercises = cardioExercises,
            durationMinutes = 40,
            effort = null,
            presentation = FinishPresentationContext(),
            lifts = emptyList(),
            plannedSets = null,
            targetMinutes = null,
        )
        val cardio = requireNotNull(model.cardio)
        assertEquals(2, cardio.blockCount)
        assertEquals("Cinta + Bicicleta", cardio.type)
        assertEquals(40, cardio.minutes)
        assertEquals(2.5, cardio.km ?: 0.0, 0.001)
        // Cada promedio de pulso pesa según el tiempo de cobertura registrado.
        assertEquals(152, cardio.heartRate)
        assertEquals(430, cardio.kcal)
    }
    @Test fun notesAndEffortUseIndependentPanelsAndPersistOnSave() {
        var saved: Pair<String, Double?>? = null
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    RecordedSessionFinishSheet("QA cierre", listOf(CompletedExercise("e", "Press",
                        sets = listOf(CompletedSet("s", weight = 50.0, reps = 5, rir = 1)))),
                        10, "", false, onNotes = {}, onAlbum = {},
                        onConfirm = { note, effort -> saved = note to effort }, onDismiss = {})
                }
            }
        }
        // Sin pestañas: todo el resumen a la vista y las anotaciones en un panel que se abre con «Notas».
        rule.onNodeWithText("Progreso").assertDoesNotExist()
        rule.onNodeWithText("HITOS").assertDoesNotExist()
        rule.onNodeWithText("Compartir al guardar").assertIsDisplayed()
        rule.onNodeWithTag("finish-notes-open").performClick()
        rule.onNodeWithTag("finish-effort-scale").assertDoesNotExist()
        rule.onNodeWithTag("finish-notes").performTextInput("QA observacion original")
        rule.onNodeWithTag("finish-notes-done").performClick()
        rule.onNodeWithTag("finish-rate-open").performClick()
        rule.onNodeWithTag("finish-effort-scale").assertIsDisplayed()
        rule.onNodeWithContentDescription("Esfuerzo 7").performClick()
        rule.onNodeWithTag("finish-notes-done").performClick()
        rule.onNodeWithTag("finish-save").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("QA observacion original" to 7.0, saved) }
    }

    @Test fun cardioDurationDisplaysProperUnitForShortAndLongDurations() {
        // 30 seconds
        val model30s = buildFinishModel(
            name = "Cardio 30s",
            completedExercises = listOf(CompletedExercise("run", "Cinta", cardioDetails = CardioDetails(type = CardioType.TREADMILL), sets = listOf(CompletedSet("s1", timeSeconds = 30)))),
            durationMinutes = 0, effort = null, presentation = FinishPresentationContext(), lifts = emptyList(), plannedSets = null, targetMinutes = null,
        )
        val cardio30s = requireNotNull(model30s.cardio)
        assertEquals(30, cardio30s.seconds)
        assertNull(cardio30s.minutes)
        assertEquals("30" to "s", cardio30s.durationFormatted)

        // 60 seconds
        val model60s = buildFinishModel(
            name = "Cardio 60s",
            completedExercises = listOf(CompletedExercise("run", "Cinta", cardioDetails = CardioDetails(type = CardioType.TREADMILL), sets = listOf(CompletedSet("s2", timeSeconds = 60)))),
            durationMinutes = 1, effort = null, presentation = FinishPresentationContext(), lifts = emptyList(), plannedSets = null, targetMinutes = null,
        )
        val cardio60s = requireNotNull(model60s.cardio)
        assertEquals(60, cardio60s.seconds)
        assertEquals(1, cardio60s.minutes)
        assertEquals("1" to "min", cardio60s.durationFormatted)

        // 90 seconds
        val model90s = buildFinishModel(
            name = "Cardio 90s",
            completedExercises = listOf(CompletedExercise("run", "Cinta", cardioDetails = CardioDetails(type = CardioType.TREADMILL), sets = listOf(CompletedSet("s3", timeSeconds = 90)))),
            durationMinutes = 1, effort = null, presentation = FinishPresentationContext(), lifts = emptyList(), plannedSets = null, targetMinutes = null,
        )
        val cardio90s = requireNotNull(model90s.cardio)
        assertEquals(90, cardio90s.seconds)
        assertEquals(1, cardio90s.minutes)
        assertEquals("1" to "min", cardio90s.durationFormatted)

        // No time recorded
        val modelNoTime = buildFinishModel(
            name = "Cardio NoTime",
            completedExercises = listOf(CompletedExercise("run", "Cinta", cardioDetails = CardioDetails(type = CardioType.TREADMILL), sets = listOf(CompletedSet("s4", timeSeconds = null, distanceKm = 1.0)))),
            durationMinutes = 0, effort = null, presentation = FinishPresentationContext(), lifts = emptyList(), plannedSets = null, targetMinutes = null,
        )
        val cardioNoTime = requireNotNull(modelNoTime.cardio)
        assertNull(cardioNoTime.seconds)
        assertNull(cardioNoTime.minutes)
        assertNull(cardioNoTime.durationFormatted)
    }

    @Test fun subMinuteDurationRemainsVisibleInTheSummary() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    RecordedSessionFinishSheet(
                        name = "Sesión corta",
                        completedExercises = listOf(CompletedExercise("e", "Press", sets = listOf(CompletedSet("s", weight = 20.0, reps = 1)))),
                        durationMinutes = 0,
                        durationSeconds = 30,
                        initialNotes = "",
                        saving = false,
                        onNotes = {}, onAlbum = {}, onConfirm = { _, _ -> }, onDismiss = {},
                    )
                }
            }
        }
        rule.onNodeWithText("30 s", substring = true).assertIsDisplayed()
    }

    @Test fun cardioOnlySummaryShowsCardioMetricsWithoutStrengthVolume() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    RecordedSessionFinishSheet(
                        name = "Cardio",
                        completedExercises = listOf(CompletedExercise(
                            exerciseId = "run", exerciseName = "Cinta",
                            cardioDetails = CardioDetails(type = CardioType.TREADMILL),
                            sets = listOf(CompletedSet("c1", timeSeconds = 300, distanceKm = 1.2, avgHeartRate = 142, calories = 80.0)),
                        )),
                        durationMinutes = 5,
                        initialNotes = "",
                        saving = false,
                        onNotes = {}, onAlbum = {}, onConfirm = { _, _ -> }, onDismiss = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("finish-cardio").assertIsDisplayed()
        rule.onNodeWithTag("finish-data").assertDoesNotExist()
        rule.onNodeWithTag("finish-volume").assertDoesNotExist()
        rule.onNodeWithText("cardio", substring = true).assertIsDisplayed()
    }

    @Test fun emptySessionOffersReturnOrConfirmedDiscard() {
        var discarded = false
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    RecordedSessionFinishSheet(
                        name = "Sesión vacía", completedExercises = emptyList(), durationMinutes = 0,
                        initialNotes = "", saving = false, onNotes = {}, onAlbum = {},
                        onConfirm = { _, _ -> error("La sesión vacía no se debe guardar") },
                        onDismiss = {}, onDiscardEmpty = { discarded = true },
                    )
                }
            }
        }
        rule.onNodeWithText("Registra al menos una serie para terminar o abandona sin guardar.").assertIsDisplayed()
        rule.onNodeWithTag("finish-save").assertIsNotEnabled()
        rule.onNodeWithTag("finish-back").assertIsDisplayed()
        rule.onNodeWithTag("finish-discard-empty").performClick()
        rule.onNodeWithText("¿Descartar esta sesión?").assertIsDisplayed()
        rule.onNodeWithText("Descartar").performClick()
        rule.runOnIdle { assertTrue(discarded) }
    }
}
