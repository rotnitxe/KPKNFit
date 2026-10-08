package com.example.kpkn.domain.account

import kotlinx.coroutines.flow.StateFlow

sealed interface AccountState {
    data object Loading : AccountState
    data object Unavailable : AccountState
    data class Guest(val profileKey: String) : AccountState
    data class SignedIn(val uid: String, val email: String?, val displayName: String?) : AccountState
    data class NeedsRecovery(val authenticatedUid: String?, val selectedUid: String?) : AccountState
}

interface AccountRepository {
    val state: StateFlow<AccountState>
    suspend fun signIn(email: String, password: String, incorporateGuest: Boolean)
    suspend fun signUp(email: String, password: String, adultConfirmed: Boolean, incorporateGuest: Boolean)
    suspend fun signInGoogle(idToken: String, incorporateGuest: Boolean)
    suspend fun sendPasswordRecovery(email: String)
    suspend fun linkGoogle(idToken: String)
    suspend fun reauthenticate(password: String)
    suspend fun signOut()
    suspend fun deleteAccount()
}
