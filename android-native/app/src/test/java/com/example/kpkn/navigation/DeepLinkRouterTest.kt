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
        val concepts = DeepLinkRouter.resolve(Uri.parse("https://www.kpkn.fit/wikilab/concepts"))

        assertEquals(KpknRoute.Nutrition.route, deepLinkScheme?.route)
        assertEquals(KpknRoute.NutritionAction.create("openFoodLog"), deepLinkAction?.route)
        assertEquals(KpknRoute.Nutrition.route, nutrition?.route)
        assertEquals(KpknRoute.Home.route, wiki?.route)
        assertEquals(KpknRoute.Concepts.route, concepts?.route)
    }

    @Test
    fun resolvesParameterizedRoutes() {
        val program = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/program/power-12"))
        val exercise = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/exercise/squat-low-bar"))
        val unknownConcept = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/wikilab/concept/not-known"))

        assertEquals(KpknRoute.ProgramDetail.create("power-12"), program?.route)
        assertEquals(KpknRoute.Home.route, exercise?.route)
        assertEquals(KpknRoute.Concepts.route, unknownConcept?.route)
    }

    @Test
    fun routesKnownConceptsAndRetiredLearnLinks() {
        val known = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/wikilab/concept/${com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE.first().id}"))
        val learn = DeepLinkRouter.resolve(Uri.parse("kpkn://learn/course/intro"))

        assertEquals(
            KpknRoute.ConceptDetail.create(com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE.first().id),
            known?.route,
        )
        assertEquals(KpknRoute.Home.route, learn?.route)
    }

    @Test
    fun resolvesNutritionWizardCalibrationAndHealthConnectRoutes() {
        assertEquals(
            KpknRoute.NutritionWizard.create(),
            DeepLinkRouter.resolve(Uri.parse("kpkn://nutrition/wizard"))?.route,
        )
        assertEquals(
            KpknRoute.NutritionCalibration.route,
            DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/nutrition/calibration"))?.route,
        )
        assertEquals(
            KpknRoute.HealthConnect.route,
            DeepLinkRouter.resolve(Uri.parse("kpkn://settings/health-connect"))?.route,
        )
    }

    @Test
    fun redirectsDeprecatedSettingsRoutesToTheNewSurfaces() {
        assertEquals(KpknRoute.Profile.route, DeepLinkRouter.resolve(Uri.parse("kpkn://settings/profile"))?.route)
        assertEquals(KpknRoute.Settings.route, DeepLinkRouter.resolve(Uri.parse("kpkn://settings/general"))?.route)
        assertEquals(KpknRoute.Settings.route, DeepLinkRouter.resolve(Uri.parse("kpkn://settings/auge"))?.route)
        assertEquals(KpknRoute.Settings.route, DeepLinkRouter.resolve(Uri.parse("kpkn://settings/diagnostics"))?.route)
    }

    @Test
    fun rejectsNonKpknOrInvalidLinks() {
        val otherHost = DeepLinkRouter.resolve(Uri.parse("https://example.com/nutrition"))
        val noId = DeepLinkRouter.resolve(Uri.parse("https://kpkn.fit/program"))

        assertNull(otherHost)
        assertNull(noId)
    }
}
