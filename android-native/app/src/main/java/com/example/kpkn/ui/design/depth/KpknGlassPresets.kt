package com.example.kpkn.ui.design.depth

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/*
 * PROFUNDIDAD · vidrio (`backdrop-filter`) y filtros (`filter`): los 7 vidrios y los 5 filtros del catálogo.
 *
 * Equivalencias (calibradas y verificadas en `paridad/CALIBRACION_DEPTH.md`):
 *  - `backdrop-filter: blur(B)` → la gaussiana de CSS tiene σ = B px. Haze 1.7.2 pasa `blurRadius.toPx()` TAL CUAL a `RenderEffect.createBlurEffect(σ, σ)`
 *    (se comprobó en el bytecode: `RenderEffect_androidKt.createRenderEffect`), así que `HazeStyle.blurRadius = B.dp`: factor 1,0 ([KpknGlassCalibration.HazeBlurPerCssBlur]).
 *  - `saturate(s)` / `brightness(b)` → la matriz de CSS (los mismos pesos de luminancia 0,213 / 0,715 / 0,072 que `ColorMatrix.setSaturation`), aplicada con una capa
 *    de color sobre el vidrio ([kpknBackdropAdjust]); funciona en todas las versiones, incluso en un lienzo de software.
 *  - Haze solo desenfoca desde Android 12 (API 31): por debajo cae al velo de respaldo de `KpknGlass` (decisión aprobada: el vidrio se calibra en Android 12+).
 *
 *   // 1. Vidrio de preset con desenfoque de fondo:  Box(Modifier.fillMaxWidth().height(72.dp).kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, shape))
 *   // 2. Los mismos presets en las funciones de siempre (los llamadores actuales no cambian):  Modifier.kpknGlassOrFallback(hazeState, shape, preset = KpknGlassPreset.VidrioHoja)
 *   // 3. Un filtro (foto apagada, halo desenfocado):  Image(painter, null, Modifier.kpknFilter(KpknFilterPreset.FotoApagada))
 *
 * El vidrio del lienzo no lleva tinte (solo desenfoque, saturación y brillo): si la pantalla quiere un relleno, lo pone con un degradado `vidrio-relleno-*`
 * de `KpknGradient` encima. Si el nodo tiene hijos, la saturación y el brillo también les afectan: pon el vidrio en un nodo de fondo sin contenido.
 */

/** Los 7 vidrios (`backdrop-filter`) del catálogo. [blur] es el valor CSS en px (= dp). */
enum class KpknGlassPreset(val catalogName: String, val blur: Dp, val saturation: Float = 1f, val brightness: Float = 1f) {
    /** `blur(22px) saturate(1.5)` · 9 filas (isla de la píldora, de las pestañas, del editor…). */
    VidrioIsla("vidrio-isla", 22.dp, 1.5f),

    /** `blur(26px) saturate(1.3)` · 5 filas. */
    VidrioHoja("vidrio-hoja", 26.dp, 1.3f),

    /** `blur(18px) saturate(1.8) brightness(0.9)` · 5 filas. */
    VidrioBarra("vidrio-barra", 18.dp, 1.8f, 0.9f),

    /** `blur(24px) saturate(1.7)` · 2 filas. */
    VidrioEditor("vidrio-editor", 24.dp, 1.7f),

    /** `blur(12px)` · 4 filas. */
    VeloDesenfoque("velo-desenfoque", 12.dp),

    /** `blur(16px) saturate(1.4)` · 1 fila. */
    VidrioInterruptor("vidrio-interruptor", 16.dp, 1.4f),

    /** `blur(6px)` · 2 filas. */
    VeloSuave("velo-suave", 6.dp),
    ;

    companion object {
        fun fromCatalogName(name: String): KpknGlassPreset? = entries.firstOrNull { it.catalogName == name }
    }
}

/** Los 5 filtros (`filter`) del catálogo. [blur] es la σ de CSS en px (= dp). */
enum class KpknFilterPreset(val catalogName: String, val blur: Dp = 0.dp, val saturation: Float = 1f, val brightness: Float = 1f) {
    /** `saturate(0.8)` · 1 fila. */
    FotoApagada("foto-apagada", saturation = 0.8f),

    /** `blur(20px)` · 1 fila (halo de color detrás de una tarjeta). */
    HaloDesenfoque("halo-desenfoque", blur = 20.dp),

    /** `blur(26px)` · 1 fila. */
    ResplandorDesenfoque("resplandor-desenfoque", blur = 26.dp),

    /** `brightness(0.62) saturate(0.8)` · 2 filas (vídeo detrás de controles). */
    VideoApagado("video-apagado", saturation = 0.8f, brightness = 0.62f),

    /** `brightness(0.9)` · 3 filas. */
    MiniaturaApagada("miniatura-apagada", brightness = 0.9f),
    ;

    companion object {
        fun fromCatalogName(name: String): KpknFilterPreset? = entries.firstOrNull { it.catalogName == name }
    }
}

