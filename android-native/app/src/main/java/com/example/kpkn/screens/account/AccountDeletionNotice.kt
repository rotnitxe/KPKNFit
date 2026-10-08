package com.example.kpkn.screens.account

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.kpkn.R
import com.example.kpkn.data.account.AccountDeletionNotices
import kotlinx.coroutines.launch

@Composable fun AccountDeletionNotice() {
    val value by AccountDeletionNotices.notice.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { AccountDeletionNotices.load() }
    value?.let { notice ->
        AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.account_delete_notice_title)) },
            text = { Text(stringResource(if (notice.localPending) R.string.account_delete_local_pending_notice else R.string.account_delete_pending_notice)) },
            confirmButton = { TextButton(onClick = { scope.launch { AccountDeletionNotices.dismiss() } }) { Text(stringResource(R.string.account_notice_understood)) } })
    }
}
