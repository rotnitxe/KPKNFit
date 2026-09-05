package com.example.kpkn.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTagEducationTest {

    @Test
    fun seenTrueNeverShowsLongEducation() {
        assertFalse(showTagEducation(seen = true, emptyRows = true))
        assertFalse(showTagEducation(seen = true, emptyRows = false))
    }

    @Test
    fun firstOpenShowsEducationEvenWithTags() {
        assertTrue(showTagEducation(seen = false, emptyRows = true))
        assertTrue(showTagEducation(seen = false, emptyRows = false))
    }
}
