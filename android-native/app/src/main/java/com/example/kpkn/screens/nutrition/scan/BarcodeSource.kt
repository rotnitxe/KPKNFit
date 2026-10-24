package com.example.kpkn.screens.nutrition.scan

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.kpkn.domain.nutrition.Gtin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * De dónde llegan las lecturas de códigos. La cámara real ([CameraBarcodeSource]) y la fuente guiada por código
 * ([ScriptedBarcodeSource], para pruebas y vistas previas sin cámara) cumplen el mismo contrato, así que el modelo del escáner no
 * sabe cuál tiene delante.
 */
interface BarcodeSource {
    /** True si el aparato tiene linterna que se pueda encender; puede tardar en saberse (la cámara se abre de forma asíncrona). */
    val torchAvailable: StateFlow<Boolean>

    /** Empieza a entregar lecturas crudas por [onReading] (en el hilo principal). Si no puede arrancar, avisa por [onFailure]. */
    fun start(onReading: (String) -> Unit, onFailure: () -> Unit = {})

    /** Para las lecturas y libera la cámara. Se puede llamar varias veces. */
    fun stop()

    fun setTorch(enabled: Boolean)
}

/**
 * Fuente controlada desde fuera: [emit] simula que el lector vio un código. La usan las pruebas, la vista previa de depuración y la
 * hoja de render; también cuenta cuántas veces se arrancó y se paró para comprobar que el escáner la libera.
 */
class ScriptedBarcodeSource(torch: Boolean = true) : BarcodeSource {
    private val torch = MutableStateFlow(torch)
    override val torchAvailable: StateFlow<Boolean> = this.torch.asStateFlow()

    var running: Boolean = false
        private set
    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set
    var torchOn: Boolean = false
        private set
    private var listener: ((String) -> Unit)? = null
    private var failure: (() -> Unit)? = null

    override fun start(onReading: (String) -> Unit, onFailure: () -> Unit) {
        running = true
        startCount++
        listener = onReading
        failure = onFailure
    }

    override fun stop() {
        if (running) stopCount++
        running = false
        listener = null
        failure = null
        torchOn = false
    }

    override fun setTorch(enabled: Boolean) {
        torchOn = enabled && torch.value
    }

    /** El lector «ve» [raw]. Se ignora si la fuente está parada, igual que la cámara real. */
    fun emit(raw: String) {
        listener?.invoke(raw)
    }

    /** Simula que la cámara no se pudo abrir. */
    fun fail() {
        failure?.invoke()
    }
}

/**
 * La cámara trasera con CameraX: vista previa + análisis de fotogramas con [decoder] (ML Kit empaquetado). Nada sale del teléfono:
 * ni los fotogramas ni los códigos. El enlace con el ciclo de vida se hace con [attach] cuando la vista previa ya existe; [start] y
 * [stop] pueden llegar antes o después y el resultado es el mismo (la cámara corre mientras haya vista, dueño y lector pedido).
 */
class CameraBarcodeSource(
    private val context: Context,
    private val decoder: BarcodeDecoder,
) : BarcodeSource {
    private val torch = MutableStateFlow(false)
    override val torchAvailable: StateFlow<Boolean> = torch.asStateFlow()

    private var previewView: PreviewView? = null
    private var owner: LifecycleOwner? = null
    private var onReading: ((String) -> Unit)? = null
    private var onFailure: (() -> Unit)? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var executor: ExecutorService? = null
    private var scope: CoroutineScope? = null
    private var binding = false
    private var torchWanted = false

    /** Une la vista previa y el ciclo de vida de la pantalla. Repetirlo con los mismos objetos no hace nada. */
    fun attach(view: PreviewView, lifecycleOwner: LifecycleOwner) {
        previewView = view
        owner = lifecycleOwner
        bindIfWanted()
    }

    /** Suelta la vista previa y el ciclo de vida (la vista se destruye). Libera la cámara. */
    fun detach() {
        release()
        previewView = null
        owner = null
    }

    override fun start(onReading: (String) -> Unit, onFailure: () -> Unit) {
        this.onReading = onReading
        this.onFailure = onFailure
        bindIfWanted()
    }

    override fun stop() {
        onReading = null
        onFailure = null
        release()
    }

    override fun setTorch(enabled: Boolean) {
        torchWanted = enabled
        camera?.takeIf { torch.value }?.cameraControl?.enableTorch(enabled)
    }

    private fun bindIfWanted() {
        val view = previewView ?: return
        val lifecycleOwner = owner ?: return
        if (onReading == null || analysis != null || binding) return
        binding = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            binding = false
            val provider = runCatching { future.get() }.getOrNull()
            // Se paró (o se soltó la vista) mientras la cámara se abría: no se enlaza nada.
            if (onReading == null || analysis != null || previewView !== view || owner !== lifecycleOwner) return@addListener
            if (provider == null || !runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)) {
                onFailure?.invoke()
                return@addListener
            }
            val analysisExecutor = Executors.newSingleThreadExecutor()
            val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysisUseCase = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysisUseCase.setAnalyzer(analysisExecutor) { frame ->
                if (!analysisScope.isActive) {
                    frame.close()
                    return@setAnalyzer
                }
                analysisScope.launch {
                    try {
                        val codes = decoder.decode(frame)
                        val best = codes.firstOrNull { Gtin.isValid(it) } ?: codes.firstOrNull()
                        if (best != null) withContext(Dispatchers.Main) { this@CameraBarcodeSource.onReading?.invoke(best) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Un fotograma que no se pudo leer no detiene el lector: llega el siguiente.
                    } finally {
                        frame.close()
                    }
                }
            }
            try {
                provider.unbind(previewUseCase, analysisUseCase)
                val bound = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, analysisUseCase)
                cameraProvider = provider
                camera = bound
                preview = previewUseCase
                analysis = analysisUseCase
                executor = analysisExecutor
                scope = analysisScope
                torch.value = bound.cameraInfo.hasFlashUnit()
                if (torchWanted && torch.value) bound.cameraControl.enableTorch(true)
            } catch (error: Exception) {
                analysisScope.cancel()
                analysisExecutor.shutdown()
                onFailure?.invoke()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun release() {
        val provider = cameraProvider
        val useCases = listOfNotNull<androidx.camera.core.UseCase>(preview, analysis)
        analysis?.clearAnalyzer()
        if (provider != null && useCases.isNotEmpty()) runCatching { provider.unbind(*useCases.toTypedArray()) }
        camera?.cameraControl?.runCatching { enableTorch(false) }
        scope?.cancel()
        executor?.shutdown()
        cameraProvider = null
        camera = null
        preview = null
        analysis = null
        executor = null
        scope = null
        torch.value = false
        binding = false
    }
}
