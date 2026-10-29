package com.example.kpkn.screens.stories

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionTemplateType
import java.io.File
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * El vídeo de 6 s de la competencia (escena h-meet-video): confeti, estallido de líneas, la medalla que entra girando y el total
 * que cuenta, grabado a 30 fps en 1080 × 1920 (MP4 H.264). Las medidas en px del lienzo de la escena (un teléfono de 393 px) se
 * pasan al de 1080 con [VIDEO_K].
 */

/** Escala de la escena (teléfono de 393 px de ancho) al lienzo de 1080 px. */
private const val VIDEO_K = 1080f / 393f

/** Duración y cuadros por segundo del vídeo. */
internal const val STORY_VIDEO_SECONDS = 6
internal const val STORY_VIDEO_FPS = 30

/** Lo que cuenta la felicitación: eyebrow, cifra que cuenta (o texto fijo), línea de debajo, medalla y pie. */
internal data class CompetitionCelebration(
    val eyebrow: String,
    val headline: String,
    val countTo: Double?,
    val decimals: Int,
    val unit: String?,
    val meta: String?,
    val medal: StoryMedal?,
    val foot: String,
)

/**
 * La felicitación de [record] (solo si ya compitió): «Copa de otoño · oro», el total que cuenta (o el titular de su disciplina) y,
 * si la hay, la línea «Mejor total: +12,5 kg».
 */
internal fun competitionCelebration(
    record: CompetitionRecord,
    history: List<CompetitionRecord>,
    today: LocalDate,
    options: CompetitionStoryOptions,
): CompetitionCelebration? {
    val availability = competitionStoryAvailability(record, history, today)
    val main = availability.templates.firstOrNull { it != CompetitionStoryTemplate.PROXIMA && it != CompetitionStoryTemplate.FOTO }
        ?: availability.templates.firstOrNull { it == CompetitionStoryTemplate.FOTO }
        ?: return null
    val page = competitionStoryPage(main, record, history, today, options)
    val medal = page.medal ?: competitionStoryPage(CompetitionStoryTemplate.FOTO, record, history, today, options).medal
    val lifting = record.sportType != CompetitionTemplateType.RUNNING &&
        record.sportType != CompetitionTemplateType.BODYBUILDING &&
        record.sportType != CompetitionTemplateType.MARTIAL_ARTS
    val total = if (lifting && main == CompetitionStoryTemplate.TOTAL) competitionTotalKg(record) else null
    return CompetitionCelebration(
        eyebrow = listOfNotNull(record.title, medal?.label?.lowercase()).joinToString(" · "),
        headline = page.title,
        countTo = total,
        decimals = if (total != null && total % 1.0 != 0.0) 1 else 0,
        unit = total?.let { "kg" },
        meta = page.badge,
        medal = medal,
        foot = page.foot,
    )
}

private val easeOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val easeFall = CubicBezierEasing(0.3f, 0.6f, 0.4f, 1f)
private val easePop = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)
private val easeCss = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** Avance (0..1) de una animación de [duration] s que empieza en [delay] s, en el instante [t]. */
private fun phase(t: Float, delay: Float, duration: Float): Float = ((t - delay) / duration).coerceIn(0f, 1f)

/** Generador del lienzo (`rng`): s = s·16807 mod (2³¹ − 1). */
private class SceneRandom(seed: Long) {
    private var s = (seed % 2147483647L).let { if (it <= 0L) 7L else it }
    fun next(): Double {
        s = s * 16807L % 2147483647L
        return s.toDouble() / 2147483647.0
    }
}

/** Una pieza de confeti: posición horizontal (0..1), color, retraso (s), ancho y alto (px de la escena) y radio. */
private class Confetto(val left: Float, val color: Color, val delay: Float, val w: Float, val h: Float, val radius: Float)

private val confetti: List<Confetto> = run {
    val r = SceneRandom(5)
    val colors = listOf(Color(0xFFF7CF73), Color(0xFFF49A6E), Color(0xFFF2EEE6), Color(0xFF8FB2FF), Color(0xFF43D18C))
    List(30) { i ->
        val left = r.next().toFloat()
        val delay = Math.round(r.next() * 900).toFloat() / 1000f
        val w = 4f + Math.round(r.next() * 6).toFloat()
        val h = 8f + Math.round(r.next() * 10).toFloat()
        Confetto(left, colors[i % 5], delay, w, h, if (i % 3 != 0) 2f else 6f)
    }
}

