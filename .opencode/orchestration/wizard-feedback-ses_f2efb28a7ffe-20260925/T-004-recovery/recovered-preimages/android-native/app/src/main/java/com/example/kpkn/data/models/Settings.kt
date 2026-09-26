package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val hasSeenWelcome: Boolean = false,
    val hasSeenHomeTour: Boolean = false,
    /** Ensayo del overlay de etiquetas de workout: solo la primera vez real. */
    val hasSeenWorkoutTagEducation: Boolean = false,

    /** Onboarding de bienvenida (primera vez): fases y finalización. */
    val onboardingCompleted: Boolean = false,
    val onboardingNameDone: Boolean = false,
    val onboardingProgramDone: Boolean = false,
    val onboardingNutritionDone: Boolean = false,
    /** Explicit onboarding choice; independent from AUGE's nutrition algorithm flag. */
    val nutritionTrackingChoice: NutritionTrackingChoice = NutritionTrackingChoice.NOT_DECIDED,
    /**
     * Modo durable de «solo registro», elegido explícitamente en el editor de
     * plan nutricional. Es distinto de [NutritionTrackingChoice.SKIPPED], que
     * oculta y silencia Nutrición por completo: con este modo Nutrición sigue
     * visible y el registro de alimentos disponible, pero NO existen metas que
     * mostrar, medir ni alertar. No se deduce de «no hay plan activo»: es una
     * elección persistida y reversible, y nunca borra los planes anteriores.
     */
    val nutritionTrackingOnly: Boolean = false,

    val username: String = "Usuario",
    val profilePicture: String? = null,
    val age: Int? = null,
    val athleteType: AthleteType = AthleteType.ENTHUSIAST,

    val weightUnit: WeightUnit = WeightUnit.KG,
    val intensityMetric: IntensityMetric = IntensityMetric.RIR,
    val barbellWeight: Double = 20.0,
    val availablePlates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    /**
     * Inventario principal del gimnasio con cantidades finitas. Un único
     * inventario (sin sistema multi-gimnasio) en unidades canónicas kg; la
     * conversión a lb es solo de visualización.
     *
     * Null = inventario derivado de [barbellWeight] + [availablePlates] con
     * cantidades ilimitadas, para no romper backups/JSON antiguos.
     */
    val equipmentInventory: EquipmentInventory? = null,
    val restTimerDefaultSeconds: Int = 90,
    val restTimerAutoStart: Boolean = false,
    /** How chatty continuous-voice TTS is during a live session. */
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.COMPLETE,
    /** Mic VAD profile: GYM = more tolerant to noise; QUIET = snappier cutoffs. */
    val voiceNoiseProfile: VoiceNoiseProfile = VoiceNoiseProfile.GYM,
    /** TTS speech rate multiplier (0.9–1.1 typical). */
    val ttsSpeechRate: Float = 1.0f,
    /** Tutorial version for the current hybrid voice explanation. */
    val voiceTutorialVersionSeen: Int = 0,
    /** Modo de captura de voz: auriculares (manos libres) vs mic del teléfono (música intacta). */
    val voiceCaptureMode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
    /** Falso hasta que el usuario elige modo por primera vez (diálogo obligatorio). */
    val hasChosenVoiceCaptureMode: Boolean = false,
    /** Intención de pre-activar voz al entrar a la próxima sesión (tarjeta de hoy). */
    val voiceArmForNextSession: Boolean = false,
    /** Cue por serie con la carga sugerida + comando "sugerencia aplicada" (opt-in). */
    val voiceAutoSuggestLoads: Boolean = false,
    /** Experimento: en Modo Música usar VOICE_COMMUNICATION (con cancelador de eco)
     *  en el mic del teléfono. Puede alterar el modo de audio; validar en físico. */
    val voiceMusicAec: Boolean = false,
    val locationPermissionRequestedOnce: Boolean = false,
    /** Frases personalizadas del usuario → intensidad (se inyectan a la gramática). */
    val voiceCustomIntensityPhrases: List<CustomIntensityPhrase> = emptyList(),

    /** User nicknames → exerciseId for voice matching. */
    val voiceExerciseAliases: Map<String, String> = emptyMap(),
    /** Display nicknames keyed by catalogDefinitionId / exerciseDbId / canonical id. */
    val exerciseNicknames: Map<String, String> = emptyMap(),
    val showPRsInWorkout: Boolean = true,
    val oneRMFormula: OneRMFormula = OneRMFormula.BRZYCKI,
    val workoutLoggerMode: WorkoutLoggerMode = WorkoutLoggerMode.PRO,
    val sessionCompactView: Boolean = false,

    /** Local parser is the only active provider; legacy values are normalized on load/import. */
    val apiProvider: ApiProvider = ApiProvider.LOCAL,
    val apiKeys: ApiKeys = ApiKeys(),
    val aiTemperature: Double = 0.7,
    val useApiForDescriptions: Boolean = false,

    val appTheme: AppTheme = AppTheme.DEFAULT,
    val themePrimaryColor: String = "#6750A4",
    val enableAnimations: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,

    val userVitals: UserVitals = UserVitals(),
    val dailyCalorieGoal: Int? = null,
    val dailyProteinGoal: Int? = null,
    val dailyCarbGoal: Int? = null,
    val dailyFatGoal: Int? = null,
    val dailyFiberGoal: Int? = 25,
    val dailySugarLimit: Int? = 50,
    val dailySodiumLimitMg: Int? = 2300,
    val dailyPotassiumGoalMg: Int? = 3500,
    val dailyHydrationGoalMl: Int? = 2000,
    val nutritionShowOverages: Boolean = true,
    val calorieGoalObjective: CalorieGoalObjective = CalorieGoalObjective.MAINTENANCE,
    /** PAL categórico 1–5 (Harris–Benedict style) usado en el plan nutricional. */
    val nutritionActivityLevel: Int = 3,
    /** omnivore | vegetarian | vegan — ajusta proteína recomendada. */
    val nutritionDietaryPreference: String = "omnivore",
    val creatineTracking: CreatineTrackingState = CreatineTrackingState(),

    val sleepTargetHours: Double = 8.0,
    val smartSleepEnabled: Boolean = false,

    val algorithmSettings: AlgorithmSettings = AlgorithmSettings(),
    val augePredictionBias: PredictionBiasProfile = PredictionBiasProfile(),
    val initialRecoveryEvidence: InitialRecoveryEvidence? = null,
    /** Global volume reference captured by the setup wizard, even without a program. */
    val volumeCalibrationProfile: VolumeCalibrationProfile? = null,

    val reducedMotionMode: Boolean = false,
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,

    val sessionAutoAdvanceFields: Boolean = true,
    val showTimeSaverPrompt: Boolean = true,
    val defaultVolumeSystem: VolumeSystem = VolumeSystem.KPNK,
    val gymName: String? = null,

    val workoutFeatureFlags: WorkoutFeatureFlags = WorkoutFeatureFlags(),
    /** One-shot import of legacy photo silos into `workout_media` (Room v26). */
    val workoutMediaLegacyImportDone: Boolean = false,
    val workoutV2HeaderWidgetsBySession: Map<String, WorkoutHeaderWidgets> = emptyMap(),

    val aiFallbackEnabled: Boolean = true,
    val aiMaxTokens: Int = 512,

    val workoutReminderEnabled: Boolean = false,
    val workoutReminderTime: String = "18:00",
    val mealReminderEnabled: Boolean = false,
    val mealReminderBreakfast: String = "08:00",
    val mealReminderLunch: String = "13:00",
    val mealReminderDinner: String = "20:00",
    val sleepReminderEnabled: Boolean = false,
    val sleepReminderTime: String = "22:00",

    /** Código BCP-47 del idioma seleccionado, o "system" para respetar el locale del SO. */
    val appLanguage: String = "system",
    val programQueueIds: List<String> = emptyList(),
    val archivedProgramIds: List<String> = emptyList(),
    /** IDs de programas con JSON corrupto aislados; el JSON vive en backups, no en Room. */
    val quarantinedProgramIds: List<String> = emptyList(),
    /** Respaldo del JSON crudo de filas corruptas (id → data). */
    val quarantinedProgramBackups: Map<String, String> = emptyMap(),
    /**
     * Relator 2.0 long-term memory (concepts shown, per-exercise counters, openers).
     * JSON of [com.example.kpkn.domain.relator.RelatorLongTermMemory]; null = empty.
     */
    val relatorMemoryJson: String? = null,
) {
    /**
     * Stock efectivo para alcanzar cargas: el inventario explícito manda,
     * incluidas sus ausencias. Si no hay inventario, se deriva del
     * legacy [barbellWeight] + [availablePlates] con cantidades ilimitadas
     * (compatibilidad con backups antiguos).
     */
    fun resolvedEquipmentInventory(): EquipmentInventory {
        val configured = equipmentInventory
            ?: return EquipmentInventory(
                barbellWeightKg = barbellWeight.takeIf { it.isFinite() && it > 0.0 },
                plates = availablePlates.mapNotNull { weightKg ->
                    weightKg.takeIf { it.isFinite() && it > 0.0 }
                        ?.let { PlateStock(weightKg = it, countPerSide = null) }
                },
            )
        return configured.copy(barbellWeightKg = configured.barbellWeightKg?.takeIf { it.isFinite() && it > 0.0 })
    }
}

