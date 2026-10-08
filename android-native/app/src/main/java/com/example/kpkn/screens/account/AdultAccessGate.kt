package com.example.kpkn.screens.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.R
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.eligibility.AdultEligibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Legacy ineligible profiles retain their data and their export/deletion controls. */
@Composable fun AdultAccessGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val profile = remember { ProfileStorage.context(context) }
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    var accepted by remember { mutableStateOf<Boolean?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var accountControls by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile) { accepted = withContext(Dispatchers.IO) {
        profile.getSharedPreferences("adult_admission", 0).getBoolean("confirmed_18", false)
    } }
    val eligibleAge = settings.age?.let(AdultEligibility::permitsDeclaredAge) ?: true
    when {
        accountControls -> AccountScreen(onBack = { accountControls = false }, onDataControls = { accountControls = false; controls = true }, dataControlsOnly = true)
        controls -> com.example.kpkn.screens.settings.SettingsScreen(onBack = { controls = false }, onAccount = { controls = false; accountControls = true }, dataControlsOnly = true)
        accepted == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        (accepted == true || settings.age?.let(AdultEligibility::permitsDeclaredAge) == true) && eligibleAge -> content()
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.adult_access_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(if (eligibleAge) R.string.adult_access_notice else R.string.adult_legacy_notice))
            if (eligibleAge) {
                val confirmationLabel = stringResource(R.string.adult_access_confirm)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(confirm, { confirm = it }, enabled = !busy, modifier = Modifier.semantics { contentDescription = confirmationLabel })
                    Text(confirmationLabel, Modifier.weight(1f))
                }
                Button(enabled = confirm && !busy, onClick = { scope.launch {
                    busy = true; error = false
                    try {
                        withContext(Dispatchers.IO) { check(profile.getSharedPreferences("adult_admission", 0).edit().putBoolean("confirmed_18", true).commit()) }
                        accepted = true
                    } catch (_: Exception) { error = true }
                    finally { busy = false }
                } }) { Text(stringResource(R.string.adult_access_continue)) }
            }
            if (error) Text(stringResource(R.string.account_operation_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { controls = true }, enabled = !busy) { Text(stringResource(R.string.adult_access_controls)) }
        }
    }
}
