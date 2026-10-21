package com.example.kpkn.domain.auge

import kotlin.math.max

/**
 * AugeParameters — Registro único de los parámetros numéricos del modelo de recuperación de AUGE (WP 2.1, F-61).
 *
 * Cada τ base, cada C por canal, cada τ por defecto, la constante de 95 % y los límites de τ se definen AQUÍ y solo aquí:
 * el motor ([AugeRecoveryEngine]), la evidencia inicial ([InitialRecoveryV2Model]), el aprendiz ([AugeAdaptiveEngine]) y la
 * interferencia ([InterferenceEngine]) los leen. `AugeParametersConsistencyTest` falla si reaparece una copia fuera de este archivo.
 *
 * Lo que ya tenía una sola fuente se queda donde está: [AugeUtils] (conservación, rendimientos decrecientes, pesos de estrés,
 * límites de capacidad, pisos fisiológicos y warps de horas), [AcwrThresholds], [RecoveryBands] y `AugeFatigueEngine.ATHLETE_CAPACITY`.
 */
object AugeParameters {

    /** Versión del registro: súbela cuando cambie CUALQUIER valor; el caché del aprendiz de fase 3 la usará para invalidar los τ aprendidos. */
    const val VERSION: Int = 1

    // ─── τ base por perfil (horas) ───────────────────────────────────────────

    /** τ del perfil rápido (bíceps, tríceps, deltoides, pantorrillas, abdomen…). */
    const val TAU_FAST_H = 24.0

    /** τ del perfil medio (pectorales, dorsales, trapecio, core…); también el de un músculo sin perfil. */
    const val TAU_MEDIUM_H = 48.0

    /** τ del perfil lento (cuádriceps, glúteos). */
    const val TAU_SLOW_H = 72.0

    /** τ del perfil pesado (isquiosurales, erectores espinales). */
    const val TAU_HEAVY_H = 96.0

    private const val PROFILE_FAST = "fast"
    private const val PROFILE_MEDIUM = "medium"
    private const val PROFILE_SLOW = "slow"
    private const val PROFILE_HEAVY = "heavy"

    /** τ base de cada perfil, por su nombre (el que devuelve [profileOf]). */
    val PROFILE_TAU_HOURS: Map<String, Double> = mapOf(
        PROFILE_FAST to TAU_FAST_H,
        PROFILE_MEDIUM to TAU_MEDIUM_H,
        PROFILE_SLOW to TAU_SLOW_H,
        PROFILE_HEAVY to TAU_HEAVY_H,
    )

    /** Perfil de recuperación de cada músculo con perfil, en el orden histórico del motor. */
    private val MUSCLE_PROFILE: Map<String, String> = mapOf(
        "Bíceps" to PROFILE_FAST, "Tríceps" to PROFILE_FAST, "Deltoides" to PROFILE_FAST,
        "Deltoides Anterior" to PROFILE_FAST, "Deltoides Lateral" to PROFILE_FAST, "Deltoides Posterior" to PROFILE_FAST,
        "Pantorrillas" to PROFILE_FAST, "Abdomen" to PROFILE_FAST, "Antebrazo" to PROFILE_FAST,
        "Gemelos" to PROFILE_FAST, "Sóleo" to PROFILE_FAST, "Oblicuos" to PROFILE_FAST, "Costales" to PROFILE_FAST,
        "Pectorales" to PROFILE_MEDIUM, "Dorsales" to PROFILE_MEDIUM, "Hombros" to PROFILE_MEDIUM, "Trapecio" to PROFILE_MEDIUM,
        "Romboide" to PROFILE_MEDIUM, "Redondo Menor" to PROFILE_MEDIUM, "Redondo Mayor" to PROFILE_MEDIUM,
        "Infraespinoso" to PROFILE_MEDIUM, "Supraespinoso" to PROFILE_MEDIUM, "Subescapular" to PROFILE_MEDIUM,
        "Serrato Anterior" to PROFILE_MEDIUM,
        "Cuádriceps" to PROFILE_SLOW, "Glúteos" to PROFILE_SLOW,
        "Glúteo Mayor" to PROFILE_SLOW, "Glúteo Medio" to PROFILE_SLOW, "Glúteo Menor" to PROFILE_SLOW,
        "Aductores" to PROFILE_MEDIUM, "Tensor Fascia Lata" to PROFILE_MEDIUM,
        "Isquiosurales" to PROFILE_HEAVY, "Bíceps Femoral" to PROFILE_HEAVY, "Semitendinoso" to PROFILE_HEAVY,
        "Semimembranoso" to PROFILE_HEAVY, "Erectores Espinales" to PROFILE_HEAVY, "Core" to PROFILE_MEDIUM,
        "Cuello" to PROFILE_MEDIUM, "Psoas" to PROFILE_MEDIUM, "Lumbar" to PROFILE_HEAVY,
    )

