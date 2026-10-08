package com.example.kpkn.domain.eligibility

import java.time.LocalDate
import java.time.Period

object AdultEligibility {
    const val MINIMUM_AGE = 18
    fun permitsBirthDate(birthDate: LocalDate, today: LocalDate): Boolean =
        !birthDate.isAfter(today) && Period.between(birthDate, today).years >= MINIMUM_AGE
    fun permitsDeclaredAge(age: Int): Boolean = age in MINIMUM_AGE..120
    /** An existing ineligible profile retains access to exports and deletion. */
    fun permitsDataControl(): Boolean = true
}
