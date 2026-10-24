package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*

/** Existing food-target accounting; recorded training expenditure is never subtracted again. */
object RecordedEnergyBalance {
    fun calculateDailyEnergyBalance(
        consumedKcal: Int,
        trainingBurnKcal: Int,
        targetKcal: Int,
    ): DailyEnergyBalance {
        // The food target already represents the selected EER/activity plan.
        // Training expenditure is reported separately and is never subtracted
        // from intake a second time (which previously created a fake deficit).
        val netKcal = consumedKcal
        if (targetKcal <= 0) {
            return DailyEnergyBalance(
                consumedKcal = consumedKcal,
                trainingBurnKcal = trainingBurnKcal,
                netKcal = netKcal,
                targetKcal = 0,
                deltaFromTarget = 0,
                status = DailyEnergyStatus.MAINTENANCE,
            )
        }
        val deltaFromTarget = consumedKcal - targetKcal
        val status = when {
            deltaFromTarget < -150 -> DailyEnergyStatus.DEFICIT
            deltaFromTarget > 150 -> DailyEnergyStatus.SURPLUS
            else -> DailyEnergyStatus.MAINTENANCE
        }
        return DailyEnergyBalance(
            consumedKcal = consumedKcal,
            trainingBurnKcal = trainingBurnKcal,
            netKcal = netKcal,
            targetKcal = targetKcal,
            deltaFromTarget = deltaFromTarget,
            status = status,
        )
    }
}
