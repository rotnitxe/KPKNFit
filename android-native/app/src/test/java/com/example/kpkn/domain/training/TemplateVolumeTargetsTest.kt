package com.example.kpkn.domain.training

import com.example.kpkn.data.models.VolumeRecommendation
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateVolumeTargetsTest {
    @Test
    fun personalized_targets_use_the_lower_mrv_without_inflating_to_existing_volume() {
        val target = TemplateVolumeScaler.targetsFor(
            listOf(VolumeRecommendation("Gluteos", 9, 14, 22)),
            mapOf("Glúteos" to 27.0),
        ).getValue("Glúteos")
        assertEquals(9, target.floor)
        assertEquals(14, target.target)
        assertEquals(16, target.ceiling)
    }

    @Test
    fun lower_personalized_mrv_wins_over_global_mrv() {
        val target = TemplateVolumeScaler.targetsFor(
            listOf(VolumeRecommendation("Pectorales", 5, 9, 12)),
        ).getValue("Pectorales")
        assertEquals(5, target.floor)
        assertEquals(9, target.target)
        assertEquals(12, target.ceiling)
    }
}
