package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.training.VolumeCalculator

data class SplitTemplateDayGroup(
    val splitId: String,
    val splitName: String,
    val dayIndex: Int,
    val dayLabel: String,
    val templates: List<SessionTemplate>,
    val estimatedMuscleVolume: Map<String, Double> = emptyMap(),
    val warnings: List<String> = emptyList()
)

data class FocusTemplateGroup(
    val category: SessionTemplateFocusCategory,
    val templates: List<SessionTemplate>
)

data class UserTemplateGroup(
    val templates: List<SessionTemplate>
)

object SessionTemplateCatalogPolicy {

    // Rango de volumen semanal objetivo por músculo canónico
    val WEEKLY_VOLUME_RANGES = mapOf(
        "Pectorales" to 10.0..14.0,
        "Dorsales" to 10.0..14.0,
        "Trapecio" to 6.0..10.0,
        "Deltoides" to 8.0..14.0,
        "Bíceps" to 6.0..10.0,
        "Tríceps" to 6.0..10.0,
        "Cuádriceps" to 10.0..16.0,
        "Isquiosurales" to 8.0..12.0,
        "Glúteos" to 8.0..14.0,
        "Pantorrillas" to 6.0..12.0,
        "Aductores" to 4.0..8.0,
        "Abdomen" to 4.0..10.0,
        "Core" to 4.0..8.0,
        // Compuestos (sentadilla / bisagra) aportan erectores secundarios reales del catálogo.
        "Erectores Espinales" to 3.0..12.0,
    )

    fun templatesForSplit(
        split: SplitTemplate,
        templates: List<SessionTemplate>,
        exerciseIndex: Map<String, ExerciseMuscleInfo> = emptyMap()
    ): List<SplitTemplateDayGroup> {
        if (split.id == "custom") return emptyList()
        val groups = mutableListOf<SplitTemplateDayGroup>()

        split.pattern.forEachIndexed { index, dayLabel ->
            if (dayLabel.equals("Descanso", ignoreCase = true)) return@forEachIndexed

            val dayTemplates = templatesForSplitDay(split.id, dayLabel, templates)

            // We pre-calculate estimated muscle volumes and warnings for the first template as representative
            val representativeTemplate = dayTemplates.firstOrNull()
            val estimatedVol = if (representativeTemplate != null && exerciseIndex.isNotEmpty()) {
                calculateSessionMuscleVolume(representativeTemplate.session, exerciseIndex)
            } else {
                emptyMap()
            }

            val warnings = mutableListOf<String>()
            if (representativeTemplate != null && exerciseIndex.isNotEmpty()) {
                val drain = evaluateTemplateRings(representativeTemplate, exerciseIndex)
                val isPl = isPowerliftingTemplate(representativeTemplate)
                val caps = RingBudgetPolicy.sessionWarningCaps(isPl)

                if (drain.cns > caps.cns) warnings += "Fatiga SNC elevada (${drain.cns}% > ${caps.cns}%)"
                if (drain.muscular > caps.muscular) warnings += "Fatiga Muscular elevada (${drain.muscular}% > ${caps.muscular}%)"
                if (drain.spinal > caps.spinal) warnings += "Carga axial/espinal elevada (${drain.spinal}% > ${caps.spinal}%)"
            }

            groups.add(
                SplitTemplateDayGroup(
                    splitId = split.id,
                    splitName = split.name,
                    dayIndex = index,
                    dayLabel = dayLabel,
                    templates = dayTemplates.sortedBy { it.sortOrder },
                    estimatedMuscleVolume = estimatedVol,
                    warnings = warnings
                )
            )
        }
        return groups
    }

    fun independentTemplateGroups(templates: List<SessionTemplate>): List<FocusTemplateGroup> {
        val independentTemplates = templates.filter { it.splitIds.isEmpty() && it.focusCategory != null }
        return independentTemplates
            .groupBy { it.focusCategory!! }
            .map { (category, list) ->
                FocusTemplateGroup(category, list.sortedBy { it.sortOrder })
            }
            .sortedBy { it.category.ordinal }
    }

