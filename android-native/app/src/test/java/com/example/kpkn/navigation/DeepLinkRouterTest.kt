package com.example.kpkn.navigation

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DeepLinkRouterTest {

    @Test
    fun resolvesTopLevelKpknRoutes() {
        val deepLinkScheme = DeepLinkRouter.resolve(Uri.parse("kpkn://nutrition"))
        val deepLinkAction = DeepLinkRouter.resolve(Uri.parse("kpkn://nutrition/action/openFoodLog"))
        val nutrition = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/nutricion"))
        val wiki = DeepLinkRouter.resolve(Uri.parse("https://www.kpkn.fit/wikilab"))

        assertEquals(KpknRoute.Nutrition.route, deepLinkScheme?.route)
        assertEquals(KpknRoute.NutritionAction.create("openFoodLog"), deepLinkAction?.route)
        assertEquals(KpknRoute.Nutrition.route, nutrition?.route)
        assertEquals(KpknRoute.WikiLab.route, wiki?.route)
    }

    @Test
    fun resolvesParameterizedRoutes() {
        val program = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/program/power-12"))
        val exercise = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/exercise/squat-low-bar"))

        assertEquals(KpknRoute.ProgramDetail.create("power-12"), program?.route)
        assertEquals(KpknRoute.WikiLabExerciseDetail.create("squat-low-bar"), exercise?.route)
    }

    @Test
    fun rejectsNonKpknOrInvalidLinks() {
        val otherHost = DeepLinkRouter.resolve(Uri.parse("https://example.com/nutrition"))
        val noId = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/program"))

        assertNull(otherHost)
        assertNull(noId)
    }
}
