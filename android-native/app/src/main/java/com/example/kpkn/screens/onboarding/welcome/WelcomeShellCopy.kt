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
    const val Resultados = "resultados"
}

internal val WelcomePages: List<WelcomePageCopy> = listOf(
    WelcomePageCopy(
        id = WelcomePageIds.Entreno,
        label = "ENTRENO",
        accent = WelcomeScenePalette.musculo,
        title = "Entrenar, sin complicarte",
        message = "Empieza la sesión de hoy y registra cada serie con kilos y repeticiones. KPKN lleva la cuenta por ti.",
        demoDescription = "Vídeo de demostración: retomas la sesión de hoy, registras una serie con kilos y repeticiones y ves el descanso.",
        period = WelcomeEntrenoPeriod,
    ),
    WelcomePageCopy(
        id = WelcomePageIds.Nutricion,
        label = "NUTRICIÓN",
        accent = WelcomeScenePalette.ok,
        title = "Cuenta lo que comes, a tu manera",
        message = "Escribe lo que comiste con tus palabras y KPKN calcula calorías y macros por ti.",
        demoDescription = "Vídeo de demostración: escribes una comida con tus palabras, KPKN la entiende y los anillos del día suben.",
        period = WelcomeNutricionPeriod,
    ),
    WelcomePageCopy(
        id = WelcomePageIds.Resultados,
        label = "RESULTADOS",
        accent = WelcomeScenePalette.columna,
        title = "Tu progreso, con contexto",
        message = "Compara sesiones y resultados registrados con sus unidades. Tú decides si quieres ajustar tu entrenamiento.",
        demoDescription = "Ejemplo ilustrativo: dos sesiones de cinco kilómetros, con su tiempo registrado. No son tus datos.",
        period = 12f,
    ),
)

/**
 * Dibuja la escena de la página [page] en el lienzo lógico [WelcomeSceneSize]; es el único sitio que las conoce. Si la
 * página tiene su vídeo y su póster grabados, muestra el vídeo (corre solo con [playing]); si no, su escena Canvas en [t].
 */
@Composable
internal fun WelcomeSceneOf(
    page: Int,
    t: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    video: WelcomeVideoAssets? = rememberWelcomeVideoAssets(WelcomePages[page].id),
) {
    val fill = modifier.fillMaxSize()
    if (video != null) {
        WelcomeVideoPlayer(video = video.video, poster = video.poster, playing = playing, modifier = fill)
        return
    }
    when (WelcomePages[page].id) {
        WelcomePageIds.Entreno -> WelcomeEntrenoScene(t, fill)
        WelcomePageIds.Nutricion -> WelcomeNutricionScene(t, fill)
        else -> WelcomeResultsScene(fill)
    }
}
