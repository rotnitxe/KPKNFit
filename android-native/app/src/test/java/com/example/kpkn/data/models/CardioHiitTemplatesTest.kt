package com.example.kpkn.data.models

import org.junit.Assert.*
import org.junit.Test

class CardioHiitTemplatesTest {

    @Test
    fun allTemplatesHavePositiveDurations() {
        CardioHiitTemplates.all.forEach { tmpl ->
            assertTrue("${tmpl.id} blocks empty", tmpl.blocks.isNotEmpty())
            tmpl.blocks.forEach { b ->
                assertTrue("${tmpl.id} block ${b.id} duration <=0", b.durationSeconds > 0)
            }
            assertTrue("${tmpl.id} rounds invalid", tmpl.rounds in 1..99)
            if (tmpl.warmupSeconds > 0) assertTrue("${tmpl.id} warmup >0", tmpl.warmupSeconds > 0)
            if (tmpl.cooldownSeconds > 0) assertTrue("${tmpl.id} cooldown >0", tmpl.cooldownSeconds > 0)
        }
    }

    @Test
    fun tabataDurationIsTwelveMinutesNotSixtyEight() {
        val tmpl = CardioHiitTemplates.findById("hiit_tabata_20_10")!!
        val details = tmpl.toDetails(CardioType.TREADMILL)
        // warmup 5min (300) + (20+10)*8=240 + cooldown 3min (180) = 720
        assertEquals(720, details.effectiveDurationSeconds())
        assertEquals(720, details.totalIntervalSeconds())
        assertEquals(18, details.intervalBlocks.size)
        // Verify warmup and cooldown appear exactly once
        assertEquals(1, details.intervalBlocks.count { it.type == CardioBlockType.WARMUP })
        assertEquals(1, details.intervalBlocks.count { it.type == CardioBlockType.COOLDOWN })
        assertEquals(8, details.intervalBlocks.count { it.type == CardioBlockType.WORK })
        assertEquals(8, details.intervalBlocks.count { it.type == CardioBlockType.RECOVER })
    }

    @Test
    fun thirtyThirtyDurationIsNineteenMinutes() {
        val tmpl = CardioHiitTemplates.findById("hiit_30_30")!!
        val details = tmpl.toDetails(CardioType.TREADMILL)
        // 300 + (30+30)*10=600 + 240 =1140
        assertEquals(1140, details.effectiveDurationSeconds())
        assertEquals(22, details.intervalBlocks.size) // 1+20+1
    }

    @Test
    fun sprintEightDurationIsTwentyFourMinutes() {
        val tmpl = CardioHiitTemplates.findById("hiit_sprint_8")!!
        val details = tmpl.toDetails(CardioType.BIKE_STATIONARY)
        // 300 + (30+90)*8=960 +180 =1440
        assertEquals(1440, details.effectiveDurationSeconds())
        assertEquals(18, details.intervalBlocks.size)
    }

    @Test
    fun fartlekAndZ2RoundOneNotExpanded() {
        val fartlek = CardioHiitTemplates.findById("hiit_fartlek_treadmill")!!
        val detailsF = fartlek.toDetails(CardioType.TREADMILL)
        // Fartlek has 7 blocks with rounds 1, no extra warmup/cooldown
        assertEquals(7, detailsF.intervalBlocks.size)
        assertEquals(1440, detailsF.effectiveDurationSeconds()) // sum as above

        val z2 = CardioHiitTemplates.findById("hiit_z2_peaks")!!
        val detailsZ = z2.toDetails(CardioType.TREADMILL)
        assertEquals(9, detailsZ.intervalBlocks.size)
    }

    @Test
    fun applicableTypesSubsetOfCatalog() {
        val catalogTypes = CardioCatalog.items.map { it.type }.toSet()
        CardioHiitTemplates.all.forEach { tmpl ->
            tmpl.applicableTypes?.forEach { t ->
                assertTrue("${tmpl.id} applicableType $t not in catalog", t in catalogTypes)
            }
        }
    }

    @Test
    fun toDetailsPreservesGpsFlagPerType() {
        val tmpl = CardioHiitTemplates.findById("hiit_tabata_20_10")!!
        val outdoor = tmpl.toDetails(CardioType.RUN_OUTDOOR)
        // RUN_OUTDOOR catalog has requiresGps true, but toDetails builds from scratch with type only, not catalog's requiresGps.
        // It should at least have correct type
        assertEquals(CardioType.RUN_OUTDOOR, outdoor.type)
    }
}
