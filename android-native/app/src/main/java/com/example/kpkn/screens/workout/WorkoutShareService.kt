package com.example.kpkn.screens.workout

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.R
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs

object WorkoutShareService {

    private data class StoryExerciseBlock(
        val name: String,
        val meta: String,
        val detail: String,
    )

    fun shareToInstagramStory(
        context: Context,
        sessionName: String,
        completedExercises: List<CompletedExercise> = emptyList(),
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
        previousTotalSets: Int? = null,
        previousVolume: Double? = null,
        previousDurationMinutes: Int? = null,
        previousBestEstimated1RM: Double? = null,
        currentBestEstimated1RM: Double? = null,
    ) {
        runCatching {
            val bitmap = renderMinimalStoryCard(
                context = context,
                sessionName = sessionName,
                completedExercises = completedExercises,
                durationMinutes = durationMinutes,
                totalVolume = totalVolume,
                totalSets = totalSets,
                previousTotalSets = previousTotalSets,
                previousVolume = previousVolume,
                previousDurationMinutes = previousDurationMinutes,
                previousBestEstimated1RM = previousBestEstimated1RM,
                currentBestEstimated1RM = currentBestEstimated1RM,
            )
            val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
            val file = File(shareDir, "workout-story-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            val instagramIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(uri, "image/png")
                setPackage(INSTAGRAM_PACKAGE)
                clipData = ClipData.newRawUri("workout_story", uri)
                putExtra("source_application", context.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            var sharedDirectly = false
            try {
                context.grantUriPermission(
                    INSTAGRAM_PACKAGE,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                context.startActivity(instagramIntent)
                sharedDirectly = true
            } catch (_: Exception) {
                // Instagram is not installed or failed to launch. Fallback.
            }

            if (!sharedDirectly) {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("workout_story", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(fallback, "Compartir entrenamiento").apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }.onFailure { error ->
            if (error !is ActivityNotFoundException) {
                error.printStackTrace()
            }
            Toast.makeText(
                context,
                "No se pudo abrir la pantalla para compartir.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private const val INSTAGRAM_PACKAGE = "com.instagram.android"

    private fun renderMinimalStoryCard(
        context: Context,
        sessionName: String,
        completedExercises: List<CompletedExercise>,
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
        previousTotalSets: Int?,
        previousVolume: Double?,
        previousDurationMinutes: Int?,
        previousBestEstimated1RM: Double?,
        currentBestEstimated1RM: Double?,
    ): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(
                    Color.parseColor("#101214"),
                    Color.parseColor("#171A1B"),
                    Color.parseColor("#0C0D0E"),
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val exerciseNames = completedExercises
            .filter { exercise -> exercise.sets.any { set -> !set.isWarmup } }
            .map { it.exerciseName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf("Sin ejercicios registrados") }
        val visibleNames = if (exerciseNames.size > 18) {
            exerciseNames.take(17) + "+${exerciseNames.size - 17} ejercicios mas"
        } else {
            exerciseNames
        }
        val lineHeight = when {
            visibleNames.size > 14 -> 52f
            visibleNames.size > 10 -> 60f
            else -> 72f
        }
        val nameTextSize = when {
            visibleNames.size > 14 -> 30f
            visibleNames.size > 10 -> 33f
            else -> 36f
        }
        val cardLeft = 112f
        val cardRight = width - 112f
        val cardWidth = cardRight - cardLeft
        val cardHeight = (260f + visibleNames.size * lineHeight).coerceIn(620f, 1240f)
        val cardTop = (height - cardHeight) / 2f
        val cardBottom = cardTop + cardHeight
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 0, 0, 0)
        }
        canvas.drawRoundRect(
            RectF(cardRect.left + 14f, cardRect.top + 18f, cardRect.right + 14f, cardRect.bottom + 18f),
            42f,
            42f,
            shadowPaint,
        )

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7F8F4")
        }
        val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(cardRect, 42f, 42f, cardPaint)
        canvas.drawRoundRect(cardRect, 42f, 42f, cardStrokePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111314")
            textSize = 56f
            isFakeBoldText = true
            letterSpacing = 0.01f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        val sessionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A2F30")
            textSize = 38f
            isFakeBoldText = true
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(44, 17, 19, 20)
            strokeWidth = 2f
        }
        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111314")
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#24292A")
            textSize = nameTextSize
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        val contentLeft = cardLeft + 64f
        val contentRight = cardRight - 64f
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.kpknicon)
        val logoSize = 74f
        val logoLeft = cardRight - 64f - logoSize
        val logoTop = cardTop + 52f
        val logoRect = RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize)
        canvas.drawBitmap(logo, null, logoRect, null)

        canvas.drawText("ENTRENAMIENTO DE HOY", contentLeft, cardTop + 112f, titlePaint)
        canvas.drawText(ellipsize(sessionName.ifBlank { "Sesión" }, sessionPaint, cardWidth - 180f), contentLeft, cardTop + 160f, sessionPaint)
        canvas.drawLine(contentLeft, cardTop + 188f, contentRight, cardTop + 188f, dividerPaint)

        var rowY = cardTop + 252f
        visibleNames.forEach { name ->
            canvas.drawCircle(contentLeft + 8f, rowY - 12f, 6f, bulletPaint)
            canvas.drawText(
                ellipsize(name, namePaint, cardWidth - 158f),
                contentLeft + 32f,
                rowY,
                namePaint,
            )
            rowY += lineHeight
        }

        return bitmap
    }

    private fun renderStoryCard(
        context: Context,
        sessionName: String,
        completedExercises: List<CompletedExercise>,
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
        previousTotalSets: Int?,
        previousVolume: Double?,
        previousDurationMinutes: Int?,
        previousBestEstimated1RM: Double?,
        currentBestEstimated1RM: Double?,
    ): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(
                    Color.parseColor("#030303"),
                    Color.parseColor("#0A1110"),
                    Color.parseColor("#050505"),
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val topGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.78f,
                170f,
                420f,
                intArrayOf(Color.argb(120, 0, 229, 168), Color.argb(0, 0, 229, 168)),
                floatArrayOf(0f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        val leftGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                120f,
                1280f,
                380f,
                intArrayOf(Color.argb(70, 0, 163, 255), Color.argb(0, 0, 163, 255)),
                floatArrayOf(0f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(width * 0.78f, 170f, 420f, topGlowPaint)
        canvas.drawCircle(120f, 1280f, 380f, leftGlowPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
            strokeWidth = 1f
        }
        var gridY = 220f
        while (gridY < height.toFloat()) {
            canvas.drawLine(60f, gridY, width - 60f, gridY, gridPaint)
            gridY += 86f
        }

        val diagonalBand = Path().apply {
            moveTo(0f, 340f)
            lineTo(width.toFloat(), 520f)
            lineTo(width.toFloat(), 970f)
            lineTo(0f, 790f)
            close()
        }
        val diagonalBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(28, 255, 255, 255)
        }
        canvas.drawPath(diagonalBand, diagonalBandPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            isFakeBoldText = true
        }
        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C6FFF0")
            textSize = 28f
            isFakeBoldText = true
            letterSpacing = 0.08f
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#ADB6B4")
            textSize = 31f
        }
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            isFakeBoldText = true
        }
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8F9A97")
            textSize = 24f
        }
        val deltaUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5EFFB8")
            textSize = 26f
            isFakeBoldText = true
        }
        val deltaDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7272")
            textSize = 26f
            isFakeBoldText = true
        }
        val deltaNeutralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B7BDBB")
            textSize = 26f
            isFakeBoldText = true
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            isFakeBoldText = true
        }
        val exerciseNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            isFakeBoldText = true
        }
        val exerciseMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8F9A97")
            textSize = 24f
        }
        val exerciseDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DDF7EF")
            textSize = 28f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA5A2")
            textSize = 26f
        }

        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(28, 198, 255, 240)
        }
        val chipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 198, 255, 240)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val statCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 14, 18, 18)
        }
        val statStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val sectionCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 10, 12, 12)
        }
        val sectionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 19, 23, 23)
        }
        val accentStripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                160f,
                intArrayOf(Color.parseColor("#D8FFF4"), Color.parseColor("#2DE1A6")),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }

        val logoFilter = buildNegativeFilter()
        drawLogo(context, canvas, 64, 56, 176, 168, 255, logoFilter)
        drawLogo(context, canvas, 548, 230, 1060, 760, 30, logoFilter)

        val chipRect = RectF(202f, 70f, 430f, 118f)
        canvas.drawRoundRect(chipRect, 22f, 22f, chipPaint)
        canvas.drawRoundRect(chipRect, 22f, 22f, chipStrokePaint)
        canvas.drawText("COMPARTIR SESION", 224f, 102f, eyebrowPaint)
        canvas.drawText("KPKN", 202f, 162f, titlePaint)
        canvas.drawText("Entrena en KPKN", 204f, 205f, subtitlePaint)

        val titleEndY = drawWrappedText(
            canvas = canvas,
            text = sessionName.uppercase(Locale.getDefault()),
            startX = 64f,
            startY = 286f,
            maxWidth = 780f,
            paint = titlePaint,
            lineHeight = 82f,
            maxLines = 2,
        )
        val exerciseBlocks = buildExerciseBlocks(completedExercises)
        val exerciseCount = exerciseBlocks.size
        val headerSummary = buildString {
            append(exerciseCount)
            append(if (exerciseCount == 1) " ejercicio" else " ejercicios")
            append("  ·  ")
            append(totalSets)
            append(if (totalSets == 1) " serie" else " series")
            append("  ·  ")
            append(durationMinutes)
            append(" min")
        }
        canvas.drawText(headerSummary, 64f, titleEndY + 24f, subtitlePaint)

        val statsTop = titleEndY + 80f
        val statsGap = 16f
        val statCardWidth = (width - 64f * 2f - statsGap * 2f) / 3f
        val statCardHeight = 146f

        fun drawStatCard(
            left: Float,
            top: Float,
            label: String,
            value: String,
            deltaText: String?,
            deltaSign: Int,
        ) {
            val rect = RectF(left, top, left + statCardWidth, top + statCardHeight)
            canvas.drawRoundRect(rect, 28f, 28f, statCardPaint)
            canvas.drawRoundRect(rect, 28f, 28f, statStrokePaint)
            canvas.drawText(label, left + 22f, top + 42f, statLabelPaint)
            canvas.drawText(value, left + 22f, top + 96f, statValuePaint)
            if (!deltaText.isNullOrBlank()) {
                val paint = when {
                    deltaSign > 0 -> deltaUpPaint
                    deltaSign < 0 -> deltaDownPaint
                    else -> deltaNeutralPaint
                }
                canvas.drawText(deltaText, left + 22f, top + 128f, paint)
            }
        }

        fun deltaInfo(current: Double, previous: Double?, lowerIsBetter: Boolean = false): Pair<String?, Int> {
            if (previous == null || previous <= 0.0) return null to 0
            val diff = current - previous
            if (abs(diff) < 0.01) return "= estable" to 0
            val percent = (abs(diff) / previous) * 100.0
            val up = diff > 0
            val betterUp = if (lowerIsBetter) !up else up
            val arrow = if (up) "+" else "-"
            val sign = if (betterUp) 1 else -1
            return "$arrow ${"%.1f".format(percent)}%" to sign
        }

        val (setsDelta, setsSign) = deltaInfo(totalSets.toDouble(), previousTotalSets?.toDouble())
        val (volumeDelta, volumeSign) = deltaInfo(totalVolume, previousVolume)
        val (durationDelta, durationSign) = deltaInfo(durationMinutes.toDouble(), previousDurationMinutes?.toDouble(), lowerIsBetter = true)
        val (ermDelta, ermSign) = deltaInfo(currentBestEstimated1RM ?: 0.0, previousBestEstimated1RM)

        drawStatCard(
            left = 64f,
            top = statsTop,
            label = "Series",
            value = totalSets.toString(),
            deltaText = setsDelta,
            deltaSign = setsSign,
        )
        drawStatCard(
            left = 64f + statCardWidth + statsGap,
            top = statsTop,
            label = "Volumen",
            value = "${totalVolume.toDisplayString()} kg",
            deltaText = volumeDelta,
            deltaSign = volumeSign,
        )
        drawStatCard(
            left = 64f + (statCardWidth + statsGap) * 2f,
            top = statsTop,
            label = "Duración",
            value = "${durationMinutes} min",
            deltaText = durationDelta,
            deltaSign = durationSign,
        )

        val e1rmChipRect = RectF(64f, statsTop + statCardHeight + 18f, 408f, statsTop + statCardHeight + 74f)
        canvas.drawRoundRect(e1rmChipRect, 24f, 24f, chipPaint)
        canvas.drawRoundRect(e1rmChipRect, 24f, 24f, chipStrokePaint)
        canvas.drawText(
            currentBestEstimated1RM?.let { "Mejor e1RM  ${it.toDisplayString()} kg" } ?: "Mejor e1RM  Sin dato",
            88f,
            statsTop + statCardHeight + 55f,
            exerciseDetailPaint,
        )
        val e1rmDeltaPaint = when {
            ermSign > 0 -> deltaUpPaint
            ermSign < 0 -> deltaDownPaint
            else -> deltaNeutralPaint
        }
        ermDelta?.let {
            canvas.drawText(it, 432f, statsTop + statCardHeight + 55f, e1rmDeltaPaint)
        }

        val sectionTop = statsTop + statCardHeight + 112f
        val sectionBottom = height - 170f
        val sectionRect = RectF(64f, sectionTop, width - 64f, sectionBottom)
        canvas.drawRoundRect(sectionRect, 36f, 36f, sectionCardPaint)
        canvas.drawRoundRect(sectionRect, 36f, 36f, sectionStrokePaint)
        canvas.drawText("Ejercicios realizados", 96f, sectionTop + 54f, sectionTitlePaint)
        canvas.drawText("Series, repeticiones y carga real", 96f, sectionTop + 92f, subtitlePaint)

        val visibleBlocks = exerciseBlocks.take(5)
        val rowTopStart = sectionTop + 120f
        val rowGap = 16f
        val rowHeight = 148f
        visibleBlocks.forEachIndexed { index, block ->
            val top = rowTopStart + index * (rowHeight + rowGap)
            val rect = RectF(88f, top, width - 88f, top + rowHeight)
            canvas.drawRoundRect(rect, 28f, 28f, rowPaint)
            canvas.drawRoundRect(rect, 28f, 28f, statStrokePaint)
            canvas.drawRoundRect(RectF(rect.left, rect.top, rect.left + 10f, rect.bottom), 8f, 8f, accentStripPaint)

            canvas.drawText(ellipsize(block.name, exerciseNamePaint, 760f), rect.left + 34f, top + 46f, exerciseNamePaint)
            canvas.drawText(ellipsize(block.meta, exerciseMetaPaint, 760f), rect.left + 34f, top + 80f, exerciseMetaPaint)
            drawWrappedText(
                canvas = canvas,
                text = block.detail,
                startX = rect.left + 34f,
                startY = top + 116f,
                maxWidth = 820f,
                paint = exerciseDetailPaint,
                lineHeight = 30f,
                maxLines = 2,
            )
        }

        if (exerciseBlocks.size > visibleBlocks.size) {
            val remaining = exerciseBlocks.size - visibleBlocks.size
            canvas.drawText(
                "+$remaining ejercicios más",
                96f,
                sectionBottom - 38f,
                subtitlePaint,
            )
        }

        drawLogo(context, canvas, width - 164, height - 132, width - 92, height - 60, 210, logoFilter)
        canvas.drawText("Compartido desde KPKN", 64f, height - 92f, sectionTitlePaint)
        canvas.drawText("kpkn.fit", 64f, height - 56f, footerPaint)

        return bitmap
    }

    private fun buildExerciseBlocks(completedExercises: List<CompletedExercise>): List<StoryExerciseBlock> =
        completedExercises.mapNotNull { exercise ->
            val workingSets = exercise.sets.filterNot { it.isWarmup }
            if (workingSets.isEmpty()) return@mapNotNull null

            val meta = buildString {
                append(workingSets.size)
                append(if (workingSets.size == 1) " serie" else " series")
                val exerciseVolume = workingSets.sumOf { it.weight * it.reps }
                if (exerciseVolume > 0.0) {
                    append("  ·  ")
                    append(exerciseVolume.toDisplayString())
                    append(" kg")
                }
            }

            val grouped = workingSets.groupBy { Triple(it.reps, it.weight, it.timeSeconds) }
            val detail = if (grouped.size == 1) {
                val key = grouped.keys.first()
                formatUniformSummary(workingSets.size, key.first, key.second, key.third)
            } else {
                workingSets.take(4).joinToString("  •  ") { formatSetToken(it) }
            }

            StoryExerciseBlock(
                name = exercise.exerciseName,
                meta = meta,
                detail = detail,
            )
        }

    private fun formatUniformSummary(setCount: Int, reps: Int, weight: Double, timeSeconds: Int?): String {
        val effort = when {
            timeSeconds != null && timeSeconds > 0 -> "$setCount x ${timeSeconds}s"
            reps > 0 -> "$setCount x $reps reps"
            else -> "$setCount series"
        }
        return if (weight > 0.0) "$effort x ${weight.toDisplayString()} kg" else effort
    }

    private fun formatSetToken(set: CompletedSet): String {
        val sidePrefix = when (set.side?.lowercase(Locale.getDefault())) {
            "left" -> "L "
            "right" -> "R "
            else -> ""
        }
        val effort = when {
            set.timeSeconds != null && set.timeSeconds > 0 -> "${set.timeSeconds}s"
            set.reps > 0 -> "${set.reps}r"
            else -> "set"
        }
        val load = if (set.weight > 0.0) "${set.weight.toDisplayString()}kg" else "BW"
        return "$sidePrefix$effort x $load"
    }

    private fun drawLogo(
        context: Context,
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        alpha: Int,
        colorFilter: ColorMatrixColorFilter,
    ) {
        ContextCompat.getDrawable(context, R.drawable.kpknicon)
            ?.mutate()
            ?.apply {
                this.alpha = alpha.coerceIn(0, 255)
                this.colorFilter = colorFilter
                setBounds(left, top, right, bottom)
                draw(canvas)
            }
    }

    private fun buildNegativeFilter(): ColorMatrixColorFilter = ColorMatrixColorFilter(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        startX: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
        maxLines: Int,
    ): Float {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var currentLine = ""
        words.forEach { word ->
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) lines += currentLine
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines += currentLine

        val visibleLines = if (lines.size > maxLines) {
            lines.take(maxLines - 1) + ellipsize(lines[maxLines - 1], paint, maxWidth)
        } else {
            lines
        }

        visibleLines.forEachIndexed { index, line ->
            canvas.drawText(line, startX, startY + index * lineHeight, paint)
        }
        return startY + (visibleLines.size - 1).coerceAtLeast(0) * lineHeight
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var trimmed = text
        while (trimmed.isNotEmpty() && paint.measureText("$trimmed…") > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return if (trimmed.isEmpty()) "…" else "$trimmed…"
    }

    private fun Double.toDisplayString(): String {
        val rounded = kotlin.math.round(this * 10.0) / 10.0
        val whole = rounded.toInt().toDouble()
        return if (abs(rounded - whole) < 0.001) {
            whole.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
