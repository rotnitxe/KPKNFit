package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType

data class CardioLiveNowNext(
    val nowLabel: String,
    val nextLabel: String?,
    val phase: CardioBlockType?,
)

/** Frases Runna-style compartidas por editor colapsado, expandido y vivo. */
object CardioPrescriptionFormatter {

    fun typeLabel(type: CardioType): String = when (type) {
        CardioType.TREADMILL -> "Cinta"
        CardioType.ELLIPTICAL -> "Elíptica"
        CardioType.ROW_MACHINE -> "Remo"
        CardioType.BIKE_STATIONARY -> "Bici estática"
        CardioType.RUN_OUTDOOR -> "Carrera exterior"
        CardioType.BIKE_OUTDOOR -> "Bici exterior"
        CardioType.WALK -> "Caminata"
        CardioType.STAIR_CLIMBER -> "Escaladora"
        CardioType.AIR_BIKE -> "Air Bike"
        CardioType.SKI_ERG -> "SkiErg"
        CardioType.CURVED_TREADMILL -> "Cinta curva"
        CardioType.SLED -> "Trineo"
    }

    fun formatDuration(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return when {
            hours > 0 && secs == 0 -> "${hours} h ${minutes} min"
            hours > 0 -> "${hours} h ${minutes} min ${secs} s"
            minutes > 0 && secs == 0 -> "$minutes min"
            minutes > 0 -> "${minutes} min ${secs} s"
            else -> "$secs s"
        }
    }

    fun formatClock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        val secs = safe % 60
        return "%02d:%02d".format(minutes, secs)
    }

    fun formatPace(secondsPerKm: Int): String {
        val safe = secondsPerKm.coerceAtLeast(1)
        return "%d:%02d/km".format(safe / 60, safe % 60)
    }

    fun formatDistanceKm(km: Double): String {
        val rounded = kotlin.math.round(km * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) "${rounded.toInt()} km" else "${"%.1f".format(rounded)} km"
    }

    fun rpeAnchor(level: Int): String = when (level.coerceIn(1, 10)) {
        in 1..2 -> "Muy suave"
        in 3..4 -> "Suave"
        in 5..6 -> "Algo duro"
        in 7..8 -> "Duro"
        9 -> "Muy duro"
        else -> "Máximo"
    }

    fun phaseLabel(type: CardioBlockType): String = when (type) {
        CardioBlockType.WARMUP -> "Calentamiento"
        CardioBlockType.WORK -> "Esfuerzo"
        CardioBlockType.RECOVER -> "Pausa"
        CardioBlockType.COOLDOWN -> "Enfriamiento"
    }

    fun sentence(details: CardioDetails): String {
        val parts = mutableListOf(typeLabel(details.type))
        when (val shape = CardioRepeatGrammar.shape(details)) {
            is CardioAuthoringShape.Steady -> {
                details.targetDurationSeconds?.takeIf { it > 0 }?.let { parts += formatDuration(it) }
                    ?: parts.add("Libre")
                details.targetDistanceKm?.takeIf { it > 0 }?.let { parts += formatDistanceKm(it) }
                details.targetPaceSecondsPerKm?.let { parts += formatPace(it) }
                details.targetHrPercent?.let { parts += "FC $it%" }
                val rpe = details.resolvedRpe().toInt().coerceIn(1, 10)
                parts += "RPE $rpe (${rpeAnchor(rpe)})"
            }
            is CardioAuthoringShape.Uniform -> {
                val repeat = shape.repeat
                if (repeat.warmupOpen) parts += "Calentamiento libre"
                else if (repeat.warmupSeconds > 0) parts += "Calentamiento ${formatDuration(repeat.warmupSeconds)}"
                val work = formatDuration(repeat.workSeconds)
                val rest = if (repeat.restSeconds > 0) " / ${formatDuration(repeat.restSeconds)} pausa" else ""
                val setsBit = if (repeat.sets > 1) " · ${repeat.sets} series" else ""
                parts += "${repeat.rounds}× ($work esfuerzo$rest)$setsBit"
                if (repeat.cooldownOpen) parts += "Enfriamiento libre"
                else if (repeat.cooldownSeconds > 0) parts += "Enfriamiento ${formatDuration(repeat.cooldownSeconds)}"
                val total = details.effectiveDurationSeconds()
                if (total > 0) parts += formatDuration(total)
            }
            is CardioAuthoringShape.Irregular -> {
                val works = shape.blocks.filter { it.type == CardioBlockType.WORK }
                if (works.size in 2..8) {
                    parts += works.joinToString(" → ") { formatDuration(it.durationSeconds) }
                } else {
                    parts += "${shape.blocks.size} bloques"
                }
                val total = details.effectiveDurationSeconds()
                if (total > 0) parts += formatDuration(total)
            }
        }
        return parts.joinToString(" · ")
    }

    fun blockPhrase(block: CardioIntervalBlock): String {
        val target = targetBits(block)
        return buildString {
            append(phaseLabel(block.type))
            append(" ")
            append(formatDuration(block.durationSeconds))
            if (!target.isNullOrBlank()) {
                append(" · ")
                append(target)
            }
        }
    }

    fun targetBits(block: CardioIntervalBlock): String? {
        val bits = listOfNotNull(
            block.speedKmh?.takeIf { it > 0 }?.let { "${trimNumber(it)} km/h" },
            block.watts?.takeIf { it > 0 }?.let { "$it W" },
            block.rpm?.takeIf { it > 0 }?.let { "$it rpm" },
            block.inclinePercent?.takeIf { it != 0.0 }?.let { "${trimNumber(it)} %" },
            block.targetPaceSecondsPerKm?.let { formatPace(it) },
            block.targetHrPercent?.let { "FC $it%" },
            block.targetKcal?.takeIf { it > 0 }?.let { "${it.toInt()} kcal" },
            block.targetDistanceMeters?.takeIf { it > 0 }?.let { "${it.toInt()} m" },
        )
        return bits.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun liveNowNext(details: CardioDetails, elapsedSeconds: Int): CardioLiveNowNext {
        if (!details.hasIntervals()) {
            val remaining = (details.targetDurationSeconds ?: 0) - elapsedSeconds
            val extras = listOfNotNull(
                details.targetPaceSecondsPerKm?.let { formatPace(it) },
                details.targetHrPercent?.let { "FC $it%" },
            ).joinToString(" · ")
            val now = if (details.targetDurationSeconds == null) {
                "Continuo libre"
            } else if (remaining <= 0) {
                "Listo"
            } else {
                "Continuo · quedan ${formatDuration(remaining.coerceAtLeast(0))}"
            }
            val labeled = if (extras.isBlank()) now else "$now · $extras"
            return CardioLiveNowNext(labeled, nextLabel = null, phase = CardioBlockType.WORK)
        }
        val progress = CardioIntervalEngine.progressAt(details, elapsedSeconds)
            ?: return CardioLiveNowNext("Listo", null, null)
        if (progress.isComplete || progress.currentBlock == null) {
            return CardioLiveNowNext("Listo", null, null)
        }
        val current = progress.currentBlock
        val now = "${phaseLabel(current.type)} · quedan ${formatDuration(progress.remainingInBlock)}" +
            (targetBits(current)?.let { " · $it" } ?: "")
        val next = progress.nextBlock?.let { blockPhrase(it) }
        return CardioLiveNowNext(now, next, current.type)
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
