package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateTag
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.domain.training.VolumeCalculator

enum class TemplateQualitySeverity { P0, P1 }

data class TemplateQualityIssue(
    val severity: TemplateQualitySeverity,
    val code: String,
    val message: String,
)

data class TemplateQualityReport(
    val templateId: String,
    val issues: List<TemplateQualityIssue>,
) {
    val p0 get() = issues.filter { it.severity == TemplateQualitySeverity.P0 }
    val p1 get() = issues.filter { it.severity == TemplateQualitySeverity.P1 }
}

/**
 * Pure Kotlin quality auditor for [SessionTemplate] against an exercise muscle index.
 * Resolves catalog entries via exerciseDbId / exerciseId (keys normalized lowercase).
 */
object SessionTemplateQualityRules {

    /** True lower days that must include calves/adductors/glute coverage. */
    private val LOWER_COMPLETENESS_CATEGORIES = setOf(
        SessionTemplateFocusCategory.PIERNAS,
        SessionTemplateFocusCategory.CUADRICEPS,
        SessionTemplateFocusCategory.ISQUIOS,
        SessionTemplateFocusCategory.GLUTEOS,
        SessionTemplateFocusCategory.CADENA_POSTERIOR,
    )

    /** Isolation-only focus days where same-muscle stacking is intentional. */
    private val HYPERFOCUS_CATEGORIES = setOf(
        SessionTemplateFocusCategory.PANTORRILLAS,
        SessionTemplateFocusCategory.CORE,
        SessionTemplateFocusCategory.ANTEBRAZOS,
        SessionTemplateFocusCategory.ADUCTORES,
    )

    private val SMALL_ARM_MUSCLES = setOf("Deltoides", "Bíceps", "Tríceps")

    private val FOCUS_KEYWORDS = listOf(
        "enfoque", "focus", "pecho", "espalda", "hombro", "brazo", "pierna",
        "glúteo", "gluteo", "cuádriceps", "cuadriceps", "isquio", "pantorr",
        "gemelo", "core", "full body", "cuerpo completo", "empuje", "tirón",
        "tiron", "sentadilla", "banca", "peso muerto", "powerlifting",
        "aductor", "anterior", "posterior", "minimalista",
    )

    private val HARD_BW_PATTERNS = listOf(
        "nordic", "dominada", "pull-up", "pull up", "pullup",
        "chin-up", "chin up", "chinup", "nordic inverso",
    )

    fun audit(
        template: SessionTemplate,
        index: Map<String, ExerciseMuscleInfo>,
    ): TemplateQualityReport {
        val normalizedIndex = normalizeIndex(index)
        val exercises = template.session.allExercises()
        val resolved = exercises.map { exercise ->
            exercise to resolveInfo(exercise, normalizedIndex)
        }
        val issues = mutableListOf<TemplateQualityIssue>()

        checkDirectVolumeCap(template, resolved, issues)
        checkBeginnerHardBodyweight(template, resolved, issues)
        checkBeginnerFreeCompound(template, resolved, issues)
        checkCompoundBeforeIsolation(resolved, issues)
        checkFocusFirst(template, resolved, issues)
        checkAlternation(template, resolved, issues)
        checkAdvancedCompoundIntensity(template, resolved, issues)
        checkLowerCompleteness(template, resolved, issues)
        checkExerciseCountAdvanced(template, exercises, issues)
        checkFocusDeclared(template, issues)

        return TemplateQualityReport(templateId = template.id, issues = issues)
    }

    fun auditAll(
        templates: List<SessionTemplate>,
        index: Map<String, ExerciseMuscleInfo>,
    ): List<TemplateQualityReport> {
        val normalizedIndex = normalizeIndex(index)
        return templates.map { audit(it, normalizedIndex) }
    }

    fun p0Violations(
        templates: List<SessionTemplate>,
        index: Map<String, ExerciseMuscleInfo>,
    ): List<TemplateQualityReport> =
        auditAll(templates, index).filter { it.p0.isNotEmpty() }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    fun isCompound(info: ExerciseMuscleInfo?): Boolean {
        if (info == null) return false
        val type = info.type
        if (type?.contains("Aislamiento", ignoreCase = true) == true) return false
        if (type?.contains("Básico", ignoreCase = true) == true) return true
        if (type == null) {
            // Not clearly isolation → treat as compound when force is multi-joint-ish.
            val force = info.force?.lowercase().orEmpty()
            val mono = force.contains("mono") || force.contains("single") ||
                force.contains("aisl") || force.contains("isolation")
            return !mono
        }
        // Accesorio / other: compound if not clearly single-joint isolation.
        if (type.contains("Accesorio", ignoreCase = true)) {
            val force = info.force?.lowercase().orEmpty()
            val mono = force.contains("mono") || force.contains("single") ||
                force.contains("unilateral aisl") || force.contains("aislamiento")
            return !mono && info.technicalDifficulty != null
        }
        return false
    }

