package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.domain.auge.CardioRingDrainEngine

data class CardioFinishLine(
    val exerciseName: String,
    val prescribedSentence: String,
    val actualSummary: String,
    val splitLabels: List<String>,
    val augeLine: String?,
)

object CardioSessionSummary {
    fun lines(
        session: Session,
        completedSets: Map<String, CompletedSet>,
        settings: Settings = Settings(),
    ): List<CardioFinishLine> {
        return session.allExercises().mapNotNull { exercise ->
            val details = exercise.cardioDetails ?: return@mapNotNull null
            if (!exercise.isCardio) return@mapNotNull null
            val completed = completedSets.entries.firstOrNull { (key, _) ->
                key.startsWith(exercise.id)
            }?.value
            val actualDuration = completed?.timeSeconds ?: 0
            val actualDistance = completed?.distanceKm
            val actual = buildList {
                if (actualDuration > 0) add(CardioPrescriptionFormatter.formatDuration(actualDuration))
                actualDistance?.takeIf { it > 0 }?.let { add(CardioPrescriptionFormatter.formatDistanceKm(it)) }
                completed?.rpe?.let { add("RPE ${if (it % 1.0 == 0.0) it.toInt() else it}") }
                completed?.avgHeartRate?.let { add("FC $it") }
            }.joinToString(" · ").ifBlank { "Sin registro" }
            val splits = completed?.kmSplitPaces.orEmpty().mapIndexed { index, pace ->
                "km ${index + 1} · ${CardioPrescriptionFormatter.formatPace(pace)}"
            }
            val drain = CardioRingDrainEngine.drain(
                details = details,
                durationSeconds = actualDuration.takeIf { it > 0 } ?: details.effectiveDurationSeconds(),
                rpeEffective = completed?.rpe ?: details.resolvedRpe(),
                settings = settings,
            )
            val auge = "AUGE cardio · CNS ${drain.cns.toInt()} · muscular ${drain.muscular.toInt()} · spinal ${drain.spinal.toInt()}"
            CardioFinishLine(
                exerciseName = exercise.name.ifBlank { CardioPrescriptionFormatter.typeLabel(details.type) },
                prescribedSentence = CardioPrescriptionFormatter.sentence(details),
                actualSummary = actual,
                splitLabels = splits,
                augeLine = auge,
            )
        }
    }
}
