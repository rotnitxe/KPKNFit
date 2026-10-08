package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoveryResponseState
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeCalibrationProfile
import com.example.kpkn.data.models.VolumeCalibrationResponses
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.CardioChoice
import com.example.kpkn.domain.onboarding.CardioChoices
import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MarksContext
import com.example.kpkn.domain.onboarding.MuscleSuggestions
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.onboarding.SetupEquationSexValues
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatValidation
import com.example.kpkn.domain.training.VolumeCalibrationEngine
import com.example.kpkn.domain.training.split.SplitCatalogRules
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import java.util.Locale

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
    return storedSelection(step, normalized).projectChoice(step, normalized.firstOrNull(), nowEpochMs)
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
    return storedSelection(step, normalized).projectChoices(step, normalized, nowEpochMs)
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
        // El tiempo por sesión es un dial: solo números (`withStepNumber`), nunca texto libre.
        SetupStepId.SESSION_TIME -> this
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
        SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT -> {
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
 * Escribe también el texto canónico en `inputTexts` (clave `step.name`; en la
 * grasa corporal, con un decimal como máximo);
 * si el retiro llega con texto inválido en el campo, se conserva ese texto
 * para que la validación lo señale en lugar de silenciar el error.
 */
fun SetupWizardDraft.withStepNumber(
    step: SetupStepId,
    value: Double?,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft {
    // El dial de minutos no deja texto crudo: el valor redondeado es la única representación.
    if (step == SetupStepId.SESSION_TIME) {
        return copy(inputTexts = inputTexts - step.name).projectNumber(step, value, nowEpochMs)
    }
    val current = inputTexts[step.name]
    val keyed = when {
        // Mismo número, otro formato («70,» / «70.» / «70.0» → 70): el crudo
        // que el usuario está escribiendo se conserva intacto.
        value != null && current != null && parseLocalizedNumber(current) == value -> this
        // Rueda/regla/figura a un valor realmente distinto: el crudo obsoleto se
        // reemplaza por el canónico del nuevo número.
        value != null -> copy(inputTexts = inputTexts + (step.name to canonicalNumberText(step, value)))
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

/**
 * Pasos de Entreno v2 cuya selección se LEE de los datos del borrador (lugares, material, perfil, día fuerte, días,
 * músculos y marcas): su reductor retira cualquier selección guardada aparte, así que un valor que no se reconoce no deja
 * rastro ni se confunde con una elección.
 */
private val DERIVED_SELECTION_STEPS: Set<SetupStepId> = setOf(
    SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY, SetupStepId.GOAL, SetupStepId.FRESH_DAY,
    SetupStepId.WEEKDAYS, SetupStepId.PRIORITIES, SetupStepId.TRAINING_MAX,
)

/** El borrador con la selección de [step] guardada, salvo en los pasos cuya selección sale de sus datos. */
private fun SetupWizardDraft.storedSelection(step: SetupStepId, values: Set<String>): SetupWizardDraft =
    if (step in DERIVED_SELECTION_STEPS) this else copy(stepSelections = stepSelections + (step to values.toList()))

/** El valor exclusivo de la definición gana siempre; nunca se mezcla con otros. */
private fun normalizeExclusive(step: SetupStepId, values: Set<String>): Set<String> {
    if (values.isEmpty()) return emptySet()
    val exclusive = SetupStepDefinitions.of(step)?.exclusiveValues.orEmpty()
    if (exclusive.isEmpty()) return values
    val chosen = exclusive.firstOrNull { it in values } ?: return values
    return setOf(chosen)
}

/**
 * Texto canónico de un número: entero sin decimales, el resto con punto. La grasa
 * corporal lleva un decimal como máximo: la figura emite un `Double` crudo
 * («33.184518814086914») y el campo de medición exacta lo mostraría tal cual. Solo se
 * acorta el texto; el valor tipado ([projectNumber]) conserva toda su precisión.
 */
private fun canonicalNumberText(step: SetupStepId, value: Double): String = when {
    step == SetupStepId.BODY_FAT && value.isFinite() ->
        String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
    value.isFinite() && value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}

// ─── Proyecciones multi ──────────────────────────────────────────────────────

private fun SetupWizardDraft.projectChoices(
    step: SetupStepId,
    values: Set<String>,
    nowEpochMs: Long,
): SetupWizardDraft = when (step) {
    SetupStepId.WEEKDAYS -> withWeekdays(values.mapNotNullTo(linkedSetOf()) { EntrenoStepValues.weekdayOf(it) })

    SetupStepId.EQUIPMENT -> withPlaces(values.mapNotNullTo(linkedSetOf()) { EntrenoStepValues.placeOf(it) })

    // Los valores son los nombres de los símbolos de material; «solo peso corporal» es exclusivo y una selección
    // vacía equivale a él (el motor recibe categorías vacías confirmadas).
    SetupStepId.AVAILABILITY -> withMaterial(values.mapNotNullTo(linkedSetOf()) { EntrenoStepValues.symbolOf(it) })

    // Músculos canónicos del motor de orden; cada uno puntúa 1 en la bolsa.
    SetupStepId.PRIORITIES -> withMuscles(values.mapNotNullTo(linkedSetOf()) { MuscleSymbols.fromCanonical(it) })

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

    else -> projectChoice(step, values.firstOrNull(), nowEpochMs)
}

// ─── Proyección única ────────────────────────────────────────────────────────

/**
 * La figura de referencia del paso de grasa corporal parte de lo que la persona eligió en el género
 * (mujer, mujer trans o estrógenos → figura femenina; hombre, hombre trans o andrógenos → masculina).
 * «Equilibrio» y «No lo sé» no eligen figura: se queda la que había. Es solo una referencia visual y se
 * puede cambiar con los botones ♀/♂ del paso, que no tocan la base de la ecuación. Con la grasa ya
 * declarada la figura es parte de esa respuesta y no se mueve sola.
 */
private fun SetupWizardDraft.withReferenceFigureFor(value: String?): SetupWizardDraft {
    val model = when (value) {
        "female", "trans_female", SetupEquationSexValues.HORMONES_ESTROGEN -> "female"
        "male", "trans_male", SetupEquationSexValues.HORMONES_ANDROGEN -> "male"
        else -> return this
    }
    return if (bodyFatSource != null) this else copy(physiqueModel = model)
}

private fun SetupWizardDraft.projectChoice(
    step: SetupStepId,
    value: String?,
    nowEpochMs: Long,
): SetupWizardDraft = when (step) {
    // La base de la ecuación vive en el borrador de nutrición y jamás toca el género de perfil.
    // Elige la ecuación, no una identidad: los glifos y las respuestas hormonales que apuntan a lo
    // mismo comparten base (estrógenos → femenina, andrógenos → masculina, equilibrio → promedio).
    // «unknown» a solas la deja sin determinar: hay que contestar la consulta hormonal.
    // La figura de referencia del paso de grasa parte de lo que se eligió ([withReferenceFigureFor]).
    SetupStepId.EQUATION_SEX, SetupStepId.NUTRITION_SEX -> withNutrition {
        it.copy(equationSex = when (value) {
            "female", "trans_female", SetupEquationSexValues.HORMONES_ESTROGEN -> EerSex.FEMALE
            "male", "trans_male", SetupEquationSexValues.HORMONES_ANDROGEN -> EerSex.MALE
            SetupEquationSexValues.HORMONES_MIXED -> EerSex.AVERAGE
            else -> null
        })
    }.let { draft -> if (step == SetupStepId.EQUATION_SEX) draft.withReferenceFigureFor(value) else draft }

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

    // El perfil de objetivo es el dato; el resto (objetivo del motor, estilo de calibración, sugerencia de músculos)
    // se deriva de él. Los valores antiguos del paso se leen como el perfil que hoy les corresponde.
    SetupStepId.GOAL -> value?.let(EntrenoStepValues::goalProfileOf)?.let { withGoalProfile(it) } ?: this

    // Un valor suelto es «solo este lugar»; la selección de varios lugares entra por `withStepChoices`.
    SetupStepId.EQUIPMENT -> value?.let(EntrenoStepValues::placeOf)?.let { withPlaces(setOf(it)) } ?: this

    SetupStepId.FRESH_DAY -> withFreshestDay(value?.let(EntrenoStepValues::weekdayOf))

    SetupStepId.VOLUME_TECHNIQUE -> withVolumeResponse(1, value)
    SetupStepId.VOLUME_CONSISTENCY -> withVolumeResponse(2, value)
    SetupStepId.VOLUME_STRENGTH -> withVolumeResponse(3, value)
    SetupStepId.VOLUME_MOBILITY -> withVolumeResponse(4, value)

    // «Lo que haya» no es un tipo: es la respuesta «sin preferencia». Las dos respuestas nunca conviven.
    SetupStepId.CARDIO_TYPE -> if (value == CardioChoice.ANY.name) {
        copy(cardioType = null, cardioNoPreference = true)
    } else {
        copy(cardioType = CardioType.entries.firstOrNull { it.name == value }, cardioNoPreference = false)
    }

    SetupStepId.CARDIO_TIME -> copy(cardioMinutes = value?.toIntOrNull())

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

    // Siempre un múltiplo de 5 dentro del rango del dial (el valor llega ya redondeado a la UI y al motor).
    SetupStepId.SESSION_TIME -> copy(
        minutesPerSession = value?.takeIf { it.isFinite() }?.let(EntrenoStepValues::roundSessionMinutes),
    )

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

/**
 * D6 (A.E2): un reparto de powerlifting solo se ofrece al perfil Powerlifting
 * ([SplitCatalogRules.isOfferedForProfile], la regla de dominio que también filtra la lista de repartos de la semana
 * armada). Si el perfil nuevo [profile] ya no ofrece el reparto elegido, se retira como lo haría la tarjeta
 * «Recomendado» ([withRecommendedSplit]): sin él, el plan propio del perfil nuevo no lo rechazaría por `SPLIT` (el
 * rechazo que solo repara «Quitar el reparto»). El reparto propio («custom») y los repartos que el perfil sí ofrece no
 * se tocan; un id que el catálogo de repartos no conoce tampoco.
 */
private fun SetupWizardDraft.withoutSplitNotOfferedFor(profile: TrainingGoalProfile): SetupWizardDraft {
    val splitId = selectedSplitId ?: return this
    val template = SPLIT_TEMPLATES.firstOrNull { split -> split.id == splitId } ?: return this
    return if (SplitCatalogRules.isOfferedForProfile(template, profile)) this else withRecommendedSplit()
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
            answers.responseState,
        ),
        output.recommendations,
        System.currentTimeMillis(),
        VolumeCalibrationEngine.REVISION,
    )
}

/**
 * T-005 / §13.2 — presencia de UNA clave del subpanel de aparatos, dentro del
 * paso AVAILABILITY (subpanel de EQUIPMENT). Solo presencia: aquí no hay kilos
 * ni cantidades. Sin disponibilidad declarada no se fabrica ninguna.
 */
fun SetupWizardDraft.withApparatusPresence(
    key: String,
    presence: com.example.kpkn.data.models.ApparatusPresence,
    isSupport: Boolean,
): SetupWizardDraft {
    val updated = SetupApparatusPanel.withPresence(trainingOptions.availability, key, presence, isSupport)
        ?: return this
    return copy(trainingOptions = trainingOptions.copy(availability = updated))
}

// ─── Entreno v2: lugares y material ──────────────────────────────────────────

/**
 * Lugares donde se entrena (uno o varios). Es el dato; el resto son derivados que el motor actual sigue leyendo:
 * `trainingEnvironment` («gym» si hay gimnasio, si no «home»), `equipment` (gimnasio → `SetupEquipment.GYM`) y la
 * disponibilidad de material, que se **resiembra** con [EquipmentSymbols.reseed]: se conserva lo ya elegido que
 * sigue ofreciéndose y se añade lo habitual de los lugares NUEVOS (gimnasio trae lo de siempre, un parque su
 * estructura y la casa nada).
 *
 * - Cambiar el material retira la confirmación de AVAILABILITY (un registro viejo no puede confirmar un valor
 *   que no describía) y, si el perfil de objetivo elegido deja de ser compatible, marca GOAL para revisar (nunca
 *   se borra la respuesta en silencio).
 * - Re-escribir los mismos lugares no toca el material ni sus confirmaciones.
 * - Sin lugares (estado intermedio) se conserva lo declarado: no hay material que recalcular.
 * - El lugar por día solo existe con dos o más lugares: se descartan los que ya no están.
 */
fun SetupWizardDraft.withPlaces(requested: Set<TrainingPlace>): SetupWizardDraft {
    val before = trainingPlaces
    val places = TrainingPlace.entries.filterTo(linkedSetOf()) { it in requested }
    val derived = copy(
        trainingPlaces = places,
        trainingEnvironment = when {
            places.isEmpty() -> null
            TrainingPlace.GYM in places -> "gym"
            else -> "home"
        },
        equipment = if (TrainingPlace.GYM in places) setOf(SetupEquipment.GYM) else emptySet(),
        // La selección se lee de `trainingPlaces`: una guardada aparte se desfasaría.
        stepSelections = stepSelections - SetupStepId.EQUIPMENT,
        dayPlaces = if (places.size >= 2) dayPlaces.filterValues { it in places } else emptyMap(),
    )
    if (places == before || places.isEmpty()) return derived
    val current = EquipmentSymbols.selectedFrom(trainingOptions.availability)
    val selection = EquipmentSymbols.reseed(before, places, current)
    // La bicicleta es de la persona, no de un lugar: cambiar de lugares no la borra.
    return derived.withReseededAvailability(
        SetupApparatusPanel.withBikeOf(trainingOptions.availability, EquipmentSymbols.availabilityOf(selection, places)),
    ).withCardioReviewIfUnavailable()
}

/** Alterna [place]; se calcula sobre el borrador último (el VM lo llama dentro de su mutex). */
fun SetupWizardDraft.withPlaceToggled(place: TrainingPlace): SetupWizardDraft =
    withPlaces(if (place in trainingPlaces) trainingPlaces - place else trainingPlaces + place)

/**
 * Material declarado como símbolos de implementos. Una selección vacía (o solo «peso corporal», que es exclusivo)
 * equivale a entrenar con el cuerpo: el motor recibe categorías vacías confirmadas. Los implementos que los lugares
 * elegidos no ofrecen se ignoran; los visibles que no se eligen quedan explícitamente ausentes.
 */
fun SetupWizardDraft.withMaterial(requested: Set<EquipmentSymbolId>): SetupWizardDraft {
    val offered = if (trainingPlaces.isEmpty()) {
        EquipmentSymbolId.entries.toSet()
    } else {
        EquipmentSymbols.symbolsFor(trainingPlaces).toSet()
    }
    val selection = requested.filterTo(linkedSetOf()) { it in offered }.let { chosen ->
        if (EquipmentSymbolId.BODYWEIGHT_ONLY in chosen) setOf(EquipmentSymbolId.BODYWEIGHT_ONLY) else chosen
    }
    // La bicicleta es de la persona, no de un símbolo: rehacer el material no la borra.
    val availability = SetupApparatusPanel.withBikeOf(trainingOptions.availability, EquipmentSymbols.availabilityOf(selection, trainingPlaces))
    return copy(
        trainingOptions = trainingOptions.copy(availability = availability),
        // La selección es la lectura inversa de la disponibilidad: una guardada aparte se desfasaría.
        stepSelections = stepSelections - SetupStepId.AVAILABILITY,
    ).withGoalReviewIfIncompatible().withCardioReviewIfUnavailable()
}

/** Alterna [symbol] respetando la exclusividad de «solo peso corporal» ([EquipmentSymbols.toggle]). */
fun SetupWizardDraft.withMaterialToggled(symbol: EquipmentSymbolId): SetupWizardDraft =
    withMaterial(EquipmentSymbols.toggle(selectedEquipmentSymbols(), symbol))

/**
 * El material cambió por una causa AJENA al paso AVAILABILITY (otros lugares): su confirmación queda obsoleta (respuesta y
 * marca de declaración; el resto del borrador intacto) y, si el perfil de objetivo ya no encaja, GOAL se marca para revisar.
 * Con la misma disponibilidad no se toca nada.
 */
private fun SetupWizardDraft.withReseededAvailability(availability: EquipmentAvailability): SetupWizardDraft {
    if (availability == trainingOptions.availability) return this
    return copy(
        trainingOptions = trainingOptions.copy(availability = availability),
        stepSelections = stepSelections - SetupStepId.AVAILABILITY,
        stepProgress = stepProgress.copy(answers = stepProgress.answers - SetupStepId.AVAILABILITY),
        declaredSteps = declaredSteps - SetupStepId.AVAILABILITY,
    ).withGoalReviewIfIncompatible()
}

/** Con material nuevo un perfil específico puede dejar de encajar: se marca GOAL para revisar (la respuesta se conserva). */
private fun SetupWizardDraft.withGoalReviewIfIncompatible(): SetupWizardDraft =
    if (goalProfile != null && !goalFitsMaterial()) {
        copy(stepProgress = stepProgress.withPendingReview(setOf(SetupStepId.GOAL)))
    } else {
        this
    }

/**
 * Con otro material o con otros lugares, el cardio que se había elegido puede dejar de existir (la cinta sin «Cardio»,
 * la bicicleta sin bicicleta): CARDIO_TYPE se marca para revisar y la respuesta se conserva, nunca se cambia en silencio.
 * Solo cuando el objetivo pide cardio: sin él el paso no está en la ruta y una respuesta vieja no debe dejar el bloque
 * de entreno por revisar.
 */
private fun SetupWizardDraft.withCardioReviewIfUnavailable(): SetupWizardDraft {
    if (!requiresCardio) return this
    val choice = cardioChoice() ?: return this
    return if (CardioChoices.isOffered(choice, trainingPlaces, trainingOptions.availability)) {
        this
    } else {
        copy(stepProgress = stepProgress.withPendingReview(setOf(SetupStepId.CARDIO_TYPE)))
    }
}

/**
 * «Tengo bicicleta»: declara ([has] = true) o retira la bicicleta al aire libre, que la persona tiene o no tiene sea cual
 * sea el lugar. Sin material declarado no se fabrica ninguno (el paso de material va antes). Quitarla deja por revisar
 * un cardio de bicicleta ya elegido (la respuesta se conserva).
 */
fun SetupWizardDraft.withOutdoorBike(has: Boolean): SetupWizardDraft {
    val updated = SetupApparatusPanel.withBike(trainingOptions.availability, has) ?: return this
    return copy(trainingOptions = trainingOptions.copy(availability = updated)).withCardioReviewIfUnavailable()
}

// ─── Entreno v2: objetivo, semana y tiempo ───────────────────────────────────

/**
 * Perfil de objetivo: guarda [profile] y deriva lo que el motor actual lee (`goal`, vía `GoalProfileMapping`), el
 * estilo de calibración (recalibra si las cuatro respuestas ya existen) y, solo si la persona aún no tocó
 * PRIORITIES, las sugerencias de músculos del perfil (precargadas, nunca confirmadas solas). El tipo de atleta se
 * deriva al activar. Un cambio de perfil no borra las preferencias de cardio, marcas ni capacidades ya declaradas.
 *
 * La compatibilidad con el material NO se decide aquí sino en la validación del paso: un perfil incompatible se
 * escribe (el control lo muestra apagado) pero no se puede confirmar.
 */
fun SetupWizardDraft.withGoalProfile(profile: TrainingGoalProfile): SetupWizardDraft {
    val goal = GoalProfileMapping.setupGoalOf(profile)
    return copy(
        goalProfile = profile,
        goal = goal,
        stepSelections = stepSelections - SetupStepId.GOAL,
    )
        .withoutSplitNotOfferedFor(profile)
        .withVolumeStyle(GoalProfileMapping.trainingStyleOf(profile))
        .withSuggestedMusclesFor(profile)
}

/** Día con más energía; mientras la persona no mueva el inicio de semana, la semana empieza ese día. */
fun SetupWizardDraft.withFreshestDay(day: Int?): SetupWizardDraft {
    val fresh = day?.takeIf { it in 1..7 }
    val startFollowsFreshDay = weekStartDay == null || weekStartDay == freshestDay
    return copy(
        freshestDay = fresh,
        weekStartDay = if (startFollowsFreshDay) fresh else weekStartDay,
        stepSelections = stepSelections - SetupStepId.FRESH_DAY,
    )
}

/**
 * Días de entreno (de 1 a 7). El número de días es DERIVADO (`daysPerWeek` = días elegidos; el motor lo lee) y se
 * descartan los lugares por día de los días que ya no están.
 */
fun SetupWizardDraft.withWeekdays(requested: Set<Int>): SetupWizardDraft {
    val days = requested.filter { it in 1..7 }.sorted().toCollection(linkedSetOf())
    return copy(
        selectedWeekdays = days,
        daysPerWeek = days.size.takeIf { it > 0 },
        dayPlaces = dayPlaces.filterKeys { it in days },
        stepSelections = stepSelections - SetupStepId.WEEKDAYS,
    )
}

/** Alterna [day] sobre el borrador último. */
fun SetupWizardDraft.withWeekdayToggled(day: Int): SetupWizardDraft =
    withWeekdays(if (day in selectedWeekdays) selectedWeekdays - day else selectedWeekdays + day)

/** Primer día de la semana (1 = lunes … 7 = domingo); null vuelve a seguir al día con más energía. */
fun SetupWizardDraft.withWeekStart(day: Int?): SetupWizardDraft =
    copy(weekStartDay = day?.takeIf { it in 1..7 } ?: freshestDay)

/**
 * Lugar de un día de entreno. Solo existe con dos o más lugares y para un día elegido; [place] null vuelve al
 * lugar por defecto (el primero de la lista: gimnasio, casa, espacios públicos).
 */
fun SetupWizardDraft.withDayPlace(day: Int, place: TrainingPlace?): SetupWizardDraft {
    if (trainingPlaces.size < 2 || day !in selectedWeekdays) return this
    if (place == null) return copy(dayPlaces = dayPlaces - day)
    if (place !in trainingPlaces) return this
    return copy(dayPlaces = dayPlaces + (day to place))
}

/** Minutos por sesión: el valor más cercano dentro del rango del dial (múltiplo de 5); null retira el dato. */
fun SetupWizardDraft.withSessionMinutes(minutes: Int?): SetupWizardDraft =
    withStepNumber(SetupStepId.SESSION_TIME, minutes?.toDouble())

/**
 * Minutos EXACTOS que pide una reparación del programa («ajustar a 28 min»): el asesor probó justo ese valor, así que no
 * se redondean al reloj de 5 en 5 (un 28 pasaría a 30 y el programa probado ya no sería el que se activa). Solo para
 * reparaciones; el paso SESSION_TIME siempre pasa por [withSessionMinutes]. Se acota al rango del reloj.
 */
internal fun SetupWizardDraft.withExactSessionMinutes(minutes: Int): SetupWizardDraft = copy(
    minutesPerSession = minutes.coerceIn(EntrenoStepValues.SESSION_MINUTES_MIN, EntrenoStepValues.SESSION_MINUTES_MAX),
    inputTexts = inputTexts - SetupStepId.SESSION_TIME.name,
)

// ─── Entreno v2: capacidades, músculos y marcas ──────────────────────────────

/** Nivel de un ejercicio de peso corporal; [level] null retira la respuesta. */
fun SetupWizardDraft.withCapability(skill: CapabilitySkill, level: CapabilityLevel?): SetupWizardDraft =
    copy(capabilities = if (level == null) capabilities - skill else capabilities + (skill to level))

/**
 * Músculos que se quieren mejorar más (hasta [MuscleSymbols.MAX_SELECTION]). Escribe el conjunto de músculos
 * canónicos (`priorityMuscles`) y la bolsa de orden del motor con 1 punto por músculo (`orderPriorities`): solo
 * reordena ejercicios. Vacío es una respuesta válida («omitir»).
 */
fun SetupWizardDraft.withMuscles(requested: Set<MuscleSymbol>): SetupWizardDraft {
    val symbols = MuscleSymbol.entries.filterTo(linkedSetOf()) { it in requested }
        .take(MuscleSymbols.MAX_SELECTION).toSet()
    val canonical = MuscleSymbols.canonicalSet(symbols)
    return copy(
        priorityMuscles = canonical,
        lowerEmphasisMuscles = lowerEmphasisMuscles - canonical,
        trainingOptions = trainingOptions.copy(orderPriorities = MuscleSymbols.orderBagOf(symbols)),
        stepSelections = stepSelections - SetupStepId.PRIORITIES,
    )
}

/** Alterna [symbol] sobre la bolsa última; al llegar al tope no entra otro (la UI avisa «Máximo 5 músculos»). */
fun SetupWizardDraft.withMuscleToggled(symbol: MuscleSymbol): SetupWizardDraft {
    val current = MuscleSymbols.symbolsOf(trainingOptions.orderPriorities)
    val next = when {
        symbol in current -> current - symbol
        current.size >= MuscleSymbols.MAX_SELECTION -> current
        else -> current + symbol
    }
    return withMuscles(next)
}

/** «Omitir»: sin músculos elegidos (también las sugerencias), que es una respuesta válida. */
fun SetupWizardDraft.withMusclesCleared(): SetupWizardDraft = withMuscles(emptySet())

/**
 * Las sugerencias del perfil sustituyen a la bolsa SOLO mientras la persona no ha tocado PRIORITIES: una elección
 * suya (incluido «omitir») nunca se pisa.
 */
private fun SetupWizardDraft.withSuggestedMusclesFor(profile: TrainingGoalProfile): SetupWizardDraft =
    if (SetupStepId.PRIORITIES in declaredSteps) this else withMuscles(MuscleSuggestions.forProfile(profile))

/**
 * Marca de un levantamiento (kg canónicos); [kg] null borra la marca («No la sé»). Un valor fuera de rango no
 * cambia nada. Deriva `knowsTrainingMarks` (hay alguna marca) y `powerliftingProfile` (sentadilla, banca y peso muerto;
 * las demás marcas solo viajan en `liftMarks`).
 */
fun SetupWizardDraft.withLiftMark(mark: LiftMark, kg: Double?): SetupWizardDraft {
    if (kg != null && (!kg.isFinite() || kg !in LIFT_MARK_RANGE_KG)) return this
    val marks = if (kg == null) liftMarks - mark else liftMarks + (mark to kg)
    val bigThree = MarksContext.BIG_THREE.any { it in marks }
    return copy(
        liftMarks = marks,
        knowsTrainingMarks = marks.isNotEmpty(),
        powerliftingProfile = if (bigThree) {
            (powerliftingProfile ?: PowerliftingProfile()).copy(
                squat1RM = marks[LiftMark.SQUAT],
                bench1RM = marks[LiftMark.BENCH],
                deadlift1RM = marks[LiftMark.DEADLIFT],
            )
        } else {
            null
        },
        stepSelections = stepSelections - SetupStepId.TRAINING_MAX,
    )
}

/** Unidad en que se muestran las marcas (`kg` o `lb`); el valor canónico sigue en kg. */
fun SetupWizardDraft.withMarksUnit(unit: String): SetupWizardDraft =
    if (unit == "kg" || unit == "lb") copy(marksUnit = unit) else this

/**
 * La técnica que se DERIVA para quien empieza («1 · Aprendiendo»): no es una respuesta de la persona, así que no
 * toca la procedencia de las otras tres respuestas de calibración. [points] null la retira (la técnica se vuelve a
 * preguntar).
 */
internal fun SetupWizardDraft.withDerivedTechnique(points: Int?): SetupWizardDraft {
    val answers = volumeAnswers.copy(technique = points)
    val profile = rebuildVolumeProfile(answers)
    return copy(
        volumeAnswers = answers,
        volumeCalibrationProfile = profile,
        volumeRecommendations = profile?.recommendations.orEmpty(),
        athleteProfileScore = profile?.athleteProfileScore,
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
    SetupStepId.GOAL -> goalProfile?.let { setOf(EntrenoStepValues.goalValue(it)) }.orEmpty()

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

    // Sin selección guardada, la base tipada se ve como la opción que la produce: «female» y «male» como
    // siempre; el promedio solo nace del equilibrio hormonal. Nunca se inventa un valor del catálogo.
    SetupStepId.EQUATION_SEX -> when (nutritionDraft?.equationSex) {
        EerSex.FEMALE -> setOf("female")
        EerSex.MALE -> setOf("male")
        EerSex.AVERAGE -> setOf(SetupEquationSexValues.HORMONES_MIXED)
        null -> emptySet()
    }

    SetupStepId.BODY_FAT -> bodyFatSource?.name.let { setOfNotNull(it) }

    SetupStepId.VOLUME_TECHNIQUE -> volumeAnswers.technique?.toString().let { setOfNotNull(it) }

    SetupStepId.VOLUME_CONSISTENCY -> volumeAnswers.consistency?.toString().let { setOfNotNull(it) }

    SetupStepId.VOLUME_STRENGTH -> volumeAnswers.strength?.toString().let { setOfNotNull(it) }

    SetupStepId.VOLUME_MOBILITY -> volumeAnswers.mobility?.toString().let { setOfNotNull(it) }

    // La selección de material es la lectura inversa de la disponibilidad declarada (símbolos); sin nada declarado, vacía.
    SetupStepId.AVAILABILITY -> selectedEquipmentSymbols().mapTo(linkedSetOf()) { it.name }

    SetupStepId.EQUIPMENT -> TrainingPlace.entries.filter { it in trainingPlaces }
        .mapTo(linkedSetOf()) { EntrenoStepValues.placeValue(it) }

    SetupStepId.HOME_EQUIPMENT -> equipment.map { it.name.lowercase() }.toSet()

    SetupStepId.FRESH_DAY -> freshestDay?.toString().let { setOfNotNull(it) }

    SetupStepId.WEEKDAYS -> selectedWeekdays.sorted().mapTo(linkedSetOf()) { it.toString() }

    SetupStepId.CAPABILITIES -> capabilities.keys.mapTo(linkedSetOf()) { it.name }

    SetupStepId.CARDIO_TYPE -> (cardioType?.name ?: CardioChoice.ANY.name.takeIf { cardioNoPreference }).let { setOfNotNull(it) }

    SetupStepId.CARDIO_TIME -> cardioMinutes?.toString().let { setOfNotNull(it) }

    SetupStepId.TRAINING_MAX -> liftMarks.keys.mapTo(linkedSetOf()) { it.name }

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
private fun SetupWizardDraft.isAnsweredForSelection(step: SetupStepId): Boolean =
    step in stepProgress.answers ||
        !stepSelections[step].isNullOrEmpty() ||
        (SetupStepGraph.questionForStep(step)?.let { question ->
            wizChat.acceptedAnswers.any { it.questionId == question }
        } == true)
