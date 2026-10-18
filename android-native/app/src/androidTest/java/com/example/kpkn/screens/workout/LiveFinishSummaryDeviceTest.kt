package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.time.LocalDate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Ejecutar solo en el teléfono: ningún registro ni ajuste toca el repositorio de producción.
 * Los tags de geometría/imagen/dial se consultan sin fusionar: el panel clickable de KpknSheet fusiona esos descendientes.
 */
@RunWith(AndroidJUnit4::class)
class LiveFinishSummaryDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val saves=mutableListOf<Pair<String,SessionClosingFeedback>>()
    private var confirm by mutableStateOf(false)
    private var spokenNotes by mutableStateOf<String?>(null)
    private var open by mutableStateOf(true)
    private var draft:String?=null
    private val savedNoteCalls=mutableListOf<String>()
    private val addedTaskCalls=mutableListOf<String>()
    private val toggledTaskCalls=mutableListOf<String>()
    private val removedTaskCalls=mutableListOf<String>()
    private val checklist=mutableStateListOf(SessionChecklistItem("task-existing","Movilidad de tobillo"))
    private val savedNotes=mutableStateListOf(SessionSavedNote("note-previous","Nota conservada del 8 de octubre","2026-10-08T12:00:00Z"))
    private val milestones=listOf(SessionMilestone("milestone","lift","Sentadilla","pr_e1rm","Nueva marca de prueba",96.0,"80 kg × 6","2026-10-16T12:00:00Z"))
    private var density=1f
    private var fontScale by mutableStateOf(1f)
    private val set=CompletedSet("set",weight=80.0,reps=6,rir=2)
    private val exercise=CompletedExercise("lift","Sentadilla",canonicalExerciseId="squat",sets=listOf(set))
    private val preview=PostSessionPreview(72,68,64,emptyMap(),8,12,6,finishOperationId="operation",inputHash="input-hash",
        completionInstantIso="2026-10-16T12:00:00Z",preSessionMuscular=76,preSessionNeural=80,preSessionSpinal=74)

    private fun show() {
        rule.setContent {
            density=LocalDensity.current.density
            CompositionLocalProvider(LocalWizardReducedMotion provides true, LocalDensity provides Density(density,fontScale)) {
                KPKNTheme {
                    val host=remember { KpknOverlayHostController(tourSeen=object:TourSeenStore {
                        override fun seenIds()=emptySet<String>()
                        override fun markSeen(tourId:String)=Unit
                    }) }
                    val haze=remember { HazeState() }
                    CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                        Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
                            // Como el Scaffold de WorkoutScreen: fondo oscuro y fuente Haze detrás de las hojas.
                            // KPKNTheme define colores, pero no pinta el fondo de la ComponentActivity de prueba.
                            Box(Modifier.fillMaxSize().hazeSource(haze).background(KpknSurface.canvas))
                            if(open) FinishWorkoutSheet(
                                session=Session("session","Pierna"),completedSets=mapOf("lift_0" to set),completedExercises=listOf(exercise),durationMinutes=40,
                                sessionIntensityResult=SessionIntensityResult(8.0,8.0,"Alta",false,false,1.0,1.0),postSessionPreview=preview,
                                hazeState=haze,voiceFinalNotes=spokenNotes,voiceFinalConfirmTriggered=confirm,
                                initialSessionNotes=draft.orEmpty(),
                                presentation=FinishPresentationContext(today=LocalDate.of(2026,10,16)),
                                sessionMilestones=milestones,sessionSavedNotes=savedNotes,sessionChecklist=checklist,
                                onSaveSessionNote={text->savedNoteCalls+=text;savedNotes+=SessionSavedNote("saved-${savedNoteCalls.size}",text,"2026-10-16T12:00:00Z")},
                                onAddChecklistItem={text->addedTaskCalls+=text;checklist+=SessionChecklistItem("task-added-${addedTaskCalls.size}",text)},
                                onToggleChecklistItem={id->toggledTaskCalls+=id;val index=checklist.indexOfFirst{it.id==id};if(index>=0)checklist[index]=checklist[index].copy(done=!checklist[index].done)},
                                onRemoveChecklistItem={id->removedTaskCalls+=id;checklist.removeAll{it.id==id}},
                                onConfirm={ notes,_,feedback,_ -> saves += notes to feedback },onDismiss={open=false},onNotesDraft={draft=it},onShare={},onSummaryReady={})
                            KpknOverlayHostContent(host)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun pagesKeepNotesAndSaveWithSameFinishIdentityAndHeight() {
        show()
        selectTab("Notas")
        rule.onNodeWithTag("finish-notes").performTextReplacement("Mantener este borrador")
        val pageHeights=mutableListOf<Float>()
        val fabTops=mutableListOf<Float>()
        listOf("Notas","Hoy","Levantamientos","Hitos","Fotos").forEach { tab ->
            selectTab(tab)
            pageHeights += rule.onNodeWithTag("finish-pager", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
            val page=rule.onNodeWithTag("finish-pager", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val fab=rule.onNodeWithTag("finish-fabs", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("el contenido llega hasta los botones",fab.top-page.bottom in 0f..(18f*density))
            fabTops += fab.top
            screenshot("$tab-100")
            rule.onNodeWithTag("finish-save").performClick()
        }
        rule.runOnIdle {
            assertEquals(5,saves.size)
            saves.forEach { (note,feedback) ->
                assertEquals("Mantener este borrador",note)
                assertEquals("operation",feedback.finishOperationId)
                assertEquals("input-hash",feedback.completedSetInputHash)
                assertEquals(76,feedback.ringStartSnapshot?.muscular)
            }
            assertTrue(pageHeights.all { abs(it-pageHeights.first())<2f })
            assertTrue(fabTops.all { abs(it-fabTops.first())<2f })
        }
        rule.runOnIdle { fontScale=1.3f }
        listOf("Hoy","Levantamientos","Hitos","Fotos","Notas").forEach { tab ->
            selectTab(tab)
            val page=rule.onNodeWithTag("finish-pager", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val fab=rule.onNodeWithTag("finish-fabs", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("130%: sin hueco sobre botones",fab.top-page.bottom in 0f..(18f*density))
            rule.onNodeWithTag("finish-save").assertIsDisplayed()
            screenshot("$tab-130")
        }
    }


    @Test fun shareOffersOnlyActualSessionPhotoModes() {
        show()
        rule.onNodeWithTag("finish-share").performClick()
        rule.onNodeWithText("General").assertExists()
        rule.onNodeWithText("Ejercicio").assertExists()
        rule.onNodeWithText("Fotos").assertDoesNotExist()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("story-preview", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        screenshot("compartir-sin-fotos")
    }

    private fun selectTab(title:String) {
        rule.onNodeWithText(title).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(title).assertIsDisplayed()
        val index=listOf("Hoy","Levantamientos","Hitos","Fotos","Notas").indexOf(title)
        rule.onNodeWithTag("finish-page-$index",useUnmergedTree=true).assertIsDisplayed()
    }

    private fun screenshot(name:String) {
        val image=rule.onRoot().captureToImage().asAndroidBitmap()
        val directory=File(rule.activity.getExternalFilesDir(null),"qa/lv13").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun voiceConfirmsFromNotesAndKeepsFinalFeedback() {
        show()
        selectTab("Notas")
        rule.runOnIdle { spokenNotes="Cierre por voz";confirm=true }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1,saves.size);assertEquals("Cierre por voz",saves.single().first);assertEquals("operation",saves.single().second.finishOperationId) }
    }

    @Test fun dismissKeepsNoteAndOfficialAdjustmentFeedsSameClosingFeedback() {
        show()
        rule.onNodeWithText("ENERGÍA").performClick()
        rule.onNodeWithTag("ring-adjust-ring", useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(55f) }
        rule.onNodeWithText("Listo").performClick()
        rule.onNodeWithTag("finish-save").performClick()
        rule.runOnIdle { assertEquals(55,saves.single().second.finalNeuralBattery);assertTrue(saves.single().second.neuralEdited) }
        selectTab("Notas")
        rule.onNodeWithTag("finish-notes").performTextReplacement("Vuelvo a entrenar")
        rule.onNodeWithTag("finish-back").performClick()
        rule.runOnIdle { assertEquals("Vuelvo a entrenar",draft);assertFalse(open) }
    }

    @Test fun milestonesDatedNotesAndChecklistUseTheirExistingCallbacksWithoutSavingOnNavigation() {
        show()
        selectTab("Hitos")
        rule.onNodeWithText("NUEVA MARCA DE PRUEBA").assertIsDisplayed()
        rule.onNodeWithText("Sentadilla").assertIsDisplayed()
        selectTab("Notas")
        rule.onNodeWithText("2026-10-16",useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithText("¿Cómo te sentiste?").assertDoesNotExist()
        rule.onNodeWithTag("finish-task-task-existing").performClick()
        rule.runOnIdle {
            assertEquals(listOf("task-existing"),toggledTaskCalls)
            assertTrue(checklist.single().done)
            assertTrue(saves.isEmpty())
            assertTrue(savedNoteCalls.isEmpty())
        }
        rule.onNodeWithContentDescription("Eliminar tarea").performClick()
        rule.runOnIdle { assertEquals(listOf("task-existing"),removedTaskCalls);assertTrue(checklist.isEmpty()) }
        rule.onNodeWithTag("finish-new-task").performTextInput("Revisar técnica del rumano")
        rule.onNodeWithTag("finish-add-task").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(listOf("Revisar técnica del rumano"),addedTaskCalls)
            assertEquals("Revisar técnica del rumano",checklist.single().text)
            assertFalse(checklist.single().done)
            assertTrue(saves.isEmpty())
        }
    }

    @Test fun datedNoteHistoryAndDraftSurviveDismissAndReopenUntilExplicitSave() {
        show()
        selectTab("Notas")
        rule.onNodeWithTag("finish-note-history").performScrollTo().performClick()
        rule.onNodeWithText("2026-10-08",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Nota conservada del 8 de octubre",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("finish-notes").performTextReplacement("Borrador sin guardar")
        selectTab("Fotos")
        selectTab("Hitos")
        rule.runOnIdle { assertTrue(saves.isEmpty());assertTrue(savedNoteCalls.isEmpty()) }
        rule.onNodeWithTag("finish-back").performClick()
        rule.runOnIdle { assertFalse(open);assertEquals("Borrador sin guardar",draft) }
        rule.runOnIdle { open=true }
        selectTab("Notas")
        rule.onNodeWithTag("finish-notes").assertTextEquals("Borrador sin guardar")
        rule.onNodeWithTag("finish-save").performClick()
        rule.runOnIdle {
            assertEquals(listOf("Borrador sin guardar"),savedNoteCalls)
            assertEquals("2026-10-16T12:00:00Z",savedNotes.last().createdAtIso)
            assertEquals("Borrador sin guardar",saves.single().first)
        }
    }

    @Test fun inlineRingAndSharePanelsReturnToSamePageWithoutLosingDraft() {
        show()
        selectTab("Notas")
        rule.onNodeWithTag("finish-notes").performTextReplacement("Conservar al ajustar")
        rule.onNodeWithTag("finish-share").performClick()
        rule.onNodeWithTag("finish-pager",useUnmergedTree=true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Volver al resumen").performClick()
        rule.onNodeWithTag("finish-page-4",useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithTag("finish-notes").assertTextEquals("Conservar al ajustar")
        selectTab("Hoy")
        rule.onNodeWithText("ENERGÍA").performClick()
        rule.onNodeWithTag("finish-pager",useUnmergedTree=true).assertDoesNotExist()
        rule.onNodeWithTag("ring-adjust-ring",useUnmergedTree=true).performSemanticsAction(SemanticsActions.SetProgress){it(55f)}
        rule.onNodeWithText("Columna").performClick()
        rule.onNodeWithTag("ring-adjust-ring",useUnmergedTree=true).performSemanticsAction(SemanticsActions.SetProgress){it(60f)}
        rule.onNodeWithText("Listo").performClick()
        rule.onNodeWithTag("finish-page-0",useUnmergedTree=true).assertIsDisplayed()
        selectTab("Notas")
        rule.onNodeWithTag("finish-notes").assertTextEquals("Conservar al ajustar")
        rule.runOnIdle { assertTrue(saves.isEmpty());assertTrue(savedNoteCalls.isEmpty()) }
        rule.onNodeWithTag("finish-save").performClick()
        rule.runOnIdle {
            assertEquals(55,saves.single().second.finalNeuralBattery)
            assertEquals(60,saves.single().second.finalSpinalBattery)
            assertEquals("Conservar al ajustar",saves.single().first)
            assertEquals(listOf("Conservar al ajustar"),savedNoteCalls)
        }
    }

}
