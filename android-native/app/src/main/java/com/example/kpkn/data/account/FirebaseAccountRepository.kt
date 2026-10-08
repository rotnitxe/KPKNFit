package com.example.kpkn.data.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import com.example.kpkn.data.backup.AppDataSessions
import com.example.kpkn.domain.account.AccountRepository
import com.example.kpkn.domain.account.AccountState
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FirebaseAccountRepository(private val context: Context, private val session: FirebaseSession?) : AccountRepository {
    private val _state = MutableStateFlow<AccountState>(AccountState.Loading)
    override val state = _state.asStateFlow()
    private val transport = session?.let(::AccountTransport)
    init { session?.auth?.addAuthStateListener { refresh() }; refresh() }
    fun refresh() {
        val profile = ProfileStorage.current()
        val user = session?.auth?.currentUser
        _state.value = com.example.kpkn.domain.account.AccountIdentityPolicy.state(profile.key, profile.uid,
            user?.uid, session != null, ProfileStorage.recoveryNeeded.value, user?.email, user?.displayName)
    }
    override suspend fun signIn(email: String, password: String, incorporateGuest: Boolean) {
        transition(incorporateGuest) { requireSession().auth.signInWithEmailAndPassword(email.trim(), password).awaitResult().user?.uid }
    }
    override suspend fun signUp(email: String, password: String, adultConfirmed: Boolean, incorporateGuest: Boolean) {
        require(adultConfirmed) { "Adult confirmation required" }
        transition(incorporateGuest) { requireSession().auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult().user?.uid }
    }
    override suspend fun signInGoogle(idToken: String, incorporateGuest: Boolean) {
        transition(incorporateGuest) { requireSession().auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitResult().user?.uid }
    }
    private suspend fun transition(incorporateGuest: Boolean, authenticate: suspend () -> String?) {
        val source = ProfileStorage.context(context)
        val imports = session?.let { com.example.kpkn.data.sync.GuestImportCoordinator(context, it, requireNotNull(transport)) }
        AppDataSessions.changeIdentity(source) {
            val snapshot = if (incorporateGuest && ProfileStorage.current().uid == null)
                requireNotNull(imports).prepare(source, com.example.kpkn.data.db.KpknDatabase.getInstance(source)) else null
            val uid = requireNotNull(authenticate())
            requireNotNull(transport).call(uid, "openAccount")
            if (snapshot != null) requireNotNull(imports).bindAndCopy(snapshot, uid)
            uid
        }
        withContext(Dispatchers.Main) { refresh() }
    }
    override suspend fun sendPasswordRecovery(email: String) = withContext(Dispatchers.IO) {
        requireSession().auth.sendPasswordResetEmail(email.trim()).awaitResult()
        Unit
    }
    override suspend fun linkGoogle(idToken: String) = withContext(Dispatchers.IO) {
        val user = requireSession().auth.currentUser ?: throw AccountServiceException("UNAUTHENTICATED")
        // Firebase links credentials to THIS proven UID; an equal email never triggers a data merge.
        user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitResult()
        withContext(Dispatchers.Main) { refresh() }
    }
    override suspend fun reauthenticate(password: String) = withContext(Dispatchers.IO) {
        val user = requireSession().auth.currentUser ?: throw AccountServiceException("UNAUTHENTICATED")
        val email = user.email ?: throw AccountServiceException("GOOGLE_REAUTHENTICATION_REQUIRED")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).awaitResult()
        user.getIdToken(true).awaitResult()
        Unit
    }
    override suspend fun signOut() {
        AppDataSessions.changeIdentity(ProfileStorage.context(context)) {
            session?.auth?.signOut()
            // Firebase has ended the session. An absent optional credential provider must not
            // strand the user in the previous local profile.
            runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            null
        }
        withContext(Dispatchers.Main) { refresh() }
    }
    override suspend fun deleteAccount() {
        val local = ProfileStorage.context(context)
        AppDataSessions.changeIdentity(local) {
            val uid = requireSession().auth.currentUser?.uid ?: throw AccountServiceException("UNAUTHENTICATED")
            val response = requireNotNull(transport).call(uid, "deleteAccount", buildJsonObject { put("confirmation", "DELETE_ACCOUNT") }, freshAuthentication = true)
            val pending = response["status"]?.toString()?.trim('"') == "DELETION_PENDING"
            check(pending || response["status"]?.toString()?.trim('"') == "DELETED")
            try {
                AccountDeletionNotices.save(pending, localPending = true)
                com.example.kpkn.data.backup.PersonalDataErasure.erase(local)
                AccountDeletionNotices.save(pending, localPending = false)
            } finally {
                // A failed local erase must not leave a revoked account signed in.
                requireSession().auth.signOut()
            }
            null
        }
        withContext(Dispatchers.Main) { refresh() }
    }
    private fun requireSession() = session ?: throw AccountServiceException("UNAVAILABLE")
    suspend fun reauthenticateGoogle(idToken: String) = withContext(Dispatchers.IO) {
        val user = requireSession().auth.currentUser ?: throw AccountServiceException("UNAUTHENTICATED")
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).awaitResult()
        user.getIdToken(true).awaitResult()
        Unit
    }
    suspend fun recoverIdentity() {
        AppDataSessions.changeIdentity(ProfileStorage.context(context)) {
            requireSession().auth.currentUser?.uid?.also { uid -> requireNotNull(transport).call(uid, "openAccount") }
        }
        withContext(Dispatchers.Main) { refresh() }
    }
    /** Explicit confirmation permits discarding the unavailable local profile, including unfinished sessions. */
    suspend fun eraseUnavailableLocalProfile() {
        val local = ProfileStorage.context(context)
        AppDataSessions.changeIdentity(local, discardOngoing = true) {
            try {
                com.example.kpkn.data.backup.PersonalDataErasure.erase(local)
            } finally { session?.auth?.signOut() }
            AccountDeletionNotices.clearLocalPending()
            null
        }
        withContext(Dispatchers.Main) { refresh() }
    }
}
