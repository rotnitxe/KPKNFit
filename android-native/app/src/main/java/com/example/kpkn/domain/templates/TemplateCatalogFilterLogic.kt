package com.example.kpkn.domain.templates

import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.sessions.SessionTemplateTag
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.exercises.ExerciseCatalogRegion

/**
 * Modo de agrupación del navegador de plantillas (UI).
 *
 * Orientado a SESIONES completas (días de entreno), no al catálogo anatómico de
 * ejercicios: aquí no existen agrupaciones por músculo suelto, cadena cinética o
 * patrón de movimiento.
 */
enum class TemplateGroupMode(val label: String) {
    MUSCLE_GROUP("Por grupo"),
    SPLIT("Por rutina (split)"),
    SESSION_TYPE("Tipo de sesión"),
    GOAL("Objetivo"),
    LEVEL("Nivel"),
    DURATION("Duración"),
}

/**
 * Agrupación gruesa para el catálogo (Pierna / Torso / Brazo / Full).
 * Deriva de [SessionTemplateFocusCategory], no de músculos sueltos.
 */
enum class TemplateDominantGroup(val label: String) {
    PIERNA("Pierna"),
    TORSO("Torso"),
    BRAZO("Brazo"),
    FULL("Full body"),
    OTRO("Otros"),
}

/**
 * Tipo de día/sesión de entrenamiento. Deriva de [SessionTemplateTag] y/o
 * [SessionTemplateFocusCategory]; una plantilla puede pertenecer a varios tipos
 * (p.ej. un Push Day es EMPUJE y PECHO a la vez).
 */
enum class TemplateSessionType(val label: String) {
    ALL("Todas"),
    EMPUJE("Empuje"),
    TIRON("Tirón"),
    PIERNAS("Piernas"),
    TORSO("Torso"),
    FULL_BODY("Full body"),
    PECHO("Pecho"),
    ESPALDA("Espalda"),
    HOMBROS("Hombros"),
    BRAZOS("Brazos"),
    GLUTEOS("Glúteos"),
    POWERLIFTING("Powerlifting"),
    MINIMALISTA("Minimalista"),
    RECUPERACION("Recuperación"),
    CORE("Core"),
}

/** Objetivo principal de la sesión. Deriva de los tags de objetivo. */
enum class TemplateSessionGoal(val label: String) {
    ALL("Todos"),
    HIPERTROFIA("Hipertrofia"),
    FUERZA("Fuerza"),
    POTENCIA("Potencia"),
    RESISTENCIA("Resistencia"),
    RECUPERACION("Recuperación"),
}

/**
 * Zona corporal dominante de la SESIÓN (no del ejercicio individual).
 * Se resuelve con [SessionTemplateFacets.dominantRegion].
 */
enum class TemplateSessionZone(val label: String) {
    ALL("Cuerpo completo / cualquiera"),
    SUPERIOR("Tren superior"),
    INFERIOR("Tren inferior"),
    FULL("Sesión full body"),
}

data class TemplateCatalogFilters(
    val searchQuery: String = "",
    val sessionType: TemplateSessionType = TemplateSessionType.ALL,
    val goal: TemplateSessionGoal = TemplateSessionGoal.ALL,
    val zone: TemplateSessionZone = TemplateSessionZone.ALL,
    val difficulty: Difficulty? = null,
    val duration: SessionTemplateDurationBucket = SessionTemplateDurationBucket.ALL,
) {
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() ||
            sessionType != TemplateSessionType.ALL ||
            goal != TemplateSessionGoal.ALL ||
            zone != TemplateSessionZone.ALL ||
            difficulty != null ||
            duration != SessionTemplateDurationBucket.ALL
}

data class TemplateCatalogNestedGroup(
    val title: String,
    val templates: List<SessionTemplate>,
)

data class TemplateCatalogSection(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val templates: List<SessionTemplate> = emptyList(),
    val nestedGroups: List<TemplateCatalogNestedGroup> = emptyList(),
) {
    val templateCount: Int
        get() = if (nestedGroups.isNotEmpty()) {
            nestedGroups.sumOf { it.templates.size }
        } else {
            templates.size
        }
}

