package com.example.kpkn.domain.templates

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateSourceType
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

    private val LEG_MUSCLES = setOf(
        "Cuádriceps", "Glúteos", "Isquiosurales", "Aductores", "Pantorrillas",
    )

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
        val strictCatalog = template.sourceType == SessionTemplateSourceType.SYSTEM
        val resolved = exercises.map { exercise ->
            exercise to resolveInfo(exercise, normalizedIndex, strict = strictCatalog)
        }
        val issues = mutableListOf<TemplateQualityIssue>()

        checkStrictCatalogIdentity(template, resolved, issues)
        checkCommonEquipment(template, resolved, issues)
        checkSystemIntensity(template, resolved, issues)
        checkHeavyPatternAlternation(template, resolved, issues)
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
        checkMultimodalPrescription(template, issues)
        checkHeavyCompoundRest(resolved, issues)
        checkPowerliftingMainLiftContract(template, resolved, issues)
        checkTwoHeavyLowerSameDay(template, resolved, issues)
        checkRestRangesByCategory(template, resolved, issues)

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

    private fun checkStrictCatalogIdentity(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.sourceType != SessionTemplateSourceType.SYSTEM) return
        val occurrences = mutableSetOf<String>()
        resolved.forEach { (exercise, info) ->
            val configurationId = exercise.catalogConfigurationId?.trim()?.lowercase()
            if (configurationId.isNullOrBlank()) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "CATALOG_CONFIGURATION_MISSING",
                    "'${exercise.name}' no tiene una configuración V2 explícita",
                )
                return@forEach
            }
            if (info == null) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "CATALOG_CONFIGURATION_UNKNOWN",
                    "Configuración '$configurationId' no existe en el catálogo aprobado",
                )
                return@forEach
            }
            if (exercise.exerciseDbId?.trim()?.lowercase() != configurationId ||
                exercise.exerciseId?.trim()?.lowercase() != configurationId
            ) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "CATALOG_ID_ALIAS",
                    "'${exercise.name}' no usa la configuración como identidad única",
                )
            }
            if (exercise.catalogRevision != info.catalogRevision ||
                exercise.catalogDefinitionId != info.catalogDefinitionId ||
                exercise.performanceProfileId != info.performanceProfileId
            ) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "CATALOG_IDENTITY_MISMATCH",
                    "Identidad V2 inconsistente en '${exercise.name}' ($configurationId)",
                )
            }
            if (exercise.selectedAspects != null) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "LEGACY_CHIP_STATE",
                    "'${exercise.name}' conserva chips legacy; deben provenir de la configuración exacta",
                )
            }
            val occurrenceId = exercise.occurrenceId?.trim()
            if (occurrenceId.isNullOrBlank() || !occurrences.add(occurrenceId)) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "OCCURRENCE_ID_INVALID",
                    "Ocurrencia duplicada o vacía en '${exercise.name}'",
                )
            }
        }
    }

    private fun checkCommonEquipment(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.sourceType != SessionTemplateSourceType.SYSTEM) return
        val forbidden = listOf(
            "banda", "band", "kettlebell", "pesa rusa", "trx", "hex", "inestable",
            "safety bar", "slider", "deslizador",
        )
        resolved.forEach { (exercise, info) ->
            val haystack = listOfNotNull(exercise.catalogConfigurationId, info?.equipment, info?.name)
                .joinToString(" ")
                .lowercase()
            if (forbidden.any(haystack::contains)) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "NON_STANDARD_VARIANT",
                    "La plantilla usa una variante no priorizada: '${exercise.name}'",
                )
            }
        }
    }

    private fun checkSystemIntensity(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.sourceType != SessionTemplateSourceType.SYSTEM) return
        val powerlifting = SessionTemplateCatalogPolicy.isPowerliftingTemplate(template)
        resolved.forEach { (exercise, info) ->
            val compound = isCompound(info)
            exercise.sets.forEachIndexed { index, set ->
                val rpe = set.targetRPE ?: set.targetRIR?.let { (10 - it).toDouble() }
                if (set.isFailure || set.isAmrap || set.targetRIR == 0) {
                    issues += TemplateQualityIssue(
                        TemplateQualitySeverity.P0,
                        "SYSTEM_FAILURE_SET",
                        "La serie ${index + 1} de '${exercise.name}' no puede ser fallo, AMRAP ni RIR 0",
                    )
                }
                val maximum = when {
                    powerlifting && compound -> 8.5
                    compound -> 8.5
                    else -> 9.0
                }
                if (rpe != null && (rpe < 6.0 || rpe > maximum)) {
                    issues += TemplateQualityIssue(
                        TemplateQualitySeverity.P0,
                        "SYSTEM_INTENSITY_POLICY",
                        "RPE/RIR fuera de política en '${exercise.name}' (serie ${index + 1}: $rpe; máximo $maximum)",
                    )
                }
            }
        }
    }

    private fun checkHeavyPatternAlternation(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.sourceType != SessionTemplateSourceType.SYSTEM) return
        fun primary(info: ExerciseMuscleInfo?): Set<String> = primaryMuscles(info)
            .map(::canonicalMuscle)
            .map(String::lowercase)
            .toSet()
        fun targetRpe(exercise: Exercise): Double = exercise.sets.mapNotNull {
            it.targetRPE ?: it.targetRIR?.let { rir -> (10 - rir).toDouble() }
        }.maxOrNull() ?: 0.0
        fun heavy(exercise: Exercise, info: ExerciseMuscleInfo?): Boolean =
            // La adyacencia se juzga por la intensidad prescrita, no por la
            // demanda teórica del ejercicio. Una sentadilla o un press pueden
            // ser técnicamente compuestos y aun así estar programados como
            // trabajo moderado (RPE 6–7,5); contar esos casos como "pesados"
            // hacía que las plantillas de volumen moderado fueran imposibles
            // de ordenar sin falsos positivos.
            isCompound(info) && targetRpe(exercise) >= 8.0
        val squatCount = resolved.count { (exercise, info) ->
            val pattern = info?.movementPattern.orEmpty().lowercase()
            pattern.contains("knee_dominant") || pattern.contains("sentadilla") ||
                exercise.catalogConfigurationId.orEmpty().contains("squat")
        }
        if (squatCount > 2) {
            issues += TemplateQualityIssue(
                TemplateQualitySeverity.P0,
                "SQUAT_VARIANT_OVERLOAD",
                "La plantilla contiene $squatCount variantes de sentadilla; el máximo del sistema es 2",
            )
        }
        resolved.zipWithNext().forEach { (previous, current) ->
            val (previousExercise, previousInfo) = previous
            val (currentExercise, currentInfo) = current
            if (!heavy(previousExercise, previousInfo) || !heavy(currentExercise, currentInfo)) return@forEach
            val overlap = primary(previousInfo).intersect(primary(currentInfo)).isNotEmpty()
            val previousPattern = previousInfo?.movementPattern.orEmpty()
            val currentPattern = currentInfo?.movementPattern.orEmpty()
            if (overlap || (previousPattern.isNotBlank() && previousPattern == currentPattern)) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "HEAVY_PATTERN_ADJACENCY",
                    "Ejercicios pesados consecutivos comparten músculo o patrón: '${previousExercise.name}' → '${currentExercise.name}'",
                )
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    fun isCompound(info: ExerciseMuscleInfo?): Boolean {
        if (info == null) return false
        info.articulationType?.let { return it.equals("MULTIARTICULAR", ignoreCase = true) }
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
        info.articulationType?.let { return it.equals("AISLADO", ignoreCase = true) }
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
        strict: Boolean = false,
    ): ExerciseMuscleInfo? {
        if (strict) {
            val configurationId = exercise.catalogConfigurationId?.trim()?.lowercase()
                ?: return null
            return index[configurationId]
        }
        return resolveCatalogExerciseInfoInIndex(
            index = index,
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
        )
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
            Difficulty.INTERMEDIO -> 8
            Difficulty.AVANZADO -> 10
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
            // Mancuernas OK solo en delts/bíceps/tríceps y en compuestos de
            // pierna (goblet squat, RDL con mancuerna): son el estándar
            // recomendado para principiantes por su curva de aprendizaje.
            val legDumbbellCompound = isDumbbell(equipment) && primaries.any { it in LEG_MUSCLES }
            if ((onlySmallArms || legDumbbellCompound) && isDumbbell(equipment)) return@forEach
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
        var isolationMuscles = emptySet<String>()
        resolved.forEach { (exercise, info) ->
            if (isIsolation(info)) {
                // Acumula los músculos primarios de los aislamientos vistos.
                isolationMuscles = isolationMuscles + primaryMuscles(info)
            } else if (isolationMuscles.isNotEmpty() && isCompound(info)) {
                // Solo es un orden inválido si el compuesto posterior trabaja el
                // mismo grupo muscular que el aislamiento anterior (p. ej. press
                // compuesto después de aperturas); entre grupos distintos es un
                // orden perfectamente válido.
                val compoundMuscles = primaryMuscles(info)
                if (compoundMuscles.any { canonicalMuscle(it) in isolationMuscles.map(::canonicalMuscle).toSet() }) {
                    issues += TemplateQualityIssue(
                        severity = TemplateQualitySeverity.P1,
                        code = "ORDER_COMPOUND_AFTER_ISO",
                        message = "Compuesto '${exercise.name}' aparece después de un aislamiento del mismo músculo en la sesión",
                    )
                }
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

        // En una plantilla dedicada (pecho, espalda, glúteos, etc.) es normal
        // que tres accesorios consecutivos compartan el primario. La regla de
        // alternancia sigue aplicándose a los días mixtos, donde sí protege la
        // recuperación entre grupos; los compuestos pesados consecutivos se
        // controlan de forma independiente en checkHeavyPatternAlternation.
        val dedicatedFocus = template.focusCategory in setOf(
            SessionTemplateFocusCategory.PECHO,
            SessionTemplateFocusCategory.ESPALDA,
            SessionTemplateFocusCategory.CUADRICEPS,
            SessionTemplateFocusCategory.PIERNAS,
            SessionTemplateFocusCategory.ISQUIOS,
            SessionTemplateFocusCategory.CADENA_POSTERIOR,
            SessionTemplateFocusCategory.GLUTEOS,
            SessionTemplateFocusCategory.HOMBROS,
            SessionTemplateFocusCategory.BRAZOS,
        )
        if (dedicatedFocus) return

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

    /** Timed/cardio/mobility payloads must carry executable durations, not just labels. */
    private fun checkMultimodalPrescription(
        template: SessionTemplate,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        val session = template.session
        // Parts are first-class session content.  Looking only at the loose
        // exercise list silently skipped cardio/mobility blocks created by the
        // editor and made their duration gates unenforceable.  Keep track of
        // exercises owned by a dedicated modality part as well: those payloads
        // may intentionally omit strength sets because their executable recipe
        // lives in the cardio/mobility fields of the part.
        val modalityPartExerciseIds = session.parts
            .filter { it.isCardioGroup || it.isMobilityGroup }
            .flatMap { it.exercises }
            .map { it.id }
            .toSet()
        session.allExercises().forEach { exercise ->
            val isModalityExercise = exercise.id in modalityPartExerciseIds ||
                exercise.cardioDetails != null ||
                exercise.mobilitySeries.isNotEmpty() ||
                exercise.mobilityConfig != null
            if (!isModalityExercise &&
                !SessionTemplateEngine.exerciseHasExecutableStrengthPrescription(exercise)
            ) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "STRENGTH_PRESCRIPTION_MISSING",
                    "Ejercicio de fuerza '${exercise.name}' no tiene series ejecutables",
                )
            }
            val cardio = exercise.cardioDetails
            if (cardio != null) {
                if (!SessionTemplateEngine.isExecutableCardio(cardio)) {
                    issues += TemplateQualityIssue(
                        TemplateQualitySeverity.P0,
                        "CARDIO_DURATION_INVALID",
                        "Cardio '${exercise.name}' no tiene una duración/intervalos ejecutables",
                    )
                }
            }
            exercise.mobilitySeries.forEach { series ->
                if (!SessionTemplateEngine.isExecutableMobility(series)) {
                    issues += TemplateQualityIssue(
                        TemplateQualitySeverity.P0,
                        "MOBILITY_DURATION_INVALID",
                        "Movilidad '${series.name}' tiene series o duración inválida",
                    )
                }
            }
            if (exercise.mobilityConfig != null &&
                !SessionTemplateEngine.isExecutableMobilityConfig(exercise.mobilityConfig)
            ) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "MOBILITY_DURATION_INVALID",
                    "Movilidad '${exercise.name}' no tiene una duración total ejecutable",
                )
            }
        }
        session.parts.flatMap { it.mobilitySeries }.forEach { series ->
            if (!SessionTemplateEngine.isExecutableMobility(series)) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "MOBILITY_DURATION_INVALID",
                    "Bloque de movilidad '${series.name}' tiene series o duración inválida",
                )
            }
        }
        session.parts.forEach { part ->
            if (part.mobilityConfig != null &&
                !SessionTemplateEngine.isExecutableMobilityConfig(part.mobilityConfig)
            ) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "MOBILITY_DURATION_INVALID",
                    "Bloque de movilidad '${part.name}' no tiene una duración total ejecutable",
                )
            }
        }
        session.parts.forEach { part ->
            val cardioPartInvalid = part.isCardioGroup &&
                (part.exercises.isEmpty() || part.exercises.any { exercise ->
                    exercise.cardioDetails?.let(SessionTemplateEngine::isExecutableCardio) != true
                })
            if (cardioPartInvalid) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "CARDIO_DURATION_INVALID",
                    "Bloque de cardio '${part.name}' no tiene una receta temporal ejecutable",
                )
            }
            val mobilityPartHasPayload = part.mobilitySeries.isNotEmpty() ||
                SessionTemplateEngine.isExecutableMobilityConfig(part.mobilityConfig) ||
                part.exercises.any { exercise ->
                    exercise.mobilitySeries.isNotEmpty() ||
                        SessionTemplateEngine.isExecutableMobilityConfig(exercise.mobilityConfig)
                }
            val mobilityPartInvalid = part.isMobilityGroup &&
                (!mobilityPartHasPayload ||
                    part.mobilitySeries.any { !SessionTemplateEngine.isExecutableMobility(it) } ||
                    part.exercises.any { exercise ->
                        exercise.mobilitySeries.isEmpty() &&
                            !SessionTemplateEngine.isExecutableMobilityConfig(exercise.mobilityConfig)
                    })
            if (mobilityPartInvalid) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "MOBILITY_DURATION_INVALID",
                    "Bloque de movilidad '${part.name}' no tiene series ni duración ejecutable",
                )
            }
        }
        session.warmup.forEach { warmup ->
            if (!SessionTemplateEngine.isExecutableWarmup(warmup)) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P0,
                    "WARMUP_DURATION_INVALID",
                    "Calentamiento '${warmup.name}' tiene una duración inválida",
                )
            }
        }

        // A label, target duration, empty modality group or empty warm-up is
        // visible editor content but cannot be executed.  Keep overwrite
        // confirmation broad (SessionTemplateEngine.sessionHasContent) while
        // making the published/generation gate agree with the strict program
        // execution contract.
        if (!SessionTemplateEngine.sessionHasExecutableContent(session)) {
            issues += TemplateQualityIssue(
                TemplateQualitySeverity.P0,
                "SESSION_EXECUTION_MISSING",
                "La sesión '${template.name}' no contiene una receta ejecutable",
            )
        }
    }

    /** Heavy compound work should expose enough rest for safe execution. */
    private fun checkHeavyCompoundRest(
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        resolved.forEach { (exercise, info) ->
            if (!isCompound(info)) return@forEach
            val lowRepStrength = exercise.sets.any { set -> (set.plannedRepAnchor() ?: Int.MAX_VALUE) <= 6 }
            val explicitStrengthLabel = listOfNotNull(
                exercise.targetSessionGoal,
                exercise.name,
                exercise.canonicalExerciseId,
                exercise.exerciseDbId,
            ).any { label ->
                val normalized = label.lowercase()
                normalized.contains("fuerza") ||
                    normalized.contains("strength") ||
                    normalized.contains("powerlifting") ||
                    normalized.contains("competition") ||
                    normalized.contains("competici") ||
                    normalized.contains("sbd")
            } || exercise.isCompetitionLift
            val highIntensity = exercise.sets.any { set ->
                (set.targetRPE ?: set.targetRIR?.let { (10 - it).toDouble() } ?: 0.0) >= 8.0 ||
                    (set.targetPercentageRM ?: 0.0) >= 80.0
            }
            // RPE/%RM alone is not enough: an 8–12-rep hypertrophy compound
            // can legitimately use 90–120 seconds.  Heavy strength work is
            // the intersection of intensity and low reps or an explicit PL /
            // fuerza contract.
            val heavy = highIntensity && (lowRepStrength || explicitStrengthLabel)
            val rest = exercise.restTime ?: info?.averageRestSeconds ?: 0
            if (heavy && rest < 180) {
                issues += TemplateQualityIssue(
                    TemplateQualitySeverity.P1,
                    "HEAVY_COMPOUND_REST_SHORT",
                    "Compuesto pesado '${exercise.name}' prescribe solo ${rest}s de descanso (<180s)",
                )
            }
        }
    }

    /** Public PL recipes must mark the exact SBD lift and give it competition rest. */
    private fun checkPowerliftingMainLiftContract(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        val exactPowerliftingSplit = template.splitIds.any { it == "pl_sbd_x3" || it == "pl_classic_4" }
        if (!exactPowerliftingSplit || !SessionTemplateCatalogPolicy.isPowerliftingTemplate(template)) return
        val main = resolved.firstOrNull()?.first ?: return
        val id = listOfNotNull(main.catalogConfigurationId, main.exerciseDbId, main.exerciseId)
            .joinToString(" ").lowercase()
        if (!main.isCompetitionLift || id.contains("smith")) {
            issues += TemplateQualityIssue(
                TemplateQualitySeverity.P0,
                "PL_MAIN_LIFT_MARKER_MISSING",
                "Receta powerlifting '${template.id}' no marca un levantamiento SBD de competición exacto",
            )
        }
        if ((main.restTime ?: 0) < 180) {
            issues += TemplateQualityIssue(
                TemplateQualitySeverity.P0,
                "PL_MAIN_REST_SHORT",
                "Receta powerlifting '${template.id}' prescribe menos de 180s para el principal",
            )
        }
    }

    /** Two heavy lower compounds same session only if recipe explicitly allows (P0). */
    private fun checkTwoHeavyLowerSameDay(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.sourceType != SessionTemplateSourceType.SYSTEM) return
        // SBD powerlifting recipes intentionally combine squat+deadlift variants
        if (template.id.startsWith("sys-sbd-") || template.id.startsWith("sys-v3-pl-sbd") || template.id.startsWith("sys-v3-pl-classic")) return
        val heavyLower = resolved.filter { (exercise, info) ->
            if (!isCompound(info)) return@filter false
            val primaries = primaryMuscles(info).map { it.lowercase() }.toSet()
            val isLower = primaries.any { it in LEG_MUSCLES.map(String::lowercase) }
            if (!isLower) return@filter false
            val rpe = exercise.sets.mapNotNull { it.targetRPE ?: it.targetRIR?.let { rir -> (10 - rir).toDouble() } }.maxOrNull() ?: 0.0
            val pct = exercise.sets.mapNotNull { it.targetPercentageRM }.maxOrNull() ?: 0.0
            val lowRep = exercise.sets.any { (it.plannedRepAnchor() ?: Int.MAX_VALUE) <= 6 }
            val heavy = (rpe >= 8.0 || pct >= 80.0) && (lowRep || exercise.isCompetitionLift)
            heavy
        }
        if (heavyLower.size >= 2) {
            val names = heavyLower.joinToString(" + ") { it.first.name }
            issues += TemplateQualityIssue(
                TemplateQualitySeverity.P0,
                "TWO_HEAVY_LOWER_SAME_DAY",
                "Dos compuestos pesados de tren inferior el mismo día sin receta que lo permita: $names",
            )
        }
    }

    /** Rest ranges by category: main 180-300, technique 120-240, compound accessory 90-180, isolation 45-120. */
    private fun checkRestRangesByCategory(
        template: SessionTemplate,
        resolved: List<Pair<Exercise, ExerciseMuscleInfo?>>,
        issues: MutableList<TemplateQualityIssue>,
    ) {
        if (template.sourceType != SessionTemplateSourceType.SYSTEM) return
        resolved.forEach { (exercise, info) ->
            val rest = exercise.restTime ?: info?.averageRestSeconds ?: return@forEach
            val isMain = exercise.isCompetitionLift
            val isIso = isIsolation(info)
            val isComp = isCompound(info)
            when {
                isMain -> {
                    if (rest < 180 || rest > 300) {
                        issues += TemplateQualityIssue(
                            TemplateQualitySeverity.P1,
                            "MAIN_REST_OUT_OF_RANGE",
                            "Levantamiento principal '${exercise.name}' descanso ${rest}s fuera de 180-300s",
                        )
                    }
                }
                isComp && !isMain -> {
                    // Heuristic: heavy technique vs accessory by RPE
                    val maxRpe = exercise.sets.mapNotNull { it.targetRPE ?: it.targetRIR?.let { rir -> (10 - rir).toDouble() } }.maxOrNull() ?: 0.0
                    if (maxRpe >= 8.0) {
                        if (rest < 120 || rest > 240) {
                            issues += TemplateQualityIssue(
                                TemplateQualitySeverity.P1,
                                "TECHNIQUE_REST_OUT_OF_RANGE",
                                "Compuesto técnico '${exercise.name}' descanso ${rest}s fuera de 120-240s",
                            )
                        }
                    } else {
                        if (rest < 90 || rest > 180) {
                            issues += TemplateQualityIssue(
                                TemplateQualitySeverity.P1,
                                "COMPOUND_ACCESSORY_REST_OUT_OF_RANGE",
                                "Accesorio compuesto '${exercise.name}' descanso ${rest}s fuera de 90-180s",
                            )
                        }
                    }
                }
                isIso -> {
                    if (rest < 45 || rest > 120) {
                        issues += TemplateQualityIssue(
                            TemplateQualitySeverity.P1,
                            "ISOLATION_REST_OUT_OF_RANGE",
                            "Aislamiento '${exercise.name}' descanso ${rest}s fuera de 45-120s",
                        )
                    }
                }
            }
        }
    }
}
