package com.example.kpkn.screens.stories

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.kpkn.domain.training.progress.MonthlyReport
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Duración dibujada de la historia al cambiar de plantilla (la misma que la del programa). */
private const val MONTH_STORY_DRAW_MILLIS = 1800

/** Mensaje cuando no se puede pintar, compartir ni guardar la historia. */
private const val MONTH_SHARE_ERROR = "No se pudo compartir la imagen."

/**
 * La hoja «Compartir el mes»: la vista previa de la historia del mes, sus plantillas (Resumen, Constancia y, si hay datos, Fuerza y
 * Músculos) y las mismas acciones que la del programa (Instagram o el selector del sistema, guardar en Fotos y más apps).
 */
@Composable
internal fun MonthShareSheet(report: MonthlyReport, programName: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduced = wizardReducedMotion()
    val story = remember(report, programName) { monthStoryFrom(report, programName, null) }
    val templates = remember(story) { monthStoryTemplates(story) }
    val hasInstagram = remember(context) { StoryShare.hasInstagram(context) }
    var template by remember { mutableStateOf(MonthStoryTemplate.RESUMEN) }
    var busy by remember { mutableStateOf(false) }
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(template, reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = MONTH_STORY_DRAW_MILLIS))
        }
    }

    // Pinta la historia en segundo plano y se la pasa a [action]. Mientras dura, los botones están apagados.
    fun withStoryBitmap(action: suspend (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) { renderMonthStory(context, story, template) }
                try {
                    action(bitmap)
                } finally {
                    bitmap.recycle()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Toast.makeText(context, MONTH_SHARE_ERROR, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun saveStory() = withStoryBitmap { bitmap ->
        StoryShare.save(context, bitmap)
        Toast.makeText(context, "Imagen guardada en Fotos", Toast.LENGTH_SHORT).show()
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveStory() else Toast.makeText(context, MONTH_SHARE_ERROR, Toast.LENGTH_SHORT).show()
    }

    // Hasta Android 9 (API 28), guardar en Fotos necesita WRITE_EXTERNAL_STORAGE: se pide solo entonces.
    fun onSaveClick() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else saveStory()
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Compartir ${story.monthName}",
                style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                color = WizardColors.text,
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StoryPreview(modifier = Modifier.width(190.dp).testTag("month-story-preview")) { measurer ->
                    drawMonthStory(story, template, measurer, progress.value)
                }
            }
            // Con una sola plantilla no hay nada que elegir.
            if (templates.size > 1) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { option ->
                        KpknCompactPill(
                            text = option.label,
                            onClick = { template = option },
                            modifier = Modifier.weight(1f).testTag("month-story-template-${option.name}"),
                            height = 40.dp,
                            filled = option == template,
                            fullWidth = true,
                        )
                    }
                }
            }
            KpknPrimaryPill(
                text = if (hasInstagram) "Compartir en Instagram" else "Compartir",
                onClick = { withStoryBitmap { bitmap -> StoryShare.shareToStories(context, bitmap) } },
                modifier = Modifier.fillMaxWidth(),
                tag = "month-story-share",
                enabled = !busy,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpknSecondaryPill(
                    text = "Guardar",
                    onClick = { onSaveClick() },
                    modifier = Modifier.weight(1f),
                    tag = "month-story-save",
                    enabled = !busy,
                )
                KpknSecondaryPill(
                    text = "Más apps",
                    onClick = { withStoryBitmap { bitmap -> StoryShare.shareWithChooser(context, bitmap) } },
                    modifier = Modifier.weight(1f),
                    tag = "month-story-more",
                    enabled = !busy,
                )
            }
        }
    }
}
