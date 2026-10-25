package com.example.kpkn.screens.progress

import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.training.MuscleVolumePanel
import com.example.kpkn.domain.training.MuscleVolumeScale
import com.example.kpkn.domain.training.VolumeShade
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.programdetail.design.programVolumeSeries
import com.example.kpkn.screens.progress.design.MuscleVolumeBodyRegions
import java.time.LocalDate

/** Cómo se pinta una región del cuerpo: sin datos (trazo discontinuo), cero real (trazo liso y vacío) o relleno con intensidad. */
enum class RegionKind { NO_DATA, ZERO, FILLED }

/** Pintura de una región. [fill] y [edge] ya llevan su opacidad; en `NO_DATA` y `ZERO` el relleno es transparente. */
data class RegionPaint(val kind: RegionKind, val fill: Color, val edge: Color, val level: Int = 0)

/** Color de las series registradas, con cinco escalones de un mismo tono. */
object MuscleVolumeBodyModel {
    private val volumeTone = SymbolPalette.mente

    /** Opacidad del relleno por escalón L1…L5: crece de forma visible y L5 no tapa el trazo de selección. */
    private val volumeAlpha = listOf(0.22f, 0.38f, 0.55f, 0.75f, 0.95f)

    /** Músculos con una región en el cuerpo; el resto (Cuello, Antebrazo, Aductores, Romboides) sale como chips bajo el cuerpo. */
    val bodyRegions: List<String> get() = MuscleVolumeBodyRegions

    fun volumePaint(shade: VolumeShade): RegionPaint = when (shade) {
        VolumeShade.NO_DATA -> RegionPaint(RegionKind.NO_DATA, Color.Transparent, Color.White.copy(alpha = 0.16f))
        VolumeShade.ZERO -> RegionPaint(RegionKind.ZERO, Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.34f))
        else -> {
            val level = shade.ordinal - VolumeShade.L1.ordinal
            RegionPaint(RegionKind.FILLED, volumeTone.copy(alpha = volumeAlpha[level]), volumeTone.copy(alpha = 0.7f), level + 1)
        }
    }

    /** Pintura de cada músculo del panel. Sin panel o sin datos en la ventana, todas las regiones salen «sin datos». */
    fun volumePaints(panel: MuscleVolumePanel?): Map<String, RegionPaint> =
        MuscleVolumeBodyRegions.associateWith { name ->
            volumePaint(panel?.row(name)?.shade ?: VolumeShade.NO_DATA)
        }

    /** Color de muestra de un escalón para la leyenda (el mismo que se pinta en el cuerpo). */
    fun swatch(shade: VolumeShade): RegionPaint = volumePaint(shade)

    /** Descripción hablada del mapa de volumen: lo que más pesa, lo que está a cero y lo que no tiene datos. */
    fun volumeSummary(panel: MuscleVolumePanel?): String {
        if (panel == null || !panel.hasData) return "Cuerpo por series registradas: sin datos en esta ventana."
        val unit = panel.unit
        val top = panel.rows.filter { (it.sets ?: 0.0) > 0.0 }.take(3)
            .joinToString(", ") { "${it.muscleName} ${programVolumeSeries(it.sets ?: 0.0)}" }
        val zero = panel.rows.count { it.sets == 0.0 }
        val head = "Cuerpo por series registradas, en $unit."
        return buildString {
            append(head)
            if (top.isNotEmpty()) append(" Más trabajados: ").append(top).append('.')
            if (zero > 0) append(" $zero grupos sin series.")
        }
    }

    private val monthNames = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** «21 oct», sin depender de la configuración regional del dispositivo. */
    fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${monthNames[date.monthValue - 1]}"

    /** Rango de la ventana en una línea: «24 sep – 21 oct». */
    fun rangeText(panel: MuscleVolumePanel): String = "${shortDate(panel.window.start)} – ${shortDate(panel.window.endInclusive)}"

    /** Texto de la ventana pedida: «4 semanas» / «1 semana». */
    fun windowText(weeks: Int): String = if (weeks == 1) "1 semana" else "$weeks semanas"

    /** Rótulo de la leyenda de volumen, con su unidad. */
    fun legendTitle(panel: MuscleVolumePanel): String =
        "Series registradas · ${MuscleVolumeScale.unitLabel(panel.request.averagePerWeek)}"
}