object TemplateCatalogFilterLogic {

    fun matchesSearch(template: SessionTemplate, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return template.name.contains(q, ignoreCase = true) ||
            template.description.contains(q, ignoreCase = true) ||
            template.muscleGroupsSummary.contains(q, ignoreCase = true) ||
            template.shortDescription.contains(q, ignoreCase = true)
    }

    /**
     * ¿La plantilla es de este tipo de sesión? Combina tags de split
     * (EMPUJE/TIRON/PIERNA/TORSO/CUERPO_COMPLETO), tags de grupo muscular
     * (PECHO/ESPALDA…) y [SessionTemplate.focusCategory]. Nunca usa cadena
     * cinética ni patrón de movimiento.
     */
    fun matchesSessionType(template: SessionTemplate, type: TemplateSessionType): Boolean {
        if (type == TemplateSessionType.ALL) return true
        val tags = template.tags
        val focus = template.focusCategory
        return when (type) {
            TemplateSessionType.ALL -> true
            TemplateSessionType.EMPUJE -> SessionTemplateTag.EMPUJE in tags
            TemplateSessionType.TIRON -> SessionTemplateTag.TIRON in tags
            TemplateSessionType.PIERNAS ->
                SessionTemplateTag.PIERNA in tags ||
                    SessionTemplateTag.CUADRICEPS in tags ||
                    SessionTemplateTag.ISQUIOTIBIALES in tags ||
                    focus == SessionTemplateFocusCategory.PIERNAS ||
                    focus == SessionTemplateFocusCategory.CUADRICEPS ||
                    focus == SessionTemplateFocusCategory.ISQUIOS
            TemplateSessionType.TORSO -> SessionTemplateTag.TORSO in tags
            TemplateSessionType.FULL_BODY ->
                SessionTemplateTag.CUERPO_COMPLETO in tags || focus == SessionTemplateFocusCategory.FULL_BODY
            TemplateSessionType.PECHO ->
                SessionTemplateTag.PECHO in tags || focus == SessionTemplateFocusCategory.PECHO
            TemplateSessionType.ESPALDA ->
                SessionTemplateTag.ESPALDA in tags || focus == SessionTemplateFocusCategory.ESPALDA
            TemplateSessionType.HOMBROS ->
                SessionTemplateTag.HOMBROS in tags || focus == SessionTemplateFocusCategory.HOMBROS
            TemplateSessionType.BRAZOS ->
                SessionTemplateTag.BRAZOS in tags || focus == SessionTemplateFocusCategory.BRAZOS
            TemplateSessionType.GLUTEOS ->
                SessionTemplateTag.GLUTEOS in tags || focus == SessionTemplateFocusCategory.GLUTEOS
            TemplateSessionType.POWERLIFTING ->
                SessionTemplateTag.POWERLIFTING in tags || focus == SessionTemplateFocusCategory.POWERLIFTING
            TemplateSessionType.MINIMALISTA ->
                SessionTemplateTag.MINIMALISTA in tags || focus == SessionTemplateFocusCategory.MINIMALISTA
            TemplateSessionType.RECUPERACION ->
                SessionTemplateTag.RECUPERACION in tags || focus == SessionTemplateFocusCategory.RECUPERACION
            TemplateSessionType.CORE ->
                SessionTemplateTag.CORE in tags || focus == SessionTemplateFocusCategory.CORE
        }
    }

    /** ¿La plantilla persigue este objetivo? Solo tags de objetivo. */
    fun matchesGoal(template: SessionTemplate, goal: TemplateSessionGoal): Boolean {
        if (goal == TemplateSessionGoal.ALL) return true
        val tags = template.tags
        return when (goal) {
            TemplateSessionGoal.ALL -> true
            TemplateSessionGoal.HIPERTROFIA -> SessionTemplateTag.HIPERTROFIA in tags
            TemplateSessionGoal.FUERZA -> SessionTemplateTag.FUERZA in tags
            TemplateSessionGoal.POTENCIA -> SessionTemplateTag.POTENCIA in tags
            TemplateSessionGoal.RESISTENCIA -> SessionTemplateTag.RESISTENCIA in tags
            TemplateSessionGoal.RECUPERACION -> SessionTemplateTag.RECUPERACION in tags
        }
    }