    fun userTemplateGroup(templates: List<SessionTemplate>): UserTemplateGroup {
        return UserTemplateGroup(
            templates = templates
                .filter { it.sourceType == SessionTemplateSourceType.USER && !it.isArchived }
                .sortedBy { it.name.lowercase() }
        )
    }

    fun templatesForSplitDay(
        splitId: String,
        dayLabel: String,
        templates: List<SessionTemplate>
    ): List<SessionTemplate> {
        val normalizedCandidates = candidateLabelsFor(dayLabel)
        val exact = templates.filter { template ->
            template.splitIds.contains(splitId) && template.splitDayLabels.any { it.equals(dayLabel, ignoreCase = true) }
        }
        val sameSplitArchetype = templates.filter { template ->
            template.splitIds.contains(splitId) && template.splitDayLabels.any { it.normalizedLabel() in normalizedCandidates }
        }
        val sharedArchetype = templates.filter { template ->
            template.splitIds.isNotEmpty() && template.splitDayLabels.any { it.normalizedLabel() in normalizedCandidates }
        }
        val independentArchetype = templates.filter { template ->
            template.splitIds.isEmpty() && template.focusCategory != null && template.focusCategory in focusCategoriesFor(dayLabel)
        }

        val exactMapped = exact.map { it to 1 }
        val sameSplitMapped = sameSplitArchetype.map { it to 2 }
        val sharedMapped = sharedArchetype.map { it to 3 }
        val independentMapped = independentArchetype.map { it to 4 }

        return (exactMapped + sameSplitMapped + sharedMapped + independentMapped)
            .distinctBy { it.first.id }
            .sortedWith(compareBy<Pair<SessionTemplate, Int>> { it.second }.thenBy { it.first.sortOrder })
            .map { it.first }
    }

