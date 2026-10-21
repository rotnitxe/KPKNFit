package com.example.kpkn.data.media.video

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lo que se sabe de un vídeo antes de decodificarlo (§3.7).
 *
 * [rotationDegrees] es el giro horario (0/90/180/270) que pide el contenedor; [displayWidth]×[displayHeight] es el tamaño ya girado.
 * [nominalFps] sale de la pista (o de la mediana de los PTS de las primeras muestras) y [captureFps] solo existe en cámara lenta.
 */
data class VideoInfo(
    val codedWidth: Int,
    val codedHeight: Int,
    val rotationDegrees: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val durationUs: Long,
    val mime: String,
    val nominalFps: Float?,
    val captureFps: Float?,
    val isHdr: Boolean,
)

/** Qué se quiere de cada fotograma. Con [SKIP] ni se dibuja ni se lee. */
enum class FrameNeeds { SKIP, RGBA, LUMA, RGBA_AND_LUMA }

/**
 * Un fotograma ya girado, del pool: vale SOLO hasta que vuelve `onFrame` (el pool reutiliza los buffers por turnos).
 * [rgba] es directo, con `stride = width * 4` y las filas de arriba abajo; [luma] lleva `width * height` bytes. Cada uno es
 * `null` si [FrameNeeds] no lo pidió. [index] cuenta desde 0 los fotogramas con PTS ≥ `fromUs` de esa llamada.
 */
class DecodedFrame(
    val index: Int,
    val ptsUs: Long,
    val width: Int,
    val height: Int,
    val rgba: ByteBuffer?,
    val luma: ByteArray?,
)

/**
 * Cuatro buffers RGBA directos (y su luma) que se reutilizan por turnos: la decodificación no asigna nada por fotograma. Con un
 * `Channel(C)` entre la decodificación y el consumidor llegan a estar vivos C + 2 fotogramas (el que procesa el consumidor, los C de
 * la cola y el que espera en `send`), así que hacen falta `SLOTS ≥ C + 2`: con `Channel(2)`, cuatro. El contrato sigue siendo
 * «válido solo dentro de `onFrame`»; lo que deba vivir más se COPIA.
 */
internal class FramePool(val width: Int, val height: Int, private val slots: Int = SLOTS) {
    /** Píxeles de un fotograma. */
    val pixelCount: Int = width * height

    private val rgba = Array(slots) { ByteBuffer.allocateDirect(pixelCount * 4).order(ByteOrder.nativeOrder()) }
    private val luma = arrayOfNulls<ByteArray>(slots)
    private var scratch: ByteArray? = null
    private var cursor = 0

    /** El siguiente hueco por turno. */
    fun nextSlot(): Int {
        val slot = cursor
        cursor = (cursor + 1) % slots
        return slot
    }

    /** El buffer RGBA del hueco, vacío (posición 0, límite = capacidad). */
    fun rgba(slot: Int): ByteBuffer = rgba[slot].also { it.clear() }

    /** La luma del hueco, sin rellenar. */
    fun luma(slot: Int): ByteArray = luma[slot] ?: ByteArray(pixelCount).also { luma[slot] = it }

    /** Calcula la luma del hueco desde su buffer RGBA ya relleno (copia en bloque a un array de montón y recorre ese). */
    fun fillLuma(slot: Int): ByteArray {
        val source = rgba[slot]
        val copy = scratch ?: ByteArray(pixelCount * 4).also { scratch = it }
        source.clear()
        source.get(copy, 0, pixelCount * 4)
        source.clear()
        val target = luma(slot)
        VideoFrameMath.lumaFromRgba(copy, pixelCount, target)
        return target
    }

    companion object {
        const val SLOTS = 4
    }
}