    /** ¿La zona dominante de la sesión coincide con el filtro? */
    fun matchesZone(facets: SessionTemplateFacets, zone: TemplateSessionZone): Boolean {
        if (zone == TemplateSessionZone.ALL) return true
        val dominant = facets.dominantRegion
        return when (zone) {
            TemplateSessionZone.ALL -> true
            TemplateSessionZone.SUPERIOR -> dominant == ExerciseCatalogRegion.UPPER
            TemplateSessionZone.INFERIOR -> dominant == ExerciseCatalogRegion.LOWER
            TemplateSessionZone.FULL -> dominant == ExerciseCatalogRegion.FULL
        }
    }

    fun matchesFilters(
        template: SessionTemplate,
        facets: SessionTemplateFacets?,
        filters: TemplateCatalogFilters,
    ): Boolean {
        if (!matchesSearch(template, filters.searchQuery)) return false
        if (!matchesSessionType(template, filters.sessionType)) return false
        if (!matchesGoal(template, filters.goal)) return false
        if (filters.difficulty != null && template.difficulty != filters.difficulty) return false

        // Zona y duración necesitan facetas reales de la sesión.
        if (facets == null) {
            if (filters.zone != TemplateSessionZone.ALL) return false
            if (filters.duration == SessionTemplateDurationBucket.ALL) return true
            val declaredDuration = template.estimatedDurationMinutes
            val range = filters.duration.range ?: return true
            // Mientras los detalles avanzados siguen bajo demanda, conservar el
            // catálogo navegable usando la duración declarada como fallback.
            return declaredDuration == null || declaredDuration in range
        }
        return matchesZone(facets, filters.zone) &&
            SessionTemplateFacetsBuilder.matchesDuration(facets, filters.duration)
    }

    /**
     * Clones literales "Enfoque X" (`sys-independent-*`): mismo contenido que otra
     * plantilla base. Se mantienen en el catálogo de sistema para políticas/tests,
     * pero no se listan en el navegador principal.
     */
    fun isHiddenCatalogClone(template: SessionTemplate): Boolean =
        template.id.startsWith("sys-independent-") ||
            template.id.startsWith("sys-v3-independent-")

    fun dominantGroup(category: SessionTemplateFocusCategory?): TemplateDominantGroup = when (category) {
        SessionTemplateFocusCategory.PIERNAS,
        SessionTemplateFocusCategory.CUADRICEPS,
        SessionTemplateFocusCategory.ISQUIOS,
        SessionTemplateFocusCategory.GLUTEOS,
        SessionTemplateFocusCategory.PANTORRILLAS,
        SessionTemplateFocusCategory.ADUCTORES,
        SessionTemplateFocusCategory.CADENA_ANTERIOR,
        SessionTemplateFocusCategory.CADENA_POSTERIOR,
        -> TemplateDominantGroup.PIERNA

        SessionTemplateFocusCategory.PECHO,
        SessionTemplateFocusCategory.ESPALDA,
        SessionTemplateFocusCategory.HOMBROS,
        SessionTemplateFocusCategory.CORE,
        -> TemplateDominantGroup.TORSO

        SessionTemplateFocusCategory.BRAZOS,
        SessionTemplateFocusCategory.ANTEBRAZOS,
        -> TemplateDominantGroup.BRAZO

        SessionTemplateFocusCategory.FULL_BODY,
        SessionTemplateFocusCategory.POWERLIFTING,
        SessionTemplateFocusCategory.MINIMALISTA,
        SessionTemplateFocusCategory.RECUPERACION,
        -> TemplateDominantGroup.FULL

        null -> TemplateDominantGroup.OTRO
    }