/** Un rayo del estallido (en la caja de 520): de [from] a [to], si va en acento. */
private class BurstRay(val from: Offset, val to: Offset, val accent: Boolean)

private val burstRays: List<BurstRay> = run {
    val r = SceneRandom(11)
    val n = 28
    List(n) { i ->
        val a = i.toDouble() / n * PI * 2.0
        val r0 = 70.0 + r.next() * 20.0
        val r1 = 150.0 + r.next() * 95.0
        BurstRay(
            Offset((260 + cos(a) * r0).toFloat(), (260 + sin(a) * r0).toFloat()),
            Offset((260 + cos(a) * r1).toFloat(), (260 + sin(a) * r1).toFloat()),
            accent = i % 5 == 0,
        )
    }
}

/** Formato de la cifra que cuenta: coma decimal y puntos de miles. */
private fun countText(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = Math.round(value * factor) / factor
    val whole = rounded.toLong()
    val text = thousands(whole)
    if (decimals == 0) return text
    val frac = Math.round(abs(rounded - whole) * factor).toString().padStart(decimals, '0')
    return "$text,$frac"
}

/** Pinta el cuadro del instante [t] (s) de la felicitación. */
internal fun DrawScope.drawCompetitionCelebration(celebration: CompetitionCelebration, measurer: TextMeasurer, t: Float) {
    val w = StoryCanvas.WIDTH.toFloat()
    val h = StoryCanvas.HEIGHT.toFloat()
    drawRect(Color.Black, size = Size(w, h))
    // Fondo `.cele`: elipse de 70 % × 50 % centrada en (50 %, 34 %), del oro al 13 % al negro en el 70 %.
    val glowCenter = Offset(w / 2f, h * 0.34f)
    val rx = 0.7f * w
    val ry = 0.5f * h
    scale(scaleX = 1f, scaleY = ry / rx, pivot = glowCenter) {
        drawRect(
            brush = Brush.radialGradient(
                0f to Color(0xFFF7CF73).copy(alpha = 0x22 / 255f),
                0.7f to Color.Black,
                1f to Color.Black,
                center = glowCenter,
                radius = rx,
            ),
            topLeft = Offset(0f, glowCenter.y - h * rx / ry),
            size = Size(w, 2f * h * rx / ry),
        )
    }
    // Confeti: cae 900 px girando 540° y se apaga (2,8 s cada pieza).
    for (piece in confetti) {
        val p = phase(t, piece.delay, 2.8f)
        if (t < piece.delay) continue
        val e = easeFall.transform(p)
        val alpha = 0.9f * (1f - e)
        if (alpha <= 0f) continue
        val pw = piece.w * VIDEO_K
        val ph = piece.h * VIDEO_K
        val x = piece.left * w
        val y = (-20f + 900f * e) * VIDEO_K
        rotate(540f * e, pivot = Offset(x + pw / 2f, y + ph / 2f)) {
            drawRoundRect(piece.color, Offset(x, y), Size(pw, ph), CornerRadius(piece.radius * VIDEO_K), alpha = alpha)
        }
    }
    // Estallido: 28 rayos y 3 círculos que se dibujan escalonados (51,6 ms) en 1,1 s, en una caja de 520 px centrada en (50 %, 30 %).
    val burstOrigin = Offset(w / 2f - 260f * VIDEO_K, h * 0.30f - 260f * VIDEO_K)
    val stagger = minOf(0.06f, 1.6f / 31f)
    burstRays.forEachIndexed { i, ray ->
        val p = easeOut.transform(phase(t, i * stagger, 1.1f))
        if (p <= 0f) return@forEachIndexed
        val from = burstOrigin + ray.from * VIDEO_K
        val to = burstOrigin + ray.to * VIDEO_K
        drawLine(
            color = if (ray.accent) Color(0xFFF7CF73) else StoryCanvas.Ink,
            start = from,
            end = from + (to - from) * p,
            strokeWidth = (if (ray.accent) 2.2f else 1.1f) * VIDEO_K,
            cap = StrokeCap.Round,
            alpha = if (ray.accent) 1f else 0.55f,
        )
    }
    val burstCenter = burstOrigin + Offset(260f, 260f) * VIDEO_K
    for (k in 0 until 3) {
        val p = easeOut.transform(phase(t, (28 + k) * stagger, 1.1f))
        if (p <= 0f) continue
        val radius = (58f + k * 26f) * VIDEO_K
        drawArc(
            color = StoryCanvas.Ink,
            startAngle = 0f,
            sweepAngle = 360f * p,
            useCenter = false,
            topLeft = burstCenter - Offset(radius, radius),
            size = Size(2 * radius, 2 * radius),
            style = Stroke(width = VIDEO_K),
            alpha = 0.18f - k * 0.04f,
        )
    }
    // La medalla entra girando: rotateY de 540° a 0, escala de 0,4 a 1 y opacidad de 0 a 1 en 1,4 s.
    celebration.medal?.let { medal ->
        val p = easeOut.transform(phase(t, 0f, 1.4f))
        val angle = (540f * (1f - p)) * PI.toFloat() / 180f
        val s = 0.4f + 0.6f * p
        val size = 120f * VIDEO_K
        withTransform({ scale(scaleX = s * abs(cos(angle)).coerceAtLeast(0.02f), scaleY = s, pivot = burstCenter) }) {
            drawCssMedal(medal, burstCenter, size, measurer, alpha = p)
        }
    }
    // Textos: eyebrow (aparece en 0,7 s desde 0,6 s), total que cuenta en 1,1 s y entra con un salto desde 0,8 s, y la línea de debajo
    // desde 1,1 s. El pie, fijo abajo.
    val column = StoryCanvas.COLUMN_WIDTH.toInt()
    var y = h * 0.52f
    val eyebrow = measurer.storyText(celebration.eyebrow.upperEs(), ShellType.eyebrow, width = column, maxLines = 1, ellipsis = true)
    drawText(eyebrow, StoryCanvas.Faint, Offset(StoryCanvas.LEFT, y), alpha = easeCss.transform(phase(t, 0.6f, 0.7f)))
    y += eyebrow.size.height + SHELL_NAME_TOP
    val count = celebration.countTo?.let { to ->
        val k = phase(t, 0f, 1.1f)
        countText(to * (1.0 - (1.0 - k).pow(3)), celebration.decimals) + (celebration.unit?.let { " $it" } ?: "")
    } ?: celebration.headline
    val name = measurer.storyText(count, ShellType.name, width = column, maxLines = 2, ellipsis = true)
    val pop = phase(t, 0.8f, 0.9f)
    if (pop > 0f) {
        val e = easePop.transform(pop)
        val pivot = Offset(StoryCanvas.LEFT + StoryCanvas.COLUMN_WIDTH / 2f, y + name.size.height / 2f)
        withTransform({
            rotate(-40f * (1f - e), pivot)
            scale(0.2f + 0.8f * e, 0.2f + 0.8f * e, pivot)
        }) {
            drawText(name, StoryCanvas.Ink, Offset(StoryCanvas.LEFT, y), alpha = e.coerceIn(0f, 1f))
        }
    }
    y += name.lineCount * 113.4f + SHELL_NAME_BOTTOM
    celebration.meta?.let { meta ->
        val layout = measurer.storyText(meta, ShellType.meta, width = column, maxLines = 1, ellipsis = true)
        drawText(layout, StoryCanvas.Muted, Offset(StoryCanvas.LEFT, y), alpha = easeCss.transform(phase(t, 1.1f, 0.7f)))
    }
    drawStoryFoot(measurer, celebration.foot)
}

