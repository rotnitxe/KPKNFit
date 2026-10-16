package com.example.kpkn.screens.workout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

/** Regresión física de renders lentos que terminan después de cambiar las opciones. */
@RunWith(AndroidJUnit4::class)
class WorkoutStoryPreviewDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Test fun cancelledRenderCannotExposePreviousBitmapOrEnableShareBeforeLatestPreview() {
        val initial=WorkoutStoryOptions()
        val second=initial.copy(rings=false)
        val latest=second.copy(duration=false)
        val requests=listOf(initial,second,latest).associateWith { CompletableDeferred<Bitmap>() }
        val started=ConcurrentHashMap.newKeySet<WorkoutStoryOptions>()
        var selected by mutableStateOf(initial)
        // Simula una decodificación nativa que no coopera con cancelación hasta devolver el bitmap.
        val renderer:suspend (Context,WorkoutStoryData,WorkoutStoryOptions)->Bitmap={_,_,options ->
            withContext(NonCancellable) { started.add(options);requests.getValue(options).await() }
        }
        val data=WorkoutStoryData("Fixture",FinishPresentationContext(),40,400.0,4,2.0,"Alta",TowerRingValues(60,70,80),emptyList())
        fun image(color:Int)=Bitmap.createBitmap(9,16,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    WorkoutStorySheet(data,HazeState(),selected,{selected=it},false,{}, {},renderStory=renderer)
                }
            }
        }
        try {
            rule.waitUntil(10_000) { initial in started }
            rule.runOnIdle { requests.getValue(initial).complete(image(Color.GREEN)) }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("story-preview").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("story-share").assertIsEnabled()
            rule.onNodeWithTag("story-toggle-RINGS").performClick()
            rule.waitUntil(10_000) { second in started }
            rule.onNodeWithTag("story-preview").assertDoesNotExist()
            rule.onNodeWithTag("story-share").assertIsNotEnabled()
            rule.onNodeWithTag("story-save-image").assertIsNotEnabled()
            // Se permite seleccionar otra opción mientras la anterior todavía se está dibujando.
            rule.onNodeWithTag("story-toggle-Duración").performClick()
            rule.waitUntil(10_000) { latest in started }
            rule.runOnIdle { requests.getValue(second).complete(image(Color.BLUE)) }
            rule.waitForIdle()
            rule.onNodeWithTag("story-preview").assertDoesNotExist()
            rule.onNodeWithTag("story-share").assertIsNotEnabled()
            rule.onNodeWithTag("story-save-image").assertIsNotEnabled()
            rule.runOnIdle { requests.getValue(latest).complete(image(Color.RED)) }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("story-preview").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("story-share").assertIsEnabled()
            rule.onNodeWithTag("story-save-image").assertIsEnabled()
            val pixels=rule.onNodeWithTag("story-preview").captureToImage().asAndroidBitmap()
            assertEquals("la vista previa pertenece a la última selección",Color.RED,pixels.getPixel(pixels.width/2,pixels.height/2))
        } finally {
            // No dejar decodificaciones no cancelables esperando si una aserción falla.
            requests.values.filterNot { it.isCompleted }.forEach { it.complete(image(Color.GRAY)) }
        }
    }
}
