package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.data.models.SessionPart
import kotlin.math.max

/**
 * Cardio de las sesiones. Todo determinista: nada de UUID ni reloj (los ids salen del id de la sesión).
 *
 * - Zona 2 (continuo, conversacional): un solo bloque con la duración pedida.
 * - Intervalos: calentamiento, rondas de trabajo/recuperación según el nivel y vuelta a la calma; las duraciones suman
 *   EXACTAMENTE los segundos pedidos (el sobrante va a la vuelta a la calma) para que el estimador mida lo prometido.
 * El estimador cuenta además 60 s de preparación del ejercicio de cardio; quien llama descuenta ese minuto del bloque.
 */
internal object CardioBuilder {

    const val SETUP_SECONDS = 60

    /** Un bloque de intervalos no pasa de 40 min (más no es un entrenamiento de intervalos). */
    const val INTERVALS_MAX_SECONDS = 40 * 60

    /** Un bloque continuo no pasa de 75 min; lo que sobra va a otro bloque (otro aparato si hay). */
    const val STEADY_MAX_SECONDS = 75 * 60

    /**
     * Reparte [total] segundos de cardio en bloques: el primero llega a [firstCap] y el resto, a [STEADY_MAX_SECONDS] cada uno.
     * Cada bloque extra paga su preparación de [SETUP_SECONDS], que sale del reparto para que el tiempo total no cambie.
     */
    fun split(total: Int, firstCap: Int): List<Int> {
        if (total <= firstCap + 10 * 60) return listOf(total)
        val out = ArrayList<Int>()
        out += firstCap
        var remaining = total - firstCap - SETUP_SECONDS
        while (remaining > 0) {
            val chunk = minOf(remaining, STEADY_MAX_SECONDS)
            out += chunk
            remaining -= chunk
            if (remaining in 1 until 5 * 60 + SETUP_SECONDS) {
                out[out.lastIndex] += (remaining - SETUP_SECONDS).coerceAtLeast(0)
                break
            }
            remaining -= SETUP_SECONDS
        }
        return out
    }

    fun displayName(type: CardioType): String = when (type) {
        CardioType.TREADMILL -> "Cinta"
        CardioType.ELLIPTICAL -> "Elíptica"
        CardioType.ROW_MACHINE -> "Remo en máquina"
        CardioType.BIKE_STATIONARY -> "Bicicleta estática"
        CardioType.RUN_OUTDOOR -> "Carrera"
        CardioType.BIKE_OUTDOOR -> "Ciclismo"
        CardioType.WALK -> "Caminata"
        CardioType.STAIR_CLIMBER -> "Escaladora"
        CardioType.AIR_BIKE -> "Air bike"
        CardioType.SKI_ERG -> "SkiErg"
        CardioType.CURVED_TREADMILL -> "Cinta curva"
        CardioType.SLED -> "Trineo"
    }

    /** Tipo de cardio: el que la persona declaró si este lugar lo permite; si no, uno que el material permita. */
    fun typeFor(ctx: GenContext, equipment: DayEquipment, intervals: Boolean, salt: Int): CardioType {
        val available = equipment.cardioTypes
        ctx.preferredCardioType?.let { preferred -> if (preferred in available) return preferred }
        val machines = available.filter { it != CardioType.WALK && it != CardioType.RUN_OUTDOOR && it != CardioType.BIKE_OUTDOOR }
        val advanced = ctx.level == RoutineLevel.INTERMEDIATE || ctx.level == RoutineLevel.ADVANCED
        if (machines.isNotEmpty()) {
            val pool = if (intervals) {
                machines.filter { it == CardioType.ROW_MACHINE || it == CardioType.BIKE_STATIONARY || it == CardioType.TREADMILL }
                    .ifEmpty { machines }
            } else {
                machines.filter { it == CardioType.BIKE_STATIONARY || it == CardioType.TREADMILL || it == CardioType.ELLIPTICAL }
                    .ifEmpty { machines }
            }
            return pool[Math.floorMod(ctx.seed + salt, pool.size)]
        }
        if (CardioType.BIKE_OUTDOOR in available && advanced) return CardioType.BIKE_OUTDOOR
        return if (intervals && advanced) CardioType.RUN_OUTDOOR else CardioType.WALK
    }