/** Constantes de equivalencia CSS → Haze/Android del vidrio. */
object KpknGlassCalibration {
    /** `HazeStyle.blurRadius` (dp) por cada px de `blur()` de CSS: ambos son la σ de la gaussiana, y Haze la pasa tal cual a `RenderEffect`. */
    const val HazeBlurPerCssBlur = 1.0f

    /** Pesos de luminancia de `saturate()` en CSS (los mismos de `ColorMatrix.setSaturation`). */
    const val LumR = 0.213f
    const val LumG = 0.715f
    const val LumB = 0.072f
}

/** El estilo de Haze del [preset]: solo desenfoque (sin tinte ni ruido), con el velo de respaldo de `KpknGlass` donde no hay desenfoque. */
fun kpknGlassPresetStyle(preset: KpknGlassPreset, tint: Color = Color.Transparent): HazeStyle = HazeStyle(
    backgroundColor = Color.Transparent,
    tints = if (tint.alpha > 0f) listOf(HazeTint(tint)) else emptyList(),
    blurRadius = (preset.blur.value * KpknGlassCalibration.HazeBlurPerCssBlur).dp,
    noiseFactor = 0f,
    fallbackTint = HazeTint(KpknGlass.VeilFallback),
)

/** La matriz de `saturate(s) brightness(b)` de CSS (4 × 5, traslación en la última columna, canales 0..255). */
fun kpknSaturateBrightnessMatrix(saturation: Float, brightness: Float): FloatArray {
    val s = saturation
    val b = brightness
    val lr = KpknGlassCalibration.LumR
    val lg = KpknGlassCalibration.LumG
    val lb = KpknGlassCalibration.LumB
    return floatArrayOf(
        b * (lr + (1 - lr) * s), b * (lg - lg * s), b * (lb - lb * s), 0f, 0f,
        b * (lr - lr * s), b * (lg + (1 - lg) * s), b * (lb - lb * s), 0f, 0f,
        b * (lr - lr * s), b * (lg - lg * s), b * (lb + (1 - lb) * s), 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/**
 * `filter: saturate(s) brightness(b)` sobre TODO lo que hay dentro de este nodo (y por debajo en la cadena). Va antes de `clip`/el vidrio para que
 * afecte al desenfoque de fondo. Sin cambios (s = 1 y b = 1) no hace nada.
 */
fun Modifier.kpknBackdropAdjust(saturation: Float, brightness: Float): Modifier {
    if (saturation == 1f && brightness == 1f) return this
    // CSS aplica cada función por separado y recorta a 0..1 entre una y otra: con saturate > 1 y brightness < 1 (vidrio-barra) no se pueden fundir en una sola
    // matriz (el azul sobresaturado se recortaría después de bajar el brillo). Entonces van dos capas: la saturación dentro y el brillo fuera.
    val nested = saturation > 1f && brightness != 1f
    return this.drawWithContent {
        val bounds = Rect(0f, 0f, size.width, size.height)
        fun layerPaint(m: FloatArray) = Paint().also { it.colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m)) }
        drawIntoCanvas { canvas ->
            if (nested) {
                canvas.saveLayer(bounds, layerPaint(kpknSaturateBrightnessMatrix(1f, brightness)))
                canvas.saveLayer(bounds, layerPaint(kpknSaturateBrightnessMatrix(saturation, 1f)))
                drawContent()
                canvas.restore()
                canvas.restore()
            } else {
                canvas.saveLayer(bounds, layerPaint(kpknSaturateBrightnessMatrix(saturation, brightness)))
                drawContent()
                canvas.restore()
            }
        }
    }
}

/**
 * Vidrio del catálogo con desenfoque de fondo de Haze ([hazeState] null o Android < 12: velo de respaldo). Atajo de
 * `kpknGlassOrFallback(hazeState, shape, preset = preset)`.
 */
fun Modifier.kpknGlassPreset(
    hazeState: HazeState?,
    preset: KpknGlassPreset,
    shape: Shape,
    withBorder: Boolean = false,
    additionalScrim: Color = Color.Transparent,
): Modifier = kpknGlassOrFallback(
    hazeState = hazeState,
    shape = shape,
    withBorder = withBorder,
    additionalScrim = additionalScrim,
    variant = KpknGlassVariant.ISLAND,
    preset = preset,
)

/**
 * `filter` del catálogo sobre este nodo: saturación y brillo con una capa de color (todas las versiones), desenfoque con `RenderEffect` (Android 12+;
 * por debajo se ignora, como el vidrio). El desenfoque tiene los bordes transparentes (`TileMode.Decal`), igual que `filter: blur()` en CSS.
 */
fun Modifier.kpknFilter(preset: KpknFilterPreset): Modifier {
    val adjusted = this.kpknBackdropAdjust(preset.saturation, preset.brightness)
    if (preset.blur.value <= 0f) return adjusted
    return adjusted.graphicsLayer {
        val sigma = preset.blur.toPx()
        renderEffect = BlurEffect(sigma, sigma, TileMode.Decal)
    }
}
