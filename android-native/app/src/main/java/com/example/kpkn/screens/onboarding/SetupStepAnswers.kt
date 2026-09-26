package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoveryResponseState
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeCalibrationProfile
import com.example.kpkn.data.models.VolumeCalibrationResponses
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatValidation
import com.example.kpkn.domain.training.VolumeCalibrationEngine
import com.example.kpkn.screens.nutrition.NutritionWizardDraft

/**
 * Reductor puro de respuestas del wizard tradicional.
 *
 * Contrato (API exacta UI/VM):
 * - [SetupWizardDraft.withStepChoice] / [SetupWizardDraft.withStepChoices] /
 *   [SetupWizardDraft.withStepText] / [SetupWizardDraft.withStepNumber]
 *   escriben la respuesta de UN paso de forma atómica: selección canónica +
 *   proyección al campo tipado que consumen los motores. **Ninguno** mueve el
 *   cursor, registra `stepProgress.answers` ni toca revisiones: la
 *   confirmación es exclusiva de `confirmCurrentStep` (el registro de
 *   respuestas ocurre allí, nunca en un toque de UI).
 * - El texto crudo vive en `inputTexts` con clave `step.name` SIEMPRE (nunca
 *   `SetupStepId`): conserva el intermedio de tecleo («1» de «19») y el texto
 *   inválido mientras el usuario corrige; la validación lo señala sin borrarlo.
 * - Los valores son los estables del catálogo (`SetupStepDefinition.options`);
 *   las proyecciones traducen a enums/dominio existentes sin fabricar datos.
 * - Exclusivos (`none`/`unknown`/`omit`): el valor excluyente gana siempre y
 *   jamás convive con otros (normalización determinista en la escritura).
 * - Fechas reales (`currentWeightMeasuredAtEpochMs`, `bodyFatCapturedAtEpochMs`,
 *   `capturedAtMs` de Rings): se escriben solo cuando hay una declaración nueva
 *   y nunca se regeneran al rehidratar o reafirmar el mismo valor.
 */

// ─── Proyección de selecciones ───────────────────────────────────────────────

/**
 * Selecciones canónicas del paso: las guardadas en [SetupWizardDraft.stepSelections]
 * o, si aún no existe ninguna (borrador rehidratado), la proyección tipada del
 * propio borrador. Nunca inventa valores: sin dato devuelve vacío.
 */
fun SetupWizardDraft.selectedValues(step: SetupStepId): Set<String> {
    val stored = stepSelections[step]
    if (!stored.isNullOrEmpty()) return stored.toSet()
    return typedSelections(step)
}

