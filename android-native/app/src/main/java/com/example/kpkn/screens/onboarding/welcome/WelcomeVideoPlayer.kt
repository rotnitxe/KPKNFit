package com.example.kpkn.screens.onboarding.welcome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.kpkn.R
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/*
 * Vídeo real de la bienvenida: un bucle sin sonido, sin controles y en zoom dentro del mismo chasis que las escenas
 * Canvas. Sustituye la escena de una página solo cuando su vídeo y su póster existen en `res/` (ver [WelcomeSceneOf]);
 * mientras no, la página sigue con su escena Canvas.
 *
 * Contrato de reproducción (el mismo del reloj de [WelcomeSceneClock]): corre solo con `playing` (página asentada y app
 * en primer plano) y nunca con movimiento reducido o ahorro de batería. En cualquier otro caso, y antes del primer
 * cuadro, se ve el póster, que es el cuadro representativo de la escena.
 */

/** Recursos de una página con vídeo: el bucle (`res/raw`) y su póster (`res/drawable-nodpi`, WebP). */
internal data class WelcomeVideoAssets(@RawRes val video: Int, @DrawableRes val poster: Int)

/**
 * Nombres de los recursos de cada página. Son literales completos, nunca compuestos: así el reductor de recursos de la
 * versión de publicación los ve en el código y no los quita. Mientras no existan, la página usa su escena Canvas.
 */
internal fun welcomeVideoNamesOf(pageId: String): Pair<String, String>? = when (pageId) {
    WelcomePageIds.Entreno -> "welcome_entreno" to "welcome_entreno_poster"
    WelcomePageIds.Nutricion -> "welcome_nutricion" to "welcome_nutricion_poster"
    WelcomePageIds.Recuperacion -> "welcome_rings" to "welcome_rings_poster"
    else -> null
}

/**
 * Resuelve el vídeo y el póster de [pageId] con [lookup] (nombre y tipo de recurso → id, 0 si no existe). Hacen falta
 * LOS DOS: sin póster no habría nada que mostrar mientras el vídeo no corre.
 */
internal fun welcomeVideoAssetsOf(pageId: String, lookup: (name: String, type: String) -> Int): WelcomeVideoAssets? {
    val (videoName, posterName) = welcomeVideoNamesOf(pageId) ?: return null
    val video = lookup(videoName, "raw")
    val poster = lookup(posterName, "drawable")
    return if (video != 0 && poster != 0) WelcomeVideoAssets(video, poster) else null
}

/** Busca los recursos de la página en esta app. Nunca lanza: si algo falla, la página queda con su escena Canvas. */
@Composable
internal fun rememberWelcomeVideoAssets(pageId: String): WelcomeVideoAssets? {
    val context = LocalContext.current
    return remember(pageId) {
        welcomeVideoAssetsOf(pageId) { name, type ->
            runCatching { context.resources.getIdentifier(name, type, context.packageName) }.getOrDefault(0)
        }
    }
}

/** Corre solo si la página está asentada y la app en primer plano ([playing]), sin movimiento reducido ni ahorro de batería. */
internal fun welcomeVideoPlays(playing: Boolean, reducedMotion: Boolean, powerSave: Boolean): Boolean =
    playing && !reducedMotion && !powerSave

/** El póster tapa el vídeo mientras no corre y también antes de su primer cuadro. */
internal fun welcomeVideoShowsPoster(plays: Boolean, firstFrameShown: Boolean): Boolean = !(plays && firstFrameShown)

/** True mientras el ahorro de batería del sistema está activo; sigue los cambios con la pantalla abierta. */
@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    var powerSave by remember { mutableStateOf(context.isPowerSaveModeOn()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                powerSave = ctx.isPowerSaveModeOn()
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return powerSave
}

private fun Context.isPowerSaveModeOn(): Boolean =
    runCatching { getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)

/**
 * Vídeo de una página de la bienvenida: [video] en bucle y [poster] como cuadro en pausa. [playing] dice que la página
 * está asentada y la app en primer plano; el movimiento reducido y el ahorro de batería los comprueba aquí mismo.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun WelcomeVideoPlayer(
    @RawRes video: Int,
    @DrawableRes poster: Int,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reducedMotion = wizardReducedMotion()
    val powerSave = rememberPowerSaveMode()
    val plays = welcomeVideoPlays(playing, reducedMotion, powerSave)

    // Un reproductor por vídeo, creado una vez. El medio se carga la primera vez que hace falta: con movimiento reducido
    // o ahorro de batería no llega a decodificarse nada.
    val exoPlayer = remember(video) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }
    }
    var prepared by remember(video) { mutableStateOf(false) }
    var firstFrameShown by remember(video) { mutableStateOf(false) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameShown = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    LaunchedEffect(exoPlayer, plays) {
        if (plays && !prepared) {
            exoPlayer.setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(video)))
            exoPlayer.prepare()
            prepared = true
        }
        exoPlayer.playWhenReady = plays
    }

    val showPoster = welcomeVideoShowsPoster(plays, firstFrameShown)
    val posterAlpha by animateFloatAsState(
        targetValue = if (showPoster) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 160),
        label = "welcomeVideoPoster",
    )

    Box(modifier.testTag("welcome-video")) {
        AndroidView(
            modifier = Modifier.matchParentSize().testTag("welcome-video-player"),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.welcome_video_player, null) as PlayerView).apply {
                    setUseController(false)
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                    this.player = exoPlayer
                }
            },
            update = { view -> if (view.player !== exoPlayer) view.player = exoPlayer },
        )
        Image(
            painter = painterResource(poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .testTag("welcome-video-poster")
                .graphicsLayer { alpha = posterAlpha },
        )
    }
}
