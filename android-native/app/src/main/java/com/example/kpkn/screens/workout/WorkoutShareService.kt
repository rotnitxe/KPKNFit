package com.example.kpkn.screens.workout

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.kpkn.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

object WorkoutShareService {

    fun shareToInstagramStory(
        context: Context,
        sessionName: String,
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
        previousTotalSets: Int? = null,
        previousVolume: Double? = null,
        previousDurationMinutes: Int? = null,
        previousBestEstimated1RM: Double? = null,
        currentBestEstimated1RM: Double? = null,
    ) {
        val bitmap = renderStoryCard(
            context = context,
            sessionName = sessionName,
            durationMinutes = durationMinutes,
            totalVolume = totalVolume,
            totalSets = totalSets,
            previousTotalSets = previousTotalSets,
            previousVolume = previousVolume,
            previousDurationMinutes = previousDurationMinutes,
            previousBestEstimated1RM = previousBestEstimated1RM,
            currentBestEstimated1RM = currentBestEstimated1RM,
        )
        val file = File(context.cacheDir, "workout-story-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("source_application", context.packageName)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Compartir entrenamiento"))
        }
    }

    private fun renderStoryCard(
        context: Context,
        sessionName: String,
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

        val bg = Paint().apply { color = Color.parseColor("#040404") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#101010")
            strokeWidth = 1f
        }
        var y = 260f
        while (y < height.toFloat()) {
            canvas.drawLine(48f, y, width - 48f, y, gridPaint)
            y += 96f
        }

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5A8") }
        canvas.drawRect(0f, 0f, width.toFloat(), 18f, accent)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BDBDBD")
            textSize = 34f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            isFakeBoldText = true
        }
        val deltaUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            textSize = 30f
            isFakeBoldText = true
        }
        val deltaDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF5252")
            textSize = 30f
            isFakeBoldText = true
        }
        val deltaNeutralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BDBDBD")
            textSize = 30f
            isFakeBoldText = true
        }

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F0F0F") }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1F1F1F")
            strokeWidth = 2f
        }

        val logo = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.kpknicon)
        logo?.setBounds(64, 54, 164, 154)
        logo?.draw(canvas)

        canvas.drawText("KPKN", 188f, 118f, titlePaint)
        canvas.drawText("Entrena en KPKN", 188f, 162f, bodyPaint)

        canvas.drawText(sessionName.take(30), 64f, 252f, titlePaint)
        canvas.drawText("Resumen de sesión", 64f, 298f, bodyPaint)

        val statsTop = 348f
        val gap = 16f
        val cardWidth = (width - 64f * 2f - gap) / 2f
        val cardHeight = 220f

        fun drawStatCard(
            left: Float,
            top: Float,
            label: String,
            value: String,
            deltaText: String?,
            deltaSign: Int,
        ) {
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)
            canvas.drawRoundRect(rect, 26f, 26f, cardPaint)
            canvas.drawRoundRect(rect, 26f, 26f, cardStroke)
            canvas.drawText(label, left + 22f, top + 54f, bodyPaint)
            canvas.drawText(value, left + 22f, top + 132f, valuePaint)
            if (!deltaText.isNullOrBlank()) {
                val paint = when {
                    deltaSign > 0 -> deltaUpPaint
                    deltaSign < 0 -> deltaDownPaint
                    else -> deltaNeutralPaint
                }
                canvas.drawText(deltaText, left + 22f, top + 186f, paint)
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
            left = 64f + cardWidth + gap,
            top = statsTop,
            label = "Volumen",
            value = "${"%.0f".format(totalVolume)} kg",
            deltaText = volumeDelta,
            deltaSign = volumeSign,
        )
        drawStatCard(
            left = 64f,
            top = statsTop + cardHeight + gap,
            label = "Duración",
            value = "${durationMinutes} min",
            deltaText = durationDelta,
            deltaSign = durationSign,
        )
        drawStatCard(
            left = 64f + cardWidth + gap,
            top = statsTop + cardHeight + gap,
            label = "Mejor e1RM",
            value = currentBestEstimated1RM?.let { "${"%.1f".format(it)} kg" } ?: "Sin dato",
            deltaText = ermDelta,
            deltaSign = ermSign,
        )

        val detailsTop = statsTop + (cardHeight + gap) * 2f + 26f
        val detailsRect = RectF(64f, detailsTop, width - 64f, detailsTop + 330f)
        canvas.drawRoundRect(detailsRect, 30f, 30f, cardPaint)
        canvas.drawRoundRect(detailsRect, 30f, 30f, cardStroke)

        canvas.drawText("Detalle rápido", 88f, detailsTop + 58f, valuePaint)
        canvas.drawText("• Sesión: ${sessionName.take(34)}", 88f, detailsTop + 122f, bodyPaint)
        canvas.drawText("• Enfoque: rendimiento + consistencia", 88f, detailsTop + 166f, bodyPaint)
        canvas.drawText("• Progresión respecto a tu sesión anterior", 88f, detailsTop + 210f, bodyPaint)
        canvas.drawText("• Compartido desde KPKN", 88f, detailsTop + 254f, bodyPaint)

        logo?.setBounds(width - 188, height - 226, width - 72, height - 110)
        logo?.draw(canvas)

        canvas.drawText("Entrena en KPKN", 64f, height - 122f, titlePaint)
        canvas.drawText("kpkn.fit", 64f, height - 76f, bodyPaint)

        return bitmap
    }
}
