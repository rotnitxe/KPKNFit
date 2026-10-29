package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionPlaceHonorsTest {

    private val record = CompetitionRecord(id = "c1", title = "Nacional", trophyId = "nine_for_nine")

    @Test
    fun first_three_places_take_their_medal() {
        assertEquals(CompetitionMedalHonor.GOLD, CompetitionPlaceHonors.medalForPlace(1))
        assertEquals(CompetitionMedalHonor.SILVER, CompetitionPlaceHonors.medalForPlace(2))
        assertEquals(CompetitionMedalHonor.BRONZE, CompetitionPlaceHonors.medalForPlace(3))
        assertNull(CompetitionPlaceHonors.medalForPlace(4))
        assertNull(CompetitionPlaceHonors.medalForPlace(null))
    }

    @Test
    fun outside_the_podium_keeps_the_exact_place_without_medal() {
        val fifth = CompetitionPlaceHonors.applyPlace(record, 5)
        assertEquals("5", fifth.placement)
        assertNull(fifth.medal)
        assertNull(fifth.trophyId)
        val tenth = CompetitionPlaceHonors.applyPlace(record, 10)
        assertEquals(10, CompetitionPlaceHonors.parsePlace(tenth.placement))
    }

    @Test
    fun podium_place_saves_the_medal_and_no_place_clears_both() {
        val silver = CompetitionPlaceHonors.applyPlace(record, 2)
        assertEquals("2", silver.placement)
        assertEquals("silver", silver.medal)
        assertEquals(CompetitionMedalHonor.SILVER, CompetitionPlaceHonors.medalOf(silver))
        val none = CompetitionPlaceHonors.applyPlace(silver, null)
        assertNull(none.placement)
        assertNull(none.medal)
        assertNull(CompetitionPlaceHonors.medalOf(none))
    }

    @Test
    fun old_four_plus_reads_as_no_place() {
        assertNull(CompetitionPlaceHonors.parsePlace("4+"))
        assertNull(CompetitionPlaceHonors.parsePlace("0"))
        assertNull(CompetitionPlaceHonors.placementString(0))
        assertEquals("4", CompetitionPlaceHonors.placementString(4))
    }

    @Test
    fun medal_of_falls_back_to_the_saved_place() {
        assertEquals(CompetitionMedalHonor.BRONZE, CompetitionPlaceHonors.medalOf(record.copy(placement = "3")))
    }

    @Test
    fun extras_toggle_on_and_off_and_admit_several() {
        val one = CompetitionPlaceHonors.toggleExtra(record, CompetitionExtra.NATIONAL_RECORD)
        val two = CompetitionPlaceHonors.toggleExtra(one, CompetitionExtra.QUALIFIED)
        assertTrue(CompetitionExtra.NATIONAL_RECORD.id in two.extras)
        assertTrue(CompetitionExtra.QUALIFIED.id in two.extras)
        val back = CompetitionPlaceHonors.toggleExtra(two, CompetitionExtra.NATIONAL_RECORD)
        assertFalse(CompetitionExtra.NATIONAL_RECORD.id in back.extras)
        assertTrue(CompetitionExtra.QUALIFIED.id in back.extras)
    }
}