enum class CalorieGoalObjective { DEFICIT, MAINTENANCE, SURPLUS }

@Serializable
enum class NutritionTrackingChoice { NOT_DECIDED, ENABLED, SKIPPED }
enum class WeightUnit { KG, LBS }
enum class IntensityMetric { RPE, RIR }
enum class OneRMFormula { BRZYCKI, EPLEY, LANDER }
enum class WorkoutLoggerMode { PRO, SIMPLE }
enum class VoiceVerbosity { COMPLETE, ESSENTIAL, SILENT }
enum class VoiceNoiseProfile { GYM, QUIET }
enum class VoiceCaptureMode { HANDS_FREE, MUSIC }
enum class ApiProvider {
    LOCAL,
    GEMINI,
    GPT,
    @Deprecated("Legacy persisted value; normalize to LOCAL")
    DEEPSEEK,
}
enum class AppTheme { DEFAULT, DARK, DEEP_BLACK, VOLT, LIGHT }
enum class HapticIntensity { LIGHT, MEDIUM, STRONG }
enum class AthleteType {
    ENTHUSIAST, POWERLIFTER, BODYBUILDER, POWERBUILDER,
    ZERCHER_LIFTER, HYBRID, WEIGHTLIFTER, CALISTHENICS
}

@Serializable
data class ApiKeys(
    val gemini: String? = null,
    val gpt: String? = null,
    @Deprecated("Legacy persisted credential; cleared during Android settings migration")
    val deepseek: String? = null,
)