/**
 * La medalla de la celebración (`.medal` del lienzo, [size] px): degradado del metal a 145°, brillo blanco en (35 %, 30 %), aro
 * interior negro al 20 %, sombra suave y la cifra al 36 % del tamaño.
 */
private fun DrawScope.drawCssMedal(medal: StoryMedal, center: Offset, size: Float, measurer: TextMeasurer, alpha: Float) {
    val r = size / 2f
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.44f), Color.Transparent), center = center + Offset(0f, 4f * VIDEO_K), radius = r + 14f * VIDEO_K),
        radius = r + 14f * VIDEO_K,
        center = center + Offset(0f, 4f * VIDEO_K),
        alpha = alpha,
    )
    // 145° en CSS: hacia abajo a la derecha.
    val angle = (145.0 - 90.0) * PI / 180.0
    val dx = (cos(angle) * r).toFloat()
    val dy = (sin(angle) * r).toFloat()
    drawCircle(Brush.linearGradient(listOf(medal.c, medal.c2), start = center - Offset(dx, dy), end = center + Offset(dx, dy)), r, center, alpha = alpha)
    val shine = Offset(center.x - r + 0.35f * size, center.y - r + 0.30f * size)
    drawCircle(
        Brush.radialGradient(0f to Color.White.copy(alpha = 0x88 / 255f), 1f to Color.Transparent, center = shine, radius = 0.42f * size),
        r,
        center,
        alpha = alpha,
    )
    drawCircle(Color.Black, r - VIDEO_K, center, style = Stroke(2f * VIDEO_K), alpha = 0.2f * alpha)
    val digit = measurer.storyText(medal.digit, ShellType.medalDigit(size * 0.36f))
    drawText(digit, Color(0xFF1B1206), center - Offset(digit.size.width / 2f, digit.size.height / 2f), alpha = alpha)
}