    fun isIsolation(info: ExerciseMuscleInfo?): Boolean {
        if (info == null) return false
        val type = info.type.orEmpty()
        if (type.contains("Aislamiento", ignoreCase = true)) return true
        if (type.contains("Accesorio", ignoreCase = true)) {
            val force = info.force?.lowercase().orEmpty()
            return force.contains("mono") || force.contains("single") ||
                force.contains("aisl") || force.contains("isolation")
        }
        return false
    }

    fun isMachine(equipment: String?): Boolean {
        val e = equipment?.lowercase().orEmpty()
        return e.contains("máquina") || e.contains("maquina") || e.contains("machine") ||
            e.contains("cable") || e.contains("polea") || e.contains("smith")
    }

    fun isDumbbell(equipment: String?): Boolean {
        val e = equipment?.lowercase().orEmpty()
        return e.contains("mancuerna") || e.contains("dumbbell")
    }

    fun isBarbellFree(equipment: String?): Boolean {
        if (equipment.isNullOrBlank()) return false
        if (isMachine(equipment)) return false
        val e = equipment.lowercase()
        return e.contains("barra") || e.contains("barbell") ||
            e.contains("peso libre") || e.contains("free weight")
    }

    private fun normalizeIndex(
        index: Map<String, ExerciseMuscleInfo>,
    ): Map<String, ExerciseMuscleInfo> =
        index.mapKeys { it.key.trim().lowercase() }

