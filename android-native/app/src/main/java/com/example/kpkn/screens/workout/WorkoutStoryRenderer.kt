package com.example.kpkn.screens.workout

import com.example.kpkn.domain.performance.StrengthModel
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.kpkn.data.programs.ProgramCoverRepository
import com.example.kpkn.data.programs.programCoverEntryIdOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

internal enum class WorkoutStoryTemplate(val label: String) { GENERAL("General"), EXERCISE("Ejercicio"), PHOTOS("Fotos") }

internal data class WorkoutStoryOptions(
    val template: WorkoutStoryTemplate = WorkoutStoryTemplate.GENERAL,
    val exerciseId: String? = null,
    val improvements: Boolean = true,
    val duration: Boolean = true,
    val intensity: Boolean = true,
)

internal data class WorkoutStoryData(
    val sessionName: String,
    val presentation: FinishPresentationContext,
    val durationMinutes: Int,
    val volume: Double,
    val sets: Int,
    val averageRir: Double?,
    val intensity: String,
    val lifts: List<FinishLiftProgress>,
    /** RPE de sesión declarado; si falta se usa 10 − RIR medio. */
    val sessionRpe: Double? = null,
) {
    val defaultExerciseId: String? get() = (lifts.filter { it.isImproved }.maxByOrNull { it.deltaKg ?: 0.0 } ?: lifts.firstOrNull())?.exerciseId
}

/** Composición nativa 360×640 a 3× para vista previa, MediaStore y el selector. Sin HTML ni datos inventados. */
internal suspend fun renderWorkoutStory(context: Context, data: WorkoutStoryData, options: WorkoutStoryOptions): Bitmap = withContext(Dispatchers.Default) {
    val background = storyBackground(context, data, options.template)
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(background, 0f, 0f, null)
        canvas.scale(3f, 3f)
        val draw = StoryDrawing(canvas, context, data)
        if (options.template != WorkoutStoryTemplate.PHOTOS) draw.header(data)
        when (options.template) {
            WorkoutStoryTemplate.GENERAL -> draw.general(data, options)
            WorkoutStoryTemplate.EXERCISE -> draw.exercise(data, options)
            WorkoutStoryTemplate.PHOTOS -> draw.photos(context, data, options)
        }
        draw.footer(data)
        bitmap
    } catch (error: Throwable) {
        bitmap.recycle(); throw error
    } finally {
        background.recycle()
    }
}

private const val CREAM = 0xFFF2EEE6.toInt()
private const val MUTED = 0xFFB5B1AC.toInt()
private const val FAINT = 0xFF7A7670.toInt()
private const val GREEN = 0xFF7FE3B0.toInt()
private const val HAIRLINE = 0x1AFFFFFF
private const val SURFACE_TOP = 0xFF17130F.toInt()
private const val SURFACE_MID = 0xFF0B0A09.toInt()
private const val SURFACE_BOTTOM = 0xFF050505.toInt()

