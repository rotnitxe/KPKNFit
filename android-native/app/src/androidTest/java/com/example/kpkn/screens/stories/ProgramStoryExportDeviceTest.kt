package com.example.kpkn.screens.stories

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.math.roundToInt

/** Verifica que la hoja real del programa puede guardar su historia como PNG en la galería. */
@RunWith(AndroidJUnit4::class)
class ProgramStoryExportDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = rule.activity.applicationContext

    @Test
    fun programaConTituloLargoYCuarentaBloquesGuardaLaHistoriaCompletaEnFotos() {
        assumeTrue("MediaStore con carpeta relativa requiere Android 10+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val program = longProgram()
        val namesBefore = galleryRows().mapTo(mutableSetOf()) { it.displayName }
        val requestedAt = System.currentTimeMillis() - 1_000L

        try {
            rule.setContent {
                KPKNTheme {
                    ProgramShareSheet(program = program, logs = emptyList(), onDismiss = {})
                }
            }

            val weekStory = ProgramStoryBuilder.build(program, ProgramStoryTemplate.SEMANA, emptyList(), LocalDate.of(2026, 10, 27))
            assertEquals(7, (weekStory.content as ProgramStoryContent.Week).days.size)
            val weekBounds = storyContentBounds(weekStory)
            println("AUDIT_C10_WEEK_BOUNDS top=${weekBounds.top} bottom=${weekBounds.bottom}")
            val weekPreview = rule.onNodeWithTag("story-preview", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            try {
                saveEvidenceBitmap("week-preview.png", weekPreview)
                assertEquals(7, countInkRuns(weekPreview, weekBounds, xStart = 75f, xEnd = 140f, sameInkGapCanvasUnits = 16f))
            } finally {
                weekPreview.recycle()
            }

            rule.onNodeWithTag("story-template-BLOQUES").performScrollTo().performClick()
            val blockStory = ProgramStoryBuilder.build(program, ProgramStoryTemplate.BLOQUES, emptyList(), LocalDate.of(2026, 10, 27))
            val blockBounds = storyContentBounds(blockStory)
            val blockContent = blockStory.content as ProgramStoryContent.Blocks
            val blockMeasurer = TextMeasurer(createFontFamilyResolver(context), StoryCanvas.Density1, LayoutDirection.Ltr)
            val blockLayout = measureBlocksStoryLayout(blockMeasurer, blockContent, blockBounds.bottom - blockBounds.top)
            val visibleBlocks = blockLayout.rows.size - 1
            val remainder = blockLayout.rows.last().left
            assertTrue("La composición visible cabe antes del pie", blockLayout.totalHeight <= blockBounds.bottom - blockBounds.top)
            assertTrue(visibleBlocks in 1 until blockContent.blocks.size)
            assertEquals("+${blockContent.blocks.size - visibleBlocks} bloques más", remainder.layoutInput.text.text)
            assertEquals(1, remainder.lineCount)
            assertFalse(remainder.hasVisualOverflow)
            val blocksPreview = rule.onNodeWithTag("story-preview", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            try {
                saveEvidenceBitmap("blocks-preview.png", blocksPreview)
                val phaseBarRuns = if (blockContent.weekColors.isNotEmpty()) 1 else 0
                assertEquals(
                    "se ven las filas medidas y la barra de fases; la última fila es el indicador +N",
                    blockLayout.rows.size + phaseBarRuns,
                    countInkRuns(blocksPreview, blockBounds, xStart = 75f, xEnd = 480f),
                )
            } finally {
                blocksPreview.recycle()
            }

            rule.onNodeWithTag("story-save").performScrollTo().assertIsEnabled().performClick()
            rule.waitUntil(30_000) {
                galleryRows().any { it.displayName !in namesBefore && it.createdAtMillis >= requestedAt }
            }

            val exported = galleryRows().first { it.displayName !in namesBefore && it.createdAtMillis >= requestedAt }
            assertTrue(exported.displayName.endsWith(".png"))
            assertEquals("image/png", exported.mimeType)
            assertTrue("La historia se guarda en Pictures/KPKN", exported.relativePath.contains("KPKN"))
            assertEquals(0, exported.pending)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(exported.uri)!!.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            assertEquals(1080, bounds.outWidth)
            assertEquals(1920, bounds.outHeight)

            val evidenceDirectory = checkNotNull(context.getExternalFilesDir("audit-c10"))
            check(evidenceDirectory.isDirectory || evidenceDirectory.mkdirs())
            val evidenceCopy = File(evidenceDirectory, "story-export.png")
            context.contentResolver.openInputStream(exported.uri)!!.use { input ->
                evidenceCopy.outputStream().use { output -> input.copyTo(output) }
            }
            assertTrue("Se conserva una copia extraíble del PNG exportado", evidenceCopy.isFile && evidenceCopy.length() > 0L)
            val exportedPng = android.util.Base64.encodeToString(evidenceCopy.readBytes(), android.util.Base64.NO_WRAP)
            exportedPng.chunked(1_500).forEachIndexed { index, chunk ->
                println("AUDIT_C10_PNG_EXPORTED_$index=$chunk")
            }
        } finally {
            galleryRows()
                .filter { it.displayName !in namesBefore && it.createdAtMillis >= requestedAt }
                .forEach { context.contentResolver.delete(it.uri, null, null) }
        }
    }

    private fun galleryRows(): List<GalleryRow> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.IS_PENDING,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val rows = mutableListOf<GalleryRow>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("KPKN-%.png"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val pendingIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_PENDING)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                rows += GalleryRow(
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    displayName = cursor.getString(nameIndex),
                    relativePath = cursor.getString(pathIndex).orEmpty(),
                    pending = cursor.getInt(pendingIndex),
                    mimeType = cursor.getString(mimeIndex),
                    createdAtMillis = cursor.getLong(dateIndex) * 1_000L,
                )
            }
        }
        return rows
    }

    private fun longProgram(): Program {
        val blocks = (1..40).map { index ->
            val sessions = if (index == 1) {
                (1..7).map { day ->
                    Session(
                        id = "c10-session-$day",
                        name = "Sesión $day",
                        exercises = listOf(
                            Exercise(
                                id = "c10-exercise-$day",
                                name = "Sentadilla",
                                sets = listOf(ExerciseSet(id = "c10-set-$day", targetReps = 8)),
                            ),
                        ),
                        dayOfWeek = day,
                    )
                }
            } else {
                emptyList()
            }
            Block(
                id = "c10-block-$index",
                name = "Bloque $index · Acumulación y fuerza",
                mesocycles = listOf(
                    Mesocycle(
                        id = "c10-meso-$index",
                        name = "Mesociclo $index",
                        weeks = listOf(ProgramWeek(id = "c10-week-$index", name = "Semana $index", sessions = sessions)),
                    ),
                ),
            )
        }
        return Program(
            id = "c10-story-export",
            name = "Programa de fuerza con preparación, técnica, acumulación y bloques distribuidos en una planificación extensa ".repeat(5),
            macrocycles = listOf(Macrocycle(id = "c10-macrocycle", name = "Plan anual", blocks = blocks)),
            startDay = 1,
            weekDays = 7,
        )
    }

    private fun saveEvidenceBitmap(name: String, bitmap: android.graphics.Bitmap) {
        val directory = checkNotNull(context.getExternalFilesDir("audit-c10"))
        check(directory.isDirectory || directory.mkdirs())
        println("AUDIT_DEVICE_EVIDENCE_DIRECTORY=${directory.absolutePath}")
        File(directory, name).outputStream().use { output ->
            assertTrue("Se guarda evidencia visual $name", bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        if (name.endsWith("-preview.png")) {
            val png = ByteArrayOutputStream().also { buffer ->
                assertTrue("Se codifica evidencia visual para el log de prueba", bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buffer))
            }.toByteArray()
            val encoded = android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP)
            val imageLabel = name.removeSuffix(".png").uppercase().replace('-', '_')
            encoded.chunked(3_000).forEachIndexed { index, chunk -> println("AUDIT_C10_PNG_${imageLabel}_$index=$chunk") }
        }
    }

    private fun storyContentBounds(story: ProgramStory): StoryContentBounds {
        var top = 0f
        var bottom = 0f
        val bitmap = renderStory(context) { measurer ->
            val headerBottom = drawStoryHeader(measurer, story.eyebrow, story.name, story.content.meta)
            val footTop = drawStoryFoot(measurer, story.footer)
            top = headerBottom + StoryCanvas.CONTENT_GAP
            bottom = footTop - StoryCanvas.CONTENT_GAP
        }
        bitmap.recycle()
        return StoryContentBounds(top, bottom)
    }

    private fun countInkRuns(
        bitmap: android.graphics.Bitmap,
        bounds: StoryContentBounds,
        xStart: Float,
        xEnd: Float,
        sameInkGapCanvasUnits: Float = 4f,
    ): Int {
        val scale = bitmap.width / StoryCanvas.WIDTH.toFloat()
        val firstY = (bounds.top * scale).roundToInt().coerceIn(0, bitmap.height - 1)
        val lastY = (bounds.bottom * scale).roundToInt().coerceIn(firstY + 1, bitmap.height)
        val firstX = (xStart * scale).roundToInt().coerceIn(0, bitmap.width - 1)
        val lastX = (xEnd * scale).roundToInt().coerceIn(firstX + 1, bitmap.width)
        val gapToSplit = (sameInkGapCanvasUnits * scale).roundToInt().coerceAtLeast(2)
        val runs = mutableListOf<IntRange>()
        var hitsInRun = 0
        var emptyRows = 0
        var runStart = firstY
        for (y in firstY until lastY) {
            val hit = (firstX until lastX).any { x ->
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.red(pixel) > 75 && android.graphics.Color.green(pixel) > 70
            }
            if (hit) {
                if (emptyRows > gapToSplit && hitsInRun > 0) {
                    runs += runStart..(y - emptyRows - 1)
                    hitsInRun = 0
                }
                if (hitsInRun == 0) runStart = y
                hitsInRun++
                emptyRows = 0
            } else {
                emptyRows++
            }
        }
        if (hitsInRun > 0) runs += runStart..(lastY - emptyRows - 1)
        println("AUDIT_C10_INK x=$firstX..$lastX y=$firstY..$lastY gap=$gapToSplit runs=${runs.joinToString()}")
        return runs.size
    }

    private data class StoryContentBounds(val top: Float, val bottom: Float)

    private data class GalleryRow(
        val uri: Uri,
        val displayName: String,
        val relativePath: String,
        val pending: Int,
        val mimeType: String,
        val createdAtMillis: Long,
    )
}