    /** Músculos con perfil, en el orden del mapa: tras los 13 [PILLARS], el motor arma con ellos el conjunto completo de baterías por músculo. */
    val PROFILED_MUSCLES: List<String> = MUSCLE_PROFILE.keys.toList()

    /** Índice por nombre normalizado (minúsculas, sin tildes); si dos nombres chocaran, manda el primero, como el recorrido lineal de antes. */
    private val PROFILE_BY_NORMALIZED_NAME: Map<String, String> = buildMap {
        MUSCLE_PROFILE.forEach { (muscle, profile) ->
            val key = normKey(muscle)
            if (key !in this) put(key, profile)
        }
    }

    /** Perfil de [muscle] (`fast`, `medium`, `slow` o `heavy`), o null si no tiene; ignora mayúsculas, tildes y espacios de los extremos. */
    fun profileOf(muscle: String): String? = PROFILE_BY_NORMALIZED_NAME[normKey(muscle)]

    /** τ base en horas de [muscle]: el de su perfil, o el medio si no tiene. Sin límites: el motor los aplica con [TAU_MIN_H] y [TAU_MAX_H]. */
    fun baseTauHours(muscle: String): Double = PROFILE_TAU_HOURS.getValue(profileOf(muscle) ?: PROFILE_MEDIUM)

    // ─── Pilares y guardas ───────────────────────────────────────────────────

    /** Los 13 pilares de las baterías por músculo y del promedio muscular global, en el orden del motor. */
    val PILLARS: List<String> = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps",
        "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas",
        "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    )

    /** τ base de cada pilar; se deriva de [baseTauHours], no se escribe aparte. */
    val PILLAR_TAU_HOURS: Map<String, Double> = PILLARS.associateWith { baseTauHours(it) }

    /** Músculos que guardan a Columna (erectores o lumbar, dorsales y trapecio). */
    val SPINE_GUARD_MUSCLES: List<String> = listOf("Erectores Espinales", "Dorsales", "Trapecio")

    // ─── C por canal ─────────────────────────────────────────────────────────

    /** C del músculo: la batería es `100·exp(−R/C)`, con R el % de la capacidad que sigue sin recuperar. */
    const val C_MUSCLE = 90.0

    /** C de la Energía (misma ley que [C_MUSCLE]). */
    const val C_ENERGY = 75.0

    /** C de la Columna (misma ley que [C_MUSCLE]). */
    const val C_SPINAL = 70.0

    // ─── τ por defecto de los canales ────────────────────────────────────────

    /** τ de la Energía, en horas crudas, cuando el caché no trae uno aprendido. */
    const val DEFAULT_TAU_ENERGY_H = 36.0

    /** τ de la Columna, en horas efectivas del warp espinal, cuando el caché no trae uno aprendido. */
    const val DEFAULT_TAU_SPINAL_H = 52.0

    /** τ del anillo muscular global en un ajuste manual sin τ aprendidos, en horas del warp muscular. */
    const val DEFAULT_TAU_GLOBAL_MUSCLE_H = 48.0

    // ─── Escalas de capacidad por canal (D5) ─────────────────────────────────

    /**
     * Factor que lleva el tanque CNS a la capacidad de Energía. Las tres magnitudes del canal viven en unidades
     * distintas: la salida de una serie (`calculateSetBatteryDrain`, ya en % de drenaje × 10), el acumulado de
     * sesión (`SessionDrainRecipe`, misma unidad que la anterior) y la carga del anillo que consume
     * `100·exp(−R/C)`. El tanque de [AugeFatigueEngine.calculatePersonalizedBatteryTanks] se expresa con la escala
     * de serie y este factor lo alinea con la carga del anillo; no es una recalibración.
     */
    const val ENERGY_CAPACITY_FACTOR = 1.15

    /** Mínimo de capacidad de Energía, en unidades de carga del anillo (evita dividir por tanques diminutos). */
    const val ENERGY_CAPACITY_MIN = 80.0

    /**
     * Capacidad de Energía para un tanque CNS: `max(80; tanque·1,15)`. La usan el ancla manual y el camino hacia
     * delante del canal, para que ambos no puedan divergir.
     */
    fun energyCapacity(cnsTank: Double): Double =
        max(ENERGY_CAPACITY_MIN, cnsTank * ENERGY_CAPACITY_FACTOR)

