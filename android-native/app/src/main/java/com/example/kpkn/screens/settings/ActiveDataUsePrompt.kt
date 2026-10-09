package com.example.kpkn.screens.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.kpkn.R
import com.example.kpkn.data.backup.BlockedDataUse
import com.example.kpkn.ui.components.KpknAlertDialog

/** Selects a workout exit flow; saving/discarding still requires its normal confirmation. */
@Composable
internal fun ActiveDataUsePrompt(blocked: BlockedDataUse, onFinish: () -> Unit,
    onDiscard: () -> Unit, onDismiss: () -> Unit) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.data_session_active_title)) },
        text = { Text(stringResource(if (blocked.workout != null)
            R.string.data_session_active_workout_description else R.string.data_session_active_capture_description)) },
        confirmButton = {
            if (blocked.workout != null) TextButton(onClick = onFinish) {
                Text(stringResource(R.string.data_session_finish_workout))
            }
        },
        dismissButton = {
            if (blocked.workout != null) TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.data_session_discard_workout))
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_back)) }
        },
    )
}
