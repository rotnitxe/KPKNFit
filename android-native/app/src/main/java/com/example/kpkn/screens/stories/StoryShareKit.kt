package com.example.kpkn.screens.stories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * La hoja de compartir de las historias nuevas (competencia, cuerpo y nutrición; `shareSheet` del lienzo Registros v4): título,
 * vista previa de 176, plantillas en control segmentado, «Incluir» con píldoras marcadas y los botones «Instagram» (con el icono de
 * compartir), «Vídeo» (solo la competencia) y «Guardar · Más apps». Las hojas de programa, mes y entreno no cambian.
 */

/** Mensaje cuando no se puede pintar, compartir ni guardar la historia. */
internal const val STORY_SHARE_ERROR = "No se pudo compartir la imagen."

/** Duración del dibujo de las líneas al cambiar de plantilla (la de las demás historias). */
private const val STORY_V4_DRAW_MILLIS = 1800

/** Una píldora de «Incluir»: su texto y si está marcada. */
internal data class StoryInclude(val label: String, val on: Boolean)

/** Icono «compartir» de KPKN Line (retícula 24, trazo 1,75): la flecha que sale de la bandeja. */
internal val StoryShareIcon: ImageVector by lazy {
    val builder = ImageVector.Builder(
        name = "KpknLine.Share",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (d in listOf("M12 4v11M7.5 8.5L12 4l4.5 4.5", "M6 13v5a2 2 0 002 2h8a2 2 0 002-2v-5")) {
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    builder.build()
}

/** Avance del dibujo de las líneas: se reinicia con cada [key] (salvo con movimiento reducido, que va directo al final). */
@Composable
internal fun rememberStoryDrawProgress(vararg key: Any?): Float {
    val reduced = wizardReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(*key, reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = STORY_V4_DRAW_MILLIS))
        }
    }
    return progress.value
}

/**
 * Las salidas de la hoja: «Instagram» (o el selector si no está), «Guardar · Más apps» (guarda en Fotos y abre el selector) y,
 * si hay, «Vídeo». [render] pinta la imagen en segundo plano; mientras dura una salida, [busy] apaga los botones.
 */
internal class StoryOutputs(
    val busy: Boolean,
    val instagram: () -> Unit,
    val saveAndMore: () -> Unit,
    val video: (suspend (Context) -> File) -> Unit,
)

@Composable
internal fun rememberStoryOutputs(render: (Context) -> Bitmap): StoryOutputs {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun launchOutput(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Toast.makeText(context, STORY_SHARE_ERROR, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun withBitmap(action: suspend (Bitmap) -> Unit) = launchOutput {
        val bitmap = withContext(Dispatchers.Default) { render(context) }
        try {
            action(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun saveAndMore() = withBitmap { bitmap ->
        StoryShare.save(context, bitmap)
        Toast.makeText(context, "Imagen guardada en Fotos", Toast.LENGTH_SHORT).show()
        StoryShare.shareWithChooser(context, bitmap)
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveAndMore() else Toast.makeText(context, STORY_SHARE_ERROR, Toast.LENGTH_SHORT).show()
    }

    return StoryOutputs(
        busy = busy,
        instagram = { withBitmap { bitmap -> StoryShare.shareToStories(context, bitmap) } },
        saveAndMore = {
            // Hasta Android 9 (API 28), guardar en Fotos necesita WRITE_EXTERNAL_STORAGE: se pide solo entonces.
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else saveAndMore()
        },
        video = { encode ->
            launchOutput {
                Toast.makeText(context, "Preparando el vídeo…", Toast.LENGTH_SHORT).show()
                val file = encode(context)
                StoryShare.shareVideo(context, file)
            }
        },
    )
}

/**
 * La hoja: [title], la vista previa de 176 dp ([preview] pinta la historia), las [templates] (si hay más de una), [extra] (p. ej.
 * la elección de foto), «Incluir» con [includes] y los botones. [onVideo] nulo oculta «Vídeo».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoryV4ShareSheet(
    title: String,
    tag: String,
    templates: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    includes: List<StoryInclude>,
    onToggle: (Int) -> Unit,
    outputs: StoryOutputs,
    onVideo: (() -> Unit)?,
    onDismiss: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.4).sp),
                color = WizardColors.text,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                preview(Modifier.width(176.dp).testTag("$tag-preview"))
            }
            if (templates.size > 1) {
                KpknSegmentedControl(
                    options = templates,
                    selectedIndex = selected,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("$tag-templates"),
                )
            }
            extra()
            if (includes.isNotEmpty()) {
                Text(
                    text = "INCLUIR",
                    style = KpknType.eyebrow,
                    color = WizardColors.textFaint,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    includes.forEachIndexed { index, include ->
                        KpknChip(
                            text = include.label,
                            selected = include.on,
                            onClick = { onToggle(index) },
                            leadingIcon = if (include.on) KpknLineIcons.Check else null,
                            modifier = Modifier.testTag("$tag-include-$index"),
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknPrimaryPill(
                    text = "Instagram",
                    onClick = outputs.instagram,
                    modifier = Modifier.weight(1f),
                    tag = "$tag-instagram",
                    icon = StoryShareIcon,
                    enabled = !outputs.busy,
                )
                if (onVideo != null) {
                    KpknSecondaryPill(text = "Vídeo", onClick = onVideo, tag = "$tag-video", enabled = !outputs.busy)
                }
            }
            Spacer(Modifier.height(8.dp))
            KpknCompactPill(
                text = "Guardar · Más apps",
                onClick = outputs.saveAndMore,
                modifier = Modifier.testTag("$tag-save-more"),
                height = 40.dp,
                filled = false,
                enabled = !outputs.busy,
                fullWidth = true,
            )
        }
    }
}

/** Carga la foto [path] (ruta o Uri `file:`/`content:`) reducida para el lienzo y girada según su EXIF; null si no se puede. */
internal suspend fun loadStoryPhoto(context: Context, path: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = if (path.startsWith("content:") || path.startsWith("file:")) Uri.parse(path) else Uri.fromFile(File(path))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            var scale = 1
            while (bounds.outWidth / scale > StoryCanvas.WIDTH * 2 || bounds.outHeight / scale > StoryCanvas.HEIGHT * 2) scale *= 2
            inSampleSize = scale
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@runCatching null
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
            }
        }.getOrNull() ?: 1
        val matrix = android.graphics.Matrix()
        when (orientation) {
            3 -> matrix.postRotate(180f)
            6 -> matrix.postRotate(90f)
            8 -> matrix.postRotate(270f)
        }
        if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
        }
    }.getOrNull()
}
