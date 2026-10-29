package com.example.kpkn.screens.stories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.onboarding.design.WizardMassUnit

/** ¿Hay una historia de Cuerpo que compartir con [rows]? (dos semanas con peso o un perímetro medido dos veces). */
fun bodyStoryAvailable(rows: List<BodyHistoryRow>): Boolean = bodyStoryTemplates(rows).isNotEmpty()

/**
 * La hoja «Compartir tu progreso» (escenas h-body-*): vista previa de 176, plantillas Cambio · Medidas, «Incluir» (Peso actual
 * apagado: el peso absoluto nunca sale por defecto; Cambio en kg, Medidas y % de grasa marcados), «Instagram» y «Guardar · Más
 * apps». Sin vídeo ni fotos de progreso.
 */
@Composable
fun BodyStoryShareSheet(rows: List<BodyHistoryRow>, weightUnit: WizardMassUnit, onDismiss: () -> Unit) {
    val templates = remember(rows) { bodyStoryTemplates(rows) }
    if (templates.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var selected by remember(templates) { mutableIntStateOf(0) }
    val template = templates[selected.coerceIn(0, templates.lastIndex)]
    var options by remember { mutableStateOf(BodyStoryOptions()) }
    val page = remember(rows, weightUnit, template, options) { bodyStoryPage(template, rows, weightUnit, options) }
    val progress = rememberStoryDrawProgress(template)
    val outputs = rememberStoryOutputs { ctx -> renderBodyStory(ctx, template, page) }
    val includes = listOf(
        StoryInclude("Peso actual", options.currentWeight),
        StoryInclude("Cambio en ${weightUnit.code}", options.change),
        StoryInclude("Medidas", options.measures),
        StoryInclude("% de grasa", options.fat),
    )
    StoryV4ShareSheet(
        title = "Compartir tu progreso",
        tag = "body-story",
        templates = templates.map { it.label },
        selected = selected,
        onSelect = { selected = it },
        includes = includes,
        onToggle = { index ->
            options = when (index) {
                0 -> options.copy(currentWeight = !options.currentWeight)
                1 -> options.copy(change = !options.change)
                2 -> options.copy(measures = !options.measures)
                else -> options.copy(fat = !options.fat)
            }
        },
        outputs = outputs,
        onVideo = null,
        onDismiss = onDismiss,
        preview = { modifier -> StoryPreview(modifier) { measurer -> drawBodyStory(template, page, measurer, progress) } },
    )
}