private class StoryDrawing(
    private val canvas: Canvas,
    context: Context,
    private val data: WorkoutStoryData,
) {
    private val headingFont = androidx.core.content.res.ResourcesCompat.getFont(context, com.example.kpkn.R.font.syne_bold)
    private val displayFont = androidx.core.content.res.ResourcesCompat.getFont(context, com.example.kpkn.R.font.syne_extrabold)
    private val bodyFont = androidx.core.content.res.ResourcesCompat.getFont(context, com.example.kpkn.R.font.wizard_body_regular)
    private val strongFont = androidx.core.content.res.ResourcesCompat.getFont(context, com.example.kpkn.R.font.wizard_body_semibold)
    private val brand = ContextCompat.getDrawable(context, com.example.kpkn.R.drawable.kpkn_logo_horizontal)?.mutate()?.apply { setTint(CREAM) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val locale = Locale.forLanguageTag("es-CL")
    private val today: LocalDate = data.presentation.today

    fun text(
        value: String,
        x: Float,
        y: Float,
        size: Float,
        bold: Boolean = false,
        color: Int = CREAM,
        align: Paint.Align = Paint.Align.LEFT,
        maxWidth: Float = 320f,
        display: Boolean = false,
        letterSpacing: Float = 0f,
    ) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color; paint.textSize = size
        paint.typeface = if (display) displayFont else if (bold && size >= 18f) headingFont else if (bold) strongFont else bodyFont
        paint.textAlign = align
        if (letterSpacing != 0f && size > 0f) paint.letterSpacing = letterSpacing / size
        var label = value.replace('\n', ' ')
        if (paint.measureText(label) > maxWidth) {
            while (label.isNotEmpty() && paint.measureText("$label…") > maxWidth) label = label.dropLast(1)
            label += "…"
        }
        canvas.drawText(label, x, y, paint)
        paint.letterSpacing = 0f
    }

    private fun logo(x: Float, y: Float, width: Float = 58f) {
        brand?.let { drawable ->
            val height = width * drawable.intrinsicHeight / drawable.intrinsicWidth
            canvas.save(); canvas.translate(x, y + (16f - height) / 2)
            canvas.scale(width / drawable.intrinsicWidth, height / drawable.intrinsicHeight)
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight); drawable.draw(canvas); canvas.restore()
        }
    }

    private fun dateLabel(): String = today.format(DateTimeFormatter.ofPattern("d MMM", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        .uppercase(locale)

    private fun line(y: Float) {
        paint.reset(); paint.isAntiAlias = true; paint.color = HAIRLINE; paint.strokeWidth = 1f
        canvas.drawLine(20f, y, 340f, y, paint)
    }

    private fun eyebrow(value: String, y: Float, color: Int = FAINT) {
        text(value.uppercase(locale), 20f, y, 8.5f, bold = true, color = color, letterSpacing = 1.6f)
    }

    fun header(data: WorkoutStoryData) {
        logo(20f, 16f)
        text(dateLabel(), 340f, 28f, 9f, bold = true, color = FAINT, align = Paint.Align.RIGHT, maxWidth = 120f, letterSpacing = 1.2f)
        line(42f)
    }

    private fun prLift(data: WorkoutStoryData): FinishLiftProgress? =
        data.lifts.filter { lift ->
            val best = lift.best ?: return@filter false
            StrengthModel.estimate1RM(best) > (lift.history.maxOfOrNull { it.estimated1Rm } ?: 0.0) + 0.005
        }.maxByOrNull { lift -> lift.best?.let { StrengthModel.estimate1RM(it) } ?: 0.0 }

    fun general(data: WorkoutStoryData, options: WorkoutStoryOptions) {
        val program = data.presentation.program?.name
        eyebrow(program ?: "Sesión", 66f)
        var y = 92f
        val titleLines = wrap(data.sessionName, 27f, 300f, 2)
        titleLines.forEachIndexed { index, lineText ->
            text(lineText, 20f, y + index * 30f, 27f, display = true, color = CREAM, maxWidth = 320f)
        }
        y += titleLines.size * 30f

        val pr = prLift(data)
        val prSet = pr?.best
        if (options.improvements && prSet != null) {
            y += 4f
            paint.reset(); paint.isAntiAlias = true; paint.color = GREEN
            canvas.drawCircle(24f, y - 3f, 2.4f, paint)
            text("PR e1RM · ${finishNumber(StrengthModel.estimate1RM(prSet))} kg", 32f, y, 9.5f, bold = true, color = GREEN, maxWidth = 300f)
            y += 4f
        }

        y += 16f
        val metrics = buildList {
            add("Series" to "${data.sets}")
            add("Tonelaje" to "${finishNumber(data.volume, 0)} kg")
            if (options.duration) add("Duración" to "${data.durationMinutes} min")
            if (options.intensity) add("sRPE" to (rpeOf(data)?.let { finishNumber(it) } ?: "—"))
        }
        metrics.chunked(2).forEachIndexed { row, pair ->
            pair.forEachIndexed { column, metric ->
                val x = 20f + column * 160f
                val rowY = y + row * 44f
                text(metric.first.uppercase(locale), x, rowY, 7.5f, bold = true, color = FAINT, maxWidth = 150f, letterSpacing = 1.2f)
                text(metric.second, x, rowY + 20f, 20f, display = true, color = CREAM, maxWidth = 150f)
            }
        }
        y += ((metrics.size + 1) / 2) * 44f + 8f
        line(y)
        y += 20f
        eyebrow("Ejercicios", y)
        y += 16f
        val visible = data.lifts.take(5)
        if (visible.isEmpty()) {
            text("Sin ejercicios registrados", 20f, y, 11f, color = MUTED)
        }
        visible.forEach { lift ->
            text(lift.name, 20f, y, 11.5f, bold = true, maxWidth = 200f)
            text(lift.best?.let { finishSetLabel(it) } ?: "—", 300f, y, 10f, color = FAINT, align = Paint.Align.RIGHT, maxWidth = 64f)
            if (options.improvements) {
                text(finishDeltaLabel(lift.deltaKg).replace(" kg", ""), 340f, y, 10.5f, bold = true,
                    color = if (lift.isImproved) GREEN else FAINT, align = Paint.Align.RIGHT, maxWidth = 36f)
            }
            y += 7f
            paint.reset(); paint.isAntiAlias = true; paint.color = 0x0DFFFFFF; paint.strokeWidth = 1f
            canvas.drawLine(20f, y, 340f, y, paint)
            y += 14f
        }

        val spark = sessionTonSpark(data)
        if (spark.size >= 2) {
            eyebrow("Tonelaje · últimas ${spark.size}", 536f)
            sparkline(spark, RectF(20f, 546f, 340f, 586f))
        }
    }

    fun exercise(data: WorkoutStoryData, options: WorkoutStoryOptions) {
        val lift = data.lifts.firstOrNull { it.exerciseId == options.exerciseId } ?: data.lifts.firstOrNull { it.exerciseId == data.defaultExerciseId }
        eyebrow("Ejercicio", 66f)
        if (lift == null) {
            text("Sin series de fuerza registradas", 20f, 100f, 14f, bold = true)
            return
        }
        text(lift.name, 20f, 96f, 20f, display = true, maxWidth = 320f)
        val best = lift.best
        val value = best?.let { if (it.weight == 0.0) "${it.reps} reps" else "${finishNumber(it.weight)} kg" } ?: "—"
        text(value, 20f, 156f, 40f, display = true, maxWidth = 320f)
        val detail = best?.let {
            val reps = if (it.weight == 0.0) "peso corporal" else "× ${it.reps} reps"
            "RIR ${finishNumber(finishActualRir(it) ?: 0.0, 0)} · 1RM ${finishNumber(StrengthModel.estimate1RM(it), 0)} kg · $reps"
        } ?: "Sin serie comparable"
        text(detail, 20f, 174f, 9.5f, color = MUTED, maxWidth = 320f)

        if (options.improvements) {
            line(196f)
            text(lift.deltaKg?.let { finishDeltaLabel(it) } ?: "Sin registro previo", 20f, 216f, 11f, bold = true,
                color = if (lift.isImproved) GREEN else MUTED, maxWidth = 180f)
        }
        val points = lift.history.map { it.estimated1Rm }
        if (points.size >= 2) {
            eyebrow("1RM estimado · ${points.size} sesiones", 262f)
            sparkline(points, RectF(20f, 276f, 340f, 360f))
        }
        val first = lift.first
        val gain = if (first != null && first.weight > 0 && best != null) (best.weight / first.weight - 1) * 100 else null
        line(392f)
        rowOfStats(
            listOf(
                "Sesiones" to "${lift.totalHistorySessions + if (best != null) 1 else 0}",
                "Desde" to (first?.let { "${finishNumber(it.weight)} kg" } ?: "—"),
                "Subida" to (gain?.let { "${if (it > 0) "+" else ""}${finishNumber(it, 0)} %" } ?: "—"),
            ),
            404f,
        )
    }

    suspend fun photos(context: Context, data: WorkoutStoryData, options: WorkoutStoryOptions) {
        eyebrow("Sesión", 66f)
        text(data.sessionName, 20f, 96f, 22f, display = true, maxWidth = 300f)
        val photoList = data.presentation.photos.take(4)
        require(photoList.isNotEmpty()) { "No hay fotos reales en esta sesión" }
        val rects = when (photoList.size) {
            1 -> listOf(RectF(20f, 118f, 340f, 500f))
            2 -> listOf(RectF(20f, 118f, 176f, 500f), RectF(184f, 118f, 340f, 500f))
            else -> listOf(
                RectF(20f, 118f, 176f, 306f), RectF(184f, 118f, 340f, 306f),
                RectF(20f, 314f, 176f, 500f), RectF(184f, 314f, 340f, 500f),
            )
        }
        photoList.forEachIndexed { index, photo ->
            val rect = rects[index]
            val image = loadStoryImage(context, photo.path, 960)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, 16f, 16f, Path.Direction.CW) })
            if (image != null) drawCover(canvas, image, rect) else {
                paint.reset(); paint.color = 0xFF141414.toInt(); canvas.drawRect(rect, paint)
                text("Foto no disponible", rect.centerX(), rect.centerY(), 10f, color = MUTED, align = Paint.Align.CENTER, maxWidth = rect.width() - 16)
            }
            canvas.restore()
            if (!photo.exerciseName.isNullOrBlank()) {
                text(photo.exerciseName.orEmpty(), rect.left + 12, rect.bottom - 14, 10f, bold = true, maxWidth = rect.width() - 24)
            }
            image?.recycle()
        }
        line(520f)
        rowOfStats(
            listOf(
                "Tonelaje" to "${finishNumber(data.volume, 0)} kg",
                "Series" to "${data.sets}",
                "Duración" to "${data.durationMinutes} min",
            ),
            542f,
        )
    }

    fun footer(data: WorkoutStoryData) {
        val left = listOfNotNull(
            data.presentation.program?.name?.uppercase(locale),
            data.presentation.weekNumber?.let { "Semana $it" },
        ).joinToString(" · ")
        text("KPKN${if (left.isBlank()) "" else " · $left"}", 20f, 616f, 7.5f, bold = true, color = FAINT, maxWidth = 240f, letterSpacing = 1.2f)
        text(dateLabel(), 340f, 616f, 7.5f, bold = true, color = FAINT, align = Paint.Align.RIGHT, maxWidth = 100f, letterSpacing = 1.2f)
    }

    private fun rowOfStats(stats: List<Pair<String, String>>, y: Float) {
        stats.forEachIndexed { index, stat ->
            val x = 20f + index * 108f
            text(stat.first.uppercase(locale), x, y, 7.5f, bold = true, color = FAINT, maxWidth = 100f, letterSpacing = 1.2f)
            text(stat.second, x, y + 18f, 13f, bold = true, maxWidth = 100f)
        }
    }

    private fun sessionTonSpark(data: WorkoutStoryData): List<Double> {
        val history = data.presentation.history
            .filter { it.sessionName == data.sessionName }
            .sortedBy { it.date }
            .takeLast(5)
            .map { it.totalVolume }
        return history + data.volume
    }

    private fun rpeOf(data: WorkoutStoryData): Double? =
        data.sessionRpe?.takeIf { it in 0.0..10.0 }
            ?: data.averageRir?.takeIf { it >= 0.0 }?.let { (10.0 - it).coerceIn(0.0, 10.0) }

    private fun wrap(value: String, size: Float, width: Float, maxLines: Int): List<String> {
        paint.reset(); paint.isAntiAlias = true; paint.textSize = size; paint.typeface = displayFont
        val words = value.split(' ')
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= width) current = candidate else {
                if (current.isNotEmpty()) lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return if (lines.size <= maxLines) lines else lines.take(maxLines - 1) + (lines[maxLines - 1] + "…")
    }

    private fun sparkline(values: List<Double>, rect: RectF) {
        if (values.size < 2) return
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        var lastX = rect.left
        var lastY = rect.bottom
        values.forEachIndexed { index, value ->
            val x = rect.left + rect.width() * index / (values.size - 1).toFloat()
            val y = rect.bottom - ((value - low) / span).toFloat() * rect.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            lastX = x; lastY = y
        }
        paint.reset(); paint.isAntiAlias = true; paint.color = CREAM; paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL; paint.color = 0xFFFFA15C.toInt()
        canvas.drawCircle(lastX, lastY, 2.6f, paint)
    }
}

