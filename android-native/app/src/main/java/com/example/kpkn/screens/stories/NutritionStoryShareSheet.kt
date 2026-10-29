package com.example.kpkn.screens.stories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** ¿Hay una historia de Nutrición que compartir? (algún día registrado, una media de macros o una racha). */
fun nutritionStoryAvailable(days: List<NutritionStoryDay>, macros: NutritionStoryMacros?, streak: NutritionStoryStreak?): Boolean =
    nutritionStoryTemplates(days, macros, streak).isNotEmpty()

/**
 * La hoja «Compartir tu semana» (escena h-nutri-sheet): vista previa de 176, plantillas Semana · Macros · Racha (solo las que tienen
 * datos), «Incluir» (Calorías y Objetivo, marcadas), «Instagram» y «Guardar · Más apps». Sin vídeo. Nunca sale qué comiste ni el
 * peso: [days] son kcal y meta por día, [macros] la media diaria de macros y [streak] la racha del hábito «Comidas».
 */
@Composable
fun NutritionStoryShareSheet(
    days: List<NutritionStoryDay>,
    macros: NutritionStoryMacros?,
    streak: NutritionStoryStreak?,
    onDismiss: () -> Unit,
) {
    val templates = remember(days, macros, streak) { nutritionStoryTemplates(days, macros, streak) }
    if (templates.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var selected by remember(templates) { mutableIntStateOf(0) }
    val template = templates[selected.coerceIn(0, templates.lastIndex)]
    var options by remember { mutableStateOf(NutritionStoryOptions()) }
    val page = remember(days, macros, streak, template, options) { nutritionStoryPage(template, days, macros, streak, options) }
    val progress = rememberStoryDrawProgress(template)
    val outputs = rememberStoryOutputs { ctx -> renderNutritionStory(ctx, template, page) }
    StoryV4ShareSheet(
        title = "Compartir tu semana",
        tag = "nutrition-story",
        templates = templates.map { it.label },
        selected = selected,
        onSelect = { selected = it },
        includes = listOf(StoryInclude("Calorías", options.calories), StoryInclude("Objetivo", options.goal)),
        onToggle = { index ->
            options = if (index == 0) options.copy(calories = !options.calories) else options.copy(goal = !options.goal)
        },
        outputs = outputs,
        onVideo = null,
        onDismiss = onDismiss,
        preview = { modifier -> StoryPreview(modifier) { measurer -> drawNutritionStory(template, page, measurer, progress) } },
    )
}
