package com.example.kpkn.screens.workout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.testutil.configureLiveSheetTestWindow
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

/**
 * Regresión física de renders lentos que terminan después de cambiar las opciones.
 * El panel clickable de KpknSheet fusiona la imagen: su tag se busca en el árbol sin fusionar.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutStoryPreviewDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Before fun configureWindow() {
        rule.activityRule.scenario.onActivity { it.configureLiveSheetTestWindow() }
    }

    @Test fun cancelledRenderCannotExposePreviousBitmapOrEnableShareBeforeLatestPreview() {
        val initial=WorkoutStoryOptions()
        val second=initial.copy(exerciseList=!initial.exerciseList)
        val latest=second.copy(duration=!second.duration)
        val requests=listOf(initial,second,latest).associateWith { CompletableDeferred<List<Bitmap>>() }
        val started=ConcurrentHashMap.newKeySet<WorkoutStoryOptions>()
        var selected by mutableStateOf(initial)
        // Simula una decodificación nativa que no coopera con cancelación hasta devolver el bitmap.
        val renderer: suspend (Context,WorkoutStoryData,WorkoutStoryOptions) -> List<Bitmap> = { _,_,options ->
            if(options.template!=WorkoutStoryTemplate.GENERAL) listOf(Bitmap.createBitmap(9,16,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.DKGRAY)})
            // El preview y las miniaturas pueden renderizar la misma plantilla a la vez. Devuelve
            // una copia por llamada para que reciclar el bitmap de la miniatura no invalide el preview.
            else withContext(NonCancellable) { started.add(options);requests.getValue(options).await().map { checkNotNull(it).copy(Bitmap.Config.ARGB_8888,false) } }
        }
        val data=WorkoutStoryData("Fixture",FinishPresentationContext(),40,400.0,4,2.0,"Alta",emptyList())
        fun image(color:Int)=Bitmap.createBitmap(9,16,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    WorkoutStorySheet(data,HazeState(),selected,{selected=it},false,{}, {},renderStoryPages=renderer)
                }
            }
        }
        try {
            rule.waitUntil(10_000) { initial in started }
            rule.runOnIdle { requests.getValue(initial).complete(listOf(image(Color.GREEN))) }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("story-preview", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("story-share").assertIsEnabled()
            rule.onNodeWithTag("story-toggle-Ejercicios").performClick()
            rule.waitUntil(10_000) { second in started }
            rule.onNodeWithTag("story-preview", useUnmergedTree = true).assertDoesNotExist()
            rule.onNodeWithTag("story-share").assertIsNotEnabled()
            rule.onNodeWithTag("story-save-image").assertIsNotEnabled()
            // Se permite seleccionar otra opción mientras la anterior todavía se está dibujando.
            rule.onNodeWithTag("story-toggle-Duración").performClick()
            rule.waitUntil(10_000) { latest in started }
            rule.runOnIdle { requests.getValue(second).complete(listOf(image(Color.BLUE))) }
            rule.waitForIdle()
            rule.onNodeWithTag("story-preview", useUnmergedTree = true).assertDoesNotExist()
            rule.onNodeWithTag("story-share").assertIsNotEnabled()
            rule.onNodeWithTag("story-save-image").assertIsNotEnabled()
            rule.runOnIdle { requests.getValue(latest).complete(listOf(image(Color.RED))) }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("story-preview", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("story-share").assertIsEnabled()
            rule.onNodeWithTag("story-save-image").assertIsEnabled()
            val center = rule.onNodeWithTag("story-preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
            val origin = IntArray(2)
            rule.runOnIdle { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(origin) }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val pixels = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                assertEquals("la vista previa pertenece a la última selección", Color.RED,
                    pixels.getPixel(origin[0] + center.x.toInt(), origin[1] + center.y.toInt()))
            } finally {
                pixels.recycle()
            }
        } finally {
            // No dejar decodificaciones no cancelables esperando si una aserción falla.
            requests.values.filterNot { it.isCompleted }.forEach { it.complete(listOf(image(Color.GRAY))) }
        }
    }

    @Test fun templatesOfferOnlyApplicableControlsAndCentralPreviewKeepsNineBySixteen() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"lv13-preview-photo-fixture.png")
        val fixture=Bitmap.createBitmap(9,16,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.DKGRAY)}
        file.outputStream().use{fixture.compress(Bitmap.CompressFormat.PNG,100,it)};fixture.recycle()
        var selected by mutableStateOf(WorkoutStoryOptions())
        var density=1f
        val lift=buildFinishLiftProgress(listOf(CompletedExercise("fixture","Sentadilla de prueba",sets=listOf(CompletedSet("set",weight=80.0,reps=6,rir=2)))),emptyList())
        val data=WorkoutStoryData("Fixture",FinishPresentationContext(photos=listOf(StoryPhoto(file.absolutePath))),40,480.0,1,2.0,"Alta",lift)
        val renderer: suspend (Context,WorkoutStoryData,WorkoutStoryOptions) -> List<Bitmap> = { _,_,_ -> listOf(Bitmap.createBitmap(9,16,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.DKGRAY)}) }
        try {
            rule.setContent {
                density=LocalDensity.current.density
                CompositionLocalProvider(LocalWizardReducedMotion provides true){KPKNTheme{WorkoutStorySheet(data,HazeState(),selected,{selected=it},false,{}, {},renderStoryPages=renderer)}}
            }
            val allOptions = setOf("Progreso", "Hitos", "Duración", "sRPE", "Ejercicios", "Músculos")
            fun check(template: WorkoutStoryTemplate, offered: Set<String>) {
                rule.onNodeWithTag("story-template-${template.name}").performClick()
                rule.waitUntil(10_000){rule.onAllNodesWithTag("story-preview",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
                val bounds=rule.onNodeWithTag("story-preview",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertEquals("La historia central debe conservar 9:16",9f/16f,bounds.width/bounds.height,.01f)
                assertTrue("El preview central no supera 220dp",bounds.width<=220f*density+2f)
                allOptions.forEach{label->
                    if(label in offered)rule.onNodeWithTag("story-toggle-$label").assertIsDisplayed()
                    else rule.onNodeWithTag("story-toggle-$label").assertDoesNotExist()
                }
                rule.onNodeWithTag("story-share").assertIsEnabled()
            }
            check(WorkoutStoryTemplate.GENERAL, allOptions)
            check(WorkoutStoryTemplate.MILESTONES, setOf("Duración"))
            check(WorkoutStoryTemplate.EXERCISE, setOf("Progreso"))
            check(WorkoutStoryTemplate.PHOTOS, setOf("Duración"))
            check(WorkoutStoryTemplate.GENERAL, allOptions)
        }finally{file.delete()}
    }

}
