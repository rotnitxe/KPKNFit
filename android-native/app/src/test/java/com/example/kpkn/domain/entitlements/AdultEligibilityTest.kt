package com.example.kpkn.domain.entitlements

import com.example.kpkn.domain.eligibility.AdultEligibility
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AdultEligibilityTest {
    @Test fun eighteenthBirthdayIsTheBoundary() {
        assertFalse(AdultEligibility.permitsBirthDate(LocalDate.parse("2008-10-08"), LocalDate.parse("2026-10-07")))
        assertTrue(AdultEligibility.permitsBirthDate(LocalDate.parse("2008-10-07"), LocalDate.parse("2026-10-07")))
        assertFalse(AdultEligibility.permitsDeclaredAge(17))
        assertTrue(AdultEligibility.permitsDeclaredAge(18))
        assertTrue(AdultEligibility.permitsDataControl())
    }
}
