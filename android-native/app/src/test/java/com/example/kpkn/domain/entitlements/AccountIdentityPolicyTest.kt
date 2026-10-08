package com.example.kpkn.domain.entitlements

import com.example.kpkn.domain.account.AccountIdentityPolicy
import com.example.kpkn.domain.account.AccountState
import org.junit.Assert.*
import org.junit.Test

class AccountIdentityPolicyTest {
    @Test fun unavailableAuthCannotExposeASelectedAccount() {
        assertTrue(AccountIdentityPolicy.state("profile-a", "a", null, false, false) is AccountState.NeedsRecovery)
    }
    @Test fun aDifferentSdkIdentityCannotExposePreviousData() {
        assertTrue(AccountIdentityPolicy.state("profile-a", "a", "b", true, false) is AccountState.NeedsRecovery)
    }
    @Test fun guestLocalUseSurvivesAnUnavailableAccountService() {
        assertEquals(AccountState.Unavailable, AccountIdentityPolicy.state("guest", null, null, false, false))
        assertEquals(AccountState.Guest("guest"), AccountIdentityPolicy.state("guest", null, null, true, false))
    }
    @Test fun aMatchingIdentityAndAnIntactRegistryAreRequired() {
        assertEquals(AccountState.SignedIn("a", null, null), AccountIdentityPolicy.state("profile-a", "a", "a", true, false))
        assertTrue(AccountIdentityPolicy.state("profile-a", "a", "a", true, true) is AccountState.NeedsRecovery)
    }
}
