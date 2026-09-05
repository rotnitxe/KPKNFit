package com.example.kpkn.domain.competitions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionPlaceHonorsTest {

    @Test
    fun first_three_map_to_medals_without_trophy() {
        val gold = CompetitionPlaceHonors.fromPlacement(1, null)
        val silver = CompetitionPlaceHonors.fromPlacement(2, "first_meet")
        val bronze = CompetitionPlaceHonors.fromPlacement(3, null)
        assertEquals(CompetitionMedalHonor.GOLD, gold?.medal)
        assertNull(gold?.trophy)
        assertEquals(CompetitionMedalHonor.SILVER, silver?.medal)
        assertNull(silver?.trophy)
        assertEquals(CompetitionMedalHonor.BRONZE, bronze?.medal)
        assertTrue(CompetitionPlaceHonors.isValid(1, null))
        assertTrue(CompetitionPlaceHonors.isValid(2, null))
        assertTrue(CompetitionPlaceHonors.isValid(3, null))
    }

    @Test
    fun fourth_or_worse_requires_catalog_trophy() {
        assertFalse(CompetitionPlaceHonors.isValid(4, null))
        assertFalse(CompetitionPlaceHonors.isValid(4, "invented"))
        val honor = CompetitionPlaceHonors.fromPlacement(4, "nine_for_nine")
        assertEquals(CompetitionTrophyHonor.NINE_FOR_NINE, honor?.trophy)
        assertNull(honor?.medal)
        assertEquals("4+", CompetitionPlaceHonors.placementString(4))
    }
}
