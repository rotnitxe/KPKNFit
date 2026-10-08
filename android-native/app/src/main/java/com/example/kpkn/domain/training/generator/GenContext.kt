package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.training.CardioPreference
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Contexto de UNA generación: la petición validada y normalizada, el índice del catálogo y los acumuladores que comparten
 * las sesiones de la semana (uso de ejercicios, volumen y notas). Una instancia vive solo durante `generate`.
 */
internal class GenContext(
    val request: RoutineRequest,
    val catalog: GeneratorCatalog,
    val programId: String,
    /** Días de entreno en el orden del ciclo semanal (desde `weekStartDay`). */
    val days: List<Int>,
    val budgets: VolumeBudgets,
) {
    val level: RoutineLevel get() = request.level
    val mode: RoutineMode get() = request.mode
    val seed: Int get() = request.variantSeed
    val targetMinutes: Int get() = request.targetMinutes
    val targetSeconds: Int get() = request.targetMinutes * 60

    /** Ventana de minutos por sesión: [85 %, 110 %] del objetivo y nunca menos de 20. */
    val windowMinutes: IntRange = run {
        val lo = maxOf(20, ceil(request.targetMinutes * 0.85).toInt())
        val hi = floor(request.targetMinutes * 1.10).toInt()
        lo..maxOf(lo, hi)
    }

    /** Lo más largo que puede medir una sesión para ser «lo que se pidió»: el tiempo pedido más la tolerancia, sin pasar de la ventana. */
    val toleratedMaxMinutes: Int =
        (request.targetMinutes + RoutineGenerator.TIME_TOLERANCE_MINUTES).coerceIn(windowMinutes.first, windowMinutes.last)

    /** Notas honestas que se acumulan durante la generación (sin repetidos, en orden). */
    val notes: LinkedHashSet<String> = LinkedHashSet()

    /** Cuántas sesiones de la semana usan cada configuración. */
    val weeklyUse: HashMap<String, Int> = HashMap()

    /** Cuántos ejercicios de la semana cuelgan de cada marca declarada (para repartir las alternativas empatadas entre marcas). */
    val weeklyMarkUse: HashMap<LiftMark, Int> = HashMap()

    /** Patrones que alguna sesión pidió y no pudo cubrir (y en cuántas sesiones ocurrió). */
    val missing: HashMap<RoutinePattern, Int> = HashMap()

    /** Patrones que sí aparecieron. */
    val covered: LinkedHashSet<RoutinePattern> = LinkedHashSet()

    val ledger: VolumeLedger = VolumeLedger(budgets)

    /** Sesiones cuyo tiempo no entró en la ventana de minutos (título y minutos reales). */
    val outside: MutableList<Pair<String, Int>> = ArrayList()

    /** Marcas de decisiones que alimentan las notas (p. ej. `hinge_bodyweight`). */
    val flags: MutableSet<String> = HashSet()

    /** Lo que cambia el modo de disciplina (null en los modos generales). */
    val discipline: DisciplineSpec? = Disciplines.of(request.mode)

    private val groupsMemo = HashMap<RoutinePattern, List<PoolGroup>>()

    /** Grupos de la reserva de [pattern]: los propios de la disciplina (si hay) y, detrás, los generales como recurso. */
    fun groupsFor(pattern: RoutinePattern): List<PoolGroup> = groupsMemo.getOrPut(pattern) {
        discipline?.pools?.get(pattern).orEmpty() + MovementPools.byPattern[pattern].orEmpty()
    }

    /** ¿El modo admite el material de esta configuración? (la calistenia no usa pesas aunque el lugar las tenga). */
    fun tierAllowed(entry: CatalogEntry): Boolean = discipline?.allowedTiers?.let { entry.tier in it } ?: true

    /** Perfil de ranking de material según modo y nivel. */
    val rankProfile: RankProfile = when {
        request.mode == RoutineMode.DISCIPLINE_CALISTHENICS -> RankProfile.CALISTHENICS
        request.mode == RoutineMode.GENERAL_FUNCTIONAL -> RankProfile.FUNCTIONAL
        request.level == RoutineLevel.NOVICE || request.level == RoutineLevel.RETURNING -> RankProfile.LEARNING
        else -> RankProfile.STANDARD
    }

    /** Minutos de cardio de un bloque tras la fuerza: lo declarado o el valor por defecto según el tiempo de sesión. */
    fun cardioBlockMinutes(): Int {
        val preference = request.cardio
        if (preference != null) return preference.minutes.coerceIn(5, 60)
        return when {
            request.targetMinutes >= 60 -> 20
            request.targetMinutes >= 45 -> 15
            else -> 10
        }
    }

    val cardioPreference: CardioPreference? get() = request.cardio
    val cardioIntensityOrNull: CardioIntensity? get() = request.cardio?.intensity
    val preferredCardioType: CardioType? get() = request.cardio?.type
}

internal enum class RankProfile { STANDARD, LEARNING, FUNCTIONAL, CALISTHENICS }