    fun calculateSessionMuscleVolume(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>
    ): Map<String, Double> {
        val entries = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), exerciseIndex.values.toList())
        return entries.associate { it.muscleName to it.displayVolume }
    }

    fun evaluateTemplateRings(
        template: SessionTemplate,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        settings: Settings = Settings(),
    ): PredictedDrain {
        return try {
            AugeFatigueEngine.calculateAdjustedPredictedDrain(
                template.session, exerciseIndex, settings,
            )
        } catch (_: Throwable) {
            PredictedDrain(cns = 100, muscular = 100, spinal = 100)
        }
    }

    fun isPowerliftingTemplate(template: SessionTemplate): Boolean {
        return template.focusCategory == SessionTemplateFocusCategory.POWERLIFTING ||
                template.tags.any {
                    it.name.contains("POWERLIFTING") ||
                    it.name.contains("SENTADILLA") ||
                    it.name.contains("PESO_MUERTO") ||
                    it.name.contains("BANCA")
                } ||
                template.id.contains("pl-") ||
                template.id.contains("sbd") ||
                template.id.contains("sheiko") ||
                template.id.contains("texas")
    }

    fun candidateLabelsForDay(dayLabel: String): Set<String> = candidateLabelsFor(dayLabel)

    fun focusCategoriesForDay(dayLabel: String): Set<SessionTemplateFocusCategory> =
        focusCategoriesFor(dayLabel)

    private fun candidateLabelsFor(dayLabel: String): Set<String> {
        val normalized = dayLabel.normalizedLabel()
        val labels = linkedSetOf(normalized)
        fun add(vararg values: String) = values.forEach { labels += it.normalizedLabel() }

        if (normalized.contains("empuje") || normalized.contains("push") || normalized.contains("press") || normalized.contains("pecho") && normalized.contains("triceps")) {
            add("Empuje", "Pecho", "Push", "Press Banca/Pecho", "Pecho/Espalda")
        }
        if (normalized.contains("tirón") || normalized.contains("tiron") || normalized.contains("pull") || normalized.contains("espalda") || normalized.contains("bíceps") || normalized.contains("biceps")) {
            add("Tirón", "Espalda", "Pull", "Peso Muerto/Espalda", "Espalda/Bíceps", "Cadena Posterior")
        }
        if (normalized.contains("pierna") || normalized.contains("lower") || normalized.contains("cuádriceps") || normalized.contains("cuadriceps")) {
            add(
                "Pierna", "Piernas", "Lower", "Cuádriceps/Glúteo", "Pierna Mantenimiento",
                "Sentadilla/Pierna", "Cadena Anterior",
                "Isquios", "Isquiosurales", "Glúteos", "Glúteo", "Femoral", "Glúteo/Isquios",
            )
        }
        if (normalized.contains("isquio") || normalized.contains("femoral") || normalized.contains("hamstring")) {
            add("Isquios", "Isquiosurales", "Femoral", "Glúteo/Isquios", "Pierna", "Lower", "Cadena Posterior")
        }
        if (normalized.contains("gluteo") || normalized.contains("glúteo")) {
            add("Glúteo/Isquios", "Glúteo Pump", "Cuádriceps/Glúteo", "Pierna", "Lower", "Glúteos", "Glúteo")
        }
        if (normalized.contains("torso") || normalized.contains("upper")) {
            add(
                "Torso", "Upper", "Upper Completo", "Torso Liviano",
                "Pecho", "Espalda", "Hombros", "Brazos", "Pecho/Espalda", "Hombro/Brazo",
                "Hombros/Brazos", "Espalda/Bíceps", "Press Banca/Pecho",
            )
        }
        if (normalized.contains("full body") || normalized.contains("cuerpo completo")) {
            add("Cuerpo Completo", "Full Body", "Full Body A", "Full Body B", "Cuerpo Completo A", "Cuerpo Completo B", "Cuerpo Completo C", "Cuerpo Completo D", "Full Body Pesado", "Full Body Liviano", "Full Body Medio")
        }
        if (normalized.contains("hombro") || normalized.contains("delts") || normalized.contains("brazo") || normalized.contains("brazos") || normalized.contains("triceps") || normalized.contains("biceps")) {
            add("Hombro/Brazo", "Hombros/Brazos", "Brazos/Hombros", "Hombros/Abs", "T1 Militar", "Hombros", "Brazos")
        }
        if (normalized.contains("anterior")) add("Cadena Anterior")
        if (normalized.contains("posterior")) add("Cadena Posterior")

        if (normalized.contains("sbd")) add("SBD Día 1", "SBD Día 2", "SBD Día 3", "SBD (Volumen)", "SBD (Técnica)", "SBD (Intensidad)")
        if (normalized.contains("sentadilla") || normalized.contains("squat")) add("Sentadilla/Banca", "Sentadilla/Peso Muerto", "T1 Sentadilla", "Sentadilla")
        if (normalized.contains("banca") || normalized.contains("bench")) add("Banca Volumen", "Banca", "T1 Banca", "Banca/Sentadilla Var.", "Peso Muerto/Banca Var.")
        if (normalized.contains("peso muerto") || normalized.contains("deadlift") || normalized.contains("dl")) add("Peso Muerto", "Peso Muerto/Banca", "Peso Muerto/Accesorios", "Peso Muerto/Press", "Variante DL/Banca", "T1 Peso Muerto")
        if (normalized.contains("volumen") || normalized.contains("recuperación") || normalized.contains("recuperacion") || normalized.contains("intensidad") || normalized.contains("repeticiones") || normalized.contains("explosivo") || normalized.contains("pesado") || normalized.contains("liviano") || normalized.contains("moderado")) {
            add("Full Body", "SBD Día 1", "SBD Día 2", "SBD Día 3", "Minimalista · Fuerza")
        }
        if (normalized.contains("max effort") || normalized.contains("dynamic effort") || normalized.contains("me ") || normalized.contains("de ")) add("Sentadilla/Banca", "T1 Sentadilla", "T1 Banca")
        if (normalized.contains("bodybuilding") || normalized.contains("accesorios")) add("Upper", "Torso", "Hombro/Brazo")
        if (normalized.contains("sesion")) add("SBD Día 1", "SBD Día 2", "SBD Día 3", "Full Body", "Minimalista · Fuerza")

        return labels
    }

    private fun focusCategoriesFor(dayLabel: String): Set<SessionTemplateFocusCategory> {
        val normalized = dayLabel.normalizedLabel()
        val categories = linkedSetOf<SessionTemplateFocusCategory>()
        if (normalized.contains("pierna") || normalized.contains("lower") || normalized.contains("cuádriceps") || normalized.contains("cuadriceps")) {
            categories += SessionTemplateFocusCategory.PIERNAS
            categories += SessionTemplateFocusCategory.CUADRICEPS
            categories += SessionTemplateFocusCategory.ISQUIOS
            categories += SessionTemplateFocusCategory.GLUTEOS
        }
        if (normalized.contains("isquio") || normalized.contains("femoral") || normalized.contains("hamstring")) {
            categories += SessionTemplateFocusCategory.ISQUIOS
            categories += SessionTemplateFocusCategory.PIERNAS
        }
        if (normalized.contains("gluteo") || normalized.contains("glúteo")) categories += SessionTemplateFocusCategory.GLUTEOS
        if (normalized.contains("pecho") || normalized.contains("empuje") || normalized.contains("push") || normalized.contains("banca")) categories += SessionTemplateFocusCategory.PECHO
        if (normalized.contains("espalda") || normalized.contains("tirón") || normalized.contains("tiron") || normalized.contains("pull")) categories += SessionTemplateFocusCategory.ESPALDA
        if (normalized.contains("hombro") || normalized.contains("delts")) categories += SessionTemplateFocusCategory.HOMBROS
        if (normalized.contains("brazo") || normalized.contains("brazos") || normalized.contains("biceps") || normalized.contains("bíceps") || normalized.contains("triceps")) {
            categories += SessionTemplateFocusCategory.BRAZOS
        }
        if (normalized.contains("torso") || normalized.contains("upper")) {
            categories += SessionTemplateFocusCategory.PECHO
            categories += SessionTemplateFocusCategory.ESPALDA
            categories += SessionTemplateFocusCategory.HOMBROS
            categories += SessionTemplateFocusCategory.BRAZOS
        }
        if (normalized.contains("full") || normalized.contains("cuerpo completo") || normalized.contains("volumen") || normalized.contains("recuperación") || normalized.contains("recuperacion")) categories += SessionTemplateFocusCategory.FULL_BODY
        if (normalized.contains("sbd") || normalized.contains("sentadilla") || normalized.contains("banca") || normalized.contains("peso muerto") || normalized.contains("t1") || normalized.contains("max effort") || normalized.contains("dynamic effort")) categories += SessionTemplateFocusCategory.POWERLIFTING
        if (normalized.contains("minimalista")) categories += SessionTemplateFocusCategory.MINIMALISTA
        if (normalized.contains("anterior")) categories += SessionTemplateFocusCategory.CADENA_ANTERIOR
        if (normalized.contains("posterior")) categories += SessionTemplateFocusCategory.CADENA_POSTERIOR
        if (normalized.contains("pantorr") || normalized.contains("gemelo") || normalized.contains("calf")) {
            categories += SessionTemplateFocusCategory.PANTORRILLAS
        }
        if (normalized.contains("core") || normalized.contains("abdom") || normalized.contains("abs")) {
            categories += SessionTemplateFocusCategory.CORE
        }
        if (normalized.contains("antebrazo") || normalized.contains("forearm") || normalized.contains("grip")) {
            categories += SessionTemplateFocusCategory.ANTEBRAZOS
        }
        if (normalized.contains("aductor") || normalized.contains("adductor")) {
            categories += SessionTemplateFocusCategory.ADUCTORES
        }
        if (normalized.contains("sesion")) categories += SessionTemplateFocusCategory.FULL_BODY
        if (normalized.contains("recuperación") || normalized.contains("recuperacion") || normalized.contains("light")) categories += SessionTemplateFocusCategory.RECUPERACION
        return categories
    }

    private fun String.normalizedLabel(): String = lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .trim()
}
