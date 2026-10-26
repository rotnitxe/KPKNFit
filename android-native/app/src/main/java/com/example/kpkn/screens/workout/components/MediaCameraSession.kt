package com.example.kpkn.screens.workout.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.WorkoutCameraChoice
import com.example.kpkn.screens.workout.WorkoutCameraLens

/**
 * Lo que decide cuándo se une y se suelta la cámara de «Modo visual» (LV8). Separa la CÁMARA (los casos de uso de CameraX, unidos a la
 * ventana mientras la cara está a la vista) de DÓNDE SE PINTA su vista previa (la tarjeta o el overlay expandido): al expandir o plegar
 * solo cambia la vista donde se pinta ([attach] y [detach]); los casos de uso siguen unidos y una grabación en marcha no se corta.
 * Sin CameraX, para probarlo con pruebas locales.
 */
internal class MediaCameraSession(
    /** Pinta la vista previa en [surface] (o en ninguna, con nulo). */
    private val showPreviewOn: (Any?) -> Unit = {},
) {
    /** ¿Están unidos los casos de uso? */
    var bound: Boolean = false
        private set

    /** Cuántas veces se unieron y se soltaron los casos de uso: expandir y plegar no deben cambiarlo. */
    var bindCount: Int = 0
        private set
    var unbindCount: Int = 0
        private set

    /** La vista donde se pinta ahora la vista previa. */
    var surface: Any? = null
        private set

    /** Los casos de uso se unieron a la ventana. */
    fun onBound() {
        bound = true
        bindCount += 1
    }

    /** La vista previa pasa a pintarse en [surface] (la de la tarjeta o la del overlay expandido). */
    fun attach(surface: Any) {
        this.surface = surface
        showPreviewOn(surface)
    }

    /** [surface] deja de verse. Si ya se pinta en otra (porque la nueva llegó antes), no se toca. */
    fun detach(surface: Any) {
        if (this.surface === surface) {
            this.surface = null
            showPreviewOn(null)
        }
    }

    /** Al ceder al grabador de técnica, su captura no pertenece a esta cara. */
    fun stopOwnedRecording(stopOnDispose: Boolean, stop: () -> Unit) {
        if (stopOnDispose) stop()
    }

    /**
     * La cara sale de la composición o la cámara cambia de calidad: ¿se pueden soltar los casos de uso? Nunca mientras se graba: soltarlos
     * cortaría el vídeo. Devuelve si hay que soltarlos ahora.
     */
    fun release(isRecording: Boolean): Boolean {
        if (isRecording) return false
        if (bound) {
            bound = false
            unbindCount += 1
        }
        return true
    }
}

/** Una petición tardía del proveedor no puede volver a abrir una cara ya cerrada. */
internal class MediaCameraBindingLease {
    private var active = true
    fun close() { active = false }
    fun bindIfActive(bind: () -> Unit) {
        if (active) bind()
    }
}

/**
 * ¿Debe estar unida la cámara de «Modo visual»? Solo con permiso, con la página asentada (la activa de la sesión) y con controlador de
 * captura. Cuando la página deja de ser la activa, la cámara se suelta (salvo que se esté grabando: ver [MediaCameraSession.release]).
 */
internal fun isMediaCameraActive(permissionGranted: Boolean, isSettledPage: Boolean, hasCapture: Boolean): Boolean =
    permissionGranted && isSettledPage && hasCapture

/**
 * Lo que pasa cuando la página de la tarjeta deja de ser la activa (LV8): se corta la grabación en marcha y el «Modo visual» expandido se
 * pliega. Con la página asentada no se hace nada.
 */
internal fun onMediaPageSettledChanged(isSettledPage: Boolean, stopIfRecording: () -> Unit, foldExpanded: () -> Unit) {
    if (isSettledPage) return
    stopIfRecording()
    foldExpanded()
}

/**
 * Une la cámara de [mediaCapture] a la ventana mientras [active] (la cara está a la vista, la página es la activa y hay permiso) y la
 * suelta al salir, salvo que se esté grabando. Devuelve la sesión: las vistas previas se le acoplan con [MediaCameraSurface].
 */
@Composable
internal fun rememberMediaCameraSession(
    mediaCapture: WorkoutMediaCaptureController?,
    active: Boolean,
    isRecording: Boolean,
    bindGeneration: Int,
    onBindFailed: () -> Unit,
    stopOnDispose: Boolean = true,
): MediaCameraSession {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val idleChoice = remember { kotlinx.coroutines.flow.MutableStateFlow(WorkoutCameraChoice()) }
    val cameraChoice by (mediaCapture?.cameraChoice ?: idleChoice).collectAsStateWithLifecycle()
    val lens = cameraChoice.lens
    // Sin controlador de captura no hay cámara: tampoco se crea ningún objeto de CameraX.
    val preview = remember(mediaCapture, active, bindGeneration, lifecycleOwner, lens) { mediaCapture?.let { Preview.Builder().build() } }
    val recording by rememberUpdatedState(isRecording)
    val shouldStopOnDispose by rememberUpdatedState(stopOnDispose)
    val bindFailed by rememberUpdatedState(onBindFailed)
    val session = remember(preview) {
        MediaCameraSession(showPreviewOn = { view -> preview?.surfaceProvider = (view as? PreviewView)?.surfaceProvider })
    }
    DisposableEffect(lifecycleOwner, mediaCapture, active, bindGeneration, lens) {
        val capture = mediaCapture
        if (!active || capture == null || preview == null) return@DisposableEffect onDispose { }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val lease = MediaCameraBindingLease()
        // La calidad puede sustituir estos casos de uso: el cierre debe soltar exactamente los que unió.
        val imageCapture = capture.imageCapture
        val videoCapture = capture.videoCapture
        fun releaseOwned() {
            if (!providerFuture.isDone) return
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: return
            if (session.release(isRecording = false) && provider.isBound(preview)) {
                provider.unbind(preview, imageCapture, videoCapture)
            }
        }
        val listener = Runnable {
            lifecycleOwner.lifecycleScope.launch {
                // Una página nueva puede asentarse mientras CameraX aún finaliza el vídeo anterior.
                capture.isRecording.first { !it }
                lease.bindIfActive {
                    val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@bindIfActive
                    val back = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val front = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    capture.updateCameraAvailability(back, front)
                    val selected = cameraChoice.detected(back, front).lens
                    val bound = runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            if (selected == WorkoutCameraLens.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            videoCapture,
                        )
                    }
                    if (bound.isFailure) bindFailed() else session.onBound()
                }
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            lease.close()
            session.stopOwnedRecording(shouldStopOnDispose, capture::stopIfRecording)
            if (shouldStopOnDispose && recording) {
                // Espera Finalize fuera del ámbito de la composición: desmontar la tarjeta no corta el archivo.
                lifecycleOwner.lifecycleScope.launch {
                    capture.isRecording.first { !it }
                    releaseOwned()
                }
            } else {
                releaseOwned()
            }
        }
    }
    return session
}

/** La vista previa de la cámara: se acopla a la [session] y, al salir, se desacopla sin soltar la cámara (la nueva vista la releva). */
@Composable
internal fun MediaCameraSurface(session: MediaCameraSession, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf<PreviewView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                view = this
            }
        },
    )
    DisposableEffect(view) {
        val current = view
        if (current != null) session.attach(current)
        onDispose { if (current != null) session.detach(current) }
    }
}
