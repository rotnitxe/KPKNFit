package com.example.kpkn.data.media.technique

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/**
 * Los metadatos de la captura de un vídeo (§3.8), que el grabador (VT8a) escribe al terminar y el analizador lee antes de
 * decodificar. TODOS los campos son opcionales: un vídeo de la galería no trae ninguno.
 *
 * Viven en `<id>.capture.json`, el mismo archivo donde `CaptureOverridesFile` guarda lo que decide el usuario
 * (`userProfileOverride`, `plateSeed` y `plateDiameterMm`). Cada uno cambia SOLO sus claves y respeta las demás, así que
 * escribir uno nunca pierde lo del otro.
 *
 * @property requestedFps fps que se pidieron a la cámara.
 * @property grantedFps fps que concedió (puede ser menos que los pedidos).
 * @property gravityRollDeg inclinación lateral del móvil (convenio de `FrameGeometry.rollDeg`), mediana de la grabación.
 * @property gravityPitchDeg inclinación hacia delante o atrás del móvil.
 * @property focalLengthPx distancia focal en PÍXELES DEL VÍDEO GRABADO (el tamaño ya girado, `VideoInfo.displayWidth` ×
 *   `displayHeight`); el analizador la lleva a los píxeles del fotograma de análisis, que es más pequeño.
 * @property lens la lente usada (`main`, `ultrawide`…).
 * @property deviceModel el modelo del dispositivo que grabó.
 * @property recordedAtMs cuándo empezó la grabación (ms desde 1970).
 */
data class CaptureMetadata(
    val requestedFps: Float? = null,
    val grantedFps: Float? = null,
    val gravityRollDeg: Float? = null,
    val gravityPitchDeg: Float? = null,
    val focalLengthPx: Float? = null,
    val lens: String? = null,
    val deviceModel: String? = null,
    val recordedAtMs: Long? = null,
) {
    companion object {
        const val KEY_REQUESTED_FPS = "requestedFps"
        const val KEY_GRANTED_FPS = "grantedFps"
        const val KEY_ROLL = "gravityRollDeg"
        const val KEY_PITCH = "gravityPitchDeg"
        const val KEY_FOCAL = "focalLengthPx"
        const val KEY_LENS = "lens"
        const val KEY_DEVICE = "deviceModel"
        const val KEY_RECORDED_AT = "recordedAtMs"
    }
}

/**
 * Lee y escribe [CaptureMetadata] en `<id>.capture.json` SIN tocar las demás claves (las del usuario, o lo que añada una versión
 * posterior). El archivo puede no existir o estar ilegible: entonces se lee como «sin dato» y se crea al escribir.
 *
 * ```
 * {"requestedFps":60.0,"grantedFps":60.0,"gravityRollDeg":-1.2,"gravityPitchDeg":84.0,"focalLengthPx":1480.0,
 *  "lens":"main","deviceModel":"SM-F731B","recordedAtMs":1780000000000,"plateDiameterMm":450}
 * ```
 *
 * E/S bloqueante: llamar desde `Dispatchers.IO`. La escritura es atómica ([TechniqueSidecars.writeAtomically]) y el
 * leer-cambiar-escribir va en una sección crítica de proceso. `CaptureOverridesFile` tiene la suya: las dos escrituras no se
 * excluyen entre sí. En la práctica no coinciden (el grabador escribe al terminar, antes de que exista un análisis, y el usuario
 * fija sus elecciones después) y el analizador NUNCA escribe aquí.
 */
object CaptureMetadataFile {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private val empty = JsonObject(emptyMap())

    /** Los metadatos del archivo; vacíos si falta, está en blanco o no se entiende. Nunca lanza. */
    fun read(file: File): CaptureMetadata = synchronized(lock) { parse(readObject(file)) }

    /**
     * Aplica [change] a lo que hay y lo escribe: un campo con valor se guarda y un campo `null` borra su clave. Lanza
     * [IOException] si no se puede guardar.
     */
    fun update(file: File, change: (CaptureMetadata) -> CaptureMetadata) {
        synchronized(lock) {
            val current = readObject(file)
            val next = change(parse(current))
            val merged = LinkedHashMap<String, JsonElement>(current)
            merged.setOrRemove(CaptureMetadata.KEY_REQUESTED_FPS, next.requestedFps?.finiteOrNull()?.let { JsonPrimitive(it) })
            merged.setOrRemove(CaptureMetadata.KEY_GRANTED_FPS, next.grantedFps?.finiteOrNull()?.let { JsonPrimitive(it) })
            merged.setOrRemove(CaptureMetadata.KEY_ROLL, next.gravityRollDeg?.finiteOrNull()?.let { JsonPrimitive(it) })
            merged.setOrRemove(CaptureMetadata.KEY_PITCH, next.gravityPitchDeg?.finiteOrNull()?.let { JsonPrimitive(it) })
            merged.setOrRemove(CaptureMetadata.KEY_FOCAL, next.focalLengthPx?.finiteOrNull()?.let { JsonPrimitive(it) })
            merged.setOrRemove(CaptureMetadata.KEY_LENS, next.lens?.let { JsonPrimitive(it) })
            merged.setOrRemove(CaptureMetadata.KEY_DEVICE, next.deviceModel?.let { JsonPrimitive(it) })
            merged.setOrRemove(CaptureMetadata.KEY_RECORDED_AT, next.recordedAtMs?.let { JsonPrimitive(it) })
            TechniqueSidecars.writeAtomically(file, JsonObject(merged).toString().toByteArray(Charsets.UTF_8))
        }
    }

    /** Guarda [metadata]: sus campos con valor sustituyen a los del archivo, los `null` borran su clave y el resto se respeta. */
    fun write(file: File, metadata: CaptureMetadata) = update(file) { metadata }

    private fun Float.finiteOrNull(): Float? = takeIf { it.isFinite() }

    private fun MutableMap<String, JsonElement>.setOrRemove(key: String, value: JsonElement?) {
        if (value == null) remove(key) else put(key, value)
    }

    private fun readObject(file: File): JsonObject {
        if (!file.isFile) return empty
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: IOException) {
            return empty
        }
        if (text.isBlank()) return empty
        return try {
            json.parseToJsonElement(text) as? JsonObject ?: empty
        } catch (_: IllegalArgumentException) {
            // SerializationException hereda de IllegalArgumentException: un sidecar ilegible solo significa «sin dato».
            empty
        }
    }

    private fun parse(obj: JsonObject): CaptureMetadata = CaptureMetadata(
        requestedFps = obj.float(CaptureMetadata.KEY_REQUESTED_FPS),
        grantedFps = obj.float(CaptureMetadata.KEY_GRANTED_FPS),
        gravityRollDeg = obj.float(CaptureMetadata.KEY_ROLL),
        gravityPitchDeg = obj.float(CaptureMetadata.KEY_PITCH),
        focalLengthPx = obj.float(CaptureMetadata.KEY_FOCAL),
        lens = obj.text(CaptureMetadata.KEY_LENS),
        deviceModel = obj.text(CaptureMetadata.KEY_DEVICE),
        recordedAtMs = (obj[CaptureMetadata.KEY_RECORDED_AT] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull,
    )

    /** Un número finito; un texto, un `null` o un valor que no es un número vale «sin dato». */
    private fun JsonObject.float(key: String): Float? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
}
