package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.training.importer.ImportReadingProgress
import com.example.kpkn.domain.training.importer.ImportReadingStage
import com.example.kpkn.domain.training.importer.ImportSourceEvidence
import com.example.kpkn.domain.training.importer.ImportSourceKind
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportRecoveryPagesTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun emptyPageShowsReaderCountsAndExpandsTheTextBeforeOfferingFreshStart() {
        var startedOver = false
        val evidence = ImportSourceEvidence(
            kind = ImportSourceKind.FILE,
            displayName = "plan.csv",
            sourceText = "Hoja 1\nNotas sin ejercicios",
            sheetCount = 2,
            rowCount = 48,
        )

        rule.setContent {
            ImportEmptyPage(
                evidence = evidence,
                imported = ImportedProgramDraft("Plan vacío", emptyList()),
                onEditText = {},
                onChooseSource = {},
                onStartOver = { startedOver = true },
            )
        }

        rule.onNodeWithText("48").assertExists()
        rule.onNodeWithTag("import-empty-extracted-text").assertDoesNotExist()
        rule.onNodeWithTag("import-empty-show-text").performClick()
        rule.onNodeWithTag("import-empty-extracted-text").assertTextContains("Notas sin ejercicios", substring = true)
        rule.onNodeWithTag("import-empty-start-over").performClick()
        assertEquals(true, startedOver)
    }

    @Test
    fun emptyPageMarksTextThatWasCutToFitThePreview() {
        val evidence = ImportSourceEvidence(
            kind = ImportSourceKind.FILE,
            sourceText = "x".repeat(2_501),
        )
        rule.setContent {
            ImportEmptyPage(
                evidence = evidence,
                imported = null,
                onEditText = {},
                onChooseSource = {},
                onStartOver = {},
            )
        }

        rule.onNodeWithTag("import-empty-show-text").performClick()
        rule.onNodeWithTag("import-empty-extracted-text").assertTextContains("…", substring = true)
    }

    @Test
    fun finalReviewOffersACatalogRetryWithoutDiscardingTheDraft() {
        var retried = false
        rule.setContent {
            ImportReviewIntro(
                imported = ImportedProgramDraft("Plan de prueba", emptyList()),
                muscleDistribution = ImportMuscleDistribution(
                    weekIndex = 1,
                    groups = listOf(ImportMuscleSeries("Pecho", 4)),
                    unassignedExercises = 1,
                ),
                requiresPro = false,
                issueCount = 0,
                pendingDecisions = 0,
                weekdays = emptySet(),
                reviewedDayIds = emptySet(),
                pendingWarnings = 0,
                catalogUnavailable = true,
                catalogRetrying = false,
                onReviewMatches = {},
                onReviewDays = {},
                onReviewSessions = {},
                onWarnings = {},
                onPlanLimits = {},
                onBackToSessions = {},
                onRetryCatalog = { retried = true },
            )
        }

        rule.onNodeWithText("Catálogo no disponible").assertExists()
        rule.onNodeWithText("El borrador sigue intacto").assertExists()
        rule.onNodeWithTag("import-review-muscle-distribution").assertExists()
        rule.onNodeWithText("Pecho 4").assertExists()
        rule.onNodeWithText("1 ejercicio propio o sin grupo quedó fuera.", substring = true).assertExists()
        rule.onNodeWithTag("import-review-retry-catalog").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun errorPageUsesAStableCauseAndKeepsBothRecoveryRoutesVisible() {
        rule.setContent {
            ImportErrorPage(
                message = "No pudimos abrir este PDF.",
                evidence = ImportSourceEvidence(kind = ImportSourceKind.FILE, displayName = "plan.pdf", mediaType = "application/pdf", pageCount = 12),
                readingProgress = ImportReadingProgress(stage = ImportReadingStage.DOCUMENT, currentPage = 1, totalPages = 12),
                onShowText = {},
                onRetryText = {},
                onPasteText = {},
                onChooseSource = {},
            )
        }

        rule.onNodeWithText("PDF_NO_LEGIBLE").assertExists()
        rule.onNodeWithText("No se modificó ningún borrador.").assertExists()
        rule.onNodeWithTag("import-error-copy").assertExists()
        rule.onNodeWithTag("import-error-choose-source").assertExists()
        rule.onNodeWithTag("import-error-paste-text").assertExists()
    }

    @Test
    fun failureMappingDoesNotExposeUnknownExceptionTextAsACause() {
        assertEquals("FUENTE_DEMASIADO_GRANDE", importFailurePresentation("El archivo es demasiado grande").code)
        assertEquals("PDF_NO_LEGIBLE", importFailurePresentation("No pudimos abrir este PDF.").code)
        assertEquals("PDF_ENCRYPTED", importFailurePresentation("El PDF está protegido con contraseña.").code)
        assertEquals("PDF_ENCRYPTED", importFailurePresentation("The encrypted PDF requires a password.").code)
        assertEquals("LECTURA_DEL_DOCUMENTO", importFailurePresentation("java.io.IOException: /private/path").code)
        assertEquals(
            "La lectura se detuvo antes de analizar el contenido. Prueba con otra fuente o pega el texto.",
            importFailurePresentation("java.io.IOException: /private/path").detail,
        )
    }
}
