package com.example.kpkn.screens.nutrition.scan

import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.BarcodeOrigin
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.Gtin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del permiso de cámara: sin decidir (se explica y se pide), concedido o denegado (queda solo la entrada manual). */
enum class CameraAccess { ASK, GRANTED, DENIED }

/** En qué punto va el escáner: leyendo, consultando la base del teléfono o mostrando el resultado. */
enum class ScanPhase { SCANNING, SEARCHING, RESULT }

data class BarcodeScanUiState(
    val access: CameraAccess = CameraAccess.ASK,
    /** La persona eligió teclear el código aunque la cámara esté disponible. */
    val manualRequested: Boolean = false,
    /** La cámara no se pudo abrir (sin cámara trasera o error al enlazarla): igual que sin permiso, solo manual. */
    val cameraFailed: Boolean = false,
    val manualText: String = "",
    val manualError: String? = null,
    val torchOn: Boolean = false,
    /** Aviso breve bajo el visor (p. ej. que falló la consulta). */
    val notice: String? = null,
    val phase: ScanPhase = ScanPhase.SCANNING,
    /** El código aceptado, en su forma canónica de 13 dígitos. */
    val code: String? = null,
    val result: BarcodeLookup? = null,
) {
    /** True si la entrada manual es lo único que hay: sin permiso concedido o con la cámara rota. */
    val manualOnly: Boolean get() = access == CameraAccess.DENIED || cameraFailed

    /** True si se muestra el campo para teclear en vez del visor. */
    val showManual: Boolean get() = manualOnly || manualRequested

    /** True si la cámara debería estar encendida ahora. */
    val cameraShouldRun: Boolean get() = access == CameraAccess.GRANTED && !showManual && phase == ScanPhase.SCANNING
}

/**
 * La lógica del escáner, sin cámara ni pantalla. Recibe las lecturas crudas de una [BarcodeSource] y solo acepta un código cuando es
 * válido (`Gtin`) y llega el MISMO en [REQUIRED_CONSECUTIVE] lecturas seguidas dentro de [STREAK_WINDOW_MS]: un ruido, un código
 * distinto o uno inválido rompen la racha. Al aceptar, vibra una vez, apaga y libera la fuente y consulta [lookup] (la base del
 * teléfono). [close] libera la fuente y cancela la consulta en curso.
 */