/** Selección única del paso: guarda el valor estable y proyecta al campo tipado. */
fun SetupWizardDraft.withStepChoice(
    step: SetupStepId,
    value: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft {
    val normalized = normalizeExclusive(step, setOf(value))
    val stored = copy(stepSelections = stepSelections + (step to normalized.toList()))
    return stored.projectChoice(step, normalized.firstOrNull(), nowEpochMs)
}

/**
 * Selección múltiple del paso. Normaliza los valores exclusivos antes de
 * guardar: si aparece uno, el conjunto resultante es SOLO ese valor.
 */
fun SetupWizardDraft.withStepChoices(
    step: SetupStepId,
    values: Set<String>,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft {
    val normalized = normalizeExclusive(step, values)
    val stored = copy(stepSelections = stepSelections + (step to normalized.toList()))
    return stored.projectChoices(step, normalized, nowEpochMs)
}

/**
 * Texto crudo del paso (clave `step.name`). El texto se conserva tal cual —
 * inválido incluido — y solo los pasos numéricos proyectan el valor parseado:
 * un texto que no parsea deja el tipado intacto para que la validación lo
 * señale sin perder lo que el usuario escribió.
 */
fun SetupWizardDraft.withStepText(
    step: SetupStepId,
    value: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft {
    val keyed = if (value.isEmpty()) {
        copy(inputTexts = inputTexts - step.name)
    } else {
        copy(inputTexts = inputTexts + (step.name to value))
    }
    return when (step) {
        SetupStepId.NAME -> keyed.copy(name = WizChatValidation.cleanText(value))
        // Grasa corporal medida: el crudo se conserva (inválido incluido) y la
        // fuente manda. Con «No lo sé» se retiran número, fecha y texto
        // obsoleto: la omisión nunca deja un resto que parezca declarado.
        SetupStepId.BODY_FAT -> when {
            bodyFatSource == SetupBodyFatSource.UNKNOWN -> copy(
                inputTexts = inputTexts - step.name,
                bodyFatPercent = null,
                bodyFatCapturedAtEpochMs = null,
            )
            else -> {
                val parsed = value.takeIf { it.isNotBlank() }?.let { parseLocalizedNumber(it) }
                when {
                    parsed != null -> keyed.projectNumber(step, parsed, nowEpochMs)
                    // Borrado explícito (texto vacío): retira también el tipado.
                    value.isBlank() -> keyed.projectNumber(step, null, nowEpochMs)
                    else -> keyed
                }
            }
        }
        SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT, SetupStepId.SESSION_TIME -> {
            val parsed = value.takeIf { it.isNotBlank() }?.let { parseLocalizedNumber(it) }
            when {
                parsed != null -> keyed.projectNumber(step, parsed, nowEpochMs)
                // Borrado explícito (texto vacío): retira también el tipado.
                value.isBlank() -> keyed.projectNumber(step, null, nowEpochMs)
                else -> keyed
            }
        }
        else -> keyed
    }
}

/**
 * Número del paso; `value` null retira el dato (nunca deja un default).
 * Escribe también el texto canónico en `inputTexts` (clave `step.name`);
 * si el retiro llega con texto inválido en el campo, se conserva ese texto
 * para que la validación lo señale en lugar de silenciar el error.
 */
fun SetupWizardDraft.withStepNumber(
    step: SetupStepId,
    value: Double?,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft {
    val current = inputTexts[step.name]
    val keyed = when {
        // Mismo número, otro formato («70,» / «70.» / «70.0» → 70): el crudo
        // que el usuario está escribiendo se conserva intacto.
        value != null && current != null && parseLocalizedNumber(current) == value -> this
        // Rueda/regla a un valor realmente distinto: el crudo obsoleto se
        // reemplaza por el canónico del nuevo número.
        value != null -> copy(inputTexts = inputTexts + (step.name to canonicalNumberText(value)))
        current == null -> this
        else -> {
            val parsed = parseLocalizedNumber(current)
            val range = SetupStepDefinitions.of(step)?.range
            val invalidText = parsed == null || (range != null && parsed !in range.min..range.max)
            if (invalidText) this else copy(inputTexts = inputTexts - step.name)
        }
    }
    return keyed.projectNumber(step, value, nowEpochMs)
}

// ─── Normalización ───────────────────────────────────────────────────────────

/** El valor exclusivo de la definición gana siempre; nunca se mezcla con otros. */
private fun normalizeExclusive(step: SetupStepId, values: Set<String>): Set<String> {
    if (values.isEmpty()) return emptySet()
    val exclusive = SetupStepDefinitions.of(step)?.exclusiveValues.orEmpty()
    if (exclusive.isEmpty()) return values
    val chosen = exclusive.firstOrNull { it in values } ?: return values
    return setOf(chosen)
}

/** Texto canónico de un número: entero sin decimales, el resto con punto. */
private fun canonicalNumberText(value: Double): String =
    if (value.isFinite() && value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// ─── Proyecciones multi ──────────────────────────────────────────────────────

private fun SetupWizardDraft.projectChoices(
    step: SetupStepId,
    values: Set<String>,
    nowEpochMs: Long,
): SetupWizardDraft = when (step) {
    SetupStepId.WEEKDAYS -> copy(
        selectedWeekdays = values.mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet(),
    )

    SetupStepId.NUTRITION_ELIGIBILITY -> withNutrition {
        it.copy(
            eligibilityUnknown = "unknown" in values,
            pregnant = "pregnancy" in values,
            lactating = "lactation" in values,
            medicalRestriction = "medical_restriction" in values,
        )
    }

    SetupStepId.RINGS_DISCOMFORT -> withRings(nowEpochMs) { answers ->
        when {
            "omit" in values -> answers.copy(discomfortIds = emptyList(), discomfortState = SetupDiscomfortState.OMITTED)
            "none" in values -> answers.copy(discomfortIds = emptyList(), discomfortState = SetupDiscomfortState.NONE)
            values.isEmpty() -> answers.copy(discomfortIds = emptyList(), discomfortState = SetupDiscomfortState.NOT_ANSWERED)
            else -> answers.copy(discomfortIds = values.sorted(), discomfortState = SetupDiscomfortState.DECLARED)
        }
    }

    SetupStepId.HOME_EQUIPMENT -> copy(equipment = values.mapNotNull { legacyEquipmentValue(it) }.toSet())

    SetupStepId.AVAILABILITY -> {
        val categories = if (AVAILABILITY_BODYWEIGHT in values) {
            emptySet()
        } else {
            values.mapNotNull { token -> EquipmentCategory.entries.firstOrNull { it.name == token } }.toSet()
        }
        copy(trainingOptions = trainingOptions.copy(availability = EquipmentAvailability(categories)))
    }

    else -> projectChoice(step, values.firstOrNull(), nowEpochMs)
}

// ─── Proyección única ────────────────────────────────────────────────────────

private fun SetupWizardDraft.projectChoice(
    step: SetupStepId,
    value: String?,
    nowEpochMs: Long,
): SetupWizardDraft = when (step) {
    // El sexo de cálculo vive en el borrador de nutrición; «unknown» lo deja
    // sin determinar explícitamente y jamás toca `physiqueModel` ni el género.
    SetupStepId.EQUATION_SEX, SetupStepId.NUTRITION_SEX -> withNutrition {
        it.copy(equationSex = when (value) {
            "female" -> EerSex.FEMALE
            "male" -> EerSex.MALE
            else -> null
        })
    }

    SetupStepId.BODY_FAT -> {
        val source = when (value?.uppercase()) {
            "MEASURED" -> SetupBodyFatSource.MEASURED
            "VISUAL_ESTIMATE", "VISUAL" -> SetupBodyFatSource.VISUAL_ESTIMATE
            "UNKNOWN" -> SetupBodyFatSource.UNKNOWN
            else -> null
        }
        when {
            source == null -> this
            // Omisión explícita: sin percentil, sin fecha y sin texto obsoleto.
            source == SetupBodyFatSource.UNKNOWN -> copy(
                bodyFatSource = SetupBodyFatSource.UNKNOWN,
                bodyFatPercent = null,
                bodyFatCapturedAtEpochMs = null,
                inputTexts = inputTexts - step.name,
            )
            source == bodyFatSource -> this
            bodyFatSource == null -> copy(bodyFatSource = source)
            // Cambiar de fuente no reutiliza el valor de la otra: la figura o
            // el campo nuevo tienen que declararlo después.
            else -> copy(
                bodyFatSource = source,
                bodyFatPercent = null,
                bodyFatCapturedAtEpochMs = null,
                inputTexts = inputTexts - step.name,
            )
        }
    }

    SetupStepId.EXPERIENCE -> copy(experience = when (value) {
        "new" -> SetupExperience.NEW
        "returning" -> SetupExperience.RETURNING
        "intermediate" -> SetupExperience.INTERMEDIATE
        "advanced" -> SetupExperience.ADVANCED
        else -> null
    })

    // Sin opción «después»/manual: ambas rutas implican entrenamiento activo.
    SetupStepId.ROUTE -> if (value == "recommended" || value == "protocol") copy(
        programRoute = if (value == "protocol") SetupProgramRoute.PROTOCOL else SetupProgramRoute.CUSTOMIZABLE,
        trainingPath = SetupTrainingPath.PERSONALIZE,
        includeTraining = true,
    ) else this

    SetupStepId.GOAL -> {
        val resolved = when (value) {
            "strength" -> SetupGoal.STRENGTH
            "muscle" -> SetupGoal.MUSCLE
            "strength_muscle" -> SetupGoal.STRENGTH_MUSCLE
            "health" -> SetupGoal.HEALTH
            "mixed" -> SetupGoal.MIXED
            else -> null
        }
        when (resolved) {
            null -> this
            else -> {
                val base = copy(goal = resolved, cardioType = null, cardioMinutes = null)
                val inferred = resolved.inferredTrainingStyle
                if (inferred != null) base.withVolumeStyle(inferred)
                else base.copy(
                    volumeAnswers = base.volumeAnswers.copy(style = null),
                    volumeCalibrationProfile = null,
                    volumeRecommendations = emptyList(),
                    athleteProfileScore = null,
                )
            }
        }
    }

    SetupStepId.STYLE -> when (value) {
        "powerlifter" -> withVolumeStyle(TrainingStyle.POWERLIFTER)
        "bodybuilder" -> withVolumeStyle(TrainingStyle.BODYBUILDER)
        "powerbuilder" -> withVolumeStyle(TrainingStyle.POWERBUILDER)
        else -> this
    }

    SetupStepId.VOLUME_TECHNIQUE -> withVolumeResponse(1, value)
    SetupStepId.VOLUME_CONSISTENCY -> withVolumeResponse(2, value)
    SetupStepId.VOLUME_STRENGTH -> withVolumeResponse(3, value)
    SetupStepId.VOLUME_MOBILITY -> withVolumeResponse(4, value)

    // Un SOLO inventario principal: cambiar de entorno NO borra lo declarado
    // (inventory, selecciones ni filas a medias). Solo se retira el perfil
    // LEGACY ajeno (`equipment` GYM/MACHINE del entorno anterior) y los grupos
    // que el nuevo entorno pregunta quedan marcados para revisión; borrar un
    // grupo concreto es la acción explícita «No tengo/Quitar» de M4.
    SetupStepId.EQUIPMENT -> if (value == null) this else {
        val environmentChanged = value != trainingEnvironment
        val neededGroups = if (environmentChanged) {
            copy(trainingEnvironment = value).inventoryGroups().map(SetupStepDefinitions::stepOf).toSet()
        } else emptySet()
        val availability = when (value) {
            "gym", "Gimnasio completo" -> EquipmentAvailability(EquipmentCategory.entries.toSet())
            "machines", "Principalmente máquinas" -> EquipmentAvailability(
                setOf(EquipmentCategory.MACHINES, EquipmentCategory.CABLE, EquipmentCategory.DUMBBELLS),
            )
            "none", "Sin material" -> EquipmentAvailability(emptySet())
            else -> null
        }
        copy(
            trainingEnvironment = value,
            equipment = when (value) {
                "gym", "Gimnasio completo" -> setOf(SetupEquipment.GYM)
                "machines", "Principalmente máquinas" -> setOf(SetupEquipment.MACHINE)
                else -> emptySet()
            },
            trainingOptions = trainingOptions.copy(availability = availability),
            stepSelections = if (environmentChanged) stepSelections - SetupStepId.AVAILABILITY else stepSelections,
            stepProgress = if (neededGroups.isNotEmpty()) stepProgress.withPendingReview(neededGroups) else stepProgress,
        )
    }

    SetupStepId.DAYS -> copy(daysPerWeek = value?.toIntOrNull())

    SetupStepId.CARDIO_TYPE -> copy(cardioType = CardioType.entries.firstOrNull { it.name == value })

    SetupStepId.CARDIO_TIME -> copy(cardioMinutes = value?.toIntOrNull())

    SetupStepId.TRAINING_MAX -> copy(
        knowsTrainingMarks = value == "yes",
        powerliftingProfile = if (value == "yes") powerliftingProfile else null,
    )

    SetupStepId.GENDER -> copy(profileGender = when (value) {
        "female" -> Gender.FEMALE
        "male" -> Gender.MALE
        "other" -> Gender.OTHER
        else -> null
    })

    SetupStepId.NUTRITION_START -> if (value == null) this else {
        val mode = when (value) {
            "self_defined" -> NutritionConfigurationMode.SELF_DEFINED
            "tracking_only" -> NutritionConfigurationMode.TRACKING_ONLY
            else -> NutritionConfigurationMode.AUTOMATIC
        }
        val base = nutritionDraft ?: NutritionWizardDraft(mode = "create", planId = nutritionPlanId)
        copy(
            includeNutrition = true,
            nutritionMode = "create",
            nutritionDraft = base.copy(
                mode = "create",
                direction = base.direction.takeUnless { it == PlanDirection.PROFESSIONAL },
                configurationMode = mode,
            ),
        )
    }

    SetupStepId.NUTRITION_DIRECTION -> withNutrition {
        it.copy(direction = when (value) {
            "deficit" -> PlanDirection.DEFICIT
            "maintenance" -> PlanDirection.MAINTENANCE
            "surplus" -> PlanDirection.SURPLUS
            else -> it.direction
        })
    }

    SetupStepId.NUTRITION_RHYTHM -> withNutrition {
        it.copy(pacePreset = when (value) {
            "slow" -> WizardPacePreset.SLOW
            "fast" -> WizardPacePreset.FAST
            "medium" -> WizardPacePreset.MEDIUM
            else -> it.pacePreset
        })
    }

    SetupStepId.NUTRITION_ACTIVITY -> withNutrition {
        it.copy(activity = EerActivity.entries.firstOrNull { item -> item.name == value } ?: it.activity)
    }

    SetupStepId.NUTRITION_DISTRIBUTION -> withNutrition {
        it.copy(weeklyDistribution = when (value) {
            "uniform" -> NutritionWeeklyDistributionMode.UNIFORM
            "variable" -> NutritionWeeklyDistributionMode.VARIABLE
            else -> it.weeklyDistribution
        })
    }

    SetupStepId.NUTRITION_HISTORY_CONTEXT -> when (value) {
        null -> copy(weightTrend = null)
        "rising", "stable", "falling" -> copy(weightTrend = value)
        else -> this
    }

    SetupStepId.RINGS_RECENT -> withRings(nowEpochMs) { answers ->
        when (value) {
            "yes" -> answers.copy(recentTraining = true, recentTrainingState = SetupRecentTrainingState.YES)
            "no" -> answers.copy(recentTraining = false, recentTrainingState = SetupRecentTrainingState.NO)
            "unknown" -> answers.copy(recentTraining = null, recentTrainingState = SetupRecentTrainingState.UNKNOWN)
            else -> answers
        }
    }

    SetupStepId.RINGS_SESSIONS -> withRings(nowEpochMs) { it.copy(sessionsLastSevenDays = value?.toIntOrNull()) }

    SetupStepId.RINGS_RECENCY -> withRings(nowEpochMs) { it.copy(lastSessionRecencyDays = value?.toIntOrNull()) }

    SetupStepId.RINGS_ACTIVITY -> withRings(nowEpochMs) { answers ->
        answers.copy(
            activityType = InitialRecoveryActivityType.entries.firstOrNull { item -> item.name == value },
            activityTypeState = if (value != null) InitialRecoveryResponseState.DECLARED
            else InitialRecoveryResponseState.UNKNOWN,
        )
    }

    SetupStepId.RINGS_INTENSITY -> withRings(nowEpochMs) { answers ->
        answers.copy(intensityLevel = InitialRecoveryIntensity.entries.firstOrNull { item -> item.name == value })
    }

    // «Sí»/«No» son una declaración explícita; «No lo sé» deja el estado en
    // UNKNOWN sin inventar sesiones ni exposición.
    SetupStepId.RINGS_AXIAL -> withRings(nowEpochMs) { answers ->
        val state = when (value) {
            "yes", "no" -> InitialRecoveryResponseState.DECLARED
            "unknown" -> InitialRecoveryResponseState.UNKNOWN
            else -> null
        }
        if (state == null) answers else answers.copy(axialExposure = answers.axialExposure.copy(state = state))
    }

    SetupStepId.RINGS_MUSCLE_FEELING -> withRings(nowEpochMs) { it.copy(muscleFeeling = feelingValue(value)) }

    SetupStepId.RINGS_ENERGY_FEELING -> withRings(nowEpochMs) { it.copy(energy = feelingValue(value)) }

    SetupStepId.RINGS_STRUCTURE_FEELING -> withRings(nowEpochMs) { it.copy(structureFeeling = feelingValue(value)) }

    SetupStepId.RINGS_START -> withRings(nowEpochMs) { it.copy(startAction = value) }

    // Pasos cuyo tipado escribe la propia UI con `updateStep` (prioridades,
    // split, plan, autorregulación, calentamientos, macros manuales, filas de
    // pesajes, inventario): aquí solo vive la selección canónica ya guardada.
    else -> this
}

/** «No lo sé» → null real (sin fabricar un nivel 0); rango 1..5 en otro caso. */
private fun feelingValue(value: String?): Int? = when {
    value == null -> null
    value == "unknown" -> null
    else -> value.toIntOrNull()?.takeIf { it in 1..5 }
}

// ─── Proyección numérica ─────────────────────────────────────────────────────

private fun SetupWizardDraft.projectNumber(
    step: SetupStepId,
    value: Double?,
    nowEpochMs: Long,
): SetupWizardDraft = when (step) {
    SetupStepId.AGE -> copy(ageYears = value?.takeIf { it.isFinite() }?.toInt())

    SetupStepId.HEIGHT -> copy(heightCm = value?.takeIf { it.isFinite() })

    SetupStepId.WEIGHT -> copy(
        weightKg = value?.takeIf { it.isFinite() },
        // Declaración real: la fecha solo cambia con un peso distinto; nunca
        // se regenera al rehidratar ni al reafirmar el mismo valor.
        currentWeightMeasuredAtEpochMs = if (value == null) null else {
            val previous = weightKg
            if (previous == null || value != previous || currentWeightMeasuredAtEpochMs == null) {
                nowEpochMs
            } else {
                currentWeightMeasuredAtEpochMs
            }
        },
    )

    SetupStepId.SESSION_TIME -> copy(minutesPerSession = value?.takeIf { it.isFinite() }?.toInt())

    SetupStepId.BODY_FAT -> copy(
        bodyFatPercent = value?.takeIf { it.isFinite() },
        bodyFatCapturedAtEpochMs = if (value == null) null else {
            val previous = bodyFatPercent
            if (previous == null || value != previous || bodyFatCapturedAtEpochMs == null) {
                nowEpochMs
            } else {
                bodyFatCapturedAtEpochMs
            }
        },
    )

    SetupStepId.NUTRITION_HISTORY_CONTEXT -> copy(previousMaximumWeightKg = value?.takeIf { it.isFinite() })

    SetupStepId.RINGS_SESSIONS -> withRings(nowEpochMs) { it.copy(sessionsLastSevenDays = value?.toInt()) }

    SetupStepId.RINGS_RECENCY -> withRings(nowEpochMs) { it.copy(lastSessionRecencyDays = value?.toInt()) }

    // NUTRITION_TARGET / macros manuales / marcas / bolsa de prioridades:
    // su tipado crudo lo escribe la UI con `updateStep`; aquí solo se conserva
    // el texto en `inputTexts` (hecho por el llamante).
    else -> this
}

// ─── Helpers tipados ─────────────────────────────────────────────────────────

private fun SetupWizardDraft.withNutrition(
    change: (NutritionWizardDraft) -> NutritionWizardDraft,
): SetupWizardDraft {
    val base = nutritionDraft ?: NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId)
    return copy(nutritionDraft = change(base))
}

/**
 * Escritura sobre Rings; crea el contenedor si hace falta y estampa
 * `capturedAtMs` solo en la primera declaración (nunca lo regenera).
 */
private fun SetupWizardDraft.withRings(
    nowEpochMs: Long,
    change: (SetupRingsAnswers) -> SetupRingsAnswers,
): SetupWizardDraft {
    val base = ringsAnswers ?: SetupRingsAnswers()
    val changed = change(base)
    return copy(ringsAnswers = if (changed.capturedAtMs == null) changed.copy(capturedAtMs = nowEpochMs) else changed)
}

/** Cambio de estilo con recalibración completa cuando las cuatro respuestas existen. */
private fun SetupWizardDraft.withVolumeStyle(style: TrainingStyle): SetupWizardDraft {
    val answers = volumeAnswers.copy(style = style)
    val profile = rebuildVolumeProfile(answers)
    return copy(
        volumeAnswers = answers,
        volumeCalibrationProfile = profile,
        volumeRecommendations = profile?.recommendations.orEmpty(),
        athleteProfileScore = profile?.athleteProfileScore,
    )
}

/** 1..4 → técnica/consistencia/fuerza/movilidad; respuesta declarada por el usuario. */
private fun SetupWizardDraft.withVolumeResponse(slot: Int, value: String?): SetupWizardDraft {
    val points = value?.toIntOrNull()?.takeIf { it in 1..3 }
    val current = volumeAnswers
    val answers = when (slot) {
        1 -> current.copy(technique = points)
        2 -> current.copy(consistency = points)
        3 -> current.copy(strength = points)
        else -> current.copy(mobility = points)
    }.copy(responseState = CalibrationResponseState.DECLARED)
    val profile = rebuildVolumeProfile(answers)
    return copy(
        volumeAnswers = answers,
        volumeCalibrationProfile = profile,
        volumeRecommendations = profile?.recommendations.orEmpty(),
        athleteProfileScore = profile?.athleteProfileScore,
    )
}

/** Perfil de calibración solo cuando las cuatro respuestas están completas. */
private fun rebuildVolumeProfile(answers: SetupVolumeAnswers): VolumeCalibrationProfile? {
    val style = answers.style ?: return null
    val technique = answers.technique ?: return null
    val consistency = answers.consistency ?: return null
    val strength = answers.strength ?: return null
    val mobility = answers.mobility ?: return null
    val output = VolumeCalibrationEngine.calculate(style, technique, consistency, strength, mobility)
    return VolumeCalibrationProfile(
        style,
        output.score,
        VolumeCalibrationResponses(
            technique,
            consistency,
            strength,
            mobility,
            answers.responseState.takeIf { it != CalibrationResponseState.UNKNOWN }
                ?: CalibrationResponseState.DECLARED,
        ),
        output.recommendations,
        System.currentTimeMillis(),
        VolumeCalibrationEngine.REVISION,
    )
}

/** Equipo legacy (T_HOME_EQUIPMENT) → valor estable del catálogo. */
private fun legacyEquipmentValue(value: String): SetupEquipment? = when (value) {
    "bodyweight" -> SetupEquipment.BODYWEIGHT
    "bands" -> SetupEquipment.BANDS
    "dumbbells" -> SetupEquipment.DUMBBELLS
    "pull_up" -> SetupEquipment.PULL_UP
    "support" -> SetupEquipment.SUPPORT
    "barbell" -> SetupEquipment.BARBELL
    "machine" -> SetupEquipment.MACHINE
    "none" -> SetupEquipment.NONE
    else -> null
}

// ─── Proyección tipada → selección (borradores rehidratados) ─────────────────

private fun SetupWizardDraft.typedSelections(step: SetupStepId): Set<String> = when (step) {
    SetupStepId.GOAL -> goal?.name?.lowercase().let { setOfNotNull(it) }

    SetupStepId.EXPERIENCE -> when (experience) {
        SetupExperience.NEW -> setOf("new")
        SetupExperience.RETURNING -> setOf("returning")
        SetupExperience.INTERMEDIATE -> setOf("intermediate")
        SetupExperience.ADVANCED -> setOf("advanced")
        null -> emptySet()
    }

    SetupStepId.ROUTE -> when {
        programRoute == SetupProgramRoute.PROTOCOL -> setOf("protocol")
        programRoute == SetupProgramRoute.CUSTOMIZABLE && isAnsweredForSelection(step) -> setOf("recommended")
        else -> emptySet()
    }

    SetupStepId.EQUATION_SEX -> nutritionDraft?.equationSex?.name?.lowercase().let { setOfNotNull(it) }

    SetupStepId.BODY_FAT -> bodyFatSource?.name.let { setOfNotNull(it) }

    SetupStepId.STYLE -> volumeAnswers.style?.name?.lowercase().let { setOfNotNull(it) }

    SetupStepId.VOLUME_TECHNIQUE -> volumeAnswers.technique?.toString().let { setOfNotNull(it) }

    SetupStepId.VOLUME_CONSISTENCY -> volumeAnswers.consistency?.toString().let { setOfNotNull(it) }

    SetupStepId.VOLUME_STRENGTH -> volumeAnswers.strength?.toString().let { setOfNotNull(it) }

    SetupStepId.VOLUME_MOBILITY -> volumeAnswers.mobility?.toString().let { setOfNotNull(it) }

    SetupStepId.AVAILABILITY -> when (val availability = trainingOptions.availability) {
        null -> emptySet()
        else -> if (availability.categories.isEmpty()) {
            setOf(AVAILABILITY_BODYWEIGHT)
        } else {
            availability.categories.map { it.name }.toSet()
        }
    }

    SetupStepId.EQUIPMENT -> trainingEnvironment?.let { env ->
        SetupStepDefinitions.of(SetupStepId.EQUIPMENT)?.options
            ?.firstOrNull { it.label == env }
            ?.value ?: env
    }.let { setOfNotNull(it) }

    SetupStepId.HOME_EQUIPMENT -> equipment.map { it.name.lowercase() }.toSet()

    SetupStepId.DAYS -> daysPerWeek?.toString().let { setOfNotNull(it) }

    SetupStepId.WEEKDAYS -> selectedWeekdays.map { it.toString() }.toSet()

    SetupStepId.CARDIO_TYPE -> cardioType?.name.let { setOfNotNull(it) }

    SetupStepId.CARDIO_TIME -> cardioMinutes?.toString().let { setOfNotNull(it) }

    SetupStepId.TRAINING_MAX ->
        if (isAnsweredForSelection(step)) setOf(if (knowsTrainingMarks) "yes" else "no") else emptySet()

    SetupStepId.GENDER -> profileGender?.name?.lowercase().let { setOfNotNull(it) }

    SetupStepId.NUTRITION_START -> nutritionDraft?.configurationMode?.name?.lowercase().let { setOfNotNull(it) }

    SetupStepId.NUTRITION_ELIGIBILITY -> buildSet {
        nutritionDraft?.let { n ->
            if (n.eligibilityUnknown) add("unknown")
            if (n.pregnant) add("pregnancy")
            if (n.lactating) add("lactation")
            if (n.medicalRestriction) add("medical_restriction")
        }
    }

    SetupStepId.NUTRITION_DIRECTION -> nutritionDraft?.direction
        ?.takeIf { it != PlanDirection.PROFESSIONAL }
        ?.name?.lowercase().let { setOfNotNull(it) }

    SetupStepId.NUTRITION_DISTRIBUTION -> nutritionDraft?.weeklyDistribution?.name?.lowercase().let { setOfNotNull(it) }

    SetupStepId.NUTRITION_HISTORY_CONTEXT -> weightTrend.let { setOfNotNull(it) }

    SetupStepId.PLAN -> selectedCatalogId.let { setOfNotNull(it) }

    SetupStepId.SPLIT -> when {
        selectedSplitId == "custom" || customSplitPattern.isNotEmpty() -> setOf("custom")
        selectedSplitId != null -> setOf(selectedSplitId!!)
        isAnsweredForSelection(step) -> setOf("recommended")
        else -> emptySet()
    }

    SetupStepId.PRIORITIES -> trainingOptions.orderPriorities.filterValues { it > 0 }.keys

    SetupStepId.RINGS_RECENT -> when (ringsAnswers?.recentTrainingState) {
        SetupRecentTrainingState.YES -> setOf("yes")
        SetupRecentTrainingState.NO -> setOf("no")
        SetupRecentTrainingState.UNKNOWN -> setOf("unknown")
        else -> emptySet()
    }

    SetupStepId.RINGS_SESSIONS -> ringsAnswers?.sessionsLastSevenDays?.toString().let { setOfNotNull(it) }

    SetupStepId.RINGS_RECENCY -> ringsAnswers?.lastSessionRecencyDays?.toString().let { setOfNotNull(it) }

    SetupStepId.RINGS_ACTIVITY -> ringsAnswers?.activityType?.name.let { setOfNotNull(it) }

    SetupStepId.RINGS_INTENSITY -> ringsAnswers?.intensityLevel?.name.let { setOfNotNull(it) }

    SetupStepId.RINGS_MUSCLE_FEELING -> {
        val typed = ringsAnswers?.muscleFeeling
        if (typed != null) setOf(typed.toString())
        else if (isAnsweredForSelection(step)) setOf("unknown") else emptySet()
    }

    SetupStepId.RINGS_ENERGY_FEELING -> {
        val typed = ringsAnswers?.energy
        if (typed != null) setOf(typed.toString())
        else if (isAnsweredForSelection(step)) setOf("unknown") else emptySet()
    }

    SetupStepId.RINGS_STRUCTURE_FEELING -> {
        val typed = ringsAnswers?.structureFeeling
        if (typed != null) setOf(typed.toString())
        else if (isAnsweredForSelection(step)) setOf("unknown") else emptySet()
    }

    SetupStepId.RINGS_DISCOMFORT -> when (ringsAnswers?.discomfortState) {
        SetupDiscomfortState.NONE -> setOf("none")
        SetupDiscomfortState.OMITTED -> setOf("omit")
        SetupDiscomfortState.DECLARED -> ringsAnswers?.discomfortIds.orEmpty().toSet()
        else -> emptySet()
    }

    // Pasos de texto/número, resultado e hito: no hay «selección» que proyectar.
    else -> emptySet()
}

/**
 * ¿El paso tiene respuesta visible hoy? Usa las mismas fuentes que la
 * validación (registro, selección cruda o espejo legacy), nunca el default
 * de un campo tipado.
 */
internal const val AVAILABILITY_BODYWEIGHT = "bodyweight_only"

private fun SetupWizardDraft.isAnsweredForSelection(step: SetupStepId): Boolean =
    step in stepProgress.answers ||
        !stepSelections[step].isNullOrEmpty() ||
        (SetupStepGraph.questionForStep(step)?.let { question ->
            wizChat.acceptedAnswers.any { it.questionId == question }
        } == true)
