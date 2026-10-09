package com.example.kpkn.screens.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.account.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountUiState(val busy: Boolean = false, val error: Boolean = false, val recoveryEmailSent: Boolean = false)
class AccountViewModel(private val context: Context) : ViewModel() {
    val account = AccountGraph.account(context)
    val accountState = account.state
    val entitlement = AccountGraph.entitlement
    private val _ui = MutableStateFlow(AccountUiState())
    val ui = _ui.asStateFlow()
    fun action(operation: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.value = AccountUiState(busy = true)
            try { withContext(Dispatchers.IO) { operation() }; _ui.value = _ui.value.copy(busy = false, error = false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: com.example.kpkn.data.backup.ActiveDataUseException) { _ui.value = AccountUiState() }
            catch (_: Exception) { _ui.value = AccountUiState(error = true) }
        }
    }
    fun signIn(email: String, password: String, guest: Boolean) = action { account.signIn(email, password, guest) }
    fun signUp(email: String, password: String, adult: Boolean, guest: Boolean) = action {
        require(password.length in 8..256 && email.length <= 254)
        account.signUp(email, password, adult, guest)
    }
    fun recover(email: String) = action {
        account.sendPasswordRecovery(email)
        withContext(Dispatchers.Main) { _ui.value = AccountUiState(recoveryEmailSent = true) }
    }
    fun synchronize(acceptConsent: Boolean) = action {
        require(acceptConsent)
        val uid = ProfileStorage.current().uid ?: error("Account required")
        val sync = AccountGraph.sync() ?: error("Sync unavailable")
        sync.enable(); AccountGraph.enqueue(context, uid)
    }
    fun stopSync() = action { AccountGraph.sync()?.disable() }
}