class BarcodeScanViewModel(
    private val source: BarcodeSource,
    private val lookup: suspend (String) -> BarcodeLookup,
    private val scope: CoroutineScope,
    initialAccess: CameraAccess = CameraAccess.ASK,
    private val onAccepted: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(BarcodeScanUiState(access = initialAccess))
    val state: StateFlow<BarcodeScanUiState> = mutableState.asStateFlow()

    /** Si hay linterna que encender (la cámara real lo sabe al abrirse). */
    val torchAvailable: StateFlow<Boolean> get() = source.torchAvailable

    private var pending: String? = null
    private var pendingCount = 0
    private var pendingAt = 0L
    private var lookupJob: Job? = null
    private var closed = false
    private var sourceRunning = false

    init {
        syncCamera()
    }

    /** La respuesta del sistema a la petición del permiso de cámara. */
    fun onPermissionResult(granted: Boolean) {
        mutableState.update { it.copy(access = if (granted) CameraAccess.GRANTED else CameraAccess.DENIED) }
        syncCamera()
    }

    /**
     * Al volver a la pantalla (p. ej. desde Ajustes, donde el permiso se pudo conceder sin pasar por la petición) se vuelve a leer. Solo
     * cambia si el permiso cambió: concedido pasa a «concedido»; revocado con la pantalla abierta, a «denegado». Sin decidir se queda igual.
     */
    fun onPermissionRechecked(granted: Boolean) {
        val current = mutableState.value.access
        val next = when {
            granted -> CameraAccess.GRANTED
            current == CameraAccess.GRANTED -> CameraAccess.DENIED
            else -> current
        }
        if (next == current) return
        mutableState.update { it.copy(access = next) }
        syncCamera()
    }

    /** La persona prefiere teclear el código. */
    fun showManualEntry() {
        mutableState.update { it.copy(manualRequested = true, manualError = null, notice = null) }
        syncCamera()
    }

    /** Vuelve del campo de texto al visor (solo si hay permiso y la cámara funciona). */
    fun showCamera() {
        mutableState.update { if (it.manualOnly) it else it.copy(manualRequested = false, manualError = null) }
        syncCamera()
    }

    fun onManualTextChange(text: String) {
        // Solo dígitos, espacios y guiones (los que se descartan al validar): pegar un texto cualquiera no deja basura en el campo.
        val cleaned = text.filter { it.isDigit() || it == ' ' || it == '-' }.take(MAX_MANUAL_CHARS)
        mutableState.update { it.copy(manualText = cleaned, manualError = null) }
    }

    /** Valida el código tecleado y, si es válido, lo busca. No hace falta repetirlo: el dígito de control ya lo respalda. */
    fun submitManual() {
        val text = mutableState.value.manualText
        val code = Gtin.normalize(text)
        if (code == null) {
            mutableState.update { it.copy(manualError = manualErrorFor(text)) }
            return
        }
        accept(code)
    }

    fun toggleTorch() {
        if (!source.torchAvailable.value) return
        val next = !mutableState.value.torchOn
        source.setTorch(next)
        mutableState.update { it.copy(torchOn = next) }
    }

    /** Una lectura cruda del lector (la entrega la fuente en el hilo principal). */
    fun onReading(raw: String) {
        val s = mutableState.value
        if (closed || !s.cameraShouldRun) return
        val code = Gtin.normalize(raw)
        if (code == null) {
            // Ruido o código no válido: rompe la racha (nunca se acepta un código de producto sin dígito de control).
            resetStreak()
            return
        }
        val time = now()
        if (code == pending && time - pendingAt <= STREAK_WINDOW_MS) pendingCount++ else {
            pending = code
            pendingCount = 1
        }
        pendingAt = time
        if (pendingCount >= REQUIRED_CONSECUTIVE) accept(code)
    }

    /** «Probar otro»: vuelve a leer. */
    fun scanAnother() {
        lookupJob?.cancel()
        resetStreak()
        mutableState.update {
            it.copy(phase = ScanPhase.SCANNING, code = null, result = null, notice = null, manualText = "", manualError = null, torchOn = false)
        }
        syncCamera()
    }

    /** Tras crear un alimento propio con el código escaneado: lo muestra como encontrado. */
    fun showCreated(food: FoodItem) {
        lookupJob?.cancel()
        mutableState.update {
            it.copy(phase = ScanPhase.RESULT, result = BarcodeLookup.Found(food, BarcodeOrigin.PROPIO), torchOn = false)
        }
        syncCamera()
    }

    /** Libera la cámara y cancela la consulta. Idempotente. */
    fun close() {
        closed = true
        lookupJob?.cancel()
        sourceRunning = false
        source.stop()
    }

    private fun accept(code: String) {
        resetStreak()
        mutableState.update {
            it.copy(phase = ScanPhase.SEARCHING, code = code, result = null, notice = null, manualError = null, torchOn = false)
        }
        syncCamera()
        onAccepted()
        lookupJob?.cancel()
        lookupJob = scope.launch {
            val found = try {
                lookup(code)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (found == null || found == BarcodeLookup.Invalid) {
                // La consulta falló: se vuelve a leer en vez de quedarse esperando.
                mutableState.update {
                    it.copy(phase = ScanPhase.SCANNING, code = null, notice = "No se pudo consultar la base. Inténtalo otra vez.")
                }
                syncCamera()
            } else {
                mutableState.update { it.copy(phase = ScanPhase.RESULT, result = found) }
            }
        }
    }

    private fun resetStreak() {
        pending = null
        pendingCount = 0
    }

    /** Enciende la cámara cuando toca y la apaga cuando no: la fuente corre solo si [BarcodeScanUiState.cameraShouldRun]. */
    private fun syncCamera() {
        if (closed) return
        val wanted = mutableState.value.cameraShouldRun
        if (wanted && !sourceRunning) {
            sourceRunning = true
            source.start(this::onReading, this::onCameraFailure)
        } else if (!wanted && sourceRunning) {
            sourceRunning = false
            source.stop()
        }
    }

    private fun onCameraFailure() {
        mutableState.update { it.copy(cameraFailed = true, notice = "No se pudo abrir la cámara. Escribe el código.") }
        syncCamera()
    }

    private fun manualErrorFor(text: String): String {
        val digits = text.count { it.isDigit() }
        return when {
            digits == 0 -> "Escribe el código de barras."
            digits != 8 && digits != 12 && digits != 13 -> "Un código de producto tiene 8, 12 o 13 dígitos."
            else -> "El código no es válido: revisa los dígitos."
        }
    }

    companion object {
        /** Lecturas iguales y seguidas que hacen falta para aceptar un código de la cámara. */
        const val REQUIRED_CONSECUTIVE = 2

        /** Si pasa más que esto entre dos lecturas, la racha se reinicia. */
        const val STREAK_WINDOW_MS = 1_500L

        private const val MAX_MANUAL_CHARS = 20
    }
}
