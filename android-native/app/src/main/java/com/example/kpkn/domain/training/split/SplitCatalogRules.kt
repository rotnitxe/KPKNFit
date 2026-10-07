package com.example.kpkn.domain.training.split

import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitDayDefinition
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.text.SpanishPlurals

/**
 * Un reparto del catálogo listo para el selector del tablero de la semana: [name] en español llano, [summary] de una
 * línea, un título corto por cada día de entreno ([dayTitles], con letra si el título se repite: «Torso A», «Torso B») y
 * el patrón de siete etiquetas del catálogo ([pattern], con «Descanso») para dibujar la mini-semana.
 */
data class SplitChoice(
    val id: String,
    val name: String,
    val summary: String,
    val dayTitles: List<String>,
    val pattern: List<String>,
    /** El catálogo lo marca «Recomendado KPKN». */
    val recommended: Boolean,
)

/**
 * Reglas del catálogo de repartos que hoy viven en la pantalla del wizard (`compatibleSplitTemplates`,
 * `isSplitOfferedForGoal`, `splitDisplayName`), copiadas al dominio para que el redistribuidor no dependa de `screens/`.
 */
object SplitCatalogRules {

    const val REST_LABEL = "Descanso"

    fun isRest(label: String): Boolean = label.equals(REST_LABEL, ignoreCase = true)

    /** Días de entreno de un reparto: las etiquetas de su patrón que no son descanso. */
    fun trainingDays(split: SplitTemplate): Int = split.pattern.count { !isRest(it) }

    /** Los días de entreno de [split] en el orden del patrón (sin los descansos). */
    internal fun trainingDefinitions(split: SplitTemplate): List<SplitDayDefinition> =
        split.effectiveDayDefinitions().filterNot { isRest(it.label) }.sortedBy { it.ordinal }

    /**
     * Un reparto de powerlifting (etiqueta `POWERLIFTING` del catálogo) solo se ofrece al perfil de fuerza
     * ([TrainingGoalProfile.POWERLIFTING]); el resto de perfiles no lo lista. Sin perfil ([profile] null) todo se ofrece.
     */
    fun isOfferedForProfile(split: SplitTemplate, profile: TrainingGoalProfile?): Boolean =
        profile == null || profile == TrainingGoalProfile.POWERLIFTING || SplitTag.POWERLIFTING !in split.tags

    /**
     * Repartos aplicables hoy: visibles para la aplicación, con [daysCount] días de entreno (cualquiera si es `null`) y
     * ofrecidos al perfil. Los repartos sin ningún día de entreno (el lienzo «Crear desde cero») nunca salen.
     */
    fun compatible(daysCount: Int?, profile: TrainingGoalProfile? = null): List<SplitTemplate> =
        SPLIT_TEMPLATES.filter { split ->
            split.isVisibleForApplication &&
                isOfferedForProfile(split, profile) &&
                trainingDays(split) > 0 &&
                (daysCount == null || trainingDays(split) == daysCount)
        }

    /**
     * Nombre en español llano de un reparto: el mismo en la lista de repartos y en la revisión. Los repartos sin nombre
     * propio aquí conservan el `name` de su plantilla.
     */
    fun displayName(template: SplitTemplate): String = when (template.id) {
        "ul_x4" -> "Torso y pierna, 4 días"
        "ppl_ul" -> "Empuje, tirón, pierna y torso"
        "fullbody_x3" -> "Cuerpo completo, 3 días"
        "ppl_x6" -> "Empuje, tirón y pierna, 6 días"
        "ul_x6" -> "Torso y pierna, 6 días"
        "ppl_arnold" -> "Empuje, tirón y pierna con énfasis"
        "phat_hybrid" -> "Torso, pierna y cuerpo completo"
        "ant_post_x4" -> "Cadena anterior y posterior, 4 días"
        "arnold_ul" -> "Estético y torso/pierna"
        "ant_post_x6" -> "Cadena anterior y posterior, 6 días"
        "bro_split" -> "Un grupo por día"
        "hybrid_fb_ap" -> "Cuerpo completo y cadenas"
        "minimalist_x2" -> "Dos días, lo esencial"
        "weekend_warrior" -> "Fin de semana"
        "glute_focus" -> "Énfasis en glúteos"
        "beach_body" -> "Más torso"
        "fullbody_x5" -> "Cuerpo completo, 5 días"
        "push_pull_x4" -> "Empuje y tirón, 4 días"
        else -> template.name
    }

    /** Nombre del reparto [splitId] en español llano; null si el catálogo no lo conoce (nunca el id). */
    fun displayName(splitId: String): String? =
        SPLIT_TEMPLATES.firstOrNull { it.id == splitId }?.let { displayName(it) }

    /**
     * Títulos cortos de los días de entreno de [split]: la etiqueta del reparto y, si se repite, una letra por
     * aparición («Torso A», «Pierna A», «Torso B», «Pierna B»).
     */
    fun dayTitles(split: SplitTemplate): List<String> = titlesOf(trainingDefinitions(split).map { it.label })

    internal fun titlesOf(labels: List<String>): List<String> {
        val repeated = labels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val seen = HashMap<String, Int>()
        return labels.map { label ->
            if (label !in repeated) {
                label
            } else {
                val occurrence = seen[label] ?: 0
                seen[label] = occurrence + 1
                "$label ${('A' + occurrence)}"
            }
        }
    }

    /** Una línea para el selector: los días y la primera ventaja editorial del reparto. */
    fun summary(split: SplitTemplate): String {
        val days = SpanishPlurals.days(trainingDays(split))
        val highlight = split.pros.firstOrNull { it.isNotBlank() } ?: split.description
        return "$days · $highlight"
    }

    fun choiceOf(split: SplitTemplate): SplitChoice = SplitChoice(
        id = split.id,
        name = displayName(split),
        summary = summary(split),
        dayTitles = dayTitles(split),
        pattern = split.pattern,
        recommended = SplitTag.RECOMENDADO_KPKN in split.tags,
    )
}