    private fun resolveInfo(
        exercise: Exercise,
        index: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val candidates = listOfNotNull(
            exercise.exerciseDbId,
            exercise.exerciseId,
            exercise.canonicalExerciseId,
        ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        for (id in candidates) {
            index[id]?.let { return it }
        }
        return null
    }

    private fun canonicalMuscle(raw: String, emphasis: String? = null): String =
        VolumeCalculator.normalizeCanonicalMuscleGroup(raw, emphasis).ifBlank { raw }

    private fun primaryMuscles(info: ExerciseMuscleInfo?): List<String> {
        if (info == null) return emptyList()
        return info.involvedMuscles
            .asSequence()
            .filter { it.role == MuscleRole.PRIMARY }
            .map { canonicalMuscle(it.muscle, it.emphasis) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun firstPrimaryMuscle(info: ExerciseMuscleInfo?): String? =
        primaryMuscles(info).firstOrNull()

    private fun isHyperfocused(template: SessionTemplate): Boolean =
        SessionTemplateTag.HYPERFOCUSED in template.tags ||
            template.focusCategory in HYPERFOCUS_CATEGORIES

    private fun directVolumeCap(template: SessionTemplate): Int {
        if (isHyperfocused(template)) return 12
        return when (template.difficulty) {
            Difficulty.PRINCIPIANTE -> 6
            Difficulty.INTERMEDIO -> 7
            Difficulty.AVANZADO -> 8
        }
    }

    // ─── Rules ────────────────────────────────────────────────────────────────

    private fun checkDirectVolumeCap(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        val cap = directVolumeCap(template)
        val primarySets = mutableMapOf<String, Int>()
        resolved.forEach { (exercise, info) ->
            val setCount = exercise.sets.size
            if (setCount <= 0 || info == null) return@forEach
            primaryMuscles(info).forEach { muscle ->
                primarySets[muscle] = (primarySets[muscle] ?: 0) + setCount
            }
        }
        primarySets.forEach { (muscle, sets) ->
            if (sets > cap) {
                issues += TemplateQualityIssue(
                    severity = TemplateQualitySeverity.P0,
                    code = "DIRECT_VOLUME_CAP",
                    message = "Volumen directo en '$muscle': $sets series > tope $cap (${template.difficulty})",
                )
            }
        }
    }

    private fun checkBeginnerHardBodyweight(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.difficulty != Difficulty.PRINCIPIANTE) return
        resolved.forEach { (exercise, info) ->
            val haystack = listOfNotNull(
                exercise.name,
                exercise.exerciseDbId,
                exercise.exerciseId,
                info?.name,
                info?.id,
            ).joinToString(" ").lowercase()
            if (HARD_BW_PATTERNS.any { haystack.contains(it) }) {
                issues += TemplateQualityIssue(
                    severity = TemplateQualitySeverity.P0,
                    code = "BEGINNER_HARD_BW",
                    message = "Principiante con ejercicio avanzado de peso corporal: '${exercise.name}'",
                )
            }
        }
    }

    private fun checkBeginnerFreeCompound(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.difficulty != Difficulty.PRINCIPIANTE) return
        resolved.forEach { (exercise, info) ->
            if (info == null || !isCompound(info)) return@forEach
            val equipment = info.equipment
            if (isMachine(equipment)) return@forEach
            val primaries = primaryMuscles(info)
            val onlySmallArms = primaries.isNotEmpty() && primaries.all { it in SMALL_ARM_MUSCLES }
            // Mancuernas OK solo en delts/bíceps/tríceps.
            if (onlySmallArms && isDumbbell(equipment)) return@forEach
            val isFree =
                isBarbellFree(equipment) ||
                    isDumbbell(equipment) ||
                    equipment?.contains("peso libre", ignoreCase = true) == true ||
                    equipment?.contains("free weight", ignoreCase = true) == true ||
                    equipment.isNullOrBlank()
            if (!isFree) return@forEach
            val hasNonSmall = primaries.any { it !in SMALL_ARM_MUSCLES }
            if (hasNonSmall || primaries.isEmpty()) {
                issues += TemplateQualityIssue(
                    severity = TemplateQualitySeverity.P0,
                    code = "BEGINNER_FREE_COMPOUND",
                    message = "Principiante con compuesto de barra/peso libre: '${exercise.name}' (${equipment})",
                )
            }
        }
    }

    private fun checkCompoundBeforeIsolation(
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        var isolationSeen = false
        resolved.forEach { (exercise, info) ->
            if (isIsolation(info)) {
                isolationSeen = true
            } else if (isolationSeen && isCompound(info)) {
                issues += TemplateQualityIssue(
                    severity = TemplateQualitySeverity.P0,
                    code = "ORDER_COMPOUND_AFTER_ISO",
                    message = "Compuesto '${exercise.name}' aparece después de un aislamiento en la sesión",
                )
            }
        }
    }

    private fun checkFocusFirst(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        val focus = template.primaryFocusMuscle?.takeIf { it.isNotBlank() } ?: return
        val focusCanonical = canonicalMuscle(focus)
        val firstCompound = resolved.firstOrNull { (_, info) -> isCompound(info) } ?: return
        val firstPrimary = firstPrimaryMuscle(firstCompound.second)
        if (firstPrimary == null ||
            canonicalMuscle(firstPrimary).lowercase() != focusCanonical.lowercase()
        ) {
            issues += TemplateQualityIssue(
                severity = TemplateQualitySeverity.P1,
                code = "FOCUS_NOT_FIRST",
                message = "Primer compuesto primario='$firstPrimary' no coincide con enfoque '$focusCanonical'",
            )
        }
    }

    private fun checkAlternation(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        // Isolation-only / HYPERFOCUSED days intentionally stack the focus muscle.
        if (isHyperfocused(template)) return

        var streakMuscle: String? = null
        var streak = 0
        resolved.forEach { (exercise, info) ->
            val primary = firstPrimaryMuscle(info)?.lowercase()
            if (primary == null) {
                streakMuscle = null
                streak = 0
                return@forEach
            }
            if (primary == streakMuscle) {
                streak++
            } else {
                streakMuscle = primary
                streak = 1
            }
            if (streak >= 3) {
                issues += TemplateQualityIssue(
                    severity = TemplateQualitySeverity.P0,
                    code = "SAME_MUSCLE_STREAK",
                    message = "3+ ejercicios consecutivos con primario '$primary' (último: '${exercise.name}')",
                )
                streak = 0
                streakMuscle = null
            }
        }
    }

    private fun checkAdvancedCompoundIntensity(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.difficulty != Difficulty.AVANZADO) return
        resolved.forEach { (exercise, info) ->
            if (!isCompound(info)) return@forEach
            exercise.sets.forEachIndexed { index, set ->
                val maxIntensity =
                    (set.targetRPE != null && set.targetRPE >= 9.5) ||
                        set.targetRIR == 0 ||
                        set.intensityMode == IntensityMode.FAILURE ||
                        set.isFailure
                if (maxIntensity) {
                    issues += TemplateQualityIssue(
                        severity = TemplateQualitySeverity.P0,
                        code = "ADV_COMPOUND_MAX_INTENSITY",
                        message = "Avanzado: compuesto '${exercise.name}' serie ${index + 1} a intensidad máxima",
                    )
                }
            }
        }
    }

