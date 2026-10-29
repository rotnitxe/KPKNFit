package com.example.kpkn.screens.stories

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.domain.training.SystemAppClock
import com.example.kpkn.screens.onboarding.design.WizardColors
import java.time.ZoneId

/**
 * ¿Hay algo que compartir de [competition]? Sirve para mostrar u ocultar la acción «Compartir» en la ficha, el ⋯, la celebración y
 * la tarjeta «Próxima». [allCompetitions] es la lista completa (para la próxima competencia y la comparación del total).
 */
fun competitionStoryAvailable(competition: CompetitionRecord, allCompetitions: List<CompetitionRecord>): Boolean =
    competitionStoryAvailability(competition, allCompetitions, SystemAppClock.today(ZoneId.systemDefault())).templates.isNotEmpty()

/**
 * La hoja «Compartir tu competencia» (escena h-meet-sheet): vista previa de 176, plantillas (Total · Intentos · Foto · Próxima en
 * fuerza; Tiempo, Tarima o Cuadro en carrera, culturismo y combate), «Incluir» (Medalla y Puntos marcados, Peso corporal apagado) y
 * «Instagram», «Vídeo» (la felicitación de 6 s, solo con resultado) y «Guardar · Más apps». Lee solo campos que ya tiene
 * [CompetitionRecord]. Con [startWithNext] abre en «Próxima» (desde la tarjeta de la próxima competencia).
 */
@Composable
fun CompetitionStoryShareSheet(
    competition: CompetitionRecord,
    allCompetitions: List<CompetitionRecord>,
    onDismiss: () -> Unit,
    startWithNext: Boolean = false,
) {
    val context = LocalContext.current
    val today = remember { SystemAppClock.today(ZoneId.systemDefault()) }
    val availability = remember(competition, allCompetitions) { competitionStoryAvailability(competition, allCompetitions, today) }
    val templates = availability.templates
    if (templates.isEmpty()) {
        // Nada que compartir (p. ej. culturismo sin foto): la hoja se cierra sola.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var selected by remember(templates) {
        mutableIntStateOf(if (startWithNext) templates.indexOf(CompetitionStoryTemplate.PROXIMA).coerceAtLeast(0) else 0)
    }
    val template = templates[selected.coerceIn(0, templates.lastIndex)]
    var options by remember { mutableStateOf(CompetitionStoryOptions()) }
    var photoIndex by remember { mutableIntStateOf(0) }
    val photoPath = availability.photos.getOrNull(photoIndex)
    val photo: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, photoPath) {
        value = photoPath?.let { loadStoryPhoto(context, it)?.asImageBitmap() }
    }
    val page = remember(competition, allCompetitions, template, options) {
        competitionStoryPage(template, competition, allCompetitions, today, options)
    }
    val usesPhoto = template == CompetitionStoryTemplate.FOTO || template == CompetitionStoryTemplate.TARIMA
    val shownPhoto = photo.takeIf { usesPhoto }
    val progress = rememberStoryDrawProgress(template, photoIndex)
    val outputs = rememberStoryOutputs { ctx -> renderCompetitionStory(ctx, template, page, shownPhoto) }
    val celebration = remember(competition, allCompetitions, options) { competitionCelebration(competition, allCompetitions, today, options) }

    val includes = buildList {
        if (availability.hasMedal) add("Medalla" to options.medal)
        if (availability.hasPoints) add("Puntos" to options.points)
        if (availability.hasBodyweight) add("Peso corporal" to options.bodyweight)
    }
    StoryV4ShareSheet(
        title = "Compartir tu competencia",
        tag = "competition-story",
        templates = templates.map { it.label },
        selected = selected,
        onSelect = { selected = it },
        includes = includes.map { StoryInclude(it.first, it.second) },
        onToggle = { index ->
            options = when (includes[index].first) {
                "Medalla" -> options.copy(medal = !options.medal)
                "Puntos" -> options.copy(points = !options.points)
                else -> options.copy(bodyweight = !options.bodyweight)
            }
        },
        outputs = outputs,
        onVideo = celebration?.let { data -> { outputs.video { ctx -> encodeCompetitionVideo(ctx, data) } } },
        onDismiss = onDismiss,
        preview = { modifier ->
            StoryPreview(modifier) { measurer -> drawCompetitionStory(template, page, measurer, shownPhoto, progress) }
        },
        extra = {
            // Con varias fotos, se elige cuál va en la historia (la primera por defecto).
            if (usesPhoto && availability.photos.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availability.photos.take(6).forEachIndexed { index, path ->
                        val shape = RoundedCornerShape(14.dp)
                        AsyncImage(
                            model = path,
                            contentDescription = "Foto ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(shape)
                                .border(if (index == photoIndex) 2.dp else 1.dp, if (index == photoIndex) WizardColors.text else Color.White.copy(alpha = 0.12f), shape)
                                .clickable(role = Role.RadioButton) { photoIndex = index },
                        )
                    }
                }
            }
        },
    )
}