    fun intensityFor(ctx: GenContext): CardioIntensity = ctx.cardioIntensityOrNull ?: CardioIntensity.BAJA

    fun steady(type: CardioType, seconds: Int, intensity: CardioIntensity = CardioIntensity.BAJA): CardioDetails = CardioDetails(
        type = type,
        intensity = intensity,
        targetDurationSeconds = seconds,
        intensityLevel = if (intensity == CardioIntensity.BAJA) 4 else intensity.resolvedLevel(),
    )

    private fun CardioIntensity.resolvedLevel(): Int = when (this) {
        CardioIntensity.BAJA -> 4
        CardioIntensity.MEDIA -> 6
        CardioIntensity.ALTA -> 8
        CardioIntensity.MUY_ALTA -> 9
    }

    /** Intervalos que suman exactamente [seconds]. */
    fun intervals(type: CardioType, seconds: Int, level: RoutineLevel, idPrefix: String): CardioDetails {
        val warm = if (seconds >= 1500) 300 else if (seconds >= 900) 180 else 120
        val cool = if (seconds >= 1500) 240 else if (seconds >= 900) 150 else 90
        val (work, recover) = when (level) {
            RoutineLevel.NOVICE -> 30 to 90
            RoutineLevel.RETURNING -> 30 to 75
            RoutineLevel.INTERMEDIATE -> 40 to 60
            RoutineLevel.ADVANCED -> 45 to 45
        }
        val middle = max(0, seconds - warm - cool)
        val rounds = max(1, middle / (work + recover))
        val used = rounds * (work + recover)
        val blocks = ArrayList<CardioIntervalBlock>()
        var index = 0
        fun add(kind: CardioBlockType, duration: Int, level: Int) {
            blocks += CardioIntervalBlock(id = "$idPrefix-b${index++}", type = kind, durationSeconds = duration, intensityLevel = level)
        }
        add(CardioBlockType.WARMUP, warm, 3)
        repeat(rounds) {
            add(CardioBlockType.WORK, work, 8)
            add(CardioBlockType.RECOVER, recover, 3)
        }
        add(CardioBlockType.COOLDOWN, cool + (middle - used).coerceAtLeast(0) + (seconds - warm - cool - middle).coerceAtLeast(0), 2)
        val total = blocks.sumOf { it.durationSeconds }
        return CardioDetails(
            type = type,
            intensity = CardioIntensity.ALTA,
            targetDurationSeconds = total,
            intervalBlocks = blocks,
            intervalRounds = 1,
        )
    }

    /**
     * Ejercicio de cardio. El cardio no está en el catálogo v2 de fuerza: su identidad es `custom:cardio-<tipo>`, que es lo
     * que el cortafuegos del catálogo (`catalogV2SelectionIssues`) acepta para ejercicios que no son del catálogo.
     */
    fun exercise(id: String, details: CardioDetails): Exercise {
        val identity = "custom:cardio-${details.type.name.lowercase()}"
        return Exercise(
            id = id,
            name = displayName(details.type),
            exerciseDbId = identity,
            exerciseId = identity,
            canonicalExerciseId = identity,
            cardioDetails = details,
        )
    }

    fun part(id: String, exercises: List<Exercise>): SessionPart = SessionPart(
        id = id,
        name = "Cardio",
        exercises = exercises,
        isCardioGroup = true,
    )
}

/**
 * Movilidad de las sesiones: elige movimientos del catálogo de movilidad (`MobilityExerciseCatalog`) por foco y los
 * encadena hasta cubrir los segundos pedidos. Los ids están verificados por una prueba.
 */