    private fun checkLowerCompleteness(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        val category = template.focusCategory ?: return
        if (category !in LOWER_COMPLETENESS_CATEGORIES) return
        if (isHyperfocused(template)) return

        var calfPrimarySets = 0
        var adductorsPrimarySets = 0
        var glutePrimarySets = 0
        var hasGluteHingeSupport = false

        resolved.forEach { (exercise, info) ->
            if (info == null) return@forEach
            val setCount = exercise.sets.size
            val primaries = primaryMuscles(info)
            if ("Pantorrillas" in primaries) calfPrimarySets += setCount
            if ("Aductores" in primaries) adductorsPrimarySets += setCount
            if ("Glúteos" in primaries) glutePrimarySets += setCount

            val isHinge = info.force?.contains("bisagra", ignoreCase = true) == true ||
                info.force?.contains("hinge", ignoreCase = true) == true ||
                info.movementPattern?.contains("hinge", ignoreCase = true) == true ||
                info.movementPattern?.contains("bisagra", ignoreCase = true) == true ||
                isCompound(info) && (
                    info.name.contains("peso muerto", ignoreCase = true) ||
                        info.name.contains("deadlift", ignoreCase = true) ||
                        info.name.contains("rdl", ignoreCase = true) ||
                        info.name.contains("hip thrust", ignoreCase = true) ||
                        info.name.contains("empuje de cadera", ignoreCase = true)
                    )
            if (isHinge) {
                val gluteInvolvement = info.involvedMuscles.filter {
                    canonicalMuscle(it.muscle, it.emphasis).equals("Glúteos", ignoreCase = true) &&
                        (it.role == MuscleRole.PRIMARY || it.role == MuscleRole.SECONDARY)
                }
                val decent = gluteInvolvement.any { involvement ->
                    val contrib = involvement.volumeContribution
                    involvement.role == MuscleRole.PRIMARY ||
                        (contrib != null && contrib >= 0.35) ||
                        (contrib == null && involvement.role == MuscleRole.SECONDARY)
                }
                if (decent) hasGluteHingeSupport = true
            }
        }

        if (calfPrimarySets <= 0) {
            issues += TemplateQualityIssue(
                severity = TemplateQualitySeverity.P1,
                code = "LOWER_MISSING_CALVES",
                message = "Sesión lower sin series primarias de Pantorrillas/gemelos",
            )
        }
        if (adductorsPrimarySets <= 0) {
            issues += TemplateQualityIssue(
                severity = TemplateQualitySeverity.P1,
                code = "LOWER_MISSING_ADDUCTORS",
                message = "Sesión lower sin series primarias de Aductores",
            )
        }
        if (glutePrimarySets <= 0 && !hasGluteHingeSupport) {
            issues += TemplateQualityIssue(
                severity = TemplateQualitySeverity.P0,
                code = "LOWER_MISSING_GLUTE",
                message = "Sesión lower sin Glúteos primario ni hinge con aporte glúteo decente",
            )
        }
    }

    private fun checkExerciseCountAdvanced(
        template: SessionTemplate,
        exercises: List<Exercise>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.difficulty != Difficulty.AVANZADO) return
        if (exercises.size > 9) {
            issues += TemplateQualityIssue(
                severity = TemplateQualitySeverity.P1,
                code = "ADV_TOO_MANY_EXERCISES",
                message = "Avanzado con ${exercises.size} ejercicios (>9)",
            )
        }
    }

    private fun checkFocusDeclared(
        template: SessionTemplate,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.shortDescription.isNotBlank()) return
        val nameLower = template.name.lowercase()
        val hasKeyword = FOCUS_KEYWORDS.any { nameLower.contains(it) }
        if (!hasKeyword) {
            issues += TemplateQualityIssue(
                severity = TemplateQualitySeverity.P1,
                code = "FOCUS_UNDECLARED",
                message = "shortDescription vacío y el nombre no declara enfoque: '${template.name}'",
            )
        }
    }
}