/** Frase verbal del usuario mapeada a intensidad (kind: RPE|RIR|PERCENT_RM|FALLO). */
@Serializable
data class CustomIntensityPhrase(
    val phrase: String,
    val kind: String,
    val value: Double? = null,
)

@Serializable
data class UserVitals(
    val age: Int? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val gender: Gender? = null,
    val bodyFatPercentage: Double? = null,
    val muscleMassPercentage: Double? = null,
    val targetWeight: Double? = null,
    /** Perfil hormonal para Mifflin/Harris; independiente de Gender. */
    val metabolicProfile: MetabolicProfile? = null,
    /** Usado por límites de cafeína y otros motores de salud. */
    val pregnancyLactation: PregnancyLactation = PregnancyLactation.NONE,
)

@Serializable
enum class PregnancyLactation { NONE, PREGNANT, LACTATING }

@Serializable
enum class CreatineProtocol { NONE, LOADING, GRADUAL }

@Serializable
data class CreatineTrackingState(
    val protocol: CreatineProtocol = CreatineProtocol.NONE,
    val protocolStartDate: String? = null,
    val onboardingSeen: Boolean = false,
)

@Serializable
enum class Gender { MALE, FEMALE, OTHER }

/**
 * MetabolicProfile — perfil hormonal interno usado exclusivamente para el cálculo de TMB.
 * Desacoplado de la identidad de género (Gender). El usuario lo elige respondiendo
 * "¿Qué hormonas predominan más en tu cuerpo hoy?" sin jerga médica.
 *
 * TESTOSTERONE → constante masculina en Mifflin/Harris (+5)
 * ESTROGEN     → constante femenina en Mifflin/Harris (-161)
 * MIXED        → promedio de ambas (-78), para perfiles en transición o no binarios
 */
