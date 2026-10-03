package com.example.kpkn.navigation

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WP-U8 / C6: una Activity recreada (rotación, plegado, muerte del proceso) recibe de nuevo el intent
 * ORIGINAL; solo un lanzamiento fresco puede consumir el ACTION_SEND o el extra del widget.
 * Robolectric porque `Intent` y `Uri` son de Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LaunchIntentResolverTest {

    private fun shareIntent(text: String?, mime: String? = "text/plain"): Intent =
        Intent(Intent.ACTION_SEND).apply {
            if (mime != null) type = mime
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
        }

    private fun widgetIntent(action: String): Intent =
        Intent().putExtra(LaunchIntentResolver.EXTRA_NUTRITION_ACTION, action)

    private fun deepLinkIntent(uri: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    // ─── Recreación: nada se reprocesa ───────────────────────────────────────

    @Test
    fun recreatedActivityIgnoresActionSend() {
        val intent = shareIntent("2 huevos y pan con palta")
        assertNull(LaunchIntentResolver.forLaunch(intent, isRecreation = true))
    }

    @Test
    fun recreatedActivityIgnoresWidgetExtraAndDeepLink() {
        assertNull(LaunchIntentResolver.forLaunch(widgetIntent("openFoodLog"), isRecreation = true))
        assertNull(
            LaunchIntentResolver.forLaunch(deepLinkIntent("kpkn://nutrition/action/openSearch"), isRecreation = true),
        )
    }

    @Test
    fun reopeningFromRecentsIgnoresTheStaleRootIntent() {
        // La tarea conserva su intent raíz: reabrirla desde Recientes lo entrega de nuevo.
        val share = shareIntent("2 huevos").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(LaunchIntentResolver.forLaunch(share, isRecreation = false))
        val widget = widgetIntent("openFoodLog").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(LaunchIntentResolver.forLaunch(widget, isRecreation = false))
        // Los flags del widget (tarea nueva, single top) no son de historial.
        val fromWidget = widgetIntent("openFoodLog")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        assertEquals(
            KpknRoute.NutritionAction.create("openFoodLog"),
            LaunchIntentResolver.forLaunch(fromWidget, isRecreation = false)?.route,
        )
    }

    // ─── Lanzamiento fresco ──────────────────────────────────────────────────

    @Test
    fun freshLaunchResolvesTheNutritionActionExtra() {
        val request = LaunchIntentResolver.forLaunch(widgetIntent("openFoodLog"), isRecreation = false)
        assertEquals(KpknRoute.NutritionAction.create("openFoodLog"), request?.route)
        assertNull(request?.sharedText)
    }

    @Test
    fun freshLaunchResolvesAShare() {
        val request = LaunchIntentResolver.forLaunch(shareIntent("  almorcé arroz con pollo  "), isRecreation = false)
        assertEquals("almorcé arroz con pollo", request?.sharedText)
        assertEquals(0, request?.sharedTab)
        assertNull(request?.route)
    }

    @Test
    fun freshLaunchResolvesADeepLink() {
        val request = LaunchIntentResolver.forLaunch(deepLinkIntent("kpkn://nutrition/action/openSearch"), isRecreation = false)
        assertEquals(KpknRoute.NutritionAction.create("openSearch"), request?.route)
    }

    @Test
    fun aPlainLauncherStartIsNotALaunchRequest() {
        assertNull(LaunchIntentResolver.forLaunch(Intent(Intent.ACTION_MAIN), isRecreation = false))
        assertNull(LaunchIntentResolver.forLaunch(null, isRecreation = false))
        assertNull(LaunchIntentResolver.forLaunch(null, isRecreation = true))
    }

    // ─── Texto compartido ────────────────────────────────────────────────────

    @Test
    fun sharedTextIsTrimmedForTextMimeTypes() {
        assertEquals("2 huevos", LaunchIntentResolver.sharedText(shareIntent("\n 2 huevos \t")))
        assertEquals("2 huevos", LaunchIntentResolver.sharedText(shareIntent("2 huevos", mime = "text/html")))
    }

    @Test
    fun sharedTextIsNullForImagesBlankTextOtherActionsAndMissingType() {
        assertNull(LaunchIntentResolver.sharedText(shareIntent("2 huevos", mime = "image/png")))
        assertNull(LaunchIntentResolver.sharedText(shareIntent("   ")))
        assertNull(LaunchIntentResolver.sharedText(shareIntent(null)))
        assertNull(LaunchIntentResolver.sharedText(shareIntent("2 huevos", mime = null)))
        assertNull(
            LaunchIntentResolver.sharedText(
                Intent(Intent.ACTION_VIEW).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "2 huevos")
                },
            ),
        )
        assertNull(LaunchIntentResolver.sharedText(null))
    }

    // ─── Ruta ────────────────────────────────────────────────────────────────

    @Test
    fun theWidgetExtraIsTrimmedAndBeatsTheDeepLink() {
        assertEquals(KpknRoute.NutritionAction.create("log"), LaunchIntentResolver.route(widgetIntent("  log  ")))
        val both = deepLinkIntent("kpkn://nutrition/action/openSearch")
            .putExtra(LaunchIntentResolver.EXTRA_NUTRITION_ACTION, "openFoodLog")
        assertEquals(KpknRoute.NutritionAction.create("openFoodLog"), LaunchIntentResolver.route(both))
    }

    @Test
    fun aBlankWidgetExtraIsIgnored() {
        assertNull(LaunchIntentResolver.route(widgetIntent("   ")))
    }

    @Test
    fun anUnknownKpknLinkFallsBackToTheNutritionActionParameter() {
        assertEquals(
            KpknRoute.NutritionAction.create("log"),
            LaunchIntentResolver.route(deepLinkIntent("kpkn://unknown-target?action=log")),
        )
    }

    @Test
    fun anUnreadableExtrasBundleNeverCrashesTheLaunch() {
        // Un ACTION_SEND ajeno con un Parcelable desconocido lanza al leer cualquier extra.
        val hostile = object : Intent(Intent.ACTION_SEND) {
            override fun getStringExtra(name: String?): String? = throw IllegalStateException("bundle ilegible")
        }.apply { type = "text/plain" }
        assertNull(LaunchIntentResolver.route(hostile))
        assertNull(LaunchIntentResolver.sharedText(hostile))
        assertNull(LaunchIntentResolver.forLaunch(hostile, isRecreation = false))
    }

    @Test
    fun theExtraKeyIsTheOneTheLaunchersWrite() {
        assertEquals("kpkn_nutrition_action", LaunchIntentResolver.EXTRA_NUTRITION_ACTION)
    }
}