    /**
     * Factor que lleva el tanque espinal a la capacidad de Columna. El tanque
     * ([AugeFatigueEngine.calculatePersonalizedBatteryTanks]) está en unidades de serie y el acumulado de sesión
     * del canal espinal usa la escala del anillo; el factor es el mismo en el ancla y en el forward.
     */
    const val SPINAL_CAPACITY_FACTOR = 0.02

    /** Mínimo de capacidad de Columna, en unidades de carga del anillo. */
    const val SPINAL_CAPACITY_MIN = 70.0

    /** Capacidad de Columna para un tanque espinal: `max(70; tanque·0,02)`. Ancla y forward comparten esta cuenta. */
    fun spinalCapacity(spinalTank: Double): Double =
        max(SPINAL_CAPACITY_MIN, spinalTank * SPINAL_CAPACITY_FACTOR)

    // ─── Constante de recuperación y límites ─────────────────────────────────

    /** ln 20 ≈ 2,9957: con k = TAU_K_95 / τ el residuo cae al 5 % (95 % recuperado) tras τ horas efectivas. */
    const val TAU_K_95 = 2.9957

    /** τ mínimo de un músculo en el motor, en horas. */
    const val TAU_MIN_H = 18.0

    /** τ máximo de un músculo en el motor, en horas. */
    const val TAU_MAX_H = 144.0

    // ─── Constantes del aprendiz bayesiano (AugeLearner / EKF) ───────────────

    /** Desviación típica del prior de ln τ: 35 % logarítmico (sdθ = 0,35). */
    const val LEARNER_SD_THETA = 0.35

    /** Varianza inicial del prior en ln² τ: P0 = sdθ² = 0,35 · 0,35 = 0,1225. */
    const val LEARNER_P0 = LEARNER_SD_THETA * LEARNER_SD_THETA

    /** Ruido de proceso por día en ln² τ: q = (0,02)² = 0,0004 / día. */
    const val LEARNER_Q_PER_DAY = 0.02 * 0.02

    /** Sensibilidad mínima (|H|mín): 0,1 puntos de batería por unidad de ln τ. */
    const val LEARNER_H_MIN = 0.1

    /** Límite inferior compartido de τ para el aprendiz (horas). */
    const val LEARNER_TAU_MIN_H = TAU_MIN_H

    /** Límite superior compartido de τ para el aprendiz (horas). */
    const val LEARNER_TAU_MAX_H = TAU_MAX_H

    /** Factor de recorte de la innovación sobre la desviación típica prevista: ±2,5 · √S. */
    const val LEARNER_INNOVATION_CLIP_SIGMA = 2.5

    /** Paso relativo de diferencia finita: ±2 % (τ− = 0,98 · e^m, τ+ = 1,02 · e^m). */
    const val LEARNER_FINITE_DIFF_STEP = 0.02

    /** Umbral de saturación de batería: lecturas o predicciones ≥ 99,5 se censuran. */
    const val LEARNER_SATURATION_THRESHOLD = 99.5

    /** Zona muerta en puntos de batería: innovaciones |ν| < 3,0 se censuran (3,0 exacto se admite). */
    const val LEARNER_DEAD_ZONE = 3.0

    /** Límite inferior de la ventana temporal de observación (horas desde la carga relevante). */
    const val LEARNER_WINDOW_MIN_HOURS = 8.0

    /** Límite superior de la ventana temporal de observación (horas desde la carga relevante = 14 días). */
    const val LEARNER_WINDOW_MAX_HOURS = 336.0

    /** Desviación típica del error de observación para sensaciones manuales de batería (puntos). */
    const val LEARNER_SIGMA_MANUAL = 5.0

    /** Desviación típica del error de observación para rendimiento por e1RM (puntos). */
    const val LEARNER_SIGMA_E1RM = 12.0

    /** Desviación típica del error de observación para cambios en RPE (ΔRPE) (puntos). */
    const val LEARNER_SIGMA_DELTA_RPE = 10.0

    /**
     * Devuelve la desviación típica σ según la fuente de la observación (`manual`, `e1rm`, `delta_rpe`).
     */
    fun learnerSigmaForSource(source: String): Double = when (source.lowercase().trim()) {
        "manual", "sensation", "sensacion", "sensación" -> LEARNER_SIGMA_MANUAL
        "e1rm", "performance", "rendimiento" -> LEARNER_SIGMA_E1RM
        "delta_rpe", "rpe", "deltarpe" -> LEARNER_SIGMA_DELTA_RPE
        else -> LEARNER_SIGMA_MANUAL
    }

    private fun normKey(s: String): String = s
        .lowercase().trim()
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")
}
