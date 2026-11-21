package com.example.kpkn.screens.programs.importer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.ExifInterface
import com.example.kpkn.data.importer.ocr.OcrImportSource
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ImportDocumentType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SourcePreviewPage(val bitmap: Bitmap?, val pageCount: Int, val error: String? = null)

/** Renders only a bounded preview from the private retained source; source bytes stay in the app's private files. */
@Composable
internal fun ImportSourcePreviewDialog(
    file: File?,
    type: ImportDocumentType,
    unavailable: Boolean,
    onDismiss: () -> Unit,
) {
    var page by remember(file) { mutableStateOf(0) }
    var preview by remember(file, type, page, unavailable) { mutableStateOf<SourcePreviewPage?>(null) }
    LaunchedEffect(file, type, page, unavailable) {
        var decoded: SourcePreviewPage? = null
        try {
            withContext(Dispatchers.IO) {
                decoded = when {
                    unavailable -> SourcePreviewPage(null, 0, "No se pudo conservar una copia privada del original.")
                    file == null -> SourcePreviewPage(null, 0, "El original ya no está disponible en este borrador.")
                    type in setOf(ImportDocumentType.IMAGE, ImportDocumentType.URL_IMAGE) -> decodeSourceImage(file)
                    type in setOf(ImportDocumentType.PDF, ImportDocumentType.URL_PDF) -> renderSourcePdf(file, page)
                    else -> SourcePreviewPage(null, 0, "Este tipo de documento no tiene vista previa visual.")
                }
            }
            preview = decoded
            decoded = null
        } finally {
            decoded?.bitmap?.recycle()
        }
    }
    val bitmap = preview?.bitmap
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = ComposeColor(0xFF1A1A1A), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text("Original importado", style = WizardTypography.cardTitle, color = WizardColors.text)
            preview?.error?.let { Text(it, style = WizardTypography.note, color = WizardColors.textMuted) }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = if (type in setOf(ImportDocumentType.PDF, ImportDocumentType.URL_PDF)) "Página ${page + 1} del PDF original" else "Foto original",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            val count = preview?.pageCount ?: 0
            if (count > 1) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text("Anterior") }
                    Text("Página ${page + 1} de $count", style = WizardTypography.note, color = WizardColors.textMuted)
                    TextButton(onClick = { page = (page + 1).coerceAtMost(count - 1) }, enabled = page + 1 < count) { Text("Siguiente") }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
            }
        }
    }
}

private fun decodeSourceImage(file: File): SourcePreviewPage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth < 1 || bounds.outHeight < 1) return SourcePreviewPage(null, 0, "No pudimos mostrar la foto original.")
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2_000) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return SourcePreviewPage(null, 0, "No pudimos mostrar la foto original.")
    return try {
        SourcePreviewPage(OcrImportSource.orientAndScale(bitmap, 2_000) { ExifInterface(file.absolutePath) }, 1)
    } catch (_: Exception) {
        SourcePreviewPage(null, 0, "No pudimos mostrar la foto original.")
    }
}

private fun renderSourcePdf(file: File, requestedPage: Int): SourcePreviewPage = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (renderer.pageCount == 0) return SourcePreviewPage(null, 0, "El PDF original no tiene páginas.")
            val index = requestedPage.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(index).use { page ->
                if (page.width <= 0 || page.height <= 0) return SourcePreviewPage(null, renderer.pageCount, "No pudimos mostrar esta página.")
                val scale = minOf(1500f / page.width, 2200f / page.height, 1f)
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                try {
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    SourcePreviewPage(bitmap, renderer.pageCount)
                } catch (error: Throwable) {
                    bitmap.recycle()
                    throw error
                }
            }
        }
    }
} catch (_: Exception) {
    SourcePreviewPage(null, 0, "No pudimos mostrar el PDF original.")
}
