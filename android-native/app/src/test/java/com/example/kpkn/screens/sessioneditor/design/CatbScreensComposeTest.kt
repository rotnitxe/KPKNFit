package com.example.kpkn.screens.sessioneditor.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.*
import com.example.kpkn.testsupport.MotCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** E7/F1/F2: cuerpos reales, clicks nativos y medidas físicas, con datos del catálogo aprobado de MOT. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class CatbScreensComposeTest {
    @get:Rule val rule = createComposeRule()
    private val state = mutableStateOf(CustomExerciseEditorState())
    private val scene = mutableStateOf("editor")
    private val scale = mutableStateOf(1f)
    private var view: View? = null
    private var density = 1f
    private var saved = 0
    private var cancelled = 0
    private var device = 0
    private var privatePhoto = 0
    private var selected = 0
    private var usedCandidate: ReferenceCandidate? = null

    private fun preview(draft: CustomExerciseEditorState) = draft.copy(preview = SmartExerciseCreator.preview(
        SmartCreateRequest(name = draft.name, implementoId = draft.implement.orEmpty(), estacionId = draft.station,
            lateralidadId = draft.laterality, description = draft.description,
            existingId = "custom:catb-test", musclesOverride = draft.muscles), MotCatalog.legacyDefaultCatalog, MotCatalog.catalog))

    private fun mount(font: Float, name: String = "sentadilla barra baja") {
        state.value = preview(CustomExerciseEditorState(name = name))
        scale.value = font
        rule.setContent {
            val base = LocalDensity.current
            density = base.density
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale.value), LocalWizardReducedMotion provides true) {
                EditorOverlayTestHost {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().testTag("catb-root")) {
                        if (scene.value == "editor") CustomExerciseEditorContent(state.value, editing = false,
                            onEdit = { change -> state.value = preview(change(state.value)).copy(reviewed = false) },
                            onReview = { state.value = state.value.copy(reviewed = it) },
                            onCover = { state.value = state.value.copy(cover = it) },
                            onSave = { saved++; state.value = state.value.copy(error = if (saved == 1) "No se pudo guardar; tu borrador sigue aquí." else null) },
                            onCancel = { cancelled++ }, onUseCandidate = { usedCandidate = it },
                            onPickDevice = { device++ }, onPickPrivate = { privatePhoto++ },
                            referenceCover = customExerciseReferenceCover(state.value.preview) { _, _ -> true })
                        else {
                            val info = state.value.preview!!.exercise
                            val model = customExerciseDetailModel(info).copy(
                                context = if (scene.value == "catalog") ExerciseFichaContext.CATALOG else ExerciseFichaContext.SESSION,
                                customCover = CustomCoverModel(unavailableReason = "La foto original ya no está disponible. Se muestra la ilustración."))
                            ExerciseFichaContent(model, onDismiss = {}, onToggleSelection = { selected++ }, onSelectOption = { _, _ -> }, onImplementationSettled = {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun target(tag: String, scroll: Boolean = false) {
        val node = rule.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("$tag: $bounds", bounds.width.value >= 48f && bounds.height.value >= 48f)
        val screen = rule.onNodeWithTag("catb-root").getUnclippedBoundsInRoot()
        assertTrue("$tag fuera de la pantalla: $bounds", bounds.left >= screen.left && bounds.right <= screen.right)
    }

    private fun fullText(label: String, ancestor: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(ancestor)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val lines = 0 until layout.lineCount
        val detail = "${layout.size}, párrafo=${layout.multiParagraph.width}, líneas=${lines.map { layout.getLineLeft(it) to layout.getLineRight(it) }}"
        println("CATB $label: $detail")
        assertEquals(label, layout.layoutInput.text.text)
        assertFalse(detail, layout.didOverflowHeight)
        assertEquals(detail, label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        assertTrue(detail, lines.none { layout.isLineEllipsized(it) })
        assertTrue(detail, lines.all { layout.getLineLeft(it) >= -1f && layout.getLineRight(it) <= layout.size.width + 1f })
    }

    private fun geometry(font: Float, height: Int) {
        mount(font)
        target("custom-exercise-save")
        target("custom-exercise-cancel")
        fullText("Crear ejercicio", "custom-exercise-save")
        save("e7_${height}_${(font * 100).toInt()}")
        mapOf("custom-cover-device" to "Añadir foto", "custom-cover-private" to "Elegir foto privada",
            "custom-cover-reference" to "Usar la del parecido", "custom-cover-pattern" to "Usar ilustración del patrón").forEach { (tag, text) ->
            target(tag, scroll = true)
            fullText(text, tag)
        }
        target("custom-exercise-review", scroll = true)
        fullText("He revisado material, patrón y músculos", "custom-exercise-review")
        save("e7_revision_${height}_${(font * 100).toInt()}")
        for (ficha in listOf("catalog", "session")) {
            rule.runOnIdle { scene.value = ficha }
            rule.waitForIdle()
            save("${if (ficha == "catalog") "f1" else "f2"}_${height}_${(font * 100).toInt()}")
            rule.onNodeWithText("Imagen técnica del catálogo").assertDoesNotExist()
            rule.onNodeWithTag("exercise-ficha-image-provenance").assertExists()
            rule.onNodeWithTag("exercise-ficha-image-abstention").assertExists()
            val info = state.value.preview!!.exercise
            assertTrue(info.setupCues.orEmpty().isNotEmpty())
            assertTrue(info.executionCues.orEmpty().isNotEmpty())
            info.setupCues.orEmpty().forEach { cue -> rule.onNode(hasText(cue) and hasAnyAncestor(hasTestTag("exercise-ficha-setup"))).assertExists() }
            info.executionCues.orEmpty().forEach { cue -> rule.onNode(hasText(cue) and hasAnyAncestor(hasTestTag("exercise-ficha-execution"))).assertExists() }
            rule.onNodeWithTag("exercise-ficha-setup").performScrollTo().assertIsDisplayed()
            fullText(info.setupCues!!.first(), "exercise-ficha-setup")
            save("${if (ficha == "catalog") "f1" else "f2"}_detalle_${height}_${(font * 100).toInt()}")
            rule.onNodeWithText("Mejor serie", substring = true).assertDoesNotExist()
            if (ficha == "catalog") {
                target("exercise-ficha-select")
                fullText("Añadir", "exercise-ficha-select")
                rule.onNodeWithTag("exercise-ficha-select").performClick()
                assertEquals(1, selected)
            } else rule.onNodeWithTag("exercise-ficha-select").assertDoesNotExist()
            target("exercise-ficha-close")
        }
    }

    @Test fun e7F1F2En880Al100() = geometry(1f, 880)
    @Test fun e7F1F2En880Al130() = geometry(1.3f, 880)
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun e7F1F2En740Al100() = geometry(1f, 740)
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun e7F1F2En740Al130() = geometry(1.3f, 740)

    @Test fun revisionErrorReintentoYCancelarConservanLosCallbacksNativos() {
        mount(1.3f)
        rule.onNodeWithTag("custom-exercise-save").assertIsNotEnabled()
        rule.onNodeWithTag("custom-exercise-review").performScrollTo().performClick()
        rule.onNodeWithTag("custom-exercise-save").performClick()
        rule.onNodeWithTag("custom-exercise-save-error").assertExists()
        fullText("Reintentar", "custom-exercise-save")
        assertEquals("sentadilla barra baja", state.value.name)
        rule.onNodeWithTag("custom-exercise-save").performClick()
        assertEquals(2, saved)
        rule.onNodeWithTag("custom-exercise-cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test fun abstencionNoOfreceReferenciasNiDescripcionInventadaYPuedeElegirFuentesDeFoto() {
        mount(1f, "XQZ 123")
        assertTrue(state.value.candidates.isEmpty())
        assertNull(state.value.preview!!.exercise.description)
        rule.onNodeWithText("Usar").assertDoesNotExist()
        rule.onNodeWithTag("custom-reference-reason").assertExists()
        rule.onNodeWithTag("custom-cover-device").performScrollTo().performClick()
        rule.onNodeWithTag("custom-cover-private").performScrollTo().performClick()
        rule.onNodeWithTag("custom-cover-pattern").performScrollTo().performClick()
        assertEquals(1, device)
        assertEquals(1, privatePhoto)
        assertEquals(CustomExerciseCoverDraft.Pattern, state.value.cover)
        assertEquals(0, saved)
    }

    @Test fun usarReferenciaExistenteYLasCuatroPortadasNoGuardaElBorrador() {
        mount(1f)
        val candidate = state.value.candidates.first()
        rule.onNode(hasText("Usar") and hasAnyAncestor(hasTestTag("custom-reference-${candidate.definitionId}")))
            .performScrollTo().performClick()
        assertEquals(candidate, usedCandidate)
        rule.onNodeWithTag("custom-cover-reference").performScrollTo().performClick()
        assertTrue(state.value.cover is CustomExerciseCoverDraft.Reference)
        rule.onNodeWithTag("custom-cover-device").performScrollTo().performClick()
        rule.onNodeWithTag("custom-cover-private").performScrollTo().performClick()
        rule.onNodeWithTag("custom-cover-pattern").performScrollTo().performClick()
        assertEquals(1, device)
        assertEquals(1, privatePhoto)
        assertEquals(CustomExerciseCoverDraft.Pattern, state.value.cover)
        assertEquals(0, saved)
    }

    @Test fun selectoresDeMaterialEstacionYLateralidadConservanValoresDelMotor() {
        mount(1.3f, "XQZ 123")
        rule.onNodeWithTag("custom-option-Material").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText(SmartExerciseCreator.implementoLabel("barbell")).performClick()
        assertEquals("barbell", state.value.implement)
        val station = SmartExerciseCreator.ESTACION_IDS.first()
        rule.onNodeWithTag("custom-option-Estación").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText(SmartExerciseCreator.estacionLabel(station)).performClick()
        assertEquals(station, state.value.station)
        val side = SmartExerciseCreator.LATERALIDAD_IDS.first()
        rule.onNodeWithTag("custom-option-Lateralidad").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText(SmartExerciseCreator.lateralidadLabel(side)).performClick()
        assertEquals(side, state.value.laterality)
        assertEquals(0, saved)
    }

    private fun save(name: String) {
        val output = System.getenv("KPKN_RENDER_DIR") ?: return
        val source = view!!
        val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { source.draw(Canvas(bitmap)) }
        val file = File(output, "catb_$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
