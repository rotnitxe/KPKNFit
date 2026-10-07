package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/*
 * Textos y datos de las tres páginas de la bienvenida: lo único que cambia de una a otra.
 *
 * Reglas de redacción (las comprueba WelcomeShellCopyTest): título ≤ 40 caracteres, mensaje ≤ 130, español
 * neutro con tuteo, tono cálido y sin tecnicismos. El color de cada apartado es el ÚNICO acento de su página:
 * solo tiñe la etiqueta que va sobre el título.
 */

internal const val WelcomeTitleMaxChars = 40
internal const val WelcomeMessageMaxChars = 130

/** Una página del carrusel: su etiqueta, su acento, sus textos, la frase para TalkBack y el periodo de su escena. */
internal data class WelcomePageCopy(
    val id: String,
    /** Etiqueta en mayúsculas sobre el título. */
    val label: String,
    /** Color propio del apartado: solo para la etiqueta. */
    val accent: Color,
    val title: String,
    val message: String,
    /** Lo que TalkBack dice del teléfono: una sola frase que describe la demostración. */
    val demoDescription: String,
    /** Duración del bucle de la escena, en segundos. */
    val period: Float,
    /**
     * true = el marco funde la escena (≈ 0,5 s) entre ciclo y ciclo. Las tres escenas actuales cierran solas, con su
     * propio velo y la barra de estado a la vista, así que ninguna lo pide; una escena nueva que no cierre sola
     * lo activa aquí.
     */
    val shellFadesLoop: Boolean = false,
)

internal object WelcomePageIds {
    const val Entreno = "entreno"
    const val Nutricion = "nutricion"
    const val Recuperacion = "recuperacion"
}

internal val WelcomePages: List<WelcomePageCopy> = listOf(
    WelcomePageCopy(
        id = WelcomePageIds.Entreno,
        label = "ENTRENO",
        accent = WelcomeScenePalette.musculo,
        title = "Entrenar, sin complicarte",
        message = "Arma tu sesión en segundos y registra cada serie mientras entrenas. KPKN lleva la cuenta por ti.",
        demoDescription = "Demostración animada: armas una sesión de entreno y registras tus series una a una.",
        period = WelcomeEntrenoPeriod,
    ),
    WelcomePageCopy(
        id = WelcomePageIds.Nutricion,
        label = "NUTRICIÓN",
        accent = WelcomeScenePalette.ok,
        title = "Cuenta lo que comes, a tu manera",
        message = "Escribe tu comida con tus propias palabras y KPKN calcula calorías y macros por ti.",
        demoDescription = "Demostración animada: escribes una comida con tus palabras y KPKN calcula sus calorías y macros.",
        period = WelcomeNutricionPeriod,
    ),
    WelcomePageCopy(
        id = WelcomePageIds.Recuperacion,
        label = "RECUPERACIÓN",
        accent = WelcomeScenePalette.columna,
        title = "Cuándo apretar y cuándo descansar",
        message = "Tus Rings muestran cómo están tu músculo, tu energía y tu columna, y se recuperan mientras descansas.",
        demoDescription = "Demostración animada: tus Rings de músculo, energía y columna bajan al entrenar y suben mientras duermes.",
        period = WelcomeRingsPeriod,
    ),
)

/** Dibuja la escena de la página [page] en el lienzo lógico [WelcomeSceneSize]; es el único sitio que las conoce. */
@Composable
internal fun WelcomeSceneOf(page: Int, t: Float, modifier: Modifier = Modifier) {
    val fill = modifier.fillMaxSize()
    when (WelcomePages[page].id) {
        WelcomePageIds.Entreno -> WelcomeEntrenoScene(t, fill)
        WelcomePageIds.Nutricion -> WelcomeNutricionScene(t, fill)
        else -> WelcomeRingsScene(t, fill)
    }
}
