package com.example.kpkn.navigation

import android.content.Intent

/**
 * Lo que el intent de un lanzamiento fresco pide a la app (WP-U8 / C6): la [route] a abrir
 * (deep link o acción rápida de Nutrición) y/o el [sharedText] recibido por «Compartir».
 * [sharedTab] es la pestaña del logger de comidas a la que va el texto compartido (0 = describir).
 */
data class LaunchRequest(
    val route: String?,
    val sharedText: String?,
    val sharedTab: Int = LaunchIntentResolver.SHARED_TEXT_TAB,
)

/**
 * Traduce el [Intent] que abrió la app, sin tocar la Activity ni el NavController.
 *
 * Fija la regla de C6: una Activity RECREADA (rotación, plegado, cambio de idioma, muerte del
 * proceso) recibe de nuevo el intent ORIGINAL (el ACTION_SEND del share, el extra del widget);
 * reprocesarlo duplicaría la navegación y el guardado. Lo mismo ocurre al reabrir la app desde
 * Recientes: la tarea conserva su intent raíz. Solo un lanzamiento fresco (`savedInstanceState == null`
 * y sin `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) consume ese intent, vía [forLaunch]; los intents
 * posteriores llegan por `onNewIntent`, que usa [route] y [sharedText] directamente.
 *
 * Los intents vienen de otras apps (MainActivity recibe ACTION_SEND): un Bundle de extras ilegible
 * (un Parcelable desconocido lanza al leerlo) no debe tumbar el arranque, así que cualquier fallo
 * al leer el intent equivale a «sin ruta / sin texto».
 */
object LaunchIntentResolver {

    /** Extra con la acción rápida de Nutrición (`openFoodLog`, `openSearch`...) de lanzadores y atajos. */
    const val EXTRA_NUTRITION_ACTION = "kpkn_nutrition_action"

    /** Pestaña «Describir» del logger de comidas: destino del texto compartido. */
    const val SHARED_TEXT_TAB = 0

    /**
     * Qué hacer con el intent de un lanzamiento; null si la Activity se recrea ([isRecreation]), si se
     * reabre desde Recientes (el intent raíz de la tarea ya se consumió) o si el intent no trae ruta
     * ni texto (un arranque normal desde el lanzador).
     */
    fun forLaunch(intent: Intent?, isRecreation: Boolean): LaunchRequest? {
        if (isRecreation || intent == null) return null
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
        val route = route(intent)
        val sharedText = sharedText(intent)
        if (route == null && sharedText == null) return null
        return LaunchRequest(route = route, sharedText = sharedText)
    }

    /**
     * Ruta de navegación del intent: el extra [EXTRA_NUTRITION_ACTION] manda sobre el deep link
     * (`kpkn://` o `https://kpkn.fit`), y un `kpkn://` desconocido se interpreta como acción de Nutrición.
     */
    fun route(intent: Intent?): String? {
        if (intent == null) return null
        return runCatching { resolveRoute(intent) }.getOrNull()
    }

    /** Texto de un ACTION_SEND de texto, sin espacios en los bordes; null si no es texto o queda en blanco. */
    fun sharedText(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND) return null
        val mime = intent.type.orEmpty()
        if (!mime.contains("text", ignoreCase = true)) return null
        return runCatching { intent.getStringExtra(Intent.EXTRA_TEXT) }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun resolveRoute(intent: Intent): String? {
        val explicitAction = intent.getStringExtra(EXTRA_NUTRITION_ACTION)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (explicitAction != null) {
            return KpknRoute.NutritionAction.create(explicitAction)
        }

        val dataRoute = DeepLinkRouter.resolve(intent.data)?.route
        if (dataRoute != null) return dataRoute

        val data = intent.data
        if (data != null && data.scheme.equals("kpkn", ignoreCase = true)) {
            val action = data.getQueryParameter("action")
                ?: data.pathSegments.lastOrNull()
            if (!action.isNullOrBlank()) {
                return KpknRoute.NutritionAction.create(action)
            }
        }
        return null
    }
}