    fun filterTemplates(
        templates: List<SessionTemplate>,
        facetsById: Map<String, SessionTemplateFacets>,
        filters: TemplateCatalogFilters,
        includeHiddenClones: Boolean = false,
    ): List<SessionTemplate> =
        templates
            .filter { includeHiddenClones || !isHiddenCatalogClone(it) }
            .filter { matchesFilters(it, facetsById[it.id], filters) }

    /** Tipos de sesión presentes en el catálogo (orden del enum, sin ALL). */
    fun availableSessionTypes(templates: List<SessionTemplate>): List<TemplateSessionType> =
        TemplateSessionType.entries
            .filter { it != TemplateSessionType.ALL }
            .filter { type -> templates.any { matchesSessionType(it, type) } }

    /** Objetivos presentes en el catálogo (orden del enum, sin ALL). */
    fun availableGoals(templates: List<SessionTemplate>): List<TemplateSessionGoal> =
        TemplateSessionGoal.entries
            .filter { it != TemplateSessionGoal.ALL }
            .filter { goal -> templates.any { matchesGoal(it, goal) } }

    fun focusCategoryLabel(category: SessionTemplateFocusCategory): String = when (category) {
        SessionTemplateFocusCategory.PIERNAS -> "Piernas"
        SessionTemplateFocusCategory.CUADRICEPS -> "Cuádriceps"
        SessionTemplateFocusCategory.ISQUIOS -> "Isquios"
        SessionTemplateFocusCategory.BRAZOS -> "Brazos"
        SessionTemplateFocusCategory.GLUTEOS -> "Glúteos"
        SessionTemplateFocusCategory.PECHO -> "Pecho"
        SessionTemplateFocusCategory.ESPALDA -> "Espalda"
        SessionTemplateFocusCategory.HOMBROS -> "Hombros"
        SessionTemplateFocusCategory.PANTORRILLAS -> "Pantorrillas"
        SessionTemplateFocusCategory.CORE -> "Core"
        SessionTemplateFocusCategory.ANTEBRAZOS -> "Antebrazos"
        SessionTemplateFocusCategory.ADUCTORES -> "Aductores"
        SessionTemplateFocusCategory.CADENA_ANTERIOR -> "Cadena anterior"
        SessionTemplateFocusCategory.CADENA_POSTERIOR -> "Cadena posterior"
        SessionTemplateFocusCategory.FULL_BODY -> "Full body"
        SessionTemplateFocusCategory.POWERLIFTING -> "Powerlifting"
        SessionTemplateFocusCategory.MINIMALISTA -> "Minimalista"
        SessionTemplateFocusCategory.RECUPERACION -> "Recuperación"
    }

    fun sessionTypeLabel(type: TemplateSessionType): String = type.label

    fun goalLabel(goal: TemplateSessionGoal): String = goal.label

    fun zoneLabel(zone: TemplateSessionZone): String = zone.label

    fun durationLabel(bucket: SessionTemplateDurationBucket): String = when (bucket) {
        SessionTemplateDurationBucket.ALL -> "Cualquiera"
        SessionTemplateDurationBucket.SHORT -> "Corta"
        SessionTemplateDurationBucket.MEDIUM -> "Media"
        SessionTemplateDurationBucket.LONG -> "Larga"
    }

    fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
        Difficulty.PRINCIPIANTE -> "Fácil"
        Difficulty.INTERMEDIO -> "Medio"
        Difficulty.AVANZADO -> "Exigente"
    }

    /**
     * Agrupa plantillas ya filtradas. En modos many-to-many (tipo de sesión /
     * objetivo) una plantilla puede aparecer en varias secciones; dentro de cada
     * sección nunca hay duplicados.
     */
    fun groupTemplates(
        templates: List<SessionTemplate>,
        facetsById: Map<String, SessionTemplateFacets>,
        mode: TemplateGroupMode,
        splits: List<SplitTemplate> = SPLIT_TEMPLATES.filterNot { it.id == "custom" },
    ): List<TemplateCatalogSection> {
        if (templates.isEmpty()) return emptyList()

        return when (mode) {
            TemplateGroupMode.MUSCLE_GROUP -> groupByMuscleGroup(templates)
            TemplateGroupMode.SPLIT -> groupBySplit(templates, splits)
            TemplateGroupMode.SESSION_TYPE -> groupBySessionType(templates)
            TemplateGroupMode.GOAL -> groupByGoal(templates)
            TemplateGroupMode.LEVEL -> groupByLevel(templates)
            TemplateGroupMode.DURATION -> groupByDuration(templates, facetsById)
        }
    }

    private fun groupByMuscleGroup(
        templates: List<SessionTemplate>,
    ): List<TemplateCatalogSection> {
        val sections = mutableListOf<TemplateCatalogSection>()
        val userTemplates = SessionTemplateCatalogPolicy.userTemplateGroup(templates).templates
        if (userTemplates.isNotEmpty()) {
            sections += TemplateCatalogSection(
                key = "user",
                title = "Mis plantillas",
                subtitle = "${userTemplates.size} guardadas",
                templates = userTemplates,
            )
        }

        val system = templates.filter { it.sourceType != SessionTemplateSourceType.USER }
        val order = listOf(
            TemplateDominantGroup.PIERNA,
            TemplateDominantGroup.TORSO,
            TemplateDominantGroup.BRAZO,
            TemplateDominantGroup.FULL,
            TemplateDominantGroup.OTRO,
        )
        order.forEach { group ->
            val list = system
                .filter { dominantGroup(it.focusCategory) == group }
                .distinctBy { it.id }
                .sortedBy { it.sortOrder }
            if (list.isNotEmpty()) {
                sections += TemplateCatalogSection(
                    key = "muscle-${group.name}",
                    title = group.label,
                    subtitle = "${list.size} plantillas",
                    templates = list,
                )
            }
        }
        return sections
    }

    private fun groupBySplit(
        templates: List<SessionTemplate>,
        splits: List<SplitTemplate>,
    ): List<TemplateCatalogSection> {
        val sections = mutableListOf<TemplateCatalogSection>()
        val userTemplates = SessionTemplateCatalogPolicy.userTemplateGroup(templates).templates
        if (userTemplates.isNotEmpty()) {
            sections += TemplateCatalogSection(
                key = "user",
                title = "Mis plantillas",
                subtitle = "${userTemplates.size} guardadas",
                templates = userTemplates,
            )
        }

        splits.forEach { split ->
            val dayGroups = SessionTemplateCatalogPolicy.templatesForSplit(split, templates, emptyMap())
                .mapNotNull { day ->
                    val dayTemplates = day.templates.distinctBy { it.id }
                    if (dayTemplates.isEmpty()) null
                    else TemplateCatalogNestedGroup(title = day.dayLabel, templates = dayTemplates)
                }
            if (dayGroups.isNotEmpty()) {
                sections += TemplateCatalogSection(
                    key = "split-${split.id}",
                    title = split.name,
                    subtitle = split.description.takeIf { it.isNotBlank() },
                    nestedGroups = dayGroups,
                )
            }
        }

        // Plantillas de sistema sin split (independientes) al final, por enfoque.
        val assignedIds = sections
            .flatMap { sec ->
                if (sec.nestedGroups.isNotEmpty()) sec.nestedGroups.flatMap { it.templates }
                else sec.templates
            }
            .map { it.id }
            .toSet()
        val leftovers = templates.filter {
            it.id !in assignedIds &&
                it.sourceType != SessionTemplateSourceType.USER &&
                it.focusCategory != null
        }
        if (leftovers.isNotEmpty()) {
            leftovers
                .groupBy { it.focusCategory!! }
                .toList()
                .sortedBy { it.first.ordinal }
                .forEach { (category, list) ->
                    val unique = list.distinctBy { it.id }.sortedBy { it.sortOrder }
                    sections += TemplateCatalogSection(
                        key = "split-focus-${category.name}",
                        title = focusCategoryLabel(category),
                        subtitle = "${unique.size} plantillas",
                        templates = unique,
                    )
                }
        }

        return sections
    }

    private fun groupBySessionType(
        templates: List<SessionTemplate>,
    ): List<TemplateCatalogSection> {
        val order = TemplateSessionType.entries.filter { it != TemplateSessionType.ALL }
        val assigned = mutableSetOf<String>()
        val sections = order.mapNotNull { type ->
            val matches = templates
                .filter { matchesSessionType(it, type) }
                .distinctBy { it.id }
                .sortedBy { it.sortOrder }
            if (matches.isEmpty()) return@mapNotNull null
            matches.forEach { assigned += it.id }
            TemplateCatalogSection(
                key = "session-${type.name}",
                title = type.label,
                subtitle = "${matches.size} plantillas",
                templates = matches,
            )
        }.toMutableList()

        val leftovers = templates.filter { it.id !in assigned }.distinctBy { it.id }
        if (leftovers.isNotEmpty()) {
            sections += TemplateCatalogSection(
                key = "session-other",
                title = "Otras sesiones",
                subtitle = "${leftovers.size} plantillas",
                templates = leftovers.sortedBy { it.sortOrder },
            )
        }
        return sections
    }

    private fun groupByGoal(
        templates: List<SessionTemplate>,
    ): List<TemplateCatalogSection> {
        val order = TemplateSessionGoal.entries.filter { it != TemplateSessionGoal.ALL }
        val assigned = mutableSetOf<String>()
        val sections = order.mapNotNull { goal ->
            val matches = templates
                .filter { matchesGoal(it, goal) }
                .distinctBy { it.id }
                .sortedBy { it.sortOrder }
            if (matches.isEmpty()) return@mapNotNull null
            matches.forEach { assigned += it.id }
            TemplateCatalogSection(
                key = "goal-${goal.name}",
                title = goal.label,
                subtitle = "${matches.size} plantillas",
                templates = matches,
            )
        }.toMutableList()

        val leftovers = templates.filter { it.id !in assigned }.distinctBy { it.id }
        if (leftovers.isNotEmpty()) {
            sections += TemplateCatalogSection(
                key = "goal-other",
                title = "Sin objetivo",
                subtitle = "${leftovers.size} plantillas",
                templates = leftovers.sortedBy { it.sortOrder },
            )
        }
        return sections
    }

    private fun groupByLevel(
        templates: List<SessionTemplate>,
    ): List<TemplateCatalogSection> {
        val order = listOf(
            Difficulty.PRINCIPIANTE,
            Difficulty.INTERMEDIO,
            Difficulty.AVANZADO,
        )
        return order.mapNotNull { level ->
            val list = templates
                .filter { it.difficulty == level }
                .distinctBy { it.id }
                .sortedBy { it.sortOrder }
            if (list.isEmpty()) null
            else TemplateCatalogSection(
                key = "level-${level.name}",
                title = difficultyLabel(level),
                subtitle = "${list.size} plantillas",
                templates = list,
            )
        }
    }

    private fun groupByDuration(
        templates: List<SessionTemplate>,
        facetsById: Map<String, SessionTemplateFacets>,
    ): List<TemplateCatalogSection> {
        val order = listOf(
            SessionTemplateDurationBucket.SHORT,
            SessionTemplateDurationBucket.MEDIUM,
            SessionTemplateDurationBucket.LONG,
        )
        val byBucket = order.associateWith { mutableListOf<SessionTemplate>() }.toMutableMap()

        templates.forEach { template ->
            val minutes = facetsById[template.id]?.realDurationMinutes
                ?: template.estimatedDurationMinutes
                ?: 0
            val bucket = SessionTemplateFacetsBuilder.durationBucket(minutes)
            byBucket.getOrPut(bucket) { mutableListOf() }.let { list ->
                if (list.none { it.id == template.id }) list += template
            }
        }

        return order.mapNotNull { bucket ->
            val list = byBucket[bucket].orEmpty().sortedBy { it.sortOrder }
            if (list.isEmpty()) null
            else TemplateCatalogSection(
                key = "duration-${bucket.name}",
                title = durationLabel(bucket),
                subtitle = "${list.size} plantillas · ${bucket.label}",
                templates = list,
            )
        }
    }
}