internal object MobilityBuilder {

    private const val ITEM_SECONDS = 45

    private val lower = listOf(
        "mob_ankle_dorsiflexion_wall", "mob_90_90_hip", "mob_hip_flexor_lunge", "mob_deep_squat_pry",
        "mob_glute_bridge_march", "mob_adductor_rockback", "mob_hamstring_walkout", "mob_hip_car",
    )
    private val upper = listOf(
        "mob_cat_cow", "mob_thread_needle", "mob_wall_slides", "mob_shoulder_car",
        "mob_scapular_protraction_retraction", "mob_open_book", "mob_wall_thoracic_extension", "mob_pec_doorway",
    )
    private val full = listOf(
        "mob_cat_cow", "mob_hip_flexor_lunge", "mob_half_kneeling_t_spine_rotation", "mob_deep_squat_pry",
        "mob_wall_slides", "mob_ankle_dorsiflexion_wall", "mob_90_90_hip", "mob_shoulder_car",
        "mob_thread_needle", "mob_hip_car",
    )
    private val recovery = listOf(
        "mob_90_90_breathing", "mob_cat_cow", "mob_child_pose_rotation", "mob_open_book",
        "mob_figure_four_supine", "mob_pigeon_supported", "mob_supine_hamstring_floss", "mob_thoracic_sidebend",
        "mob_couch_stretch", "mob_frog_stretch", "mob_butterfly_stretch", "mob_pec_floor_opener",
        "mob_lat_prayer_stretch", "mob_calf_stretch_wall", "mob_standing_hamstring_stretch", "mob_crossbody_stretch",
        "mob_neck_rotation_seated", "mob_quad_stretch_side",
    )

    /** Todos los ids de movilidad que cita el generador (para la prueba de catálogo). */
    val allIds: Set<String> = (lower + upper + full + recovery).toSet()

    private fun idsFor(focus: MobilityFocus): List<String> = when (focus) {
        MobilityFocus.LOWER -> lower
        MobilityFocus.UPPER -> upper
        MobilityFocus.FULL -> full
        MobilityFocus.RECOVERY -> recovery
    }

    /** Serie de movilidad que suma [seconds] segundos (a pasos de 45 s; el último ajusta el sobrante). */
    fun series(focus: MobilityFocus, seconds: Int, idPrefix: String, seed: Int): List<MobilitySeries> {
        if (seconds <= 0) return emptyList()
        val ids = idsFor(focus)
        val out = ArrayList<MobilitySeries>()
        var remaining = seconds
        var index = 0
        val rotation = Math.floorMod(seed, ids.size)
        while (remaining > 0) {
            val id = ids[(index + rotation) % ids.size]
            val movement = MobilityExerciseCatalog.findById(id)
            if (movement == null) {
                index++
                if (index > ids.size * 4) return out
                continue
            }
            val duration = if (remaining >= ITEM_SECONDS + 20) ITEM_SECONDS else remaining.coerceAtLeast(20)
            val round = index / ids.size
            out += MobilitySeries(
                id = "$idPrefix-m$index",
                exerciseDbId = movement.id,
                name = movement.name,
                sets = 1,
                durationSeconds = duration,
                unit = MobilityUnit.SECONDS,
                bodyZones = listOf(movement.bodyRegion),
                associatedDiscomforts = movement.discomfortIds,
                notes = if (round > 0) "Segunda vuelta" else null,
            )
            remaining -= duration
            index++
            if (index > ids.size * 6) break
        }
        return out
    }

    fun part(id: String, series: List<MobilitySeries>): SessionPart = SessionPart(
        id = id,
        name = "Movilidad",
        isMobilityGroup = true,
        mobilitySeries = series,
    )

    fun seconds(series: List<MobilitySeries>): Int = series.sumOf { (it.durationSeconds ?: 30) * it.sets.coerceAtLeast(1) }
}
