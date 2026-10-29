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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.SystemAppClock
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estilos de fondo que ofrece la hoja, en su orden. */
private val backgroundStyles = listOf(
    LineCoverStyle.PERFIL,
    LineCoverStyle.RELIEVE,
    LineCoverStyle.ONDAS,
    LineCoverStyle.ORBITAS,
    LineCoverStyle.ESPIRAL,
)

/** Duración dibujada de la historia al cambiar de plantilla o de fondo (igual que las portadas: 1,8 s). */
private const val STORY_DRAW_MILLIS = 1800

/** Mensaje cuando no se puede pintar, compartir ni guardar la historia. */
private const val SHARE_ERROR = "No se pudo compartir la imagen."

/**
 * La hoja «Compartir en historias» de un programa: la vista previa de la historia, sus plantillas, el fondo y las acciones (Instagram
 * o el selector del sistema, y guardar en Fotos). La imagen se pinta en segundo plano; mientras tanto, los botones están apagados.
 */
@Composable
internal fun ProgramShareSheet(
    program: Program,
    logs: List<WorkoutLog>,
    onDismiss: () -> Unit,
    /** La plantilla con la que abre (p. ej. Progreso desde «Tus últimas 4 semanas»); si no está disponible, Semana. */
    initialTemplate: ProgramStoryTemplate = ProgramStoryTemplate.SEMANA,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduced = wizardReducedMotion()
    val templates = remember(program, logs) { ProgramStoryBuilder.templates(program, logs) }
    val base = remember(program) { ProgramStoryBuilder.baseCover(program) }
    val hasInstagram = remember(context) { StoryShare.hasInstagram(context) }
    var template by remember { mutableStateOf(initialTemplate.takeIf { it in templates } ?: ProgramStoryTemplate.SEMANA) }
    var styleChoice by remember { mutableStateOf<LineCoverStyle?>(null) }
    var busy by remember { mutableStateOf(false) }
    val story = remember(program, logs, template, styleChoice) {
        ProgramStoryBuilder.build(program, template, logs, SystemAppClock.today(ZoneId.systemDefault()), styleChoice)
    }
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(template, styleChoice, reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = STORY_DRAW_MILLIS))
        }
    }

    // Pinta la historia en segundo plano y se la pasa a [action]. Mientras dura, los botones están apagados.
    fun withStoryBitmap(action: suspend (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) { renderProgramStory(context, story) }
                try {
                    action(bitmap)
                } finally {
                    bitmap.recycle()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Toast.makeText(context, SHARE_ERROR, Toast.LENGTH_SHORT).show()
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
        if (granted) saveStory() else Toast.makeText(context, SHARE_ERROR, Toast.LENGTH_SHORT).show()
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
                text = "Compartir en historias",
                style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                color = WizardColors.text,
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StoryPreview(modifier = Modifier.width(190.dp).testTag("story-preview")) { measurer ->
                    drawProgramStory(story, measurer, progress.value)
                }
            }
            // Con una sola plantilla no hay nada que elegir.
            if (templates.size > 1) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { option ->
                        KpknCompactPill(
                            text = option.label,
                            onClick = {
                                template = option
                                styleChoice = null
                            },
                            modifier = Modifier.weight(1f).testTag("story-template-${option.name}"),
                            height = 40.dp,
                            filled = option == template,
                            fullWidth = true,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                backgroundStyles.forEach { style ->
                    val chosen = story.cover.style == style
                    LineCoverArt(
                        spec = base.copy(style = style),
                        modifier = Modifier
                            .size(width = 36.dp, height = 45.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .then(if (chosen) Modifier.border(2.dp, WizardColors.text, RoundedCornerShape(9.dp)) else Modifier)
                            .clickable(role = Role.Button) { styleChoice = style }
                            .semantics {
                                contentDescription = style.label
                                selected = chosen
                            },
                    )
                }
            }
            KpknPrimaryPill(
                text = if (hasInstagram) "Compartir en Instagram" else "Compartir",
                onClick = { withStoryBitmap { bitmap -> StoryShare.shareToStories(context, bitmap) } },
                modifier = Modifier.fillMaxWidth(),
                tag = "story-share",
                enabled = !busy,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpknSecondaryPill(
                    text = "Guardar",
                    onClick = { onSaveClick() },
                    modifier = Modifier.weight(1f),
                    tag = "story-save",
                    enabled = !busy,
                )
                KpknSecondaryPill(
                    text = "Más apps",
                    onClick = { withStoryBitmap { bitmap -> StoryShare.shareWithChooser(context, bitmap) } },
                    modifier = Modifier.weight(1f),
                    tag = "story-more",
                    enabled = !busy,
                )
            }
        }
    }
}