private suspend fun storyBackground(context: Context, data: WorkoutStoryData, template: WorkoutStoryTemplate): Bitmap {
    val value = data.presentation.program?.coverImage
    val fromLibrary = programCoverEntryIdOf(value)?.let { ProgramCoverRepository.getInstance(context).load(it, 1080) }
    val remote = value?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?.let { url -> (context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(1080, 1920).build()) as? SuccessResult)?.drawable?.toBitmap() }
    val photo = fromLibrary ?: remote ?: value?.takeIf { it.startsWith("content:") || it.startsWith("file:") || File(it).isAbsolute }
        ?.let { loadStoryImage(context, it, 1920) }

    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (photo != null) {
        drawCover(canvas, photo, RectF(0f, 0f, 1080f, 1920f))
        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(158, 0, 0, 0) }
        canvas.drawRect(0f, 0f, 1080f, 1920f, scrim)
        val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 960f, 0f, 1920f,
                intArrayOf(Color.TRANSPARENT, Color.argb(235, 5, 5, 5)),
                floatArrayOf(0f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 960f, 1080f, 1920f, gradient)
        if (fromLibrary == null && remote == null) photo.recycle()
    } else if (value?.startsWith("gradient://") == true) {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(1080f, 1920f)) {
            drawRect(com.example.kpkn.screens.programs.programCoverBrush(value))
        }
        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        canvas.drawRect(0f, 0f, 1080f, 1920f, scrim)
    } else {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, 1920f,
            intArrayOf(SURFACE_TOP, SURFACE_MID, SURFACE_BOTTOM),
            floatArrayOf(0f, 0.38f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = android.graphics.RadialGradient(
            540f, -120f, 1250f,
            intArrayOf(0x2EFFA15C, 0x00FFA15C),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 1080f, 1400f, glow)
    }
    return bitmap
}

private suspend fun loadStoryImage(context: Context, path: String, width: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = if (path.startsWith("content:") || path.startsWith("file:")) Uri.parse(path) else Uri.fromFile(File(path))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            var scale = 1
            while (bounds.outWidth / scale > width * 2 || bounds.outHeight / scale > width * 2) scale *= 2
            inSampleSize = scale
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
            }
        }.getOrNull() ?: 1
        val matrix = android.graphics.Matrix()
        when (orientation) {
            2 -> matrix.postScale(-1f, 1f)
            3 -> matrix.postRotate(180f)
            4 -> matrix.postScale(1f, -1f)
            5 -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.postRotate(90f)
            7 -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            8 -> matrix.postRotate(270f)
        }
        if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }.getOrNull()
}

private fun drawCover(canvas: Canvas, image: Bitmap, dest: RectF) {
    val scale = max(dest.width() / image.width, dest.height() / image.height)
    val w = image.width * scale
    val h = image.height * scale
    canvas.drawBitmap(
        image, null,
        RectF(dest.centerX() - w / 2, dest.centerY() - h / 2, dest.centerX() + w / 2, dest.centerY() + h / 2),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )
}