enum class MetabolicProfile { TESTOSTERONE, ESTROGEN, MIXED }

@Serializable
data class PredictionBiasProfile(
    val cnsBias: Double = 0.0,
    val muscularBias: Double = 0.0,
    val spinalBias: Double = 0.0,
    val sampleCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
    /** Legacy profiles are treated as muscular-bias v1 and reset selectively. */
    val muscularBiasVersion: Int = 1,
)

@Serializable
data class AlgorithmSettings(
    val oneRMDecayRate: Double = 0.03,
    val failureFatigueFactor: Double = 1.5,
    val legVolumeMultiplier: Double = 1.0,
    val torsoVolumeMultiplier: Double = 1.0,
    val synergistFactor: Double = 0.25,
    val augeEnableNutritionTracking: Boolean = false,
    val augeEnableSleepTracking: Boolean = false,
    val augeRecoverySensitivity: Double = 1.0,
    val augeFatigueSensitivity: Double = 1.0,
    val augeReadinessThreshold: Int = 60,
    val augeAutoDeload: Boolean = false,
    val augeShowAlertsInSession: Boolean = true,
)

/**
 * Disco del inventario principal. Cantidad total por lado (simétrica: dos
 * piezas por unidad de [countPerSide]). Null = ilimitado, para conservar la
 * compatibilidad con `Settings.availablePlates` de backups antiguos.
 */
@Serializable
data class PlateStock(
    val weightKg: Double,
    val countPerSide: Int? = null,
)

/**
 * Par de mancuernas: [weightPerUnitKg] es la carga por unidad (una mancuerna);
 * [pairAvailable] indica si existe pareja completa (dos unidades).
 */
@Serializable
data class DumbbellPairStock(
    val weightPerUnitKg: Double,
    val pairAvailable: Boolean = true,
) {
    /** Carga total del par (dos unidades). */
    val pairTotalKg: Double get() = weightPerUnitKg * 2.0
}

/** Kettlebell del inventario principal. */
@Serializable
data class KettlebellStock(val weightKg: Double)

/**
 * Rango de cargas de máquina: la carga real avanza en pasos de [incrementKg]
 * desde [baseLoadKg] (carro/pin/stack mínimo), dentro de
 * [minLoadKg]..[maxLoadKg]. Nunca asumir incrementos universales de 0,5 kg.
 *
 * [equipmentKind] es el tipo explícito declarado por el usuario (`machine`,
 * `cable`, `smith_machine`); null = rango legacy sin tipo. El nombre NUNCA
 * infiere el tipo y una máquina nueva no se declara sin tope superior en la
 * UI (el null sólo sobrevive a lecturas antiguas).
 */
@Serializable
data class MachineLoadRange(
    val name: String = "",
    val minLoadKg: Double = 0.0,
    val maxLoadKg: Double? = null,
    val incrementKg: Double = 2.5,
    val baseLoadKg: Double = 0.0,
    val equipmentKind: String? = null,
    /**
     * Configuración real elegida del catálogo para una máquina genérica (id
     * v2 de configuración, p. ej. `leg_press__machine`); null = sin
     * configuración (rango legado o estación multi cable/Smith). El nombre de
     * la fila jamás acredita la máquina y un null nunca libera «todas las
     * máquinas»: sólo la configuración explícita empareja ejercicio y estación.
     */
    val configurationId: String? = null,
) {
    /**
     * Ajusta [targetKg] al paso más cercano real de la máquina (nunca inventa
     * cargas intermedias). [MachineLoadResult.isExact] es false cuando el paso
     * más cercano no coincide con el objetivo.
     */
    fun snapLoad(targetKg: Double): MachineLoadResult {
        val upper = maxLoadKg
        fun clamp(value: Double): Double = value.coerceAtLeast(minLoadKg).let { if (upper != null) minOf(it, upper) else it }
        if (incrementKg <= 0.0) {
            val achieved = clamp(targetKg)
            return MachineLoadResult(targetKg, achieved, kotlin.math.abs(achieved - targetKg) < 0.01)
        }
        val firstIndex = if (minLoadKg > baseLoadKg) {
            kotlin.math.ceil((minLoadKg - baseLoadKg) / incrementKg - 0.001).toInt().coerceAtLeast(0)
        } else {
            0
        }
        val targetIndex = kotlin.math.round((targetKg - baseLoadKg) / incrementKg).toInt().coerceAtLeast(firstIndex)
        val achieved = clamp(baseLoadKg + targetIndex * incrementKg)
        return MachineLoadResult(targetKg, achieved, kotlin.math.abs(achieved - targetKg) < 0.01)
    }
}

