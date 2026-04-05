package com.example.kpkn.screens.workout

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.kpkn.R
import java.io.File
import java.io.FileOutputStream

object WorkoutShareService {

    fun shareToInstagramStory(
        context: Context,
        sessionName: String,
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
    ) {
        val bitmap = renderStoryCard(context, sessionName, durationMinutes, totalVolume, totalSets)
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
    ): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = Paint().apply { color = Color.parseColor("#0C1E26") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val accent = Paint().apply { color = Color.parseColor("#00A6A6") }
        canvas.drawRect(0f, 0f, width.toFloat(), 260f, accent)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6F5F5")
            textSize = 42f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            isFakeBoldText = true
        }

        canvas.drawText("Entrenamiento completado", 64f, 160f, titlePaint)
        canvas.drawText(sessionName.take(28), 64f, 230f, bodyPaint)

        canvas.drawText("Sets", 64f, 520f, bodyPaint)
        canvas.drawText(totalSets.toString(), 64f, 610f, valuePaint)

        canvas.drawText("Volumen", 64f, 820f, bodyPaint)
        canvas.drawText("${"%.0f".format(totalVolume)} kg", 64f, 910f, valuePaint)

        canvas.drawText("Duración", 64f, 1120f, bodyPaint)
        canvas.drawText("${durationMinutes} min", 64f, 1210f, valuePaint)

        val logo = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.kpknicon)
        logo?.setBounds(width - 260, height - 260, width - 80, height - 80)
        logo?.draw(canvas)

        canvas.drawText("KPKN FIT", 64f, height - 120f, bodyPaint)

        return bitmap
    }
}
