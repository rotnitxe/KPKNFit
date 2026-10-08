package com.example.kpkn.screens.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.kpkn.BuildConfig
import com.example.kpkn.R
import com.example.kpkn.data.account.AccountGraph
import com.example.kpkn.domain.account.AccountState
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AccountScreen(onBack: () -> Unit, onDataControls: (() -> Unit)? = null,
    provided: AccountViewModel? = null, dataControlsOnly: Boolean = false) {
    val context = LocalContext.current
    val viewModel = provided ?: viewModel { AccountViewModel(context) }
    val state by viewModel.accountState.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val entitlement by viewModel.entitlement.collectAsState()
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var adult by remember { mutableStateOf(false) }; var guest by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }
    var eraseUnavailable by remember { mutableStateOf(false) }
    var conflicts by remember { mutableStateOf<List<kotlinx.serialization.json.JsonObject>>(emptyList()) }
    var crashConsent by remember { mutableStateOf(false) }
    LaunchedEffect(context) { crashConsent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.kpkn.data.diagnostics.CrashReportingController.consent(context) } }
    val scope = rememberCoroutineScope()
    fun google(action: suspend (String) -> Unit) { viewModel.action {
        require(BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID.isNotBlank())
        val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID)
            .setNonce(java.util.UUID.randomUUID().toString()).build()
        val credential = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            CredentialManager.create(context).getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        }
        action(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineMedium)
        if (ui.busy) CircularProgressIndicator()
        if (ui.error) Text(stringResource(R.string.account_operation_failed), color = MaterialTheme.colorScheme.error)
        if (ui.recoveryEmailSent) Text(stringResource(R.string.account_recovery_sent))
        when (val current = state) {
            AccountState.Loading -> CircularProgressIndicator()
            AccountState.Unavailable -> Text(stringResource(R.string.account_unavailable))
            is AccountState.NeedsRecovery -> {
                Text(stringResource(R.string.account_recovery_needed))
                Button(onClick = { viewModel.action { viewModel.account.recoverIdentity() } }, enabled = !ui.busy) { Text(stringResource(R.string.account_recover_profile)) }
                TextButton(onClick = { viewModel.action { viewModel.account.signOut() } }, enabled = !ui.busy) { Text(stringResource(R.string.account_guest)) }
                TextButton(onClick = { eraseUnavailable = true }, enabled = !ui.busy) { Text(stringResource(R.string.account_erase_unavailable_local)) }
            }
            is AccountState.Guest -> {
                Text(stringResource(R.string.account_optional))
                OutlinedTextField(email, { email = it.take(254) }, label = { Text(stringResource(R.string.account_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it.take(256) }, label = { Text(stringResource(R.string.account_password)) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!dataControlsOnly) {
                    Choice(adult, { adult = it }, stringResource(R.string.account_adult))
                    Choice(guest, { guest = it }, stringResource(R.string.account_import_guest))
                }
                Button(onClick = { viewModel.signIn(email, password, guest) }, enabled = !ui.busy && email.isNotBlank() && password.isNotEmpty()) { Text(stringResource(R.string.account_signin)) }
                if (!dataControlsOnly) {
                    Button(onClick = { viewModel.signUp(email, password, adult, guest) }, enabled = !ui.busy && adult && email.isNotBlank() && password.length >= 8) { Text(stringResource(R.string.account_signup)) }
                    if (BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID.isNotBlank()) Button(onClick = { google { viewModel.account.signInGoogle(it, guest) } }, enabled = !ui.busy && adult) { Text(stringResource(R.string.account_google)) }
                }
                TextButton(onClick = { viewModel.recover(email) }, enabled = !ui.busy && email.isNotBlank()) { Text(stringResource(R.string.account_recover_password)) }
            }
            is AccountState.SignedIn -> {
                Text(current.email ?: current.displayName ?: stringResource(R.string.account_signed_in))
                if (!dataControlsOnly) {
                Text(stringResource(R.string.account_current_plan, entitlement.effectiveLevel.name))
                Text(stringResource(R.string.account_sync_notice))
                Choice(consent, { consent = it }, stringResource(R.string.account_sync_consent))
                Button(onClick = { viewModel.synchronize(consent) }, enabled = !ui.busy && consent) { Text(stringResource(R.string.account_sync_start)) }
                TextButton(onClick = { viewModel.stopSync() }, enabled = !ui.busy) { Text(stringResource(R.string.account_sync_stop)) }
                val sync = AccountGraph.sync()
                if (sync != null) {
                    val status by sync.status.collectAsState()
                    Text(when (val value = status) {
                        com.example.kpkn.domain.sync.SyncStatus.Disabled -> stringResource(R.string.account_sync_disabled)
                        is com.example.kpkn.domain.sync.SyncStatus.Ready -> stringResource(R.string.account_sync_ready, value.pending,
                            if (value.lastConfirmedAtEpochMs == 0L) stringResource(R.string.account_never) else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(value.lastConfirmedAtEpochMs)))
                        is com.example.kpkn.domain.sync.SyncStatus.Working -> stringResource(R.string.account_sync_working, value.pending)
                        is com.example.kpkn.domain.sync.SyncStatus.Conflict -> stringResource(R.string.account_sync_conflicts, value.count)
                        is com.example.kpkn.domain.sync.SyncStatus.Error -> stringResource(R.string.account_operation_failed)
                    })
                    (status as? com.example.kpkn.domain.sync.SyncStatus.Ready)?.takeIf { it.waitingForPro > 0 }?.let {
                        Text(stringResource(R.string.account_sync_pro_pending, it.waitingForPro))
                    }
                    TextButton(onClick = { viewModel.action { val records = sync.conflicts(); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { conflicts = records } } }, enabled = !ui.busy) { Text(stringResource(R.string.account_review_conflicts)) }
                    conflicts.forEach { conflict ->
                        val id = conflict.getValue("id").toString().trim('"')
                        var versions by remember(id) { mutableStateOf<Pair<kotlinx.serialization.json.JsonObject?, kotlinx.serialization.json.JsonObject?>?>(null) }
                        LaunchedEffect(id) { runCatching { sync.conflictVersions(conflict) }.onSuccess { versions = it } }
                        Text(stringResource(R.string.account_conflict_description, conflict.getValue("incoming").let { (it as kotlinx.serialization.json.JsonObject).getValue("kind").toString().trim('"') },
                            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(conflict.getValue("createdAtEpochMs").toString().toLong()))))
                        versions?.let { (currentVersion, incomingVersion) ->
                            Text(stringResource(R.string.account_version_current)); VersionDetails(currentVersion)
                            Text(stringResource(R.string.account_version_incoming)); VersionDetails(incomingVersion)
                        }
                        TextButton(onClick = { viewModel.action { sync.resolveConflict(id, "KEEP_CURRENT"); val records = sync.conflicts(); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { conflicts = records }; AccountGraph.refreshReaders(context) } }, enabled = !ui.busy && versions != null) { Text(stringResource(R.string.account_keep_current)) }
                        TextButton(onClick = { viewModel.action { sync.resolveConflict(id, "USE_INCOMING"); val records = sync.conflicts(); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { conflicts = records }; AccountGraph.refreshReaders(context) } }, enabled = !ui.busy && versions != null) { Text(stringResource(R.string.account_use_incoming)) }
                        if (conflict["reason"]?.toString()?.trim('"') == "ACTIVE_WORKOUT_OWNED_BY_ANOTHER_DEVICE") {
                            var cancel by remember(id) { mutableStateOf(false) }
                            Choice(cancel, { cancel = it }, stringResource(R.string.account_cancel_remote_confirm))
                            TextButton(onClick = { viewModel.action { sync.cancelRemoteWorkout(conflict) } }, enabled = !ui.busy && cancel && versions != null) { Text(stringResource(R.string.account_cancel_remote)) }
                        }
                    }
                }
                TextButton(onClick = { viewModel.action { AccountGraph.subscriptions(context).restore() } }, enabled = !ui.busy) { Text(stringResource(R.string.account_restore_purchases)) }
                if (!BuildConfig.SALES_APPROVED) Text(stringResource(R.string.account_sales_pending))
                else {
                    val billing = remember { AccountGraph.subscriptions(context) }
                    val offers by billing.offers.collectAsState()
                    LaunchedEffect(billing) { runCatching { billing.queryOffers() } }
                    offers.forEach { offer ->
                        Text(stringResource(R.string.account_renewal, offer.formattedPrice, if (offer.billingPeriod == "P1M") stringResource(R.string.account_month) else stringResource(R.string.account_year)))
                        if (offer.trialDays == 14) Text(stringResource(R.string.account_trial))
                        Button(onClick = { context.activity()?.let { billing.purchase(it, current.uid, offer) } }) { Text(stringResource(R.string.account_subscribe)) }
                    }
                }
                if (BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID.isNotBlank()) TextButton(onClick = { google { viewModel.account.linkGoogle(it) } }, enabled = !ui.busy) { Text(stringResource(R.string.account_link_google)) }
                }
                TextButton(onClick = { viewModel.action { viewModel.account.signOut() } }, enabled = !ui.busy) { Text(stringResource(R.string.account_signout)) }
                HorizontalDivider()
                Text(stringResource(R.string.account_delete_notice))
                OutlinedTextField(password, { password = it.take(256) }, label = { Text(stringResource(R.string.account_password)) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
                Choice(confirmDelete, { confirmDelete = it }, stringResource(R.string.account_delete_confirm))
                Button(onClick = { viewModel.action { viewModel.account.reauthenticate(password); password = ""; viewModel.account.deleteAccount() } }, enabled = !ui.busy && confirmDelete && password.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.account_delete)) }
                if (BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID.isNotBlank()) TextButton(onClick = { google { viewModel.account.reauthenticateGoogle(it); viewModel.account.deleteAccount() } }, enabled = !ui.busy && confirmDelete) { Text(stringResource(R.string.account_delete_google)) }
            }
        }
        if (onDataControls != null) TextButton(onClick = onDataControls) { Text(stringResource(R.string.account_data_controls)) }
        if (!dataControlsOnly) {
            HorizontalDivider()
            Choice(crashConsent, { value -> viewModel.action { com.example.kpkn.data.diagnostics.CrashReportingController.changeConsent(context, value); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { crashConsent = value } } }, stringResource(R.string.account_crash_consent))
        }
    }
    if (eraseUnavailable) AlertDialog(onDismissRequest = { eraseUnavailable = false },
        title = { Text(stringResource(R.string.account_erase_unavailable_local)) },
        text = { Text(stringResource(R.string.account_erase_unavailable_confirm)) },
        confirmButton = { TextButton(onClick = { eraseUnavailable = false; viewModel.action { viewModel.account.eraseUnavailableLocalProfile() } }) { Text(stringResource(R.string.settings_delete_action)) } },
        dismissButton = { TextButton(onClick = { eraseUnavailable = false }) { Text(stringResource(R.string.settings_cancel)) } })
}
@Composable private fun Choice(value: Boolean, change: (Boolean) -> Unit, label: String) {
    Row(Modifier.fillMaxWidth().minimumInteractiveComponentSize().toggleable(value = value,
        role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = change),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(value, onCheckedChange = null)
        Text(label, Modifier.weight(1f))
    }
}
@Composable fun EraseUnavailableProfileAction(onConfirmed: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.account_erase_unavailable_local)) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.account_erase_unavailable_local)) },
        text = { Text(stringResource(R.string.account_erase_unavailable_confirm)) },
        confirmButton = { TextButton(onClick = { confirm = false; onConfirmed() }) { Text(stringResource(R.string.settings_delete_action)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.settings_cancel)) } })
}
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable private fun VersionDetails(value: kotlinx.serialization.json.JsonObject?) {
    if (value == null) { Text(stringResource(R.string.account_record_deleted)); return }
    val labels = mapOf("name" to R.string.account_field_name, "title" to R.string.account_field_name,
        "date" to R.string.account_field_date, "notes" to R.string.account_field_notes,
        "grams" to R.string.account_field_grams, "weightKg" to R.string.account_field_weight,
        "repetitions" to R.string.account_field_reps, "reps" to R.string.account_field_reps,
        "durationSeconds" to R.string.account_field_duration, "calories" to R.string.account_field_calories)
    var shown = false
    labels.forEach { (field, resource) ->
        val primitive = value[field] as? kotlinx.serialization.json.JsonPrimitive
        if (primitive != null && primitive != kotlinx.serialization.json.JsonNull) {
            shown = true; Text(stringResource(resource) + ": " + primitive.content.take(512), style = MaterialTheme.typography.bodySmall)
        }
    }
    val items = value["items"] as? kotlinx.serialization.json.JsonArray
    items?.take(8)?.forEach { item -> (item as? kotlinx.serialization.json.JsonObject)?.let { VersionDetails(it) } }
    val macros = value["macrocycles"] as? kotlinx.serialization.json.JsonArray
    if (macros != null) Text(stringResource(R.string.account_program_summary, macros.size))
    if (!shown && items == null && macros == null) Text(stringResource(R.string.account_record_preserved), style = MaterialTheme.typography.bodySmall)
}