/** Resultado de ajustar una carga al rango real de una máquina. */
data class MachineLoadResult(
    val requestedKg: Double,
    val achievedKg: Double,
    val isExact: Boolean,
)

/**
 * Resultado de resolver una carga de mancuerna por unidad.
 * [achievedPerUnitKg] es null cuando la carga es inalcanzable con el
 * inventario (p. ej. existe el peso pero sin pareja): nunca se sustituye en
 * silencio por otra carga.
 */
data class DumbbellLoadResult(
    val requestedPerUnitKg: Double,
    val achievedPerUnitKg: Double?,
    val pairAvailable: Boolean,
    val isExact: Boolean,
)

/**
 * Inventario principal del gimnasio (un solo inventario, kg canónicos).
 * Resoluciones honestas con cantidades reales; sin sustituciones silenciosas.
 *
 * [supportEquipment] guarda el material auxiliar declarado con ids canónicos
 * del catálogo (`support`, `pull_up_bar`, `band`, `ball`, `cardio`). Default
 * vacío: un JSON legado sin el campo queda vacío, nunca auto-relleno; rack y
 * banco se acreditan como `support`, que es el identificador que valida el
 * catálogo existente.
 */
@Serializable
data class EquipmentInventory(
    val barbellWeightKg: Double? = null,
    val plates: List<PlateStock> = emptyList(),
    val dumbbells: List<DumbbellPairStock> = emptyList(),
    val kettlebells: List<KettlebellStock> = emptyList(),
    val machines: List<MachineLoadRange> = emptyList(),
    val supportEquipment: Set<String> = emptySet(),
) {
    fun resolvedBarbellWeightKg(fallback: Double = 20.0): Double =
        barbellWeightKg?.takeIf { it > 0.0 } ?: fallback

    /**
     * Resuelve una carga de mancuerna por unidad:
     * 1. Peso exacto con pareja disponible → exacto.
     * 2. Peso exacto pero sin pareja → inalcanzable (sin sustitución silenciosa).
     * 3. Sin peso exacto → el más alto disponible sin superar el objetivo.
     * 4. Sin candidatos → inalcanzable.
     */
    fun resolveDumbbell(perUnitTargetKg: Double): DumbbellLoadResult {
        val exact = dumbbells.firstOrNull { kotlin.math.abs(it.weightPerUnitKg - perUnitTargetKg) < 0.001 }
        return when {
            exact != null && exact.pairAvailable -> DumbbellLoadResult(
                requestedPerUnitKg = perUnitTargetKg,
                achievedPerUnitKg = exact.weightPerUnitKg,
                pairAvailable = true,
                isExact = true,
            )
            exact != null -> DumbbellLoadResult(perUnitTargetKg, null, pairAvailable = false, isExact = false)
            else -> {
                val candidate = dumbbells
                    .filter { it.pairAvailable && it.weightPerUnitKg <= perUnitTargetKg + 0.001 }
                    .maxByOrNull { it.weightPerUnitKg }
                if (candidate == null) {
                    DumbbellLoadResult(perUnitTargetKg, null, pairAvailable = false, isExact = false)
                } else {
                    DumbbellLoadResult(perUnitTargetKg, candidate.weightPerUnitKg, pairAvailable = true, isExact = false)
                }
            }
        }
    }
}