/**
 * Graba la felicitación en un MP4 de [STORY_VIDEO_SECONDS] s a [STORY_VIDEO_FPS] fps (H.264, 1080 × 1920, ~2,6 Mb/s) en la caché de
 * la app (carpeta del FileProvider) y devuelve el archivo. Cada cuadro se pinta con el mismo código que la vista previa.
 */
internal suspend fun encodeCompetitionVideo(context: Context, celebration: CompetitionCelebration): File = withContext(Dispatchers.Default) {
    val dir = File(context.cacheDir, "shares").apply { check(isDirectory || mkdirs()) }
    val file = File.createTempFile("competition-story-", ".mp4", dir)
    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, StoryCanvas.WIDTH, StoryCanvas.HEIGHT).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 2_600_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, STORY_VIDEO_FPS)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    var muxer: MediaMuxer? = null
    var muxerStarted = false
    try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        val mux = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
        val measurer = TextMeasurer(createFontFamilyResolver(context), StoryCanvas.Density1, LayoutDirection.Ltr)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var written = 0

        // Saca lo que el codificador tenga listo; al final ([end]) espera hasta el fin del flujo. Los tiempos se reescriben por orden
        // de salida (cuadro n → n/30 s): la superficie fecha los cuadros con el reloj de pintura, no con el del vídeo.
        fun drain(end: Boolean) {
            var idle = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!end || ++idle > 500) return
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = mux.addTrack(codec.outputFormat)
                        mux.start()
                        muxerStarted = true
                    }
                    index >= 0 -> {
                        val buffer = codec.getOutputBuffer(index)
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (buffer != null && !config && info.size > 0 && muxerStarted) {
                            info.presentationTimeUs = written * 1_000_000L / STORY_VIDEO_FPS
                            written++
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            mux.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        val size = Size(StoryCanvas.WIDTH.toFloat(), StoryCanvas.HEIGHT.toFloat())
        try {
            for (frame in 0 until STORY_VIDEO_SECONDS * STORY_VIDEO_FPS) {
                val canvas = surface.lockHardwareCanvas()
                try {
                    CanvasDrawScope().draw(StoryCanvas.Density1, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), size) {
                        drawCompetitionCelebration(celebration, measurer, frame / STORY_VIDEO_FPS.toFloat())
                    }
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                drain(end = false)
            }
            codec.signalEndOfInputStream()
            drain(end = true)
        } finally {
            surface.release()
        }
        check(written > 0) { "El vídeo no tiene cuadros" }
        file
    } catch (error: Throwable) {
        file.delete()
        throw error
    } finally {
        runCatching { codec.stop() }
        codec.release()
        if (muxerStarted) runCatching { muxer?.stop() }
        muxer?.release()
    }
}
